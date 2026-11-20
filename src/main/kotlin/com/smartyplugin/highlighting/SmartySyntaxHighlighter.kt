package com.smartyplugin.highlighting

import com.intellij.lexer.LayeredLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lexer.SmartyLexer
import com.smartyplugin.lexer.SmartyMemberTokens
import com.smartyplugin.lexer.SmartyStringLexer
import com.smartyplugin.lexer.SmartyTokenTypes

class SmartySyntaxHighlighter @JvmOverloads constructor(
    private val delimiters: SmartyDelimiters = SmartyDelimiters.DEFAULT,
) : SyntaxHighlighterBase() {

    /** The Smarty lexer, with double-quoted strings split into text and embedded variables / tags. */
    override fun getHighlightingLexer(): Lexer {
        val lexer = LayeredLexer(SmartyMemberTokens.HighlightingLexer(SmartyLexer(delimiters)))
        lexer.registerLayer(SmartyStringLexer(delimiters), SmartyTokenTypes.STRING)
        return lexer
    }

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = pack(KEYS[tokenType])

    companion object {
        /**
         * With the PHP plugin, Smarty colors inherit the scheme's PHP colors (a Smarty expression is PHP); without it,
         * the generic language colors. Declared before the keys: initialization runs top to bottom.
         */
        private val PHP_COLORS = mapOf(
            "SMARTY_TAG_NAME" to "PHP_KEYWORD",
            "SMARTY_MODIFIER" to "PHP_FUNCTION_CALL",
            "SMARTY_VARIABLE" to "PHP_VAR",
            "SMARTY_CONFIG_VARIABLE" to "PHP_CONSTANT",
            "SMARTY_IDENTIFIER" to "PHP_IDENTIFIER",
            "SMARTY_PROPERTY" to "PHP_INSTANCE_FIELD",
            "SMARTY_METHOD_CALL" to "PHP_INSTANCE_METHOD",
            "SMARTY_STATIC_METHOD_CALL" to "PHP_STATIC_METHOD",
            "SMARTY_CLASS_CONSTANT" to "PHP_CONSTANT",
            "SMARTY_CLASS_REFERENCE" to "PHP_CLASS",
            "SMARTY_FUNCTION_CALL" to "PHP_FUNCTION_CALL",
            "SMARTY_STRING" to "PHP_STRING",
            "SMARTY_NUMBER" to "PHP_NUMBER",
            "SMARTY_KEYWORD" to "PHP_KEYWORD",
            "SMARTY_OPERATOR" to "PHP_OPERATION_SIGN",
            "SMARTY_PUNCTUATION" to "PHP_OPERATION_SIGN",
            "SMARTY_PARENTHESES" to "PHP_PARENTHESES",
            "SMARTY_BRACKETS" to "PHP_BRACKETS",
            "SMARTY_COMMENT" to "PHP_COMMENT",
            "SMARTY_BAD_CHARACTER" to "PHP_BAD_CHARACTER",
        )

        /** Whether the PHP plugin's colors exist; loading its color class also registers its keys. */
        private val PHP: Boolean = loadPhpColors()

        private fun loadPhpColors(): Boolean = try {
            // only visible when the optional PHP dependency is present
            Class.forName(
                "com.jetbrains.php.lang.highlighter.PhpHighlightingData", true,
                SmartySyntaxHighlighter::class.java.classLoader,
            )
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        }

        private fun key(name: String, fallback: TextAttributesKey): TextAttributesKey {
            val php = PHP_COLORS[name]
            return createTextAttributesKey(name, if (PHP && php != null) TextAttributesKey.find(php) else fallback)
        }

        @JvmField val DELIMITER = key("SMARTY_DELIMITER", DefaultLanguageHighlighterColors.BRACES)
        @JvmField val TAG_NAME_KEY = key("SMARTY_TAG_NAME", DefaultLanguageHighlighterColors.KEYWORD)
        @JvmField val ATTRIBUTE = key("SMARTY_ATTRIBUTE", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE)
        @JvmField val MODIFIER_KEY = key("SMARTY_MODIFIER", DefaultLanguageHighlighterColors.STATIC_METHOD)
        @JvmField val VARIABLE_KEY = key("SMARTY_VARIABLE", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        @JvmField val CONFIG_VARIABLE = key("SMARTY_CONFIG_VARIABLE", DefaultLanguageHighlighterColors.STATIC_FIELD)
        @JvmField val IDENTIFIER_KEY = key("SMARTY_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        @JvmField val PROPERTY_KEY = key("SMARTY_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        @JvmField val METHOD_CALL_KEY = key("SMARTY_METHOD_CALL", DefaultLanguageHighlighterColors.INSTANCE_METHOD)
        @JvmField val STATIC_METHOD_CALL_KEY =
            key("SMARTY_STATIC_METHOD_CALL", DefaultLanguageHighlighterColors.STATIC_METHOD)
        @JvmField val CLASS_CONSTANT_KEY = key("SMARTY_CLASS_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT)
        @JvmField val CLASS_REFERENCE_KEY =
            key("SMARTY_CLASS_REFERENCE", DefaultLanguageHighlighterColors.CLASS_REFERENCE)
        @JvmField val FUNCTION_CALL_KEY = key("SMARTY_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL)
        @JvmField val STRING_KEY = key("SMARTY_STRING", DefaultLanguageHighlighterColors.STRING)
        @JvmField val NUMBER_KEY = key("SMARTY_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        @JvmField val KEYWORD_KEY = key("SMARTY_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        @JvmField val OPERATOR_KEY = key("SMARTY_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        @JvmField val PUNCTUATION = key("SMARTY_PUNCTUATION", DefaultLanguageHighlighterColors.DOT)
        @JvmField val PARENTHESES = key("SMARTY_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        @JvmField val BRACKETS = key("SMARTY_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        @JvmField val COMMENT_KEY = key("SMARTY_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        @JvmField val BAD_CHAR = key("SMARTY_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val KEYS: Map<IElementType, TextAttributesKey> = HashMap<IElementType, TextAttributesKey>().apply {
            put(SmartyTokenTypes.LDELIM, DELIMITER)
            put(SmartyTokenTypes.RDELIM, DELIMITER)
            put(SmartyTokenTypes.SLASH, DELIMITER)
            put(SmartyTokenTypes.BACKTICK, DELIMITER)
            put(SmartyTokenTypes.TAG_NAME, TAG_NAME_KEY)
            put(SmartyTokenTypes.ATTR_NAME, ATTRIBUTE)
            put(SmartyTokenTypes.MODIFIER_NAME, MODIFIER_KEY)
            put(SmartyTokenTypes.VARIABLE, VARIABLE_KEY)
            put(SmartyTokenTypes.CONFIG_VAR, CONFIG_VARIABLE)
            put(SmartyTokenTypes.IDENTIFIER, IDENTIFIER_KEY)
            put(SmartyMemberTokens.PROPERTY, PROPERTY_KEY)
            put(SmartyMemberTokens.METHOD_CALL, METHOD_CALL_KEY)
            put(SmartyMemberTokens.STATIC_METHOD_CALL, STATIC_METHOD_CALL_KEY)
            put(SmartyMemberTokens.CLASS_CONSTANT, CLASS_CONSTANT_KEY)
            put(SmartyMemberTokens.CLASS_REFERENCE, CLASS_REFERENCE_KEY)
            put(SmartyMemberTokens.FUNCTION_CALL, FUNCTION_CALL_KEY)
            put(SmartyTokenTypes.STRING, STRING_KEY)
            put(SmartyTokenTypes.NUMBER, NUMBER_KEY)
            put(SmartyTokenTypes.KEYWORD, KEYWORD_KEY)
            put(SmartyTokenTypes.CONSTANT, KEYWORD_KEY)
            put(SmartyTokenTypes.OPERATOR, OPERATOR_KEY)
            put(SmartyTokenTypes.ASSIGN, OPERATOR_KEY)
            put(SmartyTokenTypes.PIPE, OPERATOR_KEY)
            put(SmartyTokenTypes.AT, OPERATOR_KEY)
            put(SmartyTokenTypes.ARROW, PUNCTUATION)
            put(SmartyTokenTypes.DOUBLE_ARROW, OPERATOR_KEY)
            put(SmartyTokenTypes.DOUBLE_COLON, PUNCTUATION)
            put(SmartyTokenTypes.COLON, PUNCTUATION)
            put(SmartyTokenTypes.DOT, PUNCTUATION)
            put(SmartyTokenTypes.COMMA, PUNCTUATION)
            put(SmartyTokenTypes.LPAREN, PARENTHESES)
            put(SmartyTokenTypes.RPAREN, PARENTHESES)
            put(SmartyTokenTypes.LBRACKET, BRACKETS)
            put(SmartyTokenTypes.RBRACKET, BRACKETS)
            put(SmartyTokenTypes.COMMENT, COMMENT_KEY)
            put(SmartyTokenTypes.BAD_CHARACTER, BAD_CHAR)
        }
    }
}
