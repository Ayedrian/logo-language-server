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
    fun `tokenises negative number`() {
        val tokens = Lexer("bk -10").tokenise()
        assertEquals(TokenType.NUMBER, tokens[1].type)
        assertEquals("-10", tokens[1].text)
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
    fun `multiline input tracks line and column`() {
        val tokens = Lexer("print 1\nprint 2").tokenise()
        assertEquals(0, tokens[0].line) // first print
        assertEquals(1, tokens[2].line) // second print
        assertEquals(0, tokens[2].char)
    }
}
