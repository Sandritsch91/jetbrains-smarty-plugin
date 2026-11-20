package com.smartyplugin.lexer

import com.intellij.lexer.DelegateLexer
import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType

/**
 * Finer token types for highlighting only: what an identifier is, judged from the characters around it.
 * The PSI keeps plain identifiers (completion, navigation and formatting work on those).
 *
 * - `$page.title`, `$user->name`: property
 * - `$user->isGuest()`: method call; `Html::encode()`: static method call
 * - `Select2::THEME`: class constant; `Html::`: class name; `::class`: keyword
 * - `count($a)`: function call
 */
object SmartyMemberTokens {
    @JvmField val PROPERTY: IElementType = SmartyTokenType("PROPERTY")
    @JvmField val METHOD_CALL: IElementType = SmartyTokenType("METHOD_CALL")
    @JvmField val STATIC_METHOD_CALL: IElementType = SmartyTokenType("STATIC_METHOD_CALL")
    @JvmField val CLASS_CONSTANT: IElementType = SmartyTokenType("CLASS_CONSTANT")
    @JvmField val CLASS_REFERENCE: IElementType = SmartyTokenType("CLASS_REFERENCE")
    @JvmField val FUNCTION_CALL: IElementType = SmartyTokenType("FUNCTION_CALL")

    /** The highlighting type of the token [start, end) in the buffer. */
    @JvmStatic
    fun refine(type: IElementType?, buffer: CharSequence, start: Int, end: Int): IElementType? {
        if (type !== SmartyTokenTypes.IDENTIFIER && type !== SmartyTokenTypes.TAG_NAME) return type
        if (startsWith(buffer, end, "::")) return CLASS_REFERENCE // Html::a(), Select2::THEME
        if (type === SmartyTokenTypes.TAG_NAME) return type

        val call = charAfterSpaces(buffer, end) == '('
        if (endsWith(buffer, start, "::")) {
            if (buffer.subSequence(start, end).toString() == "class") return SmartyTokenTypes.KEYWORD
            return if (call) STATIC_METHOD_CALL else CLASS_CONSTANT
        }
        if (endsWith(buffer, start, "->")) return if (call) METHOD_CALL else PROPERTY
        if (endsWith(buffer, start, ".")) return PROPERTY
        return if (call) FUNCTION_CALL else type
    }

    private fun startsWith(buffer: CharSequence, offset: Int, s: String): Boolean =
        offset + s.length <= buffer.length && buffer.subSequence(offset, offset + s.length).toString() == s

    private fun endsWith(buffer: CharSequence, offset: Int, s: String): Boolean =
        offset - s.length >= 0 && buffer.subSequence(offset - s.length, offset).toString() == s

    private fun charAfterSpaces(buffer: CharSequence, offset: Int): Char {
        var i = offset
        while (i < buffer.length && (buffer[i] == ' ' || buffer[i] == '\t')) i++
        return if (i < buffer.length) buffer[i] else 0.toChar()
    }

    /** The Smarty lexer with refined identifier types; the state is the Smarty lexer's, so it restarts the same way. */
    class HighlightingLexer(delegate: Lexer) : DelegateLexer(delegate) {
        override fun getTokenType(): IElementType? =
            refine(super.getTokenType(), bufferSequence, tokenStart, tokenEnd)
    }
}
