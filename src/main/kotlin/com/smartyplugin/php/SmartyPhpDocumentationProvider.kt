package com.smartyplugin.php

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.smartyplugin.lang.SmartyPlugins
import com.smartyplugin.lexer.SmartyTokenTypes.MODIFIER_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
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
        // a translated message documents itself: its translations in all languages
        if (SmartyYiiTranslations.messageCategory(token) != null) return token
        pluginTarget(token)?.let { return it }
        return SmartyPhpGotoDeclarationHandler.targets(token).firstOrNull()
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null || element.language !== com.smartyplugin.lang.SmartyLanguage) return null
        return SmartyYiiTranslations.documentation(element)
    }

    /** {box} / |money of a project plugin: its PHP function (smarty_block_box() in block.box.php, a registerPlugin callback). */
    private fun pluginTarget(token: PsiElement): PsiElement? {
        val plugins = when {
            SmartyPsiUtil.isType(token, TAG_NAME) -> SmartyPlugins.findTag(token.project, token.text)
            SmartyPsiUtil.isType(token, MODIFIER_NAME) -> SmartyPlugins.findModifier(token.project, token.text)
            else -> return null
        }
        val target = plugins.firstOrNull()?.target ?: return null
        if (target !is PsiFile) return target
        return PsiTreeUtil.findChildrenOfType(target, Function::class.java).firstOrNull { it.name.startsWith("smarty_") }
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
