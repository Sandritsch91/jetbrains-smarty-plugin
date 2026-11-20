package com.smartyplugin.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.util.text.StringUtil
import com.smartyplugin.lang.SmartyIcons
import javax.swing.Icon

class SmartyColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = SmartyIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = SmartySyntaxHighlighter()

    /** Kept in a resource file: the sample is full of `$variables`, which a Kotlin string would interpolate. */
    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Smarty"

    private companion object {
        val DEMO_TEXT: String by lazy {
            val text = SmartyColorSettingsPage::class.java.getResource("/colorSettings/SmartyDemo.tpl")!!.readText()
            StringUtil.convertLineSeparators(text).trimEnd('\n')
        }

        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Delimiters", SmartySyntaxHighlighter.DELIMITER),
            AttributesDescriptor("Tag name", SmartySyntaxHighlighter.TAG_NAME_KEY),
            AttributesDescriptor("Attribute", SmartySyntaxHighlighter.ATTRIBUTE),
            AttributesDescriptor("Modifier", SmartySyntaxHighlighter.MODIFIER_KEY),
            AttributesDescriptor("Variable", SmartySyntaxHighlighter.VARIABLE_KEY),
            AttributesDescriptor("Config variable", SmartySyntaxHighlighter.CONFIG_VARIABLE),
            AttributesDescriptor("Identifier", SmartySyntaxHighlighter.IDENTIFIER_KEY),
            AttributesDescriptor("Property (.title, ->name)", SmartySyntaxHighlighter.PROPERTY_KEY),
            AttributesDescriptor("Method call (->isGuest())", SmartySyntaxHighlighter.METHOD_CALL_KEY),
            AttributesDescriptor("Static method call (Html::encode())", SmartySyntaxHighlighter.STATIC_METHOD_CALL_KEY),
            AttributesDescriptor("Class constant (Select2::THEME)", SmartySyntaxHighlighter.CLASS_CONSTANT_KEY),
            AttributesDescriptor("Class name (Html::)", SmartySyntaxHighlighter.CLASS_REFERENCE_KEY),
            AttributesDescriptor("Function call (count())", SmartySyntaxHighlighter.FUNCTION_CALL_KEY),
            AttributesDescriptor("String", SmartySyntaxHighlighter.STRING_KEY),
            AttributesDescriptor("Number", SmartySyntaxHighlighter.NUMBER_KEY),
            AttributesDescriptor("Keyword / constant", SmartySyntaxHighlighter.KEYWORD_KEY),
            AttributesDescriptor("Operator", SmartySyntaxHighlighter.OPERATOR_KEY),
            AttributesDescriptor("Punctuation", SmartySyntaxHighlighter.PUNCTUATION),
            AttributesDescriptor("Parentheses", SmartySyntaxHighlighter.PARENTHESES),
            AttributesDescriptor("Brackets", SmartySyntaxHighlighter.BRACKETS),
            AttributesDescriptor("Comment", SmartySyntaxHighlighter.COMMENT_KEY),
            AttributesDescriptor("Bad character", SmartySyntaxHighlighter.BAD_CHAR),
        )
    }
}
