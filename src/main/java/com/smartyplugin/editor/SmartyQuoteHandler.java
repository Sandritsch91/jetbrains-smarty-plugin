package com.smartyplugin.editor;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.smartyplugin.lexer.SmartyTokenTypes;

public final class SmartyQuoteHandler extends SimpleTokenSetQuoteHandler {
    public SmartyQuoteHandler() {
        super(SmartyTokenTypes.STRING);
    }
}
