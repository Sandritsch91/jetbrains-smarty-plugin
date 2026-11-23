package com.smartyplugin.editor

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.smartyplugin.lang.SmartyFileViewProvider

/** Live templates ({if}, {foreach}, {block} ...) apply anywhere in a Smarty template. */
class SmartyTemplateContextType : TemplateContextType("Smarty") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean =
        templateActionContext.file.viewProvider is SmartyFileViewProvider
}
