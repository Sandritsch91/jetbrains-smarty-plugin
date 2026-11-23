package com.smartyplugin.php

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Ctrl+Click in PHP code on the view of `$this->render('index', [...])` (Yii2) or on the template of
 * `$smarty->display('page.tpl')` opens the Smarty template.
 */
class SmartyPhpViewGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (source == null || !source.language.isKindOf(PhpLanguage.INSTANCE)) return null
        val literal = PsiTreeUtil.getParentOfType(source, StringLiteralExpression::class.java, false) ?: return null
        val targets = SmartyPhpRenderCalls.viewTargets(literal)
        return if (targets.isEmpty()) null else targets.toTypedArray()
    }
}
