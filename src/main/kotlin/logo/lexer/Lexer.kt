package logo.lexer

class Lexer(private val sourceCode: String) {
    private var position = 0 // total position within source code string
    private var line = 0
    private var column = 0 // offset within a line

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
     */
    private fun skipWhitespaceAndComments() {
        while (position < sourceCode.length) {
            when {
                sourceCode[position] == '\n' -> { line++; column = 0; position++ }
                sourceCode[position].isWhitespace() -> advance()
                sourceCode[position] == ';' -> while (position < sourceCode.length && sourceCode[position] != '\n') position++
                else -> return
            }
        }
    }

    /**
     * Reads the next token by either trying to read a word or a (potentially negative) number
     */
    private fun nextToken(): Token {
        val startLine = line
        val startColumn = column
        val char = sourceCode[position]
        return when {
            char.isLetter() -> readWord(startLine, startColumn)
            char.isDigit() -> readNumber(startLine, startColumn)
            // handle negative numbers that start with a -
            char == '-' && position + 1 < sourceCode.length && sourceCode[position + 1].isDigit() -> readNumber(startLine, startColumn)
            char == ':' && position + 1 < sourceCode.length && sourceCode[position + 1].isLetter() -> readVariable(startLine, startColumn)
            else -> { advance(); Token(TokenType.UNKNOWN, char.toString(), startLine, startColumn) }
        }
    }

    /**
     * Tries to read a word, currently words are started by a character and then more characters or underscores
     * TODO: LOGO is not actually this restrictive, technically a name can be anything that isn't an operator,
     * whitespace, brackets, quotes and braces, meaning that dots (e.g. .macro) and symbols like ?, !, @, #, %, ^ etc.
     * are allowed in names, we therefore need to loosen our definition of a word later on
     */
    private fun readWord(startLine: Int, startColumn: Int): Token {
        val start = position
        while (position < sourceCode.length && (sourceCode[position].isLetterOrDigit() || sourceCode[position] == '_')) advance()
        val text = sourceCode.substring(start, position).lowercase() // LOGO is case-insensitive, normalise to lowercase
        // if a name is not a recognized keyword, it has to be an identifier
        val type = if (text in KEYWORDS) TokenType.KEYWORD else TokenType.IDENTIFIER
        return Token(type, text, startLine, startColumn)
    }

    /**
     * Reads a ":name" variable reference, the leading ':' is consumed but not included in the token text
     */
    private fun readVariable(startLine: Int, startColumn: Int): Token {
        advance() // consume ':'
        val start = position
        while (position < sourceCode.length && (sourceCode[position].isLetterOrDigit() || sourceCode[position] == '_')) advance()
        val name = sourceCode.substring(start, position).lowercase()
        return Token(TokenType.VARIABLE, name, startLine, startColumn)
    }

    /**
     * Tries to read a number, supports negative numbers and both integers and floating point values
     */
    private fun readNumber(startLine: Int, startColumn: Int): Token {
        val start = position
        if (sourceCode[position] == '-') advance()
        while (position < sourceCode.length && sourceCode[position].isDigit()) advance()
        if (position < sourceCode.length && sourceCode[position] == '.') {
            advance()
            while (position < sourceCode.length && sourceCode[position].isDigit()) advance()
        }
        return Token(TokenType.NUMBER, sourceCode.substring(start, position), startLine, startColumn)
    }

    private fun advance() { column++; position++ }

    companion object {
        val KEYWORDS = setOf("to", "end")
    }
}
