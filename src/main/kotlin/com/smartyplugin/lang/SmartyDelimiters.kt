package com.smartyplugin.lang

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Smarty's left_delimiter / right_delimiter: `{` `}` by default, configurable per project. */
data class SmartyDelimiters(val left: String, val right: String) {
    init {
        require(left.isNotEmpty() && right.isNotEmpty()) { "Smarty delimiters must not be empty" }
    }

    /** {name} */
    fun tag(name: String): String = left + name + right

    /** {/name} */
    fun closeTag(name: String): String = "$left/$name$right"

    fun commentStart(): String = "$left*"

    fun commentEnd(): String = "*$right"

    val isDefault: Boolean
        get() = this == DEFAULT

    companion object {
        @JvmField
        val DEFAULT = SmartyDelimiters("{", "}")

        @JvmStatic
        fun of(project: Project?): SmartyDelimiters {
            if (project == null || project.isDefault || project.isDisposed) return DEFAULT
            return SmartySettings.getInstance(project).delimiters()
        }

        @JvmStatic
        fun of(element: PsiElement): SmartyDelimiters = of(element.project)
    }
}
