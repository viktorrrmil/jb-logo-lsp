package dev.jb.logolsp

import org.antlr.v4.runtime.Token

class SymbolTableBuilder : LogoBaseVisitor<Unit>() {
    private val symbolTable = SymbolTable()

    fun build(program: LogoParser.ProgramContext): SymbolTable {
        visit(program)
        return symbolTable
    }

    override fun visitProcedureDefinition(ctx: LogoParser.ProcedureDefinitionContext) {
        ctx.IDENT()?.symbol?.let { symbolTable.define(it.toSymbol(SymbolKind.PROCEDURE)) }

        // Record each parameter as a variable
        ctx.parameter().forEach { parameterCtx ->
            parameterCtx.variableReference()?.IDENT()?.symbol?.let { token ->
                symbolTable.define(
                    Symbol(
                        name = token.text ?: return@let,
                        kind = SymbolKind.VARIABLE,
                        line = token.line,
                        char = token.charPositionInLine,
                    ),
                )
            }
        }

        visitChildren(ctx)
    }

    override fun visitVariableAssignment(ctx: LogoParser.VariableAssignmentContext) {
        ctx.quotedWord()?.QUOTED_WORD()?.symbol?.let { token ->
            val name = token.text?.removePrefix("\"") ?: return@let
            symbolTable.define(
                Symbol(
                    name = name,
                    kind = SymbolKind.VARIABLE,
                    line = token.line,
                    char = token.charPositionInLine,
                ),
            )
        }
        visitChildren(ctx)
    }

    private fun Token.toSymbol(kind: SymbolKind): Symbol {
        return Symbol(
            name = text ?: "",
            kind = kind,
            line = line,
            char = charPositionInLine,
        )
    }
}

