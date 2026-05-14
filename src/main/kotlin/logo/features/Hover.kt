package logo.features

import logo.analysis.BUILTIN_ARITIES
import logo.analysis.SymbolTable
import logo.lexer.Token
import logo.lexer.TokenType
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

/**
 * Markdown-rendered hover content. The server layer wraps this in lsp4j's Hover.
 */
data class HoverResult(val markdown: String)

/**
 * Returns hover content for the cursor at (line, char), or null if there is
 * nothing useful to show.
 *
 * Cases handled:
 *  - procedure call name (user-defined or built-in) — shows signature / arity
 *  - procedure / macro definition name — shows its own signature
 *  - parameter declaration in a "to" / ".macro" header
 *  - variable reference resolved to a param, make/local/localmake binding, or for counter
 *  - "for" loop counter declaration in the template `[i ...]`
 * Returns null for unresolved refs (including dynamic-scoping ones), unknown call
 * names, leaves (numbers, words, arrays), and whitespace.
 */
fun findHover(ast: ProgramNode, symbolTable: SymbolTable, line: Int, char: Int): HoverResult? =
    when (val hit = nodeAtCursor(ast, line, char)) {
        is NodeAtCursor.CommandName -> hoverForCallName(hit.node.nameToken, symbolTable, ast)
        is NodeAtCursor.CallName -> hoverForCallName(hit.node.nameToken, symbolTable, ast)
        is NodeAtCursor.ProcedureDefName -> HoverResult(signature(hit.node))
        is NodeAtCursor.ParamDecl -> HoverResult(paramText(hit.token, hit.owner))
        is NodeAtCursor.VariableRef -> hoverForVarRef(hit.node, symbolTable, ast)
        null -> null
    }

private fun hoverForCallName(name: Token, table: SymbolTable, ast: ProgramNode): HoverResult? {
    table.proceduresByName[name.text]?.let { return HoverResult(signature(it)) }
    BUILTIN_ARITIES[name.text]?.let { return HoverResult("`${name.text}` (built-in, arity $it)") }
    // Parser wraps `for [i 1 10] [...]` with `i` as CommandNode("i", []) — nodeAtCursor
    // classifies a cursor on `i` as CommandName, so we compensate here rather than in the walker.
    if (isForCounterDecl(ast, name)) return HoverResult(forCounterText(name))
    return null
}

private fun hoverForVarRef(node: VariableRefNode, table: SymbolTable, ast: ProgramNode): HoverResult? {
    val decl = table.varReferences[node.token] ?: return null
    return when (decl.type) {
        TokenType.VARIABLE -> findOwningProcedure(ast, decl)?.let { HoverResult(paramText(decl, it)) }
        TokenType.QUOTED_WORD -> HoverResult("local variable `:${decl.text}`")
        TokenType.IDENTIFIER -> HoverResult(forCounterText(decl))
        else -> null
    }
}

private fun signature(def: ProcedureDefNode): String {
    val params = def.params.joinToString("") { " :${it.text}" }
    return "```logo\n${def.defToken.text} ${def.nameToken.text}${params}\n```"
}

private fun paramText(token: Token, owner: ProcedureDefNode): String =
    "parameter `:${token.text}` of procedure `${owner.nameToken.text}`"

private fun forCounterText(token: Token): String = "for-loop counter `:${token.text}`"

/**
 * Locates the top-level ProcedureDefNode whose params list contains [token].
 * Used to attach an owner name to a parameter hover regardless of where the ref sits.
 */
private fun findOwningProcedure(ast: ProgramNode, token: Token): ProcedureDefNode? {
    for (stmt in ast.statements) {
        if (stmt is ProcedureDefNode && token in stmt.params) return stmt
    }
    return null
}

private fun isForCounterDecl(ast: ProgramNode, token: Token): Boolean =
    collectForCounters(ast).any { it == token }

private fun collectForCounters(ast: ProgramNode): List<Token> {
    val out = mutableListOf<Token>()
    for (stmt in ast.statements) walkForCountersStmt(stmt, out)
    return out
}

private fun walkForCountersStmt(stmt: StatementNode, out: MutableList<Token>) {
    when (stmt) {
        is ProcedureDefNode -> for (s in stmt.body) walkForCountersStmt(s, out)
        is CommandNode -> {
            if (stmt.nameToken.text == "for") {
                val tpl = stmt.args.firstOrNull() as? BlockExpressionNode
                val counter = (tpl?.statements?.firstOrNull() as? CommandNode)?.nameToken
                if (counter != null) out += counter
            }
            for (arg in stmt.args) walkForCountersExpr(arg, out)
        }
    }
}

private fun walkForCountersExpr(expr: ExpressionNode, out: MutableList<Token>) {
    when (expr) {
        is BlockExpressionNode -> for (s in expr.statements) walkForCountersStmt(s, out)
        is BinaryOpNode -> { walkForCountersExpr(expr.left, out); walkForCountersExpr(expr.right, out) }
        is UnaryOpNode -> walkForCountersExpr(expr.operand, out)
        is CallExpressionNode -> for (a in expr.args) walkForCountersExpr(a, out)
        else -> Unit
    }
}
