package logo.integration

import logo.analysis.analyse
import logo.features.findDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeclarationTest {

    @Test
    fun `go-to-declaration on procedure call returns its definition range`() {
        // line 0: to square :size fd 1 end
        // line 1: square 50
        val source = "to square :size fd 1 end\nsquare 50"
        val result = analyse(source)

        // Cursor on "square" call on line 1, char 2 ("sq^uare")
        val target = findDeclaration(result.ast, result.symbolTable, line = 1, char = 2)
        assertNotNull(target)
        // "square" in "to square :size" starts at line 0, char 3 (after "to ")
        assertEquals(0, target.range.line)
        assertEquals(3, target.range.startChar)
        assertEquals(9, target.range.endChar) // 3 + "square".length
    }

    @Test
    fun `go-to-declaration on built-in returns null`() {
        val result = analyse("fd 10")
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 0)
        assertNull(target)
    }

    @Test
    fun `go-to-declaration on whitespace returns null`() {
        val result = analyse("to square :size fd 1 end\nsquare 50")
        val target = findDeclaration(result.ast, result.symbolTable, line = 1, char = 8) // past "square "
        assertNull(target)
    }

    @Test
    fun `go-to-declaration on variable ref jumps to param declaration`() {
        // line 0: to square :size fd :size end
        //          0  3      10    16 19    25
        val source = "to square :size fd :size end"
        val result = analyse(source)

        // Cursor on the body ":size" at char 20 ("s" of size)
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 20)
        assertNotNull(target)
        // Header ":size" spans columns [10, 15)
        assertEquals(0, target.range.line)
        assertEquals(10, target.range.startChar)
        assertEquals(15, target.range.endChar)
    }

    @Test
    fun `go-to-declaration on unbound variable returns null`() {
        val result = analyse("to square :size fd :foo end")
        // Cursor on the body ":foo" at char 20
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 20)
        assertNull(target)
    }

    @Test
    fun `go-to-declaration on variable ref inside a block jumps to param`() {
        // line 0: to f :size repeat 4 [ fd :size ] end
        //          0  3 5    11     18 20 22 25    31
        val source = "to f :size repeat 4 [ fd :size ] end"
        val result = analyse(source)
        // Cursor on the inner ":size" at char 26 (the "s" of size)
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 26)
        assertNotNull(target)
        // Header ":size" spans columns [5, 10)
        assertEquals(0, target.range.line)
        assertEquals(5, target.range.startChar)
        assertEquals(10, target.range.endChar)
    }

    @Test
    fun `go-to-declaration on procedure call inside a block jumps to its definition`() {
        // line 0: to square :size fd :size end
        // line 1: repeat 4 [ square 10 ]
        val source = "to square :size fd :size end\nrepeat 4 [ square 10 ]"
        val result = analyse(source)
        // Cursor on "square" inside the block on line 1, char 12 ("sq^uare")
        val target = findDeclaration(result.ast, result.symbolTable, line = 1, char = 12)
        assertNotNull(target)
        assertEquals(0, target.range.line)
        assertEquals(3, target.range.startChar)
        assertEquals(9, target.range.endChar)
    }

    @Test
    fun `go-to-declaration on param declaration returns itself`() {
        val result = analyse("to square :size fd :size end")
        // Cursor on the header ":size" at char 10 (the colon itself)
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 10)
        assertNotNull(target)
        assertEquals(0, target.range.line)
        assertEquals(10, target.range.startChar)
        assertEquals(15, target.range.endChar)
    }

    @Test
    fun `go-to-declaration on nested call name jumps to its definition`() {
        // line 0: to greet :n print :n end
        // line 1: print greet 1
        val source = "to greet :n print :n end\nprint greet 1"
        val result = analyse(source)
        // Cursor on "greet" in nested call on line 1, char 8 ("gr^eet")
        val target = findDeclaration(result.ast, result.symbolTable, line = 1, char = 8)
        assertNotNull(target)
        // "greet" in "to greet :n" starts at line 0, char 3
        assertEquals(0, target.range.line)
        assertEquals(3, target.range.startChar)
        assertEquals(8, target.range.endChar) // 3 + "greet".length
    }

    @Test
    fun `go-to-declaration on variable ref inside comparison jumps to param`() {
        // line 0: to f :x if :x < 10 [ fd :x ] end
        //          0  3 5  8  11 14 16 18 20 23   29
        val source = "to f :x if :x < 10 [ fd :x ] end"
        val result = analyse(source)
        // Cursor on ":x" inside the comparison at char 12 (the 'x')
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 12)
        assertNotNull(target)
        // Header ":x" spans columns [5, 7)
        assertEquals(0, target.range.line)
        assertEquals(5, target.range.startChar)
        assertEquals(7, target.range.endChar)
    }

    @Test
    fun `go-to-declaration on variable ref inside arithmetic jumps to param`() {
        // line 0: to f :x fd :x + 1 end
        //          0  3 5  8  11 14 16 18
        val source = "to f :x fd :x + 1 end"
        val result = analyse(source)
        // Cursor on the body ":x" at char 12 (the 'x')
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 12)
        assertNotNull(target)
        // Header ":x" spans columns [5, 7)
        assertEquals(0, target.range.line)
        assertEquals(5, target.range.startChar)
        assertEquals(7, target.range.endChar)
    }

    @Test
    fun `go-to-declaration on macro call jumps to macro header`() {
        // line 0: .macro greet :n print :n end
        // line 1: greet 1
        val source = ".macro greet :n print :n end\ngreet 1"
        val result = analyse(source)
        // Cursor on "greet" call on line 1, char 2
        val target = findDeclaration(result.ast, result.symbolTable, line = 1, char = 2)
        assertNotNull(target)
        // "greet" in ".macro greet :n" starts at line 0, char 7
        assertEquals(0, target.range.line)
        assertEquals(7, target.range.startChar)
        assertEquals(12, target.range.endChar) // 7 + "greet".length
    }

    @Test
    fun `go-to-declaration on variable ref jumps to make declaration`() {
        // to f make "x 5 print :x end
        //  0  3 5    10 13 15    21    27
        val source = "to f make \"x 5 print :x end"
        val result = analyse(source)
        // Cursor on body :x at char 22 (the 'x' after ':')
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 22)
        assertNotNull(target)
        // "x in `make "x 5` spans [10, 12) — char 10 is '"', char 11 is 'x'
        assertEquals(0, target.range.line)
        assertEquals(10, target.range.startChar)
        assertEquals(12, target.range.endChar)
    }

    @Test
    fun `go-to-declaration on variadic call name jumps to its definition`() {
        // line 0: to greet :n print :n end
        // line 1: (greet 1)
        val source = "to greet :n print :n end\n(greet 1)"
        val result = analyse(source)
        // Cursor on "greet" inside the variadic statement call on line 1, char 3
        val target = findDeclaration(result.ast, result.symbolTable, line = 1, char = 3)
        assertNotNull(target)
        // "greet" in "to greet :n" starts at line 0, char 3
        assertEquals(0, target.range.line)
        assertEquals(3, target.range.startChar)
        assertEquals(8, target.range.endChar) // 3 + "greet".length
    }

    // ---- slice 11: block-level scope tracking ----

    @Test
    fun `go-to-declaration on variable ref inside a block jumps to in-block make declaration`() {
        // line 0: to f repeat 3 [ make "z 1 print :z ] end
        //          0  3 5      12 14 16   21 23 25   31    37
        val source = "to f repeat 3 [ make \"z 1 print :z ] end"
        val result = analyse(source)
        // Cursor on the in-block ":z" at char 33 (the 'z' of size)
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 33)
        assertNotNull(target)
        // "z" declaration spans [21, 23) — char 21 is '"', char 22 is 'z'
        assertEquals(0, target.range.line)
        assertEquals(21, target.range.startChar)
        assertEquals(23, target.range.endChar)
    }

    @Test
    fun `go-to-declaration on variable ref in nested inner block jumps to outer-block make`() {
        // line 0: to f repeat 3 [ make "z 1 repeat 2 [ print :z ] ] end
        //          0  3 5      12 14 16   21 23 25      32 34 36    43 45 47
        val source = "to f repeat 3 [ make \"z 1 repeat 2 [ print :z ] ] end"
        val result = analyse(source)
        // Cursor on the inner-block ":z" at char 44 (the 'z')
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 44)
        assertNotNull(target)
        // Outer-block "z" declaration spans [21, 23)
        assertEquals(0, target.range.line)
        assertEquals(21, target.range.startChar)
        assertEquals(23, target.range.endChar)
    }

    // ---- slice 12: `for` counter binding ----

    @Test
    fun `go-to-declaration on for loop counter ref jumps to template counter`() {
        // line 0: to f for [i 1 10] [print :i] end
        //          0  3 5    10    16 18    25 27
        val source = "to f for [i 1 10] [print :i] end"
        val result = analyse(source)
        // Cursor on the ':' of ':i' at char 25
        val target = findDeclaration(result.ast, result.symbolTable, line = 0, char = 25)
        assertNotNull(target)
        // Counter 'i' at char 10 — IDENTIFIER token (no leading colon/quote), so
        // range is [10, 11) — just the one character.
        assertEquals(0, target.range.line)
        assertEquals(10, target.range.startChar)
        assertEquals(11, target.range.endChar)
    }
}
