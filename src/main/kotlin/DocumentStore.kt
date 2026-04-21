package dev.jb.logolsp

import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.CharStreams
import java.util.concurrent.ConcurrentHashMap

data class SyntaxError(
    val line: Int,
    val charPositionInLine: Int,
    val message: String,
)

data class ParsedDocument(
    val source: String,
    val program: LogoParser.ProgramContext,
    val errors: List<SyntaxError>,
    val symbolTable: SymbolTable,
)

class DocumentStore {
    private val documents = ConcurrentHashMap<String, ParsedDocument>()

    fun get(uri: String): ParsedDocument? = documents[uri]

    fun open(uri: String, text: String) {
        documents[uri] = parse(text)
    }

    fun update(uri: String, text: String) {
        documents[uri] = parse(text)
    }

    fun close(uri: String) {
        documents.remove(uri)
    }

    private fun parse(text: String): ParsedDocument {
        val errors = mutableListOf<SyntaxError>()
        val lexer = LogoLexer(CharStreams.fromString(text))
        val parser = LogoParser(CommonTokenStream(lexer))
        val errorListener = CollectingErrorListener(errors)

        lexer.removeErrorListeners()
        parser.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        parser.addErrorListener(errorListener)

        val program = parser.program()
        val symbolTable = SymbolTableBuilder().build(program)

        return ParsedDocument(text, program, errors, symbolTable)
    }

    private class CollectingErrorListener(
        private val errors: MutableList<SyntaxError>,
    ) : ANTLRErrorListener {
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String?,
            e: RecognitionException?,
        ) {
            errors += SyntaxError(line, charPositionInLine, msg ?: "Syntax error")
        }

        override fun reportAmbiguity(
            recognizer: org.antlr.v4.runtime.Parser?,
            dfa: org.antlr.v4.runtime.dfa.DFA?,
            startIndex: Int,
            stopIndex: Int,
            exact: Boolean,
            ambigAlts: java.util.BitSet?,
            configs: org.antlr.v4.runtime.atn.ATNConfigSet?,
        ) = Unit

        override fun reportAttemptingFullContext(
            recognizer: org.antlr.v4.runtime.Parser?,
            dfa: org.antlr.v4.runtime.dfa.DFA?,
            startIndex: Int,
            stopIndex: Int,
            conflictingAlts: java.util.BitSet?,
            configs: org.antlr.v4.runtime.atn.ATNConfigSet?,
        ) = Unit

        override fun reportContextSensitivity(
            recognizer: org.antlr.v4.runtime.Parser?,
            dfa: org.antlr.v4.runtime.dfa.DFA?,
            startIndex: Int,
            stopIndex: Int,
            prediction: Int,
            configs: org.antlr.v4.runtime.atn.ATNConfigSet?,
        ) = Unit
    }
}
