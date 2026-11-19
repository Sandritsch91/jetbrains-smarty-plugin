package com.smartyplugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SmartyStringManipulator extends AbstractElementManipulator<SmartyStringLiteral> {
    @Override
    public @Nullable SmartyStringLiteral handleContentChange(@NotNull SmartyStringLiteral element,
                                                             @NotNull TextRange range,
                                                             String newContent) throws IncorrectOperationException {
        String oldText = element.getText();
        String newText = range.replace(oldText, newContent);
        ASTNode leaf = element.getNode().getFirstChildNode();
        if (leaf instanceof LeafElement leafElement) {
            leafElement.replaceWithText(newText);
            return element;
        }
        throw new IncorrectOperationException("Unexpected string literal structure");
    }

    @Override
    public @NotNull TextRange getRangeInElement(@NotNull SmartyStringLiteral element) {
        String text = element.getText();
        if (text.length() >= 2 && text.charAt(text.length() - 1) == text.charAt(0)) {
            return new TextRange(1, text.length() - 1);
        }
        return new TextRange(Math.min(1, text.length()), text.length());
    }
}
