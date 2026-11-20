package com.smartyplugin.php;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;

/**
 * The configuration array of a Yii widget, whose keys are the widget's properties:
 * <ul>
 *   <li><code>{Select2::widget(['name' =&gt; 'x', ...])}</code>, <code>{ActiveForm::begin([...])}</code></li>
 *   <li><code>{$form-&gt;field($model, 'icon')-&gt;widget(Select2::class, [...])}</code>
 *       (also <code>Select2::className()</code>)</li>
 * </ul>
 * Only the keys at the top level of the array count; nested arrays ('options' =&gt; [...]) configure something else.
 */
final class SmartyPhpWidgetConfig {
    private static final Set<String> STATIC_FACTORIES = Set.of("widget", "begin");

    private SmartyPhpWidgetConfig() {
    }

    /** The widget classes whose config array has {@code key} (a STRING token) as one of its keys, or empty. */
    static @NotNull List<PhpClass> widgetForKey(@NotNull PsiElement key) {
        PsiElement before = SmartyPhpTypes.prevSignificant(key);
        if (before == null || !(SmartyPsiUtil.is(before, COMMA) || SmartyPsiUtil.is(before, LBRACKET))) return List.of();
        PsiElement open = SmartyPsiUtil.is(before, LBRACKET) ? before : enclosingOpener(before);
        if (open == null || !SmartyPsiUtil.is(open, LBRACKET)) return List.of();
        return widgetForArray(open);
    }

    /** {@code open} is the '[' of a widget configuration: the widget classes, else empty. */
    static @NotNull List<PhpClass> widgetForArray(@NotNull PsiElement open) {
        PsiElement p = SmartyPhpTypes.prevSignificant(open);
        if (p == null) return List.of();
        if (SmartyPsiUtil.is(p, LPAREN)) {
            // Select2::widget([ / ActiveForm::begin([
            PsiElement method = SmartyPhpTypes.prevSignificant(p);
            if (method == null || !SmartyPsiUtil.is(method, IDENTIFIER) || !STATIC_FACTORIES.contains(method.getText())) {
                return List.of();
            }
            PsiElement colons = SmartyPhpTypes.prevSignificant(method);
            if (colons == null || !SmartyPsiUtil.is(colons, DOUBLE_COLON)) return List.of();
            PsiElement className = SmartyPhpClasses.classNameBefore(colons);
            return className == null ? List.of() : SmartyPhpClasses.resolve(className);
        }
        if (SmartyPsiUtil.is(p, COMMA)) {
            // ->widget(Select2::class, [ / ->widget(Select2::className(), [
            PsiElement last = SmartyPhpTypes.prevSignificant(p);
            if (last != null && SmartyPsiUtil.is(last, RPAREN)) {
                PsiElement lparen = SmartyPhpTypes.prevSignificant(last);
                last = lparen != null && SmartyPsiUtil.is(lparen, LPAREN) ? SmartyPhpTypes.prevSignificant(lparen) : null;
                if (last == null || !"className".equals(last.getText())) return List.of();
            } else if (last == null || !"class".equals(last.getText())) {
                return List.of();
            }
            PsiElement colons = SmartyPhpTypes.prevSignificant(last);
            if (colons == null || !SmartyPsiUtil.is(colons, DOUBLE_COLON)) return List.of();
            PsiElement className = SmartyPhpClasses.classNameBefore(colons);
            if (className == null) return List.of();
            PsiElement paren = SmartyPhpTypes.prevSignificant(className);
            PsiElement method = paren == null ? null : SmartyPhpTypes.prevSignificant(paren);
            PsiElement arrow = method == null ? null : SmartyPhpTypes.prevSignificant(method);
            if (!SmartyPsiUtil.is(paren, LPAREN) || method == null || !"widget".equals(method.getText())
                    || !SmartyPsiUtil.is(arrow, ARROW)) {
                return List.of();
            }
            return SmartyPhpClasses.resolve(className);
        }
        return List.of();
    }

    /** The '[' or '(' that the element is directly inside of (within the tag), or null. */
    private static @Nullable PsiElement enclosingOpener(@NotNull PsiElement from) {
        int depth = 0;
        for (PsiElement leaf = PsiTreeUtil.prevLeaf(from); leaf != null; leaf = PsiTreeUtil.prevLeaf(leaf)) {
            if (SmartyPsiUtil.is(leaf, LDELIM)) return null;
            if (SmartyPsiUtil.is(leaf, RBRACKET) || SmartyPsiUtil.is(leaf, RPAREN)) depth++;
            else if (SmartyPsiUtil.is(leaf, LBRACKET) || SmartyPsiUtil.is(leaf, LPAREN)) {
                if (depth == 0) return leaf;
                depth--;
            }
        }
        return null;
    }

    /** Keys already in the array that {@code key} belongs to. */
    private static Set<String> presentKeys(PsiElement key) {
        Set<String> keys = new HashSet<>();
        PsiElement before = SmartyPhpTypes.prevSignificant(key);
        PsiElement open = before != null && SmartyPsiUtil.is(before, LBRACKET) ? before
                : before == null ? null : enclosingOpener(before);
        if (open == null) return keys;
        int depth = 0;
        for (PsiElement leaf = SmartyPhpTypes.nextSignificant(open); leaf != null; leaf = SmartyPhpTypes.nextSignificant(leaf)) {
            if (SmartyPsiUtil.is(leaf, RDELIM)) break;
            if (SmartyPsiUtil.is(leaf, LBRACKET) || SmartyPsiUtil.is(leaf, LPAREN)) depth++;
            else if (SmartyPsiUtil.is(leaf, RBRACKET) || SmartyPsiUtil.is(leaf, RPAREN)) {
                if (depth-- == 0) break;
            } else if (depth == 0 && leaf != key && SmartyPsiUtil.is(leaf, STRING)) {
                PsiElement next = SmartyPhpTypes.nextSignificant(leaf);
                if (next != null && SmartyPsiUtil.is(next, DOUBLE_ARROW)) keys.add(SmartyPsiUtil.unquote(leaf.getText()));
            }
        }
        return keys;
    }

    /** 'pluginNa| -> 'pluginOptions' => | */
    static void completeKeys(@NotNull PsiElement key, @NotNull List<PhpClass> classes, @NotNull Project project,
                             @NotNull CompletionResultSet rs) {
        Set<String> present = presentKeys(key);
        char quote = key.getText().isEmpty() ? '\'' : key.getText().charAt(0);
        for (SmartyPhpWidgets.Property p : SmartyPhpWidgets.properties(classes, project)) {
            if (present.contains(p.name())) continue;
            rs.addElement(LookupElementBuilder.create(p.element(), p.name())
                    .withIcon(AllIcons.Nodes.Property)
                    .withTypeText(p.type(), true)
                    .withTailText(" " + p.owner().getName(), true)
                    .withInsertHandler((context, item) -> {
                        Document doc = context.getDocument();
                        int tail = context.getTailOffset();
                        CharSequence text = doc.getCharsSequence();
                        if (tail < text.length() && text.charAt(tail) == quote) {
                            tail++;
                        } else {
                            doc.insertString(tail, String.valueOf(quote));
                            tail++;
                            text = doc.getCharsSequence();
                        }
                        int next = tail;
                        while (next < text.length() && (text.charAt(next) == ' ' || text.charAt(next) == '\t')) next++;
                        if (!(next + 1 < text.length() && text.charAt(next) == '=' && text.charAt(next + 1) == '>')) {
                            doc.insertString(tail, " => ");
                            tail += 4;
                        }
                        context.getEditor().getCaretModel().moveToOffset(tail);
                    }));
        }
    }
}
