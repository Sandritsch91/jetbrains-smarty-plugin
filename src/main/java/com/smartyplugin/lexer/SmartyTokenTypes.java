package com.smartyplugin.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.OuterLanguageElementType;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.smartyplugin.lang.SmartyLanguage;

public interface SmartyTokenTypes {
    /** Text outside of Smarty tags (HTML etc.), also the body of {literal} blocks. */
    IElementType TEMPLATE_TEXT = new SmartyTokenType("TEMPLATE_TEXT");
    /** Smarty code as seen from the template data (HTML) tree. */
    IElementType OUTER_ELEMENT_TYPE = new OuterLanguageElementType("SMARTY_FRAGMENT", SmartyLanguage.INSTANCE);
    TemplateDataElementType TEMPLATE_DATA =
            new TemplateDataElementType("SMARTY_TEMPLATE_DATA", SmartyLanguage.INSTANCE, TEMPLATE_TEXT, OUTER_ELEMENT_TYPE);

    IElementType COMMENT = new SmartyTokenType("COMMENT");
    IElementType LDELIM = new SmartyTokenType("LDELIM");
    IElementType RDELIM = new SmartyTokenType("RDELIM");
    IElementType SLASH = new SmartyTokenType("SLASH");
    IElementType TAG_NAME = new SmartyTokenType("TAG_NAME");
    IElementType ATTR_NAME = new SmartyTokenType("ATTR_NAME");
    IElementType MODIFIER_NAME = new SmartyTokenType("MODIFIER_NAME");
    IElementType VARIABLE = new SmartyTokenType("VARIABLE");
    IElementType CONFIG_VAR = new SmartyTokenType("CONFIG_VAR");
    IElementType IDENTIFIER = new SmartyTokenType("IDENTIFIER");
    IElementType STRING = new SmartyTokenType("STRING");
    IElementType NUMBER = new SmartyTokenType("NUMBER");
    /** Word operators and loop keywords: eq, neq, and, or, not, mod, as, to, step, is, div, by ... */
    IElementType KEYWORD = new SmartyTokenType("KEYWORD");
    /** true, false, null */
    IElementType CONSTANT = new SmartyTokenType("CONSTANT");
    IElementType OPERATOR = new SmartyTokenType("OPERATOR");
    IElementType ASSIGN = new SmartyTokenType("ASSIGN");
    IElementType PIPE = new SmartyTokenType("PIPE");
    IElementType COLON = new SmartyTokenType("COLON");
    IElementType DOUBLE_COLON = new SmartyTokenType("DOUBLE_COLON");
    IElementType DOT = new SmartyTokenType("DOT");
    IElementType COMMA = new SmartyTokenType("COMMA");
    IElementType ARROW = new SmartyTokenType("ARROW");
    IElementType DOUBLE_ARROW = new SmartyTokenType("DOUBLE_ARROW");
    IElementType AT = new SmartyTokenType("AT");
    IElementType LPAREN = new SmartyTokenType("LPAREN");
    IElementType RPAREN = new SmartyTokenType("RPAREN");
    IElementType LBRACKET = new SmartyTokenType("LBRACKET");
    IElementType RBRACKET = new SmartyTokenType("RBRACKET");

    /** Delimits an expression embedded in a double-quoted string: "Hello `$user.name`". Highlighting only. */
    IElementType BACKTICK = new SmartyTokenType("BACKTICK");

    IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
    IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

    TokenSet COMMENTS = TokenSet.create(COMMENT);
    TokenSet STRINGS = TokenSet.create(STRING);
    TokenSet WHITESPACES = TokenSet.create(WHITE_SPACE);
}
