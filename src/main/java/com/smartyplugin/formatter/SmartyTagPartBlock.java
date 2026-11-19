package com.smartyplugin.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A token inside a Smarty tag, or a tag nested in another tag's expression, laid out by {@link SmartyTagLayout}. */
final class SmartyTagPartBlock extends AbstractBlock {
    private final Indent indent;
    private final CodeStyleSettings settings;

    SmartyTagPartBlock(@NotNull ASTNode node, @NotNull Indent indent, @NotNull CodeStyleSettings settings) {
        super(node, null, null);
        this.indent = indent;
        this.settings = settings;
    }

    @Override
    protected List<Block> buildChildren() {
        return SmartyTagLayout.isTag(myNode) ? SmartyTagLayout.buildChildren(myNode, settings) : EMPTY;
    }

    @Override
    public boolean isLeaf() {
        return !SmartyTagLayout.isTag(myNode);
    }

    @Override
    public Indent getIndent() {
        return indent;
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        return SmartyTagLayout.spacing(child1, child2);
    }

    @Override
    public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
        return new ChildAttributes(Indent.getNormalIndent(), null);
    }
}
