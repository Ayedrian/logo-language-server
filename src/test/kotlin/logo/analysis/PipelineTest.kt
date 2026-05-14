package logo.analysis

import logo.diagnostics.DiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineTest {

    @Test
    fun `parser and symbol-table diagnostics both surface`() {
        // Missing 'end' (parser ERROR) plus unbound :y (symbol-table WARNING)
        val result = analyse("to f :x fd :y")
        val severities = result.diagnostics.map { it.severity }.toSet()
        assertEquals(2, result.diagnostics.size)
        assertEquals(setOf(DiagnosticSeverity.ERROR, DiagnosticSeverity.WARNING), severities)
    }
}
