package com.smartyplugin.highlighting

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.lexer.SmartyTokenTypes

/** Smarty tokens on top, the template data language (HTML, with its JS/CSS) highlighted in between. */
class SmartyTemplateHighlighter(project: Project?, file: VirtualFile?, scheme: EditorColorsScheme) :
    LayeredLexerEditorHighlighter(SmartySyntaxHighlighter(SmartyDelimiters.of(project)), scheme) {

    init {
        val dataLanguage = SmartyFileViewProvider.computeTemplateDataLanguage(project, file)
        val dataType: FileType = dataLanguage.associatedFileType ?: HtmlFileType.INSTANCE
        val outer = SyntaxHighlighterFactory.getSyntaxHighlighter(dataType, project, file)
        if (outer != null) {
            registerLayer(SmartyTokenTypes.TEMPLATE_TEXT, LayerDescriptor(outer, ""))
        }
    }

    class Provider : EditorHighlighterProvider {
        override fun getEditorHighlighter(
            project: Project?,
            fileType: FileType,
            virtualFile: VirtualFile?,
            colors: EditorColorsScheme,
        ): EditorHighlighter = SmartyTemplateHighlighter(project, virtualFile, colors)
    }
}
