package com.smartyplugin.php;

import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.smartyplugin.lang.SmartyVariableProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Template variables passed in by PHP render / assign calls, plus Yii's $app and $this. */
public final class SmartyPhpVariableProvider implements SmartyVariableProvider {
    @Override
    public @NotNull List<Variable> getVariables(@NotNull PsiFile template) {
        List<Variable> result = new ArrayList<>();
        Set<String> passed = new HashSet<>();
        for (SmartyPhpRenderCalls.Passed p : SmartyPhpRenderCalls.passedTo(template)) {
            result.add(new Variable(p.name(), SmartyPhpTypes.presentable(SmartyPhpTypes.typeOf(p)), p.target()));
            passed.add(p.name());
        }
        for (Map.Entry<String, String> implicit : SmartyPhpYii.IMPLICIT.entrySet()) {
            if (passed.contains(implicit.getKey())) continue;
            for (PhpClass c : SmartyPhpYii.implicitClasses(template.getProject(), implicit.getKey())) {
                result.add(new Variable(implicit.getKey(), c.getName(), c));
            }
        }
        return result;
    }
}
