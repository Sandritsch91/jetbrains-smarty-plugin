package com.smartyplugin.php

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lexer.SmartyTokenTypes.ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.COMMA
import com.smartyplugin.lexer.SmartyTokenTypes.DOUBLE_ARROW
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER
import com.smartyplugin.lexer.SmartyTokenTypes.LBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.LPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.RBRACKET
import com.smartyplugin.lexer.SmartyTokenTypes.RDELIM
import com.smartyplugin.lexer.SmartyTokenTypes.RPAREN
import com.smartyplugin.lexer.SmartyTokenTypes.STRING
import com.smartyplugin.psi.SmartyPsiUtil
import com.smartyplugin.psi.SmartyStringLiteral
import java.util.Locale

/**
 * Finds the PHP code that renders a template and the variables it passes:
 * - Yii2: `$this->render('index', ['user' => $user])`, also `renderPartial`, `renderAjax`, `renderFile` and
 *   `compact('user')` as the parameter array. View names resolve like Yii does: plain names relative to the
 *   controller's view folder (`views/<controller-id>/`) or to the calling view, `//site/index` and
 *   `@app/views/...` by path.
 * - Smarty: `$smarty->display('page.tpl')` / `fetch`, with the `assign('name', $value)` / `assign([...])` calls
 *   in the same function.
 *
 * Matching is by path suffix, so it does not need to know the configured view or template folders.
 */
internal object SmartyPhpRenderCalls {
    private val YII_RENDER = setOf("render", "renderPartial", "renderAjax", "renderFile")
    private val SMARTY_RENDER = setOf("display", "fetch")
    private val SMARTY_ASSIGN = setOf("assign", "assignByRef")
    private val LEADING_SLASHES = Regex("^/+")
    private val CAMEL_HUMP = Regex("([a-z0-9])([A-Z])")
    private val NON_WORD = Regex("[^A-Za-z0-9_]+")

    /** A variable the template receives: its name, the PHP element naming it, and the value expression. */
    data class Passed(val name: String, val target: PsiElement, val value: PsiElement?)

