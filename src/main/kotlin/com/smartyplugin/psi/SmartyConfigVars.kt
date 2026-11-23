package com.smartyplugin.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.lexer.SmartyTokenTypes.BAD_CHARACTER
import com.smartyplugin.lexer.SmartyTokenTypes.CONFIG_VAR
import com.smartyplugin.lexer.SmartyTokenTypes.IDENTIFIER

/**
 * Config variables `{#title#}` from the files loaded with `{config_load file="site.conf" section="x"}`
 * in the template and the layouts it extends.
 */
object SmartyConfigVars {
    /** @param section null for variables before the first [section] of the file */
    data class ConfigVar(val name: String, val value: String, val section: String?, val target: PsiElement)

    private val SECTION = Regex("""^\s*\[\s*([^\]]+?)\s*]\s*$""")
    private val ENTRY = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$""")

    /** The variables available in [template]. */
    fun available(template: PsiFile): List<ConfigVar> {
        val result = ArrayList<ConfigVar>()
        val files = listOf(template.originalFile) + SmartyInheritance.parents(template)
        val loaded = HashSet<PsiFile>()
        for (file in files) {
            val smarty = SmartyPsiUtil.smartyFile(file) ?: continue
            for (literal in PsiTreeUtil.findChildrenOfType(smarty, SmartyStringLiteral::class.java)) {
                if (SmartyIncludes.pathTag(literal, "config_load") == null) continue
                val conf = SmartyIncludes.resolve(literal) ?: continue
                if (loaded.add(conf)) result.addAll(entries(conf))
            }
        }
        return result
    }

    /** `key = value` lines of a config file, by section; comments start with # or ;. */
    fun entries(conf: PsiFile): List<ConfigVar> = CachedValuesManager.getCachedValue(conf) {
        val result = ArrayList<ConfigVar>()
        var section: String? = null
        var offset = 0
        val vf = conf.viewProvider.virtualFile
        // from the file: .conf is often not a registered file type, so its PSI may have no text
        val text = com.intellij.openapi.fileEditor.impl.LoadTextUtil.loadText(vf).toString()
        for (line in text.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && trimmed[0] != '#' && trimmed[0] != ';') {
                val s = SECTION.find(line)
                if (s != null) {
                    section = s.groupValues[1].removePrefix(".")
                } else {
                    ENTRY.find(line)?.let { m ->
                        val name = m.groupValues[1]
                        val value = SmartyPsiUtil.unquote(m.groupValues[2])
                        val label = if (section == null) "$name = $value" else "[$section] $name = $value"
                        result.add(ConfigVar(name, value, section, SmartyFileLocation(conf, offset + line.indexOf(name), label)))
                    }
                }
            }
            offset += line.length + 1
        }
        CachedValueProvider.Result.create(result as List<ConfigVar>, conf, vf)
    }

    /** The name in `#name#`, or in `#name` while typing (lexed as a bad `#` and an identifier); null elsewhere. */
    fun nameAt(token: PsiElement): String? {
        if (SmartyPsiUtil.isType(token, CONFIG_VAR)) return token.text.trim('#')
        if (SmartyPsiUtil.isType(token, IDENTIFIER)) {
            val prev = PsiTreeUtil.prevLeaf(token)
            if (SmartyPsiUtil.isType(prev, BAD_CHARACTER) && prev!!.text == "#") return token.text
        }
        return null
    }
}
