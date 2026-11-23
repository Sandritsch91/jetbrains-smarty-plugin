package com.smartyplugin.php

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.lexer.SmartyTokenTypes.ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.ATTR_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.parser.SmartyElementTypes.OPEN_TAG
import com.smartyplugin.parser.SmartyElementTypes.TAG
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * Ctrl+Click into PHP:
 * - `name` in `$user->name`: the field, method or Yii getter
 * - `Html` / `a` in `{Html::a(...)}`: the class / the static method or constant
 * - the string in `{use class="yii\bootstrap5\Html"}`: the class
 */
class SmartyPhpGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (source == null) return null
        val file = source.containingFile
        if (file == null || file.viewProvider !is SmartyFileViewProvider) return null
        val token = SmartyPsiUtil.smartyElementAt(file, offset) ?: return null
        val targets = targets(token)
        return if (targets.isEmpty()) null else targets.toTypedArray()
    }

    internal companion object {
        fun targets(token: PsiElement): List<PsiElement> {
            if (SmartyPsiUtil.isType(token, STRING)) {
                if (SmartyYiiRoutes.isRoute(token)) return SmartyYiiRoutes.resolve(token)
                if (SmartyYiiTranslations.messageCategory(token) != null) return SmartyYiiTranslations.resolve(token)
                val arrow = SmartyPhpTypes.nextSignificant(token)
                if (arrow != null && SmartyPsiUtil.isType(arrow, DOUBLE_ARROW)) {
                    // 'pluginOptions' => ... in Select2::widget([...])
                    val widget = SmartyPhpWidgetConfig.widgetForKey(token)
                    if (widget.isNotEmpty()) {
                        return SmartyPhpWidgets.propertyTargets(widget, SmartyPsiUtil.unquote(token.text))
                    }
                }
                return useClassTargets(token)
            }

            val next = SmartyPhpTypes.nextSignificant(token)
            if ((SmartyPsiUtil.isType(token, TAG_NAME) || SmartyPsiUtil.isType(token, IDENTIFIER)) &&
                next != null && SmartyPsiUtil.isType(next, DOUBLE_COLON)
            ) {
                return SmartyPhpClasses.resolve(token) // Html in Html::a()
            }
            if (SmartyPsiUtil.isType(token, TAG_NAME)) {
                return SmartyPhpClasses.resolveUsed(token) // {Select2 ...}, {ActiveForm}...{/ActiveForm}
            }
            if (SmartyPsiUtil.isType(token, ATTR_NAME)) return widgetPropertyTargets(token)
            if (!SmartyPsiUtil.isType(token, IDENTIFIER)) return emptyList()
            val separator = SmartyPhpTypes.prevSignificant(token) ?: return emptyList()
            val call = next != null && SmartyPsiUtil.isType(next, LPAREN)

            if (SmartyPsiUtil.isType(separator, DOUBLE_COLON)) {
                val className = SmartyPhpClasses.classNameBefore(separator) ?: return emptyList()
                val result = ArrayList<PhpNamedElement>()
                for (c in SmartyPhpClasses.resolve(className)) {
                    val constant = if (call) null else c.findFieldByName(token.text, true)
                    if (constant != null) {
                        result.add(constant)
                        continue
                    }
                    c.findMethodByName(token.text)?.let { result.add(it) }
                }
                return result
            }
            if (!SmartyPsiUtil.isType(separator, ARROW)) return emptyList()
            val owner = SmartyPhpTypes.typeBeforeArrow(separator) ?: return emptyList()
            return SmartyPhpTypes.members(token.project, owner, token.text, call)
        }

        /** name= in {Select2 name='icon'}: the widget property, or its Yii setter (setName()). */
        fun widgetPropertyTargets(attrName: PsiElement): List<PhpNamedElement> {
            var tag: PsiElement? = attrName
            while (tag != null && !SmartyPsiUtil.isType(tag, TAG) && !SmartyPsiUtil.isType(tag, OPEN_TAG)) tag = tag.parent
            if (tag == null) return emptyList()
            val nameNode = SmartyPsiUtil.tagNameNode(tag.node) ?: return emptyList()
            val result = ArrayList<PhpNamedElement>()
            for (c in SmartyPhpClasses.resolveUsed(nameNode.psi)) {
                val field = c.findFieldByName(attrName.text, false)
                if (field != null) {
                    result.add(field)
                    continue
                }
                c.findMethodByName("set" + SmartyPhpTypes.capitalize(attrName.text))?.let { result.add(it) }
            }
            return result
        }

        /** {use class="yii\bootstrap5\Html"} */
        fun useClassTargets(string: PsiElement): List<PhpClass> {
            var tag: PsiElement? = string
            while (tag != null && !SmartyPsiUtil.isType(tag, TAG)) tag = tag.parent
            if (tag == null) return emptyList()
            val fqn = SmartyPhpClasses.useClassValue(tag.node)
            if (fqn == null || SmartyPsiUtil.unquote(string.text).replace("\\\\", "\\") != fqn.substring(1)) {
                return emptyList()
            }
            return ArrayList(SmartyPhpClasses.byFqn(string, fqn))
        }
    }
}
