package com.smartyplugin

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartyFormatterTest : BasePlatformTestCase() {

    private fun doTest(before: String, after: String) {
        myFixture.configureByText("test.tpl", before)
        WriteCommandAction.runWriteCommandAction(project, Runnable {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        })
        assertEquals(after, myFixture.editor.document.text)
    }

    /** A template written as an indented raw string: common indent removed, ending with a line break. */
    private fun block(text: String): String = text.trimIndent() + "\n"

    fun testTopLevelBlock() {
        doTest(
            block(
                $$"""
                {if $a}
                <div>x</div>
                {else}
                <p>y</p>
                {/if}
                """,
            ),
            block(
                $$"""
                {if $a}
                    <div>x</div>
                {else}
                    <p>y</p>
                {/if}
                """,
            ),
        )
    }

    fun testBlockInsideHtmlTag() {
        doTest(
            block(
                $$"""
                <ul>
                {foreach $items as $i}
                <li>{$i}</li>
                {/foreach}
                </ul>
                """,
            ),
            block(
                $$"""
                <ul>
                    {foreach $items as $i}
                        <li>{$i}</li>
                    {/foreach}
                </ul>
                """,
            ),
        )
    }

    fun testNestedSmartyBlocks() {
        doTest(
            block(
                $$"""
                {block name="content"}
                {if $x}
                <p>a</p>
                {/if}
                {include file="x.tpl"}
                {/block}
                """,
            ),
            block(
                $$"""
                {block name="content"}
                    {if $x}
                        <p>a</p>
                    {/if}
                    {include file="x.tpl"}
                {/block}
                """,
            ),
        )
    }

    fun testMixedNesting() {
        doTest(
            block(
                $$"""
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
                """,
            ),
            block(
                $$"""
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
                """,
            ),
        )
    }

    fun testCommentsAndInlineTags() {
        doTest(
            block(
                $$"""
                <div>
                {* a comment *}
                {include file="a.tpl"}
                <span class="{$cls}">{$text|escape}</span>
                </div>
                """,
            ),
            block(
                $$"""
                <div>
                    {* a comment *}
                    {include file="a.tpl"}
                    <span class="{$cls}">{$text|escape}</span>
                </div>
                """,
            ),
        )
    }

    fun testAlreadyFormattedIsStable() {
        val text = block(
            $$"""
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
            """,
        )
        doTest(text, text)
    }

    // ------------------------------------------------------------ {registerJs} / {registerCss}

    fun testCodeBlocksKeepTheirStructure() {
        doTest(
            block(
                $$"""
                <div>
                {registerJs}
                console.log('x');
                var test = '{$model}';
                $(function () {
                if (test) {
                console.log([
                1,
                2
                ]);
                }
                });
                {/registerJs}
                {registerCss}
                * {
                box-sizing: border-box;
                }
                {/registerCss}
                </div>
                """,
            ),
            block(
                $$"""
                <div>
                    {registerJs}
                        console.log('x');
                        var test = '{$model}';
                        $(function () {
                            if (test) {
                                console.log([
                                    1,
                                    2
                                ]);
                            }
                        });
                    {/registerJs}
                    {registerCss}
                        * {
                            box-sizing: border-box;
                        }
                    {/registerCss}
                </div>
                """,
            ),
        )
    }

    fun testCodeBlockAtTopLevelIgnoresBracesInStringsAndComments() {
        doTest(
            block(
                """
                {registerJs}
                var s = "{ not a block";
                // also not {
                /* nor { this */
                foo({
                a: '}'
                });
                {/registerJs}
                """,
            ),
            block(
                """
                {registerJs}
                    var s = "{ not a block";
                    // also not {
                    /* nor { this */
                    foo({
                        a: '}'
                    });
                {/registerJs}
                """,
            ),
        )
    }

    /**
     * The JavaScript / CSS formatters format the code, spacing and line breaks included.
     * ("a{color:red}" would be a Smarty tag; a "{" followed by whitespace is CSS.)
     */
    fun testCodeUsesLanguageFormatters() {
        doTest(
            block(
                """
                <div>
                {registerJs}
                if(ready){
                start( 1,2 );
                }
                {/registerJs}
                {registerCss}
                a{ color:red;margin:0 }
                {/registerCss}
                </div>
                """,
            ),
            block(
                """
                <div>
                    {registerJs}
                        if (ready) {
                            start(1, 2);
                        }
                    {/registerJs}
                    {registerCss}
                        a {
                            color: red;
                            margin: 0
                        }
                    {/registerCss}
                </div>
                """,
            ),
        )
    }

    /** Smarty tags in the code stay as they are; the code around them is formatted by the JavaScript formatter. */
    fun testCodeWithTagsUsesLanguageFormatter() {
        doTest(
            block(
                $$"""
                {registerJs}
                var id={$model->id};
                var name = '{$model->name|escape:'javascript'}';
                if(id>0){
                load( id );
                }
                {/registerJs}
                """,
            ),
            block(
                $$"""
                {registerJs}
                    var id = {$model->id};
                    var name = '{$model->name|escape:'javascript'}';
                    if (id > 0) {
                        load(id);
                    }
                {/registerJs}
                """,
            ),
        )
    }

    fun testFormattedCodeBlockIsStable() {
        val text = block(
            $$"""
            {registerJs}
                $(function () {
                    console.log('notifications form js');
                });
            {/registerJs}
            """,
        )
        doTest(text, text)
    }

    // ------------------------------------------------------------ inside tags

    fun testSpacingInsideTags() {
        doTest(
            block(
                $$"""
                {if $a==1&&!$b  or $c>-1}x{/if}
                {include file="a.tpl"   title=$t }
                {$name | escape : 'html'|default:"x"}
                {foreach $list as $k=>$v}{$v@iteration}{/foreach}
                {$items[ 0 ]}{$obj->name}{$user.name}{Foo::BAR}
                {assign var=x value=[ 1,2 ,3 ]}
                {$x=isset( $a )}
                """,
            ),
            block(
                $$"""
                {if $a == 1 && !$b or $c > -1}x{/if}
                {include file="a.tpl" title=$t}
                {$name|escape:'html'|default:"x"}
                {foreach $list as $k => $v}{$v@iteration}{/foreach}
                {$items[0]}{$obj->name}{$user.name}{Foo::BAR}
                {assign var=x value=[1, 2, 3]}
                {$x=isset($a)}
                """,
            ),
        )
    }

    fun testTagsInsideHtml() {
        doTest(
            block(
                $$"""
                <a href="{url route='site/index'  id=$id}" class="{if $active }active{/if}">{$title|truncate:30:"..."  }</a>
                """,
            ),
            block(
                $$"""
                <a href="{url route='site/index' id=$id}" class="{if $active}active{/if}">{$title|truncate:30:"..."}</a>
                """,
            ),
        )
    }

    fun testSpacingKeptWhereAmbiguous() {
        val text = block(
            $$"""
            {$a = 1}{$b=2}
            {for $i=0 to 3}{$i}{/for}
            {$x|default:$y + 1}
            {$a . $b}{$c.d}
            {if ($a)}x{/if}
            """,
        )
        doTest(text, text)
    }

    fun testMultiLineTag() {
        doTest(
            block(
                """
                <div class="mb-3">
                <label for="icon" class="form-label">{'Icon'|t:'hq-re/notifications'}</label>
                {Select2 name='icon'
                theme=Select2::THEME_BOOTSTRAP id='icon' pluginOptions=[
                'allowClear'=>true,
                'minimumInputLength' => 3,
                'ajax' => [
                'url' => {url route="list"},
                'dataType' => 'json',
                'data' => Yii::createObject('\yii\web\JsExpression', [
                'function (params) { return {q: params.term, iconOnly: true}; }'
                ])
                ],
                'escapeMarkup' => Yii::createObject('\yii\web\JsExpression', ['function (markup) { return markup; }']),
                ]}
                </div>
                """,
            ),
            block(
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
                        ]}
                </div>
                """,
            ),
        )
    }

    fun testYiiActiveFormIsStable() {
        val text = block(
            $$"""
            {use class="yii\bootstrap5\ActiveForm" type="block"}

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
            """,
        )
        doTest(text, text)
    }

    fun testClosingBraceOnOwnLine() {
        doTest(
            block(
                """
                {include
                file="a.tpl"
                title="x"
                        }
                """,
            ),
            block(
                """
                {include
                    file="a.tpl"
                    title="x"
                }
                """,
            ),
        )
    }

    fun testMultiLineStringIsNotReindented() {
        val text = block(
            """
            {assign var=js value='line one
              line two'}
            """,
        )
        doTest(text, text)
    }

    fun testArrayAssignmentInsideBlock() {
        val text = block(
            $$"""
            {if $app->user->can('relevanceAdministrator')}
                {$buttons[] = [
                    'icon' => 'file-excel',
                    'label' => 'Excel',
                    'url' => {path route="excel"}|cat:'?'|cat:http_build_query($app->request->get()),
                    'options' => [
                        'class' => ['btn', 'btn-secondary'],
                        'data' => ['pjax' => 0]
                    ]
                ]}
            {/if}
            """,
        )
        doTest(text, text)
    }

    fun testArrayAssignmentInNestedBlocksAndHtml() {
        val text = block(
            $$"""
            <div>
                {foreach $items as $item}
                    {if $item}
                        {$buttons[] = [
                            'label' => $item,
                            'options' => [
                                'class' => ['btn']
                            ]
                        ]}
                    {/if}
                {/foreach}
            </div>
            """,
        )
        doTest(text, text)
    }
}
