package com.smartyplugin.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lang.WhitespacesBinders
import com.intellij.psi.tree.IElementType
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lang.SmartyTags
import com.smartyplugin.lexer.SmartyTokenTypes.AT
import com.smartyplugin.lexer.SmartyTokenTypes.ASSIGN
import com.smartyplugin.lexer.SmartyTokenTypes.ATTR_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.COLON
import com.smartyplugin.lexer.SmartyTokenTypes.COMMA
import com.smartyplugin.lexer.SmartyTokenTypes.LBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.LDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.MODIFIER_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.PIPE
import com.smartyplugin.lexer.SmartyTokenTypes.RBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.RDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.RPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.SLASH
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.TEMPLATE_TEXT
import com.smartyplugin.lexer.SmartyTokenTypes.WHITE_SPACE
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.parser.SmartyElementTypes.BLOCK
import com.smartyplugin.parser.SmartyElementTypes.BLOCK_BODY
import com.smartyplugin.parser.SmartyElementTypes.CLOSE_TAG
import com.smartyplugin.parser.SmartyElementTypes.CODE_TEXT
import com.smartyplugin.parser.SmartyElementTypes.MIDDLE_TAG
import com.smartyplugin.parser.SmartyElementTypes.MODIFIER
import com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG
import com.smartyplugin.parser.SmartyElementTypes.PRINT_TAG
import com.smartyplugin.parser.SmartyElementTypes.STRING_LITERAL
import com.smartyplugin.parser.SmartyElementTypes.TAG
import java.util.ArrayDeque
import java.util.regex.Pattern

/**
 * Builds the block structure of a template. Tag contents are parsed loosely (attributes, modifiers,
 * strings, nested tags); everything else stays a flat token stream, which keeps the parser tolerant
 * while typing. Structural problems (unclosed blocks, stray closing tags) are reported by the annotator.
 */
class SmartyParser @JvmOverloads constructor(delimiters: SmartyDelimiters = SmartyDelimiters.DEFAULT) : PsiParser {
    private val closeTagPattern: Pattern = Pattern.compile(
        Pattern.quote(delimiters.left) + "/([A-Za-z_][A-Za-z0-9_]*)\\s*" + Pattern.quote(delimiters.right),
    )
    private val rightDelimiter: String = delimiters.right

    private class TagInfo(val close: Boolean, val name: String?)

    private lateinit var b: PsiBuilder

