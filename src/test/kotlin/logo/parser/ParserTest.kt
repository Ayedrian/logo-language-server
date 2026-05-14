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
    fun `parses repeat with bracketed block`() {
        val program = parse("repeat 4 [ fd 10 ]")
        assertEquals(1, program.statements.size)
        val cmd = assertIs<CommandNode>(program.statements[0])
        assertEquals("repeat", cmd.nameToken.text)
        assertEquals(2, cmd.args.size)
        assertIs<NumberNode>(cmd.args[0])
        val block = assertIs<BlockExpressionNode>(cmd.args[1])
        assertEquals(1, block.statements.size)
        val inner = assertIs<CommandNode>(block.statements[0])
        assertEquals("fd", inner.nameToken.text)
    }

    @Test
    fun `parses empty block`() {
        val program = parse("repeat 4 [ ]")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val block = assertIs<BlockExpressionNode>(cmd.args[1])
        assertEquals(0, block.statements.size)
    }

    @Test
    fun `missing closing bracket emits diagnostic and still returns partial block`() {
        val tokens = Lexer("repeat 4 [ fd 10").tokenise()
        val scanner = FirstPassScanner(tokens).also { it.scan() }
        val parser = LogoParser(tokens, BUILTIN_ARITIES + scanner.procedures)
        val program = parser.parse()
        val cmd = assertIs<CommandNode>(program.statements[0])
        val block = assertIs<BlockExpressionNode>(cmd.args[1])
        assertEquals(1, block.statements.size)
        assertEquals(null, block.rbracket)
        val d = parser.diagnostics.single()
        assertEquals(1, d.length) // spans the "[" token
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

    @Test
    fun `unary minus on number restores 'bk -10' behavior`() {
        val tokens = Lexer("bk -10").tokenise()
        val scanner = FirstPassScanner(tokens).also { it.scan() }
        val parser = LogoParser(tokens, BUILTIN_ARITIES + scanner.procedures)
        val program = parser.parse()
        val cmd = assertIs<CommandNode>(program.statements[0])
        assertEquals("bk", cmd.nameToken.text)
        assertEquals(1, cmd.args.size)
        val neg = assertIs<UnaryOpNode>(cmd.args[0])
        assertEquals("-", neg.op.text)
        val num = assertIs<NumberNode>(neg.operand)
        assertEquals(10.0, num.value)
        assertEquals(0, parser.diagnostics.size)
    }

    @Test
    fun `binary plus parses as BinaryOpNode`() {
        val program = parse("print 1 + 2")
        val cmd = assertIs<CommandNode>(program.statements[0])
        assertEquals(1, cmd.args.size)
        val bin = assertIs<BinaryOpNode>(cmd.args[0])
        assertEquals("+", bin.op.text)
        assertEquals(1.0, assertIs<NumberNode>(bin.left).value)
        assertEquals(2.0, assertIs<NumberNode>(bin.right).value)
    }

    @Test
    fun `multiplicative has higher precedence than additive`() {
        // 1 + 2 * 3 → (+ 1 (* 2 3))
        val program = parse("print 1 + 2 * 3")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val outer = assertIs<BinaryOpNode>(cmd.args[0])
        assertEquals("+", outer.op.text)
        assertEquals(1.0, assertIs<NumberNode>(outer.left).value)
        val inner = assertIs<BinaryOpNode>(outer.right)
        assertEquals("*", inner.op.text)
        assertEquals(2.0, assertIs<NumberNode>(inner.left).value)
        assertEquals(3.0, assertIs<NumberNode>(inner.right).value)
    }

    @Test
    fun `additive is left-associative`() {
        // 1 - 2 - 3 → (- (- 1 2) 3)
        val program = parse("print 1 - 2 - 3")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val outer = assertIs<BinaryOpNode>(cmd.args[0])
        assertEquals("-", outer.op.text)
        assertEquals(3.0, assertIs<NumberNode>(outer.right).value)
        val inner = assertIs<BinaryOpNode>(outer.left)
        assertEquals("-", inner.op.text)
        assertEquals(1.0, assertIs<NumberNode>(inner.left).value)
        assertEquals(2.0, assertIs<NumberNode>(inner.right).value)
    }

    @Test
    fun `parens override precedence`() {
        // (1 + 2) * 3 → (* (+ 1 2) 3)
        val program = parse("print (1 + 2) * 3")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val outer = assertIs<BinaryOpNode>(cmd.args[0])
        assertEquals("*", outer.op.text)
        val inner = assertIs<BinaryOpNode>(outer.left)
        assertEquals("+", inner.op.text)
        assertEquals(3.0, assertIs<NumberNode>(outer.right).value)
    }

    @Test
    fun `double unary minus nests`() {
        // bk - -10 → bk [ -(-10) ]
        val tokens = Lexer("bk - -10").tokenise()
        val scanner = FirstPassScanner(tokens).also { it.scan() }
        val parser = LogoParser(tokens, BUILTIN_ARITIES + scanner.procedures)
        val program = parser.parse()
        val cmd = assertIs<CommandNode>(program.statements[0])
        val outer = assertIs<UnaryOpNode>(cmd.args[0])
        val inner = assertIs<UnaryOpNode>(outer.operand)
        assertEquals(10.0, assertIs<NumberNode>(inner.operand).value)
        assertEquals(0, parser.diagnostics.size)
    }

    @Test
    fun `unary minus on variable parses`() {
        // fd -:size  →  fd [ -(:size) ]
        val program = parse("to f :size fd -:size end")
        val def = assertIs<ProcedureDefNode>(program.statements[0])
        val cmd = assertIs<CommandNode>(def.body[0])
        val neg = assertIs<UnaryOpNode>(cmd.args[0])
        val ref = assertIs<VariableRefNode>(neg.operand)
        assertEquals("size", ref.token.text)
    }

    @Test
    fun `variable reference inside arithmetic parses`() {
        // to f :x fd :x + 1 end
        val program = parse("to f :x fd :x + 1 end")
        val def = assertIs<ProcedureDefNode>(program.statements[0])
        val cmd = assertIs<CommandNode>(def.body[0])
        val bin = assertIs<BinaryOpNode>(cmd.args[0])
        assertEquals("x", assertIs<VariableRefNode>(bin.left).token.text)
        assertEquals(1.0, assertIs<NumberNode>(bin.right).value)
    }

    @Test
    fun `comparison has lower precedence than additive`() {
        // 1 + 2 < 3 + 4 → (< (+ 1 2) (+ 3 4))
        val program = parse("print 1 + 2 < 3 + 4")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val outer = assertIs<BinaryOpNode>(cmd.args[0])
        assertEquals("<", outer.op.text)
        val left = assertIs<BinaryOpNode>(outer.left)
        assertEquals("+", left.op.text)
        val right = assertIs<BinaryOpNode>(outer.right)
        assertEquals("+", right.op.text)
    }

    @Test
    fun `comparison is left-associative (no Python-style chaining)`() {
        // 1 < 2 < 3 → (< (< 1 2) 3)
        val program = parse("print 1 < 2 < 3")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val outer = assertIs<BinaryOpNode>(cmd.args[0])
        assertEquals("<", outer.op.text)
        val inner = assertIs<BinaryOpNode>(outer.left)
        assertEquals("<", inner.op.text)
        assertEquals(3.0, assertIs<NumberNode>(outer.right).value)
    }

    @Test
    fun `quoted word parses as WordLiteralNode in expression position`() {
        val program = parse("print \"hello")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val word = assertIs<WordLiteralNode>(cmd.args[0])
        assertEquals("hello", word.token.text)
    }

    @Test
    fun `nested procedure call parses as CallExpressionNode`() {
        // print sum 3 4 → print (sum 3 4)
        val program = parse("print sum 3 4")
        val cmd = assertIs<CommandNode>(program.statements[0])
        assertEquals("print", cmd.nameToken.text)
        val call = assertIs<CallExpressionNode>(cmd.args[0])
        assertEquals("sum", call.nameToken.text)
        assertEquals(2, call.args.size)
        assertEquals(3.0, assertIs<NumberNode>(call.args[0]).value)
        assertEquals(4.0, assertIs<NumberNode>(call.args[1]).value)
    }

    @Test
    fun `nested call's second arg consumes following arithmetic`() {
        // print sum 1 2 + 3 → print (sum 1 (2+3))
        val program = parse("print sum 1 2 + 3")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val call = assertIs<CallExpressionNode>(cmd.args[0])
        assertEquals(1.0, assertIs<NumberNode>(call.args[0]).value)
        val bin = assertIs<BinaryOpNode>(call.args[1])
        assertEquals("+", bin.op.text)
    }

    @Test
    fun `array literal parses with mixed numbers and words`() {
        // print { 1 red 2 } — '1' and '2' are NumberNode, 'red' is WordLiteralNode (WORD)
        val program = parse("print { 1 red 2 }")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val arr = assertIs<ArrayLiteralNode>(cmd.args[0])
        assertEquals(3, arr.elements.size)
        assertEquals(1.0, assertIs<NumberNode>(arr.elements[0]).value)
        val word = assertIs<WordLiteralNode>(arr.elements[1])
        assertEquals("red", word.token.text)
        assertEquals(2.0, assertIs<NumberNode>(arr.elements[2]).value)
    }

    @Test
    fun `nested array literal parses recursively`() {
        // print { 1 { 2 3 } 4 }
        val program = parse("print { 1 { 2 3 } 4 }")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val outer = assertIs<ArrayLiteralNode>(cmd.args[0])
        assertEquals(3, outer.elements.size)
        val inner = assertIs<ArrayLiteralNode>(outer.elements[1])
        assertEquals(2, inner.elements.size)
        assertEquals(2.0, assertIs<NumberNode>(inner.elements[0]).value)
        assertEquals(3.0, assertIs<NumberNode>(inner.elements[1]).value)
    }

    @Test
    fun `missing closing brace emits diagnostic and returns partial array`() {
        val tokens = Lexer("print { 1 2").tokenise()
        val scanner = FirstPassScanner(tokens).also { it.scan() }
        val parser = LogoParser(tokens, BUILTIN_ARITIES + scanner.procedures)
        val program = parser.parse()
        val cmd = assertIs<CommandNode>(program.statements[0])
        val arr = assertIs<ArrayLiteralNode>(cmd.args[0])
        assertEquals(2, arr.elements.size)
        assertEquals(null, arr.rbrace)
        val d = parser.diagnostics.single()
        assertEquals(1, d.length) // spans the "{" token
    }

    @Test
    fun `missing closing paren emits diagnostic and recovers`() {
        val tokens = Lexer("print (1 + 2").tokenise()
        val scanner = FirstPassScanner(tokens).also { it.scan() }
        val parser = LogoParser(tokens, BUILTIN_ARITIES + scanner.procedures)
        val program = parser.parse()
        val cmd = assertIs<CommandNode>(program.statements[0])
        // The inner expression still came through as the print arg
        assertEquals(1, cmd.args.size)
        assertIs<BinaryOpNode>(cmd.args[0])
        val d = parser.diagnostics.single()
        assertEquals(1, d.length) // spans the "(" token
    }

    @Test
    fun `parses macro definition`() {
        val program = parse(".macro greet :who print :who end")
        val def = assertIs<ProcedureDefNode>(program.statements[0])
        assertEquals(".macro", def.defToken.text)
        assertEquals("greet", def.nameToken.text)
        assertEquals(1, def.params.size)
        assertEquals("who", def.params[0].text)
        assertEquals(1, def.body.size)
    }

    @Test
    fun `variadic expression call overrides arity table`() {
        // sum's table arity is 2; the variadic form takes 4 args
        val program = parse("print (sum 1 2 3 4)")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val call = assertIs<CallExpressionNode>(cmd.args[0])
        assertEquals("sum", call.nameToken.text)
        assertEquals(4, call.args.size)
        assertEquals(1.0, assertIs<NumberNode>(call.args[0]).value)
        assertEquals(4.0, assertIs<NumberNode>(call.args[3]).value)
    }

    @Test
    fun `variadic statement call parses as CommandNode`() {
        // print's table arity is 1; the variadic form takes 3 args
        val program = parse("(print 1 2 3)")
        val cmd = assertIs<CommandNode>(program.statements[0])
        assertEquals("print", cmd.nameToken.text)
        assertEquals(3, cmd.args.size)
    }

    @Test
    fun `variadic call with zero args parses`() {
        // fd's table arity is 1; (fd) overrides to 0 args
        val program = parse("(fd)")
        val cmd = assertIs<CommandNode>(program.statements[0])
        assertEquals("fd", cmd.nameToken.text)
        assertEquals(0, cmd.args.size)
    }

    @Test
    fun `grouping still works when first token after paren is not IDENTIFIER`() {
        // Regression: (1 + 2) must still parse as grouping, not as variadic call
        val program = parse("print (1 + 2)")
        val cmd = assertIs<CommandNode>(program.statements[0])
        val bin = assertIs<BinaryOpNode>(cmd.args[0])
        assertEquals("+", bin.op.text)
    }

    @Test
    fun `missing closing paren in variadic call emits diagnostic`() {
        val tokens = Lexer("(print 1 2 3").tokenise()
        val scanner = FirstPassScanner(tokens).also { it.scan() }
        val parser = LogoParser(tokens, BUILTIN_ARITIES + scanner.procedures)
        val program = parser.parse()
        val cmd = assertIs<CommandNode>(program.statements[0])
        assertEquals(3, cmd.args.size)
        val d = parser.diagnostics.single()
        assertEquals(1, d.length) // spans the "(" token
    }
}
