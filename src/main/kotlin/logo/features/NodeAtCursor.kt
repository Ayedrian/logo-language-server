package logo.features

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

/**
 * Classified result of locating the AST node at a cursor position. Used by
 * go-to-declaration and hover so each feature can dispatch on the kind of
 * thing under the cursor without re-walking the tree.
 *
 *  - CommandName: cursor on the name token of a statement-position procedure call
 *  - CallName: cursor on the name token of an expression-position procedure call
 *  - ProcedureDefName: cursor on the name token of a "to"/".macro" header
 *  - ParamDecl: cursor on a VARIABLE token in a "to"/".macro" header's param list
 *  - VariableRef: cursor on a ":name" reference inside an expression
 */
sealed class NodeAtCursor {
    data class CommandName(val node: CommandNode) : NodeAtCursor()
    data class CallName(val node: CallExpressionNode) : NodeAtCursor()
    data class ProcedureDefName(val node: ProcedureDefNode) : NodeAtCursor()
    data class ParamDecl(val owner: ProcedureDefNode, val token: Token) : NodeAtCursor()
    data class VariableRef(val node: VariableRefNode) : NodeAtCursor()
}

/**
 * Walks the program and returns the innermost classifiable node whose token
 * covers the cursor at (line, char). Number / word / array literals and
 * operator/keyword tokens are deliberately not classified — features that
 * care about them can extend this walker later.
 */
fun nodeAtCursor(ast: ProgramNode, line: Int, char: Int): NodeAtCursor? =
    findInStatements(ast.statements, line, char)

private fun findInStatements(stmts: List<StatementNode>, line: Int, char: Int): NodeAtCursor? {
    for (stmt in stmts) {
        when (stmt) {
            is CommandNode -> findInCommand(stmt, line, char)?.let { return it }
            is ProcedureDefNode -> findInDef(stmt, line, char)?.let { return it }
        }
    }
    return null
}

private fun findInDef(def: ProcedureDefNode, line: Int, char: Int): NodeAtCursor? {
    if (def.nameToken.contains(line, char)) return NodeAtCursor.ProcedureDefName(def)
    for (p in def.params) {
        if (p.containsWithLeadingColon(line, char)) return NodeAtCursor.ParamDecl(def, p)
    }
    return findInStatements(def.body, line, char)
}

private fun findInCommand(cmd: CommandNode, line: Int, char: Int): NodeAtCursor? {
    if (cmd.nameToken.contains(line, char)) return NodeAtCursor.CommandName(cmd)
    for (arg in cmd.args) findInExpression(arg, line, char)?.let { return it }
    return null
}

private fun findInExpression(expr: ExpressionNode, line: Int, char: Int): NodeAtCursor? {
    return when (expr) {
        // VARIABLE token's char points at ':' and text excludes it, so the ":name" span is text.length + 1
        is VariableRefNode -> if (expr.token.containsWithLeadingColon(line, char)) NodeAtCursor.VariableRef(expr) else null
        is BlockExpressionNode -> findInStatements(expr.statements, line, char)
        is BinaryOpNode -> findInExpression(expr.left, line, char) ?: findInExpression(expr.right, line, char)
        is UnaryOpNode -> findInExpression(expr.operand, line, char)
        is CallExpressionNode -> {
            if (expr.nameToken.contains(line, char)) NodeAtCursor.CallName(expr)
            else expr.args.firstNotNullOfOrNull { findInExpression(it, line, char) }
        }
        // Array literals and number/word leaves carry no classifiable navigation target
        is ArrayLiteralNode -> null
        else -> null
    }
}

private fun Token.contains(line: Int, char: Int): Boolean =
    this.line == line && char >= this.char && char < this.char + this.text.length

// For VARIABLE tokens: char points at ':' but text excludes it, so the visual span is + 1
private fun Token.containsWithLeadingColon(line: Int, char: Int): Boolean =
    this.line == line && char >= this.char && char < this.char + this.text.length + 1
