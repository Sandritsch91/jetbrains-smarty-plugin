package com.smartyplugin.php;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
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
    /** Tokens that end a value: an attribute name may follow them. */
    private static final TokenSet VALUE_END = TokenSet.create(TAG_NAME, STRING, NUMBER, VARIABLE, IDENTIFIER, CONSTANT,
            CONFIG_VAR, RBRACKET, RPAREN, RDELIM);

    private SmartyPhpWidgets() {
    }

    /** The innermost tag around the element that can carry attributes ({x ...} or the opening tag of a block). */
    static @Nullable ASTNode enclosingTag(@NotNull PsiElement element) {
        for (ASTNode n = element.getNode().getTreeParent(); n != null; n = n.getTreeParent()) {
            IElementType t = n.getElementType();
            if (t == TAG || t == OPEN_TAG) return n;
            if (TAGS.contains(t) || t == BLOCK_BODY) return null;
        }
        return null;
    }

    static @NotNull List<PhpClass> widgetClasses(@NotNull PsiElement inside) {
        ASTNode tag = enclosingTag(inside);
        ASTNode name = tag == null ? null : SmartyPsiUtil.tagNameNode(tag);
        return name == null ? List.of() : SmartyPhpClasses.resolveUsed(name.getPsi());
    }

    /**
     * Whether a word typed here starts a new attribute: after the tag name or a complete value, separated by
     * whitespace, and not inside brackets: "{Select2 name='x' th|" but not "{Select2 name=th|" or "[th|".
     */
    static boolean isAttributeNamePosition(@NotNull PsiElement position, @NotNull PsiElement prev) {
        ASTNode tag = enclosingTag(position);
        if (tag == null || !VALUE_END.contains(significantType(prev))) return false;
        if (prev.getTextRange().getEndOffset() == position.getTextRange().getStartOffset()) return false;
        int depth = 0;
        for (PsiElement leaf = PsiTreeUtil.prevLeaf(position); leaf != null
                && leaf.getTextRange().getStartOffset() >= tag.getStartOffset(); leaf = PsiTreeUtil.prevLeaf(leaf)) {
            if (SmartyPsiUtil.is(leaf, RBRACKET) || SmartyPsiUtil.is(leaf, RPAREN)) depth--;
            if (SmartyPsiUtil.is(leaf, LBRACKET) || SmartyPsiUtil.is(leaf, LPAREN)) depth++;
        }
        return depth <= 0;
    }

    /** A string literal's leaf is STRING; a nested tag ends with RDELIM. */
    private static IElementType significantType(PsiElement prev) {
        return prev.getNode().getElementType();
    }

    static void completeAttributes(@NotNull PsiElement position, @NotNull List<PhpClass> classes,
                                   @NotNull Project project, @NotNull CompletionResultSet rs) {
        Set<String> present = presentAttributes(position);
        Set<String> seen = new HashSet<>();
        for (PhpClass c : classes) {
            for (Field f : c.getFields()) {
                if (f.isConstant() || !f.getModifier().isPublic() || f.getModifier().isStatic()) continue;
                if (present.contains(f.getName()) || !seen.add(f.getName())) continue;
                rs.addElement(attribute(LookupElementBuilder.create(f, f.getName()), f.getName(),
                        SmartyPhpTypes.presentable(f.getType().global(project)), c));
            }
            for (Method m : c.getMethods()) {
                String n = m.getName();
                if (n.length() <= 3 || !n.startsWith("set") || !Character.isUpperCase(n.charAt(3))) continue;
                if (m.isStatic() || !m.getAccess().isPublic() || m.getParameters().length == 0) continue;
                String prop = Character.toLowerCase(n.charAt(3)) + n.substring(4);
                if (present.contains(prop) || !seen.add(prop)) continue;
                Parameter p = m.getParameters()[0];
                rs.addElement(attribute(LookupElementBuilder.create(m, prop), prop,
                        SmartyPhpTypes.presentable(p.getType().global(project)), c));
            }
        }
    }

    private static Set<String> presentAttributes(PsiElement position) {
        Set<String> names = new HashSet<>();
        ASTNode tag = enclosingTag(position);
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
