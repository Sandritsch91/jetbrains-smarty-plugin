package com.smartyplugin.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.LeafElement

/**
 * A stretch of text inside `{registerJs}` / `{registerCss}` (yii2-smarty). It hosts the injected
 * JavaScript or CSS; Smarty tags between such stretches stay Smarty.
 */
class SmartyCodeText(node: ASTNode) : SmartyPsiElement(node), PsiLanguageInjectionHost {
    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost {
        (node.firstChildNode as? LeafElement)?.replaceWithText(text)
        return this
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}
