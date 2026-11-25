package com.smartyplugin.formatter

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.formatting.templateLanguages.BlockWithParent
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.parser.SmartyElementTypes.BLOCK
import com.smartyplugin.parser.SmartyElementTypes.BLOCK_PARTS
import com.smartyplugin.parser.SmartyElementTypes.CLOSE_TAG
import com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG
import com.smartyplugin.parser.SmartyElementTypes.BLOCK_BODY
import com.smartyplugin.parser.SmartyElementTypes.CODE_TEXT
import com.smartyplugin.psi.SmartyPsiUtil
import java.util.ArrayDeque

/**
 * Indentation of the JavaScript / CSS in `{registerJs}` and `{registerCss}`.
 *
 * The code is often cut into pieces by Smarty tags (`var id = {$model->id};`), which the JavaScript and CSS
 * formatters can't handle, so it is indented here, line by line, like multi-line Smarty tags: one level per open
 * `{ ( [`, several opened on one line count once (`$(function() {`), a line starting with a
 * closing bracket goes back to the level of the line that opened it. Brackets in strings and comments don't count;
 * continuation lines of block comments and template literals keep their layout. Spacing within lines is kept.
 */
internal object SmartyCodeLayout {
    val CODE_BLOCKS = setOf("registerJs", "registerCss")

    /** The body of a {registerJs} / {registerCss} block. */
    fun isCodeBody(node: ASTNode): Boolean {
        if (node.elementType !== BLOCK_BODY) return false
        val block = node.treeParent
        return block != null && block.elementType === BLOCK && SmartyPsiUtil.blockName(block) in CODE_BLOCKS
    }

    /** A piece of a code body: a run of code text, or a Smarty tag ([code] false) with what it stands for in the code. */
    class Piece(val node: ASTNode, val code: Boolean, val standIn: String)

    /**
     * The code texts and tags of a code body in document order, also those inside nested blocks: all branches of
     * `{if}...{else}...{/if}` are code. [structural] gives the stand-ins of the nested blocks' own tags
     * (`{if}`, `{else}`, `{/if}`); other tags stand for what they print ([SmartyPsiUtil.codeStandIn]).
     */
    fun pieces(body: ASTNode, structural: (block: ASTNode, tag: ASTNode) -> String): List<Piece> {
        val result = ArrayList<Piece>()
        fun walk(container: ASTNode) {
            var child = container.firstChildNode
            while (child != null) {
                val type = child.elementType
                when {
                    type === CODE_TEXT -> result.add(Piece(child, true, ""))
                    isJavaBlank(child.text) -> {}
                    type === BLOCK && SmartyPsiUtil.blockName(child) !in CODE_BLOCKS -> {
                        var part = child.firstChildNode
                        while (part != null) {
                            when {
                                part.elementType === BLOCK_BODY -> walk(part)
                                isJavaBlank(part.text) -> {}
                                BLOCK_PARTS.contains(part.elementType) -> result.add(Piece(part, false, structural(child, part)))
                                else -> result.add(Piece(part, false, SmartyPsiUtil.codeStandIn(part)))
                            }
                            part = part.treeNext
                        }
                    }
                    else -> result.add(Piece(child, false, SmartyPsiUtil.codeStandIn(child)))
                }
                child = child.treeNext
            }
        }
        walk(body)
        return result
    }

    /**
     * Stand-ins of a nested block's tags for the language formatter: braces when every tag of the block stands on
     * a line of its own, so that the formatter indents the branches (`{if $x}` -> `{`, `{else}` -> `}{`,
     * `{/if}` -> `}`); otherwise an empty comment, which keeps the code around it as it is (`var x = {if $a}1{else}2{/if};`).
     */
    private fun formatterStandIn(block: ASTNode, tag: ASTNode): String {
        val text = block.psi.containingFile.viewProvider.contents
        val ownLines = generateSequence(block.firstChildNode) { it.treeNext }
            .filter { BLOCK_PARTS.contains(it.elementType) }
            .all { aloneOnLine(text, it.startOffset, it.startOffset + it.textLength) }
        if (!ownLines) return "/**/" // nothing, as far as JavaScript and CSS are concerned
        return when (tag.elementType) {
            OPEN_TAG -> "{"
            CLOSE_TAG -> "}"
            else -> "}{"
        }
    }

