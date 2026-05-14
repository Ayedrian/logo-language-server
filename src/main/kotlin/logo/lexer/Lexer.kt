package logo.lexer

class Lexer(private val sourceCode: String) {
    private var position = 0 // total position within source code string
    private var line = 0
    private var column = 0 // offset within a line
    // depth-sensitive context: inside '{...}' arrays, bare names lex as WORD (literal data)
    // rather than IDENTIFIER/KEYWORD (callable name). Nested braces increment / decrement.
    private var braceDepth = 0

    fun tokenise(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (position < sourceCode.length) {
            skipWhitespaceAndComments()
            if (position >= sourceCode.length) break
            tokens += nextToken()
        }
        tokens += Token(TokenType.EOF, "", line, column) // add EOF for parser
        return tokens
    }

    /**
     * Skip space characters and comments (that start with a semicolon)
     * For newlines reset column, increment line count and position
     * '~' at end-of-line is a line continuation — '~\n' is skipped as if not present
     */
    private fun skipWhitespaceAndComments() {
        while (position < sourceCode.length) {
            when {
                sourceCode[position] == '\n' -> { line++; column = 0; position++ }
                sourceCode[position].isWhitespace() -> advance()
                sourceCode[position] == ';' -> while (position < sourceCode.length && sourceCode[position] != '\n') position++
                sourceCode[position] == '~' && position + 1 < sourceCode.length && sourceCode[position + 1] == '\n' -> {
                    position += 2; line++; column = 0
                }
                else -> return
            }
        }
    }

    /**
     * Reads the next token by dispatching on the first character: words, numbers, variables,
     * quoted-word literals, brackets/parens/braces, operators, etc.
     */
    private fun nextToken(): Token {
        val startLine = line
        val startColumn = column
        val char = sourceCode[position]
        return when {
            char.isLetter() -> readWord(startLine, startColumn)
            // names can start with '.' if followed by a letter, e.g. ".macro"
            char == '.' && position + 1 < sourceCode.length && sourceCode[position + 1].isLetter() -> readWord(startLine, startColumn)
            char.isDigit() -> readNumber(startLine, startColumn)
            // '-' is always an operator now; unary minus / negation is the parser's job (slice 7)
            char == '-' -> { advance(); Token(TokenType.MINUS, "-", startLine, startColumn) }
            char == ':' && position + 1 < sourceCode.length && sourceCode[position + 1].isLetter() -> readVariable(startLine, startColumn)
            char == '"' -> readQuotedWord(startLine, startColumn)
            char == '[' -> { advance(); Token(TokenType.LBRACKET, "[", startLine, startColumn) }
            char == ']' -> { advance(); Token(TokenType.RBRACKET, "]", startLine, startColumn) }
            char == '{' -> { advance(); braceDepth++; Token(TokenType.LBRACE, "{", startLine, startColumn) }
            char == '}' -> {
                advance()
                if (braceDepth > 0) braceDepth--
                Token(TokenType.RBRACE, "}", startLine, startColumn)
            }
            char == '(' -> { advance(); Token(TokenType.LPAREN, "(", startLine, startColumn) }
            char == ')' -> { advance(); Token(TokenType.RPAREN, ")", startLine, startColumn) }
            char == '+' -> { advance(); Token(TokenType.PLUS, "+", startLine, startColumn) }
            char == '*' -> { advance(); Token(TokenType.STAR, "*", startLine, startColumn) }
            char == '/' -> { advance(); Token(TokenType.SLASH, "/", startLine, startColumn) }
            char == '=' -> { advance(); Token(TokenType.EQ, "=", startLine, startColumn) }
            char == '<' -> readLessThan(startLine, startColumn)
            char == '>' -> readGreaterThan(startLine, startColumn)
            // '`' is UCBLogo's backquote macro reader — a one-char procedure name
            char == '`' -> { advance(); Token(TokenType.IDENTIFIER, "`", startLine, startColumn) }
            else -> { advance(); Token(TokenType.UNKNOWN, char.toString(), startLine, startColumn) }
        }
    }

