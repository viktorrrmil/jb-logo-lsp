package dev.jb.logolsp

import org.antlr.v4.runtime.Token
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class DiagnosticEngine(
    private val document: ParsedDocument,
) {
    fun provide(): List<Diagnostic> {
        val visitor = Collector(document.symbolTable)
        visitor.visit(document.program)

        val diagnostics = visitor.diagnostics.toMutableList()
        diagnostics += document.errors.map { syntaxError ->
            Diagnostic().apply {
                range = Range(
                    Position(syntaxError.line - 1, syntaxError.charPositionInLine),
                    Position(syntaxError.line - 1, syntaxError.charPositionInLine),
                )
                severity = DiagnosticSeverity.Error
                message = syntaxError.message
            }
        }
        return diagnostics
    }

    private class Collector(
        private val symbolTable: SymbolTable,
    ) : LogoBaseVisitor<Unit>() {
        val diagnostics = mutableListOf<Diagnostic>()

        override fun visitVariableReference(ctx: LogoParser.VariableReferenceContext) {
            val name = ctx.IDENT()?.text ?: return
            if (symbolTable.resolve(name) == null) {
                diagnostics += diagnostic(
                    start = ctx.start,
                    stop = ctx.stop,
                    severity = DiagnosticSeverity.Error,
                    message = "Undefined variable: :$name",
                )
            }
            visitChildren(ctx)
        }

        override fun visitProcedureCall(ctx: LogoParser.ProcedureCallContext) {
            val name = ctx.IDENT()?.text ?: return
            if (symbolTable.resolve(name) == null) {
                diagnostics += diagnostic(
                    start = ctx.IDENT()?.symbol ?: ctx.start,
                    stop = ctx.IDENT()?.symbol ?: ctx.start,
                    severity = DiagnosticSeverity.Warning,
                    message = "Unknown procedure: $name",
                )
            }
            visitChildren(ctx)
        }

        private fun diagnostic(
            start: Token,
            stop: Token,
            severity: DiagnosticSeverity,
            message: String,
        ): Diagnostic {
            return Diagnostic().apply {
                range = tokenRange(start, stop)
                this.severity = severity
                this.message = message
            }
        }

        private fun tokenRange(start: Token, stop: Token): Range {
            val startPosition = Position(start.line - 1, start.charPositionInLine)
            val endPosition = Position(
                stop.line - 1,
                stop.charPositionInLine + (stop.text?.length ?: 0),
            )
            return Range(startPosition, endPosition)
        }
    }
}

