package com.smartyplugin.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.tree.TokenSet
import com.smartyplugin.lang.SmartyLanguage

object SmartyTokenTypes {
    /** Text outside of Smarty tags (HTML etc.), also the body of {literal} blocks. */
    @JvmField val TEMPLATE_TEXT: IElementType = SmartyTokenType("TEMPLATE_TEXT")

    /** Smarty code as seen from the template data (HTML) tree. */
    @JvmField val OUTER_ELEMENT_TYPE: IElementType = OuterLanguageElementType("SMARTY_FRAGMENT", SmartyLanguage)

    @JvmField val TEMPLATE_DATA: TemplateDataElementType =
        TemplateDataElementType("SMARTY_TEMPLATE_DATA", SmartyLanguage, TEMPLATE_TEXT, OUTER_ELEMENT_TYPE)

    @JvmField val COMMENT: IElementType = SmartyTokenType("COMMENT")
    @JvmField val LDELIM: IElementType = SmartyTokenType("LDELIM")
    @JvmField val RDELIM: IElementType = SmartyTokenType("RDELIM")
    @JvmField val SLASH: IElementType = SmartyTokenType("SLASH")
    @JvmField val TAG_NAME: IElementType = SmartyTokenType("TAG_NAME")
    @JvmField val ATTR_NAME: IElementType = SmartyTokenType("ATTR_NAME")
    @JvmField val MODIFIER_NAME: IElementType = SmartyTokenType("MODIFIER_NAME")
    @JvmField val VARIABLE: IElementType = SmartyTokenType("VARIABLE")
    @JvmField val CONFIG_VAR: IElementType = SmartyTokenType("CONFIG_VAR")
    @JvmField val IDENTIFIER: IElementType = SmartyTokenType("IDENTIFIER")
    @JvmField val STRING: IElementType = SmartyTokenType("STRING")
    @JvmField val NUMBER: IElementType = SmartyTokenType("NUMBER")

    /** Word operators and loop keywords: eq, neq, and, or, not, mod, as, to, step, is, div, by ... */
    @JvmField val KEYWORD: IElementType = SmartyTokenType("KEYWORD")

    /** true, false, null */
    @JvmField val CONSTANT: IElementType = SmartyTokenType("CONSTANT")
    @JvmField val OPERATOR: IElementType = SmartyTokenType("OPERATOR")
    @JvmField val ASSIGN: IElementType = SmartyTokenType("ASSIGN")
    @JvmField val PIPE: IElementType = SmartyTokenType("PIPE")
    @JvmField val COLON: IElementType = SmartyTokenType("COLON")
    @JvmField val DOUBLE_COLON: IElementType = SmartyTokenType("DOUBLE_COLON")
    @JvmField val DOT: IElementType = SmartyTokenType("DOT")
    @JvmField val COMMA: IElementType = SmartyTokenType("COMMA")
    @JvmField val ARROW: IElementType = SmartyTokenType("ARROW")
    @JvmField val DOUBLE_ARROW: IElementType = SmartyTokenType("DOUBLE_ARROW")
    @JvmField val AT: IElementType = SmartyTokenType("AT")
    @JvmField val LPAREN: IElementType = SmartyTokenType("LPAREN")
    @JvmField val RPAREN: IElementType = SmartyTokenType("RPAREN")
    @JvmField val LBRACKET: IElementType = SmartyTokenType("LBRACKET")
    @JvmField val RBRACKET: IElementType = SmartyTokenType("RBRACKET")

    /** Delimits an expression embedded in a double-quoted string: "Hello `$user.name`". Highlighting only. */
    @JvmField val BACKTICK: IElementType = SmartyTokenType("BACKTICK")

    @JvmField val WHITE_SPACE: IElementType = TokenType.WHITE_SPACE
    @JvmField val BAD_CHARACTER: IElementType = TokenType.BAD_CHARACTER

    @JvmField val COMMENTS: TokenSet = TokenSet.create(COMMENT)
    @JvmField val STRINGS: TokenSet = TokenSet.create(STRING)
    @JvmField val WHITESPACES: TokenSet = TokenSet.create(WHITE_SPACE)
}
