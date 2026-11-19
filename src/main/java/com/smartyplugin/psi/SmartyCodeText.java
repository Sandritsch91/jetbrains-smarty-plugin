package com.smartyplugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;

/**
 * A stretch of text inside <code>{registerJs}</code> / <code>{registerCss}</code> (yii2-smarty). It hosts the injected
 * JavaScript or CSS; Smarty tags between such stretches stay Smarty.
 */
public final class SmartyCodeText extends SmartyPsiElement implements PsiLanguageInjectionHost {
    public SmartyCodeText(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public boolean isValidHost() {
        return true;
    }

    @Override
    public PsiLanguageInjectionHost updateText(@NotNull String text) {
        ASTNode leaf = getNode().getFirstChildNode();
        if (leaf instanceof LeafElement element) element.replaceWithText(text);
        return this;
    }

    @Override
    public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
        return LiteralTextEscaper.createSimple(this);
    }
}
