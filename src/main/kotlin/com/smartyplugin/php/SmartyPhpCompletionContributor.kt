package com.smartyplugin.php

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lexer.SmartyTokenTypes.ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.ATTR_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * `$user->` completes the public fields, methods and Yii getter properties of the PHP class;
 * `Html::` the public static methods and constants of a class from `{use class="..."}`.
 */
class SmartyPhpCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        if (position.language !== SmartyLanguage) return
        if (SmartyPsiUtil.isType(position, STRING)) {
            val inToken = parameters.offset - position.textRange.startOffset
            val keyStart = minOf(1, inToken)
            val keyPrefix = position.text.substring(keyStart, maxOf(keyStart, minOf(inToken, position.textLength)))
            // {url route="adm|"}, Url::to(['adm|'])
            if (SmartyYiiRoutes.isRoute(position)) {
                val routes = result.withPrefixMatcher(keyPrefix)
                SmartyYiiRoutes.complete(position, routes)
                routes.stopHere()
                return
            }
            // 'Ic|'|t:'category', Yii::t('category', 'Ic|')
            val category = SmartyYiiTranslations.messageCategory(position)
            if (category != null) {
                val messages = result.withPrefixMatcher(keyPrefix)
                SmartyYiiTranslations.completeMessages(position, category, messages)
                messages.stopHere()
                return
            }
            if (SmartyYiiTranslations.isCategory(position)) {
                val categories = result.withPrefixMatcher(keyPrefix)
                SmartyYiiTranslations.completeCategories(position, categories)
                categories.stopHere()
                return
            }
            // a key of a widget configuration: Select2::widget(['pluginNa|
            val widget = SmartyPhpWidgetConfig.widgetForKey(position)
            if (widget.isEmpty()) return
            val keys = result.withPrefixMatcher(keyPrefix)
            SmartyPhpWidgetConfig.completeKeys(position, widget, position.project, keys)
            keys.stopHere()
            return
        }
        val name = SmartyPsiUtil.isType(position, IDENTIFIER) || SmartyPsiUtil.isType(position, ATTR_NAME)
        if (!name) return
        val separator = SmartyPhpTypes.prevSignificant(position) ?: return

        val caretInToken = parameters.offset - position.textRange.startOffset
        val prefix = position.text.substring(0, maxOf(0, minOf(caretInToken, position.textLength)))
        val rs = result.withPrefixMatcher(prefix)
        val project = position.project

        if (SmartyPsiUtil.isAttributeNamePosition(position)) {
            val widget = SmartyPhpWidgets.widgetClasses(position)
            if (widget.isNotEmpty()) {
                SmartyPhpWidgets.completeAttributes(position, widget, project, rs)
                rs.stopHere()
            }
            return
        }
        if (!SmartyPsiUtil.isType(position, IDENTIFIER)) return

        if (SmartyPsiUtil.isType(separator, DOUBLE_COLON)) {
            val className = SmartyPhpClasses.classNameBefore(separator) ?: return
            completeStatic(SmartyPhpClasses.resolve(className), project, rs)
            rs.stopHere()
            return
        }
        if (!SmartyPsiUtil.isType(separator, ARROW)) return
        val owner = SmartyPhpTypes.typeBeforeArrow(separator) ?: return
        val seen = HashSet<String>()
        for (c in SmartyPhpTypes.classesOf(project, owner)) {
            for (f in c.fields) {
                if (f.isConstant || !f.modifier.isPublic || f.modifier.isStatic) continue
                if (seen.add(f.name)) rs.addElement(property(f.name, f.type.global(project), c))
            }
            for (m in c.methods) {
                if (!m.access.isPublic || m.isStatic || m.name.startsWith("__")) continue
                if (seen.add(m.name + "()")) {
                    val hasParams = m.parameters.isNotEmpty()
                    rs.addElement(
                        PrioritizedLookupElement.withPriority(
                            LookupElementBuilder.create(m, m.name)
                                .withIcon(AllIcons.Nodes.Method)
                                .withTailText(if (hasParams) "(…)" else "()", true)
                                .withTypeText(SmartyPhpTypes.presentable(m.type.global(project)), true)
                                .withInsertHandler(parentheses(hasParams)),
                            -1.0,
                        ),
                    )
                }
                // Yii: getFullName() is readable as ->fullName
                val n = m.name
                if (n.length > 3 && n.startsWith("get") && Character.isUpperCase(n[3]) && requiredParameters(m) == 0) {
                    val prop = "${Character.toLowerCase(n[3])}${n.substring(4)}"
                    if (seen.add(prop)) rs.addElement(property(prop, m.type.global(project), c))
                }
            }
        }
        rs.stopHere()
    }

    private companion object {
        /** Html:: -> public static methods and public constants. */
        fun completeStatic(classes: List<PhpClass>, project: Project, rs: CompletionResultSet) {
            val seen = HashSet<String>()
            for (c in classes) {
                for (m in c.methods) {
                    if (!m.isStatic || !m.access.isPublic || m.name.startsWith("__")) continue
                    if (!seen.add(m.name + "()")) continue
                    val hasParams = m.parameters.isNotEmpty()
                    rs.addElement(
                        LookupElementBuilder.create(m, m.name)
                            .withIcon(AllIcons.Nodes.Method)
                            .withTailText(if (hasParams) "(…)" else "()", true)
                            .withTypeText(SmartyPhpTypes.presentable(m.type.global(project)), true)
                            .withInsertHandler(parentheses(hasParams)),
                    )
                }
                for (f in c.fields) {
                    if (!f.isConstant || !f.modifier.isPublic || !seen.add(f.name)) continue
                    rs.addElement(
                        LookupElementBuilder.create(f, f.name)
                            .withIcon(AllIcons.Nodes.Constant)
                            .withTypeText(SmartyPhpTypes.presentable(f.type.global(project)), true),
                    )
                }
            }
        }

        fun property(name: String, type: PhpType, owner: PhpClass): LookupElement =
            LookupElementBuilder.create(name)
                .withIcon(AllIcons.Nodes.Property)
                .withTypeText(SmartyPhpTypes.presentable(type), true)
                .withTailText(" " + owner.name, true)

        fun requiredParameters(m: Method): Int = m.parameters.count { !it.isOptional }

        fun parentheses(hasParams: Boolean): InsertHandler<LookupElement> = InsertHandler { context, _ ->
            val doc = context.document
            val tail = context.tailOffset
            if (tail < doc.textLength && doc.charsSequence[tail] == '(') return@InsertHandler
            doc.insertString(tail, "()")
            context.editor.caretModel.moveToOffset(tail + if (hasParams) 1 else 2)
        }
    }
}
