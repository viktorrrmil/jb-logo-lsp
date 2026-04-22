package dev.jb.logolsp

import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode
import org.eclipse.lsp4j.SemanticTokens

class SemanticTokensProvider(
    private val program: LogoParser.ProgramContext,
    private val source: String,
) {
    fun provide(): SemanticTokens {
        val collector = TokenCollector()
        collector.visit(program)
        collector.collectComments(source)
        return collector.toSemanticTokens()
    }

    private class TokenCollector() : LogoBaseVisitor<Unit>() {
        private val tokens = mutableListOf<CollectedToken>()

        override fun visitProgram(ctx: LogoParser.ProgramContext) {
            visitChildren(ctx)
        }

        override fun visitProcedureDefinition(ctx: LogoParser.ProcedureDefinitionContext) {
            emitKeyword(ctx.TO())
            emitFunction(ctx.IDENT())
            emitKeyword(ctx.END())
            visitChildren(ctx)
        }

        override fun visitProcedureCall(ctx: LogoParser.ProcedureCallContext) {
            emitFunction(ctx.IDENT())
            visitChildren(ctx)
        }

        override fun visitVariableReference(ctx: LogoParser.VariableReferenceContext) {
            emitVariable(ctx.start, ctx.IDENT()?.symbol)
        }

        override fun visitVariableAssignment(ctx: LogoParser.VariableAssignmentContext) {
            emitKeyword(ctx.MAKE())
            visitChildren(ctx)
        }

        override fun visitRepeatStatement(ctx: LogoParser.RepeatStatementContext) {
            emitKeyword(ctx.REPEAT())
            visitChildren(ctx)
        }

        override fun visitIfStatement(ctx: LogoParser.IfStatementContext) {
            emitKeyword(ctx.IF())
            visitChildren(ctx)
        }

        override fun visitIfElseStatement(ctx: LogoParser.IfElseStatementContext) {
            emitKeyword(ctx.IFELSE())
            visitChildren(ctx)
        }

        override fun visitPrimaryExpression(ctx: LogoParser.PrimaryExpressionContext) {
            ctx.NUMBER()?.symbol?.let { emitNumber(it) }
            visitChildren(ctx)
        }

        override fun visitForwardCommand(ctx: LogoParser.ForwardCommandContext) {
            emitFunction(ctx.FORWARD())
            visitChildren(ctx)
        }

        override fun visitBackCommand(ctx: LogoParser.BackCommandContext) {
            emitFunction(ctx.BACK())
            visitChildren(ctx)
        }

        override fun visitRightCommand(ctx: LogoParser.RightCommandContext) {
            emitFunction(ctx.RIGHT())
            visitChildren(ctx)
        }

        override fun visitLeftCommand(ctx: LogoParser.LeftCommandContext) {
            emitFunction(ctx.LEFT())
            visitChildren(ctx)
        }

        override fun visitPenUpCommand(ctx: LogoParser.PenUpCommandContext) {
            emitFunction(ctx.PENUP())
            visitChildren(ctx)
        }

        override fun visitPenDownCommand(ctx: LogoParser.PenDownCommandContext) {
            emitFunction(ctx.PENDOWN())
            visitChildren(ctx)
        }

        override fun visitHomeCommand(ctx: LogoParser.HomeCommandContext) {
            emitFunction(ctx.HOME())
            visitChildren(ctx)
        }

        override fun visitClearScreenCommand(ctx: LogoParser.ClearScreenCommandContext) {
            emitFunction(ctx.CLEARSCREEN())
            visitChildren(ctx)
        }

        override fun visitQuotedWord(ctx: LogoParser.QuotedWordContext) {
            emitVariableFromQuoted(ctx.QUOTED_WORD())
        }

        fun collectComments(source: String) {
            var lineIndex = 0
            var lineStart = 0
            while (lineStart <= source.length) {
                val lineEnd = source.indexOfAny(charArrayOf('\r', '\n'), startIndex = lineStart).let { if (it == -1) source.length else it }
                val commentStart = source.indexOf(';', startIndex = lineStart)
                if (commentStart != -1 && commentStart < lineEnd) {
                    tokens += CollectedToken(
                        line = lineIndex,
                        char = commentStart - lineStart,
                        length = lineEnd - commentStart,
                        typeIndex = COMMENT,
                    )
                }

                if (lineEnd == source.length) break
                val nextStart = when {
                    lineEnd + 1 < source.length && source[lineEnd] == '\r' && source[lineEnd + 1] == '\n' -> lineEnd + 2
                    else -> lineEnd + 1
                }
                lineStart = nextStart
                lineIndex += 1
            }
        }

        private fun emitKeyword(node: TerminalNode?) {
            emit(node, KEYWORD)
        }

        private fun emitFunction(node: TerminalNode?) {
            emit(node, FUNCTION)
        }

        private fun emitVariableFromQuoted(node: TerminalNode?) {
            val token = node?.symbol ?: return
            tokens += CollectedToken(
                line = token.line - 1,
                char = token.charPositionInLine,
                length = token.text?.length ?: 0,
                typeIndex = VARIABLE,
            )
        }

        private fun emitNumber(token: Token?) {
            token ?: return
            tokens += CollectedToken(
                line = token.line - 1,
                char = token.charPositionInLine,
                length = token.text?.length ?: 0,
                typeIndex = NUMBER,
            )
        }

        private fun emitVariable(startToken: Token?, identToken: Token?) {
            if (startToken == null || identToken == null) return
            tokens += CollectedToken(
                line = startToken.line - 1,
                char = startToken.charPositionInLine,
                length = (identToken.text?.length ?: 0) + 1,
                typeIndex = VARIABLE,
            )
        }

        private fun emit(node: TerminalNode?, typeIndex: Int) {
            val token = node?.symbol ?: return
//            System.err.println("TOKEN: ${token.text} -> $typeIndex")
            tokens += CollectedToken(
                line = token.line - 1,
                char = token.charPositionInLine,
                length = token.text?.length ?: 0,
                typeIndex = typeIndex,
            )
        }

        fun toSemanticTokens(): SemanticTokens {
            val sortedTokens = tokens
                .distinctBy { Triple(it.line, it.char, it.typeIndex) }
                .sortedWith(compareBy<CollectedToken> { it.line }.thenBy { it.char })
            val data = ArrayList<Int>(sortedTokens.size * 5)
            var previousLine = 0
            var previousChar = 0

            for (token in sortedTokens) {
                val deltaLine = token.line - previousLine
                val deltaChar = if (deltaLine == 0) token.char - previousChar else token.char
                data += deltaLine
                data += deltaChar
                data += token.length
                data += token.typeIndex
                data += 0
                previousLine = token.line
                previousChar = token.char
            }

            return SemanticTokens(data)
        }
    }

    private data class CollectedToken(
        val line: Int,
        val char: Int,
        val length: Int,
        val typeIndex: Int,
    )

    companion object {
        val TOKEN_TYPES = listOf("keyword", "function", "variable", "number", "comment")

        private const val KEYWORD = 0
        private const val FUNCTION = 1
        private const val VARIABLE = 2
        private const val NUMBER = 3
        private const val COMMENT = 4
    }
}

