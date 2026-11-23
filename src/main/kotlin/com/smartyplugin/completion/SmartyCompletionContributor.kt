package com.smartyplugin.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lang.SmartyIcons
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lang.SmartyTags
import com.smartyplugin.lang.SmartyPluginProvider
import com.smartyplugin.lang.SmartyPlugins
import com.smartyplugin.lang.SmartyVariableProvider
import com.smartyplugin.lexer.SmartyTokenTypes.AT
import com.smartyplugin.lexer.SmartyTokenTypes.ATTR_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.DOT
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.MODIFIER_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.SLASH
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.VARIABLE
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.psi.SmartyConfigVars
import com.smartyplugin.psi.SmartyInheritance
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * Completes tag names, closing tags, attributes, modifiers, variables and `$smarty.*` properties.
 */
class SmartyCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        if (position.language !== SmartyLanguage || position.node == null) return
        val type = position.node.elementType
        val caretInToken = parameters.offset - position.textRange.startOffset
        val prefix = position.text.substring(0, maxOf(0, minOf(caretInToken, position.textLength)))
        val rs = result.withPrefixMatcher(prefix)

        // {#ti<caret>#}: config variables of the loaded .conf files
        if (SmartyConfigVars.nameAt(position) != null) {
            completeConfigVars(parameters, position, result)
            return
        }
        // {block name="<caret>"}: the blocks of the layouts above
        val blockName = SmartyInheritance.asBlockName(position)
        if (blockName != null) {
            completeBlockNames(parameters, position, blockName, result)
            return
        }

        when {
            type === TAG_NAME -> {
                val prev = PsiTreeUtil.prevLeaf(position)
                if (SmartyPsiUtil.isType(prev, SLASH)) {
                    completeClosingTag(position, rs)
                } else {
                    completeTagNames(parameters.originalFile, parameters.offset, rs)
                }
                rs.stopHere()
            }
            type === MODIFIER_NAME -> {
                completeModifiers(position.project, rs)
                rs.stopHere()
            }
            type === VARIABLE -> {
                completeVariables(parameters.originalFile, parameters.offset, rs)
                rs.stopHere()
            }
            type === IDENTIFIER || type === ATTR_NAME -> {
                val prev = PsiTreeUtil.prevLeaf(position)
                if (SmartyPsiUtil.isType(prev, DOT)) {
                    val owner = PsiTreeUtil.prevLeaf(prev!!)
                    if (owner != null && owner.text == SMARTY) {
                        for (p in SmartyTags.SMARTY_VARIABLE_PROPERTIES) {
                            rs.addElement(LookupElementBuilder.create(p).withTypeText(SMARTY, true))
                        }
                        rs.stopHere()
                    }
                    return
                }
                if (SmartyPsiUtil.isType(prev, AT)) {
                    for (p in SmartyTags.LOOP_PROPERTIES) {
                        rs.addElement(LookupElementBuilder.create(p).withTypeText("loop property", true))
                    }
                    rs.stopHere()
                    return
                }
                completeAttributes(position, rs)
            }
        }
    }

    /** A custom plugin tag: `{name |}`, for a block `{name |}{/name}`. */
    private class CustomTagInsertHandler(private val block: Boolean) : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val doc = context.document
            val tail = context.tailOffset
            val d = SmartyDelimiters.of(context.project)
            if (startsWith(doc.charsSequence, tail, d.right)) {
                doc.insertString(tail, " ")
            } else {
                doc.insertString(tail, " " + d.right + if (block) d.closeTag(item.lookupString) else "")
            }
            context.editor.caretModel.moveToOffset(tail + 1)
        }
    }

    private class TagInsertHandler(private val tag: SmartyTags.Tag) : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val doc = context.document
            val tail = context.tailOffset
            val text = doc.charsSequence
            val d = SmartyDelimiters.of(context.project)
            val braceFollows = startsWith(text, tail, d.right)
            if (tag.takesArguments) {
                if (!braceFollows && (tail >= text.length || text[tail] != ' ')) {
                    doc.insertString(tail, " ")
                }
                context.editor.caretModel.moveToOffset(tail + 1)
                if (tag.attributes.isNotEmpty()) {
                    AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
                }
                return
            }
            var insert = if (braceFollows) "" else d.right
            val caret = tail + d.right.length
            if (tag.kind == SmartyTags.Kind.BLOCK) {
                insert += d.closeTag(tag.name)
            }
            doc.insertString(tail, insert)
            context.editor.caretModel.moveToOffset(caret)
        }
    }

    private companion object {
        const val SMARTY = "\$smarty"
        const val DUMMY = "IntellijIdeaRulezzz"
        val NAME_ATTRIBUTES_DEFINING_VARIABLES = setOf("var", "item", "key", "assign")
        val IDENTIFIER_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")

        fun completeConfigVars(parameters: CompletionParameters, position: PsiElement, result: CompletionResultSet) {
            val start = position.textRange.startOffset + if (position.text.startsWith("#")) 1 else 0
            val rs = result.withPrefixMatcher(position.containingFile.text.substring(start, maxOf(start, parameters.offset)))
            val closed = position.text.startsWith("#")
            val seen = HashSet<String>()
            for (v in SmartyConfigVars.available(parameters.originalFile)) {
                if (!seen.add(v.name)) continue
                rs.addElement(
                    LookupElementBuilder.create(v.name)
                        .withIcon(AllIcons.Nodes.Variable)
                        .withTailText(" = " + v.value, true)
                        .withTypeText(v.section?.let { "[$it] " }.orEmpty() + v.target.containingFile.name, true)
                        .withInsertHandler { context, _ ->
                            val doc = context.document
                            val tail = context.tailOffset
                            if (!closed && (tail >= doc.textLength || doc.charsSequence[tail] != '#')) doc.insertString(tail, "#")
                            context.editor.caretModel.moveToOffset(tail + 1)
                        },
                )
            }
            rs.stopHere()
        }

        fun completeBlockNames(parameters: CompletionParameters, position: PsiElement, name: PsiElement, result: CompletionResultSet) {
            val quoted = name.text.startsWith("\"") || name.text.startsWith("'")
            val start = name.textRange.startOffset + if (quoted) 1 else 0
            val prefix = position.containingFile.text.substring(start, maxOf(start, parameters.offset))
            val rs = result.withPrefixMatcher(prefix)
            val seen = HashSet<String>()
            for (layout in SmartyInheritance.parents(parameters.originalFile)) {
                for (block in SmartyInheritance.blocks(layout).keys) {
                    if (seen.add(block)) {
                        rs.addElement(LookupElementBuilder.create(block).withIcon(SmartyIcons.FILE).withTypeText(layout.name, true))
                    }
                }
            }
            rs.stopHere()
        }

        // ------------------------------------------------------------ tags

        fun completeTagNames(file: PsiFile, caret: Int, rs: CompletionResultSet) {
            val seen = LinkedHashSet<String>()
            val d = SmartyDelimiters.of(file.project)
            for (tag in SmartyTags.allTags()) {
                seen.add(tag.name)
                val element = LookupElementBuilder.create(tag.name)
                    .withIcon(SmartyIcons.FILE)
                    .withTailText(if (tag.kind == SmartyTags.Kind.BLOCK) " …" + d.closeTag(tag.name) else null, true)
                    .withTypeText(tag.description, true)
                    .withInsertHandler(TagInsertHandler(tag))
                val priority = if (tag.source == "Yii2") -1.0 else 0.0
                rs.addElement(PrioritizedLookupElement.withPriority(element, priority))
            }
            // the project's plugins: function.x.php, block.x.php, registerPlugin()
            for (p in SmartyPlugins.tags(file.project)) {
                if (!seen.add(p.name)) continue
                val block = p.kind == SmartyPluginProvider.Kind.BLOCK
                rs.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(p.name)
                            .withIcon(SmartyIcons.FILE)
                            .withTailText(if (block) " …" + d.closeTag(p.name) else null, true)
                            .withTypeText(p.source, true)
                            .withInsertHandler(CustomTagInsertHandler(block)),
                        0.5,
                    ),
                )
            }
            // custom tags and plugins already used in this file
            val smarty = SmartyPsiUtil.smartyFile(file) ?: return
            PsiTreeUtil.processElements(smarty) { e ->
                if (SmartyPsiUtil.isType(e, TAG_NAME) && !e.textRange.containsOffset(caret)) {
                    val name = e.text
                    if (!name.contains(DUMMY) && seen.add(name)) {
                        rs.addElement(LookupElementBuilder.create(name).withTypeText("used in file", true))
                    }
                }
                true
            }
        }

        fun completeClosingTag(position: PsiElement, rs: CompletionResultSet) {
            var block = SmartyPsiUtil.innermostUnclosedBlock(position.node)
            var priority = 100
            val seen = LinkedHashSet<String>()
            while (block != null) {
                val name = SmartyPsiUtil.blockName(block)
                if (name != null && seen.add(name)) {
                    rs.addElement(
                        PrioritizedLookupElement.withPriority(
                            LookupElementBuilder.create(name).withIcon(SmartyIcons.FILE).withInsertHandler(CLOSE_BRACE),
                            (priority--).toDouble(),
                        ),
                    )
                }
                block = SmartyPsiUtil.innermostUnclosedBlock(block.treeParent)
            }
            for (tag in SmartyTags.allTags()) {
                if (tag.kind == SmartyTags.Kind.BLOCK && seen.add(tag.name)) {
                    rs.addElement(LookupElementBuilder.create(tag.name).withInsertHandler(CLOSE_BRACE))
                }
            }
        }

        val CLOSE_BRACE = InsertHandler<LookupElement> { context, _ ->
            val doc = context.document
            val tail = context.tailOffset
            val right = SmartyDelimiters.of(context.project).right
            if (!startsWith(doc.charsSequence, tail, right)) doc.insertString(tail, right)
            context.editor.caretModel.moveToOffset(tail + right.length)
        }

        fun startsWith(text: CharSequence, offset: Int, s: String): Boolean =
            offset + s.length <= text.length && text.subSequence(offset, offset + s.length).toString() == s

        // ------------------------------------------------------------ attributes

        fun completeAttributes(position: PsiElement, rs: CompletionResultSet) {
            var tag = SmartyPsiUtil.enclosingTag(position.node) ?: return
            // only at attribute-name positions: the name of an attribute, or a new word after the tag name or a value
            val parent = position.node.treeParent
            val attributeName = parent?.elementType === ATTRIBUTE && parent.firstChildNode === position.node
            if (!attributeName && !SmartyPsiUtil.isAttributeNamePosition(position)) return
            SmartyPsiUtil.enclosingAttributeTag(position)?.let { tag = it }
            val info = SmartyTags.tag(SmartyPsiUtil.tagName(tag)) ?: return
            val present = LinkedHashSet<String>()
            var c: ASTNode? = tag.firstChildNode
            while (c != null) {
                if (c.elementType === ATTRIBUTE) {
                    SmartyPsiUtil.attributeName(c)?.let { present.add(it) }
                }
                c = c.treeNext
            }
            for (attr in info.attributes) {
                if (attr in present) continue
                rs.addElement(
                    LookupElementBuilder.create(attr)
                        .withIcon(AllIcons.Nodes.Property)
                        .withTypeText(info.name, true)
                        .withInsertHandler { context, _ ->
                            val doc = context.document
                            val tail = context.tailOffset
                            if (tail >= doc.textLength || doc.charsSequence[tail] != '=') {
                                doc.insertString(tail, "=")
                            }
                            context.editor.caretModel.moveToOffset(tail + 1)
                        },
                )
            }
        }

        // ------------------------------------------------------------ modifiers

        fun completeModifiers(project: com.intellij.openapi.project.Project, rs: CompletionResultSet) {
            // Smarty's own plugins (vendor/smarty/.../modifier.truncate.php) are listed as built-ins
            val seen = SmartyTags.allModifiers().mapTo(HashSet()) { it.name }
            for (p in SmartyPlugins.modifiers(project)) {
                if (!seen.add(p.name)) continue
                rs.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(p.name).withIcon(AllIcons.Nodes.Function).withTypeText(p.source, true),
                        1.0,
                    ),
                )
            }
            for (m in SmartyTags.allModifiers()) {
                val element = LookupElementBuilder.create(m.name)
                    .withIcon(AllIcons.Nodes.Function)
                    .withTailText(if (m.signature.isEmpty()) null else " " + m.signature, true)
                    .withTypeText(m.source, true)
                rs.addElement(PrioritizedLookupElement.withPriority(element, if (m.source == "PHP") -1.0 else 0.0))
            }
        }

        // ------------------------------------------------------------ variables

        fun completeVariables(file: PsiFile, caret: Int, rs: CompletionResultSet) {
            val names = LinkedHashSet<String>()
            names.add(SMARTY)
            val smarty = SmartyPsiUtil.smartyFile(file)
            if (smarty != null) {
                PsiTreeUtil.processElements(smarty) { e ->
                    val node = e.node ?: return@processElements true
                    if (node.elementType === VARIABLE && !e.textRange.containsOffset(caret)) {
                        val text = node.text
                        if (text.length > 1 && !text.contains(DUMMY)) names.add(text)
                    } else if (node.elementType === ATTRIBUTE) {
                        val attr = SmartyPsiUtil.attributeName(node)
                        if (attr != null && attr in NAME_ATTRIBUTES_DEFINING_VARIABLES) {
                            val value = SmartyPsiUtil.attributeValue(node)
                            if (value != null && value.matches(IDENTIFIER_PATTERN)) names.add("$" + value)
                        }
                    }
                    true
                }
            }
            // passed in from outside (PHP render/assign calls): listed first, with their type
            val external = LinkedHashMap<String, String>()
            if (smarty != null) {
                for (v in SmartyVariableProvider.all(smarty)) {
                    val type = v.type ?: ""
                    external.merge("$" + v.name, type) { a, b -> if (a.isEmpty()) b else a }
                }
            }
            for ((name, type) in external) {
                val element = LookupElementBuilder.create(name)
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(if (type.isEmpty()) "passed in" else type, true)
                    .bold()
                rs.addElement(PrioritizedLookupElement.withPriority(element, 10.0))
            }
            for (name in names) {
                if (external.containsKey(name)) continue
                rs.addElement(
                    LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Variable)
                        .withTypeText(if (name == SMARTY) "reserved" else null, true),
                )
            }
        }
    }
}
