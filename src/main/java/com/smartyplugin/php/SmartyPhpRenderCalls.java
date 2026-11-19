package com.smartyplugin.php;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.smartyplugin.lang.SmartyLanguage;
import com.smartyplugin.psi.SmartyPsiUtil;
import com.smartyplugin.psi.SmartyStringLiteral;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.ArrayHashElement;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.elements.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.smartyplugin.lexer.SmartyTokenTypes.*;

/**
 * Finds the PHP code that renders a template and the variables it passes:
 * <ul>
 *   <li>Yii2: <code>$this-&gt;render('index', ['user' =&gt; $user])</code>, also <code>renderPartial</code>,
 *       <code>renderAjax</code>, <code>renderFile</code> and <code>compact('user')</code> as the parameter array.
 *       View names resolve like Yii does: plain names relative to the controller's view folder
 *       (<code>views/&lt;controller-id&gt;/</code>) or to the calling view, <code>//site/index</code> and
 *       <code>@app/views/...</code> by path.</li>
 *   <li>Smarty: <code>$smarty-&gt;display('page.tpl')</code> / <code>fetch</code>, with the
 *       <code>assign('name', $value)</code> / <code>assign([...])</code> calls in the same function.</li>
 * </ul>
 * Matching is by path suffix, so it does not need to know the configured view or template folders.
 */
final class SmartyPhpRenderCalls {
    private static final Set<String> YII_RENDER = Set.of("render", "renderPartial", "renderAjax", "renderFile");
    private static final Set<String> SMARTY_RENDER = Set.of("display", "fetch");
    private static final Set<String> SMARTY_ASSIGN = Set.of("assign", "assignByRef");

    /** A variable the template receives: its name, the PHP element naming it, and the value expression. */
    record Passed(@NotNull String name, @NotNull PsiElement target, @Nullable PsiElement value) {
    }

    private SmartyPhpRenderCalls() {
    }