    /** Names used in a closing tag anywhere in the file: lets custom block plugins ({form}...{/form}) nest. */
    private var closedNames: Set<String> = emptySet()

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        b = builder
        closedNames = collectClosedNames(builder.originalText)
        val file = builder.mark()
        val stack = ArrayDeque<String>()
        while (!builder.eof()) {
            parseBody(stack)
            if (!builder.eof()) builder.advanceLexer()
        }
        file.done(root)
        return builder.treeBuilt
    }

    private fun collectClosedNames(text: CharSequence): Set<String> {
        val names = HashSet<String>()
        val m = closeTagPattern.matcher(text)
        while (m.find()) names.add(m.group(1))
        return names
    }

    private fun isBlockName(name: String): Boolean {
        if (SmartyTags.isBlock(name)) return true
        if (SmartyTags.isKnownInline(name) || SmartyTags.isMiddle(name)) return false
        return name in closedNames
    }

    /** Parses content until EOF, a closing tag of an enclosing block, or a middle tag of the innermost block. */
    private fun parseBody(stack: ArrayDeque<String>) {
        // also inside {if}, {foreach} ... within the code block: all branches are code
        val code = stack.any { it in CODE_BLOCKS }
        while (!b.eof()) {
            if (b.tokenType !== LDELIM) {
                if (code && b.tokenType === TEMPLATE_TEXT) {
                    val text = b.mark()
                    b.advanceLexer()
                    text.done(CODE_TEXT)
                } else {
                    b.advanceLexer()
                }
                continue
            }
            val info = peekTag()
            if (info.close) {
                if (info.name != null && stack.contains(info.name)) return
                parseTag(CLOSE_TAG) // stray closing tag, reported by the annotator
                continue
            }
            val name = info.name
            when {
                name == null -> parseTag(PRINT_TAG)
                SmartyTags.isMiddle(name) -> {
                    if (SmartyTags.isMiddleAllowed(stack.peek(), name)) return
                    parseTag(MIDDLE_TAG) // misplaced, reported by the annotator
                }
                isBlockName(name) -> parseBlock(stack, name)
                else -> parseTag(TAG)
            }
        }
    }

    private fun parseBlock(stack: ArrayDeque<String>, name: String) {
        val block = b.mark()
        parseTag(OPEN_TAG)
        stack.push(name)
        while (true) {
            val body = b.mark()
            parseBody(stack)
            body.done(BLOCK_BODY)
            body.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER)
            if (b.eof()) break
            val info = peekTag()
            if (info.close && name == info.name) {
                parseTag(CLOSE_TAG)
                break
            }
            if (!info.close && SmartyTags.isMiddleAllowed(name, info.name)) {
                parseTag(MIDDLE_TAG)
                continue
            }
            break // closing tag of an outer block: this block stays unclosed
        }
        stack.pop()
        block.done(BLOCK)
    }

    private fun peekTag(): TagInfo {
        val m = b.mark()
        b.advanceLexer() // {
        var close = false
        if (b.tokenType === SLASH) {
            close = true
            b.advanceLexer()
        }
        val name = if (b.tokenType === TAG_NAME) b.tokenText else null
        m.rollbackTo()
        return TagInfo(close, name)
    }

    private fun parseTag(type: IElementType) {
        val m = b.mark()
        b.advanceLexer() // {
        if (b.tokenType === SLASH) b.advanceLexer()
        if (b.tokenType === TAG_NAME) b.advanceLexer()
        parseTagContent()
        if (b.tokenType === RDELIM) {
            b.advanceLexer()
        } else {
            b.error("'$rightDelimiter' expected")
        }
        m.done(type)
    }

    private fun parseTagContent() {
        while (!b.eof()) {
            val t = b.tokenType
            if (t === RDELIM || t === TEMPLATE_TEXT) return
            if (t === ATTR_NAME) {
                parseAttribute()
            } else {
                parseExpressionPart()
            }
        }
    }

    private fun parseAttribute() {
        val m = b.mark()
        b.advanceLexer() // name
        if (b.tokenType === ASSIGN) b.advanceLexer()
        while (!b.eof()) {
            val t = b.tokenType
            if (t === RDELIM || t === TEMPLATE_TEXT || t === ATTR_NAME) break
            parseExpressionPart()
        }
        m.done(ATTRIBUTE)
    }

    private fun parseExpressionPart() {
        when (b.tokenType) {
            LDELIM -> parseTag(if (peekTag().name == null) PRINT_TAG else TAG)
            PIPE -> parseModifier()
            STRING -> {
                val s = b.mark()
                b.advanceLexer()
                s.done(STRING_LITERAL)
            }
            else -> b.advanceLexer()
        }
    }

    /** |name:arg1:arg2 — an argument ends at ':', '|', '}' or whitespace. */
    private fun parseModifier() {
        val m = b.mark()
        b.advanceLexer() // |
        if (b.tokenType === AT) b.advanceLexer()
        if (b.tokenType === MODIFIER_NAME) b.advanceLexer()
        while (b.tokenType === COLON) {
            b.advanceLexer()
            var first = true
            var parens = 0
            while (!b.eof()) {
                val t = b.tokenType
                if (t === RDELIM || t === TEMPLATE_TEXT || t === ATTR_NAME) break
                if (parens == 0 && (t === COLON || t === PIPE || t === RPAREN || t === RBRACKET || t === COMMA)) break
                if (!first && parens == 0 && b.rawLookup(-1) === WHITE_SPACE) break
                if (t === LPAREN || t === LBRACKET) parens++
                if (t === RPAREN || t === RBRACKET) parens--
                parseExpressionPart()
                first = false
            }
        }
        m.done(MODIFIER)
    }

    private companion object {
        /** yii2-smarty blocks whose content is JavaScript / CSS: their text, also in nested blocks, becomes CODE_TEXT injection hosts. */
        val CODE_BLOCKS = setOf("registerJs", "registerCss")
    }
}
