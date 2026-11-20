package com.smartyplugin.lang

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Variables a template receives from outside, e.g. from the PHP code that renders it
 * (`$this->render('index', ['user' => $user])`, `$smarty->assign('user', $user)`).
 */
interface SmartyVariableProvider {
    /**
     * @param name   variable name without '$'
     * @param type   short presentable type ("User", "string[]"), or null when unknown
     * @param target where the variable is passed in, for navigation
     */
    data class Variable(val name: String, val type: String?, val target: PsiElement)

    fun getVariables(template: PsiFile): List<Variable>

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<SmartyVariableProvider> =
            ExtensionPointName.create("com.smartyplugin.variableProvider")

        @JvmStatic
        fun all(template: PsiFile): List<Variable> =
            EP_NAME.extensionList.flatMap { it.getVariables(template) }
    }
}
