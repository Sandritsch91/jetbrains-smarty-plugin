package com.smartyplugin.lang

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Smarty plugins of the project: `function.xxx.php` / `block.xxx.php` / `modifier.xxx.php` files in a plugins
 * directory, and (with the PHP plugin) `$smarty->registerPlugin('function', 'xxx', ...)` calls.
 */
interface SmartyPluginProvider {
    enum class Kind { FUNCTION, BLOCK, MODIFIER }

    /** @param target where the plugin is defined, for Ctrl+Click; [source] a short label ("block.box.php") */
    data class Plugin(val kind: Kind, val name: String, val target: PsiElement, val source: String)

    fun getPlugins(project: Project): List<Plugin>

    /** Whether a PHP function of this name exists (Smarty 3/4 accept PHP functions as modifiers). */
    fun isPhpFunction(project: Project, name: String): Boolean = false

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<SmartyPluginProvider> = ExtensionPointName.create("com.smartyplugin.pluginProvider")
    }
}

object SmartyPlugins {
    fun all(project: Project): List<SmartyPluginProvider.Plugin> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            val result = if (DumbService.isDumb(project)) emptyList()
            else SmartyPluginProvider.EP_NAME.extensionList.flatMap { it.getPlugins(project) }
            CachedValueProvider.Result.create(
                result,
                PsiModificationTracker.getInstance(project),
                DumbService.getInstance(project).modificationTracker,
            )
        }

    fun tags(project: Project) = all(project).filter { it.kind != SmartyPluginProvider.Kind.MODIFIER }

    fun modifiers(project: Project) = all(project).filter { it.kind == SmartyPluginProvider.Kind.MODIFIER }

    fun findTag(project: Project, name: String) = tags(project).filter { it.name == name }

    fun findModifier(project: Project, name: String) = modifiers(project).filter { it.name == name }

    fun isPhpFunction(project: Project, name: String): Boolean =
        SmartyPluginProvider.EP_NAME.extensionList.any { it.isPhpFunction(project, name) }
}

/** Plugin files named like Smarty loads them from its plugins directories: function.name.php, block.name.php ... */
class SmartyPluginFileProvider : SmartyPluginProvider {
    override fun getPlugins(project: Project): List<SmartyPluginProvider.Plugin> {
        val scope = GlobalSearchScope.projectScope(project)
        val names = ArrayList<String>()
        FilenameIndex.processAllFileNames({ name -> if (FILE_NAME.matches(name)) names.add(name); true }, scope, null)
        val psiManager = PsiManager.getInstance(project)
        val result = ArrayList<SmartyPluginProvider.Plugin>()
        for (fileName in names) {
            val m = FILE_NAME.matchEntire(fileName) ?: continue
            val kind = when (m.groupValues[1]) {
                "block" -> SmartyPluginProvider.Kind.BLOCK
                "modifier", "modifiercompiler" -> SmartyPluginProvider.Kind.MODIFIER
                else -> SmartyPluginProvider.Kind.FUNCTION // function, compiler
            }
            for (vf in FilenameIndex.getVirtualFilesByName(fileName, scope)) {
                val file = psiManager.findFile(vf) ?: continue
                result.add(SmartyPluginProvider.Plugin(kind, m.groupValues[2], file, fileName))
            }
        }
        return result
    }

    private companion object {
        val FILE_NAME = Regex("""(function|block|modifier|modifiercompiler|compiler)\.([A-Za-z_][A-Za-z0-9_]*)\.php""")
    }
}
