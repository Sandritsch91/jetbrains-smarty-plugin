package com.smartyplugin.lang

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JPanel

/** Settings | Languages & Frameworks | Smarty */
class SmartyConfigurable(private val project: Project) : Configurable {
    private var left: JBTextField? = null
    private var right: JBTextField? = null

    override fun getDisplayName(): @Nls String = "Smarty"

    override fun createComponent(): JComponent {
        val l = JBTextField(8)
        val r = JBTextField(8)
        left = l
        right = r
        val hint = JBLabel("Smarty's left_delimiter and right_delimiter. Comments use them too, e.g. {{* ... *}}.")
        hint.componentStyle = UIUtil.ComponentStyle.SMALL
        hint.fontColor = UIUtil.FontColor.BRIGHTER
        reset()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Left delimiter:", l)
            .addLabeledComponent("Right delimiter:", r)
            .addComponent(hint)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean = current() != SmartySettings.getInstance(project).delimiters()

    @Throws(ConfigurationException::class)
    override fun apply() {
        val l = left?.text.orEmpty()
        val r = right?.text.orEmpty()
        if (l.isEmpty() || r.isEmpty()) throw ConfigurationException("Both delimiters are required.")
        if (l.trim(Character::isWhitespace) != l || r.trim(Character::isWhitespace) != r) {
            throw ConfigurationException("Delimiters must not start or end with whitespace.")
        }
        SmartySettings.getInstance(project).setDelimiters(SmartyDelimiters(l, r))
    }

    override fun reset() {
        val d = SmartySettings.getInstance(project).delimiters()
        left?.text = d.left
        right?.text = d.right
    }

    private fun current(): SmartyDelimiters {
        val l = left?.text.orEmpty()
        val r = right?.text.orEmpty()
        return if (l.isEmpty() || r.isEmpty()) SmartyDelimiters(" ", " ") else SmartyDelimiters(l, r)
    }
}
