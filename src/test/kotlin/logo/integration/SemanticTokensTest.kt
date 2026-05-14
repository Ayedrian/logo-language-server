package logo.integration

import logo.analysis.analyse
import logo.features.SemanticTokenKind
import logo.features.collectSemanticTokens
import logo.features.encodeSemanticTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticTokensTest {

    @Test
    fun `print 1 produces correct semantic tokens`() {
        val result = analyse("print 1")
        val raw = collectSemanticTokens(result.ast)

        assertEquals(2, raw.size)
        assertEquals(SemanticTokenKind.FUNCTION, raw[0].kind) // print
        assertEquals(0, raw[0].line)
        assertEquals(0, raw[0].char)
        assertEquals(5, raw[0].length)

        assertEquals(SemanticTokenKind.NUMBER, raw[1].kind) // 1
        assertEquals(0, raw[1].line)
        assertEquals(6, raw[1].char)
        assertEquals(1, raw[1].length)
    }

    @Test
    fun `encoded token array has correct delta encoding`() {
        val result = analyse("print 1")
        val encoded = encodeSemanticTokens(collectSemanticTokens(result.ast))

        // Each token is 5 integers: [deltaLine, deltaChar, length, typeIndex, modifiers]
        assertEquals(10, encoded.size)

        // Token 0: print — deltaLine=0, deltaChar=0, len=5, type=FUNCTION(1), mods=0
        assertEquals(listOf(0, 0, 5, SemanticTokenKind.FUNCTION.index, 0), encoded.subList(0, 5))

        // Token 1: 1 — deltaLine=0, deltaChar=6, len=1, type=NUMBER(2), mods=0
        assertEquals(listOf(0, 6, 1, SemanticTokenKind.NUMBER.index, 0), encoded.subList(5, 10))
    }

    @Test
    fun `analysis produces no diagnostics for valid input`() {
        val result = analyse("fd 10")
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `statements inside a block get semantic tokens`() {
        // line 0: repeat 4 [ fd 10 ]
        //          0      7 9  11 14 17
        val result = analyse("repeat 4 [ fd 10 ]")
        val kinds = collectSemanticTokens(result.ast).map { it.kind }
        // repeat → FUNCTION, 4 → NUMBER, fd → FUNCTION, 10 → NUMBER
        assertEquals(
            listOf(
                SemanticTokenKind.FUNCTION,
                SemanticTokenKind.NUMBER,
                SemanticTokenKind.FUNCTION,
                SemanticTokenKind.NUMBER,
            ),
            kinds,
        )
    }

    @Test
    fun `both colon-name occurrences get PARAMETER tokens`() {
        // line 0: to square :size fd :size end
        //          0  3      10    16 19    25
        val result = analyse("to square :size fd :size end")
        val params = collectSemanticTokens(result.ast).filter { it.kind == SemanticTokenKind.PARAMETER }

        assertEquals(2, params.size)
        assertEquals(0, params[0].line)
        assertEquals(10, params[0].char)
        assertEquals(5, params[0].length) // covers ":size"
        assertEquals(0, params[1].line)
        assertEquals(19, params[1].char)
        assertEquals(5, params[1].length)
    }

    @Test
    fun `arithmetic operands still emit tokens, operators do not`() {
        // line 0: print 1 + 2
        //          0     6 8 10
        val result = analyse("print 1 + 2")
        val tokens = collectSemanticTokens(result.ast)
        // Expect: FUNCTION print, NUMBER 1, NUMBER 2 — '+' has no kind
        assertEquals(
            listOf(SemanticTokenKind.FUNCTION, SemanticTokenKind.NUMBER, SemanticTokenKind.NUMBER),
            tokens.map { it.kind },
        )
        assertEquals(6, tokens[1].char)
        assertEquals(10, tokens[2].char)
    }

    @Test
    fun `variable ref inside arithmetic still emits PARAMETER token`() {
        // to f :x fd :x + 1 end
        val result = analyse("to f :x fd :x + 1 end")
        val params = collectSemanticTokens(result.ast).filter { it.kind == SemanticTokenKind.PARAMETER }
        assertEquals(2, params.size) // header :x and body :x
    }

    @Test
    fun `quoted word emits STRING token covering leading quote`() {
        // line 0: print "hello
        //          0     6
        val result = analyse("print \"hello")
        val strings = collectSemanticTokens(result.ast).filter { it.kind == SemanticTokenKind.STRING }
        assertEquals(1, strings.size)
        assertEquals(0, strings[0].line)
        assertEquals(6, strings[0].char) // position of '"'
        assertEquals(6, strings[0].length) // '"hello' = 6 chars
    }

    @Test
    fun `array literal emits ARRAY tokens for braces and bare words and NUMBER for digits`() {
        // line 0: print { 1 red 2 }
        //          0     6 8 10 14 16
        val result = analyse("print { 1 red 2 }")
        val toks = collectSemanticTokens(result.ast)
        // Expect: FUNCTION print, ARRAY '{', NUMBER 1, ARRAY 'red', NUMBER 2, ARRAY '}'
        assertEquals(
            listOf(
                SemanticTokenKind.FUNCTION,
                SemanticTokenKind.ARRAY,
                SemanticTokenKind.NUMBER,
                SemanticTokenKind.ARRAY,
                SemanticTokenKind.NUMBER,
                SemanticTokenKind.ARRAY,
            ),
            toks.map { it.kind },
        )
    }

    @Test
    fun `macro definition emits KEYWORD for the macro keyword and FUNCTION for name`() {
        // line 0: .macro greet :n print :n end
        //          0     7     13 16    22 25
        val result = analyse(".macro greet :n print :n end")
        val toks = collectSemanticTokens(result.ast)
        // First token: '.macro' keyword spanning 6 chars at char 0
        assertEquals(SemanticTokenKind.KEYWORD, toks[0].kind)
        assertEquals(0, toks[0].char)
        assertEquals(6, toks[0].length)
        // Second token: 'greet' name, FUNCTION
        assertEquals(SemanticTokenKind.FUNCTION, toks[1].kind)
        assertEquals(7, toks[1].char)
        assertEquals(5, toks[1].length)
    }

    @Test
    fun `variadic call name still emits FUNCTION token`() {
        // (print 1 2 3) — print should highlight as FUNCTION, parens emit nothing
        val result = analyse("(print 1 2 3)")
        val toks = collectSemanticTokens(result.ast)
        assertEquals(
            listOf(
                SemanticTokenKind.FUNCTION, // print
                SemanticTokenKind.NUMBER,   // 1
                SemanticTokenKind.NUMBER,   // 2
                SemanticTokenKind.NUMBER,   // 3
            ),
            toks.map { it.kind },
        )
    }

    // ---- slice 11: block-level scope tracking ----

    @Test
    fun `in-block make highlights its name as STRING covering the leading quote`() {
        // line 0: to f repeat 3 [ make "z 1 print :z ] end
        //          0  3 5      12 14 16   21    26    32    38
        val result = analyse("to f repeat 3 [ make \"z 1 print :z ] end")
        val strings = collectSemanticTokens(result.ast).filter { it.kind == SemanticTokenKind.STRING }
        assertEquals(1, strings.size)
        assertEquals(0, strings[0].line)
        assertEquals(21, strings[0].char) // position of '"'
        assertEquals(2, strings[0].length) // '"z' = 2 chars
    }

    @Test
    fun `nested procedure call emits FUNCTION token for inner call`() {
        // print sum 3 4 — both 'print' and 'sum' should be FUNCTION
        val result = analyse("print sum 3 4")
        val toks = collectSemanticTokens(result.ast)
        assertEquals(
            listOf(
                SemanticTokenKind.FUNCTION, // print
                SemanticTokenKind.FUNCTION, // sum
                SemanticTokenKind.NUMBER,   // 3
                SemanticTokenKind.NUMBER,   // 4
            ),
            toks.map { it.kind },
        )
    }
}
