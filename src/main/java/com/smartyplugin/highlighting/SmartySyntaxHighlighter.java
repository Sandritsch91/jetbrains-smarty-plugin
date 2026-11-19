package com.smartyplugin.highlighting;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.smartyplugin.lexer.SmartyLexer;
import com.smartyplugin.lexer.SmartyStringLexer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;
import static com.smartyplugin.lexer.SmartyTokenTypes.*;

public final class SmartySyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey DELIMITER =
            createTextAttributesKey("SMARTY_DELIMITER", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey TAG_NAME_KEY =
            createTextAttributesKey("SMARTY_TAG_NAME", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey ATTRIBUTE =
            createTextAttributesKey("SMARTY_ATTRIBUTE", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE);
    public static final TextAttributesKey MODIFIER_KEY =
            createTextAttributesKey("SMARTY_MODIFIER", DefaultLanguageHighlighterColors.STATIC_METHOD);
    public static final TextAttributesKey VARIABLE_KEY =
            createTextAttributesKey("SMARTY_VARIABLE", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey CONFIG_VARIABLE =
            createTextAttributesKey("SMARTY_CONFIG_VARIABLE", DefaultLanguageHighlighterColors.STATIC_FIELD);
    public static final TextAttributesKey IDENTIFIER_KEY =
            createTextAttributesKey("SMARTY_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey STRING_KEY =
            createTextAttributesKey("SMARTY_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey NUMBER_KEY =
            createTextAttributesKey("SMARTY_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey KEYWORD_KEY =
            createTextAttributesKey("SMARTY_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey OPERATOR_KEY =
            createTextAttributesKey("SMARTY_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey PUNCTUATION =
            createTextAttributesKey("SMARTY_PUNCTUATION", DefaultLanguageHighlighterColors.DOT);
    public static final TextAttributesKey PARENTHESES =
            createTextAttributesKey("SMARTY_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("SMARTY_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey COMMENT_KEY =
            createTextAttributesKey("SMARTY_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey BAD_CHAR =
            createTextAttributesKey("SMARTY_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

    private static final Map<IElementType, TextAttributesKey> KEYS = new HashMap<>();

    static {
        KEYS.put(LDELIM, DELIMITER);
        KEYS.put(RDELIM, DELIMITER);
        KEYS.put(SLASH, DELIMITER);
        KEYS.put(BACKTICK, DELIMITER);
        KEYS.put(TAG_NAME, TAG_NAME_KEY);
        KEYS.put(ATTR_NAME, ATTRIBUTE);
        KEYS.put(MODIFIER_NAME, MODIFIER_KEY);
        KEYS.put(VARIABLE, VARIABLE_KEY);
        KEYS.put(CONFIG_VAR, CONFIG_VARIABLE);
        KEYS.put(IDENTIFIER, IDENTIFIER_KEY);
        KEYS.put(STRING, STRING_KEY);
        KEYS.put(NUMBER, NUMBER_KEY);
        KEYS.put(KEYWORD, KEYWORD_KEY);
        KEYS.put(CONSTANT, KEYWORD_KEY);
        KEYS.put(OPERATOR, OPERATOR_KEY);
        KEYS.put(ASSIGN, OPERATOR_KEY);
        KEYS.put(PIPE, OPERATOR_KEY);
        KEYS.put(AT, OPERATOR_KEY);
        KEYS.put(ARROW, PUNCTUATION);
        KEYS.put(DOUBLE_ARROW, OPERATOR_KEY);
        KEYS.put(DOUBLE_COLON, PUNCTUATION);
        KEYS.put(COLON, PUNCTUATION);
        KEYS.put(DOT, PUNCTUATION);
        KEYS.put(COMMA, PUNCTUATION);
        KEYS.put(LPAREN, PARENTHESES);
        KEYS.put(RPAREN, PARENTHESES);
        KEYS.put(LBRACKET, BRACKETS);
        KEYS.put(RBRACKET, BRACKETS);
        KEYS.put(COMMENT, COMMENT_KEY);
        KEYS.put(BAD_CHARACTER, BAD_CHAR);
    }

    private final com.smartyplugin.lang.SmartyDelimiters delimiters;

    public SmartySyntaxHighlighter() {
        this(com.smartyplugin.lang.SmartyDelimiters.DEFAULT);
    }

    public SmartySyntaxHighlighter(@NotNull com.smartyplugin.lang.SmartyDelimiters delimiters) {
        this.delimiters = delimiters;
    }

    /** The Smarty lexer, with double-quoted strings split into text and embedded variables / tags. */
    @Override
    public @NotNull Lexer getHighlightingLexer() {
        LayeredLexer lexer = new LayeredLexer(new SmartyLexer(delimiters));
        lexer.registerLayer(new SmartyStringLexer(delimiters), STRING);
        return lexer;
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return pack(KEYS.get(tokenType));
    }
}
