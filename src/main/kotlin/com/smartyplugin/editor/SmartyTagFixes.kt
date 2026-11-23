package com.smartyplugin.editor

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager

/** Quick fixes (Alt+Enter) for the structural errors of [SmartyAnnotator]. */
object SmartyTagFixes {
    /** "Missing closing tag {/if}": inserts it where the block ends (the end of the file or of the enclosing block). */
    class InsertClosingTag(block: PsiElement, private val closeTag: String) : IntentionAction {
        private val pointer = SmartPointerManager.createPointer(block)

        override fun getText() = "Insert $closeTag"
        override fun getFamilyName() = "Insert closing tag"
        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = pointer.element != null
        override fun startInWriteAction() = true

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            val block = pointer.element ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(block.containingFile) ?: return
            var end = block.textRange.endOffset
            // keep trailing line breaks of the block after the inserted tag
            val text = document.charsSequence
            while (end > block.textRange.startOffset && text[end - 1].isWhitespace()) end--
            document.insertString(end, closeTag)
        }
    }

    /** "Closing tag {/if} has no matching opening tag": removes it. */
    class RemoveTag(tag: PsiElement) : IntentionAction {
        private val pointer = SmartPointerManager.createPointer(tag)

        override fun getText() = "Remove ${pointer.element?.text ?: "closing tag"}"
        override fun getFamilyName() = "Remove closing tag"
        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = pointer.element != null
        override fun startInWriteAction() = true

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            val tag = pointer.element ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(tag.containingFile) ?: return
            document.deleteString(tag.textRange.startOffset, tag.textRange.endOffset)
        }
    }
}
