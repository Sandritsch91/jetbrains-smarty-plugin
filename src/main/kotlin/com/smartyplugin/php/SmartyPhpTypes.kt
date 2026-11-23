package com.smartyplugin.php

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lexer.SmartyTokenTypes.ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.KEYWORD
import com.smartyplugin.lexer.SmartyTokenTypes.LDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.RDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.RPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE
import com.smartyplugin.lexer.SmartyTokenTypes.WHITE_SPACE
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.parser.SmartyElementTypes.BLOCK
import com.smartyplugin.psi.SmartyPsiUtil
import java.util.Locale

/** PHP types of Smarty expressions: `$user`, `$item` of a foreach, `$user->profile->name`. */
internal object SmartyPhpTypes {

    // ------------------------------------------------------------------ expressions

    /** Type of the expression that ends right before `arrow` ("->"), or null. */
    fun typeBeforeArrow(arrow: PsiElement): PhpType? {
        val prev = prevSignificant(arrow) ?: return null
        return typeEndingAt(prev)
    }

    /** Type of the Smarty expression whose last token is `last`: $a, $a->b, $a->b(). */
    fun typeEndingAt(last: PsiElement): PhpType? {
        if (SmartyPsiUtil.isType(last, VARIABLE)) return typeOfVariable(last)
        var name: PsiElement? = last
        var call = false
        if (SmartyPsiUtil.isType(last, RPAREN)) {
            val open = matchingOpenParen(last)
            name = if (open == null) null else prevSignificant(open)
            call = true
        }
        if (name == null || !SmartyPsiUtil.isType(name, IDENTIFIER)) return null
        val ownerArrow = prevSignificant(name)
        if (ownerArrow != null && SmartyPsiUtil.isType(ownerArrow, DOUBLE_COLON)) {
            // Html::method()
            val className = SmartyPhpClasses.classNameBefore(ownerArrow) ?: return null
            val owner = PhpType()
            for (c in SmartyPhpClasses.resolve(className)) owner.add(c.fqn)
            return memberType(name.project, owner, name.text, call)
        }
        if (ownerArrow == null || !SmartyPsiUtil.isType(ownerArrow, ARROW)) return null
        val owner = typeBeforeArrow(ownerArrow) ?: return null
        return memberType(name.project, owner, name.text, call)
    }

    /** Type of a $variable token: passed in from PHP or another template, or the item of an enclosing foreach. */
    fun typeOfVariable(variable: PsiElement): PhpType? {
        val name = variable.text.substring(1)
        foreachItemType(variable, name)?.let { return it }
        blockWidgetType(variable, name)?.let { return it }
        return passedType(variable.containingFile, name) ?: SmartyPhpYii.implicitType(variable.project, name)
    }

    /** {ActiveForm assign="form"}...{$form->field(...)}...{/ActiveForm}: Yii assigns the widget instance. */
    private fun blockWidgetType(variable: PsiElement, name: String): PhpType? {
        var block = variable.node.treeParent
        while (block != null) {
            if (block.elementType === BLOCK) {
                val open = SmartyPsiUtil.openTag(block)
                if (open != null && name == attribute(open, "assign")) {
                    val tagName = SmartyPsiUtil.tagNameNode(open)
                    if (tagName != null) {
                        val type = PhpType()
                        for (c in SmartyPhpClasses.resolveUsed(tagName.psi)) type.add(c.fqn)
                        if (type.types.isNotEmpty()) return type
                    }
                }
            }
            block = block.treeParent
        }
        return null
    }

    private fun attribute(tag: ASTNode, attributeName: String): String? {
        var c = tag.firstChildNode
        while (c != null) {
            if (c.elementType === ATTRIBUTE && attributeName == SmartyPsiUtil.attributeName(c)) {
                return SmartyPsiUtil.attributeValue(c)
            }
            c = c.treeNext
        }
        return null
    }

    /** Variables passed in by PHP render / assign calls and by {include} tags of other templates. */
    fun allPassed(template: PsiFile): List<SmartyPhpRenderCalls.Passed> =
        SmartyPhpRenderCalls.passedTo(template) +
            com.smartyplugin.psi.SmartyIncludes.passedTo(template).map { SmartyPhpRenderCalls.Passed(it.name, it.target, it.value) }

    fun passedType(template: PsiFile, name: String): PhpType? {
        var result: PhpType? = null
        for (p in allPassed(template)) {
            if (p.name != name) continue
            val type = typeOf(p) ?: continue
            result = if (result == null) type else PhpType().add(result).add(type)
        }
        return result
    }

    /** PHP value: its PHP type. Value in a template ({$this->render('_form', ['model' => $model])}): its type there. */
    fun typeOf(passed: SmartyPhpRenderCalls.Passed): PhpType? {
        val value = passed.value
        if (value is PhpTypedElement) return value.type.global(value.project)
        if (value == null || value.language !== SmartyLanguage) return null
        // templates can render each other in a cycle
        return RecursionManager.doPreventingRecursion(value, false) { typeEndingAt(value) }
    }

    /** {foreach $items as $item}, {foreach $items as $key => $item}, {foreach from=$items item=item} */
    private fun foreachItemType(variable: PsiElement, name: String): PhpType? {
        var block = variable.node.treeParent
        while (block != null) {
            if (block.elementType === BLOCK && SmartyPsiUtil.blockName(block) == "foreach") {
                val open = SmartyPsiUtil.openTag(block)
                if (open != null) {
                    val source = foreachSource(open, name)
                    if (source != null && source !== variable) {
                        val sourceType = typeOfVariable(source)
                        if (sourceType != null) return elementType(sourceType)
                    }
                }
            }
            block = block.treeParent
        }
        return null
    }

