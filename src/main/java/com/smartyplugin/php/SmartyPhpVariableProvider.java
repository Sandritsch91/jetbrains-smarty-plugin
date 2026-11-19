package com.smartyplugin.php;

import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
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
            String type = p.value() instanceof PhpTypedElement typed
                    ? SmartyPhpTypes.presentable(typed.getType().global(template.getProject())) : null;
            result.add(new Variable(p.name(), type, p.target()));
        }
        return result;
    }
}
