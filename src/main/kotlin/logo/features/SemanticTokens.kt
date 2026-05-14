package logo.features

import logo.lexer.TokenType
import logo.parser.ArrayLiteralNode
import logo.parser.AstNode
import logo.parser.BinaryOpNode
import logo.parser.BlockExpressionNode
import logo.parser.CallExpressionNode
import logo.parser.CommandNode
import logo.parser.NumberNode
import logo.parser.ProcedureDefNode
import logo.parser.ProgramNode
import logo.parser.UnaryOpNode
import logo.parser.VariableRefNode
import logo.parser.WordLiteralNode

/**
 * Token type legend — indices here must match the list advertised in
 * the server's SemanticTokensLegend.
 */
enum class SemanticTokenKind(val index: Int) {
    KEYWORD(0),
    FUNCTION(1),
    NUMBER(2),
    PARAMETER(3),
    STRING(4), // quoted-word literals ("foo)
    ARRAY(5),  // array literal braces and bare words inside them
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
        is ProcedureDefNode -> {
            val to = node.toToken
            out += RawSemanticToken(to.line, to.char, to.text.length, SemanticTokenKind.KEYWORD)
            val name = node.nameToken
            out += RawSemanticToken(name.line, name.char, name.text.length, SemanticTokenKind.FUNCTION)
            for (param in node.params) {
                // The VARIABLE token's text excludes the leading ':' but its char points at the ':',
                // so the highlight will cover the full ":name" (length = name + 1).
                out += RawSemanticToken(param.line, param.char, param.text.length + 1, SemanticTokenKind.PARAMETER)
            }
            for (stmt in node.body) visitStatement(stmt, out)
            node.endToken?.let { end ->
                out += RawSemanticToken(end.line, end.char, end.text.length, SemanticTokenKind.KEYWORD)
            }
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
        is VariableRefNode -> {
            // VARIABLE token's char points at ':' but text excludes it, so cover ":name" (length + 1)
            val tok = node.token
            out += RawSemanticToken(tok.line, tok.char, tok.text.length + 1, SemanticTokenKind.PARAMETER)
        }
        is BlockExpressionNode -> for (stmt in node.statements) visitStatement(stmt, out)
        is BinaryOpNode -> { visitExpression(node.left, out); visitExpression(node.right, out) }
        is UnaryOpNode -> visitExpression(node.operand, out)
        is CallExpressionNode -> {
            // a nested call in expression position highlights like any other procedure call
            val tok = node.nameToken
            out += RawSemanticToken(tok.line, tok.char, tok.text.length, SemanticTokenKind.FUNCTION)
            for (arg in node.args) visitExpression(arg, out)
        }
        is WordLiteralNode -> {
            val tok = node.token
            when (tok.type) {
                // QUOTED_WORD: char points at '"' but text excludes it, cover '"foo' (length + 1)
                TokenType.QUOTED_WORD -> out += RawSemanticToken(tok.line, tok.char, tok.text.length + 1, SemanticTokenKind.STRING)
                // WORD: bare name inside an array literal — char and text both refer to the word itself
                TokenType.WORD -> out += RawSemanticToken(tok.line, tok.char, tok.text.length, SemanticTokenKind.ARRAY)
                else -> Unit
            }
        }
        is ArrayLiteralNode -> {
            // braces themselves are highlighted as array; inner elements emit their own kinds
            val lb = node.lbrace
            out += RawSemanticToken(lb.line, lb.char, lb.text.length, SemanticTokenKind.ARRAY)
            for (el in node.elements) visitExpression(el, out)
            node.rbrace?.let { rb ->
                out += RawSemanticToken(rb.line, rb.char, rb.text.length, SemanticTokenKind.ARRAY)
            }
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
