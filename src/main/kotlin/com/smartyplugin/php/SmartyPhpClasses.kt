package com.smartyplugin.php

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.parser.SmartyElementTypes.TAG
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * PHP classes named in a template: `{use class="yii\bootstrap5\Html"}` (Yii2 Smarty) makes
 * `{Html::a(...)}` refer to that class, `as="BsHtml"` renames it; other names are looked up
 * as global classes (`Yii`).
 */
internal object SmartyPhpClasses {

    /** Class name token right before "::": a tag name at the start of a tag, an identifier elsewhere. */
    fun classNameBefore(doubleColon: PsiElement): PsiElement? {
        val prev = SmartyPhpTypes.prevSignificant(doubleColon)
        return if (prev != null && (SmartyPsiUtil.isType(prev, TAG_NAME) || SmartyPsiUtil.isType(prev, IDENTIFIER))) prev else null
    }

    /** Class before "::": from {use class="..."}, else a global class (Yii). */
    fun resolve(nameToken: PsiElement): List<PhpClass> {
        val result = resolveUsed(nameToken).toMutableList()
        if (result.isEmpty()) {
            result.addAll(PhpIndex.getInstance(nameToken.project).getAnyByFQN("\\" + nameToken.text))
        }
        return result
    }

    /**
     * Class a widget tag stands for: `{Select2 ...}` or `{ActiveForm}...{/ActiveForm}` after
     * `{use class="..." type="function|block"}`. Only imported classes: {form} must not become \Form.
     */
    fun resolveUsed(nameToken: PsiElement): List<PhpClass> {
        val name = nameToken.text
        val index = PhpIndex.getInstance(nameToken.project)
        val result = ArrayList<PhpClass>()
        for (used in usedClasses(nameToken.containingFile)) {
            if (used.alias == name) result.addAll(index.getAnyByFQN(used.fqn))
        }
        return result
    }

    /** {use class="yii\bootstrap5\Html" as="BsHtml"}: the name the template uses and the class FQN. */
    data class Used(val alias: String, val fqn: String)

    /** Classes from {use class="..." [as="..."]}; the alias defaults to the short class name, like Yii. */
    fun usedClasses(file: PsiFile): List<Used> {
        val smarty = file.viewProvider.getPsi(SmartyLanguage) ?: return emptyList()
        val result = ArrayList<Used>()
        for (tag in PsiTreeUtil.collectElements(smarty) { SmartyPsiUtil.isType(it, TAG) }) {
            val fqn = useClassValue(tag.node) ?: continue
            val alias = attribute(tag.node, "as")
            result.add(Used(if (alias == null || isJavaBlank(alias)) shortName(fqn) else alias, fqn))
        }
        return result
    }

    private fun attribute(tag: ASTNode, name: String): String? {
        var c = tag.firstChildNode
        while (c != null) {
            if (c.elementType === ATTRIBUTE && name == SmartyPsiUtil.attributeName(c)) {
                return SmartyPsiUtil.attributeValue(c)
            }
            c = c.treeNext
        }
        return null
    }

    /** The class of a {use class="..."} tag, or null for other tags. */
    fun useClassValue(tag: ASTNode): String? {
        if (SmartyPsiUtil.tagName(tag) != "use") return null
        var c = tag.firstChildNode
        while (c != null) {
            if (c.elementType === ATTRIBUTE && SmartyPsiUtil.attributeName(c) == "class") {
                var value = SmartyPsiUtil.attributeValue(c)
                if (value == null || isJavaBlank(value)) return null
                value = value.replace("\\\\", "\\")
                return if (value.startsWith("\\")) value else "\\" + value
            }
            c = c.treeNext
        }
        return null
    }

    fun byFqn(context: PsiElement, fqn: String): Collection<PhpClass> =
        PhpIndex.getInstance(context.project).getAnyByFQN(fqn)

    private fun shortName(fqn: String): String {
        val slash = fqn.lastIndexOf('\\')
        return if (slash >= 0) fqn.substring(slash + 1) else fqn
    }

    /** java.lang.String.isBlank(): only [Character.isWhitespace] counts, unlike Kotlin's isBlank(). */
    private fun isJavaBlank(s: String): Boolean = s.all { Character.isWhitespace(it) }
}
