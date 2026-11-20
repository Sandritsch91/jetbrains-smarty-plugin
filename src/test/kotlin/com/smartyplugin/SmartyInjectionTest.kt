package com.smartyplugin

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** {registerJs} and {registerCss} (yii2-smarty) contain JavaScript and CSS. */
class SmartyInjectionTest : BasePlatformTestCase() {

    private fun injectedLanguageAt(text: String): String? {
        val file = myFixture.file
        val offset = file.text.indexOf(text)
        assertTrue(text, offset >= 0)
        val injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, offset + 1)
        return injected?.language?.id
    }

    fun testJavaScriptAndCss() {
        myFixture.configureByText("form.tpl", TEMPLATE)
        assertEquals("JavaScript", injectedLanguageAt("console.log"))
        assertEquals("CSS", injectedLanguageAt("box-sizing"))
    }

    fun testSmartyTagsInsideStaySmarty() {
        myFixture.configureByText("form.tpl", TEMPLATE)
        assertNull("the Smarty tag is not part of the JavaScript", injectedLanguageAt("\$model->id"))
        assertEquals("code after the tag belongs to the same script", "JavaScript", injectedLanguageAt("var after"))
    }

    fun testSmartyTagIsNotASyntaxError() {
        myFixture.configureByText("form.tpl", TEMPLATE)
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).map { "${it.description} at ${it.startOffset}" }
        assertEquals(emptyList<String>(), errors)
    }

    fun testOtherBlocksAreNotInjected() {
        myFixture.configureByText("form.tpl", "{block name='x'}console.log(1);{/block}")
        assertNull(injectedLanguageAt("console.log"))
    }

    fun testReformatWithInjectedCode() {
        myFixture.configureByText("form.tpl", TEMPLATE)
        WriteCommandAction.runWriteCommandAction(project, Runnable {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        })
        val text = myFixture.editor.document.text
        assertTrue(text, text.contains("console.log('notifications form js');"))
        assertTrue(text, text.contains("{/registerCss}"))
    }

    private companion object {
        val TEMPLATE = $$"""
            <div>
                {registerJs}
                    console.log('notifications form js');
                    var id = {$model->id};
                    var after = 1;
                {/registerJs}

                {registerCss}
                    * {
                        box-sizing: border-box;
                    }
                {/registerCss}
            </div>
            """.trimIndent() + "\n"
    }
}
