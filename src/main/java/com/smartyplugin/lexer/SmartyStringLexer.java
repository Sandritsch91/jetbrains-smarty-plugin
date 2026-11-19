package com.smartyplugin.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;

/**
 * Splits one double-quoted string token into the parts Smarty evaluates, for highlighting only
 * (it runs as a layer of the highlighting lexer; the PSI keeps one string token):
 * <ul>
 *   <li><code>"Hello $name"</code>: a simple variable (letters, digits, '_'; <code>$a.b</code> embeds just <code>$a</code>)</li>
 *   <li><code>"Hello `$user.name`"</code>: a backtick expression</li>
 *   <li><code>"Hello {$name|escape}"</code>, <code>"{counter}"</code>: an embedded tag</li>
 * </ul>
 * Everything else, and every single-quoted string, stays {@link SmartyTokenTypes#STRING}.
 */
public final class SmartyStringLexer extends LexerBase {
    private record Token(IElementType type, int start, int end) {
    }

    private CharSequence buffer = "";
    private int bufferEnd;
    private final List<Token> tokens = new ArrayList<>();
    private int index;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        tokens.clear();
        index = 0;
        if (startOffset >= endOffset) return;
        if (buffer.charAt(startOffset) != '"') {
            tokens.add(new Token(STRING, startOffset, endOffset));
            return;
        }
        split(startOffset, endOffset);
    }

    private void split(int start, int end) {
        int textStart = start;
        int p = start + 1; // after the opening quote
        while (p < end) {
            char c = buffer.charAt(p);
            if (c == '\\') {
                p += 2;
                continue;
            }
            int partEnd = -1;
            if (c == '$' && p + 1 < end && isIdentStart(buffer.charAt(p + 1))) {
                partEnd = p + 2;
                while (partEnd < end && isIdentPart(buffer.charAt(partEnd))) partEnd++;
                flushText(textStart, p);
                tokens.add(new Token(VARIABLE, p, partEnd));
            } else if (c == '`') {
                int close = indexOf('`', p + 1, end);
                if (close > 0) {
                    partEnd = close + 1;
                    flushText(textStart, p);
                    tokens.add(new Token(BACKTICK, p, p + 1));
                    delegate(p + 1, close, SmartyLexer.EXPRESSION_STATE);
                    tokens.add(new Token(BACKTICK, close, close + 1));
                }
            } else if (c == '{' && p + 1 < end && !Character.isWhitespace(buffer.charAt(p + 1))) {
                int close = matchingBrace(p, end);
                if (close > 0) {
                    partEnd = close + 1;
                    flushText(textStart, p);
                    delegate(p, partEnd, 0);
                }
            }
            if (partEnd > 0) {
                p = partEnd;
                textStart = p;
            } else {
                p++;
            }
        }
        flushText(textStart, end);
    }

    private void flushText(int from, int to) {
        if (to > from) tokens.add(new Token(STRING, from, to));
    }

    /** Lexes an embedded tag or expression with the regular Smarty lexer. */
    private void delegate(int from, int to, int state) {
        if (to <= from) return;
        SmartyLexer lexer = new SmartyLexer();
        lexer.start(buffer, from, to, state);
        IElementType type;
        while ((type = lexer.getTokenType()) != null) {
            tokens.add(new Token(type, lexer.getTokenStart(), lexer.getTokenEnd()));
            lexer.advance();
        }
    }

    /** Offset of the '}' closing the tag that starts at {@code open}, or -1; quotes and nested tags are skipped. */
    private int matchingBrace(int open, int end) {
        int depth = 0;
        char quote = 0;
        for (int q = open; q < end; q++) {
            char ch = buffer.charAt(q);
            if (quote != 0) {
                if (ch == '\\') q++;
                else if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"' && q > open) {
                // the outer string's closing quote can't be inside the tag: an unmatched '"' ends the search
                if (ch == '"' && indexOf('"', q + 1, end) < 0) return -1;
                quote = ch;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}' && --depth == 0) {
                return q;
            }
        }
        return -1;
    }

    private int indexOf(char ch, int from, int end) {
        for (int q = from; q < end; q++) {
            if (buffer.charAt(q) == ch) return q;
        }
        return -1;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private @Nullable Token current() {
        return index < tokens.size() ? tokens.get(index) : null;
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        Token t = current();
        return t == null ? null : t.type();
    }

    @Override
    public int getTokenStart() {
        Token t = current();
        return t == null ? bufferEnd : t.start();
    }

    @Override
    public int getTokenEnd() {
        Token t = current();
        return t == null ? bufferEnd : t.end();
    }

    @Override
    public void advance() {
        index++;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }
}
