package com.smartyplugin

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.smartyplugin.highlighting.SmartySyntaxHighlighter
import com.smartyplugin.lexer.SmartyTokenTypes

/** Variables, backtick expressions and tags inside double-quoted strings are highlighted. */
class SmartyStringHighlightingTest : BasePlatformTestCase() {

    private fun highlight(text: String): String {
        val lexer = SmartySyntaxHighlighter().highlightingLexer
        lexer.start(text)
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

    fun testSimpleVariable() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\"Hello ) VARIABLE(\$name) STRING(, \$1 \\\$x \") " +
                "RDELIM(})",
            highlight("{x a=\"Hello \$name, \$1 \\\$x \"}"),
        )
    }

    fun testVariableEndsAtDot() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\") VARIABLE(\$user) STRING(.name\") RDELIM(})",
            highlight("{x a=\"\$user.name\"}"),
        )
    }

    fun testBacktickExpression() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\"Hi ) BACKTICK(`) VARIABLE(\$user) DOT(.) " +
                "PROPERTY(name) BACKTICK(`) STRING(!\") RDELIM(})",
            highlight("{x a=\"Hi `\$user.name`!\"}"),
        )
    }

    fun testEmbeddedTag() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\"Hi ) LDELIM({) VARIABLE(\$name) PIPE(|) " +
                "MODIFIER_NAME(default) COLON(:) STRING(\"you\") RDELIM(}) STRING( and ) LDELIM({) " +
                "TAG_NAME(counter) RDELIM(}) STRING(\") RDELIM(})",
            highlight("{x a=\"Hi {\$name|default:\"you\"} and {counter}\"}"),
        )
    }

    fun testBraceFollowedBySpaceIsText() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(js) ASSIGN(=) STRING(\"function () { return 1; }\") RDELIM(})",
            highlight("{x js=\"function () { return 1; }\"}"),
        )
    }

    fun testMembersAndCalls() {
        assertEquals(
            "LDELIM({) VARIABLE(\$page) DOT(.) PROPERTY(title) RDELIM(}) " +
                "LDELIM({) TAG_NAME(if) VARIABLE(\$user) ARROW(->) METHOD_CALL(isGuest) LPAREN(() RPAREN()) " +
                "OPERATOR(&&) VARIABLE(\$user) ARROW(->) PROPERTY(name) RDELIM(}) " +
                "LDELIM({) CLASS_REFERENCE(Html) DOUBLE_COLON(::) STATIC_METHOD_CALL(encode) LPAREN(() " +
                "IDENTIFIER(x) RPAREN()) RDELIM(}) " +
                "LDELIM({) VARIABLE(\$t) ASSIGN(=) CLASS_REFERENCE(Select2) DOUBLE_COLON(::) " +
                "CLASS_CONSTANT(THEME) OPERATOR(+) FUNCTION_CALL(count) LPAREN(() VARIABLE(\$a) RPAREN()) " +
                "COMMA(,) CLASS_REFERENCE(Foo) DOUBLE_COLON(::) KEYWORD(class) RDELIM(})",
            highlight(
                "{\$page.title}{if \$user->isGuest() && \$user->name}{Html::encode(x)}" +
                    "{\$t = Select2::THEME + count(\$a), Foo::class}",
            ),
        )
    }

    fun testMembersInsideStrings() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING(\"Hi ) LDELIM({) VARIABLE(\$u) ARROW(->) " +
                "METHOD_CALL(name) LPAREN(() RPAREN()) RDELIM(}) STRING(\") RDELIM(})",
            highlight("{x a=\"Hi {\$u->name()}\"}"),
        )
    }

    fun testSingleQuotedStringIsNotSplit() {
        assertEquals(
            "LDELIM({) TAG_NAME(x) ATTR_NAME(a) ASSIGN(=) STRING('Hello \$name {\$x}') RDELIM(})",
            highlight("{x a='Hello \$name {\$x}'}"),
        )
    }

    /** The editor relexes incrementally; the layer must survive edits inside and around the string. */
    fun testEditorHighlighterAfterEdits() {
        myFixture.configureByText("a.tpl", "<p>{include file=\"Hello \$name<caret>!\" x=1}</p>")
        assertTokenAt("\$name", SmartyTokenTypes.VARIABLE)
        myFixture.type("Two")
        assertTokenAt("\$nameTwo", SmartyTokenTypes.VARIABLE)
        myFixture.type(" `\$a.b`")
        assertTokenAt("\$a", SmartyTokenTypes.VARIABLE)
        assertTokenAt("`", SmartyTokenTypes.BACKTICK)
        assertTokenAt("x=", SmartyTokenTypes.ATTR_NAME)
    }

    fun testQuotesStillPair() {
        myFixture.configureByText("a.tpl", "{include file=<caret>}")
        myFixture.type('"')
        myFixture.checkResult("{include file=\"<caret>\"}")
        myFixture.type("a \$b")
        myFixture.type('"')
        myFixture.checkResult("{include file=\"a \$b\"<caret>}")
    }

    private fun assertTokenAt(textAtOffset: String, expected: IElementType) {
        val text = myFixture.editor.document.text
        val offset = text.indexOf(textAtOffset)
        assertTrue(textAtOffset, offset >= 0)
        val it = (myFixture.editor as EditorEx).highlighter.createIterator(offset)
        assertEquals("$textAtOffset in $text", expected, it.tokenType)
    }
}
