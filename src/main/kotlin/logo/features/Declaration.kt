package logo.features

import logo.analysis.SymbolTable
import logo.lexer.Token
import logo.lexer.TokenType
import logo.parser.AstNode
import logo.parser.BlockExpressionNode
import logo.parser.CommandNode
import logo.parser.ExpressionNode
import logo.parser.ProcedureDefNode
import logo.parser.ProgramNode
import logo.parser.StatementNode
import logo.parser.VariableRefNode

/**
 * A source range in LSP terms: half-open, zero-based
 * Mirrors lsp4j's range/position without depending on lsp4j in the feature layer
 */
data class TokenRange(val line: Int, val startChar: Int, val endChar: Int)

/**
 * Result of a go-to-declaration lookup, in editor-agnostic form
 * The server layer converts this into an LSP location
 */
data class DeclarationTarget(val range: TokenRange)

/**
 * Finds the declaration target for the cursor at (line, char), if there is any.
 *
 * Resolution order:
 *  - cursor on a parameter declaration token in a "to" header: self-jump (return its own range)
 *  - cursor on a procedure call name: look up the user definition via proceduresByName
 *  - cursor on a variable reference (:name in a body): look up the param decl via varReferences
 * Cursor on a built-in call or on an unbound :name returns null.
 */
fun findDeclaration(ast: ProgramNode, symbolTable: SymbolTable, line: Int, char: Int): DeclarationTarget? {
    paramDeclAt(ast.statements, line, char)?.let { return DeclarationTarget(it.toRange()) }

    return when (val node = findNodeAt(ast.statements, line, char)) {
        is CommandNode -> symbolTable.proceduresByName[node.nameToken.text]
            ?.let { DeclarationTarget(it.nameToken.toRange()) }
        is VariableRefNode -> symbolTable.varReferences[node.token]
            ?.let { DeclarationTarget(it.toRange()) }
        else -> null
    }
}

/**
 * Walks statements and returns the innermost AST node whose own token covers the cursor:
 *  - a CommandNode if the cursor is on its name token
 *  - a VariableRefNode if the cursor is on its :name token (inside a command's args)
 */
private fun findNodeAt(statements: List<StatementNode>, line: Int, char: Int): AstNode? {
    for (stmt in statements) {
        when (stmt) {
            is CommandNode -> {
                findInCommand(stmt, line, char)?.let { return it }
            }
            is ProcedureDefNode -> {
                findNodeAt(stmt.body, line, char)?.let { return it }
            }
        }
    }
    return null
}

private fun findInCommand(cmd: CommandNode, line: Int, char: Int): AstNode? {
    if (cmd.nameToken.contains(line, char)) return cmd
    for (arg in cmd.args) findInExpression(arg, line, char)?.let { return it }
    return null
}

private fun findInExpression(expr: ExpressionNode, line: Int, char: Int): AstNode? {
    return when (expr) {
        // VARIABLE token's char points at ':' and text excludes it, so the ":name" span is text.length + 1
        is VariableRefNode -> if (expr.token.containsWithLeadingColon(line, char)) expr else null
        is BlockExpressionNode -> findNodeAt(expr.statements, line, char)
        else -> null
    }
}

/**
 * Returns the parameter declaration token under the cursor, if any, by scanning each
 * ProcedureDefNode's header parameters, used to make clicking a ":size" in a "to" header
 * jump to itself rather than returning no result.
 */
private fun paramDeclAt(statements: List<StatementNode>, line: Int, char: Int): Token? {
    for (stmt in statements) {
        if (stmt is ProcedureDefNode) {
            for (p in stmt.params) if (p.containsWithLeadingColon(line, char)) return p
        }
    }
    return null
}

private fun Token.contains(line: Int, char: Int): Boolean =
    this.line == line && char >= this.char && char < this.char + this.text.length

// For VARIABLE tokens: char points at ':' but text excludes it, so the visual span is + 1
private fun Token.containsWithLeadingColon(line: Int, char: Int): Boolean =
    this.line == line && char >= this.char && char < this.char + this.text.length + 1

// For VARIABLE tokens the on-screen span is ":name" (text.length + 1) because char points at ':'.
private fun Token.toRange(): TokenRange {
    val extra = if (this.type == TokenType.VARIABLE) 1 else 0
    return TokenRange(this.line, this.char, this.char + this.text.length + extra)
}
