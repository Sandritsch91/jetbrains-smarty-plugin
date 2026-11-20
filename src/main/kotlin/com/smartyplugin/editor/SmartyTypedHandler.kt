package com.smartyplugin.editor

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lexer.SmartyTokenTypes.RDELIM
import com.smartyplugin.parser.SmartyElementTypes.BLOCK
import com.smartyplugin.parser.SmartyElementTypes.CLOSE_TAG
import com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * - Typing `{/` completes the closing tag of the innermost open block.
 * - Typing the `}` of an opening block tag inserts the matching closing tag after the caret.
 * - Typing `$`, `|`, `->` or `::` inside a tag opens completion.
 */
class SmartyTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.viewProvider !is SmartyFileViewProvider) return Result.CONTINUE
        val d = SmartyDelimiters.of(project)
        if (c == '/') return completeCloseTag(project, editor, d)
        if (endsWith(editor, d.right)) return insertCloseTag(project, editor, file, d)
        if (c == '$' || c == '|') return scheduleAutoPopup(project, editor, file)
        if (c == '>' && previousCharIs(editor, '-')) return scheduleAutoPopup(project, editor, file)
        if (c == ':' && previousCharIs(editor, ':')) return scheduleAutoPopup(project, editor, file)
        return Result.CONTINUE
    }

    companion object {
        /** The character just typed completes "->" or "::" (member access). */
        private fun previousCharIs(editor: Editor, c: Char): Boolean {
            val offset = editor.caretModel.offset
            return offset >= 2 && editor.document.charsSequence[offset - 2] == c
        }

        /**
         * Opens completion for variables after `$` and for modifiers after `|` inside a tag.
         * Decided after the character is inserted: `{` alone is plain text until `{$` makes it a tag.
         */
        private fun scheduleAutoPopup(project: Project, editor: Editor, file: PsiFile): Result {
            val offset = editor.caretModel.offset
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            val typed = SmartyPsiUtil.smartyElementAt(file, offset - 1)
            if (typed != null && SmartyPsiUtil.enclosingTag(typed.node) != null) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            }
            return Result.CONTINUE
        }

        /** The text right before the caret is `s` (just completed by the typed character). */
        private fun endsWith(editor: Editor, s: String): Boolean {
            val offset = editor.caretModel.offset
            val text = editor.document.charsSequence
            return offset >= s.length && text.subSequence(offset - s.length, offset).toString() == s
        }

        private fun completeCloseTag(project: Project, editor: Editor, d: SmartyDelimiters): Result {
            val doc = editor.document
            val offset = editor.caretModel.offset
            val text = doc.charsSequence
            val opener = d.left + "/"
            if (!endsWith(editor, opener)) return Result.CONTINUE
            if (offset < text.length && Character.isLetter(text[offset])) return Result.CONTINUE

            // Decide on the text without the "{/" just typed: an unfinished tag would otherwise swallow
            // the rest of the file and make every block look unclosed.
            val start = offset - opener.length
            val withoutTyped = text.subSequence(0, start).toString() + text.subSequence(offset, text.length)
            val name = unclosedBlockNameAt(project, withoutTyped, start) ?: return Result.CONTINUE

            val hasBrace = text.subSequence(offset, minOf(text.length, offset + d.right.length)).toString() == d.right
            doc.insertString(offset, if (hasBrace) name else name + d.right)
            editor.caretModel.moveToOffset(offset + name.length + d.right.length)
            return Result.STOP
        }

        @JvmStatic
        fun unclosedBlockNameAt(project: Project, text: String, offset: Int): String? {
            val copy = PsiFileFactory.getInstance(project).createFileFromText("dummy.tpl", SmartyLanguage, text)
            if (offset <= 0) return null
            val element = SmartyPsiUtil.smartyElementAt(copy, minOf(offset, text.length) - 1) ?: return null
            // walk up to the innermost block the caret is in; a caret right after {/x} is outside of block x
            var node = element.node
            while (node != null && node.elementType !== BLOCK) {
                val parent = node.treeParent
                if (node.elementType === CLOSE_TAG && SmartyPsiUtil.isType(parent, BLOCK)) {
                    node = parent.treeParent
                    continue
                }
                node = parent
            }
            if (node == null || SmartyPsiUtil.closeTag(node) != null) return null
            return SmartyPsiUtil.blockName(node)
        }

        private fun insertCloseTag(project: Project, editor: Editor, file: PsiFile, d: SmartyDelimiters): Result {
            val doc = editor.document
            val offset = editor.caretModel.offset
            PsiDocumentManager.getInstance(project).commitDocument(doc)
            val element = SmartyPsiUtil.smartyElementAt(file, offset - 1)
            if (element == null || !SmartyPsiUtil.isType(element, RDELIM)) return Result.CONTINUE
            val tag = element.parent
            if (!SmartyPsiUtil.isType(tag, OPEN_TAG) || tag.textRange.endOffset != offset) return Result.CONTINUE
            val block = tag.parent
            if (!SmartyPsiUtil.isType(block, BLOCK) || SmartyPsiUtil.closeTag(block.node) != null) return Result.CONTINUE
            val name = SmartyPsiUtil.blockName(block.node) ?: return Result.CONTINUE
            doc.insertString(offset, d.closeTag(name))
            return Result.STOP
        }
    }
}
