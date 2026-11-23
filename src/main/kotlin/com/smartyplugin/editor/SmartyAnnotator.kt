package com.smartyplugin.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lang.SmartyTags
import com.smartyplugin.lexer.SmartyTokenTypes
import com.smartyplugin.parser.SmartyElementTypes.BLOCK
import com.smartyplugin.parser.SmartyElementTypes.CLOSE_TAG
import com.smartyplugin.parser.SmartyElementTypes.MIDDLE_TAG
import com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG
import com.smartyplugin.parser.SmartyElementTypes.TAG
import com.smartyplugin.psi.SmartyPsiUtil

/** Reports structural errors: unclosed blocks, stray closing tags and misplaced {else}-like tags. */
class SmartyAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val node = element.node ?: return
        val type = node.elementType

        if (type === BLOCK && SmartyPsiUtil.closeTag(node) == null) {
            val open = SmartyPsiUtil.openTag(node)
            val name = SmartyPsiUtil.blockName(node)
            if (open != null && name != null) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Missing closing tag " + d(element).closeTag(name))
                    .range(open.textRange)
                    .withFix(SmartyTagFixes.InsertClosingTag(element, d(element).closeTag(name)))
                    .create()
            }
        } else if (type === CLOSE_TAG && !SmartyPsiUtil.isType(node.treeParent, BLOCK)) {
            val name = SmartyPsiUtil.tagName(node)
            val message = if (name == null) {
                "Closing tag without a name"
            } else {
                "Closing tag " + d(element).closeTag(name) + " has no matching opening tag"
            }
            holder.newAnnotation(HighlightSeverity.ERROR, message)
                .range(node.textRange)
                .withFix(SmartyTagFixes.RemoveTag(element))
                .create()
        } else if (type === MIDDLE_TAG) {
            val name = SmartyPsiUtil.tagName(node)
            val parent = node.treeParent
            val blockName = if (SmartyPsiUtil.isType(parent, BLOCK)) SmartyPsiUtil.blockName(parent) else null
            if (name != null && !SmartyTags.isMiddleAllowed(blockName, name)) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    d(element).tag(name) + " is only allowed inside " + describeParents(name, d(element)),
                )
                    .range(node.textRange)
                    .create()
            }
        } else if (type === OPEN_TAG || type === TAG || type === MIDDLE_TAG) {
            checkRequiresArguments(node, holder)
        }
    }

    private fun checkRequiresArguments(tag: ASTNode, holder: AnnotationHolder) {
        val name = SmartyPsiUtil.tagName(tag)
        if (name != "if" && name != "elseif" && name != "while") return
        val nameNode = SmartyPsiUtil.tagNameNode(tag) ?: return
        var c = nameNode.treeNext
        while (c != null) {
            val t = c.elementType
            if (t !== TokenType.WHITE_SPACE && t !== SmartyTokenTypes.RDELIM) return
            c = c.treeNext
        }
        holder.newAnnotation(HighlightSeverity.ERROR, d(tag.psi).tag(name) + " requires a condition")
            .range(TextRange.from(tag.startOffset, tag.textLength))
            .create()
    }

    /** The project's delimiters, for messages like "Missing closing tag {/if}". */
    private fun d(element: PsiElement): SmartyDelimiters = SmartyDelimiters.of(element)

    private fun describeParents(middle: String, d: SmartyDelimiters): String =
        SmartyTags.allowedParentsOfMiddle(middle).joinToString(" / ") { d.tag(it) }
}
