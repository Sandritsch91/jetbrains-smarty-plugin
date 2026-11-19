package com.smartyplugin.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.smartyplugin.lexer.SmartyTokenTypes.COMMENT;
import static com.smartyplugin.parser.SmartyElementTypes.BLOCK;

/** Folds multi-line blocks ({if}...{/if}, {foreach}..., {block}...) and multi-line comments. */
public final class SmartyFoldingBuilder extends FoldingBuilderEx implements DumbAware {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> result = new ArrayList<>();
        PsiTreeUtil.processElements(root, element -> {
            ASTNode node = element.getNode();
            if (node == null) return true;
            if (node.getElementType() == BLOCK) {
                ASTNode open = SmartyPsiUtil.openTag(node);
                ASTNode close = SmartyPsiUtil.closeTag(node);
                if (open != null && close != null) {
                    TextRange range = new TextRange(open.getTextRange().getEndOffset(), close.getStartOffset());
                    if (!range.isEmpty() && spansLines(document, range)) {
                        result.add(new FoldingDescriptor(node, range));
                    }
                }
            } else if (node.getElementType() == COMMENT && spansLines(document, node.getTextRange())) {
                result.add(new FoldingDescriptor(node, node.getTextRange()));
            }
            return true;
        });
        return result.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private static boolean spansLines(Document document, TextRange range) {
        return document.getLineNumber(range.getStartOffset()) < document.getLineNumber(range.getEndOffset());
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        if (node.getElementType() == COMMENT) {
            String text = node.getText().substring(2).trim();
            int nl = text.indexOf('\n');
            String first = (nl < 0 ? text : text.substring(0, nl)).replace("*}", "").trim();
            if (first.length() > 40) first = first.substring(0, 40) + "…";
            return "{* " + first + " … *}";
        }
        return "…";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
