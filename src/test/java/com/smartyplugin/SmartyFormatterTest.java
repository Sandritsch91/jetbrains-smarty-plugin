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
}
