package com.smartyplugin

import com.smartyplugin.lexer.SmartyLexer
import junit.framework.TestCase

class SmartyLexerTest : TestCase() {

    private fun lex(text: String): String {
        val lexer = SmartyLexer()
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

    fun testPrintWithModifiers() {
        assertEquals(
            "TEMPLATE_TEXT(<b>) LDELIM({) VARIABLE(\$user) DOT(.) IDENTIFIER(name) PIPE(|) MODIFIER_NAME(escape) " +
                "COLON(:) STRING('html') PIPE(|) MODIFIER_NAME(default) COLON(:) STRING(\"x\") RDELIM(}) TEMPLATE_TEXT(</b>)",
            lex("<b>{\$user.name|escape:'html'|default:\"x\"}</b>"),
        )
    }

    fun testTagWithAttributes() {
        assertEquals(
            "LDELIM({) TAG_NAME(include) ATTR_NAME(file) ASSIGN(=) STRING(\"a.tpl\") ATTR_NAME(title) ASSIGN(=) VARIABLE(\$t) RDELIM(})",
            lex("{include file=\"a.tpl\" title=\$t}"),
        )
    }

    fun testClosingTagAndKeywords() {
        assertEquals(
            "LDELIM({) TAG_NAME(if) VARIABLE(\$a) KEYWORD(eq) NUMBER(1) OPERATOR(&&) OPERATOR(!) VARIABLE(\$b) RDELIM(}) " +
                "TEMPLATE_TEXT(x) LDELIM({) SLASH(/) TAG_NAME(if) RDELIM(})",
            lex("{if \$a eq 1 && !\$b}x{/if}"),
        )
    }

    fun testAutoLiteralAndComment() {
        assertEquals(
            "TEMPLATE_TEXT(<script>function f() { return 1; }</script>) COMMENT({* note *})",
            lex("<script>function f() { return 1; }</script>{* note *}"),
        )
    }

    fun testLiteralBlock() {
        assertEquals(
            "LDELIM({) TAG_NAME(literal) RDELIM(}) TEMPLATE_TEXT(a{\$b}c) LDELIM({) SLASH(/) TAG_NAME(literal) RDELIM(}) " +
                "LDELIM({) VARIABLE(\$x) RDELIM(})",
            lex("{literal}a{\$b}c{/literal}{\$x}"),
        )
    }

    fun testForeachArrow() {
        assertEquals(
            "LDELIM({) TAG_NAME(foreach) VARIABLE(\$list) KEYWORD(as) VARIABLE(\$k) DOUBLE_ARROW(=>) VARIABLE(\$v) RDELIM(})",
            lex("{foreach \$list as \$k => \$v}"),
        )
    }

    fun testConfigVarAndAt() {
        assertEquals(
            "LDELIM({) CONFIG_VAR(#title#) RDELIM(}) LDELIM({) VARIABLE(\$i) AT(@) IDENTIFIER(first) RDELIM(}) " +
                "LDELIM({) VARIABLE(\$a) PIPE(|) AT(@) MODIFIER_NAME(count) RDELIM(})",
            lex("{#title#}{\$i@first}{\$a|@count}"),
        )
    }

    fun testStringContainingBraces() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(js) ASSIGN(=) STRING('function () { return {q: true}; }') " +
                "ATTR_NAME(b) ASSIGN(=) NUMBER(1) RDELIM(})",
            lex("{x js='function () { return {q: true}; }' b=1}"),
        )
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(js) ASSIGN(=) STRING(\"a } b\") RDELIM(})",
            lex("{x js=\"a } b\"}"),
        )
    }

    fun testMultiLineString() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING('one\ntwo') RDELIM(})",
            lex("{x a='one\ntwo'}"),
        )
    }

    fun testDoubleQuotedStringWithEmbeddedTag() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\"Hi {\$name|default:\"you\"}!\") RDELIM(})",
            lex("{x a=\"Hi {\$name|default:\"you\"}!\"}"),
        )
    }

    fun testUnterminatedStringStopsAtTagEnd() {
        assertEquals(
            "LDELIM({) VARIABLE(\$a) PIPE(|) MODIFIER_NAME(default) COLON(:) STRING('x) RDELIM(}) TEMPLATE_TEXT(<p>done</p>)",
            lex("{\$a|default:'x}<p>done</p>"),
        )
    }

    fun testRestartFromAnyState() {
        val text = "<p>{if \$a}x{literal}{y}{/literal}{\$b|upper}{/if}</p>"
        val full = SmartyLexer()
        full.start(text, 0, text.length, 0)
        while (full.tokenType != null) {
            val start = full.tokenStart
            val state = full.state
            val partial = SmartyLexer()
            partial.start(text, start, text.length, state)
            val reference = SmartyLexer()
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
}
