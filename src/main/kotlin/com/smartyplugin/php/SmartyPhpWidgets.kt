package com.smartyplugin.php

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * Yii widgets used as tags (`{Select2 name='x' ...}`, `{ActiveForm id='f'}...{/ActiveForm}`):
 * their attributes are the widget's public properties and setters.
 */
internal object SmartyPhpWidgets {

    fun widgetClasses(inside: PsiElement): List<PhpClass> {
        val tag = SmartyPsiUtil.enclosingAttributeTag(inside)
        val name = if (tag == null) null else SmartyPsiUtil.tagNameNode(tag)
        return if (name == null) emptyList() else SmartyPhpClasses.resolveUsed(name.psi)
    }

    /** A configurable widget property: a public field, or a Yii setter (setTheme() configures "theme"). */
    data class Property(val name: String, val type: String?, val owner: PhpClass, val element: PhpNamedElement)

    fun properties(classes: List<PhpClass>, project: Project): List<Property> {
        val result = ArrayList<Property>()
        val seen = HashSet<String>()
        for (c in classes) {
            for (f in c.fields) {
                if (f.isConstant || !f.modifier.isPublic || f.modifier.isStatic) continue
                if (!seen.add(f.name)) continue
                result.add(Property(f.name, SmartyPhpTypes.presentable(f.type.global(project)), c, f))
            }
            for (m in c.methods) {
                val n = m.name
                if (n.length <= 3 || !n.startsWith("set") || !Character.isUpperCase(n[3])) continue
                if (m.isStatic || !m.access.isPublic || m.parameters.isEmpty()) continue
                val prop = "${Character.toLowerCase(n[3])}${n.substring(4)}"
                if (!seen.add(prop)) continue
                val p = m.parameters[0]
                result.add(Property(prop, SmartyPhpTypes.presentable(p.type.global(project)), c, m))
            }
        }
        return result
    }

    /** The property a name configures, for Ctrl+Click: the field, else the setter. */
    fun propertyTargets(classes: List<PhpClass>, name: String): List<PhpNamedElement> {
        val result = ArrayList<PhpNamedElement>()
        for (c in classes) {
            val field = c.findFieldByName(name, false)
            if (field != null) {
                result.add(field)
                continue
            }
            c.findMethodByName("set" + SmartyPhpTypes.capitalize(name))?.let { result.add(it) }
        }
        return result
    }

    fun completeAttributes(position: PsiElement, classes: List<PhpClass>, project: Project, rs: CompletionResultSet) {
        val present = presentAttributes(position)
        for (p in properties(classes, project)) {
            if (p.name in present) continue
            rs.addElement(attribute(LookupElementBuilder.create(p.element, p.name), p.name, p.type, p.owner))
        }
    }

    private fun presentAttributes(position: PsiElement): Set<String> {
        val names = HashSet<String>()
        val tag = SmartyPsiUtil.enclosingAttributeTag(position) ?: return names
        var c = tag.firstChildNode
        while (c != null) {
            if (c.elementType === ATTRIBUTE) {
                SmartyPsiUtil.attributeName(c)?.let { names.add(it) }
            }
            c = c.treeNext
        }
        return names
    }

    private fun attribute(builder: LookupElementBuilder, name: String, type: String?, owner: PhpClass): LookupElementBuilder =
        builder.withPresentableText(name)
            .withIcon(AllIcons.Nodes.Property)
            .withTypeText(type, true)
            .withTailText(" " + owner.name, true)
            .withInsertHandler { context, _ ->
                val doc = context.document
                val tail = context.tailOffset
                if (tail >= doc.textLength || doc.charsSequence[tail] != '=') {
                    doc.insertString(tail, "=")
                }
                context.editor.caretModel.moveToOffset(tail + 1)
            }
}
