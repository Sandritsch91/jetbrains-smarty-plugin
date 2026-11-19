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

import com.intellij.psi.util.PsiTreeUtil;

import static com.smartyplugin.lexer.SmartyTokenTypes.KEYWORD;
import static com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE;
import static com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE;
import static com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG;

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
        PsiElement local = templateDefinition(token, name);
        if (local != null) return new PsiElement[]{local};
        List<PsiElement> targets = new ArrayList<>();
        for (SmartyVariableProvider.Variable v : SmartyVariableProvider.all(token.getContainingFile())) {
            if (v.name().equals(name)) targets.add(v.target());
        }
        return targets.isEmpty() ? null : targets.toArray(PsiElement.EMPTY_ARRAY);
    }

    /**
     * The closest definition before the usage in the template itself: assign="form" of a block widget,
     * {assign var="x"}, {foreach from=$a item="x"}; for the shorthand {foreach $a as $x} the $x of the tag.
     */
    private static @Nullable PsiElement templateDefinition(PsiElement usage, String name) {
        PsiElement best = null;
        int usageStart = usage.getTextRange().getStartOffset();
        for (PsiElement e : PsiTreeUtil.collectElements(usage.getContainingFile(), e ->
                SmartyPsiUtil.is(e, ATTRIBUTE) || SmartyPsiUtil.is(e, OPEN_TAG))) {
            if (e.getTextRange().getStartOffset() >= usageStart) continue;
            PsiElement candidate = SmartyPsiUtil.is(e, ATTRIBUTE) ? attributeDefinition(e, name) : foreachItem(e, name);
            if (candidate != null && candidate != usage
                    && (best == null || candidate.getTextRange().getStartOffset() > best.getTextRange().getStartOffset())) {
                best = candidate;
            }
        }
        return best;
    }

    private static @Nullable PsiElement attributeDefinition(PsiElement attribute, String name) {
        String attr = SmartyPsiUtil.attributeName(attribute.getNode());
        if (attr == null || !DEFINING_ATTRIBUTES.contains(attr)) return null;
        if (!name.equals(SmartyPsiUtil.attributeValue(attribute.getNode()))) return null;
        PsiElement value = attribute.getLastChild();
        while (value != null && SmartyPsiUtil.is(value, com.intellij.psi.TokenType.WHITE_SPACE)) value = value.getPrevSibling();
        return value;
    }

    /** $x in {foreach $items as $x} / {foreach $items as $k => $x} */
    private static @Nullable PsiElement foreachItem(PsiElement openTag, String name) {
        if (!"foreach".equals(SmartyPsiUtil.tagName(openTag.getNode()))) return null;
        boolean afterAs = false;
        for (PsiElement leaf = PsiTreeUtil.firstChild(openTag); leaf != null && openTag.getTextRange().contains(
                leaf.getTextRange()); leaf = PsiTreeUtil.nextLeaf(leaf)) {
            if (SmartyPsiUtil.is(leaf, KEYWORD) && "as".equals(leaf.getText())) afterAs = true;
            else if (afterAs && SmartyPsiUtil.is(leaf, VARIABLE) && leaf.getText().equals("$" + name)) return leaf;
        }
        return null;
    }

    private static final java.util.Set<String> DEFINING_ATTRIBUTES = java.util.Set.of("assign", "var", "item", "key");
}
