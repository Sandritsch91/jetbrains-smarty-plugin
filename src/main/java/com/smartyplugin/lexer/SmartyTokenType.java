package com.smartyplugin.lexer;

import com.intellij.psi.tree.IElementType;
import com.smartyplugin.lang.SmartyLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SmartyTokenType extends IElementType {
    public SmartyTokenType(@NotNull @NonNls String debugName) {
        super(debugName, SmartyLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "Smarty:" + super.toString();
    }
}
