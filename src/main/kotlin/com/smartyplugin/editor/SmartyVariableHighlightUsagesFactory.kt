package com.smartyplugin.editor

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.psi.SmartyPsiUtil
import com.smartyplugin.psi.SmartyVariableUsages

/** The caret on `$name` highlights all occurrences of the variable in the template (also Ctrl+Shift+F7). */
class SmartyVariableHighlightUsagesFactory : HighlightUsagesHandlerFactory {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase<*>? {
        if (file.viewProvider !is SmartyFileViewProvider) return null
        val offset = editor.caretModel.offset
        val token = SmartyPsiUtil.smartyElementAt(file, offset)?.takeIf { SmartyVariableUsages.nameAt(it) != null }
            ?: SmartyPsiUtil.smartyElementAt(file, offset - 1)?.takeIf { SmartyVariableUsages.nameAt(it) != null }
            ?: return null
        val name = SmartyVariableUsages.nameAt(token) ?: return null
        return object : HighlightUsagesHandlerBase<PsiElement>(editor, file) {
            override fun getTargets(): List<PsiElement> = listOf(token)

            override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
                selectionConsumer.consume(targets)
            }

            override fun computeUsages(targets: List<PsiElement>) {
                for (o in SmartyVariableUsages.inTemplate(file, name)) {
                    // definitions count as writes, the rest as reads
                    if (SmartyPsiUtil.isType(o.element, com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE)) {
                        myReadUsages.add(o.range)
                    } else {
                        myWriteUsages.add(o.range)
                    }
                }
            }
        }
    }
}
