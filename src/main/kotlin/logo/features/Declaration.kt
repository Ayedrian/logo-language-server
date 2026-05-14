package logo.features

import logo.analysis.SymbolTable
import logo.lexer.Token
import logo.parser.CommandNode
import logo.parser.ProcedureDefNode
import logo.parser.ProgramNode
import logo.parser.StatementNode

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
 * Finds the declaration target for the cursor at (line, char), if there is any
 *
 * Walks the AST looking for a CommandNode whose name token covers the cursor, then resolves
 * that name through the symbol table to its ProcedureDefNode, built-in calls in LOGO have no
 * user-defined declaration (since they are built-in) and return null
 */
fun findDeclaration(ast: ProgramNode, symbolTable: SymbolTable, line: Int, char: Int): DeclarationTarget? {
    val call = findCommandAt(ast.statements, line, char) ?: return null
    val def = symbolTable.proceduresByName[call.nameToken.text] ?: return null
    return DeclarationTarget(def.nameToken.toRange())
}

private fun findCommandAt(statements: List<StatementNode>, line: Int, char: Int): CommandNode? {
    for (stmt in statements) {
        when (stmt) {
            is CommandNode -> if (stmt.nameToken.contains(line, char)) return stmt
            is ProcedureDefNode -> findCommandAt(stmt.body, line, char)?.let { return it }
        }
    }
    return null
}

private fun Token.contains(line: Int, char: Int): Boolean =
    this.line == line && char >= this.char && char < this.char + this.text.length

private fun Token.toRange(): TokenRange =
    TokenRange(this.line, this.char, this.char + this.text.length)
