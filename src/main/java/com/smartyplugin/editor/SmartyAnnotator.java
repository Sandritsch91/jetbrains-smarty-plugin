package com.smartyplugin.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.smartyplugin.lang.SmartyTags;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;

import static com.smartyplugin.parser.SmartyElementTypes.*;

/** Reports structural errors: unclosed blocks, stray closing tags and misplaced {else}-like tags. */
public final class SmartyAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        ASTNode node = element.getNode();
        if (node == null) return;
        IElementType type = node.getElementType();

        if (type == BLOCK && SmartyPsiUtil.closeTag(node) == null) {
            ASTNode open = SmartyPsiUtil.openTag(node);
            String name = SmartyPsiUtil.blockName(node);
            if (open != null && name != null) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Missing closing tag {/" + name + "}")
                        .range(open.getTextRange())
                        .create();
            }
        } else if (type == CLOSE_TAG && !SmartyPsiUtil.is(node.getTreeParent(), BLOCK)) {
            String name = SmartyPsiUtil.tagName(node);
            holder.newAnnotation(HighlightSeverity.ERROR,
                            name == null ? "Closing tag without a name" : "Closing tag {/" + name + "} has no matching opening tag")
                    .range(node.getTextRange())
                    .create();
        } else if (type == MIDDLE_TAG) {
            String name = SmartyPsiUtil.tagName(node);
            ASTNode parent = node.getTreeParent();
            String blockName = SmartyPsiUtil.is(parent, BLOCK) ? SmartyPsiUtil.blockName(parent) : null;
            if (name != null && !SmartyTags.isMiddleAllowed(blockName, name)) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                                "{" + name + "} is only allowed inside " + describeParents(name))
                        .range(node.getTextRange())
                        .create();
            }
        } else if (type == OPEN_TAG || type == TAG || type == MIDDLE_TAG) {
            checkRequiresArguments(node, holder);
        }
    }

    private static void checkRequiresArguments(ASTNode tag, AnnotationHolder holder) {
        String name = SmartyPsiUtil.tagName(tag);
        if (!"if".equals(name) && !"elseif".equals(name) && !"while".equals(name)) return;
        ASTNode nameNode = SmartyPsiUtil.tagNameNode(tag);
        if (nameNode == null) return;
        for (ASTNode c = nameNode.getTreeNext(); c != null; c = c.getTreeNext()) {
            IElementType t = c.getElementType();
            if (t != com.intellij.psi.TokenType.WHITE_SPACE && t != com.smartyplugin.lexer.SmartyTokenTypes.RDELIM) return;
        }
        holder.newAnnotation(HighlightSeverity.ERROR, "{" + name + "} requires a condition")
                .range(TextRange.from(tag.getStartOffset(), tag.getTextLength()))
                .create();
    }

    private static String describeParents(String middle) {
        StringBuilder sb = new StringBuilder();
        for (String parent : SmartyTags.allowedParentsOfMiddle(middle)) {
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append('{').append(parent).append('}');
        }
        return sb.toString();
    }
}
