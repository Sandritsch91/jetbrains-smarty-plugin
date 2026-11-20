package com.smartyplugin.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lexer.SmartyTokenTypes.BACKTICK
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE

/**
 * Splits one double-quoted string token into the parts Smarty evaluates, for highlighting only
 * (it runs as a layer of the highlighting lexer; the PSI keeps one string token):
 *
 * - `"Hello $name"`: a simple variable (letters, digits, '_'; `$a.b` embeds just `$a`)
 * - ``"Hello `$user.name`"``: a backtick expression
 * - `"Hello {$name|escape}"`, `"{counter}"`: an embedded tag
 *
 * Everything else, and every single-quoted string, stays [SmartyTokenTypes.STRING].
 */
class SmartyStringLexer @JvmOverloads constructor(
    private val delimiters: SmartyDelimiters = SmartyDelimiters.DEFAULT,
) : LexerBase() {
    private class Token(val type: IElementType, val start: Int, val end: Int)

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private val tokens = ArrayList<Token>()
    private var index = 0
    private val ld: String = delimiters.left
    private val rd: String = delimiters.right

    private fun startsWith(p: Int, end: Int, s: String): Boolean {
        if (p + s.length > end) return false
        for (i in s.indices) {
            if (buffer[p + i] != s[i]) return false
        }
        return true
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        tokens.clear()
        index = 0
        if (startOffset >= endOffset) return
        if (buffer[startOffset] != '"') {
            tokens.add(Token(STRING, startOffset, endOffset))
            return
        }
        split(startOffset, endOffset)
    }

    private fun split(start: Int, end: Int) {
        var textStart = start
        var p = start + 1 // after the opening quote
        while (p < end) {
            val c = buffer[p]
            if (c == '\\') {
                p += 2
                continue
            }
            var partEnd = -1
            if (c == '$' && p + 1 < end && isIdentStart(buffer[p + 1])) {
                partEnd = p + 2
                while (partEnd < end && isIdentPart(buffer[partEnd])) partEnd++
                flushText(textStart, p)
                tokens.add(Token(VARIABLE, p, partEnd))
            } else if (c == '`') {
                val close = indexOf('`', p + 1, end)
                if (close > 0) {
                    partEnd = close + 1
                    flushText(textStart, p)
                    tokens.add(Token(BACKTICK, p, p + 1))
                    delegate(p + 1, close, SmartyLexer.EXPRESSION_STATE)
                    tokens.add(Token(BACKTICK, close, close + 1))
                }
            } else if (startsWith(p, end, ld) && p + ld.length < end &&
                !Character.isWhitespace(buffer[p + ld.length])
            ) {
                val close = matchingBrace(p, end)
                if (close > 0) {
                    partEnd = close + rd.length
                    flushText(textStart, p)
                    delegate(p, partEnd, 0)
                }
            }
            if (partEnd > 0) {
                p = partEnd
                textStart = p
            } else {
                p++
            }
        }
        flushText(textStart, end)
    }

    private fun flushText(from: Int, to: Int) {
        if (to > from) tokens.add(Token(STRING, from, to))
    }

    /** Lexes an embedded tag or expression with the regular Smarty lexer. */
    private fun delegate(from: Int, to: Int, state: Int) {
        if (to <= from) return
        val lexer = SmartyLexer(delimiters)
        lexer.start(buffer, from, to, state)
        while (true) {
            var type = lexer.tokenType ?: break
            type = SmartyMemberTokens.refine(type, buffer, lexer.tokenStart, lexer.tokenEnd)!!
            tokens.add(Token(type, lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
    }

    /**
     * Offset of the right delimiter closing the tag that starts at `open`, or -1; quotes and nested tags
     * are skipped.
     */
    private fun matchingBrace(open: Int, end: Int): Int {
        var depth = 0
        var quote = NO_QUOTE
        var q = open
        while (q < end) {
            val ch = buffer[q]
            if (quote != NO_QUOTE) {
                if (ch == '\\') q++
                else if (ch == quote) quote = NO_QUOTE
                q++
                continue
            }
            if (startsWith(q, end, ld)) {
                depth++
                q += ld.length - 1
            } else if (startsWith(q, end, rd)) {
                if (--depth == 0) return q
                q += rd.length - 1
            } else if (ch == '\'' || ch == '"') {
                // the outer string's closing quote can't be inside the tag: an unmatched '"' ends the search
                if (ch == '"' && indexOf('"', q + 1, end) < 0) return -1
                quote = ch
            }
            q++
        }
        return -1
    }

    private fun indexOf(ch: Char, from: Int, end: Int): Int {
        for (q in from until end) {
            if (buffer[q] == ch) return q
        }
        return -1
    }

    private fun current(): Token? = tokens.getOrNull(index)

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = current()?.type

    override fun getTokenStart(): Int = current()?.start ?: bufferEnd

    override fun getTokenEnd(): Int = current()?.end ?: bufferEnd

    override fun advance() {
        index++
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    private companion object {
        const val NO_QUOTE = '\u0000'

        fun isIdentStart(c: Char): Boolean = Character.isLetter(c) || c == '_'

        fun isIdentPart(c: Char): Boolean = Character.isLetterOrDigit(c) || c == '_'
    }
}
