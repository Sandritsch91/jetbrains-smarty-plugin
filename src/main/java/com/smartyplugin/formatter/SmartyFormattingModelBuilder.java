package com.smartyplugin.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper;
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock;
import com.intellij.formatting.templateLanguages.TemplateLanguageFormattingModelBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Formats the template data language (HTML) with its own formatter and indents the bodies of Smarty
 * blocks ({if}, {foreach}, {block}, ...) on top of it.
 */
public final class SmartyFormattingModelBuilder extends TemplateLanguageFormattingModelBuilder {

    @Override
    public @NotNull TemplateLanguageBlock createTemplateLanguageBlock(@NotNull ASTNode node,
                                                                     @Nullable Wrap wrap,
                                                                     @Nullable Alignment alignment,
                                                                     @Nullable List<DataLanguageBlockWrapper> foreignChildren,
                                                                     @NotNull CodeStyleSettings codeStyleSettings) {
        return new SmartyFormatterBlock(this, codeStyleSettings, node, foreignChildren);
    }

    @Override
    public boolean dontFormatMyModel() {
        return false;
    }

    /**
     * The HTML formatter asks this for every Smarty fragment it meets in the HTML tree. Those fragments are
     * handled by this builder from the Smarty side, so the HTML formatter must not build a nested model for them.
     */
    @Override
    public boolean dontFormatMyModel(@NotNull PsiElement element) {
        return element instanceof OuterLanguageElement;
    }
}
