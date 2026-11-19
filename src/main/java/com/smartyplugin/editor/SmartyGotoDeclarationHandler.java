package com.smartyplugin.editor;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.smartyplugin.lang.SmartyFileViewProvider;
import com.smartyplugin.lang.SmartyVariableProvider;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE;

/** Ctrl+Click on <code>$name</code> goes to where the template receives it (e.g. the PHP render call). */
public final class SmartyGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement source, int offset, Editor editor) {
        if (source == null) return null;
        PsiFile file = source.getContainingFile();
        if (file == null || !(file.getViewProvider() instanceof SmartyFileViewProvider)) return null;
        PsiElement token = SmartyPsiUtil.smartyElementAt(file, offset);
        if (token == null || !SmartyPsiUtil.is(token, VARIABLE)) return null;
        String name = token.getText().substring(1);
        List<PsiElement> targets = new ArrayList<>();
        for (SmartyVariableProvider.Variable v : SmartyVariableProvider.all(token.getContainingFile())) {
            if (v.name().equals(name)) targets.add(v.target());
        }
        return targets.isEmpty() ? null : targets.toArray(PsiElement.EMPTY_ARRAY);
    }
}
