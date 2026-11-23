package com.smartyplugin

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Live templates, TODO comments and quick fixes. */
class SmartyConveniencesTest : BasePlatformTestCase() {

    fun testLiveTemplate() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("a.tpl", "<p><caret></p>")
        val applicable = TemplateManagerImpl.listApplicableTemplates(
            com.intellij.codeInsight.template.TemplateActionContext.expanding(myFixture.file, myFixture.editor),
        ).map { it.key }
        assertTrue(applicable.toString(), applicable.containsAll(listOf("if", "fe", "block", "inc")))

        val template = com.intellij.codeInsight.template.impl.TemplateSettings.getInstance().getTemplate("fe", "Smarty")!!
        com.intellij.codeInsight.template.TemplateManager.getInstance(project).startTemplate(myFixture.editor, template)
        // accept the default of each variable
        repeat(2) { myFixture.type('\t') }
        assertEquals("<p>{foreach \$items as \$item}{/foreach}</p>", myFixture.editor.document.text)
    }

    fun testTodoInComments() {
        myFixture.configureByText("a.tpl", "<p>{* TODO translate *}</p>{\$x}{* nothing *}")
        val items = PsiTodoSearchHelper.getInstance(project).findTodoItems(myFixture.file)
        assertEquals(1, items.size)
        assertEquals("TODO translate", myFixture.file.text.substring(items[0].textRange.startOffset, items[0].textRange.endOffset))
    }

    fun testInsertMissingClosingTag() {
        myFixture.configureByText("a.tpl", "{foreach \$a as \$b}\n  {if \$b}<caret>x\n{/foreach}\n")
        myFixture.launchAction(myFixture.findSingleIntention("Insert {/if}"))
        myFixture.checkResult("{foreach \$a as \$b}\n  {if \$b}x{/if}\n{/foreach}\n")
    }

    fun testRemoveStrayClosingTag() {
        myFixture.configureByText("a.tpl", "a{/i<caret>f}b")
        myFixture.launchAction(myFixture.findSingleIntention("Remove {/if}"))
        myFixture.checkResult("ab")
    }

}
