package logo.analysis

import logo.diagnostics.DiagnosticSeverity
import logo.lexer.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolTableTest {

    @Test
    fun `variable reference in body resolves to header param`() {
        // line 0: to square :size fd :size end
        //          0  3      10    16 19    25
        val result = analyse("to square :size fd :size end")
        val refs = result.symbolTable.varReferences

        assertEquals(1, refs.size)
        val (refToken, declToken) = refs.entries.single()
        // ref is the body :size at column 19
        assertEquals("size", refToken.text)
        assertEquals(19, refToken.char)
        // decl is the header :size at column 10
        assertEquals("size", declToken.text)
        assertEquals(10, declToken.char)
    }

    @Test
    fun `unbound variable reference is not recorded`() {
        val result = analyse("to square :size fd :foo end")
        assertTrue(result.symbolTable.varReferences.isEmpty())
    }

    @Test
    fun `unbound variable in body emits warning diagnostic`() {
        // line 0: to square :size fd :foo end
        //          0  3      10    16 19    25
        val result = analyse("to square :size fd :foo end")
        val d = result.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, d.severity)
        assertEquals(0, d.line)
        assertEquals(19, d.char)
        assertEquals(4, d.length) // ":foo"
        assertTrue("foo" in d.message)
    }

    @Test
    fun `bound variable produces no diagnostic`() {
        val result = analyse("to square :size fd :size end")
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `variable references inside a block resolve against the enclosing procedure params`() {
        // to f :x repeat :x [ fd :x ] end
        val result = analyse("to f :x repeat :x [ fd :x ] end")
        val refs = result.symbolTable.varReferences
        assertEquals(2, refs.size)
        assertTrue(refs.keys.all { it.text == "x" })
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `unbound variable inside a block is flagged`() {
        val result = analyse("to f :x repeat 4 [ fd :y ] end")
        val d = result.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, d.severity)
        assertTrue("y" in d.message)
    }

    @Test
    fun `top-level variable reference is flagged as unbound`() {
        // line 0: print :foo
        //          0     6
        val result = analyse("print :foo")
        val d = result.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, d.severity)
        assertEquals(0, d.line)
        assertEquals(6, d.char)
        assertEquals(4, d.length)
    }

    @Test
    fun `variable reference inside arithmetic resolves to header param`() {
        // to f :x fd :x + 1 end
        val result = analyse("to f :x fd :x + 1 end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (refToken, declToken) = refs.entries.single()
        assertEquals("x", refToken.text)
        assertEquals("x", declToken.text)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `unbound variable inside arithmetic is flagged`() {
        val result = analyse("to f :x fd :y + 1 end")
        val d = result.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, d.severity)
        assertTrue("y" in d.message)
    }

    @Test
    fun `variable reference inside comparison resolves to header param`() {
        // to f :x if :x < 10 [ fd :x ] end
        val result = analyse("to f :x if :x < 10 [ fd :x ] end")
        val refs = result.symbolTable.varReferences
        assertEquals(2, refs.size)
        assertTrue(refs.keys.all { it.text == "x" })
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `variable reference inside a nested call resolves`() {
        // to f :x print sum :x 1 end
        val result = analyse("to f :x print sum :x 1 end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (refToken, declToken) = refs.entries.single()
        assertEquals("x", refToken.text)
        assertEquals("x", declToken.text)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `macro is registered alongside procedures`() {
        // .macro defines a callable just like 'to'; the symbol table should expose it the same way
        val result = analyse(".macro greet :n print :n end")
        val def = result.symbolTable.proceduresByName["greet"]
        assertEquals("greet", def?.nameToken?.text)
        assertEquals(".macro", def?.defToken?.text)
        assertEquals(1, def?.params?.size)
    }

    @Test
    fun `variadic call args are walked for unbound variables`() {
        // (print :foo) — the variadic statement call still walks its args for unbound refs
        val result = analyse("(print :foo)")
        val d = result.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, d.severity)
        assertTrue("foo" in d.message)
    }

    @Test
    fun `array literal does not produce diagnostics or refs`() {
        // print { 1 :x 2 } — the :x lexes as VARIABLE but inside {} it becomes WORD, so no
        // unbound-variable diagnostic should fire even at top-level. Wait: inside braces only
        // identifier-shaped lexemes turn into WORD; ":x" still lexes as VARIABLE. So this
        // would emit one warning. Use bare words instead.
        val result = analyse("print { red green 1 2 }")
        assertTrue(result.symbolTable.varReferences.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `make introduces a binding for later refs`() {
        // to f make "x 5 print :x end
        //  0  3 5    10 13 15    21    27
        val result = analyse("to f make \"x 5 print :x end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, decl) = refs.entries.single()
        assertEquals("x", ref.text)
        assertEquals(21, ref.char)
        assertEquals(TokenType.QUOTED_WORD, decl.type)
        assertEquals(10, decl.char)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `localmake introduces a binding for later refs`() {
        val result = analyse("to f localmake \"x 5 print :x end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (_, decl) = refs.entries.single()
        assertEquals(TokenType.QUOTED_WORD, decl.type)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `local without value introduces a binding`() {
        val result = analyse("to f local \"x print :x end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (_, decl) = refs.entries.single()
        assertEquals(TokenType.QUOTED_WORD, decl.type)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `variable reference before its make is unbound`() {
        // to f print :x make "x 5 end
        //  0  3 5     11 14    19    24
        // Slice 12 note: ":x" before the make has no lexical decl (still asserted via
        // empty varReferences), but the name "x" IS bound elsewhere in the file (by the
        // later make), so the dynamic-scoping fallback silences the warning.
        val result = analyse("to f print :x make \"x 5 end")
        assertTrue(result.symbolTable.varReferences.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `param shadowed by later make changes resolution for subsequent refs`() {
        // to f :x print :x make "x 5 print :x end
        //  0  3 5    8     14 17    22 25 27    33
        val result = analyse("to f :x print :x make \"x 5 print :x end")
        val refs = result.symbolTable.varReferences
        assertEquals(2, refs.size)

        // First body :x (before make) resolves to the param at char 5
        val first = refs.entries.single { it.key.char == 14 }
        assertEquals(TokenType.VARIABLE, first.value.type)
        assertEquals(5, first.value.char)

        // Second body :x (after make) resolves to the "x at char 22
        val second = refs.entries.single { it.key.char == 33 }
        assertEquals(TokenType.QUOTED_WORD, second.value.type)
        assertEquals(22, second.value.char)

        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `make inside a block does not leak out`() {
        // to f repeat 3 [ make "z 10 print :z ] print :z end
        //  0  3 5      14 16    21 24 27    33   38    44 47
        // Slice 12 note: out-of-block ":z" doesn't lexically resolve (asserted via the
        // refs.size == 1), but "z" is bound in-block, so the dynamic-scoping fallback
        // silences the warning that older slices would have emitted.
        val result = analyse("to f repeat 3 [ make \"z 10 print :z ] print :z end")

        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, decl) = refs.entries.single()
        assertEquals(33, ref.char) // in-block :z
        assertEquals(21, decl.char) // "z
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `top-level make binds a name for later top-level refs`() {
        // make "g 5 print :g
        //  0    5  8  10    16
        val result = analyse("make \"g 5 print :g")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (_, decl) = refs.entries.single()
        assertEquals(TokenType.QUOTED_WORD, decl.type)
        assertEquals(5, decl.char)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `make with non-literal name adds no binding`() {
        // make's first arg is :name (a VariableRefNode), not a quoted word — no binding.
        // The :name itself is unbound at top level and emits a warning.
        val result = analyse("make :name 5")
        assertTrue(result.symbolTable.varReferences.isEmpty())
        val d = result.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, d.severity)
        assertTrue("name" in d.message)
    }

    @Test
    fun `variadic local binds every quoted-word arg`() {
        val result = analyse("to f (local \"x \"y) print :x print :y end")
        val refs = result.symbolTable.varReferences
        assertEquals(2, refs.size)
        assertTrue(refs.values.all { it.type == TokenType.QUOTED_WORD })
        assertTrue(result.diagnostics.isEmpty())
    }

    // ---- slice 11: block-level scope tracking ----

    @Test
    fun `nested block sees binding from enclosing block`() {
        // to f repeat 3 [ make "z 1 repeat 2 [ print :z ] ] end
        //  0  3 5      12 14 16   21 23   26    33 35 37   43 45 47
        val result = analyse("to f repeat 3 [ make \"z 1 repeat 2 [ print :z ] ] end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, decl) = refs.entries.single()
        assertEquals("z", ref.text)
        assertEquals(43, ref.char) // inner ":z"
        assertEquals(TokenType.QUOTED_WORD, decl.type)
        assertEquals(21, decl.char) // outer block's "z
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `binding inside inner block does not leak to outer block`() {
        // to f repeat 3 [ repeat 2 [ make "z 1 ] print :z ] end
        //  0  3 5      12 14 16     23 25    32 34    39 45 47 49
        // Slice 12 note: outer ":z" doesn't lexically resolve (refs is empty), but "z"
        // is bound in the inner block, so the dynamic-scoping fallback silences the
        // warning that older slices would have emitted.
        val result = analyse("to f repeat 3 [ repeat 2 [ make \"z 1 ] print :z ] end")
        assertTrue(result.symbolTable.varReferences.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `sibling blocks in ifelse do not share bindings`() {
        // to f ifelse 1 [ make "z 1 ] [ print :z ] end
        //  0  3 5      12 14 16   21 26 28 30   36
        // Slice 12 note: ":z" in the else-arm doesn't lexically resolve (refs empty),
        // but "z" is bound in the then-arm, so the dynamic-scoping fallback silences
        // the warning that older slices would have emitted.
        val result = analyse("to f ifelse 1 [ make \"z 1 ] [ print :z ] end")
        assertTrue(result.symbolTable.varReferences.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `in-block ordering — ref before make is unbound, ref after resolves`() {
        // to f repeat 3 [ print :z make "z 1 print :z ] end
        //  0  3 5      12 14 16    22 25   30 32 34 36    42 44 46
        // Slice 12 note: first ":z" doesn't lexically resolve (refs has only the
        // second), but "z" is bound by the same-block make, so the dynamic-scoping
        // fallback silences the warning older slices would have emitted on the first.
        val result = analyse("to f repeat 3 [ print :z make \"z 1 print :z ] end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, decl) = refs.entries.single()
        assertEquals(41, ref.char) // second ":z" — after the make
        assertEquals(TokenType.QUOTED_WORD, decl.type)
        assertEquals(30, decl.char) // "z
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `local inside a block binds within the block and does not leak`() {
        // to f repeat 3 [ local "z print :z ] print :z end
        //  0  3 5      12 14 16   22 25   31    36 38    44
        // Slice 12 note: out-of-block ":z" doesn't lexically resolve; "z" is bound in
        // the block, so the dynamic-scoping fallback silences the warning.
        val result = analyse("to f repeat 3 [ local \"z print :z ] print :z end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, decl) = refs.entries.single()
        assertEquals(31, ref.char) // in-block ":z"
        assertEquals(TokenType.QUOTED_WORD, decl.type)
        assertEquals(22, decl.char) // "z
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `localmake inside a block binds within the block and does not leak`() {
        // to f repeat 3 [ localmake "z 5 print :z ] print :z end
        //  0  3 5      12 14 16       25 27 29 31    37 39 41    47
        // Slice 12 note: out-of-block ":z" doesn't lexically resolve; "z" is bound in
        // the block, so the dynamic-scoping fallback silences the warning.
        val result = analyse("to f repeat 3 [ localmake \"z 5 print :z ] print :z end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, decl) = refs.entries.single()
        assertEquals(37, ref.char) // in-block ":z"
        assertEquals(TokenType.QUOTED_WORD, decl.type)
        assertEquals(26, decl.char) // "z
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `variadic local inside a block binds every name and does not leak`() {
        // to f repeat 3 [ (local "a "b) print :a print :b ] print :a end
        // Slice 12 note: out-of-block ":a" doesn't lexically resolve; "a" is bound in
        // the block, so the dynamic-scoping fallback silences the warning.
        val result = analyse("to f repeat 3 [ (local \"a \"b) print :a print :b ] print :a end")
        val refs = result.symbolTable.varReferences
        // In-block :a and :b both resolve; out-of-block :a is unbound (not in refs).
        assertEquals(2, refs.size)
        assertTrue(refs.values.all { it.type == TokenType.QUOTED_WORD })
        assertTrue(result.diagnostics.isEmpty())
    }

    // ---- slice 12: dynamic-scoping fallback + `for` counter binding ----

    @Test
    fun `unbound ref silenced when name is bound in another procedure (dynamic scoping)`() {
        // to a make "shared 1 end to b print :shared end
        // ":shared" in b is lexically unbound, but "shared" is bound in a, so the
        // dynamic-scoping fallback silences the warning. No jump target either.
        val result = analyse("to a make \"shared 1 end to b print :shared end")
        assertTrue(result.diagnostics.isEmpty())
        assertTrue(result.symbolTable.varReferences.isEmpty())
    }

    @Test
    fun `unbound ref still warns when name appears nowhere in the file`() {
        // to a print :nowhere end
        //  0  3 5     11      19
        // "nowhere" is never bound anywhere, so the warning fires as before.
        val result = analyse("to a print :nowhere end")
        val d = result.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, d.severity)
        assertEquals(11, d.char)
        assertEquals(8, d.length) // ":nowhere"
        assertTrue("nowhere" in d.message)
    }

    @Test
    fun `for loop counter resolves inside body`() {
        // to f for [i 1 10] [print :i] end
        //  0  3 5    10    16 18    25 27
        val result = analyse("to f for [i 1 10] [print :i] end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, decl) = refs.entries.single()
        assertEquals(25, ref.char) // :i ref (colon position)
        assertEquals(TokenType.IDENTIFIER, decl.type) // counter is an IDENTIFIER token
        assertEquals(10, decl.char) // 'i' in the template
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `for loop counter does not leak after the body`() {
        // to f for [i 1 10] [print :i] print :i end
        //  0  3 5    10    16 18    25 27   34
        // First :i (in body) resolves to counter; second :i (after body) does not
        // resolve lexically — but is silenced by the dynamic-scoping fallback since
        // "i" is bound somewhere in the file (as the counter).
        val result = analyse("to f for [i 1 10] [print :i] print :i end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, _) = refs.entries.single()
        assertEquals(25, ref.char) // in-body :i
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `nested for - inner counter shadows outer of same name`() {
        // to f for [i 1 10] [for [i 1 5] [print :i]] end
        //  0  3 5    10    16  19   24  29  31    38 40 41
        val result = analyse("to f for [i 1 10] [for [i 1 5] [print :i]] end")
        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, decl) = refs.entries.single()
        assertEquals(38, ref.char) // :i ref in inner body
        // Resolves to inner counter at char 24, not outer at char 10
        assertEquals(24, decl.char)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `for body sees both counter and in-body make binding`() {
        // to f for [i 1 10] [make "j :i print :j] end
        //  0  3 5    10    18 19   24 27 30    36 38
        val result = analyse("to f for [i 1 10] [make \"j :i print :j] end")
        val refs = result.symbolTable.varReferences
        assertEquals(2, refs.size)

        // :i (in make's value) resolves to the counter at char 10
        val iRef = refs.entries.single { it.key.text == "i" }
        assertEquals(27, iRef.key.char)
        assertEquals(TokenType.IDENTIFIER, iRef.value.type)
        assertEquals(10, iRef.value.char)

        // :j (in the print) resolves to the in-body make's "j at char 24
        val jRef = refs.entries.single { it.key.text == "j" }
        assertEquals(36, jRef.key.char)
        assertEquals(TokenType.QUOTED_WORD, jRef.value.type)
        assertEquals(24, jRef.value.char)

        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `param shadowed by in-block make is restored after the block`() {
        // to f :x repeat 3 [ print :x make "x 1 print :x ] print :x end
        //  0  3 5  8       17 19    25 28   33 35 37    43 45 49    55  60
        val result = analyse("to f :x repeat 3 [ print :x make \"x 1 print :x ] print :x end")
        val refs = result.symbolTable.varReferences
        assertEquals(3, refs.size)

        // First in-block :x → param at char 5
        val first = refs.entries.single { it.key.char == 25 }
        assertEquals(TokenType.VARIABLE, first.value.type)
        assertEquals(5, first.value.char)

        // Second in-block :x → "x at char 33
        val second = refs.entries.single { it.key.char == 44 }
        assertEquals(TokenType.QUOTED_WORD, second.value.type)
        assertEquals(33, second.value.char)

        // After-block :x → param at char 5 again (blockScope was discarded)
        val third = refs.entries.single { it.key.char == 55 }
        assertEquals(TokenType.VARIABLE, third.value.type)
        assertEquals(5, third.value.char)

        assertTrue(result.diagnostics.isEmpty())
    }
}
