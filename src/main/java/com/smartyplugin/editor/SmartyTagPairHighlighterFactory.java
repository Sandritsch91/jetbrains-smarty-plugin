package com.smartyplugin.editor;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.smartyplugin.psi.SmartyPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME;
import static com.smartyplugin.parser.SmartyElementTypes.BLOCK;
import static com.smartyplugin.parser.SmartyElementTypes.BLOCK_PARTS;

/** With the caret on a tag name, highlights all tags of that block: {if} / {elseif} / {else} / {/if}. */
public final class SmartyTagPairHighlighterFactory extends HighlightUsagesHandlerFactoryBase {
    @Override
    public @Nullable HighlightUsagesHandlerBase<?> createHighlightUsagesHandler(@NotNull Editor editor,
                                                                               @NotNull PsiFile file,
                                                                               @NotNull PsiElement target) {
        if (!SmartyPsiUtil.is(target, TAG_NAME)) return null;
        PsiElement tag = target.getParent();
        if (tag == null || !BLOCK_PARTS.contains(tag.getNode().getElementType())) return null;
        PsiElement block = tag.getParent();
        if (!SmartyPsiUtil.is(block, BLOCK)) return null;
        return new Handler(editor, file, target, block);
    }

    private static final class Handler extends HighlightUsagesHandlerBase<PsiElement> {
        private final PsiElement target;
        private final PsiElement block;

        Handler(Editor editor, PsiFile file, PsiElement target, PsiElement block) {
            super(editor, file);
            this.target = target;
            this.block = block;
        }

        @Override
        public @NotNull List<PsiElement> getTargets() {
            return List.of(target);
        }

        @Override
        protected void selectTargets(@NotNull List<? extends PsiElement> targets,
                                     @NotNull Consumer<? super List<? extends PsiElement>> selectionConsumer) {
            selectionConsumer.consume(targets);
        }

        @Override
        public void computeUsages(@NotNull List<? extends PsiElement> targets) {
            for (ASTNode child = block.getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
                if (!BLOCK_PARTS.contains(child.getElementType())) continue;
                ASTNode name = SmartyPsiUtil.tagNameNode(child);
                if (name != null) addOccurrence(name.getPsi());
            }
        }
    }
}
