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

    // ---- slice 12: BUILTIN_ARITIES expansion ----

    @Test
    fun `BUILTIN_ARITIES includes representative primitives from each category`() {
        // Sanity check that the slice 12 expansion landed primitives from each
        // category in UCBLogo's user manual. Not exhaustive — just enough to catch
        // accidentally dropping a section.
        val expected = mapOf(
            // constructors / selectors
            "first" to 1, "last" to 1, "list" to 2,
            // predicates
            "equalp" to 2, "wordp" to 1, "emptyp" to 1,
            // arithmetic
            "sin" to 1, "cos" to 1, "power" to 2, "remainder" to 2,
            // turtle motion / state
            "setpos" to 1, "setheading" to 1, "showturtle" to 0,
            // pen
            "setpencolor" to 1, "pendown" to 0,
            // logical
            "and" to 2, "not" to 1,
            // random / bit
            "random" to 1, "bitand" to 2,
            // control
            "run" to 1, "catch" to 2, "throw" to 1,
            // loops
            "for" to 2, "while" to 2, "until" to 2,
            // templating
            "apply" to 2, "map" to 2, "filter" to 2,
            // macros
            ".defmacro" to 2, "macroexpand" to 1,
            // backquote (slice 12 lexer addition)
            "`" to 1,
        )
        for ((name, arity) in expected) {
            assertEquals(arity, BUILTIN_ARITIES[name], "arity mismatch for '$name'")
        }
    }
}
