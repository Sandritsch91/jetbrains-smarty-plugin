package com.smartyplugin.formatter

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.TokenSet
import com.smartyplugin.lexer.SmartyTokenTypes.ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.ASSIGN
import com.smartyplugin.lexer.SmartyTokenTypes.AT
import com.smartyplugin.lexer.SmartyTokenTypes.ATTR_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.BAD_CHARACTER
import com.smartyplugin.lexer.SmartyTokenTypes.COLON
import com.smartyplugin.lexer.SmartyTokenTypes.COMMA
import com.smartyplugin.lexer.SmartyTokenTypes.CONFIG_VAR
import com.smartyplugin.lexer.SmartyTokenTypes.DOT
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.KEYWORD
import com.smartyplugin.lexer.SmartyTokenTypes.LBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.LDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.MODIFIER_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.OPERATOR
import com.smartyplugin.lexer.SmartyTokenTypes.PIPE
import com.smartyplugin.lexer.SmartyTokenTypes.RBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.RDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.RPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.SLASH
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE
import com.smartyplugin.lexer.SmartyTokenTypes.WHITE_SPACE
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.parser.SmartyElementTypes.MODIFIER
import com.smartyplugin.parser.SmartyElementTypes.TAGS
import java.util.ArrayDeque

/**
 * Formatting inside one Smarty tag (`{...}`).
 *
 * The tag's tokens become a flat list of blocks: attributes, modifiers and string literals are opened up,
 * nested tags (`{url route="x"}` used as a value) become one composite block each.
 * - Spacing is normalised where Smarty style is unambiguous and left as written elsewhere
 *   (modifier arguments, assignments, `.`).
 * - Line breaks are kept. A continuation line is indented one level relative to the tag, the content of
 *   `[...]` and `(...)` one more, and a line starting with `]` or `)` goes back to the level of the line that
 *   opened it. Several brackets opened on one line count as one level: `f('x', [` ... `])`.
 */
internal object SmartyTagLayout {
    private val OPENERS = TokenSet.create(LBRACKET, LPAREN)
    private val CLOSERS = TokenSet.create(RBRACKET, RPAREN)

    /** Opened up when flattening a tag; their tokens take part in the tag's layout directly. */
    private val TRANSPARENT = TokenSet.create(ATTRIBUTE, MODIFIER)

    /** After these an operator like '-' or '+' is unary: "{$a = -1}", "foo(-$x)". */
    private val BEFORE_UNARY = TokenSet.create(
        LDELIM, TAG_NAME, ASSIGN, OPERATOR, COMMA, LPAREN, LBRACKET, DOUBLE_ARROW, COLON, KEYWORD,
    )
    private val UNARY_ONLY = setOf("!", "~")
    private val INCREMENTS = setOf("++", "--")
    private val CALLABLE_KEYWORDS = setOf("isset", "empty")

    fun isTag(node: ASTNode): Boolean = TAGS.contains(node.elementType)

    /** Child blocks of a tag, with indents already decided. [lineIndent]: added to all, see SmartyFormatterBlock. */
    fun buildChildren(tag: ASTNode, settings: CodeStyleSettings, lineIndent: Int = 0): List<Block> {
        val parts = ArrayList<ASTNode>()
        flatten(tag, parts)
        val unit = indentSize(tag, settings)
        val text = tag.psi.containingFile.viewProvider.contents

        val blocks = ArrayList<Block>(parts.size)
        val open = ArrayDeque<Int>() // content level of each open bracket
        var lineLevel = 0
        for ((i, part) in parts.withIndex()) {
            val type = part.elementType
            val startsLine = i > 0 && containsLineBreak(text, parts[i - 1].textRange.endOffset, part.startOffset)
            if (startsLine) {
                lineLevel = when {
                    type === RDELIM -> 0
                    CLOSERS.contains(type) -> if (open.isEmpty()) 1 else maxOf(0, open.peek() - 1)
                    else -> if (open.isEmpty()) 1 else open.peek()
                }
            }
            if (OPENERS.contains(type)) {
                open.push(lineLevel + 1)
            } else if (CLOSERS.contains(type) && !open.isEmpty()) {
                open.pop()
            }

            val indent = Indent.getSpaceIndent(lineIndent + lineLevel * unit)
            blocks.add(SmartyTagPartBlock(part, indent, settings))
        }
        return blocks
    }

    private fun flatten(node: ASTNode, out: MutableList<ASTNode>) {
        var child = node.firstChildNode
        while (child != null) {
            val type = child.elementType
            if (!(type === WHITE_SPACE || type === TokenType.WHITE_SPACE || child.textLength == 0)) {
                if (TRANSPARENT.contains(type)) flatten(child, out)
                else out.add(child) // tokens, string literals, nested tags, error elements
            }
            child = child.treeNext
        }
    }

