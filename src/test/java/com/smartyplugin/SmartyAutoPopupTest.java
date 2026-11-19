package com.smartyplugin;

import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Completion pops up by itself after <code>$</code> and <code>|</code> inside Smarty tags, and only there. */
public class SmartyAutoPopupTest extends BasePlatformTestCase {
    private CompletionAutoPopupTester tester;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tester = new CompletionAutoPopupTester(myFixture);
    }

    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    @Override
    protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
        tester.runWithAutoPopupEnabled(testRunnable);
    }

    public void testDollarInTagShowsVariables() {
        configure("{assign var=\"fooBar\" value=1}\n{<caret>");
        tester.typeWithPauses("$");
        LookupImpl lookup = tester.getLookup();
        assertNotNull("popup expected after $", lookup);
        List<String> items = lookupStrings();
        assertTrue(String.valueOf(items), items.contains("$fooBar"));
    }

    public void testPipeInTagShowsModifiers() {
        configure("{$name<caret>}");
        tester.typeWithPauses("|");
        assertNotNull("popup expected after |", tester.getLookup());
        List<String> items = lookupStrings();
        assertTrue(String.valueOf(items), items.contains("escape"));
    }

    public void testDollarInHtmlTextDoesNothing() {
        configure("<p>Price: <caret></p>");
        tester.typeWithPauses("$");
        assertNull("no popup in plain HTML", tester.getLookup());
    }

    private void configure(String text) {
        EdtTestUtil.runInEdtAndWait(() -> myFixture.configureByText("a.tpl", text));
    }

    private List<String> lookupStrings() {
        return EdtTestUtil.runInEdtAndGet(() -> myFixture.getLookupElementStrings());
    }
}
