package com.smartyplugin.formatter

import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock

/** A token inside a Smarty tag, or a tag nested in another tag's expression, laid out by [SmartyTagLayout]. */
internal class SmartyTagPartBlock(
    node: ASTNode,
    private val indent: Indent,
    private val settings: CodeStyleSettings,
) : AbstractBlock(node, null, null) {

    override fun buildChildren(): List<Block> =
        if (SmartyTagLayout.isTag(myNode)) SmartyTagLayout.buildChildren(myNode, settings) else AbstractBlock.EMPTY

    override fun isLeaf(): Boolean = !SmartyTagLayout.isTag(myNode)

    override fun getIndent(): Indent = indent

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = SmartyTagLayout.spacing(child1, child2)

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(Indent.getNormalIndent(), null)
}
