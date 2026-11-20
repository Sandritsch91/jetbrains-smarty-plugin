package com.smartyplugin.lexer

import com.intellij.psi.tree.IElementType
import com.smartyplugin.lang.SmartyLanguage
import org.jetbrains.annotations.NonNls

open class SmartyTokenType(@NonNls debugName: String) : IElementType(debugName, SmartyLanguage) {
    override fun toString(): String = "Smarty:" + super.toString()
}
