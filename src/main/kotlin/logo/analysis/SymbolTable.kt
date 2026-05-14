package logo.analysis

import logo.parser.ProcedureDefNode
import logo.parser.ProgramNode

/**
 * Symbol table populated by walking the AST after parsing
 * Currently only tracks user-defined procedures by name
 * TODO: Add support for parameters and variable references
 */
class SymbolTable {
    val proceduresByName: MutableMap<String, ProcedureDefNode> = mutableMapOf() // for go-to-declaration
}

/**
 * Walks the AST and registers every top-level ProcedureDefNode by its (lowercased) name.
 * If the same name is defined twice, the later definition is used (LOGO's "to" redefines a procedure)
 */
class SymbolTableBuilder(private val ast: ProgramNode) {
    fun build(): SymbolTable {
        val table = SymbolTable()
        for (stmt in ast.statements) {
            if (stmt is ProcedureDefNode) table.proceduresByName[stmt.nameToken.text] = stmt
        }
        return table
    }
}
