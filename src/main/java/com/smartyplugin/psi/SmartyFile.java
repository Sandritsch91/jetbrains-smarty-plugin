package com.smartyplugin.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.smartyplugin.lang.SmartyFileType;
import com.smartyplugin.lang.SmartyLanguage;
import org.jetbrains.annotations.NotNull;

public final class SmartyFile extends PsiFileBase {
    public SmartyFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, SmartyLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return SmartyFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Smarty file";
    }
}
