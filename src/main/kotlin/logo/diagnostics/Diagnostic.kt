package logo.diagnostics

enum class DiagnosticSeverity { ERROR, WARNING }

/**
 * A reported problem in the source code, with enough info to produce an LSP Range:
 *  - (line, char) is the start position (zero-based)
 *  - length is the on-screen span on that line; diagnostics are single-line for now
 *  - severity drives the LSP severity field at the server boundary
 */
data class Diagnostic(
    val message: String,
    val line: Int,
    val char: Int,
    val length: Int,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
)
