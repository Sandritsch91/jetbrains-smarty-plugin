package com.smartyplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable

/** Completion pops up by itself after `$` and `|` inside Smarty tags, and only there. */
class SmartyAutoPopupTest : BasePlatformTestCase() {
    private lateinit var tester: CompletionAutoPopupTester

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    override fun runInDispatchThread(): Boolean = false

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        tester.runWithAutoPopupEnabled(testRunnable)
    }

    fun testDollarInTagShowsVariables() {
        configure("{assign var=\"fooBar\" value=1}\n{<caret>")
        tester.typeWithPauses("$")
        val lookup = tester.lookup
        assertNotNull("popup expected after $", lookup)
        val items = lookupStrings()
        assertTrue(items.toString(), items!!.contains("\$fooBar"))
    }

    fun testPipeInTagShowsModifiers() {
        configure("{\$name<caret>}")
        tester.typeWithPauses("|")
        assertNotNull("popup expected after |", tester.lookup)
        val items = lookupStrings()
        assertTrue(items.toString(), items!!.contains("escape"))
    }

    fun testDollarInHtmlTextDoesNothing() {
        configure("<p>Price: <caret></p>")
        tester.typeWithPauses("$")
        assertNull("no popup in plain HTML", tester.lookup)
    }

    private fun configure(text: String) {
        runInEdtAndWait { myFixture.configureByText("a.tpl", text) }
    }

    private fun lookupStrings(): List<String>? = runInEdtAndGet { myFixture.lookupElementStrings }
}
