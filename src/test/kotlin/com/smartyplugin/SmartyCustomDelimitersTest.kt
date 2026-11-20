package com.smartyplugin

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.smartyplugin.lang.SmartyDelimiters
import com.smartyplugin.lang.SmartyLanguage
import com.smartyplugin.lang.SmartySettings
import com.smartyplugin.lexer.SmartyLexer
import com.smartyplugin.parser.SmartyElementTypes

/** Smarty's left_delimiter / right_delimiter set to "{{" / "}}" for the project. */
class SmartyCustomDelimitersTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        SmartySettings.getInstance(project).setDelimiters(BRACES)
    }

    override fun tearDown() {
        try {
            SmartySettings.getInstance(project).setDelimiters(SmartyDelimiters.DEFAULT)
        } finally {
            super.tearDown()
        }
    }

    // ------------------------------------------------------------ lexer

    private fun lex(d: SmartyDelimiters, text: String): String {
        val lexer = SmartyLexer(d)
        lexer.start(text, 0, text.length, 0)
        val sb = StringBuilder()
        while (true) {
            val t = lexer.tokenType ?: break
            val name = t.toString().replace("Smarty:", "")
            if (name != "WHITE_SPACE") {
                sb.append(name).append('(').append(text, lexer.tokenStart, lexer.tokenEnd).append(") ")
            }
            lexer.advance()
        }
        return sb.toString().trim()
    }

    fun testLexer() {
        assertEquals(
            "TEMPLATE_TEXT(<p>{if \$a}x</p>) LDELIM({{) VARIABLE(\$b) PIPE(|) MODIFIER_NAME(upper) RDELIM(}}) " +
                "COMMENT({{* note *}}) TEMPLATE_TEXT({{ not a tag }})",
            lex(BRACES, "<p>{if \$a}x</p>{{\$b|upper}}{{* note *}}{{ not a tag }}"),
        )
    }

    fun testLexerHtmlCommentDelimiters() {
        val html = SmartyDelimiters("<!--{", "}-->")
        assertEquals(
            "TEMPLATE_TEXT(<p>) LDELIM(<!--{) TAG_NAME(if) VARIABLE(\$a) RDELIM(}-->) TEMPLATE_TEXT(x) " +
                "LDELIM(<!--{) SLASH(/) TAG_NAME(if) RDELIM(}-->)",
            lex(html, "<p><!--{if \$a}-->x<!--{/if}-->"),
        )
    }

    fun testLexerLiteralAndStrings() {
        assertEquals(
            "LDELIM({{) TAG_NAME(literal) RDELIM(}}) TEMPLATE_TEXT({{\$x}} {a: 1}) LDELIM({{) SLASH(/) " +
                "TAG_NAME(literal) RDELIM(}}) LDELIM({{) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) " +
                "STRING(\"a }} b {{\$c}}\") RDELIM(}})",
            lex(BRACES, "{{literal}}{{\$x}} {a: 1}{{/literal}}{{x a=\"a }} b {{\$c}}\"}}"),
        )
    }

    fun testLexerRestartsAnywhere() {
        val text = "<p>{{if \$a}}x{{literal}}{{y}}{{/literal}}{{\$b|upper}}{{/if}}</p>"
        val full = SmartyLexer(BRACES)
        full.start(text, 0, text.length, 0)
        while (full.tokenType != null) {
            val start = full.tokenStart
            val partial = SmartyLexer(BRACES)
            partial.start(text, start, text.length, full.state)
            val reference = SmartyLexer(BRACES)
            reference.start(text, 0, text.length, 0)
            while (reference.tokenStart < start) reference.advance()
            while (reference.tokenType != null) {
                assertEquals(reference.tokenType, partial.tokenType)
                assertEquals(reference.tokenEnd, partial.tokenEnd)
                reference.advance()
                partial.advance()
            }
            full.advance()
        }
    }

    // ------------------------------------------------------------ project setting

    fun testParsingAndErrors() {
        myFixture.configureByText(
            "a.tpl",
            $$"""
                <div>{if $old}x{/if}</div>
                {{if $a}}<p>{{$b|escape}}</p>{{else}}none{{/if}}
                {{foreach $items as $i}}{{$i}}{{/foreach}}
                <error descr="Missing closing tag {{/block}}">{{block name="x"}}</error>
                """.trimIndent() + "\n",
        )
        myFixture.checkHighlighting(false, false, false)
        val smarty = myFixture.file.viewProvider.getPsi(SmartyLanguage)
        assertEquals(
            "{if \$old} is plain text", 3,
            PsiTreeUtil.collectElements(smarty) { it.node.elementType === SmartyElementTypes.BLOCK }.size,
        )
    }

    fun testClosingTagOnTyping() {
        myFixture.configureByText("a.tpl", "<p>{{foreach \$a as \$b}<caret></p>")
        myFixture.type('}')
        myFixture.checkResult("<p>{{foreach \$a as \$b}}<caret>{{/foreach}}</p>")
    }

    fun testSlashCompletesBlock() {
        myFixture.configureByText("a.tpl", "{{if \$a}}x{{<caret>")
        myFixture.type('/')
        myFixture.checkResult("{{if \$a}}x{{/if}}<caret>")
    }

    fun testEnterBetweenTags() {
        myFixture.configureByText("a.tpl", "{{if \$a}}<caret>{{/if}}")
        myFixture.type('\n')
        myFixture.checkResult("{{if \$a}}\n    <caret>\n{{/if}}")
    }

    fun testTagCompletion() {
        myFixture.configureByText("a.tpl", "{{stri<caret>")
        myFixture.completeBasic()
        myFixture.checkResult("{{strip}}<caret>{{/strip}}")
    }

    fun testBlockComment() {
        myFixture.configureByText("a.tpl", "<selection>{{\$a}}</selection>")
        myFixture.performEditorAction("CommentByBlockComment")
        myFixture.checkResult("{{*{{\$a}}*}}")
    }

    fun testLineComment() {
        myFixture.configureByText("a.tpl", "<p>{{\$a}}<caret></p>")
        myFixture.performEditorAction("CommentByLineComment")
        assertEquals("{{*<p>{{\$a}}</p>*}}", myFixture.editor.document.text)
    }

    fun testFormatting() {
        myFixture.configureByText("a.tpl", "{{if \$a==1}}\n<p>x</p>\n{{/if}}\n")
        WriteCommandAction.runWriteCommandAction(project, Runnable {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        })
        assertEquals("{{if \$a == 1}}\n    <p>x</p>\n{{/if}}\n", myFixture.editor.document.text)
    }

    fun testNoParseErrors() {
        myFixture.configureByText("a.tpl", "{{assign var=\"x\" value=[1, 2]}}{{\$x|@count}}")
        val smarty = myFixture.file.viewProvider.getPsi(SmartyLanguage)
        val errors = PsiTreeUtil.findChildrenOfType(smarty, PsiErrorElement::class.java).toList()
        assertEquals(emptyList<PsiErrorElement>(), errors)
    }

    private companion object {
        val BRACES = SmartyDelimiters("{{", "}}")
    }
}
