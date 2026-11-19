package com.smartyplugin.php;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.TokenType;
import com.smartyplugin.lang.SmartyLanguage;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;
import static com.smartyplugin.parser.SmartyElementTypes.*;

/** PHP types of Smarty expressions: <code>$user</code>, <code>$item</code> of a foreach, <code>$user-&gt;profile-&gt;name</code>. */
final class SmartyPhpTypes {
    private SmartyPhpTypes() {
    }

    // ------------------------------------------------------------------ expressions

    /** Type of the expression that ends right before {@code arrow} ("->"), or null. */
    static @Nullable PhpType typeBeforeArrow(@NotNull PsiElement arrow) {
        PsiElement prev = prevSignificant(arrow);
        return prev == null ? null : typeEndingAt(prev);
    }

    /** Type of the Smarty expression whose last token is {@code last}: $a, $a->b, $a->b(). */
    static @Nullable PhpType typeEndingAt(@NotNull PsiElement last) {
        if (SmartyPsiUtil.is(last, VARIABLE)) return typeOfVariable(last);
        PsiElement name = last;
        boolean call = false;
        if (SmartyPsiUtil.is(last, RPAREN)) {
            PsiElement open = matchingOpenParen(last);
            name = open == null ? null : prevSignificant(open);
            call = true;
        }
        if (name == null || !SmartyPsiUtil.is(name, IDENTIFIER)) return null;
        PsiElement ownerArrow = prevSignificant(name);
        if (ownerArrow != null && SmartyPsiUtil.is(ownerArrow, DOUBLE_COLON)) {
            // Html::method()
            PsiElement className = SmartyPhpClasses.classNameBefore(ownerArrow);
            if (className == null) return null;
            PhpType owner = new PhpType();
            for (PhpClass c : SmartyPhpClasses.resolve(className)) owner.add(c.getFQN());
            return memberType(name.getProject(), owner, name.getText(), call);
        }
        if (ownerArrow == null || !SmartyPsiUtil.is(ownerArrow, ARROW)) return null;
        PhpType owner = typeBeforeArrow(ownerArrow);
        return owner == null ? null : memberType(name.getProject(), owner, name.getText(), call);
    }

    /** Type of a $variable token: passed in from PHP or another template, or the item of an enclosing foreach. */
    static @Nullable PhpType typeOfVariable(@NotNull PsiElement variable) {
        String name = variable.getText().substring(1);
        PhpType loopType = foreachItemType(variable, name);
        if (loopType != null) return loopType;
        PhpType widgetType = blockWidgetType(variable, name);
        if (widgetType != null) return widgetType;
        return passedType(variable.getContainingFile(), name);
    }

    /** {ActiveForm assign="form"}...{$form->field(...)}...{/ActiveForm}: Yii assigns the widget instance. */
    private static @Nullable PhpType blockWidgetType(PsiElement variable, String name) {
        for (ASTNode block = variable.getNode().getTreeParent(); block != null; block = block.getTreeParent()) {
            if (block.getElementType() != BLOCK) continue;
            ASTNode open = SmartyPsiUtil.openTag(block);
            if (open == null || !name.equals(attribute(open, "assign"))) continue;
            ASTNode tagName = SmartyPsiUtil.tagNameNode(open);
            if (tagName == null) continue;
            PhpType type = new PhpType();
            for (PhpClass c : SmartyPhpClasses.resolveUsed(tagName.getPsi())) type.add(c.getFQN());
            if (!type.getTypes().isEmpty()) return type;
        }
        return null;
    }

    private static @Nullable String attribute(ASTNode tag, String attributeName) {
        for (ASTNode c = tag.getFirstChildNode(); c != null; c = c.getTreeNext()) {
            if (c.getElementType() == ATTRIBUTE && attributeName.equals(SmartyPsiUtil.attributeName(c))) {
                return SmartyPsiUtil.attributeValue(c);
            }
        }
        return null;
    }

    static @Nullable PhpType passedType(@NotNull PsiFile template, @NotNull String name) {
        PhpType result = null;
        for (SmartyPhpRenderCalls.Passed p : SmartyPhpRenderCalls.passedTo(template)) {
            if (!p.name().equals(name)) continue;
            PhpType type = typeOf(p);
            if (type != null) result = result == null ? type : new PhpType().add(result).add(type);
        }
        return result;
    }

    /** PHP value: its PHP type. Value in a template ({$this->render('_form', ['model' => $model])}): its type there. */
    static @Nullable PhpType typeOf(@NotNull SmartyPhpRenderCalls.Passed passed) {
        PsiElement value = passed.value();
        if (value instanceof PhpTypedElement typed) return typed.getType().global(value.getProject());
        if (value == null || value.getLanguage() != SmartyLanguage.INSTANCE) return null;
        // templates can render each other in a cycle
        return RecursionManager.doPreventingRecursion(value, false, () -> typeEndingAt(value));
    }

    /** {foreach $items as $item}, {foreach $items as $key => $item}, {foreach from=$items item=item} */
    private static @Nullable PhpType foreachItemType(PsiElement variable, String name) {
        ASTNode node = variable.getNode();
        for (ASTNode block = node.getTreeParent(); block != null; block = block.getTreeParent()) {
            if (block.getElementType() != BLOCK || !"foreach".equals(SmartyPsiUtil.blockName(block))) continue;
            ASTNode open = SmartyPsiUtil.openTag(block);
            if (open == null) continue;
            PsiElement source = foreachSource(open, name);
            if (source != null && source != variable) {
                PhpType sourceType = typeOfVariable(source);
                if (sourceType != null) return elementType(sourceType);
            }
        }
        return null;
    }

