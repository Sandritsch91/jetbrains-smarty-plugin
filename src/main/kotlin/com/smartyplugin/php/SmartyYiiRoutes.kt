package com.smartyplugin.php

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_COLON
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.LBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.lexer.SmartyTokenTypes.TAG_NAME
import com.smartyplugin.parser.SmartyElementTypes.ATTRIBUTE
import com.smartyplugin.parser.SmartyElementTypes.TAG
import com.smartyplugin.psi.SmartyPsiUtil

/**
 * Yii routes in templates: `{url route="admin/create"}`, `{path route="list"}`, `Url::to(['admin/create'])`,
 * `Url::toRoute('admin/create')`.
 *
 * Routes come from the project's controllers (classes named `*Controller` in a `controllers` namespace; sub-namespaces
 * become route folders) and their actions (`actionCreate()` is `create`, plus the keys of `actions()`). A route
 * without a slash is relative to the template's controller, like Yii resolves it. Module ids are not known, so
 * routes match by their trailing controller/action part.
 */
internal object SmartyYiiRoutes {
    data class Action(val controllerId: String, val actionId: String, val controller: PhpClass, val target: PsiElement) {
        val route: String get() = "$controllerId/$actionId"
    }

    private val ACTION_METHOD = Regex("^action([A-Z][A-Za-z0-9]*)$")

    fun actions(project: Project): List<Action> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            val value = if (DumbService.isDumb(project)) emptyList() else computeActions(project)
            CachedValueProvider.Result.create(
                value, PsiModificationTracker.getInstance(project), DumbService.getInstance(project).modificationTracker,
            )
        }

    private fun computeActions(project: Project): List<Action> {
        val index = PhpIndex.getInstance(project)
        val result = ArrayList<Action>()
        for (name in index.getAllClassNames(null)) {
            if (!name.endsWith("Controller") || name.length <= "Controller".length) continue
            for (c in index.getClassesByName(name)) {
                if (c.isAbstract || c.isInterface || c.isTrait) continue
                val id = controllerId(c) ?: continue
                for (m in c.ownMethods) {
                    val match = ACTION_METHOD.matchEntire(m.name) ?: continue
                    if (!m.access.isPublic || m.isStatic) continue
                    result.add(Action(id, SmartyPhpRenderCalls.controllerId(match.groupValues[1] + "Controller"), c, m))
                }
                // public function actions() { return ['list' => IconSearchAction::class]; }
                val actions = c.findOwnMethodByName("actions") ?: continue
                for (ret in PsiTreeUtil.findChildrenOfType(actions, PhpReturn::class.java)) {
                    val array = ret.argument as? ArrayCreationExpression ?: continue
                    for (element in array.hashElements) {
                        val key = element.key as? StringLiteralExpression ?: continue
                        if (key.contents.isNotEmpty()) result.add(Action(id, key.contents, c, key))
                    }
                }
            }
        }
        return result
    }

    /** `\app\controllers\admin\UserProfileController` -> `admin/user-profile`; null outside a controllers namespace. */
    private fun controllerId(c: PhpClass): String? {
        val fqn = c.fqn.trimStart('\\')
        val at = fqn.lastIndexOf("controllers\\")
        if (at < 0 || (at > 0 && fqn[at - 1] != '\\')) return null
        val parts = fqn.substring(at + "controllers\\".length).split('\\')
        val folders = parts.dropLast(1).joinToString("") { "$it/" }
        return folders + SmartyPhpRenderCalls.controllerId(parts.last())
    }

    // ------------------------------------------------------------------ where routes are written

    /** Whether the STRING token is a route: `{url route="..."}`, `Url::to(['...'])`, `Url::toRoute('...')`. */
    fun isRoute(token: PsiElement): Boolean {
        if (!SmartyPsiUtil.isType(token, STRING)) return false
        val literal = token.parent
        val attribute = literal?.parent
        if (attribute != null && SmartyPsiUtil.isType(attribute, ATTRIBUTE) &&
            SmartyPsiUtil.attributeName(attribute.node) == "route"
        ) {
            val tag = attribute.parent
            return SmartyPsiUtil.isType(tag, TAG) && SmartyPsiUtil.tagName(tag.node) in setOf("url", "path")
        }
        var prev = SmartyPhpTypes.prevSignificant(token) ?: return false
        val inArray = SmartyPsiUtil.isType(prev, LBRACKET)
        if (inArray) prev = SmartyPhpTypes.prevSignificant(prev) ?: return false
        if (!SmartyPsiUtil.isType(prev, LPAREN)) return false
        val method = SmartyPhpTypes.prevSignificant(prev) ?: return false
        if (!SmartyPsiUtil.isType(method, IDENTIFIER)) return false
        if (method.text != "toRoute" && !(method.text == "to" && inArray)) return false
        val colons = SmartyPhpTypes.prevSignificant(method)
        val urlClass = colons?.let { SmartyPhpTypes.prevSignificant(it) }
        return SmartyPsiUtil.isType(colons, DOUBLE_COLON) && urlClass != null &&
            (SmartyPsiUtil.isType(urlClass, IDENTIFIER) || SmartyPsiUtil.isType(urlClass, TAG_NAME)) &&
            urlClass.text == "Url"
    }

    /** Controller ids a template belongs to: those rendering it, and its folder (views/<controller-id>/x.tpl). */
    private fun templateControllers(template: PsiFile): Set<String> {
        val ids = LinkedHashSet<String>()
        for (passed in SmartyPhpRenderCalls.passedTo(template)) {
            val c = PsiTreeUtil.getParentOfType(passed.target, PhpClass::class.java) ?: continue
            controllerId(c)?.let { ids.add(it) }
        }
        template.originalFile.viewProvider.virtualFile.parent?.name?.let { ids.add(it) }
        return ids
    }

    // ------------------------------------------------------------------ completion and navigation

    fun complete(token: PsiElement, rs: CompletionResultSet) {
        val project = token.project
        val own = templateControllers(token.containingFile)
        for (action in actions(project)) {
            val owner = action.controller.name
            rs.addElement(
                LookupElementBuilder.create(action.target, action.route)
                    .withIcon(AllIcons.Nodes.Method)
                    .withTypeText(owner, true)
            )
            if (action.controllerId in own) {
                // relative to the template's controller, listed first
                rs.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(action.target, action.actionId)
                            .withIcon(AllIcons.Nodes.Method)
                            .withTypeText(owner, true)
                            .bold(),
                        10.0,
                    )
                )
            }
        }
    }

    /** The action a route refers to: method or actions() entry. */
    fun resolve(token: PsiElement): List<PsiElement> {
        val route = SmartyPsiUtil.unquote(token.text).trim().trimStart('/')
        if (route.isEmpty()) return emptyList()
        val segments = route.split('/')
        val actionId = segments.last()
        val all = actions(token.project)
        if (segments.size == 1) {
            val own = templateControllers(token.containingFile)
            return all.filter { it.controllerId in own && it.actionId == actionId }.map { it.target }
        }
        // a module prefix is unknown: drop leading segments until a controller matches
        for (start in 0 until segments.size - 1) {
            val controllerId = segments.subList(start, segments.size - 1).joinToString("/")
            val matches = all.filter { it.controllerId == controllerId && it.actionId == actionId }
            if (matches.isNotEmpty()) return matches.map { it.target }
        }
        return emptyList()
    }
}
