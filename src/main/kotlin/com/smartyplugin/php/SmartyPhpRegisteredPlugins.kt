package com.smartyplugin.php

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.smartyplugin.lang.SmartyPluginProvider
import com.smartyplugin.lang.SmartyPluginProvider.Kind
import com.smartyplugin.lang.SmartyPluginProvider.Plugin

/**
 * `$smarty->registerPlugin('function', 'name', 'php_function')`, also with `Smarty::PLUGIN_BLOCK` and with
 * `[Helper::class, 'method']` / `[$this, 'method']` callbacks. Ctrl+Click goes to the callback when it resolves.
 */
class SmartyPhpRegisteredPlugins : SmartyPluginProvider {
    override fun getPlugins(project: Project): List<Plugin> {
        val result = ArrayList<Plugin>()
        val seen = HashSet<MethodReference>()
        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, _ ->
                if (!element.language.isKindOf(PhpLanguage.INSTANCE)) return@processElementsWithWord true
                val call = PsiTreeUtil.getParentOfType(element, MethodReference::class.java, false)
                if (call == null || call.name != "registerPlugin" || !seen.add(call)) return@processElementsWithWord true
                val params = call.parameters
                if (params.size < 2) return@processElementsWithWord true
                val kind = kind(params[0]) ?: return@processElementsWithWord true
                val name = (params[1] as? StringLiteralExpression)?.contents
                if (name.isNullOrEmpty()) return@processElementsWithWord true
                val target = params.getOrNull(2)?.let { callback(project, it) } ?: params[1]
                result.add(Plugin(kind, name, target, "registerPlugin"))
                true
            },
            GlobalSearchScope.projectScope(project), "registerPlugin", UsageSearchContext.IN_CODE, true,
        )
        return result
    }

    override fun isPhpFunction(project: Project, name: String): Boolean =
        PhpIndex.getInstance(project).getFunctionsByName(name).isNotEmpty()

    private fun kind(type: PsiElement): Kind? {
        val text = when (type) {
            is StringLiteralExpression -> type.contents
            is ClassConstantReference -> type.name?.removePrefix("PLUGIN_")?.lowercase()
            else -> null
        }
        return when (text) {
            "function", "compiler" -> Kind.FUNCTION
            "block" -> Kind.BLOCK
            "modifier", "modifiercompiler" -> Kind.MODIFIER
            else -> null
        }
    }

    private fun fqn(name: String) = if (name.startsWith("\\")) name else "\\" + name

    /** 'my_function' -> the function; [Helper::class, 'method'] / ['Helper', 'method'] -> the method. */
    private fun callback(project: Project, callback: PsiElement): PsiElement? {
        val index = PhpIndex.getInstance(project)
        if (callback is StringLiteralExpression) {
            val name = callback.contents
            if (name.contains("::")) {
                val (cls, method) = name.split("::", limit = 2)
                return index.getAnyByFQN(fqn(cls)).firstNotNullOfOrNull { it.findMethodByName(method) }
            }
            return index.getFunctionsByFQN(fqn(name)).firstOrNull()
                ?: index.getFunctionsByName(name.substringAfterLast('\\')).firstOrNull()
        }
        if (callback is ArrayCreationExpression) {
            val values = callback.children.mapNotNull { it.firstChild }
            if (values.size != 2) return null
            val method = (values[1] as? StringLiteralExpression)?.contents ?: return null
            val classFqn = when (val owner = values[0]) {
                is ClassConstantReference -> (owner.classReference as? ClassReference)?.fqn
                is StringLiteralExpression -> fqn(owner.contents)
                else -> null
            }
            if (classFqn != null) {
                return index.getAnyByFQN(classFqn).firstNotNullOfOrNull { it.findMethodByName(method) }
            }
            // [$this, 'method']: a method of the class around the call
            val ownerClass = PsiTreeUtil.getParentOfType(callback, PhpClass::class.java)
            return ownerClass?.findMethodByName(method)
        }
        return null
    }
}