    /** The $items token of a foreach whose item variable is {@code name}. */
    private static @Nullable PsiElement foreachSource(ASTNode openTag, String name) {
        List<ASTNode> tokens = new ArrayList<>();
        collectLeaves(openTag, tokens);
        // shorthand: $items as [$key =>] $item
        for (int i = 0; i < tokens.size(); i++) {
            ASTNode t = tokens.get(i);
            if (t.getElementType() == KEYWORD && "as".equals(t.getText()) && i > 0) {
                ASTNode last = tokens.get(tokens.size() - 1).getElementType() == RDELIM
                        ? tokens.get(tokens.size() - 2) : tokens.get(tokens.size() - 1);
                ASTNode source = tokens.get(i - 1);
                if (last.getElementType() == VARIABLE && last.getText().equals("$" + name)
                        && source.getElementType() == VARIABLE) {
                    return source.getPsi();
                }
                return null;
            }
        }
        // attributes: from=$items item=item
        ASTNode from = null;
        String item = null;
        for (ASTNode c = openTag.getFirstChildNode(); c != null; c = c.getTreeNext()) {
            if (c.getElementType() != ATTRIBUTE) continue;
            String attr = SmartyPsiUtil.attributeName(c);
            if ("item".equals(attr)) item = SmartyPsiUtil.attributeValue(c);
            if ("from".equals(attr)) {
                for (ASTNode v = c.getFirstChildNode(); v != null; v = v.getTreeNext()) {
                    if (v.getElementType() == VARIABLE) from = v;
                }
            }
        }
        return name.equals(item) && from != null ? from.getPsi() : null;
    }

    private static void collectLeaves(ASTNode node, List<ASTNode> out) {
        for (ASTNode c = node.getFirstChildNode(); c != null; c = c.getTreeNext()) {
            if (c.getFirstChildNode() != null) collectLeaves(c, out);
            else if (c.getElementType() != WHITE_SPACE && c.getElementType() != TokenType.WHITE_SPACE) out.add(c);
        }
    }

    // ------------------------------------------------------------------ PHP classes and members

    static List<PhpClass> classesOf(@NotNull Project project, @NotNull PhpType type) {
        PhpIndex index = PhpIndex.getInstance(project);
        List<PhpClass> classes = new ArrayList<>();
        for (String fqn : type.getTypes()) {
            if (!fqn.startsWith("\\") || fqn.endsWith("[]")) continue;
            classes.addAll(index.getAnyByFQN(fqn));
        }
        return classes;
    }

    static PhpType elementType(PhpType type) {
        PhpType result = new PhpType();
        for (String t : type.getTypes()) {
            if (t.endsWith("[]")) result.add(t.substring(0, t.length() - 2));
        }
        return result;
    }

    /** Members a "->name" may refer to: field, method, or Yii getter (getName() for ->name). */
    static List<PhpNamedElement> members(@NotNull Project project, @NotNull PhpType owner, @NotNull String name,
                                         boolean call) {
        Set<PhpNamedElement> result = new LinkedHashSet<>();
        for (PhpClass c : classesOf(project, owner)) {
            if (call) {
                Method m = c.findMethodByName(name);
                if (m != null) result.add(m);
                continue;
            }
            Field f = c.findFieldByName(name, false);
            if (f != null) {
                // a documented property wins over its getter: "@property-read User $user" vs "getUser(): ActiveQuery"
                result.add(f);
                continue;
            }
            Method getter = c.findMethodByName("get" + capitalize(name));
            if (getter != null) result.add(getter);
        }
        return new ArrayList<>(result);
    }

    static @Nullable PhpType memberType(@NotNull Project project, @NotNull PhpType owner, @NotNull String name,
                                        boolean call) {
        PhpType result = null;
        for (PhpNamedElement member : members(project, owner, name, call)) {
            PhpType type = member.getType().global(project);
            result = result == null ? type : new PhpType().add(result).add(type);
        }
        return result;
    }

    static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** "\app\models\User|null" -> "User|null" */
    static @Nullable String presentable(@Nullable PhpType type) {
        if (type == null || type.getTypes().isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (String t : type.getTypes()) {
            if (t.startsWith("#")) continue; // unresolved signatures
            int slash = t.lastIndexOf('\\');
            parts.add(slash >= 0 ? t.substring(slash + 1) : t);
        }
        return parts.isEmpty() ? null : String.join("|", parts);
    }

    // ------------------------------------------------------------------ tokens

    static @Nullable PsiElement prevSignificant(@NotNull PsiElement element) {
        PsiElement leaf = PsiTreeUtil.prevLeaf(element);
        while (leaf != null && (SmartyPsiUtil.is(leaf, WHITE_SPACE) || leaf.getTextLength() == 0)) {
            leaf = PsiTreeUtil.prevLeaf(leaf);
        }
        return leaf;
    }

    static @Nullable PsiElement nextSignificant(@NotNull PsiElement element) {
        PsiElement leaf = PsiTreeUtil.nextLeaf(element);
        while (leaf != null && (SmartyPsiUtil.is(leaf, WHITE_SPACE) || leaf.getTextLength() == 0)) {
            leaf = PsiTreeUtil.nextLeaf(leaf);
        }
        return leaf;
    }

    private static @Nullable PsiElement matchingOpenParen(PsiElement close) {
        int depth = 0;
        for (PsiElement leaf = close; leaf != null; leaf = PsiTreeUtil.prevLeaf(leaf)) {
            if (SmartyPsiUtil.is(leaf, RPAREN)) depth++;
            else if (SmartyPsiUtil.is(leaf, LPAREN) && --depth == 0) return leaf;
            else if (SmartyPsiUtil.is(leaf, LDELIM)) return null;
        }
        return null;
    }
}
