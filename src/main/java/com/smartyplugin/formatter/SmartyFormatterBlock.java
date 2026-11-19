package com.smartyplugin.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.templateLanguages.BlockWithParent;
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper;
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock;
import com.intellij.formatting.templateLanguages.TemplateLanguageBlockFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.psi.formatter.xml.SyntheticBlock;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.smartyplugin.lexer.SmartyTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

import static com.smartyplugin.lexer.SmartyTokenTypes.COMMENT;
import static com.smartyplugin.parser.SmartyElementTypes.*;

final class SmartyFormatterBlock extends TemplateLanguageBlock {

    SmartyFormatterBlock(@NotNull TemplateLanguageBlockFactory factory, @NotNull CodeStyleSettings settings,
                         @NotNull ASTNode node, @Nullable List<DataLanguageBlockWrapper> foreignChildren) {
        super(factory, settings, node, foreignChildren);
    }

    @Override
    protected IElementType getTemplateTextElementType() {
        return SmartyTokenTypes.TEMPLATE_TEXT;
    }

    private boolean isAtomic() {
        IElementType type = myNode.getElementType();
        return TAGS.contains(type) || type == COMMENT;
    }

    @Override
    public boolean isLeaf() {
        return isAtomic() || super.isLeaf();
    }

    @Override
    protected List<Block> buildChildren() {
        if (isAtomic()) return EMPTY;
        return super.buildChildren();
    }

    /*
     * Indentation model:
     *  - a block body is indented relative to its block, unless the block sits directly inside an HTML
     *    tag: then the HTML children of the body already carry the tag's indent, and Smarty children of the
     *    body get a normal indent themselves so that they line up with their HTML siblings.
     *  - a Smarty element that is a direct child of an HTML tag is indented like an HTML child would be.
     */
    @Override
    public Indent getIndent() {
        if (myNode.getText().isBlank()) return Indent.getNoneIndent();
        IElementType type = myNode.getElementType();
        BlockWithParent parent = getParent();

        if (parent instanceof DataLanguageBlockWrapper wrapper) {
            return indentInsideForeign(wrapper);
        }
        if (parent instanceof SmartyFormatterBlock smartyParent) {
            IElementType parentType = smartyParent.myNode.getElementType();
            if (type == BLOCK_BODY) {
                return smartyParent.isInsideHtmlTagContent() ? Indent.getNoneIndent() : Indent.getNormalIndent();
            }
            if (parentType == BLOCK_BODY) {
                return smartyParent.isInsideHtmlTagContent() ? Indent.getNormalIndent() : Indent.getNoneIndent();
            }
        }
        return Indent.getNoneIndent();
    }

    private Indent indentInsideForeign(DataLanguageBlockWrapper wrapper) {
        // synthetic HTML blocks only group a tag's children; the tag above them decides the indent
        BlockWithParent p = wrapper;
        while (p instanceof DataLanguageBlockWrapper w && w.getOriginal() instanceof SyntheticBlock) {
            p = w.getParent();
        }
        return p instanceof DataLanguageBlockWrapper w && w.getNode() instanceof XmlTag tag && indentsChildren(tag)
                ? Indent.getNormalIndent() : Indent.getNoneIndent();
    }

    /** Whether the nearest HTML ancestor of this block is a tag whose content is indented. */
    private boolean isInsideHtmlTagContent() {
        BlockWithParent p = getParent();
        while (p != null) {
            if (p instanceof DataLanguageBlockWrapper wrapper) {
                if (wrapper.getOriginal() instanceof SyntheticBlock) {
                    p = p.getParent();
                    continue;
                }
                return wrapper.getNode() instanceof XmlTag tag && indentsChildren(tag);
            }
            p = p.getParent();
        }
        return false;
    }

    private boolean indentsChildren(XmlTag tag) {
        HtmlCodeStyleSettings html = getSettings().getCustomSettings(HtmlCodeStyleSettings.class);
        String noIndent = html.HTML_DO_NOT_INDENT_CHILDREN_OF;
        if (noIndent == null || noIndent.isBlank()) return true;
        String name = tag.getName().toLowerCase(Locale.ROOT);
        for (String s : noIndent.split(",")) {
            if (s.trim().equalsIgnoreCase(name)) return false;
        }
        return true;
    }

    @Override
    public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
        IElementType type = myNode.getElementType();
        if (type == BLOCK) {
            return new ChildAttributes(Indent.getNormalIndent(), null);
        }
        if (type == BLOCK_BODY) {
            return new ChildAttributes(isInsideHtmlTagContent() ? Indent.getNormalIndent() : Indent.getNoneIndent(), null);
        }
        return new ChildAttributes(Indent.getNoneIndent(), null);
    }
}