    /** The $items token of a foreach whose item variable is `name`. */
    private fun foreachSource(openTag: ASTNode, name: String): PsiElement? {
        val tokens = ArrayList<ASTNode>()
        collectLeaves(openTag, tokens)
        // shorthand: $items as [$key =>] $item
        for (i in tokens.indices) {
            val t = tokens[i]
            if (t.elementType === KEYWORD && t.text == "as" && i > 0) {
                val last = if (tokens[tokens.size - 1].elementType === RDELIM) tokens[tokens.size - 2] else tokens[tokens.size - 1]
                val source = tokens[i - 1]
                if (last.elementType === VARIABLE && last.text == "$$name" && source.elementType === VARIABLE) {
                    return source.psi
                }
                return null
            }
        }
        // attributes: from=$items item=item
        var from: ASTNode? = null
        var item: String? = null
        var c = openTag.firstChildNode
        while (c != null) {
            if (c.elementType === ATTRIBUTE) {
                val attr = SmartyPsiUtil.attributeName(c)
                if (attr == "item") item = SmartyPsiUtil.attributeValue(c)
                if (attr == "from") {
                    var v = c.firstChildNode
                    while (v != null) {
                        if (v.elementType === VARIABLE) from = v
                        v = v.treeNext
                    }
                }
            }
            c = c.treeNext
        }
        return if (name == item && from != null) from.psi else null
    }

    private fun collectLeaves(node: ASTNode, out: MutableList<ASTNode>) {
        var c = node.firstChildNode
        while (c != null) {
            if (c.firstChildNode != null) {
                collectLeaves(c, out)
            } else if (c.elementType !== WHITE_SPACE && c.elementType !== TokenType.WHITE_SPACE) {
                out.add(c)
            }
            c = c.treeNext
        }
    }

    // ------------------------------------------------------------------ PHP classes and members

    fun classesOf(project: Project, type: PhpType): List<PhpClass> {
        val index = PhpIndex.getInstance(project)
        val classes = ArrayList<PhpClass>()
        for (fqn in type.types) {
            if (!fqn.startsWith("\\") || fqn.endsWith("[]")) continue
            classes.addAll(index.getAnyByFQN(fqn))
        }
        return classes
    }

    fun elementType(type: PhpType): PhpType {
        val result = PhpType()
        for (t in type.types) {
            if (t.endsWith("[]")) result.add(t.substring(0, t.length - 2))
        }
        return result
    }

    /** Members a "->name" may refer to: field, method, or Yii getter (getName() for ->name). */
    fun members(project: Project, owner: PhpType, name: String, call: Boolean): List<PhpNamedElement> {
        val result = LinkedHashSet<PhpNamedElement>()
        for (c in classesOf(project, owner)) {
            if (call) {
                c.findMethodByName(name)?.let { result.add(it) }
                continue
            }
            val f = c.findFieldByName(name, false)
            if (f != null) {
                // a documented property wins over its getter: "@property-read User $user" vs "getUser(): ActiveQuery"
                result.add(f)
                continue
            }
            c.findMethodByName("get" + capitalize(name))?.let { result.add(it) }
        }
        return ArrayList(result)
    }

    fun memberType(project: Project, owner: PhpType, name: String, call: Boolean): PhpType? {
        var result: PhpType? = null
        for (member in members(project, owner, name, call)) {
            val type = member.type.global(project)
            result = if (result == null) type else PhpType().add(result).add(type)
        }
        return result
    }

    fun capitalize(s: String): String =
        if (s.isEmpty()) s else s.substring(0, 1).uppercase(Locale.ROOT) + s.substring(1)

    /** "\app\models\User|null" -> "User|null" */
    fun presentable(type: PhpType?): String? {
        if (type == null || type.types.isEmpty()) return null
        val parts = ArrayList<String>()
        for (t in type.types) {
            if (t.startsWith("#")) continue // unresolved signatures
            val slash = t.lastIndexOf('\\')
            parts.add(if (slash >= 0) t.substring(slash + 1) else t)
        }
        return if (parts.isEmpty()) null else parts.joinToString("|")
    }

    // ------------------------------------------------------------------ tokens

    fun prevSignificant(element: PsiElement): PsiElement? {
        var leaf = PsiTreeUtil.prevLeaf(element)
        while (leaf != null && (SmartyPsiUtil.isType(leaf, WHITE_SPACE) || leaf.textLength == 0)) {
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return leaf
    }

    fun nextSignificant(element: PsiElement): PsiElement? {
        var leaf = PsiTreeUtil.nextLeaf(element)
        while (leaf != null && (SmartyPsiUtil.isType(leaf, WHITE_SPACE) || leaf.textLength == 0)) {
            leaf = PsiTreeUtil.nextLeaf(leaf)
        }
        return leaf
    }

    private fun matchingOpenParen(close: PsiElement): PsiElement? {
        var depth = 0
        var leaf: PsiElement? = close
        while (leaf != null) {
            if (SmartyPsiUtil.isType(leaf, RPAREN)) {
                depth++
            } else if (SmartyPsiUtil.isType(leaf, LPAREN)) {
                if (--depth == 0) return leaf
            } else if (SmartyPsiUtil.isType(leaf, LDELIM)) {
                return null
            }
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return null
    }
}
