package com.smartyplugin.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.parser.SmartyElementTypes.TAGS

/** A quoted string inside a tag. Strings naming a template file get file references (Ctrl+Click, rename). */
class SmartyStringLiteral(node: ASTNode) : SmartyPsiElement(node) {

    val value: String
        get() = SmartyPsiUtil.unquote(text)

    /** {include file="x.tpl"}, {include 'x.tpl'}, {extends "layout.tpl"} ... */
    fun isTemplatePath(): Boolean {
        val parent = parent
        val tag: ASTNode? = if (SmartyPsiUtil.isType(parent, ATTRIBUTE)) {
            if (SmartyPsiUtil.attributeName(parent.node) != "file") return false
            parent.parent?.node
        } else {
            parent?.node
        }
        if (tag == null || !TAGS.contains(tag.elementType)) return false
        val name = SmartyPsiUtil.tagName(tag) // null for print tags: {$form->field($model, 'x')}
        return name != null && name in FILE_TAGS
    }

    override fun getReferences(): Array<PsiReference> {
        if (!isTemplatePath()) return PsiReference.EMPTY_ARRAY
        if (text.length < 2) return PsiReference.EMPTY_ARRAY
        val value = value
        // dynamic paths and resource prefixes ("file:", "string:", "extends:") are not resolvable statically
        if (value.isEmpty() || value.contains("$") || value.contains(SmartyDelimiters.of(this).left) ||
            value.contains("`") || value.matches(RESOURCE_PREFIX)
        ) {
            return PsiReference.EMPTY_ARRAY
        }
        val set = object : FileReferenceSet(value, this, 1, null, true) {
            override fun isSoft(): Boolean = true

            override fun computeDefaultContexts(): Collection<PsiFileSystemItem> =
                templateRoots(element.containingFile)
        }
        @Suppress("UNCHECKED_CAST")
        return set.allReferences as Array<PsiReference>
    }

    companion object {
        private val FILE_TAGS = setOf("include", "extends", "config_load", "fetch", "include_php")
        private val RESOURCE_PREFIX = Regex("^[a-zA-Z_]+:.*")

        /**
         * Smarty resolves relative paths against the configured template_dir, which we don't know.
         * Approximate it with the template's own directory and all its ancestors up to the content root.
         */
        @JvmStatic
        fun templateRoots(file: PsiFile): List<PsiFileSystemItem> {
            val result = ArrayList<PsiFileSystemItem>()
            val original = file.originalFile
            val vf = original.virtualFile
            if (vf == null) {
                original.containingDirectory?.let { result.add(it) }
                return result
            }
            val contentRoot = ProjectFileIndex.getInstance(file.project).getContentRootForFile(vf)
            val manager = file.manager
            var dir = vf.parent
            var guard = 0
            while (dir != null && guard++ < 12) {
                manager.findDirectory(dir)?.let { result.add(it) }
                if (dir == contentRoot || contentRoot == null && guard >= 4) break
                dir = dir.parent
            }
            return result
        }
    }
}
