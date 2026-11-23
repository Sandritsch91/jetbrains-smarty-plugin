package com.smartyplugin.php

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.smartyplugin.lexer.SmartyTokenTypes.COLON
import com.smartyplugin.lexer.SmartyTokenTypes.COMMA
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.MODIFIER_NAME
import com.smartyplugin.lexer.SmartyTokenTypes.PIPE
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.psi.SmartyPsiUtil
import javax.swing.Icon

/**
 * Yii translations in templates: `{'Icon'|t:'hq-re/notifications'}` and `{Yii::t('hq-re/notifications', 'Icon')}`.
 *
 * Messages are read from
 * - Gettext files (`GettextMessageSource`): every `*.po` file, the category is the `msgctxt`, the language the folder
 * - PHP message files (`PhpMessageSource`): `messages/<language>/<category>.php` returning `['message' => 'translation']`
 */
internal object SmartyYiiTranslations {
    data class Message(
        val category: String,
        val message: String,
        val language: String,
        val translation: String,
        val target: PsiElement,
    )

    private val LANGUAGE = Regex("^[a-z]{2,3}([-_][A-Za-z]{2,4})?$")

    fun messages(project: Project): List<Message> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            val value = if (DumbService.isDumb(project)) emptyList() else computeMessages(project)
            CachedValueProvider.Result.create(
                value, PsiModificationTracker.getInstance(project), DumbService.getInstance(project).modificationTracker,
            )
        }

    private fun computeMessages(project: Project): List<Message> {
        val scope = GlobalSearchScope.allScope(project)
        val psi = PsiManager.getInstance(project)
        val result = ArrayList<Message>()
        for (vf in FilenameIndex.getAllFilesByExt(project, "po", scope)) {
            val file = psi.findFile(vf) ?: continue
            readPo(file, vf.parent?.name ?: "", result)
        }
        for (dir in FilenameIndex.getVirtualFilesByName("messages", scope)) {
            if (!dir.isDirectory) continue
            for (languageDir in dir.children) {
                if (!languageDir.isDirectory || !LANGUAGE.matches(languageDir.name)) continue
                readPhpMessages(psi, languageDir, result)
            }
        }
        return result
    }

    // ------------------------------------------------------------------ .po files

    private fun readPo(file: PsiFile, language: String, out: MutableList<Message>) {
        val text = file.viewProvider.contents
        var context: String? = null
        var id: StringBuilder? = null
        var idOffset = 0
        var str: StringBuilder? = null
        var current: StringBuilder? = null

        fun flush() {
            val ctx = context
            val msg = id?.toString()
            if (ctx != null && !msg.isNullOrEmpty()) {
                out.add(Message(ctx, msg, language, str?.toString() ?: "", com.smartyplugin.psi.SmartyFileLocation(file, idOffset, "$language: ${str ?: ""}")))
            }
            context = null; id = null; str = null; current = null
        }

        var lineStart = 0
        while (lineStart < text.length) {
            var lineEnd = StringUtil.indexOf(text, '\n', lineStart)
            if (lineEnd < 0) lineEnd = text.length
            val line = text.subSequence(lineStart, lineEnd).toString().trim()
            when {
                line.startsWith("msgctxt ") -> {
                    if (id != null) flush()
                    context = poString(line.substring(8))
                    current = null
                }
                line.startsWith("msgid ") -> {
                    if (id != null) flush() // previous entry without a blank line; its msgctxt doesn't carry over
                    id = StringBuilder(poString(line.substring(6)))
                    idOffset = lineStart + text.subSequence(lineStart, lineEnd).indexOf("msgid")
                    current = id
                }
                line.startsWith("msgstr ") || line.startsWith("msgstr[0] ") -> {
                    str = StringBuilder(poString(line.substringAfter(' ')))
                    current = str
                }
                line.startsWith("\"") -> current?.append(poString(line))
                line.isEmpty() -> if (id != null && str != null) flush()
                line.startsWith("msgid_plural") || line.startsWith("msgstr[") -> current = null
            }
            lineStart = lineEnd + 1
        }
        if (id != null) flush()
    }

    /** `"a \"b\"\n"` -> `a "b"` + newline */
    private fun poString(quoted: String): String {
        val s = quoted.trim()
        if (s.length < 2 || !s.startsWith("\"") || !s.endsWith("\"")) return ""
        return StringUtil.unescapeStringCharacters(s.substring(1, s.length - 1))
    }

    /** A `msgid` line in a .po file, as a navigation target. */
    // ------------------------------------------------------------------ PHP message files

    private fun readPhpMessages(psi: PsiManager, languageDir: VirtualFile, out: MutableList<Message>) {
        VfsUtilCore.iterateChildrenRecursively(languageDir, null) { vf ->
            if (!vf.isDirectory && vf.extension == "php") {
                val category = VfsUtilCore.getRelativePath(vf, languageDir)?.removeSuffix(".php")
                val file = psi.findFile(vf)
                if (category != null && file != null) {
                    for (ret in PsiTreeUtil.findChildrenOfType(file, PhpReturn::class.java)) {
                        val array = ret.argument as? ArrayCreationExpression ?: continue
                        for (element in array.hashElements) {
                            val key = element.key as? StringLiteralExpression ?: continue
                            val value = (element.value as? StringLiteralExpression)?.contents ?: ""
                            out.add(Message(category, key.contents, languageDir.name, value, key))
                        }
                    }
                }
            }
            true
        }
    }

    // ------------------------------------------------------------------ where translations are written

    /** Message string: the category it belongs to ("" when unknown), or null when it is not a message. */
    fun messageCategory(token: PsiElement): String? {
        if (!SmartyPsiUtil.isType(token, STRING)) return null
        // 'Icon'|t:'category'
        val pipe = SmartyPhpTypes.nextSignificant(token)
        if (pipe != null && SmartyPsiUtil.isType(pipe, PIPE)) {
            val modifier = SmartyPhpTypes.nextSignificant(pipe)
            if (modifier != null && SmartyPsiUtil.isType(modifier, MODIFIER_NAME) && modifier.text == "t") {
                val colon = SmartyPhpTypes.nextSignificant(modifier)
                val category = colon?.let { SmartyPhpTypes.nextSignificant(it) }
                return if (SmartyPsiUtil.isType(colon, COLON) && category != null && SmartyPsiUtil.isType(category, STRING)) {
                    SmartyPsiUtil.unquote(category.text)
                } else ""
            }
        }
        // Yii::t('category', 'Icon')
        val comma = SmartyPhpTypes.prevSignificant(token) ?: return null
        if (!SmartyPsiUtil.isType(comma, COMMA)) return null
        val category = SmartyPhpTypes.prevSignificant(comma) ?: return null
        if (!SmartyPsiUtil.isType(category, STRING) || !isYiiTFirstArgument(category)) return null
        return SmartyPsiUtil.unquote(category.text)
    }

    /** Category string: `|t:'category'` or the first argument of `Yii::t(`. */
    fun isCategory(token: PsiElement): Boolean {
        if (!SmartyPsiUtil.isType(token, STRING)) return false
        val colon = SmartyPhpTypes.prevSignificant(token)
        if (colon != null && SmartyPsiUtil.isType(colon, COLON)) {
            val modifier = SmartyPhpTypes.prevSignificant(colon)
            return modifier != null && SmartyPsiUtil.isType(modifier, MODIFIER_NAME) && modifier.text == "t"
        }
        return isYiiTFirstArgument(token)
    }

    private fun isYiiTFirstArgument(token: PsiElement): Boolean {
        val paren = SmartyPhpTypes.prevSignificant(token) ?: return false
        if (!SmartyPsiUtil.isType(paren, LPAREN)) return false
        val method = SmartyPhpTypes.prevSignificant(paren) ?: return false
        if (!SmartyPsiUtil.isType(method, IDENTIFIER) || method.text != "t") return false
        val colons = SmartyPhpTypes.prevSignificant(method)
        val owner = colons?.let { SmartyPhpTypes.prevSignificant(it) }
        return SmartyPsiUtil.isType(colons, DOUBLE_COLON) && owner != null &&
            (SmartyPsiUtil.isType(owner, IDENTIFIER) || SmartyPsiUtil.isType(owner, TAG_NAME)) && owner.text == "Yii"
    }

    private fun matching(project: Project, category: String): List<Message> =
        messages(project).filter { category.isEmpty() || it.category == category }

    // ------------------------------------------------------------------ completion, navigation, documentation

    fun completeMessages(token: PsiElement, category: String, rs: CompletionResultSet) {
        val quote = token.text.firstOrNull() ?: '\''
        for ((message, translations) in matching(token.project, category).groupBy { it.message }) {
            val first = translations.first()
            rs.addElement(
                LookupElementBuilder.create(first.target, message)
                    .withIcon(AllIcons.Nodes.Tag)
                    .withTypeText(translations.joinToString(", ") { "${it.language}: ${it.translation}" }, true)
                    .withTailText(if (category.isEmpty()) "  ${first.category}" else null, true)
                    .withInsertHandler { context, _ ->
                        // keep the string valid when the message contains the quote character
                        if (message.contains(quote)) {
                            val start = context.startOffset
                            context.document.replaceString(start, context.tailOffset, message.replace("$quote", "\\$quote"))
                        }
                    }
            )
        }
    }

    fun completeCategories(token: PsiElement, rs: CompletionResultSet) {
        for ((category, messages) in messages(token.project).groupBy { it.category }) {
            rs.addElement(
                LookupElementBuilder.create(category)
                    .withIcon(AllIcons.Nodes.Folder)
                    .withTypeText("${messages.map { it.message }.distinct().size} messages", true)
            )
        }
    }

    /** All translations of the message, one per language. */
    fun resolve(token: PsiElement): List<PsiElement> {
        val category = messageCategory(token) ?: return emptyList()
        val message = SmartyPsiUtil.unquote(token.text).replace("\\'", "'").replace("\\\"", "\"")
        return matching(token.project, category).filter { it.message == message }.map { it.target }
    }

    /** Hover: the translations of a message, as HTML. */
    fun documentation(token: PsiElement): String? {
        val category = messageCategory(token) ?: return null
        val message = SmartyPsiUtil.unquote(token.text).replace("\\'", "'").replace("\\\"", "\"")
        val found = matching(token.project, category).filter { it.message == message }
        val escape = StringUtil::escapeXmlEntities
        return buildString {
            append("<div class='definition'><pre>").append(escape(message)).append("</pre></div><div class='content'>")
            if (found.isEmpty()) {
                append("No translation found").append(if (category.isEmpty()) "." else " in category <code>${escape(category)}</code>.")
            } else {
                append("<table>")
                for (m in found.sortedBy { it.language }) {
                    append("<tr><td><b>").append(escape(m.language)).append("</b>&nbsp;</td><td>")
                        .append(escape(m.translation)).append("</td></tr>")
                }
                append("</table>")
            }
            append("</div><table class='sections'><tr><td valign='top' class='section'><p>Category:</td><td valign='top'>")
                .append(escape(found.firstOrNull()?.category ?: category)).append("</td></tr></table>")
        }
    }
}
