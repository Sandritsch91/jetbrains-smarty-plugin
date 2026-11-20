package com.smartyplugin.php

import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType

/** Variables the Yii2 Smarty renderer provides in every template: $app (Yii::$app) and $this (the View). */
internal object SmartyPhpYii {
    val IMPLICIT: Map<String, String> = mapOf(
        "app" to "\\yii\\web\\Application",
        "this" to "\\yii\\web\\View",
    )

    /** The class of an implicit variable, empty when the name isn't one or Yii isn't in the project. */
    fun implicitClasses(project: Project, name: String): Collection<PhpClass> {
        val fqn = IMPLICIT[name] ?: return emptyList()
        return PhpIndex.getInstance(project).getAnyByFQN(fqn)
    }

    fun implicitType(project: Project, name: String): PhpType? {
        val classes = implicitClasses(project, name)
        if (classes.isEmpty()) return null
        val type = PhpType()
        for (c in classes) type.add(c.fqn)
        return type
    }
}
