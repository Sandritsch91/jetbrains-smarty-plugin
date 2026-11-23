package com.smartyplugin.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.lang.SmartyVariableProvider
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE

/**
 * Occurrences of a template variable, for usage highlighting and rename:
 * `$name` in the template, its definitions (`assign="name"`, `{assign var="name"}`, `item="name"`),
 * `"…$name…"` inside double-quoted strings, and the places that pass it in (PHP render calls, `{include}`).
 */
object SmartyVariableUsages {
    private val DEFINING_ATTRIBUTES = setOf("assign", "var", "item", "key")
    private val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** An occurrence: the range of the bare name (without `$` or quotes) in [element]'s file. */
    data class Occurrence(val element: PsiElement, val range: TextRange)

    /** The variable name when [token] is `$name` or the value of a defining attribute; null otherwise. */
    fun nameAt(token: PsiElement): String? {
        if (SmartyPsiUtil.isType(token, VARIABLE)) return token.text.substring(1).takeIf { it.matches(NAME) }
        if (SmartyPsiUtil.isType(token, STRING) || SmartyPsiUtil.isType(token, IDENTIFIER)) {
            val attribute = PsiTreeUtil.findFirstParent(token) { SmartyPsiUtil.isType(it, ATTRIBUTE) } ?: return null
            if (SmartyPsiUtil.attributeName(attribute.node) !in DEFINING_ATTRIBUTES) return null
            val value = SmartyPsiUtil.attributeValue(attribute.node) ?: return null
            if (!value.matches(NAME) || SmartyPsiUtil.unquote(token.text) != value) return null
            return value
        }
        return null
    }

    /** Occurrences in the template itself. */
    fun inTemplate(file: PsiFile, name: String): List<Occurrence> {
        val smarty = SmartyPsiUtil.smartyFile(file) ?: return emptyList()
        val inString = Regex("\\$" + Regex.escape(name) + "(?![A-Za-z0-9_])")
        val result = ArrayList<Occurrence>()
        PsiTreeUtil.processElements(smarty) { e ->
            if (e.firstChild != null) return@processElements true
            when {
                SmartyPsiUtil.isType(e, VARIABLE) && e.text == "$$name" -> {
                    val start = e.textRange.startOffset + 1
                    result.add(Occurrence(e, TextRange(start, start + name.length)))
                }
                nameAt(e) == name -> {
                    val start = e.textRange.startOffset + e.text.indexOf(name)
                    result.add(Occurrence(e, TextRange(start, start + name.length)))
                }
                SmartyPsiUtil.isType(e, STRING) && e.text.startsWith("\"") -> {
                    for (m in inString.findAll(e.text)) {
                        val start = e.textRange.startOffset + m.range.first + 1
                        result.add(Occurrence(e, TextRange(start, start + name.length)))
                    }
                }
            }
            true
        }
        return result
    }

    /**
     * Where the variable is passed in: `'name' =>` / `compact('name')` / `assign('name', …)` in PHP,
     * `name=` of `{include}` in other templates. Implicit variables ($app, $this) have no such place.
     */
    fun passingSites(file: PsiFile, name: String): List<Occurrence> {
        val result = ArrayList<Occurrence>()
        for (v in SmartyVariableProvider.all(file)) {
            if (v.name != name) continue
            val target = v.target
            if (target is PsiFile || !target.isValid) continue
            val text = target.text
            // the bare name inside the key literal / attribute name; classes ($app -> Application) never match
            val at = text.indexOf(name)
            if (at < 0 || SmartyPsiUtil.unquote(text) != name) continue
            val start = target.textRange.startOffset + at
            result.add(Occurrence(target, TextRange(start, start + name.length)))
        }
        return result.distinctBy { it.element.containingFile to it.range }
    }
}
