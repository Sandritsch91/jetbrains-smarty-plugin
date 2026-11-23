package com.smartyplugin.psi

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.TokenType
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lang.SmartyVariableProvider
import com.smartyplugin.lexer.SmartyTokenTypes.ASSIGN
import com.smartyplugin.lexer.SmartyTokenTypes.ATTR_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.PIPE
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE

/**
 * Variables a template receives from `{include file="x.tpl" title=$t user=$u}` in other templates:
 * every attribute of the include except Smarty's own (file, assign, scope, caching ...).
 */
object SmartyIncludes {
    /** @param target the attribute name in the including template; [value] the last token of its value */
    data class Passed(val name: String, val target: PsiElement, val value: PsiElement?)

    private val OWN_ATTRIBUTES = setOf(
        "file", "assign", "scope", "inline", "nocache", "caching", "cache_lifetime", "cache_id", "compile_id",
    )
    private val NON_WORD = Regex("[^A-Za-z0-9_]+")

    fun passedTo(template: PsiFile): List<Passed> {
        val file = template.originalFile
        val project = file.project
        return CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(
                if (DumbService.isDumb(project)) emptyList() else compute(file),
                PsiModificationTracker.getInstance(project),
                DumbService.getInstance(project).modificationTracker,
            )
        }
    }

    private fun compute(template: PsiFile): List<Passed> {
        val target = template.viewProvider.virtualFile
        val word = target.nameWithoutExtension.split(NON_WORD).firstOrNull { it.isNotEmpty() } ?: return emptyList()
        val project = template.project
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val templates = LinkedHashSet<VirtualFile>()
        for (ext in arrayOf("tpl", "smarty")) templates.addAll(FilenameIndex.getAllFilesByExt(project, ext, scope))

        val result = ArrayList<Passed>()
        for (vf in templates) {
            if (vf == target) continue
            val file = psiManager.findFile(vf)
            if (file == null || !StringUtil.contains(file.viewProvider.contents, word)) continue
            val smarty = file.viewProvider.getPsi(SmartyLanguage) ?: continue
            for (literal in PsiTreeUtil.findChildrenOfType(smarty, SmartyStringLiteral::class.java)) {
                if (!literal.isTemplatePath() || !includes(literal, target)) continue
                val tag = (if (SmartyPsiUtil.isType(literal.parent, ATTRIBUTE)) literal.parent.parent else literal.parent)
                    ?: continue
                if (SmartyPsiUtil.tagName(tag.node) != "include") continue
                for (attribute in tag.children) {
                    if (!SmartyPsiUtil.isType(attribute, ATTRIBUTE)) continue
                    val nameNode = attribute.node.firstChildNode
                    if (nameNode == null || nameNode.elementType !== ATTR_NAME) continue
                    val name = nameNode.text
                    if (name in OWN_ATTRIBUTES) continue
                    result.add(Passed(name, nameNode.psi, lastValueToken(attribute)))
                }
            }
        }
        return result
    }

    private fun includes(literal: SmartyStringLiteral, target: VirtualFile): Boolean {
        val reference = literal.references.lastOrNull() ?: return false
        val resolved = reference.resolve() as? PsiFile ?: return false
        return resolved.viewProvider.virtualFile == target
    }

    /** title=$model->title|escape -> the token the value expression ends with (before modifiers it is typed by). */
    private fun lastValueToken(attribute: PsiElement): PsiElement? {
        var afterAssign = false
        var last: PsiElement? = null
        var leaf: PsiElement? = PsiTreeUtil.firstChild(attribute)
        while (leaf != null && attribute.textRange.contains(leaf.textRange)) {
            when {
                SmartyPsiUtil.isType(leaf, ASSIGN) && !afterAssign -> afterAssign = true
                afterAssign && SmartyPsiUtil.isType(leaf, PIPE) -> break
                afterAssign && !SmartyPsiUtil.isType(leaf, TokenType.WHITE_SPACE) -> last = leaf
            }
            leaf = PsiTreeUtil.nextLeaf(leaf)
        }
        return last
    }
}

/** Variables passed by {include} tags in other templates, without type; the PHP plugin adds types. */
class SmartyIncludeVariableProvider : SmartyVariableProvider {
    override fun getVariables(template: PsiFile): List<SmartyVariableProvider.Variable> =
        SmartyIncludes.passedTo(template).map { SmartyVariableProvider.Variable(it.name, null, it.target) }
}
