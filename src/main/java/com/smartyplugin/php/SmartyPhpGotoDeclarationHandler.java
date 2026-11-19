package com.smartyplugin.php;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.smartyplugin.lang.SmartyFileViewProvider;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;

/** Ctrl+Click on <code>name</code> in <code>$user-&gt;name</code> goes to the PHP field, method or Yii getter. */
public final class SmartyPhpGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement source, int offset, Editor editor) {
        if (source == null) return null;
        PsiFile file = source.getContainingFile();
        if (file == null || !(file.getViewProvider() instanceof SmartyFileViewProvider)) return null;
        PsiElement token = SmartyPsiUtil.smartyElementAt(file, offset);
        if (token == null || !SmartyPsiUtil.is(token, IDENTIFIER)) return null;
        PsiElement arrow = SmartyPhpTypes.prevSignificant(token);
        if (arrow == null || !SmartyPsiUtil.is(arrow, ARROW)) return null;
        PhpType owner = SmartyPhpTypes.typeBeforeArrow(arrow);
        if (owner == null) return null;
        PsiElement next = SmartyPhpTypes.nextSignificant(token);
        boolean call = next != null && SmartyPsiUtil.is(next, LPAREN);
        List<PhpNamedElement> members = SmartyPhpTypes.members(token.getProject(), owner, token.getText(), call);
        return members.isEmpty() ? null : members.toArray(PsiElement.EMPTY_ARRAY);
    }
}
