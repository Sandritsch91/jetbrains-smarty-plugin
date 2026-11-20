package com.smartyplugin.php

import com.intellij.psi.PsiFile
import com.smartyplugin.lang.SmartyVariableProvider
import com.smartyplugin.lang.SmartyVariableProvider.Variable

/** Template variables passed in by PHP render / assign calls, plus Yii's $app and $this. */
class SmartyPhpVariableProvider : SmartyVariableProvider {
    override fun getVariables(template: PsiFile): List<Variable> {
        val result = ArrayList<Variable>()
        val passed = HashSet<String>()
        for (p in SmartyPhpRenderCalls.passedTo(template)) {
            result.add(Variable(p.name, SmartyPhpTypes.presentable(SmartyPhpTypes.typeOf(p)), p.target))
            passed.add(p.name)
        }
        for (name in SmartyPhpYii.IMPLICIT.keys) {
            if (name in passed) continue
            for (c in SmartyPhpYii.implicitClasses(template.project, name)) {
                result.add(Variable(name, c.name, c))
            }
        }
        return result
    }
}
