package logo.parser

import logo.lexer.Token
import logo.lexer.TokenType

/**
 * First parser pass: Scans for "to <name> [:<param> ...]" blocks and stores each
 * procedure's arity (how many parameters it takes) so that the recursive descent parser
 * knows how many arguments to consume at every call site (in the second pass)
 */
class FirstPassScanner(private val tokens: List<Token>) {
    // procedure name (lowercase) → arity
    val procedures: MutableMap<String, Int> = mutableMapOf()

    private var position = 0

    fun scan() {
        while (position < tokens.size) {
            if (current().type == TokenType.KEYWORD && current().text == "to") {
                scanProcedure()
            } else {
                position++
            }
        }
    }

    private fun scanProcedure() {
        position++ // consumes the "to" keyword
        if (position >= tokens.size || current().type != TokenType.IDENTIFIER) return
        val name = current().text
        position++ // consume procedure name

        var arity = 0
        // count number of VARIABLE (":param") tokens until we encounter something that isn't a variable
        while (position < tokens.size && current().type == TokenType.VARIABLE) {
            position++
            arity++
        }
        procedures[name] = arity
    }

    private fun current(): Token = tokens[position]
}
