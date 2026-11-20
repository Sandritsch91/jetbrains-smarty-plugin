package com.smartyplugin.formatter

import com.intellij.formatting.Alignment
import com.intellij.formatting.Wrap
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock
import com.intellij.formatting.templateLanguages.TemplateLanguageFormattingModelBuilder
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.templateLanguages.OuterLanguageElement

/**
 * Formats the template data language (HTML) with its own formatter and indents the bodies of Smarty
 * blocks ({if}, {foreach}, {block}, ...) on top of it.
 */
class SmartyFormattingModelBuilder : TemplateLanguageFormattingModelBuilder() {

    override fun createTemplateLanguageBlock(
        node: ASTNode,
        wrap: Wrap?,
        alignment: Alignment?,
        foreignChildren: List<DataLanguageBlockWrapper>?,
        codeStyleSettings: CodeStyleSettings,
    ): TemplateLanguageBlock = SmartyFormatterBlock(this, codeStyleSettings, node, foreignChildren)

    override fun dontFormatMyModel(): Boolean = false

    /**
     * The HTML formatter asks this for every Smarty fragment it meets in the HTML tree. Those fragments are
     * handled by this builder from the Smarty side, so the HTML formatter must not build a nested model for them.
     */
    override fun dontFormatMyModel(element: PsiElement): Boolean = element is OuterLanguageElement
}
