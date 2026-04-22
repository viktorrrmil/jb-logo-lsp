package dev.jb.logolsp

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
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
        System.err.println("SEMANTIC TOKENS REQUEST RECEIVED")
        val document = store.get(params.textDocument.uri)
        val tokens = document?.let { SemanticTokensProvider(it.program, it.symbolTable).provide() } ?: SemanticTokens(emptyList())
        return CompletableFuture.completedFuture(tokens)
    }

    override fun declaration(
        params: DeclarationParams,
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>?> {
        return CompletableFuture.completedFuture(resolveSymbolLocation(params.textDocument.uri, params.position.line, params.position.character))
    }

    override fun definition(
        params: DefinitionParams,
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>?> {
        return CompletableFuture.completedFuture(resolveSymbolLocation(params.textDocument.uri, params.position.line, params.position.character))
    }

    private fun symbolNameForDeclaration(token: TerminalNode): String? {
        val parent = token.parent
        return when {
            parent is LogoParser.VariableReferenceContext && token.symbol.type == LogoLexer.IDENT -> token.text
            parent is LogoParser.VariableReferenceContext && token.symbol.type == LogoLexer.COLON -> {
                parent.IDENT()?.text
            }
            parent is LogoParser.ProcedureCallContext && token.symbol.type == LogoLexer.IDENT -> token.text
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
        val symbolName = symbolNameForDeclaration(token) ?: return null
        val symbol = document.symbolTable.resolve(symbolName) ?: return null

        val start = Position(symbol.line - 1, symbol.char)
        val end = Position(symbol.line - 1, symbol.char + symbol.name.length)
        return Either.forLeft(listOf(Location(uri, Range(start, end))))
    }
}

