package com.smartyplugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.smartyplugin.parser.SmartyElementTypes.*;

/** A quoted string inside a tag. Strings naming a template file get file references (Ctrl+Click, rename). */
public final class SmartyStringLiteral extends SmartyPsiElement {
    private static final Set<String> FILE_TAGS = Set.of("include", "extends", "config_load", "fetch", "include_php");

    public SmartyStringLiteral(@NotNull ASTNode node) {
        super(node);
    }

    public @NotNull String getValue() {
        return SmartyPsiUtil.unquote(getText());
    }

    /** {include file="x.tpl"}, {include 'x.tpl'}, {extends "layout.tpl"} ... */
    public boolean isTemplatePath() {
        PsiElement parent = getParent();
        ASTNode tag;
        if (SmartyPsiUtil.is(parent, ATTRIBUTE)) {
            if (!"file".equals(SmartyPsiUtil.attributeName(parent.getNode()))) return false;
            tag = parent.getParent() == null ? null : parent.getParent().getNode();
        } else {
            tag = parent == null ? null : parent.getNode();
        }
        if (tag == null || !TAGS.contains(tag.getElementType())) return false;
        return FILE_TAGS.contains(SmartyPsiUtil.tagName(tag));
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        if (!isTemplatePath()) return PsiReference.EMPTY_ARRAY;
        String text = getText();
        if (text.length() < 2) return PsiReference.EMPTY_ARRAY;
        String value = getValue();
        // dynamic paths and resource prefixes ("file:", "string:", "extends:") are not resolvable statically
        if (value.isEmpty() || value.contains("$") || value.contains("{") || value.contains("`")
                || value.matches("^[a-zA-Z_]+:.*")) {
            return PsiReference.EMPTY_ARRAY;
        }
        FileReferenceSet set = new FileReferenceSet(value, this, 1, null, true) {
            @Override
            protected boolean isSoft() {
                return true;
            }

            @Override
            public @NotNull Collection<PsiFileSystemItem> computeDefaultContexts() {
                return templateRoots(getElement().getContainingFile());
            }
        };
        return set.getAllReferences();
    }

    /**
     * Smarty resolves relative paths against the configured template_dir, which we don't know.
     * Approximate it with the template's own directory and all its ancestors up to the content root.
     */
    static @NotNull List<PsiFileSystemItem> templateRoots(@NotNull PsiFile file) {
        List<PsiFileSystemItem> result = new ArrayList<>();
        PsiFile original = file.getOriginalFile();
        VirtualFile vf = original.getVirtualFile();
        if (vf == null) {
            PsiDirectory dir = original.getContainingDirectory();
            if (dir != null) result.add(dir);
            return result;
        }
        VirtualFile contentRoot = ProjectFileIndex.getInstance(file.getProject()).getContentRootForFile(vf);
        PsiManager manager = file.getManager();
        VirtualFile dir = vf.getParent();
        int guard = 0;
        while (dir != null && guard++ < 12) {
            PsiDirectory psiDir = manager.findDirectory(dir);
            if (psiDir != null) result.add(psiDir);
            if (dir.equals(contentRoot) || contentRoot == null && guard >= 4) break;
            dir = dir.getParent();
        }
        return result;
    }
}
