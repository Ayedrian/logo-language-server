package logo.analysis

import logo.diagnostics.Diagnostic
import logo.diagnostics.DiagnosticSeverity
import logo.lexer.Token
import logo.lexer.TokenType
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
 *
 * Variable resolution uses an ordered, mutating scope: as we walk through statements in order,
 * bindings introduced by `make`, `local`, and `localmake` are added to the scope and seen by
 * subsequent refs. A procedure body's scope is seeded with its params; the top level starts empty.
 * Block expressions get a copy of the scope at entry, so in-block bindings don't leak out (refs
 * inside the block still see bindings introduced before it, including bindings in enclosing blocks).
 *
 * Variable refs that don't resolve (whether in a body or at the top level) become WARNING diagnostics.
 * Note: this is a lexical approximation. LOGO uses dynamic scoping at runtime, so a :x ref inside a
 * procedure may legitimately bind to a `local "x` in the caller — we can't see that statically and
 * will warn. Slice 12 adds a file-wide-union fallback to silence those false positives.
 */
class SymbolTableBuilder(private val ast: ProgramNode) {
    private val table = SymbolTable()
    private val diagnostics = mutableListOf<Diagnostic>()

    fun build(): SymbolTableResult {
        val topLevelScope = mutableMapOf<String, Token>()
        for (stmt in ast.statements) {
            when (stmt) {
                is ProcedureDefNode -> {
                    table.proceduresByName[stmt.nameToken.text] = stmt
                    val bodyScope = mutableMapOf<String, Token>()
                    for (param in stmt.params) bodyScope[param.text] = param
                    for (bodyStmt in stmt.body) walkStatement(bodyStmt, bodyScope)
                }
                is CommandNode -> walkStatement(stmt, topLevelScope)
            }
        }
        return SymbolTableResult(table, diagnostics)
    }

    private fun walkStatement(stmt: StatementNode, scope: MutableMap<String, Token>) {
        when (stmt) {
            is CommandNode -> {
                // Walk args first so that "make "x :x + 1" sees the old :x before the new binding
                for (arg in stmt.args) walkExpression(arg, scope)
                bindIfMakeOrLocal(stmt, scope)
            }
            is ProcedureDefNode -> Unit // nested defs aren't part of the current language subset
        }
    }

    /**
     * If [cmd] is `make`/`localmake` (binds first arg) or `local` (binds every arg), pull the
     * QUOTED_WORD token(s) out and add them to [scope] as the declaration for that name.
     * Non-literal arguments (e.g. `make :name 5`) silently produce no binding.
     */
    private fun bindIfMakeOrLocal(cmd: CommandNode, scope: MutableMap<String, Token>) {
        when (cmd.nameToken.text) {
            "make", "localmake" -> {
                val first = cmd.args.firstOrNull() ?: return
                if (first is WordLiteralNode && first.token.type == TokenType.QUOTED_WORD) {
                    scope[first.token.text] = first.token
                }
            }
            "local" -> {
                for (arg in cmd.args) {
                    if (arg is WordLiteralNode && arg.token.type == TokenType.QUOTED_WORD) {
                        scope[arg.token.text] = arg.token
                    }
                }
            }
        }
    }

    private fun walkExpression(expr: ExpressionNode, scope: MutableMap<String, Token>) {
        when (expr) {
            is VariableRefNode -> {
                val decl = scope[expr.token.text]
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
            // Refs inside a block see bindings introduced before the block (in the enclosing
            // scope, including outer blocks); bindings introduced inside the block stay inside
            // via copy-on-entry. In-block statements walk in source order, mutating blockScope.
            is BlockExpressionNode -> {
                val blockScope = scope.toMutableMap()
                for (stmt in expr.statements) walkStatement(stmt, blockScope)
            }
            is BinaryOpNode -> { walkExpression(expr.left, scope); walkExpression(expr.right, scope) }
            is UnaryOpNode -> walkExpression(expr.operand, scope)
            // nested calls in expression context (e.g. "print sum :x 1") — recurse into args
            is CallExpressionNode -> for (arg in expr.args) walkExpression(arg, scope)
            // word literals and array literals carry no variable refs; arrays hold only literal data
            is WordLiteralNode, is ArrayLiteralNode -> Unit
            else -> Unit
        }
    }
}
