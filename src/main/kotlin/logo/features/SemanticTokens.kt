package logo.features

import logo.lexer.TokenType
import logo.parser.AstNode
import logo.parser.CommandNode
import logo.parser.NumberNode
import logo.parser.ProgramNode

/**
 * Token type legend — indices here must match the list advertised in
 * the server's SemanticTokensLegend.
 */
enum class SemanticTokenKind(val index: Int) {
    KEYWORD(0),
    FUNCTION(1),
    NUMBER(2),
}

data class RawSemanticToken(
    val line: Int,
    val char: Int,
    val length: Int,
    val kind: SemanticTokenKind,
)

fun collectSemanticTokens(ast: ProgramNode): List<RawSemanticToken> {
    val result = mutableListOf<RawSemanticToken>()
    for (stmt in ast.statements) visitStatement(stmt, result)
    return result.sortedWith(compareBy({ it.line }, { it.char }))
}

private fun visitStatement(node: AstNode, out: MutableList<RawSemanticToken>) {
    when (node) {
        is CommandNode -> {
            val tok = node.nameToken
            val kind = if (tok.type == TokenType.KEYWORD) SemanticTokenKind.KEYWORD else SemanticTokenKind.FUNCTION
            out += RawSemanticToken(tok.line, tok.char, tok.text.length, kind)
            for (arg in node.args) visitExpression(arg, out)
        }
        else -> Unit
    }
}

private fun visitExpression(node: AstNode, out: MutableList<RawSemanticToken>) {
    when (node) {
        is NumberNode -> {
            val tok = node.token
            out += RawSemanticToken(tok.line, tok.char, tok.text.length, SemanticTokenKind.NUMBER)
        }
        else -> Unit
    }
}

/**
 * Encodes the token list into LSP's delta-encoded integer array:
 * for each token: [deltaLine, deltaStartChar, length, tokenType, tokenModifiers]
 */
fun encodeSemanticTokens(tokens: List<RawSemanticToken>): List<Int> {
    val data = mutableListOf<Int>()
    var prevLine = 0
    var prevChar = 0
    for (tok in tokens) {
        val deltaLine = tok.line - prevLine
        val deltaChar = if (deltaLine == 0) tok.char - prevChar else tok.char
        data += deltaLine
        data += deltaChar
        data += tok.length
        data += tok.kind.index
        data += 0 // no modifiers
        prevLine = tok.line
        prevChar = tok.char
    }
    return data
}
