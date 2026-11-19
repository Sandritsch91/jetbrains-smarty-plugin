package com.smartyplugin;

import com.intellij.psi.tree.IElementType;
import com.smartyplugin.lexer.SmartyLexer;
import junit.framework.TestCase;

public class SmartyLexerTest extends TestCase {

    private static String lex(String text) {
        SmartyLexer lexer = new SmartyLexer();
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

    public void testPrintWithModifiers() {
        assertEquals("TEMPLATE_TEXT(<b>) LDELIM({) VARIABLE($user) DOT(.) IDENTIFIER(name) PIPE(|) MODIFIER_NAME(escape) "
                        + "COLON(:) STRING('html') PIPE(|) MODIFIER_NAME(default) COLON(:) STRING(\"x\") RDELIM(}) TEMPLATE_TEXT(</b>)",
                lex("<b>{$user.name|escape:'html'|default:\"x\"}</b>"));
    }

    public void testTagWithAttributes() {
        assertEquals("LDELIM({) TAG_NAME(include) ATTR_NAME(file) ASSIGN(=) STRING(\"a.tpl\") ATTR_NAME(title) ASSIGN(=) VARIABLE($t) RDELIM(})",
                lex("{include file=\"a.tpl\" title=$t}"));
    }

    public void testClosingTagAndKeywords() {
        assertEquals("LDELIM({) TAG_NAME(if) VARIABLE($a) KEYWORD(eq) NUMBER(1) OPERATOR(&&) OPERATOR(!) VARIABLE($b) RDELIM(}) "
                        + "TEMPLATE_TEXT(x) LDELIM({) SLASH(/) TAG_NAME(if) RDELIM(})",
                lex("{if $a eq 1 && !$b}x{/if}"));
    }

    public void testAutoLiteralAndComment() {
        assertEquals("TEMPLATE_TEXT(<script>function f() { return 1; }</script>) COMMENT({* note *})",
                lex("<script>function f() { return 1; }</script>{* note *}"));
    }

    public void testLiteralBlock() {
        assertEquals("LDELIM({) TAG_NAME(literal) RDELIM(}) TEMPLATE_TEXT(a{$b}c) LDELIM({) SLASH(/) TAG_NAME(literal) RDELIM(}) "
                        + "LDELIM({) VARIABLE($x) RDELIM(})",
                lex("{literal}a{$b}c{/literal}{$x}"));
    }

    public void testForeachArrow() {
        assertEquals("LDELIM({) TAG_NAME(foreach) VARIABLE($list) KEYWORD(as) VARIABLE($k) DOUBLE_ARROW(=>) VARIABLE($v) RDELIM(})",
                lex("{foreach $list as $k => $v}"));
    }

    public void testConfigVarAndAt() {
        assertEquals("LDELIM({) CONFIG_VAR(#title#) RDELIM(}) LDELIM({) VARIABLE($i) AT(@) IDENTIFIER(first) RDELIM(}) "
                        + "LDELIM({) VARIABLE($a) PIPE(|) AT(@) MODIFIER_NAME(count) RDELIM(})",
                lex("{#title#}{$i@first}{$a|@count}"));
    }

    public void testStringContainingBraces() {
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(js) ASSIGN(=) STRING('function () { return {q: true}; }') "
                        + "ATTR_NAME(b) ASSIGN(=) NUMBER(1) RDELIM(})",
                lex("{x js='function () { return {q: true}; }' b=1}"));
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(js) ASSIGN(=) STRING(\"a } b\") RDELIM(})",
                lex("{x js=\"a } b\"}"));
    }

    public void testMultiLineString() {
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING('one\ntwo') RDELIM(})",
                lex("{x a='one\ntwo'}"));
    }

    public void testDoubleQuotedStringWithEmbeddedTag() {
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\"Hi {$name|default:\"you\"}!\") RDELIM(})",
                lex("{x a=\"Hi {$name|default:\"you\"}!\"}"));
    }

    public void testUnterminatedStringStopsAtTagEnd() {
        assertEquals("LDELIM({) VARIABLE($a) PIPE(|) MODIFIER_NAME(default) COLON(:) STRING('x) RDELIM(}) TEMPLATE_TEXT(<p>done</p>)",
                lex("{$a|default:'x}<p>done</p>"));
    }

    public void testRestartFromAnyState() {
        String text = "<p>{if $a}x{literal}{y}{/literal}{$b|upper}{/if}</p>";
        SmartyLexer full = new SmartyLexer();
        full.start(text, 0, text.length(), 0);
        while (full.getTokenType() != null) {
            int start = full.getTokenStart();
            int state = full.getState();
            SmartyLexer partial = new SmartyLexer();
            partial.start(text, start, text.length(), state);
            SmartyLexer reference = new SmartyLexer();
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
}
