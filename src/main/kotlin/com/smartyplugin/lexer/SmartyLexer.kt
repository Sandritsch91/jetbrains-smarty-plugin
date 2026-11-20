package com.smartyplugin.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lexer.SmartyTokenTypes.ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.ASSIGN
import com.smartyplugin.lexer.SmartyTokenTypes.AT
import com.smartyplugin.lexer.SmartyTokenTypes.ATTR_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.BAD_CHARACTER
import com.smartyplugin.lexer.SmartyTokenTypes.COLON
import com.smartyplugin.lexer.SmartyTokenTypes.COMMA
import com.smartyplugin.lexer.SmartyTokenTypes.COMMENT
import com.smartyplugin.lexer.SmartyTokenTypes.CONFIG_VAR
import com.smartyplugin.lexer.SmartyTokenTypes.CONSTANT
import com.smartyplugin.lexer.SmartyTokenTypes.DOT
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.KEYWORD
import com.smartyplugin.lexer.SmartyTokenTypes.LBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.LDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.MODIFIER_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.NUMBER
import com.smartyplugin.lexer.SmartyTokenTypes.OPERATOR
import com.smartyplugin.lexer.SmartyTokenTypes.PIPE
import com.smartyplugin.lexer.SmartyTokenTypes.RBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.RDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.RPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.SLASH
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.TEMPLATE_TEXT
import com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE
import com.smartyplugin.lexer.SmartyTokenTypes.WHITE_SPACE
import java.util.Locale

/**
 * Hand written lexer for Smarty 3/4/5 templates using the default `{` / `}` delimiters.
 *
 * Follows Smarty's "auto literal" rule: a `{` followed by whitespace is plain text, so inline
 * JavaScript and CSS keep working without {literal} blocks.
 *
 * The lexer state is fully encoded in an int so the editor highlighter can restart anywhere:
 * ```
 *   bits 0-1  base state (CONTENT, TAG, TAG_START, LITERAL)
 *   bit  2    previous significant token was '|' (next identifier is a modifier name)
 *   bit  3    current tag is {literal ...}: switch to LITERAL after its closing '}'
 *   bits 4+   nesting depth of '{' inside tags
 * ```
 */
class SmartyLexer @JvmOverloads constructor(delimiters: SmartyDelimiters = SmartyDelimiters.DEFAULT) : LexerBase() {
    // the delimiters are fixed per lexer instance, so the int state stays enough to restart anywhere

    /** left_delimiter / right_delimiter, "{" and "}" unless configured. */
    private val ld: String = delimiters.left
    private val rd: String = delimiters.right

