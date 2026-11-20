package com.smartyplugin.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.lang.SmartyVariableProvider
import com.smartyplugin.lexer.SmartyTokenTypes.KEYWORD
import com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG
import com.smartyplugin.psi.SmartyPsiUtil

/** Ctrl+Click on `$name` goes to where the template receives it (e.g. the PHP render call). */
class SmartyGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (source == null) return null
        val file = source.containingFile
        if (file == null || file.viewProvider !is SmartyFileViewProvider) return null
        val token = SmartyPsiUtil.smartyElementAt(file, offset)
        if (token == null || !SmartyPsiUtil.isType(token, VARIABLE)) return null
        val name = token.text.substring(1)
        val local = templateDefinition(token, name)
        if (local != null) return arrayOf(local)
        val targets = SmartyVariableProvider.all(token.containingFile).filter { it.name == name }.map { it.target }
        return if (targets.isEmpty()) null else targets.toTypedArray()
    }

    private companion object {
        val DEFINING_ATTRIBUTES = setOf("assign", "var", "item", "key")

        /**
         * The closest definition before the usage in the template itself: assign="form" of a block widget,
         * {assign var="x"}, {foreach from=$a item="x"}; for the shorthand {foreach $a as $x} the $x of the tag.
         */
        fun templateDefinition(usage: PsiElement, name: String): PsiElement? {
            var best: PsiElement? = null
            val usageStart = usage.textRange.startOffset
            val candidates = PsiTreeUtil.collectElements(usage.containingFile) {
                SmartyPsiUtil.isType(it, ATTRIBUTE) || SmartyPsiUtil.isType(it, OPEN_TAG)
            }
            for (e in candidates) {
                if (e.textRange.startOffset >= usageStart) continue
                val candidate = if (SmartyPsiUtil.isType(e, ATTRIBUTE)) attributeDefinition(e, name) else foreachItem(e, name)
                if (candidate != null && candidate !== usage &&
                    (best == null || candidate.textRange.startOffset > best.textRange.startOffset)
                ) {
                    best = candidate
                }
            }
            return best
        }

        fun attributeDefinition(attribute: PsiElement, name: String): PsiElement? {
            val attr = SmartyPsiUtil.attributeName(attribute.node)
            if (attr == null || attr !in DEFINING_ATTRIBUTES) return null
            if (name != SmartyPsiUtil.attributeValue(attribute.node)) return null
            var value = attribute.lastChild
            while (value != null && SmartyPsiUtil.isType(value, TokenType.WHITE_SPACE)) value = value.prevSibling
            return value
        }

        /** $x in {foreach $items as $x} / {foreach $items as $k => $x} */
        fun foreachItem(openTag: PsiElement, name: String): PsiElement? {
            if (SmartyPsiUtil.tagName(openTag.node) != "foreach") return null
            var afterAs = false
            var leaf: PsiElement? = PsiTreeUtil.firstChild(openTag)
            while (leaf != null && openTag.textRange.contains(leaf.textRange)) {
                if (SmartyPsiUtil.isType(leaf, KEYWORD) && leaf.text == "as") {
                    afterAs = true
                } else if (afterAs && SmartyPsiUtil.isType(leaf, VARIABLE) && leaf.text == "$$name") {
                    return leaf
                }
                leaf = PsiTreeUtil.nextLeaf(leaf)
            }
            return null
        }
    }
}
