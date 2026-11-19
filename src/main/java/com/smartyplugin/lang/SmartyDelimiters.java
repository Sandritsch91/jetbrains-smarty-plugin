package com.smartyplugin.lang;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Smarty's left_delimiter / right_delimiter: <code>{</code> <code>}</code> by default, configurable per project. */
public record SmartyDelimiters(@NotNull String left, @NotNull String right) {
    public static final SmartyDelimiters DEFAULT = new SmartyDelimiters("{", "}");

    public SmartyDelimiters {
        if (left.isEmpty() || right.isEmpty()) throw new IllegalArgumentException("Smarty delimiters must not be empty");
    }

    public static @NotNull SmartyDelimiters of(@Nullable Project project) {
        if (project == null || project.isDefault() || project.isDisposed()) return DEFAULT;
        return SmartySettings.getInstance(project).delimiters();
    }

    public static @NotNull SmartyDelimiters of(@NotNull PsiElement element) {
        return of(element.getProject());
    }

    /** {name} */
    public @NotNull String tag(@NotNull String name) {
        return left + name + right;
    }

    /** {/name} */
    public @NotNull String closeTag(@NotNull String name) {
        return left + "/" + name + right;
    }

    public @NotNull String commentStart() {
        return left + "*";
    }

    public @NotNull String commentEnd() {
        return "*" + right;
    }

    public boolean isDefault() {
        return equals(DEFAULT);
    }
}
