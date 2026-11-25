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

    fun testDelimiterTagsAreBraces() {
        myFixture.configureByText("form.tpl", DELIMITERS)
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).map { "${it.description} at ${it.startOffset}" }
        assertEquals(emptyList<String>(), errors)
    }

    fun testAllBranchesOfNestedBlocksAreCode() {
        myFixture.configureByText("form.tpl", DELIMITERS)
        assertEquals("JavaScript", injectedLanguageAt("var that"))
        assertEquals("JavaScript", injectedLanguageAt("closest("))
        assertNull("the tags of the block stay Smarty", injectedLanguageAt("{else}"))
    }

    fun testReformatKeepsInlineBlocks() {
        val text = $$"""
            {registerJs}
                var size = {if $big}10{else}5{/if};
                if (size) {ldelim}
                    {if $debug}console.log(size);{/if}
                {rdelim}
            {/registerJs}
            """.trimIndent() + "\n"
        myFixture.configureByText("form.tpl", text)
        WriteCommandAction.runWriteCommandAction(project, Runnable {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        })
        assertEquals(text, myFixture.editor.document.text)
    }

    fun testReformatWhenTheCodeDoesNotParse() {
        // {$selector} prints the start of a call, e.g. "jQuery('#form').yiiActiveForm('add', ": not known here
        val text = $$"""
            {registerJs}
                {$selector}{ldelim}
                    id: 'field-{$cnt}-label',
                    name: 'Field[{$cnt}][label]',
                    validate: function (attribute, value, messages) {ldelim}
                        yii.validation.required(value, messages, {ldelim}
                            message: '{'{attribute} cannot be blank.'|t:"yii":[
                                'attribute' => $model->getAttributeLabel('label')
                            ]}'
                        {rdelim});
                    {rdelim}
                {rdelim});
            {/registerJs}
            """.trimIndent() + "\n"
        myFixture.configureByText("form.tpl", text.lines().joinToString("\n") { it.trim() })
        WriteCommandAction.runWriteCommandAction(project, Runnable {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        })
        assertEquals(text, myFixture.editor.document.text)
    }

    fun testReformatWithDelimiterTags() {
        myFixture.configureByText("form.tpl", DELIMITERS.lines().joinToString("\n") { it.trim() })
        WriteCommandAction.runWriteCommandAction(project, Runnable {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        })
        assertEquals(DELIMITERS, myFixture.editor.document.text)
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

        /** {ldelim} and {rdelim} print the braces of the JavaScript. */
        val DELIMITERS = $$"""
            {registerJs}
                jQuery('#form').yiiActiveForm('add', {ldelim}
                    id: 'field-{$cnt}-label',
                    validate: function (attribute, value, messages) {ldelim}
                        yii.validation.required(value, messages, {ldelim}
                            message: '{'{attribute} cannot be blank.'|t:"yii":[
                                'attribute' => $model->getAttributeLabel('label')
                            ]}'
                        {rdelim});
                    {rdelim}
                {rdelim});
                jQuery('#delete-{$id}').on('click', function () {ldelim}
                    if (confirm('Sure?')) {ldelim}
                        {if $model->id}
                            var that = this;
                            jQuery.ajax({ldelim}
                                url: '{path route="field/delete" id=$model->id}',
                                method: 'post'
                            {rdelim}).done(function () {
                                jQuery(that).remove();
                            });
                        {else}
                            jQuery(this).closest('.row').remove();
                        {/if}
                    {rdelim}
                {rdelim});
            {/registerJs}
            """.trimIndent() + "\n"
    }
}
