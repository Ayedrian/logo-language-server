package logo.parser

import logo.analysis.BUILTIN_ARITIES
import logo.lexer.Lexer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParserTest {

    private fun parse(source: String): ProgramNode {
        val tokens = Lexer(source).tokenise()
        val scanner = FirstPassScanner(tokens).also { it.scan() }
        return LogoParser(tokens, BUILTIN_ARITIES + scanner.procedures).parse()
    }

    @Test
    fun `parses print 1`() {
        val program = parse("print 1")
        assertEquals(1, program.statements.size)
        val cmd = assertIs<CommandNode>(program.statements[0])
        assertEquals("print", cmd.nameToken.text)
        assertEquals(1, cmd.args.size)
        val num = assertIs<NumberNode>(cmd.args[0])
        assertEquals(1.0, num.value)
    }

    @Test
    fun `parses two commands`() {
        val program = parse("fd 10\nbk 5")
        assertEquals(2, program.statements.size)
    }

    @Test
    fun `unknown token is skipped without crashing`() {
        val program = parse("@ print 1")
        assertEquals(1, program.statements.size) // @ skipped, print 1 parsed
    }

    @Test
    fun `no diagnostics for valid input`() {
        val tokens = Lexer("print 1").tokenise()
        val parser = LogoParser(tokens, BUILTIN_ARITIES)
        parser.parse()
        assertEquals(0, parser.diagnostics.size)
    }

    @Test
    fun `parses procedure definition with one param and one body statement`() {
        val program = parse("to square :size fd 1 end")
        assertEquals(1, program.statements.size)
        val def = assertIs<ProcedureDefNode>(program.statements[0])
        assertEquals("square", def.nameToken.text)
        assertEquals(1, def.params.size)
        assertEquals("size", def.params[0].text)
        assertEquals(1, def.body.size)
        assertIs<CommandNode>(def.body[0])
    }

    @Test
    fun `variable reference in body parses to VariableRefNode`() {
        val program = parse("to square :size fd :size end")
        val def = assertIs<ProcedureDefNode>(program.statements[0])
        assertEquals(1, def.body.size)
        val cmd = assertIs<CommandNode>(def.body[0])
        assertEquals("fd", cmd.nameToken.text)
        assertEquals(1, cmd.args.size)
        val ref = assertIs<VariableRefNode>(cmd.args[0])
        assertEquals("size", ref.token.text)
    }

    @Test
    fun `missing end emits diagnostic and still returns partial def`() {
        val tokens = Lexer("to square :size fd 1").tokenise()
        val scanner = FirstPassScanner(tokens).also { it.scan() }
        val parser = LogoParser(tokens, BUILTIN_ARITIES + scanner.procedures)
        val program = parser.parse()
        assertEquals(1, program.statements.size)
        assertIs<ProcedureDefNode>(program.statements[0])
        assertEquals(1, parser.diagnostics.size)
        // Diagnostic should span the name token "square" so the editor highlights something meaningful
        val d = parser.diagnostics.single()
        assertEquals("square".length, d.length)
    }
}
