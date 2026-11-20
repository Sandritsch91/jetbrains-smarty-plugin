package com.smartyplugin

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.smartyplugin.lang.SmartyFileType
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.psi.SmartyStringLiteral

class SmartyEditingTest : BasePlatformTestCase() {

    // ------------------------------------------------------------ file type

    /** PhpStorm bundles JetBrains' Smarty plugin, which also registers *.tpl. */
    fun testTplFilesUseThisPlugin() {
        val type = FileTypeManager.getInstance().getFileTypeByFileName("page.tpl")
        assertSame(SmartyFileType, type)
    }

    // ------------------------------------------------------------ parsing / annotator

    fun testValidTemplateHasNoErrors() {
        myFixture.configureByText(
            "ok.tpl",
            $$"""
                {extends file="layout.tpl"}
                {block name="content"}
                  <h1 class="{if $big}big{else}small{/if}">{$title|escape}</h1>
                  {foreach $items as $item}
                    <p>{$item@iteration}: {$item.name|truncate:30:"..."}</p>
                  {foreachelse}
                    <p>none</p>
                  {/foreach}
                  <script>var a = { b: 1 }; function f() { return a; }</script>
                  {literal}<style>p {color: red}</style>{/literal}
                  {myCustomBlock}inner{/myCustomBlock}
                {/block}
                """.trimIndent() + "\n",
        )
        val smarty = myFixture.file.viewProvider.getPsi(SmartyLanguage)
        assertNull(PsiTreeUtil.findChildOfType(smarty, PsiErrorElement::class.java))
        myFixture.checkHighlighting(false, false, false)
    }

    /** Yii2 widget call with PHP arrays, static calls, a nested tag and JS code in strings. */
    fun testYiiWidgetWithJsInStrings() {
        myFixture.configureByText(
            "widget.tpl",
            """
                <div class="mb-3">
                    <label for="icon" class="form-label">{'Icon'|t:'hq-re/notifications'}</label>
                    {Select2 name='icon'
                        theme=Select2::THEME_BOOTSTRAP id='icon' pluginOptions=[
                        'allowClear' => true,
                        'minimumInputLength' => 3,
                        'ajax' => [
                            'url' => {url route="list"},
                            'dataType' => 'json',
                            'data' => Yii::createObject('\yii\web\JsExpression', [
                                'function (params) { return {q: params.term, iconOnly: true}; }'
                            ])
                        ],
                        'escapeMarkup' => Yii::createObject('\yii\web\JsExpression', ['function (markup) { return markup; }']),
                        'templateResult' => Yii::createObject('\yii\web\JsExpression', ['function (symbol) { return symbol.text; }'])
                    ]}
                </div>
                """.trimIndent() + "\n",
        )
        val smarty = myFixture.file.viewProvider.getPsi(SmartyLanguage)
        val error = PsiTreeUtil.findChildOfType(smarty, PsiErrorElement::class.java)
        assertNull(error?.let { it.errorDescription + " at " + it.textOffset }, error)
        myFixture.checkHighlighting(false, false, false)
    }

    /** Functions, blocks and modifiers of yiisoft/yii2-smarty. */
    fun testYiiSmartyExtension() {
        myFixture.configureByText(
            "yii.tpl",
            $$"""
                {use class="yii\bootstrap5\Html" as="BsHtml" type="static"}
                {set title='Home' layout='main'}
                {title}Home{/title}
                {description}All about us{/description}
                {meta name='robots' content='noindex'}
                {js expression='function () {}' assign='handler'}
                {registerJsFile url='/js/app.js' position='POS_END'}
                {registerJs key='init' position='POS_READY'}console.log(1);{/registerJs}
                {registerCssFile url='/css/app.css'}
                {registerCss}body { color: red; }{/registerCss}
                <a href="{url route='site/index' id=3}">{path route='site/about'}</a>
                {$this->beginPage()|void}
                """.trimIndent() + "\n",
        )
        val smarty = myFixture.file.viewProvider.getPsi(SmartyLanguage)
        val error = PsiTreeUtil.findChildOfType(smarty, PsiErrorElement::class.java)
        assertNull(error?.errorDescription, error)
        myFixture.checkHighlighting(false, false, false)
    }

    fun testYiiTagCompletion() {
        myFixture.configureByText("c.tpl", "{regis<caret>")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings
        assertNotNull(items)
        assertTrue(items.toString(), items!!.containsAll(listOf("registerJs", "registerJsFile", "registerCss", "registerCssFile")))
    }

    fun testYiiBlockWithoutArgumentsInsertsClosingTag() {
        myFixture.configureByText("c.tpl", "{titl<caret>")
        myFixture.completeBasic()
        myFixture.checkResult("{title}<caret>{/title}")
    }

    fun testYiiAttributesAndModifier() {
        myFixture.configureByText("c.tpl", "{use class='x' a<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{use class='x' as=<caret>}")
        myFixture.configureByText("d.tpl", "{\$x|voi<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{\$x|void<caret>}")
    }

    fun testStructuralErrors() {
        myFixture.configureByText(
            "bad.tpl",
            "<error descr=\"Missing closing tag {/if}\">{if \$a}</error>\n" +
                "<error descr=\"Closing tag {/foreach} has no matching opening tag\">{/foreach}</error>\n",
        )
        myFixture.checkHighlighting(false, false, false)
    }

    fun testMisplacedElse() {
        myFixture.configureByText("bad.tpl", "<error descr=\"{else} is only allowed inside {if}\">{else}</error>\n")
        myFixture.checkHighlighting(false, false, false)
    }

    // ------------------------------------------------------------ completion

