package com.smartyplugin.php

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * Hover / Quick Documentation on Smarty code that refers to PHP shows PHP's own documentation:
 * the PHP element is handed to the platform, which renders it with the PHP documentation provider.
 * - classes (`Html::`, widget tags, `{use class="..."}`), members (`->name`, `->isGuest()`, `Html::encode()`),
 *   widget attributes and configuration keys: the same targets as Ctrl+Click
 * - `$variable`: the class of its type, or the PHP value that is passed in
 */
class SmartyPhpDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        if (file.viewProvider !is SmartyFileViewProvider) return null
        val token = SmartyPsiUtil.smartyElementAt(file, targetOffset) ?: return null
        if (SmartyPsiUtil.isType(token, VARIABLE)) return variableTarget(token)
        return SmartyPhpGotoDeclarationHandler.targets(token).firstOrNull()
    }

    private fun variableTarget(variable: PsiElement): PsiElement? {
        val type = SmartyPhpTypes.typeOfVariable(variable)
        if (type != null) {
            SmartyPhpTypes.classesOf(variable.project, type).firstOrNull()?.let { return it }
        }
        // no class (a string, an array, ...): the PHP value it is passed as, which PHP documents with its type
        val name = variable.text.substring(1)
        return SmartyPhpRenderCalls.passedTo(variable.containingFile)
            .firstOrNull { it.name == name && it.value is PhpTypedElement }?.value
    }
}
