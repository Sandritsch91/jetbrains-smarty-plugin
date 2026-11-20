package com.smartyplugin.editor

import com.intellij.codeInsight.generation.CommenterDataHolder
import com.intellij.codeInsight.generation.SelfManagingCommenter
import com.intellij.lang.Commenter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.smartyplugin.lang.SmartyDelimiters

/**
 * `{* ... *}` comments, built from the project's delimiters (`{{* ... *}}` with custom ones).
 * Self-managing so that it can look at the file's project; Smarty has no line comments, so commenting a line
 * wraps it in a block comment.
 */
class SmartyCommenter : Commenter, SelfManagingCommenter<SmartyCommenter.Data> {

    class Data(d: SmartyDelimiters) : CommenterDataHolder() {
        val prefix: String = d.commentStart()
        val suffix: String = d.commentEnd()
    }

    // ---------------------------------------------------------------- plain Commenter (default delimiters)

    override fun getLineCommentPrefix(): String? = null

    override fun getBlockCommentPrefix(): String = SmartyDelimiters.DEFAULT.commentStart()

    override fun getBlockCommentSuffix(): String = SmartyDelimiters.DEFAULT.commentEnd()

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null

    // ---------------------------------------------------------------- lines

    override fun createLineCommentingState(startLine: Int, endLine: Int, document: Document, file: PsiFile): Data =
        Data(SmartyDelimiters.of(file.project))

    override fun commentLine(line: Int, offset: Int, document: Document, data: Data) {
        val end = document.getLineEndOffset(line)
        document.insertString(end, data.suffix)
        document.insertString(offset, data.prefix)
    }

    override fun uncommentLine(line: Int, offset: Int, document: Document, data: Data) {
        val end = document.getLineEndOffset(line)
        val text = document.charsSequence
        var contentEnd = end
        while (contentEnd > offset && Character.isWhitespace(text[contentEnd - 1])) contentEnd--
        if (endsWith(text, contentEnd, data.suffix)) {
            document.deleteString(contentEnd - data.suffix.length, contentEnd)
        }
        if (startsWith(text, offset, data.prefix)) document.deleteString(offset, offset + data.prefix.length)
    }

    override fun isLineCommented(line: Int, offset: Int, document: Document, data: Data): Boolean {
        val text = document.charsSequence
        var end = document.getLineEndOffset(line)
        while (end > offset && Character.isWhitespace(text[end - 1])) end--
        return startsWith(text, offset, data.prefix) && endsWith(text, end, data.suffix) &&
            end - offset >= data.prefix.length + data.suffix.length
    }

    override fun getCommentPrefix(line: Int, document: Document, data: Data): String = data.prefix

    // ---------------------------------------------------------------- blocks

    override fun createBlockCommentingState(selectionStart: Int, selectionEnd: Int, document: Document, file: PsiFile): Data =
        Data(SmartyDelimiters.of(file.project))

    /** The comment around the selection, if there is one. */
    override fun getBlockCommentRange(selectionStart: Int, selectionEnd: Int, document: Document, data: Data): TextRange? {
        val text = document.text
        val start = text.lastIndexOf(data.prefix, selectionStart)
        if (start < 0) return null
        var end = text.indexOf(data.suffix, start + data.prefix.length)
        if (end < 0) return null
        end += data.suffix.length
        return if (end >= selectionEnd) TextRange(start, end) else null
    }

    override fun getBlockCommentPrefix(selectionStart: Int, document: Document, data: Data): String = data.prefix

    override fun getBlockCommentSuffix(selectionEnd: Int, document: Document, data: Data): String = data.suffix

    override fun uncommentBlockComment(startOffset: Int, endOffset: Int, document: Document, data: Data) {
        val text = document.charsSequence
        if (endsWith(text, endOffset, data.suffix)) document.deleteString(endOffset - data.suffix.length, endOffset)
        if (startsWith(text, startOffset, data.prefix)) {
            document.deleteString(startOffset, startOffset + data.prefix.length)
        }
    }

    override fun insertBlockComment(startOffset: Int, endOffset: Int, document: Document, data: Data): TextRange {
        document.insertString(endOffset, data.suffix)
        document.insertString(startOffset, data.prefix)
        return TextRange(startOffset, endOffset + data.prefix.length + data.suffix.length)
    }

    private companion object {
        fun startsWith(text: CharSequence, offset: Int, s: String): Boolean =
            offset >= 0 && offset + s.length <= text.length &&
                text.subSequence(offset, offset + s.length).toString() == s

        fun endsWith(text: CharSequence, end: Int, s: String): Boolean =
            end - s.length >= 0 && end <= text.length &&
                text.subSequence(end - s.length, end).toString() == s
    }
}
