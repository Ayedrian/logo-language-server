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
 * bindings introduced by `make`, `local`, `localmake`, and the `for` counter are added to the
 * scope and seen by subsequent refs. A procedure body's scope is seeded with its params; the top
 * level starts empty. Block expressions get a copy of the scope at entry, so in-block bindings
 * don't leak out (refs inside the block still see bindings introduced before it, including
 * bindings in enclosing blocks).
 *
 * Unresolved refs check a file-wide bound-name set (collected in a pre-pass) before warning.
 * If the name is bound anywhere in the file, the ref is silently unresolved — LOGO uses dynamic
 * scoping at runtime, so a ref may legitimately bind to a `local "x` in a caller we can't see
 * statically. These refs still get no jump target. Only names that don't appear in any binding
 * anywhere in the file warn.
 */
class SymbolTableBuilder(private val ast: ProgramNode) {
    private val table = SymbolTable()
    private val diagnostics = mutableListOf<Diagnostic>()
    private val globallyBound: Set<String> = collectGloballyBound(ast)

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
                // `for` is a special form: its template binds a counter that's only visible
                // inside the body block (and not in sibling scopes), so it can't be handled
                // by the post-walk bindIfMakeOrLocal path.
                if (stmt.nameToken.text == "for") {
                    walkForLoop(stmt, scope)
                } else {
                    // Walk args first so that "make "x :x + 1" sees the old :x before the new binding
                    for (arg in stmt.args) walkExpression(arg, scope)
                    bindIfMakeOrLocal(stmt, scope)
                }
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

    /**
     * Handles `for [counter start end (step?)] [body]`. The template is parsed as a generic
     * BlockExpressionNode whose only kept statement is CommandNode(counter, []) — start/end/step
     * are NUMBER/VARIABLE tokens that parseStatement silently skips. We extract the counter
     * IDENTIFIER token and seed it into a copy of the scope for the body block, so the counter
     * is visible inside the body but doesn't leak to siblings or to the enclosing scope.
     *
     * Variable refs inside the template's start/end/step expressions are NOT analyzed (the
     * template parsing already discarded them). See README "Scope and Limitations".
     */
    private fun walkForLoop(cmd: CommandNode, scope: MutableMap<String, Token>) {
        val template = cmd.args.getOrNull(0)
        val body = cmd.args.getOrNull(1)
        val counter: Token? = (template as? BlockExpressionNode)
            ?.statements?.firstOrNull()
            ?.let { it as? CommandNode }
            ?.nameToken
        if (body is BlockExpressionNode) {
            val bodyScope = scope.toMutableMap()
            if (counter != null) bodyScope[counter.text] = counter
            for (s in body.statements) walkStatement(s, bodyScope)
        }
    }

    private fun walkExpression(expr: ExpressionNode, scope: MutableMap<String, Token>) {
        when (expr) {
            is VariableRefNode -> {
                val decl = scope[expr.token.text]
                when {
                    decl != null -> table.varReferences[expr.token] = decl
                    // Dynamic-scoping fallback: if the name is bound somewhere in the file,
                    // suppress the warning — at runtime the ref may bind dynamically to that
                    // binding. We still record no jump target; go-to-declaration returns null.
                    expr.token.text in globallyBound -> Unit
                    else -> diagnostics += Diagnostic(
                        message = "Unbound variable ':${expr.token.text}'",
                        line = expr.token.line,
                        char = expr.token.char,
                        // VARIABLE token's char points at ':' but text excludes it, so span = text.length + 1
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

/**
 * Pre-pass that collects every name bound *anywhere* in the file: procedure params, make /
 * localmake / local first args, and `for` counters — at any nesting depth. Used as the
 * dynamic-scoping fallback: an unbound :x lexical ref is silenced (no warning) iff its name
 * appears in this set, since LOGO's dynamic scoping may bind it at runtime.
 */
private fun collectGloballyBound(ast: ProgramNode): Set<String> {
    val names = mutableSetOf<String>()
    for (stmt in ast.statements) collectBindings(stmt, names)
    return names
}

private fun collectBindings(stmt: StatementNode, names: MutableSet<String>) {
    when (stmt) {
        is ProcedureDefNode -> {
            for (param in stmt.params) names += param.text
            for (s in stmt.body) collectBindings(s, names)
        }
        is CommandNode -> {
            when (stmt.nameToken.text) {
                "make", "localmake" -> {
                    val first = stmt.args.firstOrNull()
                    if (first is WordLiteralNode && first.token.type == TokenType.QUOTED_WORD) {
                        names += first.token.text
                    }
                }
                "local" -> {
                    for (arg in stmt.args) {
                        if (arg is WordLiteralNode && arg.token.type == TokenType.QUOTED_WORD) {
                            names += arg.token.text
                        }
                    }
                }
                "for" -> {
                    val template = stmt.args.firstOrNull()
                    val counter = (template as? BlockExpressionNode)
                        ?.statements?.firstOrNull()
                        ?.let { it as? CommandNode }
                        ?.nameToken
                    if (counter != null) names += counter.text
                }
            }
            // Recurse into expression args to find bindings nested inside blocks
            for (arg in stmt.args) collectBindingsInExpression(arg, names)
        }
    }
}

private fun collectBindingsInExpression(expr: ExpressionNode, names: MutableSet<String>) {
    when (expr) {
        is BlockExpressionNode -> for (s in expr.statements) collectBindings(s, names)
        is BinaryOpNode -> {
            collectBindingsInExpression(expr.left, names)
            collectBindingsInExpression(expr.right, names)
        }
        is UnaryOpNode -> collectBindingsInExpression(expr.operand, names)
        is CallExpressionNode -> for (arg in expr.args) collectBindingsInExpression(arg, names)
        else -> Unit // leaves: NumberNode, VariableRefNode, WordLiteralNode, ArrayLiteralNode
    }
}