    private fun containsLineBreak(text: CharSequence, from: Int, to: Int): Boolean {
        var i = from
        while (i < to && i < text.length) {
            if (text[i] == '\n') return true
            i++
        }
        return false
    }

    private fun indentSize(tag: ASTNode, settings: CodeStyleSettings): Int =
        settings.getIndentOptionsByFile(tag.psi.containingFile).INDENT_SIZE

    // ------------------------------------------------------------------ spacing

    private fun spaces(n: Int): Spacing = Spacing.createSpacing(n, n, 0, true, 0)

    fun spacing(child1: Block?, child2: Block): Spacing? {
        if (child1 !is ASTBlock || child2 !is ASTBlock) return null
        val left = child1.node ?: return null
        val right = child2.node ?: return null
        val l = left.elementType
        val r = right.elementType
        if (l === BAD_CHARACTER || r === BAD_CHARACTER || l === TokenType.ERROR_ELEMENT || r === TokenType.ERROR_ELEMENT) {
            return null
        }

        // delimiters: "{/if}", "{$a}", "{if $a}"
        if (l === LDELIM || r === RDELIM || l === SLASH && left.treePrev?.elementType === LDELIM) {
            return spaces(0)
        }

        // modifiers: "|escape:'html':true", "|@count"; arguments are kept as written
        val modifier = commonModifier(left, right)
        if (r === PIPE && isInModifier(right) || l === PIPE && isInModifier(left)) return spaces(0)
        if (modifier != null) {
            if (l === AT || r === AT || r === MODIFIER_NAME || l === COLON || r === COLON) return spaces(0)
            return null
        }

        // attributes: name=value
        if (l === ATTR_NAME && r === ASSIGN) return spaces(0)
        if (l === ASSIGN && isInAttribute(left)) return spaces(0)
        if (l === ASSIGN || r === ASSIGN) return null // {$a = 1}, {for $i=0 to 3}: as written
        if (r === ATTR_NAME) return spaces(1)

        // member access and loop properties: $a->b, Foo::BAR, $item@first; '.' is kept as written
        if (l === ARROW || r === ARROW || l === DOUBLE_COLON || r === DOUBLE_COLON || l === AT || r === AT) return spaces(0)
        if (l === DOT || r === DOT) return null

        // brackets: foo($a), $a[0], [1, 2]; "{if ($a)}" vs "{isset($a)}" can't be told apart: as written
        if (l === TAG_NAME && r === LPAREN) return null
        if (l === LPAREN || l === LBRACKET || r === RPAREN || r === RBRACKET) return spaces(0)
        if (r === LPAREN) return if (isCallee(l) || isCallableKeyword(left)) spaces(0) else spaces(1)
        if (r === LBRACKET) return if (isCallee(l) || l === RBRACKET) spaces(0) else spaces(1)

        if (r === COMMA) return spaces(0)
        if (l === COMMA) return spaces(1)

        if (l === OPERATOR) {
            val op = left.text
            if (op in INCREMENTS) return null
            if (op in UNARY_ONLY || isUnary(left)) return spaces(0)
            return spaces(1)
        }
        if (r === OPERATOR && right.text in INCREMENTS) return null

        return spaces(1) // after the tag name, around =>, keywords, binary operators, ternary ':'
    }

    private fun isCallee(type: com.intellij.psi.tree.IElementType): Boolean =
        type === IDENTIFIER || type === VARIABLE || type === RPAREN || type === CONFIG_VAR

    /** isset($a), empty($a) */
    private fun isCallableKeyword(node: ASTNode): Boolean =
        node.elementType === KEYWORD && node.text in CALLABLE_KEYWORDS

    private fun isUnary(operator: ASTNode): Boolean {
        val op = operator.text
        if (op != "-" && op != "+" && op != "&") return false
        val prev = prevSignificant(operator)
        return prev == null || BEFORE_UNARY.contains(prev.elementType)
    }

    /** Previous non-whitespace leaf inside the same tag. */
    private fun prevSignificant(node: ASTNode): ASTNode? {
        var tag: ASTNode? = node
        while (tag != null && !isTag(tag)) tag = tag.treeParent
        if (tag == null) return null
        var leaf = TreeUtil.prevLeaf(node)
        while (leaf != null && leaf.startOffset >= tag.startOffset) {
            val t = leaf.elementType
            if (t !== WHITE_SPACE && t !== TokenType.WHITE_SPACE && leaf.textLength > 0) return leaf
            leaf = TreeUtil.prevLeaf(leaf)
        }
        return null
    }

    private fun isInModifier(node: ASTNode): Boolean = node.treeParent?.elementType === MODIFIER

    private fun isInAttribute(node: ASTNode): Boolean = node.treeParent?.elementType === ATTRIBUTE

    private fun commonModifier(left: ASTNode, right: ASTNode): ASTNode? {
        val parent = left.treeParent
        return if (parent != null && parent.elementType === MODIFIER && parent === right.treeParent) parent else null
    }
}
