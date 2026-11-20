package com.smartyplugin.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import static com.smartyplugin.parser.SmartyElementTypes.BLOCK;
import static com.smartyplugin.parser.SmartyElementTypes.BLOCK_BODY;
import static com.smartyplugin.parser.SmartyElementTypes.CODE_TEXT;

/**
 * Indentation of the JavaScript / CSS in <code>{registerJs}</code> and <code>{registerCss}</code>.
 * <p>
 * The code is often cut into pieces by Smarty tags (<code>var id = {$model->id};</code>), which the JavaScript and CSS
 * formatters can't handle, so it is indented here, line by line, like multi-line Smarty tags: one level per open
 * <code>{ ( [</code>, several opened on one line count once (<code>$(function() {</code>), a line starting with a
 * closing bracket goes back to the level of the line that opened it. Brackets in strings and comments don't count;
 * continuation lines of block comments and template literals keep their layout. Spacing within lines is kept.
 */
final class SmartyCodeLayout {
    static final Set<String> CODE_BLOCKS = Set.of("registerJs", "registerCss");

    private SmartyCodeLayout() {
    }

    /** The body of a {registerJs} / {registerCss} block. */
    static boolean isCodeBody(@NotNull ASTNode node) {
        if (node.getElementType() != BLOCK_BODY) return false;
        ASTNode block = node.getTreeParent();
        return block != null && block.getElementType() == BLOCK && CODE_BLOCKS.contains(SmartyPsiUtil.blockName(block));
    }

    /** A line of code (or a comment that continues over several lines), indented by bracket depth. */
    static final class LineBlock implements Block {
        private final TextRange range;
        private final Indent indent;

        LineBlock(TextRange range, Indent indent) {
            this.range = range;
            this.indent = indent;
        }

        @Override
        public @NotNull TextRange getTextRange() {
            return range;
        }

        @Override
        public @NotNull List<Block> getSubBlocks() {
            return List.of();
        }

        @Override
        public @Nullable Wrap getWrap() {
            return null;
        }

        @Override
        public @Nullable Indent getIndent() {
            return indent;
        }

        @Override
        public @Nullable Alignment getAlignment() {
            return null;
        }

        @Override
        public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
            return null;
        }

