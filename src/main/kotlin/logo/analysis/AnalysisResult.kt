package logo.analysis

import logo.parser.Diagnostic
import logo.parser.ProgramNode

/**
 * An analysis result is an abstract syntax tree, symbol table and list of diagnostics
 */
data class AnalysisResult(
    val ast: ProgramNode,
    val symbolTable: SymbolTable,
    val diagnostics: List<Diagnostic>,
)
