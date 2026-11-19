package com.smartyplugin.lang;

import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import org.jetbrains.annotations.NotNull;

public final class SmartyLanguage extends Language implements TemplateLanguage {
    /** Language ID. Deliberately not "Smarty" to avoid clashing with JetBrains' own Smarty plugin. */
    public static final String ID = "SmartyTpl";
    public static final SmartyLanguage INSTANCE = new SmartyLanguage();

    private SmartyLanguage() {
        super(ID);
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Smarty";
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }
}