        @Override
        public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
            return new ChildAttributes(Indent.getNoneIndent(), null);
        }

        @Override
        public boolean isIncomplete() {
            return false;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }
    }

    // ------------------------------------------------------------------ formatted by the language's formatter

    private static final java.util.Map<String, String> LANGUAGES = java.util.Map.of(
            "registerJs", "JavaScript",
            "registerCss", "CSS");
    private static final java.util.Map<String, String> EXTENSIONS = java.util.Map.of(
            "JavaScript", "js",
            "CSS", "css");
    /** Stands in for a Smarty tag in the copy given to the formatter, as in the injection. */
    private static final String PLACEHOLDER = "0";

    /** A piece of the body that the formatter treats as a unit: a run of code without inner changes, or a tag. */
    private record Unit(int hostStart, int hostEnd, int copyStart, int copyEnd, @Nullable ASTNode tag) {
    }

    /** A unit with the whitespace and indent the language formatter gave it. */
    static final class CodeBlock implements Block, com.intellij.formatting.templateLanguages.BlockWithParent {
        private final TextRange range;
        private final Indent indent;
        final @Nullable Spacing spacingBefore;
        private final @Nullable Block tag;
        private com.intellij.formatting.templateLanguages.BlockWithParent parent;

        CodeBlock(TextRange range, Indent indent, @Nullable Spacing spacingBefore, @Nullable Block tag) {
            this.range = range;
            this.indent = indent;
            this.spacingBefore = spacingBefore;
            this.tag = tag;
            if (tag instanceof com.intellij.formatting.templateLanguages.BlockWithParent child) child.setParent(this);
        }

        @Override public @NotNull TextRange getTextRange() { return range; }
        @Override public @NotNull List<Block> getSubBlocks() { return tag == null ? List.of() : List.of(tag); }
        @Override public @Nullable Wrap getWrap() { return null; }
        @Override public @Nullable Indent getIndent() { return indent; }
        @Override public @Nullable Alignment getAlignment() { return null; }
        @Override public @Nullable Spacing getSpacing(@Nullable Block c1, @NotNull Block c2) { return null; }
        @Override public @NotNull ChildAttributes getChildAttributes(int i) { return new ChildAttributes(Indent.getNoneIndent(), null); }
        @Override public boolean isIncomplete() { return false; }
        @Override public boolean isLeaf() { return tag == null; }
        @Override public com.intellij.formatting.templateLanguages.BlockWithParent getParent() { return parent; }
        @Override public void setParent(com.intellij.formatting.templateLanguages.BlockWithParent p) { parent = p; }
    }

    /** Spacing between two children of a code body: what the language formatter produced. */
    static @Nullable Spacing spacing(@Nullable Block child1, @NotNull Block child2) {
        return child2 instanceof CodeBlock code ? code.spacingBefore : null;
    }

    /**
     * Formats a copy of the body's code with the JavaScript / CSS formatter (Smarty tags replaced by a placeholder)
     * and returns blocks that reproduce its whitespace in the template. Null when that is not possible (no such
     * language, not in a write action, or the formatter changed more than whitespace).
     */
    static @Nullable List<Block> formattedBlocks(@NotNull ASTNode body, int base, @NotNull TagBlocks tags) {
        String languageId = LANGUAGES.get(SmartyPsiUtil.blockName(body.getTreeParent()));
        com.intellij.lang.Language language = languageId == null ? null : com.intellij.lang.Language.findLanguageByID(languageId);
        if (language == null) return null;
        if (!com.intellij.openapi.application.ApplicationManager.getApplication().isWriteAccessAllowed()) return null;

        // the copy: code as written, every tag replaced by the placeholder
        StringBuilder copy = new StringBuilder();
        List<Unit> pieces = new ArrayList<>(); // code texts and tags, with their offsets in host and copy
        for (ASTNode child = body.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            int copyStart = copy.length();
            if (child.getElementType() == CODE_TEXT) {
                copy.append(child.getChars());
                pieces.add(new Unit(child.getStartOffset(), child.getStartOffset() + child.getTextLength(), copyStart, copy.length(), null));
            } else if (!child.getText().isBlank()) {
                copy.append(PLACEHOLDER);
                pieces.add(new Unit(child.getStartOffset(), child.getStartOffset() + child.getTextLength(), copyStart, copy.length(), child));
            } else {
                copy.append(child.getChars());
            }
        }
        String original = copy.toString();
        String formatted = format(body, language, original);
        if (formatted == null || !nonWhitespace(original).equals(nonWhitespace(formatted))) return null;

        // map every non-whitespace character of the original copy to its position in the formatted text
        int[] target = new int[original.length()];
        int f = 0;
        for (int i = 0; i < original.length(); i++) {
            if (Character.isWhitespace(original.charAt(i))) continue;
            while (Character.isWhitespace(formatted.charAt(f))) f++;
            target[i] = f++;
        }
        int minIndent = minIndent(formatted);

        List<Block> result = new ArrayList<>();
        int unitStart = -1; // copy offset of the current unit's first character
        int last = -1;      // copy offset of the previous non-whitespace character
        Unit piece = null;
        for (int i = 0; i <= original.length(); i++) {
            boolean end = i == original.length();
            if (!end && Character.isWhitespace(original.charAt(i))) continue;
            Unit at = end ? null : pieceAt(pieces, i);
            boolean boundary = end || last < 0 || at != piece || at.tag() != null
                    || i != last + 1 || target[i] != target[last] + 1;
            if (boundary && unitStart >= 0) {
                result.add(block(piece, unitStart, last + 1, target, formatted, original, base, minIndent, tags,
                        result.isEmpty()));
            }
            if (end) break;
            if (boundary) unitStart = i;
            piece = at;
            last = i;
            if (at != null && at.tag() != null) {
                // a tag is one unit: its placeholder is a single character
                last = at.copyEnd() - 1;
                i = last;
            }
        }
        return result;
    }

    private static @Nullable Unit pieceAt(List<Unit> pieces, int copyOffset) {
        for (Unit u : pieces) if (copyOffset >= u.copyStart() && copyOffset < u.copyEnd()) return u;
        return null;
    }

    private static Block block(Unit piece, int copyStart, int copyEnd, int[] target, String formatted, String original,
                               int base, int minIndent, TagBlocks tags, boolean first) {
        TextRange range;
        Block tag = null;
        if (piece.tag() != null) {
            range = TextRange.create(piece.hostStart(), piece.hostEnd());
            tag = tags.create(piece.tag());
        } else {
            int shift = piece.hostStart() - piece.copyStart();
            range = TextRange.create(copyStart + shift, copyEnd + shift);
        }
        // whitespace before the unit in the formatted text
        int at = target[copyStart];
        int ws = at;
        while (ws > 0 && Character.isWhitespace(formatted.charAt(ws - 1))) ws--;
        String gap = formatted.substring(ws, at);
        int lineFeeds = (int) gap.chars().filter(c -> c == '\n').count();
        Spacing spacing = first ? null : lineFeeds > 0
                ? Spacing.createSpacing(0, 0, lineFeeds, false, lineFeeds - 1)
                : Spacing.createSpacing(gap.length(), gap.length(), 0, false, 0);
        boolean startsLine = lineFeeds > 0 || ws == 0 || first;
        Indent indent = startsLine
                ? Indent.getSpaceIndent(base + Math.max(0, lineIndent(formatted, at) - minIndent))
                : Indent.getNoneIndent();
        return new CodeBlock(range, indent, spacing, tag);
    }

    private static @Nullable String format(ASTNode body, com.intellij.lang.Language language, String text) {
        com.intellij.openapi.project.Project project = body.getPsi().getProject();
        try {
            com.intellij.psi.PsiFile copy = com.intellij.psi.PsiFileFactory.getInstance(project).createFileFromText(
                    "smarty-code." + EXTENSIONS.get(language.getID()), language, text, false, false);
            com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project).reformat(copy);            return copy.getText();
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            return null; // fall back to the bracket layout
        }
    }

    private static String nonWhitespace(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) if (!Character.isWhitespace(s.charAt(i))) sb.append(s.charAt(i));
        return sb.toString();
    }

    /** Indentation (columns) of the line containing offset. */
    private static int lineIndent(String text, int offset) {
        int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
        int col = 0;
        for (int i = lineStart; i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t'); i++) {
            col += text.charAt(i) == '\t' ? 4 : 1;
        }
        return col;
    }

    /** The smallest indentation of a non-empty line: the formatted code's own base level. */
    private static int minIndent(String text) {
        int min = Integer.MAX_VALUE;
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            min = Math.min(min, lineIndent(line, 0));
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /** Factory for the blocks of Smarty tags inside the code ({$model->id}). */
    interface TagBlocks {
        @NotNull Block create(@NotNull ASTNode tag);
    }

    /**
     * Blocks for the body: a {@link LineBlock} per line of code text, and the given blocks for Smarty tags in
     * between. {@code base} is the indent (in columns) of the body's lines relative to the body.
     */
    static List<Block> buildChildren(@NotNull ASTNode body, int base, int unit, @NotNull TagBlocks tags) {
        List<Block> result = new ArrayList<>();
        Scanner scanner = new Scanner();
        Deque<Integer> open = new ArrayDeque<>(); // content level of each open bracket
        int lineLevel = 0;
        boolean atLineStart = true; // the body starts right after the opening tag; its first line counts as new
        LineBlock last = null;

        for (ASTNode child = body.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() != CODE_TEXT) {
                if (!child.getText().isBlank()) {
                    result.add(tags.create(child));
                    last = null;
                    atLineStart = false;
                }
                continue;
            }
            CharSequence text = child.getChars();
            int start = child.getStartOffset();
            int i = 0;
            while (i < text.length()) {
                int lineEnd = indexOf(text, '\n', i);
                int contentStart = i;
                while (contentStart < lineEnd && isBlank(text.charAt(contentStart))) contentStart++;
                int contentEnd = lineEnd;
                while (contentEnd > contentStart && isBlank(text.charAt(contentEnd - 1))) contentEnd--;
                boolean newLine = atLineStart || i > 0;

                if (contentEnd > contentStart) {
                    boolean continuation = newLine && scanner.insideMultiLine() && last != null;
                    if (newLine && !continuation) {
                        char first = text.charAt(contentStart);
                        if (isCloser(first)) lineLevel = open.isEmpty() ? 0 : Math.max(0, open.peek() - 1);
                        else lineLevel = open.isEmpty() ? 0 : open.peek();
                    }
                    TextRange range = new TextRange(start + contentStart, start + contentEnd);
                    if (continuation) {
                        // a block comment or template literal spanning lines: one block, inner layout kept
                        int at = result.indexOf(last);
                        last = new LineBlock(last.getTextRange().union(range), last.getIndent());
                        result.set(at, last);
                    } else {
                        last = new LineBlock(range, Indent.getSpaceIndent(base + lineLevel * unit));
                        result.add(last);
                    }
                    scanner.scan(text, contentStart, contentEnd, open, lineLevel);
                }
                if (lineEnd >= text.length()) {
                    atLineStart = false;
                    break;
                }
                scanner.lineBreak();
                atLineStart = true;
                i = lineEnd + 1;
            }
            if (text.length() > 0 && text.charAt(text.length() - 1) == '\n') atLineStart = true;
        }
        return result;
    }

    private static int indexOf(CharSequence text, char c, int from) {
        for (int i = from; i < text.length(); i++) if (text.charAt(i) == c) return i;
        return text.length();
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t' || c == '\r';
    }

    private static boolean isCloser(char c) {
        return c == '}' || c == ')' || c == ']';
    }

    /** Tracks strings and comments across lines and across Smarty tags, and counts brackets outside them. */
    private static final class Scanner {
        private char quote; // ' " ` while inside a string
        private boolean blockComment;

        boolean insideMultiLine() {
            return blockComment || quote == '`';
        }

        void lineBreak() {
            if (quote == '\'' || quote == '"') quote = 0; // unterminated single-line string
        }

        void scan(CharSequence text, int from, int to, Deque<Integer> open, int lineLevel) {
            for (int i = from; i < to; i++) {
                char c = text.charAt(i);
                char next = i + 1 < to ? text.charAt(i + 1) : 0;
                if (blockComment) {
                    if (c == '*' && next == '/') {
                        blockComment = false;
                        i++;
                    }
                } else if (quote != 0) {
                    if (c == '\\') i++;
                    else if (c == quote) quote = 0;
                } else if (c == '/' && next == '*') {
                    blockComment = true;
                    i++;
                } else if (c == '/' && next == '/') {
                    return; // line comment
                } else if (c == '\'' || c == '"' || c == '`') {
                    quote = c;
                } else if (c == '{' || c == '(' || c == '[') {
                    open.push(lineLevel + 1);
                } else if (isCloser(c) && !open.isEmpty()) {
                    open.pop();
                }
            }
        }
    }
}
