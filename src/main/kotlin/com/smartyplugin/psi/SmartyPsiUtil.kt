package com.smartyplugin.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lexer.SmartyTokenTypes.ASSIGN
import com.smartyplugin.lexer.SmartyTokenTypes.ATTR_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.CONFIG_VAR
import com.smartyplugin.lexer.SmartyTokenTypes.CONSTANT
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.LBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.LDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.NUMBER
import com.smartyplugin.lexer.SmartyTokenTypes.RBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.RDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.RPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.SLASH
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE
import com.smartyplugin.lexer.SmartyTokenTypes.WHITE_SPACE
import com.smartyplugin.parser.SmartyElementTypes.BLOCK
import com.smartyplugin.parser.SmartyElementTypes.BLOCK_BODY
import com.smartyplugin.parser.SmartyElementTypes.CLOSE_TAG
import com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG
import com.smartyplugin.parser.SmartyElementTypes.STRING_LITERAL
import com.smartyplugin.parser.SmartyElementTypes.TAG
import com.smartyplugin.parser.SmartyElementTypes.TAGS

object SmartyPsiUtil {
    @JvmStatic
    fun isType(node: ASTNode?, type: IElementType): Boolean = node != null && node.elementType === type

    @JvmStatic
    fun isType(element: PsiElement?, type: IElementType): Boolean = element != null && isType(element.node, type)

    /** Tag name token of a tag node ({if}, {/if}, {include}), or null for print tags. */
    @JvmStatic
    fun tagNameNode(tag: ASTNode?): ASTNode? {
        if (tag == null) return null
        var c = tag.firstChildNode
        while (c != null) {
            val t = c.elementType
            if (t === TAG_NAME) return c
            if (t !== LDELIM && t !== SLASH && t !== WHITE_SPACE) return null
            c = c.treeNext
        }
        return null
    }

    @JvmStatic
    fun tagName(tag: ASTNode?): String? = tagNameNode(tag)?.text

    @JvmStatic
    fun openTag(block: ASTNode): ASTNode? = block.findChildByType(OPEN_TAG)

    @JvmStatic
    fun closeTag(block: ASTNode): ASTNode? = block.findChildByType(CLOSE_TAG)

    @JvmStatic
    fun blockName(block: ASTNode): String? = tagName(openTag(block))

    /** Closest tag-like ancestor (TAG, OPEN_TAG, ...) of the element, inclusive. */
    @JvmStatic
    fun enclosingTag(node: ASTNode?): ASTNode? {
        var n = node
        while (n != null) {
            if (TAGS.contains(n.elementType)) return n
            n = n.treeParent
        }
        return null
    }

    /** The innermost enclosing block that has no closing tag yet. */
    @JvmStatic
    fun innermostUnclosedBlock(node: ASTNode?): ASTNode? {
        var n = node
        while (n != null) {
            if (n.elementType === BLOCK && closeTag(n) == null) return n
            n = n.treeParent
        }
        return null
    }

    /** Text of an attribute value that is a plain string or identifier, without quotes. */
    @JvmStatic
    fun attributeValue(attribute: ASTNode): String? {
        var afterAssign: ASTNode? = null
        var seenAssign = false
        var c = attribute.firstChildNode
        while (c != null) {
            if (c.elementType === WHITE_SPACE) {
                c = c.treeNext
                continue
            }
            if (c.elementType === ASSIGN) {
                seenAssign = true
                c = c.treeNext
                continue
            }
            if (seenAssign) {
                afterAssign = c
                break
            }
            c = c.treeNext
        }
        if (afterAssign == null) return null
        val text = afterAssign.text
        if (afterAssign.elementType === STRING_LITERAL) return unquote(text)
        if (afterAssign.elementType === IDENTIFIER) return text
        return null
    }

    @JvmStatic
    fun attributeName(attribute: ASTNode): String? {
        val first = attribute.firstChildNode
        return if (first != null && first.elementType === ATTR_NAME) first.text else null
    }

    @JvmStatic
    fun unquote(text: String): String {
        if (text.length >= 2) {
            val q = text[0]
            if ((q == '"' || q == '\'') && text[text.length - 1] == q) {
                return text.substring(1, text.length - 1)
            }
        }
        if (text.isNotEmpty() && (text[0] == '"' || text[0] == '\'')) return text.substring(1)
        return text
    }

    @JvmStatic
    fun smartyFile(file: PsiFile): PsiFile? = file.viewProvider.getPsi(SmartyLanguage)

    /** Tokens that end a value: an attribute name may follow them. */
    private val VALUE_END: TokenSet = TokenSet.create(
        TAG_NAME, STRING, NUMBER, VARIABLE, IDENTIFIER, CONSTANT, CONFIG_VAR, RBRACKET, RPAREN, RDELIM,
    )

    /** The innermost tag around the element that can carry attributes ({x ...} or the opening tag of a block). */
    @JvmStatic
    fun enclosingAttributeTag(element: PsiElement): ASTNode? {
        var n = element.node.treeParent
        while (n != null) {
            val t = n.elementType
            if (t === TAG || t === OPEN_TAG) return n
            if (TAGS.contains(t) || t === BLOCK_BODY) return null
            n = n.treeParent
        }
        return null
    }

    /**
     * Whether a word typed at `position` starts a new attribute: right after the tag name or after a complete
     * value, separated by whitespace, and not inside brackets: "{x a='1' b|" but not "{x a=b|" or "{x a=[b|".
     * (The parser attaches such a half-typed word to the previous attribute's value, so the tree can't tell.)
     */
    @JvmStatic
    fun isAttributeNamePosition(position: PsiElement): Boolean {
        val tag = enclosingAttributeTag(position) ?: return false
        var prev = PsiTreeUtil.prevLeaf(position)
        while (prev != null && (isType(prev, WHITE_SPACE) || prev.textLength == 0)) {
            prev = PsiTreeUtil.prevLeaf(prev)
        }
        if (prev == null || !VALUE_END.contains(prev.node.elementType)) return false
        if (prev.textRange.endOffset == position.textRange.startOffset) return false
        var depth = 0
        var leaf: PsiElement? = prev
        while (leaf != null && leaf.textRange.startOffset >= tag.startOffset) {
            if (isType(leaf, RBRACKET) || isType(leaf, RPAREN)) depth--
            if (isType(leaf, LBRACKET) || isType(leaf, LPAREN)) depth++
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return depth <= 0
    }

    /** Smarty leaf at the given offset, even when the caret is in the template data (HTML) part. */
    @JvmStatic
    fun smartyElementAt(file: PsiFile, offset: Int): PsiElement? {
        val smarty = file.viewProvider.getPsi(SmartyLanguage)
        if (smarty == null || offset < 0) return null
        // PsiFile.findElementAt may answer from the template data tree in plain-text regions; the AST never does
        return smarty.node.findLeafElementAt(offset)?.psi
    }
}
