package com.smartyplugin.lexer;

import com.intellij.lexer.LexerBase;
import com.smartyplugin.lang.SmartyDelimiters;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;

/**
 * Hand written lexer for Smarty 3/4/5 templates using the default <code>{</code> / <code>}</code> delimiters.
 * <p>
 * Follows Smarty's "auto literal" rule: a <code>{</code> followed by whitespace is plain text, so inline
 * JavaScript and CSS keep working without {literal} blocks.
 * <p>
 * The lexer state is fully encoded in an int so the editor highlighter can restart anywhere:
 * <pre>
 *   bits 0-1  base state (CONTENT, TAG, TAG_START, LITERAL)
 *   bit  2    previous significant token was '|' (next identifier is a modifier name)
 *   bit  3    current tag is {literal ...}: switch to LITERAL after its closing '}'
 *   bits 4+   nesting depth of '{' inside tags
 * </pre>
 */
public final class SmartyLexer extends LexerBase {
    // the delimiters are fixed per lexer instance, so the int state stays enough to restart anywhere
    static final int CONTENT = 0;
    static final int TAG = 1;
    static final int TAG_START = 2;
    static final int LITERAL = 3;
    private static final int BASE_MASK = 0x3;
    private static final int AFTER_PIPE = 0x4;
    private static final int LITERAL_TAG = 0x8;
    private static final int DEPTH_SHIFT = 4;
    /** Inside a tag, after its name: lexes a bare expression such as the "$a.b" of "`$a.b`" in a string. */
    public static final int EXPRESSION_STATE = TAG | (1 << DEPTH_SHIFT);

    /** left_delimiter / right_delimiter, "{" and "}" unless configured. */
    private final String ld;
    private final String rd;
    /** {/literal} in lower case, for a case-insensitive search. */
    private final String literalEnd;

    public SmartyLexer() {
        this(SmartyDelimiters.DEFAULT);
    }

    public SmartyLexer(@NotNull SmartyDelimiters delimiters) {
        this.ld = delimiters.left();
        this.rd = delimiters.right();
        this.literalEnd = delimiters.closeTag("literal").toLowerCase(java.util.Locale.ROOT);
    }

    private static final Set<String> KEYWORDS = Set.of(
            "eq", "ne", "neq", "gt", "lt", "ge", "gte", "le", "lte", "and", "or", "not", "xor", "mod",
            "is", "div", "by", "even", "odd", "as", "to", "step", "in", "instanceof", "isset", "empty");
    private static final Set<String> CONSTANTS = Set.of("true", "false", "null", "TRUE", "FALSE", "NULL");

