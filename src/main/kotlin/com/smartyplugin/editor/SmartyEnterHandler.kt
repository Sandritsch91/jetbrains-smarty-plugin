package com.smartyplugin.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.lexer.SmartyTokenTypes.RDELIM
import com.smartyplugin.parser.SmartyElementTypes.MIDDLE_TAG
import com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG
import com.smartyplugin.psi.SmartyPsiUtil

/** Enter between `{if $x}` and `{/if}` puts the closing tag on its own line. */
class SmartyEnterHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?,
    ): Result {
        if (file.viewProvider !is SmartyFileViewProvider) return Result.Continue
        val doc = editor.document
        val text = doc.charsSequence
        val offset = caretOffset.get()
        val d = SmartyDelimiters.of(file.project)
        val before = d.right
        val after = d.left + "/"
        if (offset < before.length || offset + after.length > text.length) return Result.Continue
        if (text.subSequence(offset - before.length, offset).toString() != before ||
            text.subSequence(offset, offset + after.length).toString() != after
        ) {
            return Result.Continue
        }
        PsiDocumentManager.getInstance(file.project).commitDocument(doc)
        val brace = SmartyPsiUtil.smartyElementAt(file, offset - 1)
        if (brace == null || !SmartyPsiUtil.isType(brace, RDELIM)) return Result.Continue
        val tag = brace.parent
        if (!SmartyPsiUtil.isType(tag, OPEN_TAG) && !SmartyPsiUtil.isType(tag, MIDDLE_TAG)) return Result.Continue

        val lineStart = doc.getLineStartOffset(doc.getLineNumber(offset))
        var i = lineStart
        while (i < offset && (text[i] == ' ' || text[i] == '\t')) i++
        val indent = text.subSequence(lineStart, i).toString()
        val unit = indentUnit(file)
        // Built here rather than by the formatter: an empty block body has no block to ask for child indent.
        doc.insertString(offset, "\n" + indent + unit + "\n" + indent)
        editor.caretModel.moveToOffset(offset + 1 + indent.length + unit.length)
        return Result.Stop
    }

    private fun indentUnit(file: PsiFile): String {
        val options = CodeStyle.getSettings(file).getIndentOptions(HtmlFileType.INSTANCE)
        if (options.USE_TAB_CHARACTER) return "\t"
        return " ".repeat(maxOf(0, options.INDENT_SIZE))
    }
}
