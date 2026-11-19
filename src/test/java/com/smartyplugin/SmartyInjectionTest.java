package com.smartyplugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/** {registerJs} and {registerCss} (yii2-smarty) contain JavaScript and CSS. */
public class SmartyInjectionTest extends BasePlatformTestCase {

    private static final String TEMPLATE = """
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
            """;

    private String injectedLanguageAt(String text) {
        PsiFile file = myFixture.getFile();
        int offset = file.getText().indexOf(text);
        assertTrue(text, offset >= 0);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(file, offset + 1);
        return injected == null ? null : injected.getLanguage().getID();
    }

    public void testJavaScriptAndCss() {
        myFixture.configureByText("form.tpl", TEMPLATE);
        assertEquals("JavaScript", injectedLanguageAt("console.log"));
        assertEquals("CSS", injectedLanguageAt("box-sizing"));
    }

    public void testSmartyTagsInsideStaySmarty() {
        myFixture.configureByText("form.tpl", TEMPLATE);
        assertNull("the Smarty tag is not part of the JavaScript", injectedLanguageAt("$model->id"));
        assertEquals("code after the tag belongs to the same script", "JavaScript", injectedLanguageAt("var after"));
    }

    public void testSmartyTagIsNotASyntaxError() {
        myFixture.configureByText("form.tpl", TEMPLATE);
        List<String> errors = myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
                .map(h -> h.getDescription() + " at " + h.getStartOffset()).toList();
        assertEquals(List.of(), errors);
    }

    public void testOtherBlocksAreNotInjected() {
        myFixture.configureByText("form.tpl", "{block name='x'}console.log(1);{/block}");
        assertNull(injectedLanguageAt("console.log"));
    }

    public void testReformatWithInjectedCode() {
        myFixture.configureByText("form.tpl", TEMPLATE);
        WriteCommandAction.runWriteCommandAction(getProject(),
                (Runnable) () -> CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile()));
        String text = myFixture.getEditor().getDocument().getText();
        assertTrue(text, text.contains("console.log('notifications form js');"));
        assertTrue(text, text.contains("{/registerCss}"));
    }
}
