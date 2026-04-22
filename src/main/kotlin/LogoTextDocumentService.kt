package dev.jb.logolsp

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

class LogoTextDocumentService(
    private val store: DocumentStore,
) : TextDocumentService {
    var client: LanguageClient? = null

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        params ?: return
        store.open(params.textDocument.uri, params.textDocument.text)
        publishDiagnostics(params.textDocument.uri)
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        params ?: return
        store.update(params.textDocument.uri, params.contentChanges.last().text)
        publishDiagnostics(params.textDocument.uri)
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        params ?: return
        store.close(params.textDocument.uri)
        client?.publishDiagnostics(PublishDiagnosticsParams(params.textDocument.uri, emptyList()))
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
        // No-op for now.
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val document = store.get(params.textDocument.uri)
        val tokens = document?.let { SemanticTokensProvider(it.program, it.source).provide() }
            ?: SemanticTokens(emptyList())
        return CompletableFuture.completedFuture(tokens)
    }

    override fun declaration(
        params: DeclarationParams,
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>?> {
        return CompletableFuture.completedFuture(
            resolveSymbolLocation(params.textDocument.uri, params.position.line, params.position.character),
        )
    }

    override fun definition(
        params: DefinitionParams,
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>?> {
        return CompletableFuture.completedFuture(
            resolveSymbolLocation(params.textDocument.uri, params.position.line, params.position.character),
        )
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
        val uri = params.textDocument.uri
        val document = store.get(uri) ?: return CompletableFuture.completedFuture(emptyList())
        val line = params.range.start.line
        val char = params.range.start.character

        // Find if cursor is on a parameter of a procedure definition
        val token = tokenAtPosition(document.program, line, char)
            ?: return CompletableFuture.completedFuture(emptyList())

        val procedure = findEnclosingProcedure(document.program, token)
            ?: return CompletableFuture.completedFuture(emptyList())

        // Only show if cursor is actually on a parameter
        val isOnParam = procedure.parameter().any { param ->
            param.variableReference()?.IDENT()?.symbol?.let { t ->
                t.line - 1 == line && t.charPositionInLine <= char && char < t.charPositionInLine + t.text.length
            } == true
        }
        if (!isOnParam) return CompletableFuture.completedFuture(emptyList())

        val procedureName = procedure.IDENT()?.text ?: return CompletableFuture.completedFuture(emptyList())
        val currentOrder = procedure.parameter().mapNotNull { it.variableReference()?.IDENT()?.text }

        val reversedOrder = currentOrder.reversed()
        val command = Command(
            "Change Signature: Reverse Parameter Order",
            "logo.changeSignature",
            listOf(uri, procedureName, reversedOrder)
        )
        val action = CodeAction("Change Signature: Reverse Parameter Order").apply {
            kind = CodeActionKind.Refactor
            this.command = command
        }

        return CompletableFuture.completedFuture(listOf(Either.forRight(action)))
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit?> {
        val document = store.get(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(null)
        val token = tokenAtPosition(document.program, params.position.line, params.position.character)
            ?: return CompletableFuture.completedFuture(null)

        val parameterContext = parameterRenameContext(document.program, token)
        if (parameterContext != null) {
            val collector = ScopedParameterReferenceCollector(parameterContext.name, params.newName)
            collector.visit(parameterContext.procedure)
            val edits = mutableListOf<TextEdit>()
            edits += TextEdit(tokenRange(parameterContext.parameterToken), params.newName)
            edits += collector.edits
            return CompletableFuture.completedFuture(WorkspaceEdit(mapOf(params.textDocument.uri to edits)))
        }

        val symbolName = symbolNameForDeclaration(token)
            ?: return CompletableFuture.completedFuture(null)
        val symbol = document.symbolTable.resolve(symbolName)
            ?: return CompletableFuture.completedFuture(null)

        val collector = ReferenceCollector(symbol.name, symbol.kind, params.newName)
        collector.visit(document.program)
        val edits = collector.edits
        if (edits.isEmpty()) return CompletableFuture.completedFuture(null)

        val workspaceEdit = WorkspaceEdit(mapOf(params.textDocument.uri to edits))
        return CompletableFuture.completedFuture(workspaceEdit)
    }

    override fun prepareRename(
        params: PrepareRenameParams,
    ): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>?> {
        val document = store.get(params.textDocument.uri)
            ?: return failedFuture("No renameable symbol at cursor")
        val token = tokenAtPosition(document.program, params.position.line, params.position.character)
            ?: return failedFuture("No renameable symbol at cursor")
        val symbolName = symbolNameForDeclaration(token)
            ?: return failedFuture("No renameable symbol at cursor")
        document.symbolTable.resolve(symbolName)
            ?: return failedFuture("No renameable symbol at cursor")

        val range = if (token.symbol.type == LogoLexer.QUOTED_WORD) {
            val t = token.symbol
            Range(
                Position(t.line - 1, t.charPositionInLine + 1),
                Position(t.line - 1, t.charPositionInLine + (t.text?.length ?: 0)),
            )
        } else {
            tokenRange(token.symbol)
        }
        return CompletableFuture.completedFuture(Either3.forFirst(range))
    }

    private fun <T> failedFuture(message: String): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        future.completeExceptionally(noRenameableSymbol(message))
        return future
    }

    private fun noRenameableSymbol(message: String = "No renameable symbol at cursor"): ResponseErrorException {
        return ResponseErrorException(
            ResponseError(ResponseErrorCode.InvalidRequest, message, null)
        )
    }

    private fun symbolNameForDeclaration(token: TerminalNode): String? {
        val parent = token.parent
        return when {
            parent is LogoParser.VariableReferenceContext && token.symbol.type == LogoLexer.IDENT -> token.text
            parent is LogoParser.VariableReferenceContext && token.symbol.type == LogoLexer.COLON -> parent.IDENT()?.text
            parent is LogoParser.ProcedureCallContext && token.symbol.type == LogoLexer.IDENT -> token.text
            parent is LogoParser.ProcedureDefinitionContext && token.symbol.type == LogoLexer.IDENT -> token.text
            parent is LogoParser.QuotedWordContext &&
                token.symbol.type == LogoLexer.QUOTED_WORD &&
                parent.parent is LogoParser.VariableAssignmentContext -> token.text.removePrefix("\"")
            else -> null
        }
    }

    private fun tokenAtPosition(tree: ParseTree, line: Int, char: Int): TerminalNode? {
        if (tree is TerminalNode) {
            val token = tree.symbol ?: return null
            val tokenLine = token.line - 1
            val startChar = token.charPositionInLine
            val endChar = startChar + (token.text?.length ?: 0)
            return if (tokenLine == line && char >= startChar && char < endChar) tree else null
        }

        for (index in 0 until tree.childCount) {
            val match = tokenAtPosition(tree.getChild(index), line, char)
            if (match != null) return match
        }
        return null
    }

    private fun resolveSymbolLocation(uri: String, line: Int, char: Int): Either<List<Location>, List<LocationLink>>? {
        val document = store.get(uri) ?: return null
        val token = tokenAtPosition(document.program, line, char) ?: return null

        val parameterContext = parameterRenameContext(document.program, token)
        if (parameterContext != null) {
            val start = Position(parameterContext.parameterToken.line - 1, parameterContext.parameterToken.charPositionInLine)
            val end = Position(
                parameterContext.parameterToken.line - 1,
                parameterContext.parameterToken.charPositionInLine + (parameterContext.parameterToken.text?.length ?: 0),
            )
            return Either.forLeft(listOf(Location(uri, Range(start, end))))
        }

        val symbolName = symbolNameForDeclaration(token) ?: return null
        val symbol = document.symbolTable.resolve(symbolName) ?: return null

        val start = Position(symbol.line - 1, symbol.char)
        val end = Position(symbol.line - 1, symbol.char + symbol.name.length)
        return Either.forLeft(listOf(Location(uri, Range(start, end))))
    }

    private fun publishDiagnostics(uri: String) {
        val diagnostics = store.get(uri)?.let { DiagnosticEngine(it).provide() } ?: emptyList()
        client?.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
    }

    private fun tokenRange(token: Token): Range {
        val start = Position(token.line - 1, token.charPositionInLine)
        val end = Position(token.line - 1, token.charPositionInLine + (token.text?.length ?: 0))
        return Range(start, end)
    }

    private fun parameterRenameContext(
        tree: ParseTree,
        target: TerminalNode,
    ): ParameterRenameContext? {
        val variableReference = target.parent as? LogoParser.VariableReferenceContext ?: return null
        val parameter = variableReference.parent as? LogoParser.ParameterContext ?: return null
        val procedure = findEnclosingProcedure(tree, target) ?: return null
        if (!procedure.parameter().contains(parameter)) return null

        val parameterToken = variableReference.IDENT()?.symbol ?: return null
        return ParameterRenameContext(
            procedure = procedure,
            parameterToken = parameterToken,
            name = parameterToken.text,
        )
    }

    private fun findEnclosingProcedure(
        tree: ParseTree,
        target: TerminalNode,
    ): LogoParser.ProcedureDefinitionContext? {
        var current: ParseTree? = target
        while (current != null) {
            if (current is LogoParser.ProcedureDefinitionContext) {
                return current
            }
            if (current == tree) {
                break
            }
            current = current.parent
        }
        return null
    }

    private fun containsPosition(start: Token, stop: Token, line: Int, char: Int): Boolean {
        val startLine = start.line - 1
        val endLine = stop.line - 1
        val endChar = stop.charPositionInLine + (stop.text?.length ?: 0)

        if (line < startLine || line > endLine) return false
        if (line == startLine && char < start.charPositionInLine) return false
        if (line == endLine && char > endChar) return false
        return true
    }

    private fun noRenameableSymbol(): ResponseErrorException {
        return ResponseErrorException(
            ResponseError(ResponseErrorCode.InvalidRequest, "No renameable symbol at cursor", null),
        )
    }

    private class ReferenceCollector(
        private val symbolName: String,
        private val symbolKind: SymbolKind,
        private val newName: String,
    ) : LogoBaseVisitor<Unit>() {
        val edits = mutableListOf<TextEdit>()

        override fun visitVariableReference(ctx: LogoParser.VariableReferenceContext) {
            if (symbolKind != SymbolKind.VARIABLE) {
                visitChildren(ctx)
                return
            }
            val ident = ctx.IDENT()?.symbol ?: return
            if (!ident.text.equals(symbolName, ignoreCase = true)) {
                visitChildren(ctx)
                return
            }
            edits += TextEdit(Range(
                Position(ident.line - 1, ident.charPositionInLine),
                Position(ident.line - 1, ident.charPositionInLine + (ident.text?.length ?: 0)),
            ), newName)
            visitChildren(ctx)
        }

        override fun visitProcedureCall(ctx: LogoParser.ProcedureCallContext) {
            if (symbolKind != SymbolKind.PROCEDURE) {
                visitChildren(ctx)
                return
            }
            val ident = ctx.IDENT()?.symbol ?: return
            if (!ident.text.equals(symbolName, ignoreCase = true)) {
                visitChildren(ctx)
                return
            }
            edits += TextEdit(Range(
                Position(ident.line - 1, ident.charPositionInLine),
                Position(ident.line - 1, ident.charPositionInLine + (ident.text?.length ?: 0)),
            ), newName)
            visitChildren(ctx)
        }

        override fun visitProcedureDefinition(ctx: LogoParser.ProcedureDefinitionContext) {
            val definitionIdent = ctx.IDENT()?.symbol
            if (symbolKind == SymbolKind.PROCEDURE && definitionIdent != null &&
                definitionIdent.text.equals(symbolName, ignoreCase = true)
            ) {
                edits += TextEdit(Range(
                    Position(definitionIdent.line - 1, definitionIdent.charPositionInLine),
                    Position(definitionIdent.line - 1, definitionIdent.charPositionInLine + (definitionIdent.text?.length ?: 0)),
                ), newName)
            }

            if (symbolKind == SymbolKind.VARIABLE) {
                ctx.parameter().forEach { parameter ->
                    val ident = parameter.variableReference()?.IDENT()?.symbol ?: return@forEach
                    if (!ident.text.equals(symbolName, ignoreCase = true)) return@forEach
                    edits += TextEdit(Range(
                        Position(ident.line - 1, ident.charPositionInLine),
                        Position(ident.line - 1, ident.charPositionInLine + (ident.text?.length ?: 0)),
                    ), newName)
                }
            }

            visitChildren(ctx)
        }

        override fun visitVariableAssignment(ctx: LogoParser.VariableAssignmentContext) {
            if (symbolKind != SymbolKind.VARIABLE) {
                visitChildren(ctx)
                return
            }

            val quotedWord = ctx.quotedWord()?.QUOTED_WORD()?.symbol
            if (quotedWord != null) {
                val currentName = quotedWord.text?.removePrefix("\"") ?: ""
                if (currentName.equals(symbolName, ignoreCase = true)) {
                    val start = quotedWord.charPositionInLine + 1
                    val end = start + currentName.length
                    edits += TextEdit(
                        Range(
                            Position(quotedWord.line - 1, start),
                            Position(quotedWord.line - 1, end),
                        ),
                        newName,
                    )
                }
            }

            visitChildren(ctx)
        }
    }

    private class ScopedParameterReferenceCollector(
        private val parameterName: String,
        private val newName: String,
    ) : LogoBaseVisitor<Unit>() {
        val edits = mutableListOf<TextEdit>()

        override fun visitVariableReference(ctx: LogoParser.VariableReferenceContext) {
            if (ctx.parent is LogoParser.ParameterContext) {
                visitChildren(ctx)
                return
            }

            val ident = ctx.IDENT()?.symbol ?: return
            if (ident.text.equals(parameterName, ignoreCase = true)) {
                edits += TextEdit(
                    Range(
                        Position(ident.line - 1, ident.charPositionInLine),
                        Position(ident.line - 1, ident.charPositionInLine + (ident.text?.length ?: 0)),
                    ),
                    newName,
                )
            }

            visitChildren(ctx)
        }
    }

    private data class ParameterRenameContext(
        val procedure: LogoParser.ProcedureDefinitionContext,
        val parameterToken: Token,
        val name: String,
    )
}