    fun passedTo(template: PsiFile): List<Passed> {
        val file = template.originalFile
        val project = file.project
        // while indexing nothing can be found; the dumb-mode tracker drops that empty result once indexing ends
        return CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(
                if (DumbService.isDumb(project)) emptyList() else compute(file),
                PsiModificationTracker.getInstance(project),
                DumbService.getInstance(project).modificationTracker,
            )
        }
    }

    private fun compute(template: PsiFile): List<Passed> {
        val vf = template.viewProvider.virtualFile
        val path = withoutExtension(vf.path)
        val baseName = withoutExtension(vf.name)
        val word = searchWord(baseName) ?: return emptyList()

        val project = template.project
        val result = ArrayList<Passed>()
        collectFromTemplates(project, path, word, result)
        val seen = HashSet<StringLiteralExpression>()
        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, _ ->
                if (!element.language.isKindOf(PhpLanguage.INSTANCE)) return@processElementsWithWord true
                val literal = PsiTreeUtil.getParentOfType(element, StringLiteralExpression::class.java, false)
                // the processor sees every element from the leaf up to the file for one occurrence
                if (literal == null || !seen.add(literal)) return@processElementsWithWord true
                val call = PsiTreeUtil.getParentOfType(literal, MethodReference::class.java)
                if (call == null || call.parameters.isEmpty() || call.parameters[0] !== literal) {
                    return@processElementsWithWord true
                }
                val method = call.name ?: return@processElementsWithWord true
                if (method in YII_RENDER && matchesYiiView(literal, call, path)) {
                    collectYii(call, result)
                } else if (method in SMARTY_RENDER && pathEndsWith(path, withoutExtension(literal.contents))) {
                    collectSmartyAssigns(call, result)
                }
                true
            },
            GlobalSearchScope.projectScope(project), word, UsageSearchContext.IN_STRINGS, true,
        )
        return result
    }

    // ------------------------------------------------------------------ renders in templates

    /** {$this->render('_form', ['model' => $model])} in another template. */
    private fun collectFromTemplates(project: Project, templatePath: String, word: String, out: MutableList<Passed>) {
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        // by extension, not by file type: right after *.tpl was reassigned from another Smarty plugin, the file
        // type index may still list the files under the old type
        val templates = LinkedHashSet<VirtualFile>()
        for (ext in arrayOf("tpl", "smarty")) {
            templates.addAll(FilenameIndex.getAllFilesByExt(project, ext, scope))
        }
        for (vf in templates) {
            val file = psiManager.findFile(vf)
            if (file == null || !StringUtil.contains(file.viewProvider.contents, word)) continue
            val smarty = file.viewProvider.getPsi(SmartyLanguage) ?: continue
            for (literal in PsiTreeUtil.findChildrenOfType(smarty, SmartyStringLiteral::class.java)) {
                if (isTemplateRenderCall(literal) && matchesTemplateView(literal, vf, templatePath)) {
                    collectTemplateArray(literal, out)
                }
            }
        }
    }

    /** The literal is the first argument of ->render( / ->renderPartial( / ... */
    private fun isTemplateRenderCall(literal: SmartyStringLiteral): Boolean {
        val paren = SmartyPhpTypes.prevSignificant(literal)
        if (paren == null || !SmartyPsiUtil.isType(paren, LPAREN)) return false
        val name = SmartyPhpTypes.prevSignificant(paren)
        if (name == null || !SmartyPsiUtil.isType(name, IDENTIFIER) || name.text !in YII_RENDER) return false
        val arrow = SmartyPhpTypes.prevSignificant(name)
        return arrow != null && SmartyPsiUtil.isType(arrow, ARROW)
    }

    private fun matchesTemplateView(literal: SmartyStringLiteral, caller: VirtualFile, templatePath: String): Boolean {
        val view = withoutExtension(SmartyPsiUtil.unquote(literal.text).replace('\\', '/'))
        if (view.isEmpty()) return false
        if (view.startsWith("@")) {
            val slash = view.indexOf('/')
            return slash > 0 && pathEndsWith(templatePath, view.substring(slash + 1))
        }
        if (view.startsWith("/")) return pathEndsWith(templatePath, view.replaceFirst(LEADING_SLASHES, ""))
        val dir = caller.parent
        return dir != null && templatePath == dir.path + "/" + view
    }

    /** Entries of the [...] after the view name: 'key' => expression. The value is the expression's last token. */
    private fun collectTemplateArray(literal: SmartyStringLiteral, out: MutableList<Passed>) {
        val comma = SmartyPhpTypes.nextSignificant(literal)
        if (comma == null || !SmartyPsiUtil.isType(comma, COMMA)) return
        val open = SmartyPhpTypes.nextSignificant(comma)
        if (open == null || !SmartyPsiUtil.isType(open, LBRACKET)) return
        var depth = 1
        var key: PsiElement? = null
        var afterArrow = false
        var lastValue: PsiElement? = null
        var t = SmartyPhpTypes.nextSignificant(open)
        while (t != null) {
            if (SmartyPsiUtil.isType(t, RDELIM) && depth <= 1) break
            val entryEnd = depth == 1 && (SmartyPsiUtil.isType(t, COMMA) || SmartyPsiUtil.isType(t, RBRACKET))
            if (entryEnd) {
                if (key != null && afterArrow) {
                    out.add(Passed(SmartyPsiUtil.unquote(key.text), key, lastValue))
                }
                key = null
                afterArrow = false
                lastValue = null
                if (SmartyPsiUtil.isType(t, RBRACKET)) break
                t = SmartyPhpTypes.nextSignificant(t)
                continue
            }
            if (SmartyPsiUtil.isType(t, LBRACKET) || SmartyPsiUtil.isType(t, LPAREN)) depth++
            if (SmartyPsiUtil.isType(t, RBRACKET) || SmartyPsiUtil.isType(t, RPAREN)) depth--
            if (depth == 1 && !afterArrow && SmartyPsiUtil.isType(t, STRING)) {
                key = t.parent as? SmartyStringLiteral ?: t
            } else if (depth == 1 && key != null && SmartyPsiUtil.isType(t, DOUBLE_ARROW)) {
                afterArrow = true
            } else if (afterArrow) {
                lastValue = t
            }
            t = SmartyPhpTypes.nextSignificant(t)
        }
    }

    // ------------------------------------------------------------------ view name matching

    private fun matchesYiiView(literal: StringLiteralExpression, call: MethodReference, templatePath: String): Boolean {
        val view = withoutExtension(literal.contents.replace('\\', '/'))
        if (view.isEmpty()) return false
        if (view.startsWith("@")) {
            val slash = view.indexOf('/')
            return slash > 0 && pathEndsWith(templatePath, view.substring(slash + 1))
        }
        if (view.startsWith("/")) return pathEndsWith(templatePath, view.replaceFirst(LEADING_SLASHES, ""))
        if (call.name == "renderFile") return pathEndsWith(templatePath, view)

        val owner = PsiTreeUtil.getParentOfType(call, PhpClass::class.java)
        if (owner != null && owner.name.endsWith("Controller") && owner.name.length > "Controller".length) {
            val id = controllerId(owner.name)
            return pathEndsWith(templatePath, "$id/$view")
        }
        // a view rendering another view, or a widget rendering from its "views" folder
        val caller = call.containingFile.viewProvider.virtualFile
        val dir = caller.parent ?: return false
        val callerDir = dir.path
        return templatePath == "$callerDir/$view" || templatePath == "$callerDir/views/$view"
    }

    /** PostCommentController -> post-comment */
    fun controllerId(className: String): String {
        val name = className.substring(0, className.length - "Controller".length)
        return CAMEL_HUMP.replace(name, "\$1-\$2").lowercase(Locale.ROOT)
    }

    private fun pathEndsWith(templatePath: String, suffix: String): Boolean {
        if (suffix.isEmpty()) return false
        return templatePath == suffix || templatePath.endsWith("/$suffix")
    }

    private fun withoutExtension(name: String): String {
        for (ext in arrayOf(".tpl", ".smarty", ".php")) {
            if (name.endsWith(ext)) return name.substring(0, name.length - ext.length)
        }
        return name
    }

    /** A word of the template name as the word index stores it: "_form" for "_form", "my" for "my-page". */
    private fun searchWord(baseName: String): String? = baseName.split(NON_WORD).firstOrNull { it.isNotEmpty() }

    // ------------------------------------------------------------------ passed variables

    private fun collectYii(call: MethodReference, out: MutableList<Passed>) {
        val params = call.parameters
        if (params.size < 2) return
        val vars = params[1]
        if (vars is ArrayCreationExpression) {
            collectArray(vars, out)
        } else if (vars is FunctionReference && vars.name == "compact") {
            val scope = PsiTreeUtil.getParentOfType(call, Function::class.java)
            for (arg in vars.parameters) {
                if (arg is StringLiteralExpression) {
                    out.add(Passed(arg.contents, arg, findVariable(scope, arg.contents)))
                }
            }
        }
    }

    private fun collectArray(array: ArrayCreationExpression, out: MutableList<Passed>) {
        for (element in array.hashElements) {
            val key = element.key
            if (key is StringLiteralExpression && key.contents.isNotEmpty()) {
                out.add(Passed(key.contents, key, element.value))
            }
        }
    }

    /** assign() calls in the function that displays the template. */
    private fun collectSmartyAssigns(render: MethodReference, out: MutableList<Passed>) {
        val scope = PsiTreeUtil.getParentOfType(render, Function::class.java)
        val root: PsiElement = scope ?: render.containingFile
        for (call in PsiTreeUtil.findChildrenOfType(root, MethodReference::class.java)) {
            val callName = call.name
            if (callName == null || callName !in SMARTY_ASSIGN) continue
            val params = call.parameters
            val first = params.firstOrNull()
            if (first is StringLiteralExpression && first.contents.isNotEmpty()) {
                out.add(Passed(first.contents, first, if (params.size > 1) params[1] else null))
            } else if (first is ArrayCreationExpression) {
                collectArray(first, out)
            }
        }
    }

    /** First occurrence of $name in the function, used as the value of compact('name'). */
    private fun findVariable(scope: Function?, name: String): PsiElement? {
        if (scope == null) return null
        return PsiTreeUtil.findChildrenOfType(scope, Variable::class.java).firstOrNull { it.name == name }
    }
}
