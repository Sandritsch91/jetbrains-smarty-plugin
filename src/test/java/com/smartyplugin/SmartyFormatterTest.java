package com.smartyplugin;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class SmartyFormatterTest extends BasePlatformTestCase {

    private void doTest(String before, String after) {
        myFixture.configureByText("test.tpl", before);
        WriteCommandAction.runWriteCommandAction(getProject(),
                (Runnable) () -> CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile()));
        assertEquals(after, myFixture.getEditor().getDocument().getText());
    }

    public void testTopLevelBlock() {
        doTest("""
                {if $a}
                <div>x</div>
                {else}
                <p>y</p>
                {/if}
                """, """
                {if $a}
                    <div>x</div>
                {else}
                    <p>y</p>
                {/if}
                """);
    }

    public void testBlockInsideHtmlTag() {
        doTest("""
                <ul>
                {foreach $items as $i}
                <li>{$i}</li>
                {/foreach}
                </ul>
                """, """
                <ul>
                    {foreach $items as $i}
                        <li>{$i}</li>
                    {/foreach}
                </ul>
                """);
    }

    public void testNestedSmartyBlocks() {
        doTest("""
                {block name="content"}
                {if $x}
                <p>a</p>
                {/if}
                {include file="x.tpl"}
                {/block}
                """, """
                {block name="content"}
                    {if $x}
                        <p>a</p>
                    {/if}
                    {include file="x.tpl"}
                {/block}
                """);
    }

    public void testMixedNesting() {
        doTest("""
                <div>
                {if $x}
                <ul>
                {foreach $a as $b}
                {if $b}
                <li>{$b}</li>
                {/if}
                {/foreach}
                </ul>
                {/if}
                </div>
                """, """
                <div>
                    {if $x}
                        <ul>
                            {foreach $a as $b}
                                {if $b}
                                    <li>{$b}</li>
                                {/if}
                            {/foreach}
                        </ul>
                    {/if}
                </div>
                """);
    }

    public void testCommentsAndInlineTags() {
        doTest("""
                <div>
                {* a comment *}
                {include file="a.tpl"}
                <span class="{$cls}">{$text|escape}</span>
                </div>
                """, """
                <div>
                    {* a comment *}
                    {include file="a.tpl"}
                    <span class="{$cls}">{$text|escape}</span>
                </div>
                """);
    }

    public void testAlreadyFormattedIsStable() {
        String text = """
                {extends file="layout.tpl"}
                {block name="body"}
                    <table>
                        {foreach $rows as $row}
                            <tr>
                                <td>{$row.name}</td>
                            </tr>
                        {foreachelse}
                            <tr>
                                <td>none</td>
                            </tr>
                        {/foreach}
                    </table>
                {/block}
                """;
        doTest(text, text);
    }

    // ------------------------------------------------------------ inside tags

    public void testSpacingInsideTags() {
        doTest("""
                {if $a==1&&!$b  or $c>-1}x{/if}
                {include file="a.tpl"   title=$t }
                {$name | escape : 'html'|default:"x"}
                {foreach $list as $k=>$v}{$v@iteration}{/foreach}
                {$items[ 0 ]}{$obj->name}{$user.name}{Foo::BAR}
                {assign var=x value=[ 1,2 ,3 ]}
                {$x=isset( $a )}
                """, """
                {if $a == 1 && !$b or $c > -1}x{/if}
                {include file="a.tpl" title=$t}
                {$name|escape:'html'|default:"x"}
                {foreach $list as $k => $v}{$v@iteration}{/foreach}
                {$items[0]}{$obj->name}{$user.name}{Foo::BAR}
                {assign var=x value=[1, 2, 3]}
                {$x=isset($a)}
                """);
    }

    public void testTagsInsideHtml() {
        doTest("""
                <a href="{url route='site/index'  id=$id}" class="{if $active }active{/if}">{$title|truncate:30:"..."  }</a>
                """, """
                <a href="{url route='site/index' id=$id}" class="{if $active}active{/if}">{$title|truncate:30:"..."}</a>
                """);
    }

    public void testSpacingKeptWhereAmbiguous() {
        String text = """
                {$a = 1}{$b=2}
                {for $i=0 to 3}{$i}{/for}
                {$x|default:$y + 1}
                {$a . $b}{$c.d}
                {if ($a)}x{/if}
                """;
        doTest(text, text);
    }

    public void testMultiLineTag() {
        doTest("""
                <div class="mb-3">
                <label for="icon" class="form-label">{'Icon'|t:'hq-re/notifications'}</label>
                {Select2 name='icon'
                theme=Select2::THEME_BOOTSTRAP id='icon' pluginOptions=[
                'allowClear'=>true,
                'minimumInputLength' => 3,
                'ajax' => [
                'url' => {url route="list"},
                'dataType' => 'json',
                'data' => Yii::createObject('\\yii\\web\\JsExpression', [
                'function (params) { return {q: params.term, iconOnly: true}; }'
                ])
                ],
                'escapeMarkup' => Yii::createObject('\\yii\\web\\JsExpression', ['function (markup) { return markup; }']),
                ]}
                </div>
                """, """
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
                        ]}
                </div>
                """);
    }

    public void testYiiActiveFormIsStable() {
        String text = """
                {use class="yii\\bootstrap5\\ActiveForm" type="block"}

                <div class="card panel-default">
                    <div class="card-body">
                        {ActiveForm assign="form" id="wedoConnectionForm"}
                            <div class="row g-3">
                                {$form->field($model, 'token', [
                                    'options' => [
                                        'class' => ['col-12', 'col-lg-6']
                                    ]
                                ])->textInput()}

                                <div class="w-100 m-0"></div>

                                {$form->field($model, 'bucket_id', [
                                    'options' => [
                                        'class' => ['col-12', 'col-lg-6']
                                    ]
                                ])->widget(Select2::class, [
                                    'data' => $buckets
                                ])}
                            </div>
                        {/ActiveForm}
                    </div>
                </div>
                """;
        doTest(text, text);
    }

    public void testClosingBraceOnOwnLine() {
        doTest("""
                {include
                file="a.tpl"
                title="x"
                        }
                """, """
                {include
                    file="a.tpl"
                    title="x"
                }
                """);
    }

    public void testMultiLineStringIsNotReindented() {
        String text = """
                {assign var=js value='line one
                  line two'}
                """;
        doTest(text, text);
    }
}
