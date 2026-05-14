package logo.integration

import logo.analysis.analyse
import logo.features.findHover
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HoverTest {

    @Test
    fun `hover on user procedure call shows signature`() {
        // line 0: to square :size end
        // line 1: square 50
        val source = "to square :size end\nsquare 50"
        val result = analyse(source)
        // Cursor on the "square" call on line 1, char 2
        val hover = findHover(result.ast, result.symbolTable, line = 1, char = 2)
        assertNotNull(hover)
        assertTrue("to square :size" in hover.markdown, "got: ${hover.markdown}")
    }

    @Test
    fun `hover on user procedure def name shows signature`() {
        val source = "to square :size end\nsquare 50"
        val result = analyse(source)
        // Cursor on "square" in the header at line 0, char 5
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 5)
        assertNotNull(hover)
        assertTrue("to square :size" in hover.markdown, "got: ${hover.markdown}")
    }

    @Test
    fun `hover on macro def name uses dot-macro keyword`() {
        val source = ".macro greet :n end"
        val result = analyse(source)
        // Cursor on "greet" at line 0, char 7
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 7)
        assertNotNull(hover)
        assertTrue(".macro greet :n" in hover.markdown, "got: ${hover.markdown}")
    }

    @Test
    fun `hover on built-in call shows arity`() {
        val source = "fd 10"
        val result = analyse(source)
        // Cursor on "fd" at line 0, char 0
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 0)
        assertNotNull(hover)
        assertTrue("fd" in hover.markdown)
        assertTrue("arity 1" in hover.markdown, "got: ${hover.markdown}")
    }

    @Test
    fun `hover on parameter declaration shows owning procedure`() {
        val source = "to square :size fd :size end"
        val result = analyse(source)
        // Cursor on header ":size" at line 0, char 10 (the colon itself)
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 10)
        assertNotNull(hover)
        assertTrue("parameter" in hover.markdown)
        assertTrue("square" in hover.markdown, "got: ${hover.markdown}")
    }

    @Test
    fun `hover on parameter body ref shows owning procedure`() {
        val source = "to square :size fd :size end"
        val result = analyse(source)
        // Cursor on body ":size" at line 0, char 20 (the 's')
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 20)
        assertNotNull(hover)
        assertTrue("parameter" in hover.markdown)
        assertTrue("square" in hover.markdown, "got: ${hover.markdown}")
    }

    @Test
    fun `hover on make-bound variable ref shows local variable`() {
        // to f make "y 1 print :y end
        //  0  3 5    10 13 15    21    27
        val source = "to f make \"y 1 print :y end"
        val result = analyse(source)
        // Cursor on body ":y" at line 0, char 22 (the 'y')
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 22)
        assertNotNull(hover)
        assertTrue("local variable" in hover.markdown)
        assertTrue("y" in hover.markdown, "got: ${hover.markdown}")
    }

    @Test
    fun `hover on for counter ref shows for-loop counter`() {
        // to f for [i 1 10] [print :i] end
        //  0  3 5    10    16 18    25 27   31
        val source = "to f for [i 1 10] [print :i] end"
        val result = analyse(source)
        // Cursor on body ":i" at line 0, char 26 (the 'i')
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 26)
        assertNotNull(hover)
        assertTrue("for-loop counter" in hover.markdown)
        assertTrue("i" in hover.markdown, "got: ${hover.markdown}")
    }

    @Test
    fun `hover on for counter declaration shows for-loop counter`() {
        val source = "to f for [i 1 10] [print :i] end"
        val result = analyse(source)
        // Cursor on template "i" at line 0, char 10
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 10)
        assertNotNull(hover)
        assertTrue("for-loop counter" in hover.markdown)
        assertTrue("i" in hover.markdown, "got: ${hover.markdown}")
    }

    @Test
    fun `hover on dynamic-scoping ref returns null`() {
        // line 0: to a make "shared 1 end
        // line 1: to b print :shared end
        val source = "to a make \"shared 1 end\nto b print :shared end"
        val result = analyse(source)
        // Cursor on ":shared" inside b at line 1, char 13 (the 's' of shared)
        val hover = findHover(result.ast, result.symbolTable, line = 1, char = 13)
        assertNull(hover)
    }

    @Test
    fun `hover on whitespace returns null`() {
        val source = "fd 10"
        val result = analyse(source)
        // Cursor at line 0, char 2 (the space between "fd" and "10")
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 2)
        assertNull(hover)
    }

    @Test
    fun `hover on number literal returns null`() {
        val source = "fd 10"
        val result = analyse(source)
        // Cursor on "10" at line 0, char 3
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 3)
        assertNull(hover)
    }

    @Test
    fun `hover on unknown procedure call returns null`() {
        val source = "frobnicate 1"
        val result = analyse(source)
        // Cursor on "frobnicate" at line 0, char 2
        val hover = findHover(result.ast, result.symbolTable, line = 0, char = 2)
        assertNull(hover)
    }
}
