package dev.jb.logolsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class LogoLanguageServer : LanguageServer {
    private var client: LanguageClient? = null
    private val documentStore = DocumentStore()
    private val textDocumentService = LogoTextDocumentService(documentStore)
    private val workspaceService = LogoWorkspaceService()

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        return CompletableFuture.supplyAsync {
            val capabilities = ServerCapabilities()
            capabilities.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
            capabilities.declarationProvider = Either.forLeft(true)
            capabilities.definitionProvider = Either.forLeft(true)
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
    }
}


class LogoWorkspaceService : WorkspaceService {
    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        //TODO("Not yet implemented")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        //TODO("Not yet implemented")
    }
}