    private fun aloneOnLine(text: CharSequence, start: Int, end: Int): Boolean {
        var i = start - 1
        while (i >= 0 && text[i] != '\n') if (!Character.isWhitespace(text[i--])) return false
        i = end
        while (i < text.length && text[i] != '\n') if (!Character.isWhitespace(text[i++])) return false
        return true
    }

    /** A line of code (or a comment that continues over several lines), indented by bracket depth. */
    class LineBlock(private val range: TextRange, private val indent: Indent) : Block {
        override fun getTextRange(): TextRange = range
        override fun getSubBlocks(): List<Block> = emptyList()
        override fun getWrap(): Wrap? = null
        override fun getIndent(): Indent = indent
        override fun getAlignment(): Alignment? = null
        override fun getSpacing(child1: Block?, child2: Block): Spacing? = null
        override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(Indent.getNoneIndent(), null)
        override fun isIncomplete(): Boolean = false
        override fun isLeaf(): Boolean = true
    }

    // ------------------------------------------------------------------ formatted by the language's formatter

    private val LANGUAGES = mapOf(
        "registerJs" to "JavaScript",
        "registerCss" to "CSS",
    )
    private val EXTENSIONS = mapOf(
        "JavaScript" to "js",
        "CSS" to "css",
    )

    /** A piece of the body that the formatter treats as a unit: a run of code without inner changes, or a tag. */
    private class Unit(val hostStart: Int, val hostEnd: Int, val copyStart: Int, val copyEnd: Int, val tag: ASTNode?)

    /** A unit with the whitespace and indent the language formatter gave it. */
    class CodeBlock(
        private val range: TextRange,
        private val indent: Indent,
        val spacingBefore: Spacing?,
        private val tag: Block?,
    ) : Block, BlockWithParent {
        private var parent: BlockWithParent? = null

        init {
            if (tag is BlockWithParent) tag.parent = this
        }

        override fun getTextRange(): TextRange = range
        override fun getSubBlocks(): List<Block> = if (tag == null) emptyList() else listOf(tag)
        override fun getWrap(): Wrap? = null
        override fun getIndent(): Indent = indent
        override fun getAlignment(): Alignment? = null
        override fun getSpacing(child1: Block?, child2: Block): Spacing? = null
        override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(Indent.getNoneIndent(), null)
        override fun isIncomplete(): Boolean = false
        override fun isLeaf(): Boolean = tag == null
        override fun getParent(): BlockWithParent? = parent
        override fun setParent(newParent: BlockWithParent?) {
            parent = newParent
        }
    }

    /** Spacing between two children of a code body: what the language formatter produced. */
    fun spacing(child1: Block?, child2: Block): Spacing? = (child2 as? CodeBlock)?.spacingBefore

