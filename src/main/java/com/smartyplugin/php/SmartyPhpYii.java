package com.smartyplugin.php;

import com.intellij.openapi.project.Project;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Variables the Yii2 Smarty renderer provides in every template: $app (Yii::$app) and $this (the View). */
final class SmartyPhpYii {
    static final Map<String, String> IMPLICIT = Map.of(
            "app", "\\yii\\web\\Application",
            "this", "\\yii\\web\\View");

    private SmartyPhpYii() {
    }

    /** The class of an implicit variable, empty when the name isn't one or Yii isn't in the project. */
    static @NotNull Collection<PhpClass> implicitClasses(@NotNull Project project, @NotNull String name) {
        String fqn = IMPLICIT.get(name);
        return fqn == null ? List.of() : PhpIndex.getInstance(project).getAnyByFQN(fqn);
    }

    static @Nullable PhpType implicitType(@NotNull Project project, @NotNull String name) {
        Collection<PhpClass> classes = implicitClasses(project, name);
        if (classes.isEmpty()) return null;
        PhpType type = new PhpType();
        for (PhpClass c : classes) type.add(c.getFQN());
        return type;
    }
}
