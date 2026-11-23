package com.smartyplugin

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** {extends} and {block}: block name completion, navigation to the overridden block, gutter icons. */
class SmartyInheritanceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "templates/layout.tpl",
            "<title>{block name=\"title\"}Site{/block}</title>\n<body>{block 'content'}{/block}{block name=footer}{/block}</body>\n",
        )
        myFixture.addFileToProject(
            "templates/pages/base.tpl",
            "{extends file=\"layout.tpl\"}\n{block name=\"content\"}<main>{block name=\"main\"}{/block}</main>{/block}\n",
        )
    }

    private fun configure(text: String) {
        val file = myFixture.addFileToProject("templates/pages/user.tpl", text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
    }

    fun testBlockNamesFromAllLayouts() {
        configure("{extends 'pages/base.tpl'}\n{block name=\"<caret>\"}{/block}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings!!
        assertSameElements(items, "title", "content", "footer", "main")
    }

    fun testNavigateToOverriddenBlock() {
        configure("{extends 'pages/base.tpl'}\n{block name=\"title\"}User{/block}{block name=\"main\"}x{/block}")
        val text = myFixture.editor.document.text
        val title = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("\"title\"") + 2)
        assertEquals("layout.tpl", title?.containingFile?.name)
        assertEquals("\"title\"", title?.text)

        val main = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("\"main\"") + 2)
        assertEquals("base.tpl", main?.containingFile?.name)
    }

    fun testGutterIcons() {
        configure("{extends 'pages/base.tpl'}\n{block name=\"content\"}x{/block}")
        // the middle template overrides content of the layout and is overridden by user.tpl
        val base = myFixture.findFileInTempDir("templates/pages/base.tpl")
        myFixture.configureFromExistingVirtualFile(base)
        val tooltips = myFixture.findAllGutters().mapNotNull { it.tooltipText }
        assertTrue(tooltips.toString(), tooltips.any { it.contains("Overrides block 'content' in layout.tpl") })
        assertTrue(tooltips.toString(), tooltips.any { it.contains("Overridden in user.tpl") })

        // a block nobody overrides and that overrides nothing has no icon
        val layout = myFixture.findFileInTempDir("templates/layout.tpl")
        myFixture.configureFromExistingVirtualFile(layout)
        val layoutTips = myFixture.findAllGutters().mapNotNull { it.tooltipText }
        assertTrue(layoutTips.toString(), layoutTips.any { it.contains("Overridden in base.tpl") })
        assertEquals(layoutTips.toString(), 1, layoutTips.count { it.contains("Overridden") })
    }
}

/** {#vars#} from the .conf files loaded with {config_load}, also in the layout. */
class SmartyConfigVarsTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "configs/site.conf",
            "# site settings\npageTitle = \"Main Menu\"\nbodyBgColor = #000000\n\n[Customer]\npageTitle = \"Customer Info\"\nrowColor = blue\n",
        )
        myFixture.addFileToProject("configs/other.conf", "unrelated = 1\n")
        myFixture.addFileToProject("templates/layout.tpl", "{config_load file='../configs/site.conf' section='Customer'}{block 'content'}{/block}")
    }

    private fun configure(text: String) {
        val file = myFixture.addFileToProject("templates/page.tpl", text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
    }

    fun testCompletionFromLayoutConfig() {
        configure("{extends 'layout.tpl'}{block 'content'}{#<caret>}{/block}")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "pageTitle", "bodyBgColor", "rowColor")
    }

    fun testCompletionInsertsClosingHash() {
        configure("{config_load '../configs/site.conf'}{#row<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{config_load '../configs/site.conf'}{#rowColor#<caret>}")
    }

    fun testCompletionInsideClosedVariable() {
        configure("{config_load '../configs/site.conf'}{#row<caret>#}")
        myFixture.completeBasic()
        myFixture.checkResult("{config_load '../configs/site.conf'}{#rowColor#<caret>}")
    }

    fun testNavigation() {
        configure("{config_load '../configs/site.conf'}{#pageTitle#}")
        val offset = myFixture.editor.document.text.indexOf("#pageTitle") + 3
        val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, offset)
        assertEquals(2, targets.size) // global and [Customer]
        assertEquals("site.conf", targets[0].containingFile.name)
        assertEquals(16, targets[0].textOffset) // after the comment line
    }
}
