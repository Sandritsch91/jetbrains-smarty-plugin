package com.smartyplugin.editor

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.smartyplugin.psi.SmartyInheritance

/**
 * Gutter icons on `{block name="content"}`: up to the block it overrides in the layout,
 * down to the blocks overriding it in templates that extend this one.
 */
class SmartyBlockLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // markers go on leaves: the first token of the block name
        if (element.firstChild != null) return
        val name = SmartyInheritance.asBlockName(element) ?: return
        if (PsiTreeUtil.getDeepestFirst(name) !== element) return
        val blockName = SmartyInheritance.nameOf(name)

        SmartyInheritance.overridden(name)?.let { parent ->
            result.add(
                NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod)
                    .setTargets(parent)
                    .setTooltipText("Overrides block '$blockName' in ${parent.containingFile.name}")
                    .createLineMarkerInfo(element),
            )
        }
        val children = SmartyInheritance.overriding(name)
        if (children.isNotEmpty()) {
            result.add(
                NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod)
                    .setTargets(children)
                    .setTooltipText(
                        if (children.size == 1) "Overridden in ${children[0].containingFile.name}"
                        else "Overridden in ${children.size} templates",
                    )
                    .createLineMarkerInfo(element),
            )
        }
    }
}
