package com.smartyplugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

open class SmartyPsiElement(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun toString(): String = node.elementType.toString()
}
