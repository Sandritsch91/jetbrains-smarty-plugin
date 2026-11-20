package com.smartyplugin.php

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.smartyplugin.lexer.SmartyTokenTypes.ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.COMMA
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.LBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.LDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.RBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.RDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.RPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * The configuration array of a Yii widget, whose keys are the widget's properties:
 * - `{Select2::widget(['name' => 'x', ...])}`, `{ActiveForm::begin([...])}`
 * - `{$form->field($model, 'icon')->widget(Select2::class, [...])}` (also `Select2::className()`)
 *
 * Only the keys at the top level of the array count; nested arrays ('options' => [...]) configure something else.
 */
internal object SmartyPhpWidgetConfig {
    private val STATIC_FACTORIES = setOf("widget", "begin")

    /** The widget classes whose config array has `key` (a STRING token) as one of its keys, or empty. */
    fun widgetForKey(key: PsiElement): List<PhpClass> {
        val before = SmartyPhpTypes.prevSignificant(key)
        if (before == null || !(SmartyPsiUtil.isType(before, COMMA) || SmartyPsiUtil.isType(before, LBRACKET))) {
            return emptyList()
        }
        val open = if (SmartyPsiUtil.isType(before, LBRACKET)) before else enclosingOpener(before)
        if (open == null || !SmartyPsiUtil.isType(open, LBRACKET)) return emptyList()
        return widgetForArray(open)
    }

    /** `open` is the '[' of a widget configuration: the widget classes, else empty. */
    fun widgetForArray(open: PsiElement): List<PhpClass> {
        val p = SmartyPhpTypes.prevSignificant(open) ?: return emptyList()
        if (SmartyPsiUtil.isType(p, LPAREN)) {
            // Select2::widget([ / ActiveForm::begin([
            val method = SmartyPhpTypes.prevSignificant(p)
            if (method == null || !SmartyPsiUtil.isType(method, IDENTIFIER) || method.text !in STATIC_FACTORIES) {
                return emptyList()
            }
            val colons = SmartyPhpTypes.prevSignificant(method)
            if (colons == null || !SmartyPsiUtil.isType(colons, DOUBLE_COLON)) return emptyList()
            val className = SmartyPhpClasses.classNameBefore(colons) ?: return emptyList()
            return SmartyPhpClasses.resolve(className)
        }
        if (SmartyPsiUtil.isType(p, COMMA)) {
            // ->widget(Select2::class, [ / ->widget(Select2::className(), [
            var last = SmartyPhpTypes.prevSignificant(p)
            if (last != null && SmartyPsiUtil.isType(last, RPAREN)) {
                val lparen = SmartyPhpTypes.prevSignificant(last)
                last = if (lparen != null && SmartyPsiUtil.isType(lparen, LPAREN)) SmartyPhpTypes.prevSignificant(lparen) else null
                if (last == null || last.text != "className") return emptyList()
            } else if (last == null || last.text != "class") {
                return emptyList()
            }
            val colons = SmartyPhpTypes.prevSignificant(last)
            if (colons == null || !SmartyPsiUtil.isType(colons, DOUBLE_COLON)) return emptyList()
            val className = SmartyPhpClasses.classNameBefore(colons) ?: return emptyList()
            val paren = SmartyPhpTypes.prevSignificant(className)
            val method = if (paren == null) null else SmartyPhpTypes.prevSignificant(paren)
            val arrow = if (method == null) null else SmartyPhpTypes.prevSignificant(method)
            if (!SmartyPsiUtil.isType(paren, LPAREN) || method == null || method.text != "widget" ||
                !SmartyPsiUtil.isType(arrow, ARROW)
            ) {
                return emptyList()
            }
            return SmartyPhpClasses.resolve(className)
        }
        return emptyList()
    }

    /** The '[' or '(' that the element is directly inside of (within the tag), or null. */
    private fun enclosingOpener(from: PsiElement): PsiElement? {
        var depth = 0
        var leaf = PsiTreeUtil.prevLeaf(from)
        while (leaf != null) {
            if (SmartyPsiUtil.isType(leaf, LDELIM)) return null
            if (SmartyPsiUtil.isType(leaf, RBRACKET) || SmartyPsiUtil.isType(leaf, RPAREN)) {
                depth++
            } else if (SmartyPsiUtil.isType(leaf, LBRACKET) || SmartyPsiUtil.isType(leaf, LPAREN)) {
                if (depth == 0) return leaf
                depth--
            }
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return null
    }

    /** Keys already in the array that `key` belongs to. */
    private fun presentKeys(key: PsiElement): Set<String> {
        val keys = HashSet<String>()
        val before = SmartyPhpTypes.prevSignificant(key)
        val open = when {
            before != null && SmartyPsiUtil.isType(before, LBRACKET) -> before
            before == null -> null
            else -> enclosingOpener(before)
        } ?: return keys
        var depth = 0
        var leaf = SmartyPhpTypes.nextSignificant(open)
        while (leaf != null) {
            if (SmartyPsiUtil.isType(leaf, RDELIM)) break
            if (SmartyPsiUtil.isType(leaf, LBRACKET) || SmartyPsiUtil.isType(leaf, LPAREN)) {
                depth++
            } else if (SmartyPsiUtil.isType(leaf, RBRACKET) || SmartyPsiUtil.isType(leaf, RPAREN)) {
                if (depth == 0) break
                depth--
            } else if (depth == 0 && leaf !== key && SmartyPsiUtil.isType(leaf, STRING)) {
                val next = SmartyPhpTypes.nextSignificant(leaf)
                if (next != null && SmartyPsiUtil.isType(next, DOUBLE_ARROW)) keys.add(SmartyPsiUtil.unquote(leaf.text))
            }
            leaf = SmartyPhpTypes.nextSignificant(leaf)
        }
        return keys
    }

    /** 'pluginNa| -> 'pluginOptions' => | */
    fun completeKeys(key: PsiElement, classes: List<PhpClass>, project: Project, rs: CompletionResultSet) {
        val present = presentKeys(key)
        val quote = if (key.text.isEmpty()) '\'' else key.text[0]
        for (p in SmartyPhpWidgets.properties(classes, project)) {
            if (p.name in present) continue
            rs.addElement(
                LookupElementBuilder.create(p.element, p.name)
                    .withIcon(AllIcons.Nodes.Property)
                    .withTypeText(p.type, true)
                    .withTailText(" " + p.owner.name, true)
                    .withInsertHandler { context, _ ->
                        val doc = context.document
                        var tail = context.tailOffset
                        var text = doc.charsSequence
                        if (tail < text.length && text[tail] == quote) {
                            tail++
                        } else {
                            doc.insertString(tail, quote.toString())
                            tail++
                            text = doc.charsSequence
                        }
                        var next = tail
                        while (next < text.length && (text[next] == ' ' || text[next] == '\t')) next++
                        if (!(next + 1 < text.length && text[next] == '=' && text[next + 1] == '>')) {
                            doc.insertString(tail, " => ")
                            tail += 4
                        }
                        context.editor.caretModel.moveToOffset(tail)
                    },
            )
        }
    }
}