    static @NotNull List<Passed> passedTo(@NotNull PsiFile template) {
        PsiFile file = template.getOriginalFile();
        Project project = file.getProject();
        // while indexing nothing can be found; the dumb-mode tracker drops that empty result once indexing ends
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                DumbService.isDumb(project) ? List.of() : compute(file),
                PsiModificationTracker.getInstance(project), DumbService.getInstance(project).getModificationTracker()));
    }

    private static List<Passed> compute(PsiFile template) {
        VirtualFile vf = template.getViewProvider().getVirtualFile();
        String path = withoutExtension(vf.getPath());
        String baseName = withoutExtension(vf.getName());
        String word = searchWord(baseName);
        if (word == null) return List.of();

        Project project = template.getProject();
        List<Passed> result = new ArrayList<>();
        collectFromTemplates(project, path, word, result);
        Set<StringLiteralExpression> seen = new HashSet<>();
        PsiSearchHelper.getInstance(project).processElementsWithWord((element, offsetInElement) -> {
            if (!element.getLanguage().isKindOf(PhpLanguage.INSTANCE)) return true;
            StringLiteralExpression literal = PsiTreeUtil.getParentOfType(element, StringLiteralExpression.class, false);
            // the processor sees every element from the leaf up to the file for one occurrence
            if (literal == null || !seen.add(literal)) return true;
            MethodReference call = PsiTreeUtil.getParentOfType(literal, MethodReference.class);
            if (call == null || call.getParameters().length == 0 || call.getParameters()[0] != literal) return true;
            String method = call.getName();
            if (method == null) return true;
            if (YII_RENDER.contains(method) && matchesYiiView(literal, call, path)) {
                collectYii(call, result);
            } else if (SMARTY_RENDER.contains(method) && pathEndsWith(path, withoutExtension(literal.getContents()))) {
                collectSmartyAssigns(call, result);
            }
            return true;
        }, GlobalSearchScope.projectScope(project), word, UsageSearchContext.IN_STRINGS, true);
        return result;
    }

    // ------------------------------------------------------------------ renders in templates

    /** {$this->render('_form', ['model' => $model])} in another template. */
    private static void collectFromTemplates(Project project, String templatePath, String word, List<Passed> out) {
        PsiManager psiManager = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        // by extension, not by file type: right after *.tpl was reassigned from another Smarty plugin, the file
        // type index may still list the files under the old type
        Set<VirtualFile> templates = new LinkedHashSet<>();
        for (String ext : new String[]{"tpl", "smarty"}) {
            templates.addAll(FilenameIndex.getAllFilesByExt(project, ext, scope));
        }
        for (VirtualFile vf : templates) {
            PsiFile file = psiManager.findFile(vf);
            if (file == null || !StringUtil.contains(file.getViewProvider().getContents(), word)) continue;
            PsiFile smarty = file.getViewProvider().getPsi(SmartyLanguage.INSTANCE);
            if (smarty == null) continue;
            for (SmartyStringLiteral literal : PsiTreeUtil.findChildrenOfType(smarty, SmartyStringLiteral.class)) {
                if (isTemplateRenderCall(literal) && matchesTemplateView(literal, vf, templatePath)) {
                    collectTemplateArray(literal, out);
                }
            }
        }
    }

    /** The literal is the first argument of ->render( / ->renderPartial( / ... */
    private static boolean isTemplateRenderCall(SmartyStringLiteral literal) {
        PsiElement paren = SmartyPhpTypes.prevSignificant(literal);
        if (paren == null || !SmartyPsiUtil.is(paren, LPAREN)) return false;
        PsiElement name = SmartyPhpTypes.prevSignificant(paren);
        if (name == null || !SmartyPsiUtil.is(name, IDENTIFIER) || !YII_RENDER.contains(name.getText())) return false;
        PsiElement arrow = SmartyPhpTypes.prevSignificant(name);
        return arrow != null && SmartyPsiUtil.is(arrow, ARROW);
    }

    private static boolean matchesTemplateView(SmartyStringLiteral literal, VirtualFile caller, String templatePath) {
        String view = withoutExtension(SmartyPsiUtil.unquote(literal.getText()).replace('\\', '/'));
        if (view.isEmpty()) return false;
        if (view.startsWith("@")) {
            int slash = view.indexOf('/');
            return slash > 0 && pathEndsWith(templatePath, view.substring(slash + 1));
        }
        if (view.startsWith("/")) return pathEndsWith(templatePath, view.replaceFirst("^/+", ""));
        VirtualFile dir = caller.getParent();
        return dir != null && templatePath.equals(dir.getPath() + "/" + view);
    }

    /** Entries of the [...] after the view name: 'key' => expression. The value is the expression's last token. */
    private static void collectTemplateArray(SmartyStringLiteral literal, List<Passed> out) {
        PsiElement comma = SmartyPhpTypes.nextSignificant(literal);
        if (comma == null || !SmartyPsiUtil.is(comma, COMMA)) return;
        PsiElement open = SmartyPhpTypes.nextSignificant(comma);
        if (open == null || !SmartyPsiUtil.is(open, LBRACKET)) return;
        int depth = 1;
        PsiElement key = null;
        boolean afterArrow = false;
        PsiElement lastValue = null;
        for (PsiElement t = SmartyPhpTypes.nextSignificant(open); t != null; t = SmartyPhpTypes.nextSignificant(t)) {
            if (SmartyPsiUtil.is(t, RDELIM) && depth <= 1) break;
            boolean entryEnd = depth == 1 && (SmartyPsiUtil.is(t, COMMA) || SmartyPsiUtil.is(t, RBRACKET));
            if (entryEnd) {
                if (key != null && afterArrow) {
                    out.add(new Passed(SmartyPsiUtil.unquote(key.getText()), key, lastValue));
                }
                key = null;
                afterArrow = false;
                lastValue = null;
                if (SmartyPsiUtil.is(t, RBRACKET)) break;
                continue;
            }
            if (SmartyPsiUtil.is(t, LBRACKET) || SmartyPsiUtil.is(t, LPAREN)) depth++;
            if (SmartyPsiUtil.is(t, RBRACKET) || SmartyPsiUtil.is(t, RPAREN)) depth--;
            if (depth == 1 && !afterArrow && SmartyPsiUtil.is(t, STRING)) {
                key = t.getParent() instanceof SmartyStringLiteral s ? s : t;
            } else if (depth == 1 && key != null && SmartyPsiUtil.is(t, DOUBLE_ARROW)) {
                afterArrow = true;
            } else if (afterArrow) {
                lastValue = t;
            }
        }
    }

    // ------------------------------------------------------------------ view name matching

    private static boolean matchesYiiView(StringLiteralExpression literal, MethodReference call, String templatePath) {
        String view = withoutExtension(literal.getContents().replace('\\', '/'));
        if (view.isEmpty()) return false;
        if (view.startsWith("@")) {
            int slash = view.indexOf('/');
            return slash > 0 && pathEndsWith(templatePath, view.substring(slash + 1));
        }
        if (view.startsWith("/")) return pathEndsWith(templatePath, view.replaceFirst("^/+", ""));
        if ("renderFile".equals(call.getName())) return pathEndsWith(templatePath, view);

        PhpClass owner = PsiTreeUtil.getParentOfType(call, PhpClass.class);
        if (owner != null && owner.getName().endsWith("Controller") && owner.getName().length() > "Controller".length()) {
            String id = controllerId(owner.getName());
            return pathEndsWith(templatePath, id + "/" + view);
        }
        // a view rendering another view, or a widget rendering from its "views" folder
        VirtualFile caller = call.getContainingFile().getViewProvider().getVirtualFile();
        VirtualFile dir = caller.getParent();
        if (dir == null) return false;
        String callerDir = dir.getPath();
        return templatePath.equals(callerDir + "/" + view) || templatePath.equals(callerDir + "/views/" + view);
    }

    /** PostCommentController -> post-comment */
    static String controllerId(String className) {
        String name = className.substring(0, className.length() - "Controller".length());
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private static boolean pathEndsWith(String templatePath, String suffix) {
        if (suffix.isEmpty()) return false;
        return templatePath.equals(suffix) || templatePath.endsWith("/" + suffix);
    }

    private static String withoutExtension(String name) {
        for (String ext : new String[]{".tpl", ".smarty", ".php"}) {
            if (name.endsWith(ext)) return name.substring(0, name.length() - ext.length());
        }
        return name;
    }

    /** A word of the template name as the word index stores it: "_form" for "_form", "my" for "my-page". */
    private static @Nullable String searchWord(String baseName) {
        for (String part : baseName.split("[^A-Za-z0-9_]+")) {
            if (!part.isEmpty()) return part;
        }
        return null;
    }

    // ------------------------------------------------------------------ passed variables

    private static void collectYii(MethodReference call, List<Passed> out) {
        PsiElement[] params = call.getParameters();
        if (params.length < 2) return;
        PsiElement vars = params[1];
        if (vars instanceof ArrayCreationExpression array) {
            collectArray(array, out);
        } else if (vars instanceof FunctionReference fn && "compact".equals(fn.getName())) {
            Function scope = PsiTreeUtil.getParentOfType(call, Function.class);
            for (PsiElement arg : fn.getParameters()) {
                if (arg instanceof StringLiteralExpression s) {
                    out.add(new Passed(s.getContents(), s, findVariable(scope, s.getContents())));
                }
            }
        }
    }

    private static void collectArray(ArrayCreationExpression array, List<Passed> out) {
        for (ArrayHashElement element : array.getHashElements()) {
            if (element.getKey() instanceof StringLiteralExpression key && !key.getContents().isEmpty()) {
                out.add(new Passed(key.getContents(), key, element.getValue()));
            }
        }
    }

    /** assign() calls in the function that displays the template. */
    private static void collectSmartyAssigns(MethodReference render, List<Passed> out) {
        Function scope = PsiTreeUtil.getParentOfType(render, Function.class);
        PsiElement root = scope != null ? scope : render.getContainingFile();
        for (MethodReference call : PsiTreeUtil.findChildrenOfType(root, MethodReference.class)) {
            if (call.getName() == null || !SMARTY_ASSIGN.contains(call.getName())) continue;
            PsiElement[] params = call.getParameters();
            if (params.length >= 1 && params[0] instanceof StringLiteralExpression name && !name.getContents().isEmpty()) {
                out.add(new Passed(name.getContents(), name, params.length > 1 ? params[1] : null));
            } else if (params.length >= 1 && params[0] instanceof ArrayCreationExpression array) {
                collectArray(array, out);
            }
        }
    }

    /** First occurrence of $name in the function, used as the value of compact('name'). */
    private static @Nullable PsiElement findVariable(@Nullable Function scope, String name) {
        if (scope == null) return null;
        for (Variable v : PsiTreeUtil.findChildrenOfType(scope, Variable.class)) {
            if (name.equals(v.getName())) return v;
        }
        return null;
    }
}
