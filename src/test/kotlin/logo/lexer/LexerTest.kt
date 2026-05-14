package logo.lexer

import kotlin.test.Test
import kotlin.test.assertEquals

class LexerTest {

    @Test
    fun `tokenises print 1`() {
        val tokens = Lexer("print 1").tokenise()
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("print", tokens[0].text)
        assertEquals(0, tokens[0].line)
        assertEquals(0, tokens[0].char)

        assertEquals(TokenType.NUMBER, tokens[1].type)
        assertEquals("1", tokens[1].text)
        assertEquals(0, tokens[1].line)
        assertEquals(6, tokens[1].char)

        assertEquals(TokenType.EOF, tokens[2].type)
    }

    @Test
    fun `keywords to and end are KEYWORD type`() {
        val tokens = Lexer("to end").tokenise()
        assertEquals(TokenType.KEYWORD, tokens[0].type)
        assertEquals(TokenType.KEYWORD, tokens[1].type)
    }

    @Test
    fun `'-' lexes as MINUS operator (unary handled at parse time)`() {
        val tokens = Lexer("bk -10").tokenise()
        assertEquals(TokenType.MINUS, tokens[1].type)
        assertEquals("-", tokens[1].text)
        assertEquals(TokenType.NUMBER, tokens[2].type)
        assertEquals("10", tokens[2].text)
    }

    @Test
    fun `line comment is skipped`() {
        val tokens = Lexer("print 1 ; this is a comment").tokenise()
        // IDENTIFIER, NUMBER, EOF
        assertEquals(3, tokens.size)
        assertEquals(TokenType.EOF, tokens[2].type)
    }

    @Test
    fun `unknown character produces UNKNOWN token`() {
        val tokens = Lexer("@").tokenise()
        assertEquals(TokenType.UNKNOWN, tokens[0].type)
        assertEquals("@", tokens[0].text)
    }

    @Test
    fun `colon name produces VARIABLE token without leading colon`() {
        val tokens = Lexer(":size").tokenise()
        assertEquals(TokenType.VARIABLE, tokens[0].type)
        assertEquals("size", tokens[0].text)
        assertEquals(0, tokens[0].char)
    }

    @Test
    fun `single-char operators and parens lex to their own tokens`() {
        val tokens = Lexer("+ * / = ( )").tokenise()
        assertEquals(TokenType.PLUS, tokens[0].type)
        assertEquals(TokenType.STAR, tokens[1].type)
        assertEquals(TokenType.SLASH, tokens[2].type)
        assertEquals(TokenType.EQ, tokens[3].type)
        assertEquals(TokenType.LPAREN, tokens[4].type)
        assertEquals(TokenType.RPAREN, tokens[5].type)
    }

    @Test
    fun `quoted word strips leading quote and includes operators`() {
        val tokens = Lexer("\"foo \"+ \"").tokenise()
        assertEquals(TokenType.QUOTED_WORD, tokens[0].type)
        assertEquals("foo", tokens[0].text)
        assertEquals(TokenType.QUOTED_WORD, tokens[1].type)
        assertEquals("+", tokens[1].text)
        assertEquals(TokenType.QUOTED_WORD, tokens[2].type)
        assertEquals("", tokens[2].text) // bare " at EOF
    }

    @Test
    fun `bare names inside braces lex as WORD`() {
        // { red green to } -> LBRACE, WORD("red"), WORD("green"), WORD("to"), RBRACE
        // 'to' is normally KEYWORD but inside an array it's literal data
        val tokens = Lexer("{ red green to }").tokenise()
        assertEquals(TokenType.LBRACE, tokens[0].type)
        assertEquals(TokenType.WORD, tokens[1].type)
        assertEquals("red", tokens[1].text)
        assertEquals(TokenType.WORD, tokens[2].type)
        assertEquals(TokenType.WORD, tokens[3].type)
        assertEquals("to", tokens[3].text)
        assertEquals(TokenType.RBRACE, tokens[4].type)
    }

