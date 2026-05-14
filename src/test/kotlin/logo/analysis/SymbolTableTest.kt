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
        val result = analyse("to f print :x make \"x 5 end")
        assertTrue(result.symbolTable.varReferences.isEmpty())
        val d = result.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, d.severity)
        assertEquals(11, d.char)
        assertTrue("x" in d.message)
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
        val result = analyse("to f repeat 3 [ make \"z 10 print :z ] print :z end")

        val refs = result.symbolTable.varReferences
        assertEquals(1, refs.size)
        val (ref, decl) = refs.entries.single()
        assertEquals(33, ref.char) // in-block :z
        assertEquals(21, decl.char) // "z

        val d = result.diagnostics.single()
        assertEquals(DiagnosticSeverity.WARNING, d.severity)
        assertEquals(44, d.char) // out-of-block :z
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
}
