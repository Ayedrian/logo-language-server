package logo.analysis

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
}
