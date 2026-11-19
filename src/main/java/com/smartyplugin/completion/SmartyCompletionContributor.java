package com.smartyplugin.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.smartyplugin.lang.SmartyIcons;
import com.smartyplugin.lang.SmartyLanguage;
import com.smartyplugin.lang.SmartyTags;
import com.smartyplugin.lang.SmartyVariableProvider;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;
import static com.smartyplugin.parser.SmartyElementTypes.*;

/**
 * Completes tag names, closing tags, attributes, modifiers, variables and <code>$smarty.*</code> properties.
 */
public final class SmartyCompletionContributor extends CompletionContributor {
    private static final Set<String> NAME_ATTRIBUTES_DEFINING_VARIABLES = Set.of("var", "item", "key", "assign");

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        if (position.getLanguage() != SmartyLanguage.INSTANCE || position.getNode() == null) return;
        IElementType type = position.getNode().getElementType();
        int caretInToken = parameters.getOffset() - position.getTextRange().getStartOffset();
        String prefix = position.getText().substring(0, Math.max(0, Math.min(caretInToken, position.getTextLength())));
        CompletionResultSet rs = result.withPrefixMatcher(prefix);

        if (type == TAG_NAME) {
            PsiElement prev = PsiTreeUtil.prevLeaf(position);
            if (SmartyPsiUtil.is(prev, SLASH)) {
                completeClosingTag(position, rs);
            } else {
                completeTagNames(parameters.getOriginalFile(), parameters.getOffset(), rs);
            }
            rs.stopHere();
        } else if (type == MODIFIER_NAME) {
            completeModifiers(rs);
            rs.stopHere();
        } else if (type == VARIABLE) {
            completeVariables(parameters.getOriginalFile(), parameters.getOffset(), rs);
            rs.stopHere();
        } else if (type == IDENTIFIER || type == ATTR_NAME) {
            PsiElement prev = PsiTreeUtil.prevLeaf(position);
            if (SmartyPsiUtil.is(prev, DOT)) {
                PsiElement owner = PsiTreeUtil.prevLeaf(prev);
                if (owner != null && "$smarty".equals(owner.getText())) {
                    for (String p : SmartyTags.SMARTY_VARIABLE_PROPERTIES) {
                        rs.addElement(LookupElementBuilder.create(p).withTypeText("$smarty", true));
                    }
                    rs.stopHere();
                }
                return;
            }
            if (SmartyPsiUtil.is(prev, AT)) {
                for (String p : SmartyTags.LOOP_PROPERTIES) {
                    rs.addElement(LookupElementBuilder.create(p).withTypeText("loop property", true));
                }
                rs.stopHere();
                return;
            }
            completeAttributes(position, rs);
        }
    }

    // ---------------------------------------------------------------- tags

    private static void completeTagNames(PsiFile file, int caret, CompletionResultSet rs) {
        Set<String> seen = new LinkedHashSet<>();
        for (SmartyTags.Tag tag : SmartyTags.allTags()) {
            seen.add(tag.name());
            LookupElementBuilder element = LookupElementBuilder.create(tag.name())
                    .withIcon(SmartyIcons.FILE)
                    .withTailText(tag.kind() == SmartyTags.Kind.BLOCK ? " …{/" + tag.name() + "}" : null, true)
                    .withTypeText(tag.description(), true)
                    .withInsertHandler(new TagInsertHandler(tag));
            double priority = "Yii2".equals(tag.source()) ? -1 : 0;
            rs.addElement(PrioritizedLookupElement.withPriority(element, priority));
        }
        // custom tags and plugins already used in this file
        PsiFile smarty = SmartyPsiUtil.smartyFile(file);
        if (smarty == null) return;
        PsiTreeUtil.processElements(smarty, e -> {
            if (SmartyPsiUtil.is(e, TAG_NAME) && !e.getTextRange().containsOffset(caret)) {
                String name = e.getText();
                if (!name.contains(DUMMY) && seen.add(name)) {
                    rs.addElement(LookupElementBuilder.create(name).withTypeText("used in file", true));
                }
            }
            return true;
        });
    }

    private static void completeClosingTag(PsiElement position, CompletionResultSet rs) {
        ASTNode block = SmartyPsiUtil.innermostUnclosedBlock(position.getNode());
        int priority = 100;
        Set<String> seen = new LinkedHashSet<>();
        while (block != null) {
            String name = SmartyPsiUtil.blockName(block);
            if (name != null && seen.add(name)) {
                rs.addElement(PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(name).withIcon(SmartyIcons.FILE).withInsertHandler(CLOSE_BRACE),
                        priority--));
            }
            block = SmartyPsiUtil.innermostUnclosedBlock(block.getTreeParent());
        }
        for (SmartyTags.Tag tag : SmartyTags.allTags()) {
            if (tag.kind() == SmartyTags.Kind.BLOCK && seen.add(tag.name())) {
                rs.addElement(LookupElementBuilder.create(tag.name()).withInsertHandler(CLOSE_BRACE));
            }
        }
    }

    private static final String DUMMY = "IntellijIdeaRulezzz";

    private static final InsertHandler<LookupElement> CLOSE_BRACE = (context, item) -> {
        Document doc = context.getDocument();
        int tail = context.getTailOffset();
        if (tail >= doc.getTextLength() || doc.getCharsSequence().charAt(tail) != '}') {
            doc.insertString(tail, "}");
        }
        context.getEditor().getCaretModel().moveToOffset(tail + 1);
    };

    private record TagInsertHandler(SmartyTags.Tag tag) implements InsertHandler<LookupElement> {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
            Document doc = context.getDocument();
            int tail = context.getTailOffset();
            CharSequence text = doc.getCharsSequence();
            boolean braceFollows = tail < text.length() && text.charAt(tail) == '}';
            if (tag.takesArguments()) {
                if (!braceFollows && (tail >= text.length() || text.charAt(tail) != ' ')) {
                    doc.insertString(tail, " ");
                }
                context.getEditor().getCaretModel().moveToOffset(tail + 1);
                if (!tag.attributes().isEmpty()) {
                    AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
                }
                return;
            }
            String insert = braceFollows ? "" : "}";
            int caret = tail + 1;
            if (tag.kind() == SmartyTags.Kind.BLOCK) {
                insert += "{/" + tag.name() + "}";
            }
            doc.insertString(tail, insert);
            context.getEditor().getCaretModel().moveToOffset(caret);
        }
    }

    // ---------------------------------------------------------------- attributes

    private static void completeAttributes(PsiElement position, CompletionResultSet rs) {
        ASTNode tag = SmartyPsiUtil.enclosingTag(position.getNode());
        if (tag == null) return;
        // only at attribute-name positions: directly in the tag or as the name of an attribute
        ASTNode parent = position.getNode().getTreeParent();
        if (parent != tag && !(parent.getElementType() == ATTRIBUTE && parent.getFirstChildNode() == position.getNode())) {
            return;
        }
        SmartyTags.Tag info = SmartyTags.tag(SmartyPsiUtil.tagName(tag));
        if (info == null) return;
        Set<String> present = new LinkedHashSet<>();
        for (ASTNode c = tag.getFirstChildNode(); c != null; c = c.getTreeNext()) {
            if (c.getElementType() == ATTRIBUTE) {
                String n = SmartyPsiUtil.attributeName(c);
                if (n != null) present.add(n);
            }
        }
        for (String attr : info.attributes()) {
            if (present.contains(attr)) continue;
            rs.addElement(LookupElementBuilder.create(attr)
                    .withIcon(AllIcons.Nodes.Property)
                    .withTypeText(info.name(), true)
                    .withInsertHandler((context, item) -> {
                        Document doc = context.getDocument();
                        int tail = context.getTailOffset();
                        if (tail >= doc.getTextLength() || doc.getCharsSequence().charAt(tail) != '=') {
                            doc.insertString(tail, "=");
                        }
                        context.getEditor().getCaretModel().moveToOffset(tail + 1);
                    }));
        }
    }

    // ---------------------------------------------------------------- modifiers

    private static void completeModifiers(CompletionResultSet rs) {
        for (SmartyTags.Modifier m : SmartyTags.allModifiers()) {
            LookupElementBuilder element = LookupElementBuilder.create(m.name())
                    .withIcon(AllIcons.Nodes.Function)
                    .withTailText(m.signature().isEmpty() ? null : " " + m.signature(), true)
                    .withTypeText(m.source(), true);
            rs.addElement(PrioritizedLookupElement.withPriority(element, "PHP".equals(m.source()) ? -1 : 0));
        }
    }

    // ---------------------------------------------------------------- variables

    private static void completeVariables(PsiFile file, int caret, CompletionResultSet rs) {
        Set<String> names = new LinkedHashSet<>();
        names.add("$smarty");
        PsiFile smarty = SmartyPsiUtil.smartyFile(file);
        if (smarty != null) {
            PsiTreeUtil.processElements(smarty, e -> {
                ASTNode node = e.getNode();
                if (node == null) return true;
                if (node.getElementType() == VARIABLE && !e.getTextRange().containsOffset(caret)) {
                    String text = node.getText();
                    if (text.length() > 1 && !text.contains(DUMMY)) names.add(text);
                } else if (node.getElementType() == ATTRIBUTE) {
                    String attr = SmartyPsiUtil.attributeName(node);
                    if (attr != null && NAME_ATTRIBUTES_DEFINING_VARIABLES.contains(attr)) {
                        String value = SmartyPsiUtil.attributeValue(node);
                        if (value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*")) names.add("$" + value);
                    }
                }
                return true;
            });
        }
        // passed in from outside (PHP render/assign calls): listed first, with their type
        java.util.Map<String, String> external = new java.util.LinkedHashMap<>();
        if (smarty != null) {
            for (SmartyVariableProvider.Variable v : SmartyVariableProvider.all(smarty)) {
                String type = v.type() == null ? "" : v.type();
                external.merge("$" + v.name(), type, (a, b) -> a.isEmpty() ? b : a);
            }
        }
        for (java.util.Map.Entry<String, String> e : external.entrySet()) {
            LookupElementBuilder element = LookupElementBuilder.create(e.getKey())
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(e.getValue().isEmpty() ? "passed in" : e.getValue(), true)
                    .bold();
            rs.addElement(PrioritizedLookupElement.withPriority(element, 10));
        }
        for (String name : names) {
            if (external.containsKey(name)) continue;
            rs.addElement(LookupElementBuilder.create(name)
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText("$smarty".equals(name) ? "reserved" : null, true));
        }
    }
}
