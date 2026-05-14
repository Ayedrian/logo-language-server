package logo.analysis

import logo.lexer.Lexer
import logo.parser.FirstPassScanner
import logo.parser.LogoParser

// built-in procedure arities for the limited subset we support so far
val BUILTIN_ARITIES: Map<String, Int> = mapOf(
    "print" to 1,
    "forward" to 1, "fd" to 1,
    "back" to 1, "bk" to 1,
    "left" to 1, "lt" to 1,
    "right" to 1, "rt" to 1,
    "sum" to 2, "difference" to 2,
    "product" to 2, "quotient" to 2,
    "penup" to 0, "pu" to 0,
    "pendown" to 0, "pd" to 0,
)

fun analyse(source: String): AnalysisResult {
    val tokens = Lexer(source).tokenise()

    val scanner = FirstPassScanner(tokens)
    scanner.scan()

    val arities = BUILTIN_ARITIES + scanner.procedures
    val parser = LogoParser(tokens, arities)
    val ast = parser.parse()

    val symbolTable = SymbolTableBuilder(ast).build()

    return AnalysisResult(ast, symbolTable, parser.diagnostics)
}
