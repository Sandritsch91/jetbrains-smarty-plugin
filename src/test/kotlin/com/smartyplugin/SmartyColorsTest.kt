package com.smartyplugin

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.smartyplugin.highlighting.SmartySyntaxHighlighter

/** With the PHP plugin, Smarty colors are the scheme's PHP colors. */
class SmartyColorsTest : BasePlatformTestCase() {

    /** Delimiters and attributes keep their own colors, independent of PHP. */
    fun testDelimiterAndAttributeKeepGenericColors() {
        assertEquals(DefaultLanguageHighlighterColors.BRACES, SmartySyntaxHighlighter.DELIMITER.fallbackAttributeKey)
        assertEquals(
            DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE,
            SmartySyntaxHighlighter.ATTRIBUTE.fallbackAttributeKey,
        )
    }

    fun testInheritsPhpColorsInDefaultAndDarcula() {
        for (schemeName in arrayOf("Default", "Darcula")) {
            val scheme = EditorColorsManager.getInstance().getScheme(schemeName)
            assertNotNull(schemeName, scheme)
            scheme!!
            for ((key, phpName) in PHP_BASE) {
                val smarty = scheme.getAttributes(key)
                val php = scheme.getAttributes(TextAttributesKey.find(phpName))
                assertEquals(schemeName + " " + key.externalName, php, smarty)
            }
        }
    }

    /** PHP variables and Smarty strings look different, so the comparison above means something. */
    fun testPhpColorsDiffer() {
        val scheme = EditorColorsManager.getInstance().getScheme("Default")!!
        assertFalse(
            scheme.getAttributes(TextAttributesKey.find("PHP_VAR")) ==
                scheme.getAttributes(TextAttributesKey.find("PHP_STRING")),
        )
    }

    private companion object {
        val PHP_BASE: Map<TextAttributesKey, String> = mapOf(
            SmartySyntaxHighlighter.VARIABLE_KEY to "PHP_VAR",
            SmartySyntaxHighlighter.STRING_KEY to "PHP_STRING",
            SmartySyntaxHighlighter.TAG_NAME_KEY to "PHP_KEYWORD",
            SmartySyntaxHighlighter.MODIFIER_KEY to "PHP_FUNCTION_CALL",
            SmartySyntaxHighlighter.COMMENT_KEY to "PHP_COMMENT",
            SmartySyntaxHighlighter.PROPERTY_KEY to "PHP_INSTANCE_FIELD",
            SmartySyntaxHighlighter.METHOD_CALL_KEY to "PHP_INSTANCE_METHOD",
            SmartySyntaxHighlighter.STATIC_METHOD_CALL_KEY to "PHP_STATIC_METHOD",
            SmartySyntaxHighlighter.CLASS_REFERENCE_KEY to "PHP_CLASS",
        )
    }
}
