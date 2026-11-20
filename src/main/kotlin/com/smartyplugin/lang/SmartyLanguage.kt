package com.smartyplugin.lang

import com.intellij.lang.Language
import com.intellij.psi.templateLanguages.TemplateLanguage

object SmartyLanguage : Language("SmartyTpl"), TemplateLanguage {
    /** Language ID. Deliberately not "Smarty" to avoid clashing with JetBrains' own Smarty plugin. */
    const val ID: String = "SmartyTpl"

    private fun readResolve(): Any = SmartyLanguage

    override fun getDisplayName(): String = "Smarty"

    override fun isCaseSensitive(): Boolean = true
}
