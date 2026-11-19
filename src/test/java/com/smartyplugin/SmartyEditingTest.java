package com.smartyplugin;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.smartyplugin.lang.SmartyLanguage;
import com.smartyplugin.psi.SmartyStringLiteral;

import java.util.Collection;
import java.util.List;

public class SmartyEditingTest extends BasePlatformTestCase {

    // ------------------------------------------------------------ file type

    /** PhpStorm bundles JetBrains' Smarty plugin, which also registers *.tpl. */
    public void testTplFilesUseThisPlugin() {
        com.intellij.openapi.fileTypes.FileType type =
                com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByFileName("page.tpl");
        assertSame(com.smartyplugin.lang.SmartyFileType.INSTANCE, type);
    }

    // ------------------------------------------------------------ parsing / annotator

    public void testValidTemplateHasNoErrors() {
        myFixture.configureByText("ok.tpl", """
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
                """);
        PsiFile smarty = myFixture.getFile().getViewProvider().getPsi(SmartyLanguage.INSTANCE);
        assertNull(PsiTreeUtil.findChildOfType(smarty, PsiErrorElement.class));
        myFixture.checkHighlighting(false, false, false);
    }

    /** Yii2 widget call with PHP arrays, static calls, a nested tag and JS code in strings. */
    public void testYiiWidgetWithJsInStrings() {
        myFixture.configureByText("widget.tpl", """
                <div class="mb-3">
                    <label for="icon" class="form-label">{'Icon'|t:'hq-re/notifications'}</label>
                    {Select2 name='icon'
                        theme=Select2::THEME_BOOTSTRAP id='icon' pluginOptions=[
                        'allowClear' => true,
                        'minimumInputLength' => 3,
                        'ajax' => [
                            'url' => {url route="list"},
                            'dataType' => 'json',
                            'data' => Yii::createObject('\\yii\\web\\JsExpression', [
                                'function (params) { return {q: params.term, iconOnly: true}; }'
                            ])
                        ],
                        'escapeMarkup' => Yii::createObject('\\yii\\web\\JsExpression', ['function (markup) { return markup; }']),
                        'templateResult' => Yii::createObject('\\yii\\web\\JsExpression', ['function (symbol) { return symbol.text; }'])
                    ]}
                </div>
                """);
        PsiFile smarty = myFixture.getFile().getViewProvider().getPsi(SmartyLanguage.INSTANCE);
        PsiErrorElement error = PsiTreeUtil.findChildOfType(smarty, PsiErrorElement.class);
        assertNull(error == null ? null : error.getErrorDescription() + " at " + error.getTextOffset(), error);
        myFixture.checkHighlighting(false, false, false);
    }

    public void testStructuralErrors() {
        myFixture.configureByText("bad.tpl",
                "<error descr=\"Missing closing tag {/if}\">{if $a}</error>\n" +
                "<error descr=\"Closing tag {/foreach} has no matching opening tag\">{/foreach}</error>\n");
        myFixture.checkHighlighting(false, false, false);
    }

    public void testMisplacedElse() {
        myFixture.configureByText("bad.tpl",
                "<error descr=\"{else} is only allowed inside {if}\">{else}</error>\n");
        myFixture.checkHighlighting(false, false, false);
    }

    // ------------------------------------------------------------ completion

    public void testTagNameCompletion() {
        myFixture.configureByText("c.tpl", "<div>{inc<caret></div>");
        myFixture.completeBasic();
        assertEquals("<div>{include </div>", myFixture.getEditor().getDocument().getText());
    }

    public void testBlockTagCompletionWithoutArguments() {
        myFixture.configureByText("c.tpl", "{stri<caret>");
        myFixture.completeBasic();
        myFixture.checkResult("{strip}<caret>{/strip}");
    }

