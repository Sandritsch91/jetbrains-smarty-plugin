package com.smartyplugin.editor;

import com.intellij.codeInsight.generation.CommenterDataHolder;
import com.intellij.codeInsight.generation.SelfManagingCommenter;
import com.intellij.lang.Commenter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.smartyplugin.lang.SmartyDelimiters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <code>{* ... *}</code> comments, built from the project's delimiters (<code>{{* ... *}}</code> with custom ones).
 * Self-managing so that it can look at the file's project; Smarty has no line comments, so commenting a line
 * wraps it in a block comment.
 */
public final class SmartyCommenter implements Commenter, SelfManagingCommenter<SmartyCommenter.Data> {

    static final class Data extends CommenterDataHolder {
        final String prefix;
        final String suffix;

        Data(SmartyDelimiters d) {
            prefix = d.commentStart();
            suffix = d.commentEnd();
        }
    }

    // ---------------------------------------------------------------- plain Commenter (default delimiters)

    @Override
    public @Nullable String getLineCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getBlockCommentPrefix() {
        return SmartyDelimiters.DEFAULT.commentStart();
    }

    @Override
    public @Nullable String getBlockCommentSuffix() {
        return SmartyDelimiters.DEFAULT.commentEnd();
    }

    @Override
    public @Nullable String getCommentedBlockCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentSuffix() {
        return null;
    }

    // ---------------------------------------------------------------- lines

    @Override
    public @Nullable Data createLineCommentingState(int startLine, int endLine, @NotNull Document document,
                                                   @NotNull PsiFile file) {
        return new Data(SmartyDelimiters.of(file.getProject()));
    }

    @Override
    public void commentLine(int line, int offset, @NotNull Document document, @NotNull Data data) {
        int end = document.getLineEndOffset(line);
        document.insertString(end, data.suffix);
        document.insertString(offset, data.prefix);
    }

    @Override
    public void uncommentLine(int line, int offset, @NotNull Document document, @NotNull Data data) {
        int end = document.getLineEndOffset(line);
        CharSequence text = document.getCharsSequence();
        int contentEnd = end;
        while (contentEnd > offset && Character.isWhitespace(text.charAt(contentEnd - 1))) contentEnd--;
        if (endsWith(text, contentEnd, data.suffix)) {
            document.deleteString(contentEnd - data.suffix.length(), contentEnd);
        }
        if (startsWith(text, offset, data.prefix)) document.deleteString(offset, offset + data.prefix.length());
    }

    @Override
    public boolean isLineCommented(int line, int offset, @NotNull Document document, @NotNull Data data) {
        CharSequence text = document.getCharsSequence();
        int end = document.getLineEndOffset(line);
        while (end > offset && Character.isWhitespace(text.charAt(end - 1))) end--;
        return startsWith(text, offset, data.prefix) && endsWith(text, end, data.suffix)
                && end - offset >= data.prefix.length() + data.suffix.length();
    }

    @Override
    public @Nullable String getCommentPrefix(int line, @NotNull Document document, @NotNull Data data) {
        return data.prefix;
    }

    // ---------------------------------------------------------------- blocks

    @Override
    public @Nullable Data createBlockCommentingState(int selectionStart, int selectionEnd, @NotNull Document document,
                                                    @NotNull PsiFile file) {
        return new Data(SmartyDelimiters.of(file.getProject()));
    }

    /** The comment around the selection, if there is one. */
    @Override
    public @Nullable TextRange getBlockCommentRange(int selectionStart, int selectionEnd, @NotNull Document document,
                                                    @NotNull Data data) {
        String text = document.getText();
        int start = text.lastIndexOf(data.prefix, selectionStart);
        if (start < 0) return null;
        int end = text.indexOf(data.suffix, start + data.prefix.length());
        if (end < 0) return null;
        end += data.suffix.length();
        return end >= selectionEnd ? new TextRange(start, end) : null;
    }

    @Override
    public @Nullable String getBlockCommentPrefix(int selectionStart, @NotNull Document document, @NotNull Data data) {
        return data.prefix;
    }

    @Override
    public @Nullable String getBlockCommentSuffix(int selectionEnd, @NotNull Document document, @NotNull Data data) {
        return data.suffix;
    }

    @Override
    public void uncommentBlockComment(int startOffset, int endOffset, Document document, Data data) {
        CharSequence text = document.getCharsSequence();
        if (endsWith(text, endOffset, data.suffix)) document.deleteString(endOffset - data.suffix.length(), endOffset);
        if (startsWith(text, startOffset, data.prefix)) {
            document.deleteString(startOffset, startOffset + data.prefix.length());
        }
    }

    @Override
    public @NotNull TextRange insertBlockComment(int startOffset, int endOffset, Document document, Data data) {
        document.insertString(endOffset, data.suffix);
        document.insertString(startOffset, data.prefix);
        return new TextRange(startOffset, endOffset + data.prefix.length() + data.suffix.length());
    }

    private static boolean startsWith(CharSequence text, int offset, String s) {
        return offset >= 0 && offset + s.length() <= text.length()
                && text.subSequence(offset, offset + s.length()).toString().equals(s);
    }

    private static boolean endsWith(CharSequence text, int end, String s) {
        return end - s.length() >= 0 && end <= text.length()
                && text.subSequence(end - s.length(), end).toString().equals(s);
    }
}