    private CharSequence buffer = "";
    private int bufferEnd;
    private int tokenStart;
    private int tokenEnd;
    private int state;
    private int tokenState;
    private IElementType tokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.tokenEnd = startOffset;
        this.state = initialState;
        advance();
    }

    @Override
    public int getState() {
        return tokenState;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }

    @Override
    public void advance() {
        tokenStart = tokenEnd;
        tokenState = state;
        if (tokenStart >= bufferEnd) {
            tokenType = null;
            return;
        }
        switch (state & BASE_MASK) {
            case CONTENT -> lexContent();
            case LITERAL -> lexLiteral();
            default -> lexTag();
        }
    }

    // ---------------------------------------------------------------- content

    private void lexContent() {
        int p = tokenStart;
        if (isTagStart(p)) {
            if (lexComment(p)) return;
            tokenEnd = p + ld.length();
            tokenType = LDELIM;
            state = compose(TAG_START, false, false, 1);
            return;
        }
        while (p < bufferEnd && !isTagStart(p)) p++;
        tokenEnd = p;
        tokenType = TEMPLATE_TEXT;
    }

    /** {* comment *} starting at p (p is at the left delimiter); false if there is none. */
    private boolean lexComment(int p) {
        if (charAt(p + ld.length()) != '*') return false;
        String end = "*" + rd;
        int close = indexOf(end, p + ld.length() + 1);
        tokenEnd = close < 0 ? bufferEnd : close + end.length();
        tokenType = COMMENT;
        return true;
    }

    private void lexLiteral() {
        int end = indexOfIgnoreCase(literalEnd, tokenStart);
        if (end == tokenStart) {
            tokenEnd = tokenStart + ld.length();
            tokenType = LDELIM;
            state = compose(TAG_START, false, false, 1);
            return;
        }
        tokenEnd = end < 0 ? bufferEnd : end;
        tokenType = TEMPLATE_TEXT;
    }

    /** The left delimiter starts a tag unless it is followed by whitespace (Smarty auto_literal). */
    private boolean isTagStart(int p) {
        int after = p + ld.length();
        return startsWith(p, ld) && after < bufferEnd && !Character.isWhitespace(charAt(after));
    }

    private boolean startsWith(int p, String s) {
        if (p < 0 || p + s.length() > bufferEnd) return false;
        for (int i = 0; i < s.length(); i++) {
            if (buffer.charAt(p + i) != s.charAt(i)) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- tags

    private void lexTag() {
        final int base = state & BASE_MASK;
        final boolean literalTag = (state & LITERAL_TAG) != 0;
        final int depth = state >>> DEPTH_SHIFT;
        final int p = tokenStart;
        final char c = charAt(p);

        if (Character.isWhitespace(c)) {
            int q = p;
            while (q < bufferEnd && Character.isWhitespace(charAt(q))) q++;
            tokenEnd = q;
            tokenType = WHITE_SPACE;
            return; // state unchanged
        }

        if (startsWith(p, rd)) {
            tokenEnd = p + rd.length();
            tokenType = RDELIM;
            int newDepth = depth - 1;
            if (newDepth <= 0) {
                state = compose(literalTag ? LITERAL : CONTENT, false, false, 0);
            } else {
                state = compose(TAG, false, literalTag, newDepth);
            }
            return;
        }
        if (startsWith(p, ld)) {
            if (lexComment(p)) return;
            tokenEnd = p + ld.length();
            tokenType = LDELIM;
            state = compose(TAG_START, false, literalTag, depth + 1);
            return;
        }
        if (c == '/' && base == TAG_START) {
            tokenEnd = p + 1;
            tokenType = SLASH;
            return;
        }

        boolean afterPipe = (state & AFTER_PIPE) != 0;
        boolean nextAfterPipe = false;
        boolean nextLiteralTag = literalTag;

        if (isIdentStart(c)) {
            int q = p + 1;
            while (q < bufferEnd && isIdentPart(charAt(q))) q++;
            tokenEnd = q;
            String word = buffer.subSequence(p, q).toString();
            if (base == TAG_START) {
                tokenType = TAG_NAME;
                if (depth == 1 && "literal".equals(word) && charAt(p - 1) != '/') {
                    nextLiteralTag = true;
                }
            } else if (afterPipe) {
                tokenType = MODIFIER_NAME;
            } else if (!isMemberAccess(p) && isFollowedByAssign(q)) {
                tokenType = ATTR_NAME; // before keywords: {use class="X" as="Y"}
            } else if (KEYWORDS.contains(word) && !isMemberAccess(p)) {
                tokenType = KEYWORD;
            } else if (CONSTANTS.contains(word) && !isMemberAccess(p)) {
                tokenType = CONSTANT;
            } else {
                tokenType = IDENTIFIER;
            }
        } else if (c == '$') {
            int q = p + 1;
            while (q < bufferEnd && isIdentPart(charAt(q))) q++;
            tokenEnd = q;
            tokenType = VARIABLE;
        } else if (c == '#') {
            int q = p + 1;
            while (q < bufferEnd && isIdentPart(charAt(q))) q++;
            if (q > p + 1 && charAt(q) == '#') {
                tokenEnd = q + 1;
                tokenType = CONFIG_VAR;
            } else {
                tokenEnd = p + 1;
                tokenType = BAD_CHARACTER;
            }
        } else if (c == '"' || c == '\'') {
            tokenEnd = scanString(p, c);
            tokenType = STRING;
        } else if (Character.isDigit(c)) {
            int q = p;
            while (q < bufferEnd && Character.isDigit(charAt(q))) q++;
            if (charAt(q) == '.' && Character.isDigit(charAt(q + 1))) {
                q++;
                while (q < bufferEnd && Character.isDigit(charAt(q))) q++;
            }
            tokenEnd = q;
            tokenType = NUMBER;
        } else {
            lexPunctuation(p, c);
            if (tokenType == PIPE) nextAfterPipe = true;
            if (tokenType == AT && afterPipe) nextAfterPipe = true; // {$list|@count}
        }

        state = compose(TAG, nextAfterPipe, nextLiteralTag, depth);
    }

    private void lexPunctuation(int p, char c) {
        char n = charAt(p + 1);
        char n2 = charAt(p + 2);
        tokenEnd = p + 1;
        switch (c) {
            case '|' -> {
                if (n == '|') { tokenEnd = p + 2; tokenType = OPERATOR; } else tokenType = PIPE;
            }
            case '@' -> tokenType = AT;
            case '-' -> {
                if (n == '>') { tokenEnd = p + 2; tokenType = ARROW; }
                else if (n == '-' || n == '=') { tokenEnd = p + 2; tokenType = OPERATOR; }
                else tokenType = OPERATOR;
            }
            case '=' -> {
                if (n == '=' && n2 == '=') { tokenEnd = p + 3; tokenType = OPERATOR; }
                else if (n == '=') { tokenEnd = p + 2; tokenType = OPERATOR; }
                else if (n == '>') { tokenEnd = p + 2; tokenType = DOUBLE_ARROW; }
                else tokenType = ASSIGN;
            }
            case '!' -> {
                if (n == '=' && n2 == '=') tokenEnd = p + 3;
                else if (n == '=') tokenEnd = p + 2;
                tokenType = OPERATOR;
            }
            case '<' -> {
                if (n == '=' || n == '>') tokenEnd = p + 2;
                tokenType = OPERATOR;
            }
            case '>' -> {
                if (n == '=') tokenEnd = p + 2;
                tokenType = OPERATOR;
            }
            case '&' -> {
                if (n == '&') tokenEnd = p + 2;
                tokenType = OPERATOR;
            }
            case '+' -> {
                if (n == '+' || n == '=') tokenEnd = p + 2;
                tokenType = OPERATOR;
            }
            case '*', '/', '%', '?', '^', '~' -> {
                if (n == '=' && c != '?') tokenEnd = p + 2;
                tokenType = OPERATOR;
            }
            case ':' -> {
                if (n == ':') { tokenEnd = p + 2; tokenType = DOUBLE_COLON; } else tokenType = COLON;
            }
            case '.' -> tokenType = DOT;
            case ',' -> tokenType = COMMA;
            case '(' -> tokenType = LPAREN;
            case ')' -> tokenType = RPAREN;
            case '[' -> tokenType = LBRACKET;
            case ']' -> tokenType = RBRACKET;
            default -> tokenType = BAD_CHARACTER;
        }
    }

    /**
     * Strings end at the matching quote, so they may contain <code>}</code> and line breaks
     * (<code>'function () { return {a: 1}; }'</code>). Only a string that is never closed stops early,
     * at the end of the line or tag, so that a missing quote does not swallow the rest of the file.
     */
    private int scanString(int p, char quote) {
        int end = scanTerminatedString(p, quote);
        return end >= 0 ? end : scanUnterminatedString(p, quote);
    }

    /** End offset after the closing quote, or -1 when the string is not closed. */
    private int scanTerminatedString(int p, char quote) {
        int q = p + 1;
        int tags = 0; // tags embedded in a double-quoted string ("Hi {$name|default:"you"}") may contain quotes
        while (q < bufferEnd) {
            char ch = charAt(q);
            if (ch == '\\') {
                q += 2;
                continue;
            }
            if (quote == '"') {
                if (startsWith(q, ld) && startsEmbeddedTag(q + ld.length())) {
                    tags++;
                    q += ld.length();
                    continue;
                }
                if (tags > 0 && startsWith(q, rd)) {
                    tags--;
                    q += rd.length();
                    continue;
                }
            }
            if (ch == quote && tags == 0) return q + 1;
            q++;
        }
        return -1;
    }

    private boolean startsEmbeddedTag(int q) {
        if (q >= bufferEnd) return false;
        char ch = charAt(q);
        return ch == '$' || ch == '/' || ch == '#' || Character.isLetter(ch);
    }

    private int scanUnterminatedString(int p, char quote) {
        int q = p + 1;
        int braces = 0;
        while (q < bufferEnd) {
            char ch = charAt(q);
            if (ch == '\\') {
                q += 2;
                continue;
            }
            if (ch == '\n') return q;
            if (quote == '"' && startsWith(q, ld)) {
                braces++;
                q += ld.length();
                continue;
            }
            if (startsWith(q, rd)) {
                if (braces == 0) return q; // don't swallow the tag end
                braces--;
                q += rd.length();
                continue;
            }
            q++;
        }
        return Math.min(q, bufferEnd);
    }

    /** Identifier directly preceded by '.', '->' or '::' (property / method access). */
    private boolean isMemberAccess(int p) {
        char prev = charAt(p - 1);
        return prev == '.' || (prev == '>' && charAt(p - 2) == '-') || (prev == ':' && charAt(p - 2) == ':');
    }

    private boolean isFollowedByAssign(int q) {
        while (q < bufferEnd && (charAt(q) == ' ' || charAt(q) == '\t')) q++;
        return charAt(q) == '=' && charAt(q + 1) != '=' && charAt(q + 1) != '>';
    }

    // ---------------------------------------------------------------- helpers

    private static int compose(int base, boolean afterPipe, boolean literalTag, int depth) {
        return base | (afterPipe ? AFTER_PIPE : 0) | (literalTag ? LITERAL_TAG : 0) | (Math.max(depth, 0) << DEPTH_SHIFT);
    }

    private char charAt(int i) {
        return i >= 0 && i < bufferEnd ? buffer.charAt(i) : '\0';
    }

    private int indexOf(String s, int from) {
        outer:
        for (int i = from; i <= bufferEnd - s.length(); i++) {
            for (int j = 0; j < s.length(); j++) {
                if (buffer.charAt(i + j) != s.charAt(j)) continue outer;
            }
            return i;
        }
        return -1;
    }

    private int indexOfIgnoreCase(String s, int from) {
        outer:
        for (int i = from; i <= bufferEnd - s.length(); i++) {
            for (int j = 0; j < s.length(); j++) {
                if (Character.toLowerCase(buffer.charAt(i + j)) != s.charAt(j)) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
