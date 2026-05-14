package logo.lexer

enum class TokenType {
    /**
     * keywords are just "to" and "end" (and technically .macro as well)
     * if, repeat etc. are not keywords but built-in procedures (so they go under IDENTIFIERS below)
     */
    KEYWORD,

    /**
     * procedure & macro definitions, built-in primitives (such as fd, bk...) and bare name expressions (procedure name passed as value)
     * the LOGO grammar doesn't distinguish between user defined procedures and built-in procedures
     */
    IDENTIFIER,

    NUMBER,

    /**
     * a variable reference or parameter declaration, written as ":name" in LOGO
     * the token's text holds the name without the leading colon
     */
    VARIABLE,

    EOF, // useful (parser doesn't need bounds check and can just keep consuming tokens until it discovers an EOF token)
    UNKNOWN, // unrecognised characters, we keep these for error recovery instead of ignoring them
}

data class Token(
    val type: TokenType,
    val text: String,
    val line: Int, // line in which the token sits, starts at 0
    val char: Int, // offset inside line, also starts at 0
)
