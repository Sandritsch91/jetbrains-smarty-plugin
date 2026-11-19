package com.smartyplugin.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.TemplateLanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class SmartyFileType extends LanguageFileType implements TemplateLanguageFileType {
    public static final SmartyFileType INSTANCE = new SmartyFileType();
    public static final String NAME = "Smarty template";

    private SmartyFileType() {
        super(SmartyLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return NAME;
    }

    @Override
    public @NotNull String getDescription() {
        return "Smarty template";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "tpl";
    }

    @Override
    public Icon getIcon() {
        return SmartyIcons.FILE;
    }
}
