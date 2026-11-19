package com.smartyplugin;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;

import java.util.List;

/** Variables passed in from PHP: Yii2 render calls and Smarty assign/display. */
public class SmartyPhpTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("models/User.php", """
                <?php
                namespace app\\models;

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
                """);
        myFixture.addFileToProject("models/Profile.php", """
                <?php
                namespace app\\models;

                class Profile {
                    public string $city = '';
                }
                """);
        myFixture.addFileToProject("controllers/PostCommentController.php", """
                <?php
                namespace app\\controllers;

                use app\\models\\User;

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
                """);
        myFixture.addFileToProject("legacy/page.php", """
                <?php
                function show($smarty) {
                    $smarty->assign('headline', 'Welcome');
                    $smarty->assign(['count' => 3]);
                    $smarty->display('page.tpl');
                }
                """);
    }

    private void configureTemplate(String path, String text) {
        PsiFile file = myFixture.addFileToProject(path, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    }

    public void testVariablesFromYiiRender() {
        configureTemplate("views/post-comment/index.tpl", "{$<caret>}");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("$user", "$users", "$title")));
    }

    public void testVariablesFromCompact() {
        configureTemplate("views/post-comment/view.tpl", "{$us<caret>}");
        myFixture.completeBasic();
        myFixture.checkResult("{$user<caret>}");
    }

    public void testVariablesFromSmartyAssign() {
        configureTemplate("templates/page.tpl", "{$<caret>}");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("$headline", "$count")));
    }

    public void testOtherTemplatesGetNothing() {
        configureTemplate("views/site/index.tpl", "{$<caret>}");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertTrue(String.valueOf(items), items == null || !items.contains("$user"));
    }

    public void testMemberCompletion() {
        configureTemplate("views/post-comment/index.tpl", "{$user-><caret>}");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("name", "fullName", "getFullName", "profile", "can", "initials")));
        assertFalse(items.toString(), items.contains("secret"));
        assertFalse(items.toString(), items.contains("count"));
        assertFalse(items.toString(), items.contains("ROLE"));
    }

    public void testChainedMemberCompletion() {
        configureTemplate("views/post-comment/index.tpl", "{$user->profile->ci<caret>}");
        myFixture.completeBasic();
        myFixture.checkResult("{$user->profile->city<caret>}");
    }

    public void testMethodCallChain() {
        configureTemplate("views/post-comment/index.tpl", "{$user->getProfile()->ci<caret>}");
        myFixture.completeBasic();
        myFixture.checkResult("{$user->getProfile()->city<caret>}");
    }

    public void testForeachItemType() {
        configureTemplate("views/post-comment/index.tpl", "{foreach $users as $u}{$u-><caret>}{/foreach}");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("name", "fullName")));
    }

    public void testGotoVariable() {
        configureTemplate("views/post-comment/index.tpl", "{$us<caret>er->name}");
        PsiElement target = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(),
                myFixture.getCaretOffset());
        assertInstanceOf(target, StringLiteralExpression.class);
        assertEquals("user", ((StringLiteralExpression) target).getContents());
    }

    /** Layout of a real Yii module: namespaced classes, inherited @property docs, an unclosed tag while typing. */
    private void addNotificationsModule() {
        myFixture.addFileToProject("src/models/Notification.php", """
                <?php
                namespace acme\\notifications\\models;

                /**
                 * @property integer $id
                 * @property string $key
                 * @property-read \\acme\\notifications\\models\\Address $user
                 */
                abstract class Notification {
                    public function rules(): array { return []; }
                    public function getUser(): \\yii\\db\\ActiveQuery { return null; }
                }
                """);
        myFixture.addFileToProject("src/models/Address.php", """
                <?php
                namespace acme\\notifications\\models;
                class Address { public string $email = ''; }
                """);
        myFixture.addFileToProject("src/models/ManualNotification.php", """
                <?php
                namespace acme\\notifications\\models;

                class ManualNotification extends Notification {
                    public function getTitle(): string { return ''; }
                }
                """);
        myFixture.addFileToProject("src/controllers/AdminController.php", """
                <?php
                namespace acme\\notifications\\controllers;

                use acme\\notifications\\models\\ManualNotification;

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
                """);
    }

    public void testYiiModuleWhileTyping() {
        addNotificationsModule();
        configureTemplate("src/views/admin/create.tpl", """
                {set title='Add notification'|t:'hq-re/notifications'}

                {$model-><caret>

                <div class="notification">
                    {$this->render('_form', [
                        'model' => $model,
                        'groups' => $groups
                    ])}
                </div>
                """);
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("id", "key", "user", "title", "rules")));
    }

    public void testRelationPropertyUsesDocType() {
        addNotificationsModule();
        configureTemplate("src/views/admin/create.tpl", "{$model->user->em<caret>}");
        myFixture.completeBasic();
        myFixture.checkResult("{$model->user->email<caret>}");
    }

    /** _form.tpl is rendered from create.tpl: {$this->render('_form', ['model' => $model])} */
    public void testVariablesFromSmartyRender() {
        addNotificationsModule();
        myFixture.addFileToProject("src/views/admin/create.tpl", """
                <div>
                    {$this->render('_form', [
                        'model' => $model,
                        'groups' => $groups
                    ])}
                </div>
                """);
        configureTemplate("src/views/admin/_form.tpl", "{$model-><caret>}");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("id", "title")));
    }

    private void addHtmlHelper() {
        myFixture.addFileToProject("vendor/yii/bootstrap5/Html.php", """
                <?php
                namespace yii\\bootstrap5;

                class BaseHtml {
                    public const ICON = 'i';
                    public static function a(string $text, $url = null): string { return ''; }
                    public static function encode(string $s): string { return $s; }
                    public function instanceOnly(): void {}
                    protected static function hidden(): void {}
                }
                class Html extends BaseHtml {
                    public static function tag(string $name): \\yii\\bootstrap5\\Tag { return new Tag(); }
                }
                class Tag { public string $name = ''; }
                """);
    }

    public void testStaticCompletionFromUseTag() {
        addHtmlHelper();
        configureTemplate("views/site/page.tpl", """
                {use class="yii\\bootstrap5\\Html"}

                {Html::<caret>}
                """);
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("a", "encode", "tag", "ICON")));
        assertFalse(items.toString(), items.contains("instanceOnly"));
        assertFalse(items.toString(), items.contains("hidden"));
    }

    public void testStaticCallInsideExpression() {
        addHtmlHelper();
        configureTemplate("views/site/page.tpl", """
                {use class="yii\\bootstrap5\\Html"}
                {$link = Html::enc<caret>}
                """);
        myFixture.completeBasic();
        myFixture.checkResult("""
                {use class="yii\\bootstrap5\\Html"}
                {$link = Html::encode(<caret>)}
                """);
    }

    public void testStaticCallChain() {
        addHtmlHelper();
        configureTemplate("views/site/page.tpl", """
                {use class="yii\\bootstrap5\\Html"}
                {Html::tag('b')->na<caret>}
                """);
        myFixture.completeBasic();
        myFixture.checkResult("""
                {use class="yii\\bootstrap5\\Html"}
                {Html::tag('b')->name<caret>}
                """);
    }

    public void testGotoStaticAndUseClass() {
        addHtmlHelper();
        configureTemplate("views/site/page.tpl", """
                {use class="yii\\bootstrap5\\Html"}
                {Html::a('x')}{Html::ICON}
                """);
        String text = myFixture.getEditor().getDocument().getText();
        PsiElement cls = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), text.indexOf("Html::a") + 1);
        assertInstanceOf(cls, com.jetbrains.php.lang.psi.elements.PhpClass.class);
        PsiElement method = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), text.indexOf("::a") + 2);
        assertInstanceOf(method, Method.class);
        assertEquals("a", ((Method) method).getName());
        PsiElement constant = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), text.indexOf("ICON") + 1);
        assertInstanceOf(constant, Field.class);
        PsiElement used = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), text.indexOf("bootstrap5") + 1);
        assertInstanceOf(used, com.jetbrains.php.lang.psi.elements.PhpClass.class);
        assertEquals("Html", ((com.jetbrains.php.lang.psi.elements.PhpClass) used).getName());
    }

    public void testGotoWidgetTags() {
        myFixture.addFileToProject("vendor/kartik/select2/Select2.php", """
                <?php
                namespace kartik\\select2;
                class Select2 {
                    const THEME_BOOTSTRAP = 'bootstrap';
                    public string $name = '';
                    public function setTheme(string $theme): void {}
                }
                """);
        myFixture.addFileToProject("vendor/yii/widgets/ActiveForm.php", """
                <?php
                namespace yii\\widgets;
                class ActiveForm { public ?string $id = null; }
                """);
        myFixture.addFileToProject("lib/Form.php", "<?php\nclass Form {}\n");
        configureTemplate("views/site/page.tpl", """
                {use class="kartik\\select2\\Select2" type="function"}
                {use class="yii\\widgets\\ActiveForm" type="block"}
                {ActiveForm id="f"}
                    {Select2 name='icon'
                        theme=Select2::THEME_BOOTSTRAP}
                {/ActiveForm}
                {form}x{/form}
                """);
        String text = myFixture.getEditor().getDocument().getText();
        assertTarget(text.indexOf("{Select2") + 2, com.jetbrains.php.lang.psi.elements.PhpClass.class, "Select2");
        assertTarget(text.indexOf("{ActiveForm") + 2, com.jetbrains.php.lang.psi.elements.PhpClass.class, "ActiveForm");
        assertTarget(text.indexOf("{/ActiveForm") + 3, com.jetbrains.php.lang.psi.elements.PhpClass.class, "ActiveForm");
        assertTarget(text.indexOf("name='icon'") + 1, Field.class, "name");
        assertTarget(text.indexOf("theme=") + 1, Method.class, "setTheme");
        assertTarget(text.indexOf("id=\"f\"") + 1, Field.class, "id");
        assertNull("{form} is not the global class Form", GotoDeclarationAction.findTargetElement(getProject(),
                myFixture.getEditor(), text.indexOf("{form}") + 2));
    }

    private void addSelect2() {
        myFixture.addFileToProject("vendor/kartik/base/InputWidget.php", """
                <?php
                namespace kartik\\base;
                class InputWidget {
                    public string $name = '';
                    public array $options = [];
                    public static $counter = 0;
                    protected string $hidden = '';
                }
                """);
        myFixture.addFileToProject("vendor/kartik/select2/Select2.php", """
                <?php
                namespace kartik\\select2;
                class Select2 extends \\kartik\\base\\InputWidget {
                    const THEME_BOOTSTRAP = 'bootstrap';
                    public string $language = '';
                    public array $pluginOptions = [];
                    public function setTheme(string $theme): void {}
                }
                """);
    }

    public void testWidgetAttributeCompletion() {
        addSelect2();
        configureTemplate("views/site/page.tpl", """
                {use class="kartik\\select2\\Select2" type="function"}
                {Select2 name='icon' <caret>
                    pluginOptions=[
                        'allowClear' => true
                    ]}
                """);
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("language", "options", "theme")));
        assertFalse("already present: " + items, items.contains("name") || items.contains("pluginOptions"));
        assertFalse(items.toString(), items.contains("counter") || items.contains("hidden"));
    }

    public void testWidgetAttributeInsertsAssign() {
        addSelect2();
        configureTemplate("views/site/page.tpl", """
                {use class="kartik\\select2\\Select2" type="function"}
                {Select2 name='icon' lang<caret>}
                """);
        myFixture.completeBasic();
        myFixture.checkResult("""
                {use class="kartik\\select2\\Select2" type="function"}
                {Select2 name='icon' language=<caret>}
                """);
    }

    public void testNoWidgetAttributesInsideValues() {
        addSelect2();
        configureTemplate("views/site/page.tpl", """
                {use class="kartik\\select2\\Select2" type="function"}
                {Select2 pluginOptions=['allowClear' => true, lang<caret>]}
                """);
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertTrue(String.valueOf(items), items == null || !items.contains("language"));
    }

    private void assertTarget(int offset, Class<? extends com.jetbrains.php.lang.psi.elements.PhpNamedElement> type,
                              String name) {
        PsiElement target = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset);
        assertInstanceOf(target, type);
        assertEquals(name, ((com.jetbrains.php.lang.psi.elements.PhpNamedElement) target).getName());
    }

    public void testGotoMembers() {
        configureTemplate("views/post-comment/index.tpl", "{$user->na<caret>me} {$user->fullName}");
        PsiElement field = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(),
                myFixture.getCaretOffset());
        assertInstanceOf(field, Field.class);

        int getter = myFixture.getEditor().getDocument().getText().indexOf("fullName") + 2;
        PsiElement method = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), getter);
        assertInstanceOf(method, Method.class);
        assertEquals("getFullName", ((Method) method).getName());
    }
}
