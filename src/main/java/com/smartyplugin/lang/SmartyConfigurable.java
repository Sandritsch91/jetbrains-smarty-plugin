package com.smartyplugin.lang;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/** Settings | Languages &amp; Frameworks | Smarty */
public final class SmartyConfigurable implements Configurable {
    private final Project project;
    private JBTextField left;
    private JBTextField right;

    public SmartyConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "Smarty";
    }

    @Override
    public @Nullable JComponent createComponent() {
        left = new JBTextField(8);
        right = new JBTextField(8);
        JBLabel hint = new JBLabel("Smarty's left_delimiter and right_delimiter. Comments use them too, e.g. {{* ... *}}.");
        hint.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        hint.setFontColor(UIUtil.FontColor.BRIGHTER);
        reset();
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Left delimiter:", left)
                .addLabeledComponent("Right delimiter:", right)
                .addComponent(hint)
                .addComponentFillVertically(new javax.swing.JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        return !current().equals(SmartySettings.getInstance(project).delimiters());
    }

    @Override
    public void apply() throws ConfigurationException {
        String l = left.getText();
        String r = right.getText();
        if (l.isEmpty() || r.isEmpty()) throw new ConfigurationException("Both delimiters are required.");
        if (!l.strip().equals(l) || !r.strip().equals(r)) {
            throw new ConfigurationException("Delimiters must not start or end with whitespace.");
        }
        SmartySettings.getInstance(project).setDelimiters(new SmartyDelimiters(l, r));
    }

    @Override
    public void reset() {
        SmartyDelimiters d = SmartySettings.getInstance(project).delimiters();
        if (left != null) left.setText(d.left());
        if (right != null) right.setText(d.right());
    }

    private SmartyDelimiters current() {
        String l = left == null ? "" : left.getText();
        String r = right == null ? "" : right.getText();
        return l.isEmpty() || r.isEmpty() ? new SmartyDelimiters(" ", " ") : new SmartyDelimiters(l, r);
    }
}