    /**
     * Formats a copy of the body's code with the JavaScript / CSS formatter (Smarty tags replaced by a placeholder)
     * and returns blocks that reproduce its whitespace in the template. Null when that is not possible (no such
     * language, not in a write action, or the formatter changed more than whitespace).
     */
    fun formattedBlocks(body: ASTNode, base: Int, tags: TagBlocks): List<Block>? {
        val languageId = LANGUAGES[SmartyPsiUtil.blockName(body.treeParent)] ?: return null
        val language = Language.findLanguageByID(languageId) ?: return null
        if (!ApplicationManager.getApplication().isWriteAccessAllowed) return null

        // the copy: code as written, every tag replaced by its stand-in, as in the injection
        val copy = StringBuilder()
        val pieces = ArrayList<Unit>() // code texts and tags, with their offsets in host and copy
        val text = body.psi.containingFile.viewProvider.contents
        var hostPos = body.startOffset
        for (piece in pieces(body, ::formatterStandIn)) {
            val node = piece.node
            // between pieces there is only whitespace (around tags, at the edges of nested bodies)
            val gap = text.subSequence(hostPos, node.startOffset)
            if (!isJavaBlank(gap.toString())) return null
            copy.append(gap)
            val copyStart = copy.length
            val hostEnd = node.startOffset + node.textLength
            copy.append(if (piece.code) node.chars else piece.standIn)
            pieces.add(Unit(node.startOffset, hostEnd, copyStart, copy.length, if (piece.code) null else node))
            hostPos = hostEnd
        }
        copy.append(text.subSequence(hostPos, body.startOffset + body.textLength))
        val original = copy.toString()
        val formatted = format(body, language, original)
        if (formatted == null || nonWhitespace(original) != nonWhitespace(formatted)) return null

        // map every non-whitespace character of the original copy to its position in the formatted text
        val target = IntArray(original.length)
        var f = 0
        for (i in original.indices) {
            if (Character.isWhitespace(original[i])) continue
            while (Character.isWhitespace(formatted[f])) f++
            target[i] = f++
        }
        val minIndent = minIndent(formatted)

        val result = ArrayList<Block>()
        var unitStart = -1 // copy offset of the current unit's first character
        var last = -1 // copy offset of the previous non-whitespace character
        var piece: Unit? = null
        var i = 0
        while (i <= original.length) {
            val end = i == original.length
            if (!end && Character.isWhitespace(original[i])) {
                i++
                continue
            }
            val at = if (end) null else pieceAt(pieces, i)
            val boundary = end || last < 0 || at !== piece || at?.tag != null ||
                i != last + 1 || target[i] != target[last] + 1
            if (boundary && unitStart >= 0) {
                result.add(
                    block(
                        piece!!, unitStart, last + 1, target, original, formatted, base, minIndent, tags,
                        first = result.isEmpty(), afterTag = (result.lastOrNull() as? CodeBlock)?.isLeaf == false,
                    ),
                )
            }
            if (end) break
            if (boundary) unitStart = i
            piece = at
            last = i
            if (at?.tag != null) {
                // a tag is one unit: its placeholder is a single character
                last = at.copyEnd - 1
                i = last
            }
            i++
        }
        return result
    }

    private fun pieceAt(pieces: List<Unit>, copyOffset: Int): Unit? =
        pieces.firstOrNull { copyOffset >= it.copyStart && copyOffset < it.copyEnd }

    private fun block(
        piece: Unit,
        copyStart: Int,
        copyEnd: Int,
        target: IntArray,
        original: String,
        formatted: String,
        base: Int,
        minIndent: Int,
        tags: TagBlocks,
        first: Boolean,
        afterTag: Boolean,
    ): Block {
        val range = if (piece.tag != null) {
            TextRange.create(piece.hostStart, piece.hostEnd)
        } else {
            val shift = piece.hostStart - piece.copyStart
            TextRange.create(copyStart + shift, copyEnd + shift)
        }
        // a tag in the middle of a line: its continuation lines follow the indent of that line in the formatted code
        fun tag(startsLine: Boolean): Block? {
            val node = piece.tag ?: return null
            if (startsLine) return tags.create(node, 0) // relative to its own block, which carries the indent
            var lineStart = original.lastIndexOf('\n', copyStart - 1) + 1
            while (lineStart < copyStart && Character.isWhitespace(original[lineStart])) lineStart++
            return tags.create(node, base + maxOf(0, lineIndent(formatted, target[lineStart]) - minIndent))
        }
        // whitespace before the unit in the formatted text
        val at = target[copyStart]
        var ws = at
        while (ws > 0 && Character.isWhitespace(formatted[ws - 1])) ws--
        val gap = formatted.substring(ws, at)
        val lineFeeds = gap.count { it == '\n' }
        // a line with a Smarty tag in it stays one line: the formatter may break it around the tag's stand-in
        var o = copyStart
        while (o > 0 && Character.isWhitespace(original[o - 1])) o--
        val originalGap = original.substring(o, copyStart)
        if (!first && lineFeeds > 0 && (piece.tag != null || afterTag) && '\n' !in originalGap) {
            val keep = Spacing.createSpacing(originalGap.length, originalGap.length, 0, false, 0)
            return CodeBlock(range, Indent.getNoneIndent(), keep, tag(false))
        }
        val spacing = when {
            first -> null
            lineFeeds > 0 -> Spacing.createSpacing(0, 0, lineFeeds, false, lineFeeds - 1)
            else -> Spacing.createSpacing(gap.length, gap.length, 0, false, 0)
        }
        val startsLine = lineFeeds > 0 || ws == 0 || first
        val indent = if (startsLine) {
            Indent.getSpaceIndent(base + maxOf(0, lineIndent(formatted, at) - minIndent))
        } else {
            Indent.getNoneIndent()
        }
        return CodeBlock(range, indent, spacing, tag(startsLine))
    }

