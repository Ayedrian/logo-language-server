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

// infix arithmetic: '+', '-', '*', '/'. Left-associative; precedence is encoded by the
// shape of the tree (parser builds multiplicative groups deeper than additive ones).
// op carries the operator token (for source-position info).
data class BinaryOpNode(
    val op: Token,
    val left: ExpressionNode,
    val right: ExpressionNode,
) : ExpressionNode()

// prefix unary minus. The UCBLogo grammar only has '-' as a unary operator (no unary '+').
data class UnaryOpNode(
    val op: Token,
    val operand: ExpressionNode,
) : ExpressionNode()

// a procedure call appearing in expression position (e.g. "print sum 3 4" — sum is a value-producing call).
// Mirrors CommandNode structurally; kept separate because CommandNode is a StatementNode.
data class CallExpressionNode(
    val nameToken: Token,
    val args: List<ExpressionNode>,
) : ExpressionNode()

// a quoted-word literal "foo (token.type == QUOTED_WORD) or a bare word inside an array literal
// (token.type == WORD). token.text excludes the leading '"' for QUOTED_WORD.
data class WordLiteralNode(val token: Token) : ExpressionNode()

// an array literal "{ <elements> }". Elements are NumberNode, WordLiteralNode, or nested ArrayLiteralNode.
// Arrays hold literal data only — they carry no scope and contain no variable refs or calls.
// rbrace is null if '}' was missing.
data class ArrayLiteralNode(
    val lbrace: Token,
    val elements: List<ExpressionNode>,
    val rbrace: Token?,
) : ExpressionNode()
