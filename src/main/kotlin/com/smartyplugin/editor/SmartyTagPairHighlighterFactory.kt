package com.smartyplugin.editor

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.parser.SmartyElementTypes.BLOCK
import com.smartyplugin.parser.SmartyElementTypes.BLOCK_PARTS
import com.smartyplugin.psi.SmartyPsiUtil

/** With the caret on a tag name, highlights all tags of that block: {if} / {elseif} / {else} / {/if}. */
class SmartyTagPairHighlighterFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(
        editor: Editor,
        file: PsiFile,
        target: PsiElement,
    ): HighlightUsagesHandlerBase<*>? {
        if (!SmartyPsiUtil.isType(target, TAG_NAME)) return null
        val tag = target.parent
        if (tag == null || !BLOCK_PARTS.contains(tag.node.elementType)) return null
        val block = tag.parent
        if (!SmartyPsiUtil.isType(block, BLOCK)) return null
        return Handler(editor, file, target, block)
    }

    private class Handler(
        editor: Editor,
        file: PsiFile,
        private val target: PsiElement,
        private val block: PsiElement,
    ) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {

        override fun getTargets(): List<PsiElement> = listOf(target)

        override fun selectTargets(
            targets: List<PsiElement>,
            selectionConsumer: Consumer<in List<PsiElement>>,
        ) {
            selectionConsumer.consume(targets)
        }

        override fun computeUsages(targets: List<PsiElement>) {
            var child = block.node.firstChildNode
            while (child != null) {
                if (BLOCK_PARTS.contains(child.elementType)) {
                    SmartyPsiUtil.tagNameNode(child)?.let { addOccurrence(it.psi) }
                }
                child = child.treeNext
            }
        }
    }
}
