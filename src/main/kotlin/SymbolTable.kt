package dev.jb.logolsp

enum class SymbolKind {
    PROCEDURE,
    VARIABLE,
}

data class Symbol(
    val name: String,
    val kind: SymbolKind,
    val line: Int,
    val char: Int,
)

class SymbolTable {
    private val symbolsByName = linkedMapOf<String, Symbol>()

    val symbols: Map<String, Symbol>
        get() = symbolsByName

    fun define(symbol: Symbol) {
        symbolsByName[symbol.name.lowercase()] = symbol
    }

    fun resolve(name: String): Symbol? = symbolsByName[name.lowercase()]
}

