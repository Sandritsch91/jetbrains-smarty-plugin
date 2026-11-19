package com.smartyplugin.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.psi.tree.IElementType;
import com.smartyplugin.lang.SmartyTags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;
import static com.smartyplugin.parser.SmartyElementTypes.*;

/**
 * Builds the block structure of a template. Tag contents are parsed loosely (attributes, modifiers,
 * strings, nested tags); everything else stays a flat token stream, which keeps the parser tolerant
 * while typing. Structural problems (unclosed blocks, stray closing tags) are reported by the annotator.
 */
public final class SmartyParser implements PsiParser {
    /** yii2-smarty blocks whose content is JavaScript / CSS: their text becomes CODE_TEXT injection hosts. */
    private static final Set<String> CODE_BLOCKS = Set.of("registerJs", "registerCss");
    private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("\\{/([A-Za-z_][A-Za-z0-9_]*)\\s*}");

    private record TagInfo(boolean close, @Nullable String name) {
    }

    private PsiBuilder b;
    /** Names used in a closing tag anywhere in the file: lets custom block plugins ({form}...{/form}) nest. */
    private Set<String> closedNames;

    @Override
    public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        this.b = builder;
        this.closedNames = collectClosedNames(builder.getOriginalText());
        PsiBuilder.Marker file = builder.mark();
        Deque<String> stack = new ArrayDeque<>();
        while (!builder.eof()) {
            parseBody(stack);
            if (!builder.eof()) builder.advanceLexer();
        }
        file.done(root);
        return builder.getTreeBuilt();
    }

    private static Set<String> collectClosedNames(CharSequence text) {
        Set<String> names = new HashSet<>();
        Matcher m = CLOSE_TAG_PATTERN.matcher(text);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    private boolean isBlockName(String name) {
        if (SmartyTags.isBlock(name)) return true;
        if (SmartyTags.isKnownInline(name) || SmartyTags.isMiddle(name)) return false;
        return closedNames.contains(name);
    }

    /** Parses content until EOF, a closing tag of an enclosing block, or a middle tag of the innermost block. */
    private void parseBody(Deque<String> stack) {
        boolean code = stack.peek() != null && CODE_BLOCKS.contains(stack.peek());
        while (!b.eof()) {
            if (b.getTokenType() != LDELIM) {
                if (code && b.getTokenType() == TEMPLATE_TEXT) {
                    PsiBuilder.Marker text = b.mark();
                    b.advanceLexer();
                    text.done(CODE_TEXT);
                } else {
                    b.advanceLexer();
                }
                continue;
            }
            TagInfo info = peekTag();
            if (info.close()) {
                if (info.name() != null && stack.contains(info.name())) return;
                parseTag(CLOSE_TAG); // stray closing tag, reported by the annotator
                continue;
            }
            String name = info.name();
            if (name == null) {
                parseTag(PRINT_TAG);
            } else if (SmartyTags.isMiddle(name)) {
                if (SmartyTags.isMiddleAllowed(stack.peek(), name)) return;
                parseTag(MIDDLE_TAG); // misplaced, reported by the annotator
            } else if (isBlockName(name)) {
                parseBlock(stack, name);
            } else {
                parseTag(TAG);
            }
        }
    }

    private void parseBlock(Deque<String> stack, String name) {
        PsiBuilder.Marker block = b.mark();
        parseTag(OPEN_TAG);
        stack.push(name);
        while (true) {
            PsiBuilder.Marker body = b.mark();
            parseBody(stack);
            body.done(BLOCK_BODY);
            body.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER);
            if (b.eof()) break;
            TagInfo info = peekTag();
            if (info.close() && name.equals(info.name())) {
                parseTag(CLOSE_TAG);
                break;
            }
            if (!info.close() && SmartyTags.isMiddleAllowed(name, info.name())) {
                parseTag(MIDDLE_TAG);
                continue;
            }
            break; // closing tag of an outer block: this block stays unclosed
        }
        stack.pop();
        block.done(BLOCK);
    }

    private TagInfo peekTag() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // {
        boolean close = false;
        if (b.getTokenType() == SLASH) {
            close = true;
            b.advanceLexer();
        }
        String name = b.getTokenType() == TAG_NAME ? b.getTokenText() : null;
        m.rollbackTo();
        return new TagInfo(close, name);
    }

    private void parseTag(IElementType type) {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // {
        if (b.getTokenType() == SLASH) b.advanceLexer();
        if (b.getTokenType() == TAG_NAME) b.advanceLexer();
        parseTagContent();
        if (b.getTokenType() == RDELIM) {
            b.advanceLexer();
        } else {
            b.error("'}' expected");
        }
        m.done(type);
    }

    private void parseTagContent() {
        while (!b.eof()) {
            IElementType t = b.getTokenType();
            if (t == RDELIM || t == TEMPLATE_TEXT) return;
            if (t == ATTR_NAME) {
                parseAttribute();
            } else {
                parseExpressionPart();
            }
        }
    }

    private void parseAttribute() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // name
        if (b.getTokenType() == ASSIGN) b.advanceLexer();
        while (!b.eof()) {
            IElementType t = b.getTokenType();
            if (t == RDELIM || t == TEMPLATE_TEXT || t == ATTR_NAME) break;
            parseExpressionPart();
        }
        m.done(ATTRIBUTE);
    }

    private void parseExpressionPart() {
        IElementType t = b.getTokenType();
        if (t == LDELIM) {
            parseTag(peekTag().name() == null ? PRINT_TAG : TAG);
        } else if (t == PIPE) {
            parseModifier();
        } else if (t == STRING) {
            PsiBuilder.Marker s = b.mark();
            b.advanceLexer();
            s.done(STRING_LITERAL);
        } else {
            b.advanceLexer();
        }
    }

    /** |name:arg1:arg2 — an argument ends at ':', '|', '}' or whitespace. */
    private void parseModifier() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // |
        if (b.getTokenType() == AT) b.advanceLexer();
        if (b.getTokenType() == MODIFIER_NAME) b.advanceLexer();
        while (b.getTokenType() == COLON) {
            b.advanceLexer();
            boolean first = true;
            int parens = 0;
            while (!b.eof()) {
                IElementType t = b.getTokenType();
                if (t == RDELIM || t == TEMPLATE_TEXT || t == ATTR_NAME) break;
                if (parens == 0 && (t == COLON || t == PIPE || t == RPAREN || t == RBRACKET || t == COMMA)) break;
                if (!first && parens == 0 && b.rawLookup(-1) == WHITE_SPACE) break;
                if (t == LPAREN || t == LBRACKET) parens++;
                if (t == RPAREN || t == RBRACKET) parens--;
                parseExpressionPart();
                first = false;
            }
        }
        m.done(MODIFIER);
    }
}
