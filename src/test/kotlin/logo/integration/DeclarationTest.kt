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
}
