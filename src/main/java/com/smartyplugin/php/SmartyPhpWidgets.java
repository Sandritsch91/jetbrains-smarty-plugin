package com.smartyplugin.php;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;
import static com.smartyplugin.parser.SmartyElementTypes.*;

/**
 * Yii widgets used as tags (<code>{Select2 name='x' ...}</code>, <code>{ActiveForm id='f'}...{/ActiveForm}</code>):
 * their attributes are the widget's public properties and setters.
 */
final class SmartyPhpWidgets {
    private SmartyPhpWidgets() {
    }

    static @NotNull List<PhpClass> widgetClasses(@NotNull PsiElement inside) {
        ASTNode tag = SmartyPsiUtil.enclosingAttributeTag(inside);
        ASTNode name = tag == null ? null : SmartyPsiUtil.tagNameNode(tag);
        return name == null ? List.of() : SmartyPhpClasses.resolveUsed(name.getPsi());
    }

    /** A configurable widget property: a public field, or a Yii setter (setTheme() configures "theme"). */
    record Property(@NotNull String name, @Nullable String type, @NotNull PhpClass owner,
                    @NotNull com.jetbrains.php.lang.psi.elements.PhpNamedElement element) {
    }

    static @NotNull List<Property> properties(@NotNull List<PhpClass> classes, @NotNull Project project) {
        List<Property> result = new java.util.ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PhpClass c : classes) {
            for (Field f : c.getFields()) {
                if (f.isConstant() || !f.getModifier().isPublic() || f.getModifier().isStatic()) continue;
                if (!seen.add(f.getName())) continue;
                result.add(new Property(f.getName(), SmartyPhpTypes.presentable(f.getType().global(project)), c, f));
            }
            for (Method m : c.getMethods()) {
                String n = m.getName();
                if (n.length() <= 3 || !n.startsWith("set") || !Character.isUpperCase(n.charAt(3))) continue;
                if (m.isStatic() || !m.getAccess().isPublic() || m.getParameters().length == 0) continue;
                String prop = Character.toLowerCase(n.charAt(3)) + n.substring(4);
                if (!seen.add(prop)) continue;
                Parameter p = m.getParameters()[0];
                result.add(new Property(prop, SmartyPhpTypes.presentable(p.getType().global(project)), c, m));
            }
        }
        return result;
    }

    /** The property a name configures, for Ctrl+Click: the field, else the setter. */
    static @NotNull List<com.jetbrains.php.lang.psi.elements.PhpNamedElement> propertyTargets(
            @NotNull List<PhpClass> classes, @NotNull String name) {
        List<com.jetbrains.php.lang.psi.elements.PhpNamedElement> result = new java.util.ArrayList<>();
        for (PhpClass c : classes) {
            Field field = c.findFieldByName(name, false);
            if (field != null) {
                result.add(field);
                continue;
            }
            Method setter = c.findMethodByName("set" + SmartyPhpTypes.capitalize(name));
            if (setter != null) result.add(setter);
        }
        return result;
    }

    static void completeAttributes(@NotNull PsiElement position, @NotNull List<PhpClass> classes,
                                   @NotNull Project project, @NotNull CompletionResultSet rs) {
        Set<String> present = presentAttributes(position);
        for (Property p : properties(classes, project)) {
            if (present.contains(p.name())) continue;
            rs.addElement(attribute(LookupElementBuilder.create(p.element(), p.name()), p.name(), p.type(), p.owner()));
        }
    }

    private static Set<String> presentAttributes(PsiElement position) {
        Set<String> names = new HashSet<>();
        ASTNode tag = SmartyPsiUtil.enclosingAttributeTag(position);
        if (tag == null) return names;
        for (ASTNode c = tag.getFirstChildNode(); c != null; c = c.getTreeNext()) {
            if (c.getElementType() == ATTRIBUTE) {
                String n = SmartyPsiUtil.attributeName(c);
                if (n != null) names.add(n);
            }
        }
        return names;
    }

    private static LookupElementBuilder attribute(LookupElementBuilder builder, String name, @Nullable String type,
                                                  PhpClass owner) {
        return builder.withPresentableText(name)
                .withIcon(AllIcons.Nodes.Property)
                .withTypeText(type, true)
                .withTailText(" " + owner.getName(), true)
                .withInsertHandler((context, item) -> {
                    Document doc = context.getDocument();
                    int tail = context.getTailOffset();
                    if (tail >= doc.getTextLength() || doc.getCharsSequence().charAt(tail) != '=') {
                        doc.insertString(tail, "=");
                    }
                    context.getEditor().getCaretModel().moveToOffset(tail + 1);
                });
    }
}
