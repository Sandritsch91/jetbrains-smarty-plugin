package com.smartyplugin.lang

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutors
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.smartyplugin.lexer.SmartyTokenTypes

/** A Smarty file is two PSI trees over one document: Smarty and its template data language (HTML by default). */
class SmartyFileViewProvider private constructor(
    manager: PsiManager,
    file: VirtualFile,
    eventSystemEnabled: Boolean,
    private val templateDataLanguage: Language,
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled),
    ConfigurableTemplateLanguageFileViewProvider {

    constructor(manager: PsiManager, file: VirtualFile, eventSystemEnabled: Boolean) :
        this(manager, file, eventSystemEnabled, computeTemplateDataLanguage(manager.project, file))

    override fun getBaseLanguage(): Language = SmartyLanguage

    override fun getTemplateDataLanguage(): Language = templateDataLanguage

    override fun getLanguages(): Set<Language> = setOf(SmartyLanguage, templateDataLanguage)

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
        SmartyFileViewProvider(manager, fileCopy, false, templateDataLanguage)

    override fun createFile(lang: Language): PsiFile? {
        val definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null
        if (lang === templateDataLanguage) {
            val file = definition.createFile(this) as PsiFileImpl
            file.contentElementType = SmartyTokenTypes.TEMPLATE_DATA
            return file
        }
        if (lang.isKindOf(baseLanguage)) {
            return definition.createFile(this)
        }
        return null
    }

    companion object {
        @JvmStatic
        fun computeTemplateDataLanguage(project: Project?, file: VirtualFile?): Language {
            var language: Language? = null
            if (project != null && file != null) {
                language = TemplateDataLanguageMappings.getInstance(project).getMapping(file)
            }
            if (language == null || language is TemplateLanguage) {
                language = HTMLLanguage.INSTANCE
            }
            if (project != null && file != null) {
                val substituted = LanguageSubstitutors.getInstance().substituteLanguage(language, file, project)
                if (substituted !is TemplateLanguage) language = substituted
            }
            return language
        }
    }
}
