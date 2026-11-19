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
