package logo.parser

import logo.diagnostics.Diagnostic
import logo.lexer.Token
import logo.lexer.TokenType

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
     * A statement is either a procedure definition ("to ... end") or a procedure call.
     */
    private fun parseStatement(): StatementNode? {
        val tok = current()
        return when {
            tok.type == TokenType.KEYWORD && tok.text == "to" -> parseProcedureDef()
            tok.type == TokenType.IDENTIFIER -> parseCommand()
            else -> { pos++; null } // skip anything we don't handle yet
        }
    }

    /**
     * Parses "to <name> :p1 :p2 ... <body> end"
     * Body statements are parsed with the same logic as top-level statements
     * Important (error recovery): If there's no "end" at the end, we stop atEOF and
     * emit a diagnostic, and still return the partial definition
     */
    private fun parseProcedureDef(): ProcedureDefNode {
        val toToken = consume() // "to"
        if (isAtEnd() || current().type != TokenType.IDENTIFIER) {
            diagnostics += Diagnostic("Expected procedure name after 'to'", toToken.line, toToken.char, toToken.text.length)
            return ProcedureDefNode(toToken, toToken, emptyList(), emptyList(), null)
        }
        val nameToken = consume()

        val params = mutableListOf<Token>()
        while (!isAtEnd() && current().type == TokenType.VARIABLE) params += consume()

        val body = mutableListOf<StatementNode>()
        while (!isAtEnd() && !(current().type == TokenType.KEYWORD && current().text == "end")) {
            skipUnknown()
            if (isAtEnd() || (current().type == TokenType.KEYWORD && current().text == "end")) break
            parseStatement()?.let { body += it }
        }

        val endToken = if (isAtEnd()) {
            diagnostics += Diagnostic("Expected 'end' to close procedure '${nameToken.text}'", nameToken.line, nameToken.char, nameToken.text.length)
            null
        } else {
            consume()
        }
        return ProcedureDefNode(toToken, nameToken, params, body, endToken)
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
            TokenType.VARIABLE -> {
                consume()
                VariableRefNode(tok)
            }
            TokenType.LBRACKET -> parseBlock()
            else -> {
                // EOF has empty text; use length 1 so the LSP range is well-formed
                val len = if (tok.text.isEmpty()) 1 else tok.text.length
                diagnostics += Diagnostic("Expected expression", tok.line, tok.char, len)
                null
            }
        }
    }

    /**
     * Parses "[ <statements> ]" as a block expression. Statements inside a block use the same
     * parsing logic as top-level statements. On missing "]" (EOF reached), emits a diagnostic
     * spanning the opening "[" and returns a partial block with rbracket = null.
     */
    private fun parseBlock(): BlockExpressionNode {
        val lbracket = consume() // "["
        val statements = mutableListOf<StatementNode>()
        while (!isAtEnd() && current().type != TokenType.RBRACKET) {
            skipUnknown()
            if (isAtEnd() || current().type == TokenType.RBRACKET) break
            parseStatement()?.let { statements += it }
        }
        val rbracket = if (isAtEnd()) {
            diagnostics += Diagnostic("Expected ']' to close block", lbracket.line, lbracket.char, lbracket.text.length)
            null
        } else {
            consume()
        }
        return BlockExpressionNode(lbracket, statements, rbracket)
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
