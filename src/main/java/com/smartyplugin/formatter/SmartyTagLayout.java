package com.smartyplugin.formatter;

import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;
import static com.smartyplugin.parser.SmartyElementTypes.*;

/**
 * Formatting inside one Smarty tag (<code>{...}</code>).
 * <p>
 * The tag's tokens become a flat list of blocks: attributes, modifiers and string literals are opened up,
 * nested tags (<code>{url route="x"}</code> used as a value) become one composite block each.
 * <ul>
 *   <li>Spacing is normalised where Smarty style is unambiguous and left as written elsewhere
 *       (modifier arguments, assignments, <code>.</code>).</li>
 *   <li>Line breaks are kept. A continuation line is indented one level relative to the tag, the content of
 *       <code>[...]</code> and <code>(...)</code> one more, and a line starting with <code>]</code> or
 *       <code>)</code> goes back to the level of the line that opened it. Several brackets opened on one
 *       line count as one level: <code>f('x', [</code> ... <code>])</code>.</li>
 * </ul>
 */
final class SmartyTagLayout {
    private static final TokenSet OPENERS = TokenSet.create(LBRACKET, LPAREN);
    private static final TokenSet CLOSERS = TokenSet.create(RBRACKET, RPAREN);
    /** Opened up when flattening a tag; their tokens take part in the tag's layout directly. */
    private static final TokenSet TRANSPARENT = TokenSet.create(ATTRIBUTE, MODIFIER);
    /** After these an operator like '-' or '+' is unary: "{$a = -1}", "foo(-$x)". */
    private static final TokenSet BEFORE_UNARY = TokenSet.create(LDELIM, TAG_NAME, ASSIGN, OPERATOR, COMMA, LPAREN,
            LBRACKET, DOUBLE_ARROW, COLON, KEYWORD);
    private static final Set<String> UNARY_ONLY = Set.of("!", "~");
    private static final Set<String> INCREMENTS = Set.of("++", "--");
    private static final Set<String> CALLABLE_KEYWORDS = Set.of("isset", "empty");

    private SmartyTagLayout() {
    }

    static boolean isTag(@NotNull ASTNode node) {
        return TAGS.contains(node.getElementType());
    }

    /** Child blocks of a tag, with indents already decided. */
    static List<Block> buildChildren(@NotNull ASTNode tag, @NotNull CodeStyleSettings settings) {
        List<ASTNode> parts = new ArrayList<>();
        flatten(tag, parts);
        int unit = indentSize(tag, settings);
        CharSequence text = tag.getPsi().getContainingFile().getViewProvider().getContents();

        List<Block> blocks = new ArrayList<>(parts.size());
        Deque<Integer> open = new ArrayDeque<>(); // content level of each open bracket
        int lineLevel = 0;
        for (int i = 0; i < parts.size(); i++) {
            ASTNode part = parts.get(i);
            IElementType type = part.getElementType();
            boolean startsLine = i > 0 && containsLineBreak(text, parts.get(i - 1).getTextRange().getEndOffset(),
                    part.getStartOffset());
            if (startsLine) {
                if (type == RDELIM) lineLevel = 0;
                else if (CLOSERS.contains(type)) lineLevel = open.isEmpty() ? 1 : Math.max(0, open.peek() - 1);
                else lineLevel = open.isEmpty() ? 1 : open.peek();
            }
            if (OPENERS.contains(type)) open.push(lineLevel + 1);
            else if (CLOSERS.contains(type) && !open.isEmpty()) open.pop();

            Indent indent = Indent.getSpaceIndent(lineLevel * unit);
            blocks.add(new SmartyTagPartBlock(part, indent, settings));
        }
        return blocks;
    }

