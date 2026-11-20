package com.smartyplugin.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lexer.SmartyTokenTypes.COMMENT
import com.smartyplugin.parser.SmartyElementTypes.BLOCK
import com.smartyplugin.psi.SmartyPsiUtil

/** Folds multi-line blocks ({if}...{/if}, {foreach}..., {block}...) and multi-line comments. */
class SmartyFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val result = ArrayList<FoldingDescriptor>()
        PsiTreeUtil.processElements(root) { element ->
            val node = element.node ?: return@processElements true
            if (node.elementType === BLOCK) {
                val open = SmartyPsiUtil.openTag(node)
                val close = SmartyPsiUtil.closeTag(node)
                if (open != null && close != null) {
                    val range = TextRange(open.textRange.endOffset, close.startOffset)
                    if (!range.isEmpty && spansLines(document, range)) {
                        result.add(FoldingDescriptor(node, range))
                    }
                }
            } else if (node.elementType === COMMENT && spansLines(document, node.textRange)) {
                result.add(FoldingDescriptor(node, node.textRange))
            }
            true
        }
        return result.toTypedArray()
    }

    private fun spansLines(document: Document, range: TextRange): Boolean =
        document.getLineNumber(range.startOffset) < document.getLineNumber(range.endOffset)

    override fun getPlaceholderText(node: ASTNode): String {
        if (node.elementType === COMMENT) {
            val d = SmartyDelimiters.of(node.psi)
            var text = node.text
            if (text.startsWith(d.commentStart())) text = text.substring(d.commentStart().length)
            text = text.trim { it <= ' ' }
            val nl = text.indexOf('\n')
            var first = (if (nl < 0) text else text.substring(0, nl)).replace(d.commentEnd(), "").trim { it <= ' ' }
            if (first.length > 40) first = first.substring(0, 40) + "…"
            return d.commentStart() + " " + first + " … " + d.commentEnd()
        }
        return "…"
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
