package dev.jb.logolsp

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

class LogoTextDocumentService(
    private val store: DocumentStore,
) : TextDocumentService {
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

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val document = store.get(params.textDocument.uri)
        val tokens = document?.let { SemanticTokensProvider(it.program).provide() } ?: SemanticTokens(emptyList())
        return CompletableFuture.completedFuture(tokens)
    }
}

