package com.smartyplugin.editor;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.smartyplugin.lang.SmartyFileViewProvider;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;

import static com.smartyplugin.lexer.SmartyTokenTypes.RDELIM;
import static com.smartyplugin.parser.SmartyElementTypes.MIDDLE_TAG;
import static com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG;

/** Enter between <code>{if $x}</code> and <code>{/if}</code> puts the closing tag on its own line. */
public final class SmartyEnterHandler extends EnterHandlerDelegateAdapter {
    @Override
    public Result preprocessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull Ref<Integer> caretOffset,
                                  @NotNull Ref<Integer> caretAdvance, @NotNull DataContext dataContext,
                                  EditorActionHandler originalHandler) {
        if (!(file.getViewProvider() instanceof SmartyFileViewProvider)) return Result.Continue;
        Document doc = editor.getDocument();
        CharSequence text = doc.getCharsSequence();
        int offset = caretOffset.get();
        if (offset <= 0 || offset + 1 >= text.length()) return Result.Continue;
        if (text.charAt(offset - 1) != '}' || text.charAt(offset) != '{' || text.charAt(offset + 1) != '/') {
            return Result.Continue;
        }
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(doc);
        PsiElement brace = SmartyPsiUtil.smartyElementAt(file, offset - 1);
        if (brace == null || !SmartyPsiUtil.is(brace, RDELIM)) return Result.Continue;
        PsiElement tag = brace.getParent();
        if (!SmartyPsiUtil.is(tag, OPEN_TAG) && !SmartyPsiUtil.is(tag, MIDDLE_TAG)) return Result.Continue;

        int lineStart = doc.getLineStartOffset(doc.getLineNumber(offset));
        int i = lineStart;
        while (i < offset && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        String indent = text.subSequence(lineStart, i).toString();
        String unit = indentUnit(file);
        // Built here rather than by the formatter: an empty block body has no block to ask for child indent.
        doc.insertString(offset, "\n" + indent + unit + "\n" + indent);
        editor.getCaretModel().moveToOffset(offset + 1 + indent.length() + unit.length());
        return EnterHandlerDelegate.Result.Stop;
    }

    private static String indentUnit(PsiFile file) {
        CommonCodeStyleSettings.IndentOptions options = CodeStyle.getSettings(file).getIndentOptions(HtmlFileType.INSTANCE);
        if (options.USE_TAB_CHARACTER) return "\t";
        return " ".repeat(Math.max(0, options.INDENT_SIZE));
    }
}