    fun testTagNameCompletion() {
        myFixture.configureByText("c.tpl", "<div>{inc<caret></div>")
        myFixture.completeBasic()
        assertEquals("<div>{include </div>", myFixture.editor.document.text)
    }

    fun testBlockTagCompletionWithoutArguments() {
        myFixture.configureByText("c.tpl", "{stri<caret>")
        myFixture.completeBasic()
        myFixture.checkResult("{strip}<caret>{/strip}")
    }

    fun testModifierCompletion() {
        myFixture.configureByText("c.tpl", "{\$name|upp<caret>}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings
        assertNotNull(items)
        assertEquals("upper", items!![0]) // Smarty modifiers rank above PHP functions
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        myFixture.checkResult("{\$name|upper<caret>}")
    }

    fun testVariableCompletion() {
        myFixture.configureByText("c.tpl", "{assign var=\"fooBar\" value=1}{foreach \$list as \$entry}{/foreach}{\$f<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{assign var=\"fooBar\" value=1}{foreach \$list as \$entry}{/foreach}{\$fooBar<caret>}")
    }

    fun testAttributeCompletion() {
        myFixture.configureByText("c.tpl", "{include fi<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{include file=<caret>}")
    }

    fun testClosingTagCompletion() {
        myFixture.configureByText("c.tpl", "{foreach \$a as \$b}{if \$b}x{/if}{/fo<caret>")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings
        assertNotNull(items)
        assertEquals("the open block comes first", "foreach", items!![0])
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertText("{foreach \$a as \$b}{if \$b}x{/if}{/foreach}<caret>")
    }

    fun testSmartyPropertyCompletion() {
        myFixture.configureByText("c.tpl", "{\$smarty.<caret>}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings
        assertNotNull(items)
        assertTrue(items!!.contains("now"))
        assertTrue(items.contains("capture"))
    }

    // ------------------------------------------------------------ typing

    fun testClosingBraceInsertsEndTag() {
        myFixture.configureByText("t.tpl", "<p>{foreach \$a as \$b<caret></p>")
        myFixture.type('}')
        myFixture.checkResult("<p>{foreach \$a as \$b}<caret>{/foreach}</p>")
    }

    fun testClosingBraceOnInlineTagDoesNothing() {
        myFixture.configureByText("t.tpl", "{include file='a.tpl'<caret>")
        myFixture.type('}')
        myFixture.checkResult("{include file='a.tpl'}<caret>")
    }

    fun testSlashCompletesInnermostBlock() {
        myFixture.configureByText("t.tpl", "{if \$a}\n  {foreach \$l as \$i}\n    x\n  {<caret>\n{/if}")
        myFixture.type('/')
        assertText("{if \$a}\n  {foreach \$l as \$i}\n    x\n  {/foreach}<caret>\n{/if}")
    }

    fun testSlashDoesNothingWhenEverythingIsClosed() {
        myFixture.configureByText("t.tpl", "{if \$a}x{/if}{<caret>")
        myFixture.type('/')
        myFixture.checkResult("{if \$a}x{/if}{/<caret>")
    }

    fun testEnterBetweenTags() {
        myFixture.configureByText("t.tpl", "{if \$a}<caret>{/if}")
        myFixture.type('\n')
        assertText("{if \$a}\n    <caret>\n{/if}")
    }

    fun testCommentLine() {
        myFixture.configureByText("t.tpl", "{\$a<caret>}")
        myFixture.performEditorAction("CommentByBlockComment")
        val text = myFixture.editor.document.text
        assertTrue(text, text.contains("{*") && text.contains("*}"))
    }

    private fun assertText(expectedWithCaret: String) {
        val text = myFixture.editor.document.text
        val caret = myFixture.editor.caretModel.offset
        assertEquals(expectedWithCaret, text.substring(0, caret) + "<caret>" + text.substring(caret))
    }

    // ------------------------------------------------------------ navigation

    /** Strings in print tags have no tag name; asking them for references (Find Usages does) must not fail. */
    fun testStringsInPrintTagsHaveNoReferences() {
        myFixture.configureByText(
            "_form.tpl",
            $$"""
                {use class="yii\bootstrap5\ActiveForm" type="block"}
                {ActiveForm assign="form" id="wedoConnectionForm"}
                    {$form->field($model, 'token', [
                        'options' => [
                            'class' => ['col-12', 'col-lg-6']
                        ]
                    ])->textInput()}
                    {'Icon'|t:'app'}
                {/ActiveForm}
                """.trimIndent() + "\n",
        )
        val smarty = myFixture.file.viewProvider.getPsi(SmartyLanguage)
        val strings = PsiTreeUtil.findChildrenOfType(smarty, SmartyStringLiteral::class.java)
        assertTrue(strings.size > 5)
        for (s in strings) {
            assertFalse(s.text, s.isTemplatePath())
            assertEquals(s.text, 0, s.references.size)
        }
    }

    fun testIncludeResolvesRelativeToTemplateRoots() {
        myFixture.addFileToProject("views/layouts/main.tpl", "<html>{block name=\"body\"}{/block}</html>")
        val page = myFixture.addFileToProject("views/site/index.tpl", "{extends file=\"layouts/main.tpl\"}")
        myFixture.configureFromExistingVirtualFile(page.virtualFile)
        val smarty = myFixture.file.viewProvider.getPsi(SmartyLanguage)
        val strings = PsiTreeUtil.findChildrenOfType(smarty, SmartyStringLiteral::class.java)
        assertEquals(1, strings.size)
        val refs = strings.iterator().next().references
        assertTrue(refs.isNotEmpty())
        val target = refs[refs.size - 1].resolve()
        assertNotNull("include path should resolve", target)
        assertEquals("main.tpl", (target as PsiFile).name)
    }
}