    public void testModifierCompletion() {
        myFixture.configureByText("c.tpl", "{$name|upp<caret>}");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertEquals("upper", items.get(0)); // Smarty modifiers rank above PHP functions
        myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR);
        myFixture.checkResult("{$name|upper<caret>}");
    }

    public void testVariableCompletion() {
        myFixture.configureByText("c.tpl", "{assign var=\"fooBar\" value=1}{foreach $list as $entry}{/foreach}{$f<caret>}");
        myFixture.completeBasic();
        myFixture.checkResult("{assign var=\"fooBar\" value=1}{foreach $list as $entry}{/foreach}{$fooBar<caret>}");
    }

    public void testAttributeCompletion() {
        myFixture.configureByText("c.tpl", "{include fi<caret>}");
        myFixture.completeBasic();
        myFixture.checkResult("{include file=<caret>}");
    }

    public void testClosingTagCompletion() {
        myFixture.configureByText("c.tpl", "{foreach $a as $b}{if $b}x{/if}{/fo<caret>");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertEquals("the open block comes first", "foreach", items.get(0));
        myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR);
        assertText("{foreach $a as $b}{if $b}x{/if}{/foreach}<caret>");
    }

    public void testSmartyPropertyCompletion() {
        myFixture.configureByText("c.tpl", "{$smarty.<caret>}");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.contains("now"));
        assertTrue(items.contains("capture"));
    }

    // ------------------------------------------------------------ typing

    public void testClosingBraceInsertsEndTag() {
        myFixture.configureByText("t.tpl", "<p>{foreach $a as $b<caret></p>");
        myFixture.type('}');
        myFixture.checkResult("<p>{foreach $a as $b}<caret>{/foreach}</p>");
    }

    public void testClosingBraceOnInlineTagDoesNothing() {
        myFixture.configureByText("t.tpl", "{include file='a.tpl'<caret>");
        myFixture.type('}');
        myFixture.checkResult("{include file='a.tpl'}<caret>");
    }

    public void testSlashCompletesInnermostBlock() {
        myFixture.configureByText("t.tpl", "{if $a}\n  {foreach $l as $i}\n    x\n  {<caret>\n{/if}");
        myFixture.type('/');
        assertText("{if $a}\n  {foreach $l as $i}\n    x\n  {/foreach}<caret>\n{/if}");
    }

    public void testSlashDoesNothingWhenEverythingIsClosed() {
        myFixture.configureByText("t.tpl", "{if $a}x{/if}{<caret>");
        myFixture.type('/');
        myFixture.checkResult("{if $a}x{/if}{/<caret>");
    }

    public void testEnterBetweenTags() {
        myFixture.configureByText("t.tpl", "{if $a}<caret>{/if}");
        myFixture.type('\n');
        assertText("{if $a}\n    <caret>\n{/if}");
    }

    public void testCommentLine() {
        myFixture.configureByText("t.tpl", "{$a<caret>}");
        myFixture.performEditorAction("CommentByBlockComment");
        String text = myFixture.getEditor().getDocument().getText();
        assertTrue(text, text.contains("{*") && text.contains("*}"));
    }

    private void assertText(String expectedWithCaret) {
        String text = myFixture.getEditor().getDocument().getText();
        int caret = myFixture.getEditor().getCaretModel().getOffset();
        assertEquals(expectedWithCaret, text.substring(0, caret) + "<caret>" + text.substring(caret));
    }

    // ------------------------------------------------------------ navigation

    /** Strings in print tags have no tag name; asking them for references (Find Usages does) must not fail. */
    public void testStringsInPrintTagsHaveNoReferences() {
        myFixture.configureByText("_form.tpl", """
                {use class="yii\\bootstrap5\\ActiveForm" type="block"}
                {ActiveForm assign="form" id="wedoConnectionForm"}
                    {$form->field($model, 'token', [
                        'options' => [
                            'class' => ['col-12', 'col-lg-6']
                        ]
                    ])->textInput()}
                    {'Icon'|t:'app'}
                {/ActiveForm}
                """);
        PsiFile smarty = myFixture.getFile().getViewProvider().getPsi(SmartyLanguage.INSTANCE);
        Collection<SmartyStringLiteral> strings = PsiTreeUtil.findChildrenOfType(smarty, SmartyStringLiteral.class);
        assertTrue(strings.size() > 5);
        for (SmartyStringLiteral s : strings) {
            assertFalse(s.getText(), s.isTemplatePath());
            assertEquals(s.getText(), 0, s.getReferences().length);
        }
    }

    public void testIncludeResolvesRelativeToTemplateRoots() {
        myFixture.addFileToProject("views/layouts/main.tpl", "<html>{block name=\"body\"}{/block}</html>");
        PsiFile page = myFixture.addFileToProject("views/site/index.tpl", "{extends file=\"layouts/main.tpl\"}");
        myFixture.configureFromExistingVirtualFile(page.getVirtualFile());
        PsiFile smarty = myFixture.getFile().getViewProvider().getPsi(SmartyLanguage.INSTANCE);
        Collection<SmartyStringLiteral> strings = PsiTreeUtil.findChildrenOfType(smarty, SmartyStringLiteral.class);
        assertEquals(1, strings.size());
        PsiReference[] refs = strings.iterator().next().getReferences();
        assertTrue(refs.length > 0);
        PsiElement target = refs[refs.length - 1].resolve();
        assertNotNull("include path should resolve", target);
        assertEquals("main.tpl", ((PsiFile) target).getName());
    }
}
