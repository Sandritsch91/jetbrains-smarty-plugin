package com.smartyplugin.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.smartyplugin.lang.SmartyLanguage;
import com.smartyplugin.lexer.SmartyTokenType;

public interface SmartyElementTypes {
    IFileElementType FILE = new IFileElementType("SMARTY_FILE", SmartyLanguage.INSTANCE);

    /** {if ...} ... {elseif} ... {else} ... {/if} */
    IElementType BLOCK = new SmartyTokenType("BLOCK");
    /** Content between the tags of a block. */
    IElementType BLOCK_BODY = new SmartyTokenType("BLOCK_BODY");
    IElementType OPEN_TAG = new SmartyTokenType("OPEN_TAG");
    IElementType MIDDLE_TAG = new SmartyTokenType("MIDDLE_TAG");
    IElementType CLOSE_TAG = new SmartyTokenType("CLOSE_TAG");
    /** A function tag without a body: {include file="x.tpl"} */
    IElementType TAG = new SmartyTokenType("TAG");
    /** Output tag: {$var|escape} */
    IElementType PRINT_TAG = new SmartyTokenType("PRINT_TAG");
    IElementType ATTRIBUTE = new SmartyTokenType("ATTRIBUTE");
    IElementType MODIFIER = new SmartyTokenType("MODIFIER");
    IElementType STRING_LITERAL = new SmartyTokenType("STRING_LITERAL");
    /** Text inside {registerJs} / {registerCss}: hosts the injected JavaScript / CSS. */
    IElementType CODE_TEXT = new SmartyTokenType("CODE_TEXT");

    TokenSet TAGS = TokenSet.create(OPEN_TAG, MIDDLE_TAG, CLOSE_TAG, TAG, PRINT_TAG);
    TokenSet BLOCK_PARTS = TokenSet.create(OPEN_TAG, MIDDLE_TAG, CLOSE_TAG);
}
