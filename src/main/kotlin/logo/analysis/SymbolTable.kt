package logo.analysis

import logo.lexer.Token
import logo.parser.CommandNode
import logo.parser.ExpressionNode
import logo.parser.ProcedureDefNode
import logo.parser.ProgramNode
import logo.parser.StatementNode
import logo.parser.VariableRefNode

/**
 * Symbol table populated by walking the AST after parsing
 * - proceduresByName: user-defined procedures keyed by name (for go-to-declaration on call sites)
 * - varReferences: each variable reference token mapped to the parameter declaration token it resolves to,
 *   keyed by Token (not VariableRefNode) because Token is a data class whose (line, char) make distinct
 *   refs naturally distinguishable, and it keeps the map free of AST-node stuff,
 *   unresolved refs (no matching parameter in scope) will return null
 */
class SymbolTable {
    val proceduresByName: MutableMap<String, ProcedureDefNode> = mutableMapOf()
    val varReferences: MutableMap<Token, Token> = mutableMapOf()
}

/**
 * Walks the AST and registers every top-level ProcedureDefNode by its (lowercased) name
 * If the same name is defined twice, the later definition is used (LOGO's "to" redefines a procedure)
 * For each procedure, also resolves variable references in its body against its parameters
 */
class SymbolTableBuilder(private val ast: ProgramNode) {
    fun build(): SymbolTable {
        val table = SymbolTable()
        for (stmt in ast.statements) {
            if (stmt is ProcedureDefNode) {
                table.proceduresByName[stmt.nameToken.text] = stmt
                resolveBodyRefs(stmt, table)
            }
        }
        return table
    }

    private fun resolveBodyRefs(def: ProcedureDefNode, table: SymbolTable) {
        val paramsByName = def.params.associateBy { it.text }
        for (stmt in def.body) walkStatement(stmt, paramsByName, table)
    }

    private fun walkStatement(stmt: StatementNode, params: Map<String, Token>, table: SymbolTable) {
        when (stmt) {
            is CommandNode -> for (arg in stmt.args) walkExpression(arg, params, table)
            is ProcedureDefNode -> Unit // nested defs aren't part of the current language subset
        }
    }

    private fun walkExpression(expr: ExpressionNode, params: Map<String, Token>, table: SymbolTable) {
        if (expr is VariableRefNode) {
            params[expr.token.text]?.let { table.varReferences[expr.token] = it }
        }
    }
}
