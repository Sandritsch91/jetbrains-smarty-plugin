package com.smartyplugin.php;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.smartyplugin.lang.SmartyLanguage;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;
import static com.smartyplugin.parser.SmartyElementTypes.*;

/**
 * PHP classes named in a template: <code>{use class="yii\bootstrap5\Html"}</code> (Yii2 Smarty) makes
 * <code>{Html::a(...)}</code> refer to that class; other names are looked up as global classes (<code>Yii</code>).
 */
final class SmartyPhpClasses {
    private SmartyPhpClasses() {
    }

    /** Class name token right before "::": a tag name at the start of a tag, an identifier elsewhere. */
    static @Nullable PsiElement classNameBefore(@NotNull PsiElement doubleColon) {
        PsiElement prev = SmartyPhpTypes.prevSignificant(doubleColon);
        return prev != null && (SmartyPsiUtil.is(prev, TAG_NAME) || SmartyPsiUtil.is(prev, IDENTIFIER)) ? prev : null;
    }

    /** Class before "::": from {use class="..."}, else a global class (Yii). */
    static @NotNull List<PhpClass> resolve(@NotNull PsiElement nameToken) {
        List<PhpClass> result = resolveUsed(nameToken);
        if (result.isEmpty()) {
            result.addAll(PhpIndex.getInstance(nameToken.getProject()).getAnyByFQN("\\" + nameToken.getText()));
        }
        return result;
    }

    /**
     * Class a widget tag stands for: <code>{Select2 ...}</code> or <code>{ActiveForm}...{/ActiveForm}</code> after
     * <code>{use class="..." type="function|block"}</code>. Only imported classes: {form} must not become \Form.
     */
    static @NotNull List<PhpClass> resolveUsed(@NotNull PsiElement nameToken) {
        String name = nameToken.getText();
        PhpIndex index = PhpIndex.getInstance(nameToken.getProject());
        List<PhpClass> result = new ArrayList<>();
        for (String fqn : usedClasses(nameToken.getContainingFile())) {
            if (shortName(fqn).equals(name)) result.addAll(index.getAnyByFQN(fqn));
        }
        return result;
    }

    /** FQNs from {use class="..."}, with a leading backslash. */
    static @NotNull List<String> usedClasses(@NotNull PsiFile file) {
        PsiFile smarty = file.getViewProvider().getPsi(SmartyLanguage.INSTANCE);
        List<String> result = new ArrayList<>();
        if (smarty == null) return result;
        for (PsiElement tag : PsiTreeUtil.collectElements(smarty, e -> SmartyPsiUtil.is(e, TAG))) {
            String fqn = useClassValue(tag.getNode());
            if (fqn != null) result.add(fqn);
        }
        return result;
    }

    /** The class of a {use class="..."} tag, or null for other tags. */
    static @Nullable String useClassValue(@NotNull ASTNode tag) {
        if (!"use".equals(SmartyPsiUtil.tagName(tag))) return null;
        for (ASTNode c = tag.getFirstChildNode(); c != null; c = c.getTreeNext()) {
            if (c.getElementType() == ATTRIBUTE && "class".equals(SmartyPsiUtil.attributeName(c))) {
                String value = SmartyPsiUtil.attributeValue(c);
                if (value == null || value.isBlank()) return null;
                value = value.replace("\\\\", "\\");
                return value.startsWith("\\") ? value : "\\" + value;
            }
        }
        return null;
    }

    static @NotNull Collection<PhpClass> byFqn(@NotNull PsiElement context, @NotNull String fqn) {
        return PhpIndex.getInstance(context.getProject()).getAnyByFQN(fqn);
    }

    private static String shortName(String fqn) {
        int slash = fqn.lastIndexOf('\\');
        return slash >= 0 ? fqn.substring(slash + 1) : fqn;
    }
}
