package com.smartyplugin

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

/** Hover / Quick Documentation: PHP's documentation for PHP targets, built-in docs for Smarty tags and modifiers. */
class SmartyDocumentationTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "models/User.php", """
            <?php
            namespace app\models;

            /** A registered user. */
            class User {
                /** @var string Display name */
                public string ${'$'}name = '';
                /** Whether the user is not logged in. */
                public function isGuest(): bool { return true; }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "vendor/yii/helpers/Html.php", """
            <?php
            namespace yii\helpers;
            /** HTML helper. */
            class Html { /** Encodes special characters. */ public static function encode(${'$'}s): string { return ''; } }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "controllers/SiteController.php", """
            <?php
            namespace app\controllers;
            use app\models\User;
            class SiteController {
                public function actionIndex() {
                    ${'$'}user = new User();
                    return ${'$'}this->render('index', ['user' => ${'$'}user]);
                }
            }
            """.trimIndent()
        )
    }

    private fun configure(text: String) {
        val file = myFixture.addFileToProject("views/site/index.tpl", text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
    }

    private fun targetAt(text: String, inside: Int = 1): PsiElement? {
        val offset = myFixture.editor.document.text.indexOf(text) + inside
        myFixture.editor.caretModel.moveToOffset(offset)
        return DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)
    }

    private fun docOf(element: PsiElement): String =
        DocumentationManager.getProviderFromElement(element).generateDoc(element, null) ?: ""

    fun testPhpTargets() {
        configure(
            """
            {use class="yii\helpers\Html"}
            {${'$'}user->name} {if ${'$'}user->isGuest()}x{/if} {Html::encode(${'$'}user->name)}
            """.trimIndent()
        )
        val field = targetAt("name}", 1)
        assertInstanceOf(field, Field::class.java)
        assertTrue(docOf(field!!), docOf(field).contains("Display name"))

        val method = targetAt("isGuest", 2)
        assertInstanceOf(method, Method::class.java)
        assertTrue(docOf(method!!), docOf(method).contains("not logged in"))

        val html = targetAt("Html::encode", 1)
        assertInstanceOf(html, PhpClass::class.java)
        assertTrue(docOf(html!!), docOf(html).contains("HTML helper"))

        val variable = targetAt("${'$'}user->name}", 2)
        assertInstanceOf(variable, PhpClass::class.java)
        assertEquals("User", (variable as PhpClass).name)
    }

    fun testSmartyTagsAndModifiers() {
        configure("{foreach ${'$'}items as ${'$'}i}{${'$'}i|escape}{/foreach}")
        val foreach = targetAt("foreach", 2)
        assertNotNull(foreach)
        val tagDoc = docOf(foreach!!)
        assertTrue(tagDoc, tagDoc.contains("{/foreach}") && tagDoc.contains("block tag"))

        val escape = targetAt("escape", 2)
        assertNotNull(escape)
        assertTrue(docOf(escape!!), docOf(escape).contains("|escape"))
    }
}