    /**
     * Reads a word: a sequence of name-continuation characters. Per UCBLogo, names can contain
     * '.', '?', '!' in addition to letters, digits and '_'. Operators, whitespace, brackets,
     * parens, braces, ':', '"', ';', '~' all terminate a name.
     * '\X' consumes both characters as part of the word; token text preserves the raw source span.
     */
    private fun readWord(startLine: Int, startColumn: Int): Token {
        val start = position
        readNameChars()
        val text = sourceCode.substring(start, position).lowercase() // LOGO is case-insensitive, normalise to lowercase
        // inside an array literal '{...}', bare names are literal data, not callable identifiers
        val type = when {
            braceDepth > 0 -> TokenType.WORD
            text in KEYWORDS -> TokenType.KEYWORD
            else -> TokenType.IDENTIFIER
        }
        return Token(type, text, startLine, startColumn)
    }

    /**
     * Shared loop for word- and variable-name continuation chars, honouring '\X' escapes
     */
    private fun readNameChars() {
        while (position < sourceCode.length) {
            val c = sourceCode[position]
            if (c == '\\' && position + 1 < sourceCode.length) { advance(); advance() }
            else if (isWordCont(c)) advance()
            else break
        }
    }

    private fun isWordCont(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '.' || c == '?' || c == '!'

    /**
     * Reads a "word literal: the leading '"' is consumed but excluded from the token text
     * (same convention as VARIABLE's leading ':'). Continues until a word-literal terminator
     * (whitespace, [], (), {}, ;, ~, " or another quote). Operators are NOT terminators —
     * "+ is the word "+".
     */
    private fun readQuotedWord(startLine: Int, startColumn: Int): Token {
        advance() // consume '"'
        val start = position
        while (position < sourceCode.length) {
            val c = sourceCode[position]
            if (c == '\\' && position + 1 < sourceCode.length) { advance(); advance() }
            else if (isQuotedWordTerminator(c)) break
            else advance()
        }
        val text = sourceCode.substring(start, position).lowercase()
        return Token(TokenType.QUOTED_WORD, text, startLine, startColumn)
    }

    private fun isQuotedWordTerminator(c: Char): Boolean =
        c.isWhitespace() || c == '[' || c == ']' || c == '(' || c == ')' ||
        c == '{' || c == '}' || c == ';' || c == '~' || c == '"'

    /**
     * Reads a ":name" variable reference, the leading ':' is consumed but not included in the token text
     * Variable names use the same name-continuation rule as procedure names (see isWordCont)
     */
    private fun readVariable(startLine: Int, startColumn: Int): Token {
        advance() // consume ':'
        val start = position
        readNameChars()
        val name = sourceCode.substring(start, position).lowercase()
        return Token(TokenType.VARIABLE, name, startLine, startColumn)
    }

    /**
     * Tries to read a number, supports both integers and floating point values
     * Sign is handled at the parser level via unary minus (slice 7)
     */
    private fun readNumber(startLine: Int, startColumn: Int): Token {
        val start = position
        while (position < sourceCode.length && sourceCode[position].isDigit()) advance()
        if (position < sourceCode.length && sourceCode[position] == '.') {
            advance()
            while (position < sourceCode.length && sourceCode[position].isDigit()) advance()
        }
        return Token(TokenType.NUMBER, sourceCode.substring(start, position), startLine, startColumn)
    }

    /**
     * Reads '<', '<=' or '<>' with one-char lookahead
     */
    private fun readLessThan(startLine: Int, startColumn: Int): Token {
        advance() // consume '<'
        return when {
            position < sourceCode.length && sourceCode[position] == '=' -> { advance(); Token(TokenType.LEQ, "<=", startLine, startColumn) }
            position < sourceCode.length && sourceCode[position] == '>' -> { advance(); Token(TokenType.NEQ, "<>", startLine, startColumn) }
            else -> Token(TokenType.LT, "<", startLine, startColumn)
        }
    }

    /**
     * Reads '>' or '>=' with one-char lookahead
     */
    private fun readGreaterThan(startLine: Int, startColumn: Int): Token {
        advance() // consume '>'
        return if (position < sourceCode.length && sourceCode[position] == '=') {
            advance(); Token(TokenType.GEQ, ">=", startLine, startColumn)
        } else {
            Token(TokenType.GT, ">", startLine, startColumn)
        }
    }

    private fun advance() { column++; position++ }

    companion object {
        val KEYWORDS = setOf("to", "end", ".macro")
    }
}
