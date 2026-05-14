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

    /**
     * "[" and "]" — delimit instruction lists passed as arguments to block-taking
     * primitives (repeat, if, ifelse, while, ...)
     */
    LBRACKET,
    RBRACKET,

    /**
     * "{" and "}" — delimit array literals. Inside braces, identifier-shaped tokens
     * become WORD (literal data) instead of IDENTIFIER (callable name).
     */
    LBRACE,
    RBRACE,

    /**
     * "(" and ")" — used for grouping in arithmetic and for variadic call form: ( name args* )
     */
    LPAREN,
    RPAREN,

    // arithmetic / comparison operators
    PLUS, MINUS, STAR, SLASH,
    EQ, LT, GT, LEQ, GEQ, NEQ,

    /**
     * a quoted-word literal "foo. The leading " is consumed but excluded from the token text
     * (same convention as VARIABLE's leading ':')
     */
    QUOTED_WORD,

    /**
     * a bare word inside an array literal { ... }. The lexer emits WORD instead of IDENTIFIER
     * when braceDepth > 0; lower-cased to match the convention used elsewhere.
     */
    WORD,

    EOF, // useful (parser doesn't need bounds check and can just keep consuming tokens until it discovers an EOF token)
    UNKNOWN, // unrecognised characters, we keep these for error recovery instead of ignoring them
}

data class Token(
    val type: TokenType,
    val text: String,
    val line: Int, // line in which the token sits, starts at 0
    val char: Int, // offset inside line, also starts at 0
)
