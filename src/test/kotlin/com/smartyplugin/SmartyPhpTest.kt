package com.smartyplugin

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Field
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Variables passed in from PHP: Yii2 render calls and Smarty assign/display. */
class SmartyPhpTest : BasePlatformTestCase() {

    /** A file written as an indented raw string: common indent removed, ending with a line break. */
    private fun block(text: String): String = text.trimIndent() + "\n"

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "models/User.php",
            block(
                $$"""
                <?php
                namespace app\models;

                /** @property-read string $initials */
                class User {
                    public string $name = '';
                    public static int $count = 0;
                    private string $secret = '';
                    const ROLE = 'user';
                    public function getFullName(): string { return $this->name; }
                    public function getProfile(): Profile { return new Profile(); }
                    public function can(string $permission): bool { return true; }
                }
                """,
            ),
        )
        myFixture.addFileToProject(
            "models/Profile.php",
            block(
                $$"""
                <?php
                namespace app\models;

                class Profile {
                    public string $city = '';
                }
                """,
            ),
        )
        myFixture.addFileToProject(
            "controllers/PostCommentController.php",
            block(
                $$"""
                <?php
                namespace app\controllers;

                use app\models\User;

                class PostCommentController {
                    public function actionIndex() {
                        $user = new User();
                        /** @var User[] $users */
                        $users = [];
                        return $this->render('index', ['user' => $user, 'users' => $users, 'title' => 'Hi']);
                    }
                    public function actionView() {
                        $user = new User();
                        return $this->render('view.tpl', compact('user'));
                    }
                }
                """,
            ),
        )
        myFixture.addFileToProject(
            "legacy/page.php",
            block(
                $$"""
                <?php
                function show($smarty) {
                    $smarty->assign('headline', 'Welcome');
                    $smarty->assign(['count' => 3]);
                    $smarty->display('page.tpl');
                }
                """,
            ),
        )
    }

    private fun configureTemplate(path: String, text: String) {
        val file = myFixture.addFileToProject(path, text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
    }

    private fun lookupStrings(): List<String> {
        val items = myFixture.lookupElementStrings
        assertNotNull(items)
        return items!!
    }

    fun testVariablesFromYiiRender() {
        configureTemplate("views/post-comment/index.tpl", "{$<caret>}")
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("\$user", "\$users", "\$title")))
    }

    fun testVariablesFromCompact() {
        configureTemplate("views/post-comment/view.tpl", "{\$us<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{\$user<caret>}")
    }

    fun testVariablesFromSmartyAssign() {
        configureTemplate("templates/page.tpl", "{$<caret>}")
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("\$headline", "\$count")))
    }

    fun testOtherTemplatesGetNothing() {
        configureTemplate("views/site/index.tpl", "{$<caret>}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings
        assertTrue(items.toString(), items == null || !items.contains("\$user"))
    }

    fun testMemberCompletion() {
        configureTemplate("views/post-comment/index.tpl", "{\$user-><caret>}")
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("name", "fullName", "getFullName", "profile", "can", "initials")))
        assertFalse(items.toString(), items.contains("secret"))
        assertFalse(items.toString(), items.contains("count"))
        assertFalse(items.toString(), items.contains("ROLE"))
    }

    fun testChainedMemberCompletion() {
        configureTemplate("views/post-comment/index.tpl", "{\$user->profile->ci<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{\$user->profile->city<caret>}")
    }

    fun testMethodCallChain() {
        configureTemplate("views/post-comment/index.tpl", "{\$user->getProfile()->ci<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{\$user->getProfile()->city<caret>}")
    }

    fun testForeachItemType() {
        configureTemplate("views/post-comment/index.tpl", "{foreach \$users as \$u}{\$u-><caret>}{/foreach}")
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("name", "fullName")))
    }

    fun testGotoVariable() {
        configureTemplate("views/post-comment/index.tpl", "{\$us<caret>er->name}")
        val target = GotoDeclarationAction.findTargetElement(project, myFixture.editor, myFixture.caretOffset)
        assertInstanceOf(target, StringLiteralExpression::class.java)
        assertEquals("user", (target as StringLiteralExpression).contents)
    }

    fun testVariablesFromInclude() {
        myFixture.addFileToProject(
            "views/post-comment/index.tpl",
            "{foreach \$users as \$u}{include file=\"_row.tpl\" row=\$u label=\$u->name|escape nocache}{/foreach}",
        )
        configureTemplate("views/post-comment/_row.tpl", "{\$row->profile->ci<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{\$row->profile->city<caret>}")

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("{$}")
        }
        myFixture.editor.caretModel.moveToOffset(2)
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("\$row", "\$label")))
        assertFalse(items.toString(), items.contains("\$nocache") || items.contains("\$file"))
    }

    /** Layout of a real Yii module: namespaced classes, inherited @property docs, an unclosed tag while typing. */
    private fun addNotificationsModule() {
        myFixture.addFileToProject(
            "src/models/Notification.php",
            block(
                $$"""
                <?php
                namespace acme\notifications\models;

                /**
                 * @property integer $id
                 * @property string $key
                 * @property-read \acme\notifications\models\Address $user
                 */
                abstract class Notification {
                    public function rules(): array { return []; }
                    public function getUser(): \yii\db\ActiveQuery { return null; }
                }
                """,
            ),
        )
        myFixture.addFileToProject(
            "src/models/Address.php",
            block(
                $$"""
                <?php
                namespace acme\notifications\models;
                class Address { public string $email = ''; }
                """,
            ),
        )
        myFixture.addFileToProject(
            "src/models/ManualNotification.php",
            block(
                """
                <?php
                namespace acme\notifications\models;

                class ManualNotification extends Notification {
                    public function getTitle(): string { return ''; }
                }
                """,
            ),
        )
        myFixture.addFileToProject(
            "src/controllers/AdminController.php",
            block(
                $$"""
                <?php
                namespace acme\notifications\controllers;

                use acme\notifications\models\ManualNotification;

                class AdminController extends Controller {
                    public function actionCreate(): Response|string {
                        $model = new ManualNotification();
                        $groups = [];
                        return $this->render('create', [
                            'model' => $model,
                            'groups' => $groups,
                        ]);
                    }
                }
                """,
            ),
        )
    }

    fun testYiiModuleWhileTyping() {
        addNotificationsModule()
        configureTemplate(
            "src/views/admin/create.tpl",
            block(
                $$"""
                {set title='Add notification'|t:'hq-re/notifications'}

                {$model-><caret>

                <div class="notification">
                    {$this->render('_form', [
                        'model' => $model,
                        'groups' => $groups
                    ])}
                </div>
                """,
            ),
        )
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("id", "key", "user", "title", "rules")))
    }

    fun testRelationPropertyUsesDocType() {
        addNotificationsModule()
        configureTemplate("src/views/admin/create.tpl", "{\$model->user->em<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{\$model->user->email<caret>}")
    }

    /** _form.tpl is rendered from create.tpl: {$this->render('_form', ['model' => $model])} */
    fun testVariablesFromSmartyRender() {
        addNotificationsModule()
        myFixture.addFileToProject(
            "src/views/admin/create.tpl",
            block(
                $$"""
                <div>
                    {$this->render('_form', [
                        'model' => $model,
                        'groups' => $groups
                    ])}
                </div>
                """,
            ),
        )
        configureTemplate("src/views/admin/_form.tpl", "{\$model-><caret>}")
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("id", "title")))
    }

    fun testGotoViewFromTemplateRender() {
        myFixture.addFileToProject("src/views/admin/_form.tpl", "form")
        myFixture.addFileToProject("src/views/other/_form.tpl", "other form")
        configureTemplate("src/views/admin/create.tpl", "{\$this->render('_fo<caret>rm', ['model' => \$model])}")
        val target = GotoDeclarationAction.findTargetElement(project, myFixture.editor, myFixture.caretOffset)
        val path = (target as com.intellij.psi.PsiFile).virtualFile.path
        assertTrue(path, path.endsWith("src/views/admin/_form.tpl"))
    }

    fun testGotoViewFromController() {
        myFixture.addFileToProject("views/post-comment/index.tpl", "index")
        myFixture.addFileToProject("views/site/index.tpl", "other index")
        myFixture.configureFromTempProjectFile("controllers/PostCommentController.php")
        val offset = myFixture.editor.document.text.indexOf("'index'") + 2
        val target = GotoDeclarationAction.findTargetElement(project, myFixture.editor, offset)
        val path = (target as com.intellij.psi.PsiFile).virtualFile.path
        assertTrue(path, path.endsWith("/views/post-comment/index.tpl"))
    }

    fun testGotoTemplateFromSmartyDisplay() {
        myFixture.addFileToProject("templates/page.tpl", "page")
        myFixture.configureFromTempProjectFile("legacy/page.php")
        val offset = myFixture.editor.document.text.indexOf("'page.tpl'") + 2
        val target = GotoDeclarationAction.findTargetElement(project, myFixture.editor, offset)
        assertEquals("page.tpl", (target as com.intellij.psi.PsiFile).name)
    }

    fun testRenameRenamesPhpKey() {
        configureTemplate("views/post-comment/index.tpl", "{\$user->name} {\$users}")
        com.smartyplugin.editor.SmartyVariableRenameHandler.rename(myFixture.file, 3, "member")
        myFixture.checkResult("{\$member->name} {\$users}")
        val controller = myFixture.findFileInTempDir("controllers/PostCommentController.php")
        val php = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(controller)!!.text
        assertTrue(php, php.contains("['member' => \$user, 'users' => \$users"))
        assertTrue("other actions keep their variable", php.contains("compact('user')"))
    }

    fun testRegisteredPlugins() {
        myFixture.addFileToProject(
            "app/SmartySetup.php",
            block(
                $$"""
                <?php
                namespace app;

                /** Shouting text. */
                function smarty_hello($params) { return 'hello'; }

                class Helper {
                    /** Upper-case it. */
                    public static function shout(string $s): string { return strtoupper($s); }
                }

                function setup(\Smarty $smarty) {
                    $smarty->registerPlugin('function', 'hello', 'app\smarty_hello');
                    $smarty->registerPlugin(\Smarty::PLUGIN_MODIFIER, 'shout', [Helper::class, 'shout']);
                    $smarty->registerPlugin('block', 'panel', [$this, 'panel']);
                }
                """,
            ),
        )
        configureTemplate("templates/page.tpl", "{hel<caret>}")
        myFixture.completeBasic()
        myFixture.lookupElements?.firstOrNull { it.lookupString == "hello" }?.let {
            myFixture.lookup.currentItem = it
            myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR)
        }
        myFixture.checkResult("{hello <caret>}")

        configureTemplate("templates/page2.tpl", "{\$x|sh<caret>}")
        myFixture.completeBasic()
        myFixture.checkResult("{\$x|shout<caret>}")

        configureTemplate("templates/page3.tpl", "{hello}{\$x|shout}")
        val text = myFixture.editor.document.text
        val hello = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("hello") + 1)
        assertEquals("smarty_hello", (hello as PhpNamedElement).name)
        val shout = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("shout") + 1)
        assertEquals("shout", (shout as Method).name)

        // hover: PHP's documentation of the callback
        myFixture.editor.caretModel.moveToOffset(text.indexOf("shout") + 1)
        val element = com.intellij.codeInsight.documentation.DocumentationManager.getInstance(project)
            .findTargetElement(myFixture.editor, myFixture.file)
        assertEquals("shout", (element as Method).name)
    }

    private fun addHtmlHelper() {
        myFixture.addFileToProject(
            "vendor/yii/bootstrap5/Html.php",
            block(
                $$"""
                <?php
                namespace yii\bootstrap5;

                class BaseHtml {
                    public const ICON = 'i';
                    public static function a(string $text, $url = null): string { return ''; }
                    public static function encode(string $s): string { return $s; }
                    public function instanceOnly(): void {}
                    protected static function hidden(): void {}
                }
                class Html extends BaseHtml {
                    public static function tag(string $name): \yii\bootstrap5\Tag { return new Tag(); }
                }
                class Tag { public string $name = ''; }
                """,
            ),
        )
    }

    fun testStaticCompletionFromUseTag() {
        addHtmlHelper()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="yii\bootstrap5\Html"}

                {Html::<caret>}
                """,
            ),
        )
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("a", "encode", "tag", "ICON")))
        assertFalse(items.toString(), items.contains("instanceOnly"))
        assertFalse(items.toString(), items.contains("hidden"))
    }

    fun testUseAlias() {
        addHtmlHelper()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="yii\bootstrap5\Html" as="BsHtml"}
                {BsHtml::enc<caret>}
                """,
            ),
        )
        myFixture.completeBasic()
        myFixture.checkResult(
            block(
                """
                {use class="yii\bootstrap5\Html" as="BsHtml"}
                {BsHtml::encode(<caret>)}
                """,
            ),
        )
    }

    fun testStaticCallInsideExpression() {
        addHtmlHelper()
        configureTemplate(
            "views/site/page.tpl",
            block(
                $$"""
                {use class="yii\bootstrap5\Html"}
                {$link = Html::enc<caret>}
                """,
            ),
        )
        myFixture.completeBasic()
        myFixture.checkResult(
            block(
                $$"""
                {use class="yii\bootstrap5\Html"}
                {$link = Html::encode(<caret>)}
                """,
            ),
        )
    }

    fun testStaticCallChain() {
        addHtmlHelper()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="yii\bootstrap5\Html"}
                {Html::tag('b')->na<caret>}
                """,
            ),
        )
        myFixture.completeBasic()
        myFixture.checkResult(
            block(
                """
                {use class="yii\bootstrap5\Html"}
                {Html::tag('b')->name<caret>}
                """,
            ),
        )
    }

    fun testGotoStaticAndUseClass() {
        addHtmlHelper()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="yii\bootstrap5\Html"}
                {Html::a('x')}{Html::ICON}
                """,
            ),
        )
        val text = myFixture.editor.document.text
        val cls = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("Html::a") + 1)
        assertInstanceOf(cls, PhpClass::class.java)
        val method = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("::a") + 2)
        assertInstanceOf(method, Method::class.java)
        assertEquals("a", (method as Method).name)
        val constant = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("ICON") + 1)
        assertInstanceOf(constant, Field::class.java)
        val used = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("bootstrap5") + 1)
        assertInstanceOf(used, PhpClass::class.java)
        assertEquals("Html", (used as PhpClass).name)
    }

    fun testGotoWidgetTags() {
        myFixture.addFileToProject(
            "vendor/kartik/select2/Select2.php",
            block(
                $$"""
                <?php
                namespace kartik\select2;
                class Select2 {
                    const THEME_BOOTSTRAP = 'bootstrap';
                    public string $name = '';
                    public function setTheme(string $theme): void {}
                }
                """,
            ),
        )
        myFixture.addFileToProject(
            "vendor/yii/widgets/ActiveForm.php",
            block(
                $$"""
                <?php
                namespace yii\widgets;
                class ActiveForm { public ?string $id = null; }
                """,
            ),
        )
        myFixture.addFileToProject("lib/Form.php", "<?php\nclass Form {}\n")
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="kartik\select2\Select2" type="function"}
                {use class="yii\widgets\ActiveForm" type="block"}
                {ActiveForm id="f"}
                    {Select2 name='icon'
                        theme=Select2::THEME_BOOTSTRAP}
                {/ActiveForm}
                {form}x{/form}
                """,
            ),
        )
        val text = myFixture.editor.document.text
        assertTarget(text.indexOf("{Select2") + 2, PhpClass::class.java, "Select2")
        assertTarget(text.indexOf("{ActiveForm") + 2, PhpClass::class.java, "ActiveForm")
        assertTarget(text.indexOf("{/ActiveForm") + 3, PhpClass::class.java, "ActiveForm")
        assertTarget(text.indexOf("name='icon'") + 1, Field::class.java, "name")
        assertTarget(text.indexOf("theme=") + 1, Method::class.java, "setTheme")
        assertTarget(text.indexOf("id=\"f\"") + 1, Field::class.java, "id")
        assertNull(
            "{form} is not the global class Form",
            GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("{form}") + 2),
        )
    }

    private fun addSelect2() {
        myFixture.addFileToProject(
            "vendor/kartik/base/InputWidget.php",
            block(
                $$"""
                <?php
                namespace kartik\base;
                class InputWidget {
                    public string $name = '';
                    public array $options = [];
                    public static $counter = 0;
                    protected string $hidden = '';
                }
                """,
            ),
        )
        myFixture.addFileToProject(
            "vendor/kartik/select2/Select2.php",
            block(
                $$"""
                <?php
                namespace kartik\select2;
                class Select2 extends \kartik\base\InputWidget {
                    const THEME_BOOTSTRAP = 'bootstrap';
                    public string $language = '';
                    public array $pluginOptions = [];
                    public function setTheme(string $theme): void {}
                }
                """,
            ),
        )
    }

    fun testWidgetAttributeCompletion() {
        addSelect2()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="kartik\select2\Select2" type="function"}
                {Select2 name='icon' <caret>
                    pluginOptions=[
                        'allowClear' => true
                    ]}
                """,
            ),
        )
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("language", "options", "theme")))
        assertFalse("already present: $items", items.contains("name") || items.contains("pluginOptions"))
        assertFalse(items.toString(), items.contains("counter") || items.contains("hidden"))
    }

    fun testWidgetAttributeInsertsAssign() {
        addSelect2()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="kartik\select2\Select2" type="function"}
                {Select2 name='icon' lang<caret>}
                """,
            ),
        )
        myFixture.completeBasic()
        myFixture.checkResult(
            block(
                """
                {use class="kartik\select2\Select2" type="function"}
                {Select2 name='icon' language=<caret>}
                """,
            ),
        )
    }

    fun testNoWidgetAttributesInsideValues() {
        addSelect2()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="kartik\select2\Select2" type="function"}
                {Select2 pluginOptions=['allowClear' => true, lang<caret>]}
                """,
            ),
        )
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings
        assertTrue(items.toString(), items == null || !items.contains("language"))
    }

    private fun addActiveForm() {
        myFixture.addFileToProject(
            "vendor/yii/bootstrap5/ActiveForm.php",
            block(
                $$"""
                <?php
                namespace yii\bootstrap5;
                class ActiveForm {
                    public function errorSummary($models): string { return ''; }
                    public function field($model, string $attribute): ActiveField { return new ActiveField(); }
                }
                class ActiveField {
                    public function textInput(): self { return $this; }
                }
                """,
            ),
        )
    }

    fun testBlockWidgetAssignType() {
        addActiveForm()
        configureTemplate(
            "views/site/page.tpl",
            block(
                $$"""
                {use class="yii\bootstrap5\ActiveForm" type="block"}
                {ActiveForm assign="form" id="notificationsForm"}
                    {$form-><caret>}
                {/ActiveForm}
                """,
            ),
        )
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("errorSummary", "field")))
    }

    fun testBlockWidgetAssignChain() {
        addActiveForm()
        configureTemplate(
            "views/site/page.tpl",
            block(
                $$"""
                {use class="yii\bootstrap5\ActiveForm" type="block"}
                {ActiveForm assign="form"}
                    {$form->field($model, 'title')->text<caret>}
                {/ActiveForm}
                """,
            ),
        )
        myFixture.completeBasic()
        myFixture.checkResult(
            block(
                $$"""
                {use class="yii\bootstrap5\ActiveForm" type="block"}
                {ActiveForm assign="form"}
                    {$form->field($model, 'title')->textInput()<caret>}
                {/ActiveForm}
                """,
            ),
        )
    }

    fun testGotoTemplateDefinitions() {
        addActiveForm()
        configureTemplate(
            "views/site/page.tpl",
            block(
                $$"""
                {use class="yii\bootstrap5\ActiveForm" type="block"}
                {ActiveForm assign="form"}
                    {$form->errorSummary($model)}
                {/ActiveForm}
                {assign var="total" value=3}{$total}
                {foreach $items as $row}{$row}{/foreach}
                """,
            ),
        )
        val text = myFixture.editor.document.text
        val form = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("\$form->") + 2)
        assertNotNull(form)
        assertEquals("\"form\"", form!!.text)
        val total = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("{\$total}") + 3)
        assertNotNull(total)
        assertEquals("\"total\"", total!!.text)
        val row = GotoDeclarationAction.findTargetElement(project, myFixture.editor, text.indexOf("{\$row}") + 3)
        assertNotNull(row)
        assertEquals(text.indexOf("\$row}{\$row"), row!!.textRange.startOffset)
    }

    private fun addYiiCore() {
        myFixture.addFileToProject(
            "vendor/yiisoft/yii2/web/Application.php",
            block(
                $$"""
                <?php
                namespace yii\web;
                /** @property-read User $user */
                class Application { public string $name = ''; }
                """,
            ),
        )
        myFixture.addFileToProject(
            "vendor/yiisoft/yii2/web/User.php",
            block(
                $$"""
                <?php
                namespace yii\web;
                class User { public $identity; public function getIsGuest(): bool { return true; } }
                """,
            ),
        )
        myFixture.addFileToProject(
            "vendor/yiisoft/yii2/web/View.php",
            block(
                $$"""
                <?php
                namespace yii\web;
                class View {
                    public string $title = '';
                    public function render(string $view, array $params = []): string { return ''; }
                }
                """,
            ),
        )
    }

    fun testYiiImplicitVariables() {
        addYiiCore()
        configureTemplate("views/site/page.tpl", "{$<caret>}")
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("\$app", "\$this")))
    }

    fun testYiiAppMembers() {
        addYiiCore()
        configureTemplate("views/site/page.tpl", "{\$app->user-><caret>}")
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("identity", "isGuest")))
    }

    fun testYiiViewMembersAndGoto() {
        addYiiCore()
        configureTemplate("views/site/page.tpl", "{\$this-><caret>}")
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("title", "render")))

        val view = GotoDeclarationAction.findTargetElement(project, myFixture.editor, 3)
        assertInstanceOf(view, PhpClass::class.java)
        assertEquals("View", (view as PhpClass).name)
    }

    fun testNoImplicitVariablesWithoutYii() {
        configureTemplate("views/site/page.tpl", "{$<caret>}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings
        assertTrue(items.toString(), items == null || !items.contains("\$app"))
    }

    fun testWidgetConfigKeyWhileTyping() {
        addSelect2()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="kartik\select2\Select2"}
                {Select2::widget([
                    'name' => 'users',
                    'pluginOpt<caret>
                ])}
                """,
            ),
        )
        myFixture.completeBasic()
        myFixture.checkResult(
            block(
                """
                {use class="kartik\select2\Select2"}
                {Select2::widget([
                    'name' => 'users',
                    'pluginOptions' => <caret>
                ])}
                """,
            ),
        )
    }

    fun testWidgetConfigKeys() {
        addSelect2()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="kartik\select2\Select2"}
                {Select2::widget(['name' => 'users', '<caret>'])}
                """,
            ),
        )
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("language", "options", "pluginOptions", "theme")))
        assertFalse("already present: $items", items.contains("name"))
    }

    fun testActiveFieldWidgetConfigKeys() {
        addSelect2()
        configureTemplate(
            "views/site/page.tpl",
            block(
                $$"""
                {use class="kartik\select2\Select2"}
                {$form->field($model, 'icon')->widget(Select2::class, [
                    'data' => $items,
                    '<caret>'
                ])}
                """,
            ),
        )
        myFixture.completeBasic()
        val items = lookupStrings()
        assertTrue(items.toString(), items.containsAll(listOf("language", "pluginOptions")))
    }

    fun testNoWidgetKeysInNestedArraysOrValues() {
        addSelect2()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="kartik\select2\Select2"}
                {Select2::widget(['options' => ['<caret>']])}
                """,
            ),
        )
        myFixture.completeBasic()
        val nested = myFixture.lookupElementStrings
        assertTrue(nested.toString(), nested == null || !nested.contains("pluginOptions"))

        configureTemplate(
            "views/site/value.tpl",
            block(
                """
                {use class="kartik\select2\Select2"}
                {Select2::widget(['name' => '<caret>'])}
                """,
            ),
        )
        myFixture.completeBasic()
        val value = myFixture.lookupElementStrings
        assertTrue(value.toString(), value == null || !value.contains("pluginOptions"))
    }

    fun testGotoWidgetConfigKey() {
        addSelect2()
        configureTemplate(
            "views/site/page.tpl",
            block(
                """
                {use class="kartik\select2\Select2"}
                {Select2::widget(['pluginOptions' => [], 'theme' => 'x'])}
                """,
            ),
        )
        val text = myFixture.editor.document.text
        assertTarget(text.indexOf("'pluginOptions'") + 2, Field::class.java, "pluginOptions")
        assertTarget(text.indexOf("'theme'") + 2, Method::class.java, "setTheme")
    }

    private fun assertTarget(offset: Int, type: Class<out PhpNamedElement>, name: String) {
        val target = GotoDeclarationAction.findTargetElement(project, myFixture.editor, offset)
        assertInstanceOf(target, type)
        assertEquals(name, (target as PhpNamedElement).name)
    }

    fun testGotoMembers() {
        configureTemplate("views/post-comment/index.tpl", "{\$user->na<caret>me} {\$user->fullName}")
        val field: PsiElement? = GotoDeclarationAction.findTargetElement(project, myFixture.editor, myFixture.caretOffset)
        assertInstanceOf(field, Field::class.java)

        val getter = myFixture.editor.document.text.indexOf("fullName") + 2
        val method = GotoDeclarationAction.findTargetElement(project, myFixture.editor, getter)
        assertInstanceOf(method, Method::class.java)
        assertEquals("getFullName", (method as Method).name)
    }
}
