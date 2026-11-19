package com.smartyplugin.lang;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.smartyplugin.lexer.SmartyTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** A Smarty file is two PSI trees over one document: Smarty and its template data language (HTML by default). */
public final class SmartyFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider
        implements ConfigurableTemplateLanguageFileViewProvider {

    private final Language templateDataLanguage;

    public SmartyFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file, boolean eventSystemEnabled) {
        this(manager, file, eventSystemEnabled, computeTemplateDataLanguage(manager.getProject(), file));
    }

    private SmartyFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file, boolean eventSystemEnabled,
                                   @NotNull Language templateDataLanguage) {
        super(manager, file, eventSystemEnabled);
        this.templateDataLanguage = templateDataLanguage;
    }

    public static @NotNull Language computeTemplateDataLanguage(@Nullable Project project, @Nullable VirtualFile file) {
        Language language = null;
        if (project != null && file != null) {
            language = TemplateDataLanguageMappings.getInstance(project).getMapping(file);
        }
        if (language == null || language instanceof TemplateLanguage) {
            language = HTMLLanguage.INSTANCE;
        }
        if (project != null && file != null) {
            Language substituted = LanguageSubstitutors.getInstance().substituteLanguage(language, file, project);
            if (!(substituted instanceof TemplateLanguage)) language = substituted;
        }
        return language;
    }

    @Override
    public @NotNull Language getBaseLanguage() {
        return SmartyLanguage.INSTANCE;
    }

    @Override
    public @NotNull Language getTemplateDataLanguage() {
        return templateDataLanguage;
    }

    @Override
    public @NotNull Set<Language> getLanguages() {
        return Set.of(SmartyLanguage.INSTANCE, templateDataLanguage);
    }

    @Override
    protected @NotNull MultiplePsiFilesPerDocumentFileViewProvider cloneInner(@NotNull VirtualFile fileCopy) {
        return new SmartyFileViewProvider(getManager(), fileCopy, false, templateDataLanguage);
    }

    @Override
    protected @Nullable PsiFile createFile(@NotNull Language lang) {
        ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
        if (definition == null) return null;
        if (lang == templateDataLanguage) {
            PsiFileImpl file = (PsiFileImpl) definition.createFile(this);
            file.setContentElementType(SmartyTokenTypes.TEMPLATE_DATA);
            return file;
        }
        if (lang.isKindOf(getBaseLanguage())) {
            return definition.createFile(this);
        }
        return null;
    }
}
