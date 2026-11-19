package com.smartyplugin.php;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.smartyplugin.lang.SmartyLanguage;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.smartyplugin.lexer.SmartyTokenTypes.ARROW;
import static com.smartyplugin.lexer.SmartyTokenTypes.ATTR_NAME;
import static com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON;
import static com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER;

/**
 * <code>$user-&gt;</code> completes the public fields, methods and Yii getter properties of the PHP class;
 * <code>Html::</code> the public static methods and constants of a class from <code>{use class="..."}</code>.
 */
public final class SmartyPhpCompletionContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        if (position.getLanguage() != SmartyLanguage.INSTANCE) return;
        boolean name = SmartyPsiUtil.is(position, IDENTIFIER) || SmartyPsiUtil.is(position, ATTR_NAME);
        if (!name) return;
        PsiElement separator = SmartyPhpTypes.prevSignificant(position);
        if (separator == null) return;

        int caretInToken = parameters.getOffset() - position.getTextRange().getStartOffset();
        String prefix = position.getText().substring(0, Math.max(0, Math.min(caretInToken, position.getTextLength())));
        CompletionResultSet rs = result.withPrefixMatcher(prefix);
        Project project = position.getProject();

        if (com.smartyplugin.psi.SmartyPsiUtil.isAttributeNamePosition(position)) {
            List<PhpClass> widget = SmartyPhpWidgets.widgetClasses(position);
            if (!widget.isEmpty()) {
                SmartyPhpWidgets.completeAttributes(position, widget, project, rs);
                rs.stopHere();
            }
            return;
        }
        if (!SmartyPsiUtil.is(position, IDENTIFIER)) return;

        if (SmartyPsiUtil.is(separator, DOUBLE_COLON)) {
            PsiElement className = SmartyPhpClasses.classNameBefore(separator);
            if (className == null) return;
            completeStatic(SmartyPhpClasses.resolve(className), project, rs);
            rs.stopHere();
            return;
        }
        if (!SmartyPsiUtil.is(separator, ARROW)) return;
        PhpType owner = SmartyPhpTypes.typeBeforeArrow(separator);
        if (owner == null) return;
        Set<String> seen = new HashSet<>();
        for (PhpClass c : SmartyPhpTypes.classesOf(project, owner)) {
            for (Field f : c.getFields()) {
                if (f.isConstant() || !f.getModifier().isPublic() || f.getModifier().isStatic()) continue;
                if (seen.add(f.getName())) rs.addElement(property(f.getName(), f.getType().global(project), c));
            }
            for (Method m : c.getMethods()) {
                if (!m.getAccess().isPublic() || m.isStatic() || m.getName().startsWith("__")) continue;
                if (seen.add(m.getName() + "()")) {
                    boolean hasParams = m.getParameters().length > 0;
                    rs.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(m, m.getName())
                            .withIcon(AllIcons.Nodes.Method)
                            .withTailText(hasParams ? "(…)" : "()", true)
                            .withTypeText(SmartyPhpTypes.presentable(m.getType().global(project)), true)
                            .withInsertHandler(parentheses(hasParams)), -1));
                }
                // Yii: getFullName() is readable as ->fullName
                String n = m.getName();
                if (n.length() > 3 && n.startsWith("get") && Character.isUpperCase(n.charAt(3))
                        && requiredParameters(m) == 0) {
                    String prop = Character.toLowerCase(n.charAt(3)) + n.substring(4);
                    if (seen.add(prop)) rs.addElement(property(prop, m.getType().global(project), c));
                }
            }
        }
        rs.stopHere();
    }

    /** Html:: -> public static methods and public constants. */
    private static void completeStatic(java.util.List<PhpClass> classes, Project project, CompletionResultSet rs) {
        Set<String> seen = new HashSet<>();
        for (PhpClass c : classes) {
            for (Method m : c.getMethods()) {
                if (!m.isStatic() || !m.getAccess().isPublic() || m.getName().startsWith("__")) continue;
                if (!seen.add(m.getName() + "()")) continue;
                boolean hasParams = m.getParameters().length > 0;
                rs.addElement(LookupElementBuilder.create(m, m.getName())
                        .withIcon(AllIcons.Nodes.Method)
                        .withTailText(hasParams ? "(…)" : "()", true)
                        .withTypeText(SmartyPhpTypes.presentable(m.getType().global(project)), true)
                        .withInsertHandler(parentheses(hasParams)));
            }
            for (Field f : c.getFields()) {
                if (!f.isConstant() || !f.getModifier().isPublic() || !seen.add(f.getName())) continue;
                rs.addElement(LookupElementBuilder.create(f, f.getName())
                        .withIcon(AllIcons.Nodes.Constant)
                        .withTypeText(SmartyPhpTypes.presentable(f.getType().global(project)), true));
            }
        }
    }

    private static LookupElement property(String name, PhpType type, PhpClass owner) {
        return LookupElementBuilder.create(name)
                .withIcon(AllIcons.Nodes.Property)
                .withTypeText(SmartyPhpTypes.presentable(type), true)
                .withTailText(" " + owner.getName(), true);
    }

    private static int requiredParameters(Method m) {
        int n = 0;
        for (var p : m.getParameters()) if (!p.isOptional()) n++;
        return n;
    }

    private static InsertHandler<LookupElement> parentheses(boolean hasParams) {
        return (context, item) -> {
            Document doc = context.getDocument();
            int tail = context.getTailOffset();
            if (tail < doc.getTextLength() && doc.getCharsSequence().charAt(tail) == '(') return;
            doc.insertString(tail, "()");
            context.getEditor().getCaretModel().moveToOffset(tail + (hasParams ? 1 : 2));
        };
    }
}