    @Test
    fun `nested braces track depth, outer context restored after closing`() {
        // { a { b } c } foo -> WORD a, WORD b inside inner, WORD c, then IDENTIFIER foo outside
        val tokens = Lexer("{ a { b } c } foo").tokenise()
        assertEquals(TokenType.WORD, tokens[1].type) // a
        assertEquals(TokenType.LBRACE, tokens[2].type)
        assertEquals(TokenType.WORD, tokens[3].type) // b
        assertEquals(TokenType.RBRACE, tokens[4].type)
        assertEquals(TokenType.WORD, tokens[5].type) // c, still inside outer brace
        assertEquals(TokenType.RBRACE, tokens[6].type)
        assertEquals(TokenType.IDENTIFIER, tokens[7].type) // foo, back to outer context
    }

    @Test
    fun `backslash escape lets a normally-terminating char appear in a quoted word`() {
        // "a\;b is the quoted word a\;b (raw source span preserved)
        val tokens = Lexer("\"a\\;b").tokenise()
        assertEquals(TokenType.QUOTED_WORD, tokens[0].type)
        assertEquals("a\\;b", tokens[0].text)
    }

    @Test
    fun `tilde at end of line is a line continuation`() {
        val tokens = Lexer("fd 1 ~\nbk 2").tokenise()
        // fd, 1, bk, 2, EOF
        assertEquals(5, tokens.size)
        assertEquals("fd", tokens[0].text)
        assertEquals("1", tokens[1].text)
        // 'bk' is on line 1 because '~\n' incremented line, but parsing continues seamlessly
        assertEquals("bk", tokens[2].text)
        assertEquals(1, tokens[2].line)
        assertEquals("2", tokens[3].text)
    }

    @Test
    fun `extended name characters dot question bang are part of word`() {
        val tokens = Lexer("do.while equal? print!").tokenise()
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("do.while", tokens[0].text)
        assertEquals(TokenType.IDENTIFIER, tokens[1].type)
        assertEquals("equal?", tokens[1].text)
        assertEquals(TokenType.IDENTIFIER, tokens[2].type)
        assertEquals("print!", tokens[2].text)
    }

    @Test
    fun `dot-macro lexes as KEYWORD`() {
        val tokens = Lexer(".macro foo").tokenise()
        assertEquals(TokenType.KEYWORD, tokens[0].type)
        assertEquals(".macro", tokens[0].text)
        assertEquals(TokenType.IDENTIFIER, tokens[1].type)
        assertEquals("foo", tokens[1].text)
    }

    @Test
    fun `comparison operators including digraphs lex correctly`() {
        val tokens = Lexer("< > <= >= <>").tokenise()
        assertEquals(TokenType.LT, tokens[0].type)
        assertEquals(TokenType.GT, tokens[1].type)
        assertEquals(TokenType.LEQ, tokens[2].type)
        assertEquals("<=", tokens[2].text)
        assertEquals(TokenType.GEQ, tokens[3].type)
        assertEquals(">=", tokens[3].text)
        assertEquals(TokenType.NEQ, tokens[4].type)
        assertEquals("<>", tokens[4].text)
    }

    @Test
    fun `digraph lookahead is greedy left-to-right`() {
        // "<==" should be "<=" then "="
        val tokens = Lexer("<==").tokenise()
        assertEquals(TokenType.LEQ, tokens[0].type)
        assertEquals(TokenType.EQ, tokens[1].type)
    }

    @Test
    fun `brackets produce LBRACKET and RBRACKET tokens`() {
        val tokens = Lexer("[ fd 1 ]").tokenise()
        assertEquals(TokenType.LBRACKET, tokens[0].type)
        assertEquals("[", tokens[0].text)
        assertEquals(0, tokens[0].char)
        assertEquals(TokenType.IDENTIFIER, tokens[1].type)
        assertEquals(TokenType.NUMBER, tokens[2].type)
        assertEquals(TokenType.RBRACKET, tokens[3].type)
        assertEquals("]", tokens[3].text)
        assertEquals(7, tokens[3].char)
    }

    @Test
    fun `multiline input tracks line and column`() {
        val tokens = Lexer("print 1\nprint 2").tokenise()
        assertEquals(0, tokens[0].line) // first print
        assertEquals(1, tokens[2].line) // second print
        assertEquals(0, tokens[2].char)
    }

    @Test
    fun `backtick lexes as IDENTIFIER`() {
        // UCBLogo's backquote macro reader is a one-char procedure name.
        val tokens = Lexer("` foo").tokenise()
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("`", tokens[0].text)
        assertEquals(0, tokens[0].char)
        assertEquals(TokenType.IDENTIFIER, tokens[1].type)
        assertEquals("foo", tokens[1].text)
    }
}