    /** {/literal} in lower case, for a case-insensitive search. */
    private val literalEnd: String = delimiters.closeTag("literal").lowercase(Locale.ROOT)

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var state = 0
    private var tokenState = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokenEnd = startOffset
        this.state = initialState
        advance()
    }

    override fun getState(): Int = tokenState

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = tokenEnd
        tokenState = state
        if (tokenStart >= bufferEnd) {
            tokenType = null
            return
        }
        when (state and BASE_MASK) {
            CONTENT -> lexContent()
            LITERAL -> lexLiteral()
            else -> lexTag()
        }
    }

    // ---------------------------------------------------------------- content

    private fun lexContent() {
        var p = tokenStart
        if (isTagStart(p)) {
            if (lexComment(p)) return
            tokenEnd = p + ld.length
            tokenType = LDELIM
            state = compose(TAG_START, afterPipe = false, literalTag = false, depth = 1)
            return
        }
        while (p < bufferEnd && !isTagStart(p)) p++
        tokenEnd = p
        tokenType = TEMPLATE_TEXT
    }

    /** {* comment *} starting at p (p is at the left delimiter); false if there is none. */
    private fun lexComment(p: Int): Boolean {
        if (charAt(p + ld.length) != '*') return false
        val end = "*$rd"
        val close = indexOf(end, p + ld.length + 1)
        tokenEnd = if (close < 0) bufferEnd else close + end.length
        tokenType = COMMENT
        return true
    }

    private fun lexLiteral() {
        val end = indexOfIgnoreCase(literalEnd, tokenStart)
        if (end == tokenStart) {
            tokenEnd = tokenStart + ld.length
            tokenType = LDELIM
            state = compose(TAG_START, afterPipe = false, literalTag = false, depth = 1)
            return
        }
        tokenEnd = if (end < 0) bufferEnd else end
        tokenType = TEMPLATE_TEXT
    }

    /** The left delimiter starts a tag unless it is followed by whitespace (Smarty auto_literal). */
    private fun isTagStart(p: Int): Boolean {
        val after = p + ld.length
        return startsWith(p, ld) && after < bufferEnd && !Character.isWhitespace(charAt(after))
    }

    private fun startsWith(p: Int, s: String): Boolean {
        if (p < 0 || p + s.length > bufferEnd) return false
        for (i in s.indices) {
            if (buffer[p + i] != s[i]) return false
        }
        return true
    }

    // ---------------------------------------------------------------- tags

    private fun lexTag() {
        val base = state and BASE_MASK
        val literalTag = (state and LITERAL_TAG) != 0
        val depth = state ushr DEPTH_SHIFT
        val p = tokenStart
        val c = charAt(p)

        if (Character.isWhitespace(c)) {
            var q = p
            while (q < bufferEnd && Character.isWhitespace(charAt(q))) q++
            tokenEnd = q
            tokenType = WHITE_SPACE
            return // state unchanged
        }

        if (startsWith(p, rd)) {
            tokenEnd = p + rd.length
            tokenType = RDELIM
            val newDepth = depth - 1
            state = if (newDepth <= 0) {
                compose(if (literalTag) LITERAL else CONTENT, afterPipe = false, literalTag = false, depth = 0)
            } else {
                compose(TAG, afterPipe = false, literalTag = literalTag, depth = newDepth)
            }
            return
        }
        if (startsWith(p, ld)) {
            if (lexComment(p)) return
            tokenEnd = p + ld.length
            tokenType = LDELIM
            state = compose(TAG_START, afterPipe = false, literalTag = literalTag, depth = depth + 1)
            return
        }
        if (c == '/' && base == TAG_START) {
            tokenEnd = p + 1
            tokenType = SLASH
            return
        }

        val afterPipe = (state and AFTER_PIPE) != 0
        var nextAfterPipe = false
        var nextLiteralTag = literalTag

        if (isIdentStart(c)) {
            var q = p + 1
            while (q < bufferEnd && isIdentPart(charAt(q))) q++
            tokenEnd = q
            val word = buffer.subSequence(p, q).toString()
            tokenType = when {
                base == TAG_START -> {
                    if (depth == 1 && word == "literal" && charAt(p - 1) != '/') {
                        nextLiteralTag = true
                    }
                    TAG_NAME
                }
                afterPipe -> MODIFIER_NAME
                !isMemberAccess(p) && isFollowedByAssign(q) -> ATTR_NAME // before keywords: {use class="X" as="Y"}
                word in KEYWORDS && !isMemberAccess(p) -> KEYWORD
                word in CONSTANTS && !isMemberAccess(p) -> CONSTANT
                else -> IDENTIFIER
            }
        } else if (c == '$') {
            var q = p + 1
            while (q < bufferEnd && isIdentPart(charAt(q))) q++
            tokenEnd = q
            tokenType = VARIABLE
        } else if (c == '#') {
            var q = p + 1
            while (q < bufferEnd && isIdentPart(charAt(q))) q++
            if (q > p + 1 && charAt(q) == '#') {
                tokenEnd = q + 1
                tokenType = CONFIG_VAR
            } else {
                tokenEnd = p + 1
                tokenType = BAD_CHARACTER
            }
        } else if (c == '"' || c == '\'') {
            tokenEnd = scanString(p, c)
            tokenType = STRING
        } else if (Character.isDigit(c)) {
            var q = p
            while (q < bufferEnd && Character.isDigit(charAt(q))) q++
            if (charAt(q) == '.' && Character.isDigit(charAt(q + 1))) {
                q++
                while (q < bufferEnd && Character.isDigit(charAt(q))) q++
            }
            tokenEnd = q
            tokenType = NUMBER
        } else {
            lexPunctuation(p, c)
            if (tokenType === PIPE) nextAfterPipe = true
            if (tokenType === AT && afterPipe) nextAfterPipe = true // {$list|@count}
        }

        state = compose(TAG, nextAfterPipe, nextLiteralTag, depth)
    }

    private fun lexPunctuation(p: Int, c: Char) {
        val n = charAt(p + 1)
        val n2 = charAt(p + 2)
        tokenEnd = p + 1
        when (c) {
            '|' -> if (n == '|') {
                tokenEnd = p + 2
                tokenType = OPERATOR
            } else {
                tokenType = PIPE
            }
            '@' -> tokenType = AT
            '-' -> if (n == '>') {
                tokenEnd = p + 2
                tokenType = ARROW
            } else if (n == '-' || n == '=') {
                tokenEnd = p + 2
                tokenType = OPERATOR
            } else {
                tokenType = OPERATOR
            }
            '=' -> if (n == '=' && n2 == '=') {
                tokenEnd = p + 3
                tokenType = OPERATOR
            } else if (n == '=') {
                tokenEnd = p + 2
                tokenType = OPERATOR
            } else if (n == '>') {
                tokenEnd = p + 2
                tokenType = DOUBLE_ARROW
            } else {
                tokenType = ASSIGN
            }
            '!' -> {
                if (n == '=' && n2 == '=') tokenEnd = p + 3
                else if (n == '=') tokenEnd = p + 2
                tokenType = OPERATOR
            }
            '<' -> {
                if (n == '=' || n == '>') tokenEnd = p + 2
                tokenType = OPERATOR
            }
            '>' -> {
                if (n == '=') tokenEnd = p + 2
                tokenType = OPERATOR
            }
            '&' -> {
                if (n == '&') tokenEnd = p + 2
                tokenType = OPERATOR
            }
            '+' -> {
                if (n == '+' || n == '=') tokenEnd = p + 2
                tokenType = OPERATOR
            }
            '*', '/', '%', '?', '^', '~' -> {
                if (n == '=' && c != '?') tokenEnd = p + 2
                tokenType = OPERATOR
            }
            ':' -> if (n == ':') {
                tokenEnd = p + 2
                tokenType = DOUBLE_COLON
            } else {
                tokenType = COLON
            }
            '.' -> tokenType = DOT
            ',' -> tokenType = COMMA
            '(' -> tokenType = LPAREN
            ')' -> tokenType = RPAREN
            '[' -> tokenType = LBRACKET
            ']' -> tokenType = RBRACKET
            else -> tokenType = BAD_CHARACTER
        }
    }

    /**
     * Strings end at the matching quote, so they may contain `}` and line breaks
     * (`'function () { return {a: 1}; }'`). Only a string that is never closed stops early,
     * at the end of the line or tag, so that a missing quote does not swallow the rest of the file.
     */
    private fun scanString(p: Int, quote: Char): Int {
        val end = scanTerminatedString(p, quote)
        return if (end >= 0) end else scanUnterminatedString(p, quote)
    }

    /** End offset after the closing quote, or -1 when the string is not closed. */
    private fun scanTerminatedString(p: Int, quote: Char): Int {
        var q = p + 1
        var tags = 0 // tags embedded in a double-quoted string ("Hi {$name|default:"you"}") may contain quotes
        while (q < bufferEnd) {
            val ch = charAt(q)
            if (ch == '\\') {
                q += 2
                continue
            }
            if (quote == '"') {
                if (startsWith(q, ld) && startsEmbeddedTag(q + ld.length)) {
                    tags++
                    q += ld.length
                    continue
                }
                if (tags > 0 && startsWith(q, rd)) {
                    tags--
                    q += rd.length
                    continue
                }
            }
            if (ch == quote && tags == 0) return q + 1
            q++
        }
        return -1
    }

    private fun startsEmbeddedTag(q: Int): Boolean {
        if (q >= bufferEnd) return false
        val ch = charAt(q)
        return ch == '$' || ch == '/' || ch == '#' || Character.isLetter(ch)
    }

    private fun scanUnterminatedString(p: Int, quote: Char): Int {
        var q = p + 1
        var braces = 0
        while (q < bufferEnd) {
            val ch = charAt(q)
            if (ch == '\\') {
                q += 2
                continue
            }
            if (ch == '\n') return q
            if (quote == '"' && startsWith(q, ld)) {
                braces++
                q += ld.length
                continue
            }
            if (startsWith(q, rd)) {
                if (braces == 0) return q // don't swallow the tag end
                braces--
                q += rd.length
                continue
            }
            q++
        }
        return minOf(q, bufferEnd)
    }

    /** Identifier directly preceded by '.', '->' or '::' (property / method access). */
    private fun isMemberAccess(p: Int): Boolean {
        val prev = charAt(p - 1)
        return prev == '.' || (prev == '>' && charAt(p - 2) == '-') || (prev == ':' && charAt(p - 2) == ':')
    }

    private fun isFollowedByAssign(start: Int): Boolean {
        var q = start
        while (q < bufferEnd && (charAt(q) == ' ' || charAt(q) == '\t')) q++
        return charAt(q) == '=' && charAt(q + 1) != '=' && charAt(q + 1) != '>'
    }

    // ---------------------------------------------------------------- helpers

    private fun charAt(i: Int): Char = if (i >= 0 && i < bufferEnd) buffer[i] else '\u0000'

    private fun indexOf(s: String, from: Int): Int {
        outer@ for (i in from..bufferEnd - s.length) {
            for (j in s.indices) {
                if (buffer[i + j] != s[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun indexOfIgnoreCase(s: String, from: Int): Int {
        outer@ for (i in from..bufferEnd - s.length) {
            for (j in s.indices) {
                if (Character.toLowerCase(buffer[i + j]) != s[j]) continue@outer
            }
            return i
        }
        return -1
    }

    companion object {
        const val CONTENT = 0
        const val TAG = 1
        const val TAG_START = 2
        const val LITERAL = 3
        private const val BASE_MASK = 0x3
        private const val AFTER_PIPE = 0x4
        private const val LITERAL_TAG = 0x8
        private const val DEPTH_SHIFT = 4

        /** Inside a tag, after its name: lexes a bare expression such as the "$a.b" of "`$a.b`" in a string. */
        const val EXPRESSION_STATE = TAG or (1 shl DEPTH_SHIFT)

        private val KEYWORDS = setOf(
            "eq", "ne", "neq", "gt", "lt", "ge", "gte", "le", "lte", "and", "or", "not", "xor", "mod",
            "is", "div", "by", "even", "odd", "as", "to", "step", "in", "instanceof", "isset", "empty",
        )
        private val CONSTANTS = setOf("true", "false", "null", "TRUE", "FALSE", "NULL")

        private fun compose(base: Int, afterPipe: Boolean, literalTag: Boolean, depth: Int): Int =
            base or (if (afterPipe) AFTER_PIPE else 0) or (if (literalTag) LITERAL_TAG else 0) or
                (maxOf(depth, 0) shl DEPTH_SHIFT)

        private fun isIdentStart(c: Char): Boolean = Character.isLetter(c) || c == '_'

        private fun isIdentPart(c: Char): Boolean = Character.isLetterOrDigit(c) || c == '_'
    }
}
