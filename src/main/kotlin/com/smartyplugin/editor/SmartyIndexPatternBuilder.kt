package com.smartyplugin.editor

import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.lexer.SmartyLexer
import com.smartyplugin.lexer.SmartyTokenType
import com.smartyplugin.lexer.SmartyTokenTypes

/**
 * TODO / FIXME in `{* … *}` comments. The indexing lexer splits each comment into its delimiters and its text,
 * so the TODO item is the text alone, whatever the configured delimiters are.
 */
class SmartyIndexPatternBuilder : IndexPatternBuilder {
    override fun getIndexingLexer(file: PsiFile): Lexer? =
        if (file.viewProvider is SmartyFileViewProvider) CommentSplittingLexer(SmartyLexer(SmartyDelimiters.of(file.project))) else null

    override fun getCommentTokenSet(file: PsiFile): TokenSet? =
        if (file.viewProvider is SmartyFileViewProvider) COMMENT_TEXT_SET else null

    override fun getCommentStartDelta(tokenType: IElementType?): Int = 0

    override fun getCommentEndDelta(tokenType: IElementType?): Int = 0

    private class CommentSplittingLexer(private val delegate: Lexer) : LexerBase() {
        private data class Token(val type: IElementType, val start: Int, val end: Int)

        private var buffer: CharSequence = ""
        private var bufferEnd = 0
        private val tokens = ArrayList<Token>()
        private var index = 0

        override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
            this.buffer = buffer
            bufferEnd = endOffset
            tokens.clear()
            index = 0
            delegate.start(buffer, startOffset, endOffset, initialState)
            while (true) {
                val type = delegate.tokenType ?: break
                val start = delegate.tokenStart
                val end = delegate.tokenEnd
                if (type === SmartyTokenTypes.COMMENT) split(start, end) else tokens.add(Token(type, start, end))
                delegate.advance()
            }
        }

        /** {* text *} -> "{*" + " text" + " *}" */
        private fun split(start: Int, end: Int) {
            val text = buffer.subSequence(start, end)
            val open = text.indexOf('*').let { if (it < 0) 0 else it + 1 }
            var close = text.lastIndexOf('*').let { if (it < open) text.length else it }
            while (close > open && text[close - 1].isWhitespace()) close--
            if (open > 0) tokens.add(Token(TokenType.WHITE_SPACE, start, start + open))
            if (close > open) tokens.add(Token(COMMENT_TEXT, start + open, start + close))
            if (close < text.length) tokens.add(Token(TokenType.WHITE_SPACE, start + close, end))
        }

        override fun getState() = 0
        override fun getTokenType(): IElementType? = tokens.getOrNull(index)?.type
        override fun getTokenStart() = tokens.getOrNull(index)?.start ?: bufferEnd
        override fun getTokenEnd() = tokens.getOrNull(index)?.end ?: bufferEnd
        override fun advance() { index++ }
        override fun getBufferSequence() = buffer
        override fun getBufferEnd() = bufferEnd
    }

    private companion object {
        val COMMENT_TEXT: IElementType = SmartyTokenType("COMMENT_TEXT")
        val COMMENT_TEXT_SET: TokenSet = TokenSet.create(COMMENT_TEXT)
    }
}