    private static void flatten(ASTNode node, List<ASTNode> out) {
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            IElementType type = child.getElementType();
            if (type == WHITE_SPACE || type == TokenType.WHITE_SPACE || child.getTextLength() == 0) continue;
            if (TRANSPARENT.contains(type)) flatten(child, out);
            else out.add(child); // tokens, string literals, nested tags, error elements
        }
    }

    private static boolean containsLineBreak(CharSequence text, int from, int to) {
        for (int i = from; i < to && i < text.length(); i++) {
            if (text.charAt(i) == '\n') return true;
        }
        return false;
    }

    private static int indentSize(ASTNode tag, CodeStyleSettings settings) {
        PsiFile file = tag.getPsi().getContainingFile();
        return settings.getIndentOptionsByFile(file).INDENT_SIZE;
    }

    // ------------------------------------------------------------------ spacing

    private static Spacing spaces(int n) {
        return Spacing.createSpacing(n, n, 0, true, 0);
    }

    static @Nullable Spacing spacing(@Nullable Block child1, @NotNull Block child2) {
        if (!(child1 instanceof ASTBlock b1) || !(child2 instanceof ASTBlock b2)) return null;
        ASTNode left = b1.getNode();
        ASTNode right = b2.getNode();
        if (left == null || right == null) return null;
        IElementType l = left.getElementType();
        IElementType r = right.getElementType();
        if (l == BAD_CHARACTER || r == BAD_CHARACTER || l == TokenType.ERROR_ELEMENT || r == TokenType.ERROR_ELEMENT) {
            return null;
        }

        // delimiters: "{/if}", "{$a}", "{if $a}"
        if (l == LDELIM || r == RDELIM || l == SLASH && left.getTreePrev() != null
                && left.getTreePrev().getElementType() == LDELIM) {
            return spaces(0);
        }

        // modifiers: "|escape:'html':true", "|@count"; arguments are kept as written
        ASTNode modifier = commonModifier(left, right);
        if (r == PIPE && isInModifier(right) || l == PIPE && isInModifier(left)) return spaces(0);
        if (modifier != null) {
            if (l == AT || r == AT || r == MODIFIER_NAME || l == COLON || r == COLON) return spaces(0);
            return null;
        }

        // attributes: name=value
        if (l == ATTR_NAME && r == ASSIGN) return spaces(0);
        if (l == ASSIGN && isInAttribute(left)) return spaces(0);
        if (l == ASSIGN || r == ASSIGN) return null; // {$a = 1}, {for $i=0 to 3}: as written
        if (r == ATTR_NAME) return spaces(1);

        // member access and loop properties: $a->b, Foo::BAR, $item@first; '.' is kept as written
        if (l == ARROW || r == ARROW || l == DOUBLE_COLON || r == DOUBLE_COLON || l == AT || r == AT) return spaces(0);
        if (l == DOT || r == DOT) return null;

        // brackets: foo($a), $a[0], [1, 2]; "{if ($a)}" vs "{isset($a)}" can't be told apart: as written
        if (l == TAG_NAME && r == LPAREN) return null;
        if (l == LPAREN || l == LBRACKET || r == RPAREN || r == RBRACKET) return spaces(0);
        if (r == LPAREN) return isCallee(l) || isCallableKeyword(left) ? spaces(0) : spaces(1);
        if (r == LBRACKET) return isCallee(l) || l == RBRACKET ? spaces(0) : spaces(1);

        if (r == COMMA) return spaces(0);
        if (l == COMMA) return spaces(1);

        if (l == OPERATOR) {
            String op = left.getText();
            if (INCREMENTS.contains(op)) return null;
            if (UNARY_ONLY.contains(op) || isUnary(left)) return spaces(0);
            return spaces(1);
        }
        if (r == OPERATOR && INCREMENTS.contains(right.getText())) return null;

        return spaces(1); // after the tag name, around =>, keywords, binary operators, ternary ':'
    }

    private static boolean isCallee(IElementType type) {
        return type == IDENTIFIER || type == VARIABLE || type == RPAREN || type == CONFIG_VAR;
    }

    /** isset($a), empty($a) */
    private static boolean isCallableKeyword(ASTNode node) {
        return node.getElementType() == KEYWORD && CALLABLE_KEYWORDS.contains(node.getText());
    }

    private static boolean isUnary(ASTNode operator) {
        String op = operator.getText();
        if (!op.equals("-") && !op.equals("+") && !op.equals("&")) return false;
        ASTNode prev = prevSignificant(operator);
        return prev == null || BEFORE_UNARY.contains(prev.getElementType());
    }

    /** Previous non-whitespace leaf inside the same tag. */
    private static @Nullable ASTNode prevSignificant(ASTNode node) {
        ASTNode tag = node;
        while (tag != null && !isTag(tag)) tag = tag.getTreeParent();
        if (tag == null) return null;
        ASTNode leaf = TreeUtil.prevLeaf(node);
        while (leaf != null && leaf.getStartOffset() >= tag.getStartOffset()) {
            IElementType t = leaf.getElementType();
            if (t != WHITE_SPACE && t != TokenType.WHITE_SPACE && leaf.getTextLength() > 0) return leaf;
            leaf = TreeUtil.prevLeaf(leaf);
        }
        return null;
    }

    private static boolean isInModifier(ASTNode node) {
        ASTNode parent = node.getTreeParent();
        return parent != null && parent.getElementType() == MODIFIER;
    }

    private static boolean isInAttribute(ASTNode node) {
        ASTNode parent = node.getTreeParent();
        return parent != null && parent.getElementType() == ATTRIBUTE;
    }

    private static @Nullable ASTNode commonModifier(ASTNode left, ASTNode right) {
        ASTNode parent = left.getTreeParent();
        return parent != null && parent.getElementType() == MODIFIER && parent == right.getTreeParent() ? parent : null;
    }
}
