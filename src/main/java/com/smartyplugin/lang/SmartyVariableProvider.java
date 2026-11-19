package com.smartyplugin.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Variables a template receives from outside, e.g. from the PHP code that renders it
 * (<code>$this-&gt;render('index', ['user' =&gt; $user])</code>, <code>$smarty-&gt;assign('user', $user)</code>).
 */
public interface SmartyVariableProvider {
    ExtensionPointName<SmartyVariableProvider> EP_NAME = ExtensionPointName.create("com.smartyplugin.variableProvider");

    /**
     * @param name   variable name without '$'
     * @param type   short presentable type ("User", "string[]"), or null when unknown
     * @param target where the variable is passed in, for navigation
     */
    record Variable(@NotNull String name, @Nullable String type, @NotNull PsiElement target) {
    }

    @NotNull List<Variable> getVariables(@NotNull PsiFile template);

    static @NotNull List<Variable> all(@NotNull PsiFile template) {
        List<Variable> result = new ArrayList<>();
        for (SmartyVariableProvider provider : EP_NAME.getExtensionList()) {
            result.addAll(provider.getVariables(template));
        }
        return result;
    }
}
