package dev.jb.logolsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class LogoLanguageServer : LanguageServer {
    private lateinit var client: LanguageClient
    private val documentStore = DocumentStore()
    private val textDocumentService = LogoTextDocumentService(documentStore)
    private val workspaceService = LogoWorkspaceService()

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        return CompletableFuture.supplyAsync {
            val capabilities = ServerCapabilities()
            capabilities.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
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
    }
}

class LogoTextDocumentService(private val store: DocumentStore) : TextDocumentService {
    override fun didOpen(params: DidOpenTextDocumentParams?) {
        params ?: return
        store.open(params.textDocument.uri, params.textDocument.text)
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        params ?: return
        store.update(params.textDocument.uri, params.contentChanges.last().text)
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        params ?: return
        store.close(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
        //TODO("Not yet implemented")
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

