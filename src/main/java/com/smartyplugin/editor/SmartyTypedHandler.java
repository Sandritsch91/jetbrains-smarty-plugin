package com.smartyplugin.editor;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.smartyplugin.lang.SmartyFileViewProvider;
import com.smartyplugin.lang.SmartyLanguage;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.smartyplugin.lexer.SmartyTokenTypes.RDELIM;
import static com.smartyplugin.parser.SmartyElementTypes.BLOCK;
import static com.smartyplugin.parser.SmartyElementTypes.CLOSE_TAG;
import static com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG;

/**
 * <ul>
 *   <li>Typing <code>{/</code> completes the closing tag of the innermost open block.</li>
 *   <li>Typing the <code>}</code> of an opening block tag inserts the matching closing tag after the caret.</li>
 *   <li>Typing <code>$</code>, <code>|</code> or <code>-&gt;</code> inside a tag opens completion.</li>
 * </ul>
 */
public final class SmartyTypedHandler extends TypedHandlerDelegate {

    /**
     * Opens completion for variables after <code>$</code> and for modifiers after <code>|</code> inside a tag.
     * Decided after the character is inserted: <code>{</code> alone is plain text until <code>{$</code> makes it a tag.
     */
    /** The '>' just typed completes "->" (member access). */
    private static boolean isArrow(Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        return offset >= 2 && editor.getDocument().getCharsSequence().charAt(offset - 2) == '-';
    }

    private static Result scheduleAutoPopup(Project project, Editor editor, PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        PsiElement typed = SmartyPsiUtil.smartyElementAt(file, offset - 1);
        if (typed != null && SmartyPsiUtil.enclosingTag(typed.getNode()) != null) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        }
        return Result.CONTINUE;
    }

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (!(file.getViewProvider() instanceof SmartyFileViewProvider)) return Result.CONTINUE;
        if (c == '/') return completeCloseTag(project, editor, file);
        if (c == '}') return insertCloseTag(project, editor, file);
        if (c == '$' || c == '|') return scheduleAutoPopup(project, editor, file);
        if (c == '>' && isArrow(editor)) return scheduleAutoPopup(project, editor, file);
        return Result.CONTINUE;
    }

    private static Result completeCloseTag(Project project, Editor editor, PsiFile file) {
        Document doc = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        CharSequence text = doc.getCharsSequence();
        if (offset < 2 || text.charAt(offset - 2) != '{') return Result.CONTINUE;
        if (offset < text.length() && Character.isLetter(text.charAt(offset))) return Result.CONTINUE;

        // Decide on the text without the "{/" just typed: an unfinished tag would otherwise swallow
        // the rest of the file and make every block look unclosed.
        String withoutTyped = text.subSequence(0, offset - 2).toString() + text.subSequence(offset, text.length());
        String name = unclosedBlockNameAt(project, withoutTyped, offset - 2);
        if (name == null) return Result.CONTINUE;

        boolean hasBrace = offset < text.length() && text.charAt(offset) == '}';
        doc.insertString(offset, hasBrace ? name : name + "}");
        editor.getCaretModel().moveToOffset(offset + name.length() + 1);
        return Result.STOP;
    }

    public static @Nullable String unclosedBlockNameAt(Project project, String text, int offset) {
        PsiFile copy = PsiFileFactory.getInstance(project).createFileFromText("dummy.tpl", SmartyLanguage.INSTANCE, text);
        if (offset <= 0) return null;
        PsiElement element = SmartyPsiUtil.smartyElementAt(copy, Math.min(offset, text.length()) - 1);
        if (element == null) return null;
        // walk up to the innermost block the caret is in; a caret right after {/x} is outside of block x
        ASTNode node = element.getNode();
        while (node != null && node.getElementType() != BLOCK) {
            ASTNode parent = node.getTreeParent();
            if (node.getElementType() == CLOSE_TAG && SmartyPsiUtil.is(parent, BLOCK)) {
                node = parent.getTreeParent();
                continue;
            }
            node = parent;
        }
        if (node == null || SmartyPsiUtil.closeTag(node) != null) return null;
        return SmartyPsiUtil.blockName(node);
    }

    private static Result insertCloseTag(Project project, Editor editor, PsiFile file) {
        Document doc = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        PsiDocumentManager.getInstance(project).commitDocument(doc);
        PsiElement element = SmartyPsiUtil.smartyElementAt(file, offset - 1);
        if (element == null || !SmartyPsiUtil.is(element, RDELIM)) return Result.CONTINUE;
        PsiElement tag = element.getParent();
        if (!SmartyPsiUtil.is(tag, OPEN_TAG) || tag.getTextRange().getEndOffset() != offset) return Result.CONTINUE;
        PsiElement block = tag.getParent();
        if (!SmartyPsiUtil.is(block, BLOCK) || SmartyPsiUtil.closeTag(block.getNode()) != null) return Result.CONTINUE;
        String name = SmartyPsiUtil.blockName(block.getNode());
        if (name == null) return Result.CONTINUE;
        doc.insertString(offset, "{/" + name + "}");
        return Result.STOP;
    }
}
