package logo.parser

import logo.lexer.Token

sealed class AstNode // sealed so that all subclasses need to be defined in here

// root level node, a LOGO program is an ordered list of statements
data class ProgramNode(val statements: List<StatementNode>) : AstNode()

// a statement, sealed class so that we can add more statement kinds and "when" statements need to cover all cases
sealed class StatementNode : AstNode()

// a single procedure call with its name and a list of arguments passed to the procedure
data class CommandNode(
    val nameToken: Token,
    val args: List<ExpressionNode>,
) : StatementNode()

// expression node (arguments to commands), in the future will be things such as variable references, arithmetic, list literals etc.
sealed class ExpressionNode : AstNode()

// numeric literal with parsed value and original lexer token (for semantic token encodign and diagnostics)
data class NumberNode(val value: Double, val token: Token) : ExpressionNode()
