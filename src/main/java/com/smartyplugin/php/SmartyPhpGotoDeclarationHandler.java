package com.smartyplugin.php;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.smartyplugin.lang.SmartyFileViewProvider;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;
import static com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG;
import static com.smartyplugin.parser.SmartyElementTypes.TAG;

/**
 * Ctrl+Click into PHP:
 * <ul>
 *   <li><code>name</code> in <code>$user-&gt;name</code>: the field, method or Yii getter</li>
 *   <li><code>Html</code> / <code>a</code> in <code>{Html::a(...)}</code>: the class / the static method or constant</li>
 *   <li>the string in <code>{use class="yii\bootstrap5\Html"}</code>: the class</li>
 * </ul>
 */
public final class SmartyPhpGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement source, int offset, Editor editor) {
        if (source == null) return null;
        PsiFile file = source.getContainingFile();
        if (file == null || !(file.getViewProvider() instanceof SmartyFileViewProvider)) return null;
        PsiElement token = SmartyPsiUtil.smartyElementAt(file, offset);
        if (token == null) return null;
        List<? extends PsiElement> targets = targets(token);
        return targets.isEmpty() ? null : targets.toArray(PsiElement.EMPTY_ARRAY);
    }

    private static List<? extends PsiElement> targets(PsiElement token) {
        if (SmartyPsiUtil.is(token, STRING)) {
            PsiElement arrow = SmartyPhpTypes.nextSignificant(token);
            if (arrow != null && SmartyPsiUtil.is(arrow, DOUBLE_ARROW)) {
                // 'pluginOptions' => ... in Select2::widget([...])
                List<PhpClass> widget = SmartyPhpWidgetConfig.widgetForKey(token);
                if (!widget.isEmpty()) {
                    return SmartyPhpWidgets.propertyTargets(widget, SmartyPsiUtil.unquote(token.getText()));
                }
            }
            return useClassTargets(token);
        }

        PsiElement next = SmartyPhpTypes.nextSignificant(token);
        if ((SmartyPsiUtil.is(token, TAG_NAME) || SmartyPsiUtil.is(token, IDENTIFIER))
                && next != null && SmartyPsiUtil.is(next, DOUBLE_COLON)) {
            return SmartyPhpClasses.resolve(token); // Html in Html::a()
        }
        if (SmartyPsiUtil.is(token, TAG_NAME)) {
            return SmartyPhpClasses.resolveUsed(token); // {Select2 ...}, {ActiveForm}...{/ActiveForm}
        }
        if (SmartyPsiUtil.is(token, ATTR_NAME)) return widgetPropertyTargets(token);
        if (!SmartyPsiUtil.is(token, IDENTIFIER)) return List.of();
        PsiElement separator = SmartyPhpTypes.prevSignificant(token);
        if (separator == null) return List.of();
        boolean call = next != null && SmartyPsiUtil.is(next, LPAREN);

        if (SmartyPsiUtil.is(separator, DOUBLE_COLON)) {
            PsiElement className = SmartyPhpClasses.classNameBefore(separator);
            if (className == null) return List.of();
            List<PhpNamedElement> result = new ArrayList<>();
            for (PhpClass c : SmartyPhpClasses.resolve(className)) {
                Field constant = call ? null : c.findFieldByName(token.getText(), true);
                if (constant != null) {
                    result.add(constant);
                    continue;
                }
                Method method = c.findMethodByName(token.getText());
                if (method != null) result.add(method);
            }
            return result;
        }
        if (!SmartyPsiUtil.is(separator, ARROW)) return List.of();
        PhpType owner = SmartyPhpTypes.typeBeforeArrow(separator);
        return owner == null ? List.of() : SmartyPhpTypes.members(token.getProject(), owner, token.getText(), call);
    }

    /** name= in {Select2 name='icon'}: the widget property, or its Yii setter (setName()). */
    private static List<PhpNamedElement> widgetPropertyTargets(PsiElement attrName) {
        PsiElement tag = attrName;
        while (tag != null && !SmartyPsiUtil.is(tag, TAG) && !SmartyPsiUtil.is(tag, OPEN_TAG)) tag = tag.getParent();
        if (tag == null) return List.of();
        com.intellij.lang.ASTNode nameNode = SmartyPsiUtil.tagNameNode(tag.getNode());
        if (nameNode == null) return List.of();
        List<PhpNamedElement> result = new ArrayList<>();
        for (PhpClass c : SmartyPhpClasses.resolveUsed(nameNode.getPsi())) {
            Field field = c.findFieldByName(attrName.getText(), false);
            if (field != null) {
                result.add(field);
                continue;
            }
            Method setter = c.findMethodByName("set" + SmartyPhpTypes.capitalize(attrName.getText()));
            if (setter != null) result.add(setter);
        }
        return result;
    }

    /** {use class="yii\bootstrap5\Html"} */
    private static List<PhpClass> useClassTargets(PsiElement string) {
        PsiElement tag = string;
        while (tag != null && !SmartyPsiUtil.is(tag, TAG)) tag = tag.getParent();
        if (tag == null) return List.of();
        String fqn = SmartyPhpClasses.useClassValue(tag.getNode());
        if (fqn == null || !SmartyPsiUtil.unquote(string.getText()).replace("\\\\", "\\")
                .equals(fqn.substring(1))) {
            return List.of();
        }
        return new ArrayList<>(SmartyPhpClasses.byFqn(string, fqn));
    }
}
