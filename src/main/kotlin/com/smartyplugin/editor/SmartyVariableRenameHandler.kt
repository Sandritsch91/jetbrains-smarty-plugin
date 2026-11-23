package com.smartyplugin.editor

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.psi.SmartyPsiUtil
import com.smartyplugin.psi.SmartyVariableUsages

/**
 * Shift+F6 on `$name` in a template renames the variable in the template and where it is passed in:
 * the key of the PHP render call / `compact()` / `assign()`, and the attribute of `{include}` in other templates.
 */
class SmartyVariableRenameHandler : RenameHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean = variableAt(dataContext) != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        if (editor == null || file == null) return
        val name = nameAt(file, editor.caretModel.offset) ?: return
        val newName = Messages.showInputDialog(
            project, "Rename variable '$$name' and where it is passed in to:", "Rename Smarty Variable",
            null, name, NAME_VALIDATOR,
        ) ?: return
        if (newName != name) rename(file, editor.caretModel.offset, newName)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        val editor = dataContext?.let { CommonDataKeys.EDITOR.getData(it) }
        val file = dataContext?.let { CommonDataKeys.PSI_FILE.getData(it) }
        invoke(project, editor, file, dataContext)
    }

    companion object {
        private val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private val NAME_VALIDATOR = object : InputValidator {
            override fun checkInput(inputString: String?) = inputString != null && inputString.matches(NAME)
            override fun canClose(inputString: String?) = checkInput(inputString)
        }

        private fun variableAt(dataContext: DataContext): String? {
            val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
            val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return null
            return nameAt(file, editor.caretModel.offset)
        }

        fun nameAt(file: PsiFile, offset: Int): String? {
            if (file.viewProvider !is SmartyFileViewProvider) return null
            // the caret may stand right after the name
            val token = SmartyPsiUtil.smartyElementAt(file, offset)?.let { SmartyVariableUsages.nameAt(it) }
            return token ?: SmartyPsiUtil.smartyElementAt(file, offset - 1)?.let { SmartyVariableUsages.nameAt(it) }
        }

        /** Renames the variable at [offset] of [file] to [newName]; also used by the tests. */
        fun rename(file: PsiFile, offset: Int, newName: String) {
            val project = file.project
            val name = nameAt(file, offset) ?: return
            val occurrences = SmartyVariableUsages.inTemplate(file, name) + SmartyVariableUsages.passingSites(file, name)
            val documents = PsiDocumentManager.getInstance(project)
            val byDocument = occurrences
                .mapNotNull { o -> documents.getDocument(o.element.containingFile)?.let { it to o.range } }
                .groupBy({ it.first }, { it.second })
            WriteCommandAction.writeCommandAction(project).withName("Rename Variable").run<RuntimeException> {
                for ((document, ranges) in byDocument) {
                    for (range in ranges.distinct().sortedByDescending { it.startOffset }) {
                        document.replaceString(range.startOffset, range.endOffset, newName)
                    }
                    documents.commitDocument(document)
                }
            }
        }
    }
}
