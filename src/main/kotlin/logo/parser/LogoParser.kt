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
        return CommandNode(nameToken, parseCallArgs(nameToken))
    }

    /**
     * Reads the arity-many argument expressions following a call's name token. Shared by
     * parseCommand (statement context) and parsePrimary (expression context — "print sum 3 4").
     * Arity is looked up via the merged built-in + user-defined arity table; unknown names
     * default to 0 args.
     */
    private fun parseCallArgs(nameToken: Token): List<ExpressionNode> {
        val arity = arities[nameToken.text] ?: 0
        val args = mutableListOf<ExpressionNode>()
        repeat(arity) {
            if (!isAtEnd()) parseExpression()?.let { args += it }
        }
        return args
    }

    /**
     * Expression entry point. Implemented as a precedence-climbing recursive descent:
     *   comparison ( < > = <= >= <> )  →  additive ( + - )  →  multiplicative ( * / )
     *     →  unary ( prefix - )  →  primary
     * All binary levels are left-associative; comparisons do not chain Python-style (a < b < c
     * parses as (a < b) < c, the same as additive). Block expressions [ ... ] and array
     * literals { ... } only appear at primary level. Parens '(' expr ')' are transparent —
     * no ParenExpressionNode, the inner expression is returned directly.
     */
    private fun parseExpression(): ExpressionNode? = parseComparison()

    private fun parseComparison(): ExpressionNode? {
        var left = parseAdditive() ?: return null
        while (!isAtEnd() && isComparisonOp(current().type)) {
            val op = consume()
            val right = parseAdditive() ?: return left
            left = BinaryOpNode(op, left, right)
        }
        return left
    }

    private fun isComparisonOp(t: TokenType): Boolean =
        t == TokenType.LT || t == TokenType.GT || t == TokenType.EQ ||
            t == TokenType.LEQ || t == TokenType.GEQ || t == TokenType.NEQ

    private fun parseAdditive(): ExpressionNode? {
        var left = parseMultiplicative() ?: return null
        while (!isAtEnd() && (current().type == TokenType.PLUS || current().type == TokenType.MINUS)) {
            val op = consume()
            val right = parseMultiplicative() ?: return left
            left = BinaryOpNode(op, left, right)
        }
        return left
    }

    private fun parseMultiplicative(): ExpressionNode? {
        var left = parseUnary() ?: return null
        while (!isAtEnd() && (current().type == TokenType.STAR || current().type == TokenType.SLASH)) {
            val op = consume()
            val right = parseUnary() ?: return left
            left = BinaryOpNode(op, left, right)
        }
        return left
    }

    private fun parseUnary(): ExpressionNode? {
        if (!isAtEnd() && current().type == TokenType.MINUS) {
            val op = consume()
            val operand = parseUnary() ?: return null
            return UnaryOpNode(op, operand)
        }
        return parsePrimary()
    }

    private fun parsePrimary(): ExpressionNode? {
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
            TokenType.QUOTED_WORD -> {
                consume()
                WordLiteralNode(tok)
            }
            // a bare IDENTIFIER in expression position is a value-producing procedure call.
            // Arity comes from the same table parseCommand uses, so "print sum 3 4" parses as
            // print (sum 3 4). Unknown names default to arity 0.
            TokenType.IDENTIFIER -> {
                val nameToken = consume()
                CallExpressionNode(nameToken, parseCallArgs(nameToken))
            }
            TokenType.LBRACKET -> parseBlock()
            TokenType.LBRACE -> parseArrayLiteral()
            TokenType.LPAREN -> parseParenExpression()
            else -> {
                // EOF has empty text; use length 1 so the LSP range is well-formed
                val len = if (tok.text.isEmpty()) 1 else tok.text.length
                diagnostics += Diagnostic("Expected expression", tok.line, tok.char, len)
                null
            }
        }
    }

    /**
     * Parses "{ <element>* }" as an array literal. Elements are NUMBER, WORD, or nested
     * array literals — instruction lists are not allowed inside arrays (arrays hold data,
     * not code). Unrecognised tokens inside an array are skipped silently for now.
     * On missing "}" (EOF reached), emits a diagnostic spanning the opening "{" and returns
     * a partial array with rbrace = null.
     */
    private fun parseArrayLiteral(): ArrayLiteralNode {
        val lbrace = consume() // "{"
        val elements = mutableListOf<ExpressionNode>()
        while (!isAtEnd() && current().type != TokenType.RBRACE) {
            val tok = current()
            when (tok.type) {
                TokenType.NUMBER -> { consume(); elements += NumberNode(tok.text.toDouble(), tok) }
                TokenType.WORD -> { consume(); elements += WordLiteralNode(tok) }
                TokenType.LBRACE -> elements += parseArrayLiteral()
                else -> pos++ // skip unrecognised token; arrays are permissive
            }
        }
        val rbrace = if (isAtEnd()) {
            diagnostics += Diagnostic("Expected '}' to close array", lbrace.line, lbrace.char, lbrace.text.length)
            null
        } else {
            consume()
        }
        return ArrayLiteralNode(lbrace, elements, rbrace)
    }

    /**
     * Parses '( expr )'. Parens are transparent — the inner expression is returned directly.
     * On missing ')' (EOF reached), emits a diagnostic spanning the opening '(' and returns
     * the partial inner expression. Variadic call form ( name args* ) is deferred to slice 9.
     */
    private fun parseParenExpression(): ExpressionNode? {
        val lparen = consume() // "("
        val inner = parseExpression()
        if (isAtEnd() || current().type != TokenType.RPAREN) {
            diagnostics += Diagnostic("Expected ')' to close expression", lparen.line, lparen.char, lparen.text.length)
        } else {
            consume() // ")"
        }
        return inner
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
