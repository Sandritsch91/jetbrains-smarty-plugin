package com.smartyplugin

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The project's Smarty plugin files: function.x.php, block.x.php, modifier.x.php. */
class SmartyPluginsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("smarty/plugins/function.greet.php", "<?php\nfunction smarty_function_greet(\$params, \$template) { return 'Hi'; }\n")
        myFixture.addFileToProject("smarty/plugins/block.box.php", "<?php\nfunction smarty_block_box(\$params, \$content) { return \$content; }\n")
        myFixture.addFileToProject("smarty/plugins/modifier.money.php", "<?php\nfunction smarty_modifier_money(\$v) { return \$v; }\n")
        // Smarty's own plugin file of a built-in modifier is not listed twice
        myFixture.addFileToProject("vendor/smarty/libs/plugins/modifier.truncate.php", "<?php\n")
    }

    /** Picks [name] when completion shows a list. */
    private fun select(name: String) {
        val item = myFixture.lookupElements?.firstOrNull { it.lookupString == name } ?: return
        myFixture.lookup.currentItem = item
        myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR)
    }

    fun testFunctionTagCompletion() {
        myFixture.configureByText("a.tpl", "{gre<caret>")
        myFixture.completeBasic()
        myFixture.checkResult("{greet <caret>}")
    }

    fun testBlockTagCompletionInsertsClosingTag() {
        myFixture.configureByText("a.tpl", "{bo<caret>")
        myFixture.completeBasic()
        select("box")
        myFixture.checkResult("{box <caret>}{/box}")
    }

    fun testModifierCompletion() {
        myFixture.configureByText("a.tpl", "{\$price|<caret>}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings!!
        assertTrue(items.toString(), "money" in items)
        assertEquals(items.toString(), 1, items.count { it == "truncate" })
    }

    fun testNavigation() {
        myFixture.configureByText("a.tpl", "{box title='x'}{greet}{/box}{\$price|money}")
        val text = myFixture.editor.document.text
        fun target(at: String) =
            (GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf(at) + 1) as PsiFile).name
        assertEquals("block.box.php", target("box title"))
        assertEquals("function.greet.php", target("greet"))
        assertEquals("block.box.php", target("box}"))
        assertEquals("modifier.money.php", target("money"))
    }
}

/** The optional inspection for unknown tags and modifiers. */
class SmartyUnknownNameInspectionTest : BasePlatformTestCase() {
    fun testUnknownNames() {
        myFixture.enableInspections(com.smartyplugin.editor.SmartyUnknownNameInspection())
        myFixture.addFileToProject("plugins/block.box.php", "<?php\n")
        myFixture.addFileToProject("plugins/modifier.money.php", "<?php\n")
        myFixture.configureByText(
            "a.tpl",
            """
            {function name="menu"}x{/function}
            {use class="yii\bootstrap5\Html"}{use class="kartik\select2\Select2" as="Picker"}
            {if ${'$'}a}{menu}{/if}{box}{/box}{Picker name="x"}{Html::a('x')}
            {${'$'}p|money|escape|upper}
            {<warning descr="Unknown tag 'boxx'">boxx</warning>}{${'$'}p|<warning descr="Unknown modifier 'monee'">monee</warning>}
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, false)
    }
}
