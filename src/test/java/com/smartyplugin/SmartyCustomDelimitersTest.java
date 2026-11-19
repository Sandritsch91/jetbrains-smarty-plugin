package com.smartyplugin;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.smartyplugin.lang.SmartyDelimiters;
import com.smartyplugin.lang.SmartyLanguage;
import com.smartyplugin.lang.SmartySettings;
import com.smartyplugin.lexer.SmartyLexer;
import com.smartyplugin.parser.SmartyElementTypes;

import java.util.List;

/** Smarty's left_delimiter / right_delimiter set to "{{" / "}}" for the project. */
public class SmartyCustomDelimitersTest extends BasePlatformTestCase {
    private static final SmartyDelimiters BRACES = new SmartyDelimiters("{{", "}}");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SmartySettings.getInstance(getProject()).setDelimiters(BRACES);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            SmartySettings.getInstance(getProject()).setDelimiters(SmartyDelimiters.DEFAULT);
        } finally {
            super.tearDown();
        }
    }

    // ------------------------------------------------------------ lexer

    private static String lex(SmartyDelimiters d, String text) {
        SmartyLexer lexer = new SmartyLexer(d);
        lexer.start(text, 0, text.length(), 0);
        StringBuilder sb = new StringBuilder();
        IElementType t;
        while ((t = lexer.getTokenType()) != null) {
            String name = t.toString().replace("Smarty:", "");
            if (!name.equals("WHITE_SPACE")) {
                sb.append(name).append('(').append(text, lexer.getTokenStart(), lexer.getTokenEnd()).append(") ");
            }
            lexer.advance();
        }
        return sb.toString().trim();
    }

    public void testLexer() {
        assertEquals("TEMPLATE_TEXT(<p>{if $a}x</p>) LDELIM({{) VARIABLE($b) PIPE(|) MODIFIER_NAME(upper) RDELIM(}}) "
                        + "COMMENT({{* note *}}) TEMPLATE_TEXT({{ not a tag }})",
                lex(BRACES, "<p>{if $a}x</p>{{$b|upper}}{{* note *}}{{ not a tag }}"));
    }

    public void testLexerHtmlCommentDelimiters() {
        SmartyDelimiters html = new SmartyDelimiters("<!--{", "}-->");
        assertEquals("TEMPLATE_TEXT(<p>) LDELIM(<!--{) TAG_NAME(if) VARIABLE($a) RDELIM(}-->) TEMPLATE_TEXT(x) "
                        + "LDELIM(<!--{) SLASH(/) TAG_NAME(if) RDELIM(}-->)",
                lex(html, "<p><!--{if $a}-->x<!--{/if}-->"));
    }

    public void testLexerLiteralAndStrings() {
        assertEquals("LDELIM({{) TAG_NAME(literal) RDELIM(}}) TEMPLATE_TEXT({{$x}} {a: 1}) LDELIM({{) SLASH(/) "
                        + "TAG_NAME(literal) RDELIM(}}) LDELIM({{) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) "
                        + "STRING(\"a }} b {{$c}}\") RDELIM(}})",
                lex(BRACES, "{{literal}}{{$x}} {a: 1}{{/literal}}{{x a=\"a }} b {{$c}}\"}}"));
    }

    public void testLexerRestartsAnywhere() {
        String text = "<p>{{if $a}}x{{literal}}{{y}}{{/literal}}{{$b|upper}}{{/if}}</p>";
        SmartyLexer full = new SmartyLexer(BRACES);
        full.start(text, 0, text.length(), 0);
        while (full.getTokenType() != null) {
            int start = full.getTokenStart();
            SmartyLexer partial = new SmartyLexer(BRACES);
            partial.start(text, start, text.length(), full.getState());
            SmartyLexer reference = new SmartyLexer(BRACES);
            reference.start(text, 0, text.length(), 0);
            while (reference.getTokenStart() < start) reference.advance();
            while (reference.getTokenType() != null) {
                assertEquals(reference.getTokenType(), partial.getTokenType());
                assertEquals(reference.getTokenEnd(), partial.getTokenEnd());
                reference.advance();
                partial.advance();
            }
            full.advance();
        }
    }

    // ------------------------------------------------------------ project setting

    public void testParsingAndErrors() {
        myFixture.configureByText("a.tpl", """
                <div>{if $old}x{/if}</div>
                {{if $a}}<p>{{$b|escape}}</p>{{else}}none{{/if}}
                {{foreach $items as $i}}{{$i}}{{/foreach}}
                <error descr="Missing closing tag {{/block}}">{{block name="x"}}</error>
                """);
        myFixture.checkHighlighting(false, false, false);
        PsiFile smarty = myFixture.getFile().getViewProvider().getPsi(SmartyLanguage.INSTANCE);
        assertEquals("{if $old} is plain text", 3, PsiTreeUtil.collectElements(smarty,
                e -> e.getNode().getElementType() == SmartyElementTypes.BLOCK).length);
    }

    public void testClosingTagOnTyping() {
        myFixture.configureByText("a.tpl", "<p>{{foreach $a as $b}<caret></p>");
        myFixture.type('}');
        myFixture.checkResult("<p>{{foreach $a as $b}}<caret>{{/foreach}}</p>");
    }

    public void testSlashCompletesBlock() {
        myFixture.configureByText("a.tpl", "{{if $a}}x{{<caret>");
        myFixture.type('/');
        myFixture.checkResult("{{if $a}}x{{/if}}<caret>");
    }

    public void testEnterBetweenTags() {
        myFixture.configureByText("a.tpl", "{{if $a}}<caret>{{/if}}");
        myFixture.type('\n');
        myFixture.checkResult("{{if $a}}\n    <caret>\n{{/if}}");
    }

    public void testTagCompletion() {
        myFixture.configureByText("a.tpl", "{{stri<caret>");
        myFixture.completeBasic();
        myFixture.checkResult("{{strip}}<caret>{{/strip}}");
    }

    public void testBlockComment() {
        myFixture.configureByText("a.tpl", "<selection>{{$a}}</selection>");
        myFixture.performEditorAction("CommentByBlockComment");
        myFixture.checkResult("{{*{{$a}}*}}");
    }

    public void testLineComment() {
        myFixture.configureByText("a.tpl", "<p>{{$a}}<caret></p>");
        myFixture.performEditorAction("CommentByLineComment");
        assertEquals("{{*<p>{{$a}}</p>*}}", myFixture.getEditor().getDocument().getText());
    }

    public void testFormatting() {
        myFixture.configureByText("a.tpl", "{{if $a==1}}\n<p>x</p>\n{{/if}}\n");
        WriteCommandAction.runWriteCommandAction(getProject(),
                (Runnable) () -> CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile()));
        assertEquals("{{if $a == 1}}\n    <p>x</p>\n{{/if}}\n", myFixture.getEditor().getDocument().getText());
    }

    public void testNoParseErrors() {
        myFixture.configureByText("a.tpl", "{{assign var=\"x\" value=[1, 2]}}{{$x|@count}}");
        PsiFile smarty = myFixture.getFile().getViewProvider().getPsi(SmartyLanguage.INSTANCE);
        List<PsiErrorElement> errors = List.copyOf(PsiTreeUtil.findChildrenOfType(smarty, PsiErrorElement.class));
        assertEquals(List.of(), errors);
    }
}
