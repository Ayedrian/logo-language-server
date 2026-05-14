package logo.analysis

import logo.diagnostics.Diagnostic
import logo.diagnostics.DiagnosticSeverity
import logo.lexer.Token
import logo.parser.ArrayLiteralNode
import logo.parser.BinaryOpNode
import logo.parser.BlockExpressionNode
import logo.parser.CallExpressionNode
import logo.parser.CommandNode
import logo.parser.ExpressionNode
import logo.parser.ProcedureDefNode
import logo.parser.ProgramNode
import logo.parser.StatementNode
import logo.parser.UnaryOpNode
import logo.parser.VariableRefNode
import logo.parser.WordLiteralNode

/**
 * Symbol table populated by walking the AST after parsing
 * - proceduresByName: user-defined procedures keyed by name (for go-to-declaration on call sites)
 * - varReferences: each variable reference token mapped to the parameter declaration token it resolves to,
 *   keyed by Token because Token is a data class whose (line, char) make distinct refs naturally
 *   distinguishable, unresolved refs (no matching parameter in scope) are omitted
 */
class SymbolTable {
    val proceduresByName: MutableMap<String, ProcedureDefNode> = mutableMapOf()
    val varReferences: MutableMap<Token, Token> = mutableMapOf()
}

/**
 * Result of walking the AST, contains both the populated table and any semantic diagnostics
 * (currently only "unbound variable") encountered during the walk
 */
data class SymbolTableResult(val table: SymbolTable, val diagnostics: List<Diagnostic>)

/**
 * Walks the AST and registers every top-level ProcedureDefNode by its (lowercased) name
 * If the same name is defined twice, the later definition is used (LOGO's "to" redefines a procedure)
 * For each procedure, resolves variable references in its body against its parameters
 * Variable refs that don't resolve (whether in a body or at the top level) become WARNING diagnostics
 */
class SymbolTableBuilder(private val ast: ProgramNode) {
    private val table = SymbolTable()
    private val diagnostics = mutableListOf<Diagnostic>()

    fun build(): SymbolTableResult {
        for (stmt in ast.statements) {
            when (stmt) {
                is ProcedureDefNode -> {
                    table.proceduresByName[stmt.nameToken.text] = stmt
                    val params = stmt.params.associateBy { it.text }
                    for (bodyStmt in stmt.body) walkStatement(bodyStmt, params)
                }
                is CommandNode -> walkStatement(stmt, emptyMap())
            }
        }
        return SymbolTableResult(table, diagnostics)
    }

    private fun walkStatement(stmt: StatementNode, params: Map<String, Token>) {
        when (stmt) {
            is CommandNode -> for (arg in stmt.args) walkExpression(arg, params)
            is ProcedureDefNode -> Unit // nested defs aren't part of the current language subset
        }
    }

    private fun walkExpression(expr: ExpressionNode, params: Map<String, Token>) {
        when (expr) {
            is VariableRefNode -> {
                val decl = params[expr.token.text]
                if (decl != null) {
                    table.varReferences[expr.token] = decl
                } else {
                    // VARIABLE token's char points at ':' but text excludes it, so span = text.length + 1
                    diagnostics += Diagnostic(
                        message = "Unbound variable ':${expr.token.text}'",
                        line = expr.token.line,
                        char = expr.token.char,
                        length = expr.token.text.length + 1,
                        severity = DiagnosticSeverity.WARNING,
                    )
                }
            }
            // statements inside a block resolve against the same scope as the enclosing context;
            // block-local bindings (LOCAL / MAKE) are deferred to a later slice
            is BlockExpressionNode -> for (stmt in expr.statements) walkStatement(stmt, params)
            is BinaryOpNode -> { walkExpression(expr.left, params); walkExpression(expr.right, params) }
            is UnaryOpNode -> walkExpression(expr.operand, params)
            // nested calls in expression context (e.g. "print sum :x 1") — recurse into args
            is CallExpressionNode -> for (arg in expr.args) walkExpression(arg, params)
            // word literals and array literals carry no variable refs; arrays hold only literal data
            is WordLiteralNode, is ArrayLiteralNode -> Unit
            else -> Unit
        }
    }
}
