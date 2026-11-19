package com.smartyplugin;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.smartyplugin.highlighting.SmartySyntaxHighlighter;
import com.smartyplugin.lexer.SmartyTokenTypes;

/** Variables, backtick expressions and tags inside double-quoted strings are highlighted. */
public class SmartyStringHighlightingTest extends BasePlatformTestCase {

    private static String highlight(String text) {
        Lexer lexer = new SmartySyntaxHighlighter().getHighlightingLexer();
        lexer.start(text);
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

    public void testSimpleVariable() {
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\"Hello ) VARIABLE($name) STRING(, $1 \\$x \") "
                        + "RDELIM(})",
                highlight("{x a=\"Hello $name, $1 \\$x \"}"));
    }

    public void testVariableEndsAtDot() {
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\") VARIABLE($user) STRING(.name\") RDELIM(})",
                highlight("{x a=\"$user.name\"}"));
    }

    public void testBacktickExpression() {
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\"Hi ) BACKTICK(`) VARIABLE($user) DOT(.) "
                        + "IDENTIFIER(name) BACKTICK(`) STRING(!\") RDELIM(})",
                highlight("{x a=\"Hi `$user.name`!\"}"));
    }

    public void testEmbeddedTag() {
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\"Hi ) LDELIM({) VARIABLE($name) PIPE(|) "
                        + "MODIFIER_NAME(default) COLON(:) STRING(\"you\") RDELIM(}) STRING( and ) LDELIM({) "
                        + "TAG_NAME(counter) RDELIM(}) STRING(\") RDELIM(})",
                highlight("{x a=\"Hi {$name|default:\"you\"} and {counter}\"}"));
    }

    public void testBraceFollowedBySpaceIsText() {
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(js) ASSIGN(=) STRING(\"function () { return 1; }\") RDELIM(})",
                highlight("{x js=\"function () { return 1; }\"}"));
    }

    public void testSingleQuotedStringIsNotSplit() {
        assertEquals("LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING('Hello $name {$x}') RDELIM(})",
                highlight("{x a='Hello $name {$x}'}"));
    }

    /** The editor relexes incrementally; the layer must survive edits inside and around the string. */
    public void testEditorHighlighterAfterEdits() {
        myFixture.configureByText("a.tpl", "<p>{include file=\"Hello $name<caret>!\" x=1}</p>");
        assertTokenAt("$name", SmartyTokenTypes.VARIABLE);
        myFixture.type("Two");
        assertTokenAt("$nameTwo", SmartyTokenTypes.VARIABLE);
        myFixture.type(" `$a.b`");
        assertTokenAt("$a", SmartyTokenTypes.VARIABLE);
        assertTokenAt("`", SmartyTokenTypes.BACKTICK);
        assertTokenAt("x=", SmartyTokenTypes.ATTR_NAME);
    }

    public void testQuotesStillPair() {
        myFixture.configureByText("a.tpl", "{include file=<caret>}");
        myFixture.type('"');
        myFixture.checkResult("{include file=\"<caret>\"}");
        myFixture.type("a $b");
        myFixture.type('"');
        myFixture.checkResult("{include file=\"a $b\"<caret>}");
    }

    private void assertTokenAt(String textAtOffset, IElementType expected) {
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf(textAtOffset);
        assertTrue(textAtOffset, offset >= 0);
        HighlighterIterator it = ((EditorEx) myFixture.getEditor()).getHighlighter().createIterator(offset);
        assertEquals(textAtOffset + " in " + text, expected, it.getTokenType());
    }
}
