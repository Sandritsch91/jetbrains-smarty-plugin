package com.smartyplugin.actions;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.smartyplugin.lang.SmartyIcons;
import org.jetbrains.annotations.NotNull;

public final class CreateSmartyFileAction extends CreateFileFromTemplateAction implements DumbAware {
    static final String TEMPLATE_NAME = "Smarty File";

    @Override
    protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory,
                               @NotNull CreateFileFromTemplateDialog.Builder builder) {
        builder.setTitle("New Smarty Template")
                .addKind("Smarty template", SmartyIcons.FILE, TEMPLATE_NAME);
    }

    @Override
    protected @NotNull String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return "Create Smarty Template " + newName;
    }
}
