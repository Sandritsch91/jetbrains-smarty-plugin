package com.smartyplugin.psi

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.lexer.SmartyTokenTypes.ASSIGN
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.WHITE_SPACE
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG
import com.smartyplugin.parser.SmartyElementTypes.STRING_LITERAL

/**
 * Template inheritance: `{extends file="layout.tpl"}` and `{block name="content"}` overriding the block
 * of the same name in the layout (or the layout's own parent).
 */
object SmartyInheritance {
    private const val MAX_DEPTH = 10

    /** The name of {block name="content"} / {block "content"} / {block name=content}: the literal or identifier. */
    fun blockNameElement(openTag: PsiElement): PsiElement? {
        if (!SmartyPsiUtil.isType(openTag, OPEN_TAG) || SmartyPsiUtil.tagName(openTag.node) != "block") return null
        var c = openTag.node.firstChildNode
        while (c != null) {
            if (c.elementType === STRING_LITERAL) return c.psi
            if (c.elementType === ATTRIBUTE && SmartyPsiUtil.attributeName(c) == "name") {
                var v = c.firstChildNode
                var afterAssign = false
                while (v != null) {
                    if (v.elementType === ASSIGN) {
                        afterAssign = true
                    } else if (afterAssign && v.elementType !== WHITE_SPACE) {
                        return if (v.elementType === STRING_LITERAL || v.elementType === IDENTIFIER) v.psi else null
                    }
                    v = v.treeNext
                }
                return null
            }
            c = c.treeNext
        }
        return null
    }

    fun nameOf(nameElement: PsiElement): String = SmartyPsiUtil.unquote(nameElement.text)

    /** The block name element when [element] is one or lies in one (the STRING token of the literal). */
    fun asBlockName(element: PsiElement): PsiElement? {
        val candidate = if (SmartyPsiUtil.isType(element.parent, STRING_LITERAL)) element.parent else element
        val tag = PsiTreeUtil.findFirstParent(candidate) { SmartyPsiUtil.isType(it, OPEN_TAG) } ?: return null
        return if (blockNameElement(tag) === candidate) candidate else null
    }

    /** Blocks of a template by name (the first one of each name), as name elements. */
    fun blocks(file: PsiFile): Map<String, PsiElement> {
        val smarty = SmartyPsiUtil.smartyFile(file) ?: return emptyMap()
        return CachedValuesManager.getCachedValue(smarty) {
            val result = LinkedHashMap<String, PsiElement>()
            PsiTreeUtil.processElements(smarty) { e ->
                blockNameElement(e)?.let { result.putIfAbsent(nameOf(it), it) }
                true
            }
            CachedValueProvider.Result.create(result as Map<String, PsiElement>, smarty)
        }
    }

    /** The layouts above [file]: its {extends} template, that one's, ... */
    fun parents(file: PsiFile): List<PsiFile> {
        val result = ArrayList<PsiFile>()
        var current: PsiFile? = SmartyIncludes.referencedTemplate(file, "extends")
        while (current != null && result.size < MAX_DEPTH && current !in result && current != file.originalFile) {
            result.add(current)
            current = SmartyIncludes.referencedTemplate(current, "extends")
        }
        return result
    }

    /** The block of the same name in the nearest layout above: what [nameElement]'s block overrides. */
    fun overridden(nameElement: PsiElement): PsiElement? {
        val name = nameOf(nameElement)
        for (parent in parents(nameElement.containingFile)) {
            blocks(parent)[name]?.let { return it }
        }
        return null
    }

    /** Templates that {extends} this one, directly. */
    fun children(file: PsiFile): List<PsiFile> {
        val original = file.originalFile
        val project = original.project
        return CachedValuesManager.getCachedValue(original) {
            val result = if (DumbService.isDumb(project)) emptyList()
            else SmartyIncludes.referringTags(original, "extends").map { it.containingFile }.distinct()
            CachedValueProvider.Result.create(
                result,
                PsiModificationTracker.getInstance(project),
                DumbService.getInstance(project).modificationTracker,
            )
        }
    }

    /** Blocks of the same name in templates extending this one, also further down when a child skips the block. */
    fun overriding(nameElement: PsiElement): List<PsiElement> {
        val name = nameOf(nameElement)
        val result = ArrayList<PsiElement>()
        val seen = HashSet<PsiFile>()
        var level = children(nameElement.containingFile)
        var depth = 0
        while (level.isNotEmpty() && depth++ < MAX_DEPTH) {
            val next = ArrayList<PsiFile>()
            for (child in level) {
                if (!seen.add(child)) continue
                val block = blocks(child)[name]
                if (block != null) result.add(block) else next.addAll(children(child))
            }
            level = next
        }
        return result
    }
}
