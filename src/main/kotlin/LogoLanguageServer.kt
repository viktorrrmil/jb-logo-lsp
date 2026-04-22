package dev.jb.logolsp

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.antlr.v4.runtime.Token
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class LogoLanguageServer : LanguageServer {
    private var client: LanguageClient? = null
    private val documentStore = DocumentStore()
    private val textDocumentService = LogoTextDocumentService(documentStore)
    private val workspaceService = LogoWorkspaceService(documentStore)

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        return CompletableFuture.supplyAsync {
            val capabilities = ServerCapabilities()
            capabilities.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
            capabilities.declarationProvider = Either.forLeft(true)
            capabilities.definitionProvider = Either.forLeft(true)
            capabilities.renameProvider = Either.forRight(RenameOptions(true))
            capabilities.codeActionProvider = Either.forLeft(true)
            capabilities.executeCommandProvider = ExecuteCommandOptions(listOf("logo.changeSignature"))
            capabilities.semanticTokensProvider = SemanticTokensWithRegistrationOptions(
                SemanticTokensLegend(SemanticTokensProvider.TOKEN_TYPES, emptyList()),
                true,
            )
            InitializeResult(capabilities)
        }
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        System.exit(0)
    }

    override fun getTextDocumentService(): TextDocumentService {
        return textDocumentService
    }

    override fun getWorkspaceService(): WorkspaceService {
        return workspaceService
    }

    fun connect(client: LanguageClient) {
        this.client = client
        textDocumentService.client = client
        workspaceService.connect(client)
    }
}


class LogoWorkspaceService(
    private val store: DocumentStore,
    private var client: LanguageClient? = null,
) : WorkspaceService {
    fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        //TODO("Not yet implemented")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        //TODO("Not yet implemented")
    }

    override fun executeCommand(params: ExecuteCommandParams?): CompletableFuture<Any> {
        params ?: return CompletableFuture.completedFuture(null)
        if (params.command != "logo.changeSignature") {
            return CompletableFuture.completedFuture(null)
        }

        val args = params.arguments ?: return CompletableFuture.completedFuture(null)
        val uri = asString(args.getOrNull(0)) ?: return CompletableFuture.completedFuture(null)
        val procedureName = asString(args.getOrNull(1)) ?: return CompletableFuture.completedFuture(null)
        val newParameterOrder = asStringList(args.getOrNull(2)) ?: return CompletableFuture.completedFuture(null)

        val edit = buildChangeSignatureEdit(uri, procedureName, newParameterOrder)
            ?: return CompletableFuture.completedFuture(null)

        client?.applyEdit(ApplyWorkspaceEditParams(edit))

        return CompletableFuture.completedFuture(null)
    }

    private fun buildChangeSignatureEdit(
        uri: String,
        procedureName: String,
        newParameterOrder: List<String>,
    ): WorkspaceEdit? {
        val document = store.get(uri) ?: return null
        val procedure = findProcedure(document.program, procedureName) ?: return null
        val existingOrder = procedure.parameter().mapNotNull { it.variableReference()?.IDENT()?.text }
        if (existingOrder.size != newParameterOrder.size) return null
        if (existingOrder.any { old -> newParameterOrder.none { it.equals(old, ignoreCase = true) } }) return null

        val edits = mutableListOf<TextEdit>()

        if (procedure.parameter().isNotEmpty()) {
            val first = procedure.parameter().first().start
            val last = procedure.parameter().last().stop
            if (first != null && last != null) {
                val parameterText = newParameterOrder.joinToString(" ") { ":$it" }
                edits += TextEdit(tokenRange(first, last), parameterText)
            }
        }

        collectProcedureCalls(document.program, procedureName).forEach { call ->
            val arguments = call.argument()
            if (arguments.size != existingOrder.size || arguments.isEmpty()) return@forEach

            val reordered = newParameterOrder.mapNotNull { newName ->
                val oldIndex = existingOrder.indexOfFirst { it.equals(newName, ignoreCase = true) }
                if (oldIndex == -1) {
                    null
                } else {
                    argumentText(arguments[oldIndex], document.source)
                }
            }
            if (reordered.size != existingOrder.size) return@forEach

            val firstArg = arguments.first().start
            val lastArg = arguments.last().stop
            if (firstArg != null && lastArg != null) {
                edits += TextEdit(tokenRange(firstArg, lastArg), reordered.joinToString(" "))
            }
        }

        if (edits.isEmpty()) return null
        return WorkspaceEdit(mapOf(uri to edits))
    }

    private fun findProcedure(
        tree: org.antlr.v4.runtime.tree.ParseTree,
        procedureName: String,
    ): LogoParser.ProcedureDefinitionContext? {
        var result: LogoParser.ProcedureDefinitionContext? = null
        object : LogoBaseVisitor<Unit>() {
            override fun visitProcedureDefinition(ctx: LogoParser.ProcedureDefinitionContext) {
                if (ctx.IDENT()?.text?.equals(procedureName, ignoreCase = true) == true) {
                    result = ctx
                    return
                }
                visitChildren(ctx)
            }
        }.visit(tree)
        return result
    }

    private fun collectProcedureCalls(
        tree: org.antlr.v4.runtime.tree.ParseTree,
        procedureName: String,
    ): List<LogoParser.ProcedureCallContext> {
        val calls = mutableListOf<LogoParser.ProcedureCallContext>()
        object : LogoBaseVisitor<Unit>() {
            override fun visitProcedureCall(ctx: LogoParser.ProcedureCallContext) {
                if (ctx.IDENT()?.text?.equals(procedureName, ignoreCase = true) == true) {
                    calls += ctx
                }
                visitChildren(ctx)
            }
        }.visit(tree)
        return calls
    }

    private fun argumentText(argument: LogoParser.ArgumentContext, source: String): String {
        val start = argument.start?.startIndex ?: return ""
        val stop = argument.stop?.stopIndex ?: return ""
        if (start < 0 || stop < start || stop >= source.length) return ""
        return source.substring(start, stop + 1)
    }

    private fun tokenRange(start: Token, stop: Token): Range {
        val startPosition = Position(start.line - 1, start.charPositionInLine)
        val endPosition = Position(stop.line - 1, stop.charPositionInLine + (stop.text?.length ?: 0))
        return Range(startPosition, endPosition)
    }

    private fun asString(value: Any?): String? {
        return when (value) {
            is String -> value
            is JsonElement -> value.takeIf { it.isJsonPrimitive }?.asString
            else -> value?.toString()
        }
    }

    private fun asStringList(value: Any?): List<String>? {
        return when (value) {
            is List<*> -> value.mapNotNull { asString(it) }
            is JsonArray -> value.mapNotNull { asString(it) }
            else -> null
        }
    }
}

