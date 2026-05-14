package logo.parser

import logo.lexer.Token
import logo.lexer.TokenType

// A reported problem in the source code we can display
data class Diagnostic(val message: String, val line: Int, val char: Int)

class LogoParser(
    private val tokens: List<Token>,
    // combined arity table (built-in procedures merged with user-defined procedures from the first parser pass)
    private val arities: Map<String, Int>,
) {
    val diagnostics = mutableListOf<Diagnostic>()
    private var pos = 0

    /**
     * A program node is a list of statements
     */
    fun parse(): ProgramNode {
        val statements = mutableListOf<StatementNode>()
        while (!isAtEnd()) {
            skipUnknown() //
            if (isAtEnd()) break
            parseStatement()?.let { statements += it }
        }
        return ProgramNode(statements)
    }

    /**
     * Currently a statement can only be a procedure call
     * TODO: Parse more kinds of statements
     */
    private fun parseStatement(): StatementNode? {
        val tok = current()
        return when {
            tok.type == TokenType.IDENTIFIER -> parseCommand()
            else -> { pos++; null } // skip anything we don't handle yet
        }
    }

    /**
     * Parse procedure call using the arity of the procedure
     */
    private fun parseCommand(): CommandNode {
        val nameToken = consume()
        val arity = arities[nameToken.text] ?: 0
        val args = mutableListOf<ExpressionNode>()
        repeat(arity) {
            if (!isAtEnd()) parseExpression()?.let { args += it }
        }
        return CommandNode(nameToken, args)
    }

    /**
     * Parse an expression, currently only a number is supported
     * TODO: Support more types of expressions in LOGO
     */
    private fun parseExpression(): ExpressionNode? {
        val tok = current()
        return when (tok.type) {
            TokenType.NUMBER -> {
                consume()
                NumberNode(tok.text.toDouble(), tok)
            }
            else -> {
                diagnostics += Diagnostic("Expected expression", tok.line, tok.char)
                null
            }
        }
    }

    // Error recovery: skip unrecognised tokens before a statement boundary
    // TODO: emit a diagnostic? or have the lexer do this?
    private fun skipUnknown() {
        while (!isAtEnd() && current().type == TokenType.UNKNOWN) pos++
    }

    private fun consume(): Token = tokens[pos++]
    private fun current(): Token = tokens[pos]
    private fun isAtEnd(): Boolean = tokens[pos].type == TokenType.EOF
}
