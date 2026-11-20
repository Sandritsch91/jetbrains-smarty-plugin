package com.smartyplugin.formatter

import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.templateLanguages.BlockWithParent
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock
import com.intellij.formatting.templateLanguages.TemplateLanguageBlockFactory
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings
import com.intellij.psi.formatter.xml.SyntheticBlock
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlTag
import com.smartyplugin.lexer.SmartyTokenTypes
import com.smartyplugin.lexer.SmartyTokenTypes.COMMENT
import com.smartyplugin.parser.SmartyElementTypes.BLOCK
import com.smartyplugin.parser.SmartyElementTypes.BLOCK_BODY
import java.util.Locale

internal class SmartyFormatterBlock(
    private val factory: TemplateLanguageBlockFactory,
    settings: CodeStyleSettings,
    node: ASTNode,
    foreignChildren: List<DataLanguageBlockWrapper>?,
) : TemplateLanguageBlock(factory, settings, node, foreignChildren) {

    override fun getTemplateTextElementType(): IElementType = SmartyTokenTypes.TEMPLATE_TEXT

    private val isAtomic: Boolean
        get() = myNode.elementType === COMMENT

    override fun isLeaf(): Boolean {
        if (SmartyTagLayout.isTag(myNode)) return false
        return isAtomic || super.isLeaf()
    }

    override fun buildChildren(): List<Block> {
        if (isAtomic) return AbstractBlock.EMPTY
        if (SmartyTagLayout.isTag(myNode)) return SmartyTagLayout.buildChildren(myNode, settings)
        if (SmartyCodeLayout.isCodeBody(myNode)) return buildCodeChildren()
        return super.buildChildren()
    }

    /** {registerJs} / {registerCss}: formatted by the JavaScript / CSS formatter, see [SmartyCodeLayout]. */
    private fun buildCodeChildren(): List<Block> {
        val unit = settings.getIndentOptionsByFile(myNode.psi.containingFile).INDENT_SIZE
        // lines of the body line up like HTML children of the block would
        val base = if (isInsideHtmlTagContent()) unit else 0
        val tags = SmartyCodeLayout.TagBlocks { tag ->
            val block = SmartyFormatterBlock(factory, settings, tag, null)
            block.parent = this
            block
        }
        return SmartyCodeLayout.formattedBlocks(myNode, base, tags)
            ?: SmartyCodeLayout.buildChildren(myNode, base, unit, tags)
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (SmartyTagLayout.isTag(myNode)) return SmartyTagLayout.spacing(child1, child2)
        if (SmartyCodeLayout.isCodeBody(myNode)) return SmartyCodeLayout.spacing(child1, child2)
        return super.getSpacing(child1, child2)
    }

    /*
     * Indentation model:
     *  - a block body is indented relative to its block, unless the block sits directly inside an HTML
     *    tag: then the HTML children of the body already carry the tag's indent, and Smarty children of the
     *    body get a normal indent themselves so that they line up with their HTML siblings.
     *  - a Smarty element that is a direct child of an HTML tag is indented like an HTML child would be.
     */
    override fun getIndent(): Indent {
        if (isJavaBlank(myNode.text)) return Indent.getNoneIndent()
        val type = myNode.elementType
        val parent = parent

        if (parent is DataLanguageBlockWrapper) {
            return indentInsideForeign(parent)
        }
        if (parent is SmartyFormatterBlock) {
            val parentType = parent.myNode.elementType
            if (type === BLOCK_BODY) {
                return if (parent.isInsideHtmlTagContent()) Indent.getNoneIndent() else Indent.getNormalIndent()
            }
            if (parentType === BLOCK_BODY) {
                return if (parent.isInsideHtmlTagContent()) Indent.getNormalIndent() else Indent.getNoneIndent()
            }
        }
        return Indent.getNoneIndent()
    }

    private fun indentInsideForeign(wrapper: DataLanguageBlockWrapper): Indent {
        // synthetic HTML blocks only group a tag's children; the tag above them decides the indent
        var p: BlockWithParent? = wrapper
        while (p is DataLanguageBlockWrapper && p.original is SyntheticBlock) {
            p = p.parent
        }
        val node = (p as? DataLanguageBlockWrapper)?.node
        return if (node is XmlTag && indentsChildren(node)) Indent.getNormalIndent() else Indent.getNoneIndent()
    }

    /** Whether the nearest HTML ancestor of this block is a tag whose content is indented. */
    private fun isInsideHtmlTagContent(): Boolean {
        var p: BlockWithParent? = parent
        while (p != null) {
            if (p is DataLanguageBlockWrapper) {
                if (p.original is SyntheticBlock) {
                    p = p.parent
                    continue
                }
                val node = p.node
                return node is XmlTag && indentsChildren(node)
            }
            p = p.parent
        }
        return false
    }

    private fun indentsChildren(tag: XmlTag): Boolean {
        val html = settings.getCustomSettings(HtmlCodeStyleSettings::class.java)
        val noIndent = html.HTML_DO_NOT_INDENT_CHILDREN_OF
        if (noIndent == null || isJavaBlank(noIndent)) return true
        val name = tag.name.lowercase(Locale.ROOT)
        return noIndent.split(",").none { it.trim { c -> c <= ' ' }.equals(name, ignoreCase = true) }
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val type = myNode.elementType
        if (type === BLOCK) {
            return ChildAttributes(Indent.getNormalIndent(), null)
        }
        if (type === BLOCK_BODY) {
            return ChildAttributes(if (isInsideHtmlTagContent()) Indent.getNormalIndent() else Indent.getNoneIndent(), null)
        }
        if (SmartyTagLayout.isTag(myNode)) {
            return ChildAttributes(Indent.getNormalIndent(), null) // Enter inside a multi-line tag
        }
        return ChildAttributes(Indent.getNoneIndent(), null)
    }

    private companion object {
        /** java.lang.String.isBlank(): only [Character.isWhitespace] counts, unlike Kotlin's isBlank(). */
        fun isJavaBlank(s: String): Boolean = s.all { Character.isWhitespace(it) }
    }
}
