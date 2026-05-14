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

// a user-defined procedure: "to <name> :p1 :p2 ... <body> end"
// nameToken is the procedure name (for go-to-declaration), parameters are VARIABLE tokens,
// body is the list of statements between the parameter list and "end" keyword
// toToken and endToken are kept for semantic-token highlighting, endToken is null if "end" was missing
data class ProcedureDefNode(
    val toToken: Token,
    val nameToken: Token, // will be used for go-to-declaration
    val params: List<Token>,
    val body: List<StatementNode>,
    val endToken: Token?,
) : StatementNode()

// expression node (arguments to commands), in the future will be things such as variable references, arithmetic, list literals etc.
sealed class ExpressionNode : AstNode()

// numeric literal with parsed value and original lexer token (for semantic token encodign and diagnostics)
data class NumberNode(val value: Double, val token: Token) : ExpressionNode()

// a variable reference, written as ":name" in LOGO, token.text is name without the colon
data class VariableRefNode(val token: Token) : ExpressionNode()

// a bracketed instruction list "[ stmt ... ]" passed as an argument to a block-taking primitive
// (repeat, if, ifelse, while, ...). lbracket/rbracket are kept for semantic-token highlighting and
// for diagnostics; rbracket is null if "]" was missing
data class BlockExpressionNode(
    val lbracket: Token,
    val statements: List<StatementNode>,
    val rbracket: Token?,
) : ExpressionNode()
