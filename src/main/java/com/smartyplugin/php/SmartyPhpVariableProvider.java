package com.smartyplugin.php;

import com.intellij.psi.PsiFile;
import com.smartyplugin.lang.SmartyVariableProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Template variables passed in by PHP render / assign calls. */
public final class SmartyPhpVariableProvider implements SmartyVariableProvider {
    @Override
    public @NotNull List<Variable> getVariables(@NotNull PsiFile template) {
        List<Variable> result = new ArrayList<>();
        for (SmartyPhpRenderCalls.Passed p : SmartyPhpRenderCalls.passedTo(template)) {
            result.add(new Variable(p.name(), SmartyPhpTypes.presentable(SmartyPhpTypes.typeOf(p)), p.target()));
        }
        return result;
    }
}
