package com.smartyplugin.editor

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.lang.SmartyPlugins
import com.smartyplugin.lang.SmartyTags
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON
import com.smartyplugin.lexer.SmartyTokenTypes.MODIFIER_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.SLASH
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.WHITE_SPACE
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.parser.SmartyElementTypes.TAGS
import com.smartyplugin.psi.SmartyInheritance
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * Tags and modifiers that are neither built in, nor plugins of the project, nor template functions
 * (`{function name="x"}`) or classes imported with `{use class="..."}`. Off by default: plugins registered in ways
 * the plugin cannot see (plugin directories outside the project, runtime registration) would all be reported.
 */
class SmartyUnknownNameInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element.firstChild != null) return
            when {
                SmartyPsiUtil.isType(element, TAG_NAME) -> checkTag(element, holder)
                SmartyPsiUtil.isType(element, MODIFIER_NAME) -> checkModifier(element, holder)
            }
        }
    }

    private fun checkTag(name: PsiElement, holder: ProblemsHolder) {
        if (SmartyPsiUtil.isType(prevSignificant(name), SLASH)) return // {/x}: reported on {x} if at all
        if (SmartyPsiUtil.isType(nextSignificant(name), DOUBLE_COLON)) return // {Html::a(...)}
        val text = name.text
        if (SmartyTags.tag(text) != null || text in localNames(name.containingFile)) return
        if (SmartyPlugins.findTag(name.project, text).isNotEmpty()) return
        holder.registerProblem(name, "Unknown tag '$text'", ProblemHighlightType.WARNING)
    }

    private fun checkModifier(name: PsiElement, holder: ProblemsHolder) {
        val text = name.text
        if (SmartyTags.allModifiers().any { it.name == text }) return
        val project = name.project
        if (SmartyPlugins.findModifier(project, text).isNotEmpty() || SmartyPlugins.isPhpFunction(project, text)) return
        holder.registerProblem(name, "Unknown modifier '$text'", ProblemHighlightType.WARNING)
    }

    /** Names defined in the template and its layouts: {function name="x"}, {use class="a\B"} (B) / as="C" (C). */
    private fun localNames(file: PsiFile): Set<String> {
        val smarty = SmartyPsiUtil.smartyFile(file) ?: return emptySet()
        return CachedValuesManager.getCachedValue(smarty) {
            val names = HashSet<String>()
            for (f in listOf(smarty) + SmartyInheritance.parents(smarty)) {
                val root = SmartyPsiUtil.smartyFile(f) ?: continue
                PsiTreeUtil.processElements(root) { e ->
                    if (e.node != null && TAGS.contains(e.node.elementType)) collectNames(e, names)
                    true
                }
            }
            CachedValueProvider.Result.create(names as Set<String>, com.intellij.psi.util.PsiModificationTracker.getInstance(file.project))
        }
    }

    private fun collectNames(tag: PsiElement, out: MutableSet<String>) {
        val tagName = SmartyPsiUtil.tagName(tag.node) ?: return
        if (tagName != "function" && tagName != "use") return
        var value: String? = null
        var alias: String? = null
        for (attribute in tag.children) {
            if (!SmartyPsiUtil.isType(attribute, ATTRIBUTE)) continue
            when (SmartyPsiUtil.attributeName(attribute.node)) {
                "name", "class" -> value = SmartyPsiUtil.attributeValue(attribute.node)
                "as" -> alias = SmartyPsiUtil.attributeValue(attribute.node)
            }
        }
        // shorthand {function "x"} / {use "a\B"}: the first string
        if (value == null) {
            value = tag.children.firstOrNull { it is com.smartyplugin.psi.SmartyStringLiteral }?.let { SmartyPsiUtil.unquote(it.text) }
        }
        val name = alias ?: value?.replace("\\\\", "\\")?.substringAfterLast('\\') ?: return
        if (name.isNotEmpty()) out.add(name)
    }

    private fun prevSignificant(e: PsiElement): PsiElement? {
        var leaf = PsiTreeUtil.prevLeaf(e)
        while (leaf != null && SmartyPsiUtil.isType(leaf, WHITE_SPACE)) leaf = PsiTreeUtil.prevLeaf(leaf)
        return leaf
    }

    private fun nextSignificant(e: PsiElement): PsiElement? {
        var leaf = PsiTreeUtil.nextLeaf(e)
        while (leaf != null && SmartyPsiUtil.isType(leaf, WHITE_SPACE)) leaf = PsiTreeUtil.nextLeaf(leaf)
        return leaf
    }
}
