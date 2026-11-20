package com.smartyplugin.editor

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lang.SmartyFileViewProvider
import com.smartyplugin.lang.SmartyTags
import com.smartyplugin.lexer.SmartyTokenTypes.MODIFIER_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.psi.SmartyPsiUtil

/** Hover / Quick Documentation for Smarty's own tags (`{foreach}`, `{registerJs}`) and modifiers (`|escape`). */
class SmartyDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        if (file.viewProvider !is SmartyFileViewProvider) return null
        val token = SmartyPsiUtil.smartyElementAt(file, targetOffset) ?: return null
        return when {
            SmartyPsiUtil.isType(token, TAG_NAME) && SmartyTags.tag(token.text) != null -> token
            SmartyPsiUtil.isType(token, MODIFIER_NAME) && modifier(token.text) != null -> token
            else -> null
        }
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null
        if (SmartyPsiUtil.isType(element, TAG_NAME)) {
            val tag = SmartyTags.tag(element.text) ?: return null
            val d = SmartyDelimiters.of(element)
            val signature = buildString {
                append(d.left).append(tag.name)
                tag.attributes.forEach { append(' ').append(it).append("=…") }
                append(d.right)
                if (tag.kind == SmartyTags.Kind.BLOCK) append(" … ").append(d.closeTag(tag.name))
            }
            return doc(signature, tag.description, "${tag.source} ${kind(tag.kind)}")
        }
        if (SmartyPsiUtil.isType(element, MODIFIER_NAME)) {
            val modifier = modifier(element.text) ?: return null
            val signature = "|" + modifier.name + if (modifier.signature.isEmpty()) "" else ":" + modifier.signature
            val description = if (modifier.source == "PHP") "PHP function used as a modifier" else null
            return doc(signature, description, "${modifier.source} modifier")
        }
        return null
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null
        if (SmartyPsiUtil.isType(element, TAG_NAME)) return SmartyTags.tag(element.text)?.description
        return null
    }

    private fun modifier(name: String) = SmartyTags.allModifiers().firstOrNull { it.name == name }

    private fun kind(kind: SmartyTags.Kind) = when (kind) {
        SmartyTags.Kind.BLOCK -> "block tag"
        SmartyTags.Kind.MIDDLE -> "tag inside a block"
        SmartyTags.Kind.INLINE -> "tag"
    }

    private fun doc(signature: String, description: String?, source: String): String = buildString {
        append(DocumentationMarkup.DEFINITION_START).append(StringUtil.escapeXmlEntities(signature))
            .append(DocumentationMarkup.DEFINITION_END)
        if (description != null) {
            append(DocumentationMarkup.CONTENT_START).append(StringUtil.escapeXmlEntities(description))
                .append(DocumentationMarkup.CONTENT_END)
        }
        append(DocumentationMarkup.SECTIONS_START)
        append(DocumentationMarkup.SECTION_HEADER_START).append("Source:").append(DocumentationMarkup.SECTION_SEPARATOR)
            .append(StringUtil.escapeXmlEntities(source)).append(DocumentationMarkup.SECTION_END)
        append(DocumentationMarkup.SECTIONS_END)
    }
}
