package com.smartyplugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.smartyplugin.lang.SmartyLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;
import static com.smartyplugin.parser.SmartyElementTypes.*;

public final class SmartyPsiUtil {
    private SmartyPsiUtil() {
    }

    public static boolean is(@Nullable ASTNode node, IElementType type) {
        return node != null && node.getElementType() == type;
    }

    public static boolean is(@Nullable PsiElement element, IElementType type) {
        return element != null && is(element.getNode(), type);
    }

    /** Tag name token of a tag node ({if}, {/if}, {include}), or null for print tags. */
    public static @Nullable ASTNode tagNameNode(@Nullable ASTNode tag) {
        if (tag == null) return null;
        for (ASTNode c = tag.getFirstChildNode(); c != null; c = c.getTreeNext()) {
            IElementType t = c.getElementType();
            if (t == TAG_NAME) return c;
            if (t != LDELIM && t != SLASH && t != WHITE_SPACE) return null;
        }
        return null;
    }

    public static @Nullable String tagName(@Nullable ASTNode tag) {
        ASTNode name = tagNameNode(tag);
        return name == null ? null : name.getText();
    }

    public static @Nullable ASTNode openTag(@NotNull ASTNode block) {
        return block.findChildByType(OPEN_TAG);
    }

    public static @Nullable ASTNode closeTag(@NotNull ASTNode block) {
        return block.findChildByType(CLOSE_TAG);
    }

    public static @Nullable String blockName(@NotNull ASTNode block) {
        return tagName(openTag(block));
    }

    /** Closest tag-like ancestor (TAG, OPEN_TAG, ...) of the element, inclusive. */
    public static @Nullable ASTNode enclosingTag(@Nullable ASTNode node) {
        while (node != null) {
            if (TAGS.contains(node.getElementType())) return node;
            node = node.getTreeParent();
        }
        return null;
    }

    /** The innermost enclosing block that has no closing tag yet. */
    public static @Nullable ASTNode innermostUnclosedBlock(@Nullable ASTNode node) {
        while (node != null) {
            if (node.getElementType() == BLOCK && closeTag(node) == null) return node;
            node = node.getTreeParent();
        }
        return null;
    }

    /** Text of an attribute value that is a plain string or identifier, without quotes. */
    public static @Nullable String attributeValue(@NotNull ASTNode attribute) {
        ASTNode afterAssign = null;
        boolean seenAssign = false;
        for (ASTNode c = attribute.getFirstChildNode(); c != null; c = c.getTreeNext()) {
            if (c.getElementType() == WHITE_SPACE) continue;
            if (c.getElementType() == ASSIGN) {
                seenAssign = true;
                continue;
            }
            if (seenAssign) {
                afterAssign = c;
                break;
            }
        }
        if (afterAssign == null) return null;
        String text = afterAssign.getText();
        if (afterAssign.getElementType() == STRING_LITERAL) return unquote(text);
        if (afterAssign.getElementType() == IDENTIFIER) return text;
        return null;
    }

    public static @Nullable String attributeName(@NotNull ASTNode attribute) {
        ASTNode first = attribute.getFirstChildNode();
        return first != null && first.getElementType() == ATTR_NAME ? first.getText() : null;
    }

    public static @NotNull String unquote(@NotNull String text) {
        if (text.length() >= 2) {
            char q = text.charAt(0);
            if ((q == '"' || q == '\'') && text.charAt(text.length() - 1) == q) {
                return text.substring(1, text.length() - 1);
            }
        }
        if (!text.isEmpty() && (text.charAt(0) == '"' || text.charAt(0) == '\'')) return text.substring(1);
        return text;
    }

    public static @Nullable PsiFile smartyFile(@NotNull PsiFile file) {
        FileViewProvider vp = file.getViewProvider();
        return vp.getPsi(SmartyLanguage.INSTANCE);
    }

    /** Smarty leaf at the given offset, even when the caret is in the template data (HTML) part. */
    public static @Nullable PsiElement smartyElementAt(@NotNull PsiFile file, int offset) {
        PsiFile smarty = file.getViewProvider().getPsi(SmartyLanguage.INSTANCE);
        if (smarty == null || offset < 0) return null;
        // PsiFile.findElementAt may answer from the template data tree in plain-text regions; the AST never does
        ASTNode leaf = smarty.getNode().findLeafElementAt(offset);
        return leaf == null ? null : leaf.getPsi();
    }
}
