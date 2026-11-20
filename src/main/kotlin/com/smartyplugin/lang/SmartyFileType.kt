package com.smartyplugin.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.TemplateLanguageFileType
import javax.swing.Icon

object SmartyFileType : LanguageFileType(SmartyLanguage), TemplateLanguageFileType {
    const val NAME: String = "Smarty template"

    override fun getName(): String = NAME

    override fun getDescription(): String = "Smarty template"

    override fun getDefaultExtension(): String = "tpl"

    override fun getIcon(): Icon = SmartyIcons.FILE
}
