package logo.features

import logo.analysis.SymbolTable
import logo.lexer.Token
import logo.lexer.TokenType
import logo.parser.ProgramNode

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
 * Resolution by node kind under the cursor:
 *  - parameter declaration in a "to" header: self-jump (return its own range)
 *  - procedure definition name in a "to"/".macro" header: self-jump
 *  - procedure call name (statement or expression): look up the user definition via proceduresByName
 *  - variable reference (:name in a body): look up the decl via varReferences
 * Cursor on a built-in call or on an unbound :name returns null.
 */
fun findDeclaration(ast: ProgramNode, symbolTable: SymbolTable, line: Int, char: Int): DeclarationTarget? {
    return when (val hit = nodeAtCursor(ast, line, char)) {
        is NodeAtCursor.ParamDecl -> DeclarationTarget(hit.token.toRange())
        is NodeAtCursor.ProcedureDefName -> DeclarationTarget(hit.node.nameToken.toRange())
        is NodeAtCursor.CommandName -> symbolTable.proceduresByName[hit.node.nameToken.text]
            ?.let { DeclarationTarget(it.nameToken.toRange()) }
        is NodeAtCursor.CallName -> symbolTable.proceduresByName[hit.node.nameToken.text]
            ?.let { DeclarationTarget(it.nameToken.toRange()) }
        is NodeAtCursor.VariableRef -> symbolTable.varReferences[hit.node.token]
            ?.let { DeclarationTarget(it.toRange()) }
        null -> null
    }
}

// VARIABLE / QUOTED_WORD tokens' char points at the leading ':' or '"' but text excludes it,
// so the on-screen span is text.length + 1.
private fun Token.toRange(): TokenRange {
    val extra = if (this.type == TokenType.VARIABLE || this.type == TokenType.QUOTED_WORD) 1 else 0
    return TokenRange(this.line, this.char, this.char + this.text.length + extra)
}
