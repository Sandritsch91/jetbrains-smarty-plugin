package com.smartyplugin

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Yii routes ({url route="..."}, Url::to) and translations (|t:'category', Yii::t). */
class SmartyYiiTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "src/controllers/AdminController.php", """
            <?php
            namespace acme\notifications\controllers;
            class AdminController {
                public function actions(): array { return ['list' => 'acme\IconSearchAction']; }
                public function actionIndex() {}
                public function actionCreate() {}
                public function actionSendTestMail() {}
                protected function actionHidden() {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/controllers/api/UserProfileController.php", """
            <?php
            namespace acme\notifications\controllers\api;
            class UserProfileController { public function actionView() {} }
            """.trimIndent()
        )
        for ((language, group) in listOf("de" to "Gruppe", "en" to "Group")) {
            myFixture.addFileToProject(
                "src/messages/$language/messages.po", """
                # views/admin/create
                msgctxt "hq-re/notifications"
                msgid "Add notification"
                msgstr "${if (language == "de") "Notification erstellen" else "Add notification"}"

                msgctxt "hq-re/notifications"
                msgid "Group"
                msgstr "$group"

                msgctxt "hq-re/other"
                msgid "Only elsewhere"
                msgstr "x"
                """.trimIndent()
            )
        }
        myFixture.addFileToProject("app/messages/de/app.php", "<?php\nreturn ['Hello' => 'Hallo', 'Bye' => 'Tschüss'];\n")
    }

    private fun configure(text: String) {
        val file = myFixture.addFileToProject("src/views/admin/create.tpl", text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
    }

    private fun offsetOf(text: String, inside: Int = 1) = myFixture.editor.document.text.indexOf(text) + inside

    // ------------------------------------------------------------ routes

    fun testRouteCompletion() {
        configure("{url route=\"<caret>\"}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings!!
        assertTrue(items.toString(), items.containsAll(listOf(
            "create", "list", "send-test-mail", "admin/index", "admin/list", "api/user-profile/view",
        )))
        assertFalse(items.toString(), items.contains("hidden") || items.contains("admin/hidden"))
    }

    fun testRouteNavigation() {
        configure(
            """
            {url route="list"} {path route="admin/create"} {Url::to(['/notifications/admin/send-test-mail', 'id' => 1])}
            {Url::toRoute('api/user-profile/view')}
            """.trimIndent()
        )
        val list = GotoDeclarationAction.findTargetElement(project, myFixture.editor, offsetOf("\"list\"", 2))
        assertInstanceOf(list, StringLiteralExpression::class.java)
        assertEquals("list", (list as StringLiteralExpression).contents)

        val create = GotoDeclarationAction.findTargetElement(project, myFixture.editor, offsetOf("admin/create", 2))
        assertEquals("actionCreate", (create as Method).name)

        val mail = GotoDeclarationAction.findTargetElement(project, myFixture.editor, offsetOf("send-test-mail", 2))
        assertEquals("actionSendTestMail", (mail as Method).name)

        val view = GotoDeclarationAction.findTargetElement(project, myFixture.editor, offsetOf("api/user-profile", 2))
        assertEquals("actionView", (view as Method).name)
    }

    // ------------------------------------------------------------ translations

    fun testMessageCompletion() {
        configure("{'Gr<caret>'|t:'hq-re/notifications'}")
        myFixture.completeBasic()
        myFixture.checkResult("{'Group<caret>'|t:'hq-re/notifications'}")
    }

    fun testMessagesOnlyOfTheCategory() {
        configure("{'<caret>'|t:'hq-re/notifications'}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings!!
        assertTrue(items.toString(), items.containsAll(listOf("Add notification", "Group")))
        assertFalse(items.toString(), items.contains("Only elsewhere"))
    }

    fun testCategoryCompletion() {
        configure("{'x'|t:'<caret>'}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings!!
        assertTrue(items.toString(), items.containsAll(listOf("hq-re/notifications", "hq-re/other", "app")))
    }

    fun testYiiTAndPhpMessageFiles() {
        configure("{Yii::t('app', 'He<caret>')}")
        myFixture.completeBasic()
        myFixture.checkResult("{Yii::t('app', 'Hello<caret>')}")
    }

    fun testMessageNavigationAndHover() {
        configure("{'Group'|t:'hq-re/notifications'}")
        val offset = offsetOf("'Group'", 2)
        val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, offset)
        assertEquals(2, targets.size) // de and en

        myFixture.editor.caretModel.moveToOffset(offset)
        val element = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)!!
        val doc = DocumentationManager.getProviderFromElement(element).generateDoc(element, null) ?: ""
        assertTrue(doc, doc.contains("Gruppe") && doc.contains("hq-re/notifications"))
    }
}
