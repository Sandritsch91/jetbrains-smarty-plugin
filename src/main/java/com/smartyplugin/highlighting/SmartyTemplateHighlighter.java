package com.smartyplugin.highlighting;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.highlighter.HtmlFileType;
import com.smartyplugin.lang.SmartyFileViewProvider;
import com.smartyplugin.lexer.SmartyTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Smarty tokens on top, the template data language (HTML, with its JS/CSS) highlighted in between. */
public final class SmartyTemplateHighlighter extends LayeredLexerEditorHighlighter {

    public SmartyTemplateHighlighter(@Nullable Project project, @Nullable VirtualFile file, @NotNull EditorColorsScheme scheme) {
        super(new SmartySyntaxHighlighter(), scheme);
        Language dataLanguage = SmartyFileViewProvider.computeTemplateDataLanguage(project, file);
        FileType dataType = dataLanguage.getAssociatedFileType();
        if (dataType == null) dataType = HtmlFileType.INSTANCE;
        SyntaxHighlighter outer = SyntaxHighlighterFactory.getSyntaxHighlighter(dataType, project, file);
        if (outer != null) {
            registerLayer(SmartyTokenTypes.TEMPLATE_TEXT, new LayerDescriptor(outer, ""));
        }
    }

    public static final class Provider implements EditorHighlighterProvider {
        @Override
        public EditorHighlighter getEditorHighlighter(@Nullable Project project, @NotNull FileType fileType,
                                                      @Nullable VirtualFile virtualFile, @NotNull EditorColorsScheme colors) {
            return new SmartyTemplateHighlighter(project, virtualFile, colors);
        }
    }
}
