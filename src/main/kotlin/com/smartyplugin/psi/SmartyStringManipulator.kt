package com.smartyplugin.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.util.IncorrectOperationException

class SmartyStringManipulator : AbstractElementManipulator<SmartyStringLiteral>() {
    @Throws(IncorrectOperationException::class)
    override fun handleContentChange(element: SmartyStringLiteral, range: TextRange, newContent: String): SmartyStringLiteral {
        val newText = range.replace(element.text, newContent)
        val leaf = element.node.firstChildNode as? LeafElement
            ?: throw IncorrectOperationException("Unexpected string literal structure")
        leaf.replaceWithText(newText)
        return element
    }

    override fun getRangeInElement(element: SmartyStringLiteral): TextRange {
        val text = element.text
        if (text.length >= 2 && text[text.length - 1] == text[0]) {
            return TextRange(1, text.length - 1)
        }
        return TextRange(minOf(1, text.length), text.length)
    }
}
