package com.smartyplugin.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.smartyplugin.lang.SmartyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Map;

public final class SmartyColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Delimiters", SmartySyntaxHighlighter.DELIMITER),
            new AttributesDescriptor("Tag name", SmartySyntaxHighlighter.TAG_NAME_KEY),
            new AttributesDescriptor("Attribute", SmartySyntaxHighlighter.ATTRIBUTE),
            new AttributesDescriptor("Modifier", SmartySyntaxHighlighter.MODIFIER_KEY),
            new AttributesDescriptor("Variable", SmartySyntaxHighlighter.VARIABLE_KEY),
            new AttributesDescriptor("Config variable", SmartySyntaxHighlighter.CONFIG_VARIABLE),
            new AttributesDescriptor("Identifier", SmartySyntaxHighlighter.IDENTIFIER_KEY),
            new AttributesDescriptor("String", SmartySyntaxHighlighter.STRING_KEY),
            new AttributesDescriptor("Number", SmartySyntaxHighlighter.NUMBER_KEY),
            new AttributesDescriptor("Keyword / constant", SmartySyntaxHighlighter.KEYWORD_KEY),
            new AttributesDescriptor("Operator", SmartySyntaxHighlighter.OPERATOR_KEY),
            new AttributesDescriptor("Punctuation", SmartySyntaxHighlighter.PUNCTUATION),
            new AttributesDescriptor("Parentheses", SmartySyntaxHighlighter.PARENTHESES),
            new AttributesDescriptor("Brackets", SmartySyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Comment", SmartySyntaxHighlighter.COMMENT_KEY),
            new AttributesDescriptor("Bad character", SmartySyntaxHighlighter.BAD_CHAR),
    };

    @Override
    public @Nullable Icon getIcon() {
        return SmartyIcons.FILE;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new SmartySyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return """
                {* Page template *}
                {extends file="layouts/main.tpl"}
                {block name="content"}
                  <h1>{$page.title|escape:'html'|default:"Untitled"}</h1>
                  {if $user->isGuest() && #showLogin#}
                    {include file="partials/login.tpl" redirect=true}
                  {elseif $items|@count gt 0}
                    <ul>
                    {foreach $items as $key => $item}
                      <li class="{cycle values='odd,even'}">{$item@iteration}: {$item.name|truncate:30:"..."}</li>
                    {foreachelse}
                      <li>No items</li>
                    {/foreach}
                    </ul>
                  {/if}
                {/block}""";
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Smarty";
    }
}