    private fun format(body: ASTNode, language: Language, text: String): String? {
        val project = body.psi.project
        return try {
            val copy = PsiFileFactory.getInstance(project).createFileFromText(
                "smarty-code." + EXTENSIONS[language.id], language, text, false, false,
            )
            // code that doesn't parse (a tag printing part of a statement, "{$selector}{ldelim}") is moved around
            // by the formatter as it misreads it: use the bracket layout instead
            if (PsiTreeUtil.hasErrorElements(copy)) return null
            CodeStyleManager.getInstance(project).reformat(copy)
            copy.text
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: RuntimeException) {
            null // fall back to the bracket layout
        }
    }

    private fun nonWhitespace(s: String): String = s.filterNot { Character.isWhitespace(it) }

    /** Indentation (columns) of the line containing offset. */
    private fun lineIndent(text: String, offset: Int): Int {
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        var col = 0
        var i = lineStart
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) {
            col += if (text[i] == '\t') 4 else 1
            i++
        }
        return col
    }

    /** The smallest indentation of a non-empty line: the formatted code's own base level. */
    private fun minIndent(text: String): Int {
        var min = Int.MAX_VALUE
        for (line in text.split("\n")) {
            if (isJavaBlank(line)) continue
            min = minOf(min, lineIndent(line, 0))
        }
        return if (min == Int.MAX_VALUE) 0 else min
    }

    /** java.lang.String.isBlank(): only [Character.isWhitespace] counts, unlike Kotlin's isBlank(). */
    private fun isJavaBlank(s: String): Boolean = s.all { Character.isWhitespace(it) }

    /** Factory for the blocks of Smarty tags inside the code ({$model->id}). */
    fun interface TagBlocks {
        /** @param lineIndent indent (columns, relative to the body) of the line the tag starts on */
        fun create(tag: ASTNode, lineIndent: Int): Block
    }

    /**
     * Blocks for the body: a [LineBlock] per line of code text, and the given blocks for Smarty tags in
     * between. `base` is the indent (in columns) of the body's lines relative to the body.
     */
    fun buildChildren(body: ASTNode, base: Int, unit: Int, tags: TagBlocks): List<Block> {
        val result = ArrayList<Block>()
        val scanner = Scanner()
        val open = ArrayDeque<Int>() // content level of each open bracket
        var lineLevel = 0
        var atLineStart = true // the body starts right after the opening tag; its first line counts as new
        var last: LineBlock? = null

        val fileText = body.psi.containingFile.viewProvider.contents
        for (piece in pieces(body, ::formatterStandIn)) {
            val child = piece.node
            if (!piece.code) {
                // a tag stands for what it prints: {ldelim} / {rdelim} and the tags of a nested block on lines of
                // their own open and close a level like braces ({if} -> "{", {else} -> "}{", {/if} -> "}")
                val braces = if (scanner.inString()) "" else piece.standIn.filter { it == '{' || it == '}' }
                val startsLine = startsLine(fileText, child.startOffset)
                if (startsLine) {
                    lineLevel = if (braces.startsWith("}")) {
                        if (open.isEmpty()) 0 else maxOf(0, open.peek() - 1)
                    } else {
                        if (open.isEmpty()) 0 else open.peek()
                    }
                }
                for (c in braces) {
                    if (c == '{') open.push(lineLevel + 1) else if (!open.isEmpty()) open.pop()
                }
                val indent = if (startsLine) Indent.getSpaceIndent(base + lineLevel * unit) else Indent.getNoneIndent()
                result.add(CodeBlock(TextRange.from(child.startOffset, child.textLength), indent, null, tags.create(child, if (startsLine) 0 else base + lineLevel * unit)))
                last = null
                atLineStart = false
                continue
            }
            val text = child.chars
            val start = child.startOffset
            var i = 0
            while (i < text.length) {
                val lineEnd = indexOf(text, '\n', i)
                var contentStart = i
                while (contentStart < lineEnd && isBlank(text[contentStart])) contentStart++
                var contentEnd = lineEnd
                while (contentEnd > contentStart && isBlank(text[contentEnd - 1])) contentEnd--
                val newLine = atLineStart || i > 0

                if (contentEnd > contentStart) {
                    val previous = last
                    val continuation = newLine && scanner.insideMultiLine() && previous != null
                    if (newLine && !continuation) {
                        val first = text[contentStart]
                        lineLevel = if (isCloser(first)) {
                            if (open.isEmpty()) 0 else maxOf(0, open.peek() - 1)
                        } else {
                            if (open.isEmpty()) 0 else open.peek()
                        }
                    }
                    val range = TextRange(start + contentStart, start + contentEnd)
                    if (continuation) {
                        // a block comment or template literal spanning lines: one block, inner layout kept
                        val at = result.indexOf(previous)
                        val merged = LineBlock(previous!!.textRange.union(range), previous.indent)
                        result[at] = merged
                        last = merged
                    } else {
                        val block = LineBlock(range, Indent.getSpaceIndent(base + lineLevel * unit))
                        result.add(block)
                        last = block
                    }
                    scanner.scan(text, contentStart, contentEnd, open, lineLevel)
                }
                if (lineEnd >= text.length) {
                    atLineStart = false
                    break
                }
                scanner.lineBreak()
                atLineStart = true
                i = lineEnd + 1
            }
            if (text.isNotEmpty() && text[text.length - 1] == '\n') atLineStart = true
        }
        return result
    }

    /** Whether only whitespace precedes the offset on its line. */
    private fun startsLine(text: CharSequence, offset: Int): Boolean {
        var i = offset - 1
        while (i >= 0 && text[i] != '\n') if (!Character.isWhitespace(text[i--])) return false
        return true
    }

    private fun indexOf(text: CharSequence, c: Char, from: Int): Int {
        for (i in from until text.length) if (text[i] == c) return i
        return text.length
    }

    private fun isBlank(c: Char): Boolean = c == ' ' || c == '\t' || c == '\r'

    private fun isCloser(c: Char): Boolean = c == '}' || c == ')' || c == ']'

    /** Tracks strings and comments across lines and across Smarty tags, and counts brackets outside them. */
    private class Scanner {
        private var quote = NO_QUOTE // ' " ` while inside a string
        private var blockComment = false

        fun insideMultiLine(): Boolean = blockComment || quote == '`'

        /** Inside a string or comment: braces printed by tags here are text. */
        fun inString(): Boolean = blockComment || quote != NO_QUOTE

        fun lineBreak() {
            if (quote == '\'' || quote == '"') quote = NO_QUOTE // unterminated single-line string
        }

        fun scan(text: CharSequence, from: Int, to: Int, open: ArrayDeque<Int>, lineLevel: Int) {
            var i = from
            while (i < to) {
                val c = text[i]
                val next = if (i + 1 < to) text[i + 1] else NO_QUOTE
                if (blockComment) {
                    if (c == '*' && next == '/') {
                        blockComment = false
                        i++
                    }
                } else if (quote != NO_QUOTE) {
                    if (c == '\\') i++
                    else if (c == quote) quote = NO_QUOTE
                } else if (c == '/' && next == '*') {
                    blockComment = true
                    i++
                } else if (c == '/' && next == '/') {
                    return // line comment
                } else if (c == '\'' || c == '"' || c == '`') {
                    quote = c
                } else if (c == '{' || c == '(' || c == '[') {
                    open.push(lineLevel + 1)
                } else if (isCloser(c) && !open.isEmpty()) {
                    open.pop()
                }
                i++
            }
        }

        private companion object {
            const val NO_QUOTE = '\u0000'
        }
    }
}
