package com.mobin.plugin

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class SqlExecutorSettingsConfigurable(private val project: Project) : Configurable {
    private val jarDirectoryField = TextFieldWithBrowseButton()
    private val settings: SqlExecutorSettings = SqlExecutorSettings.getInstance(project)

    override fun createComponent(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("JAR Directory:", jarDirectoryField)
            .addComponentFillVertically(JBPanel<JBPanel<*>>(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        return jarDirectoryField.text != settings.jarDirectory
    }

    override fun apply() {
        settings.jarDirectory = jarDirectoryField.text
    }

    override fun reset() {
        jarDirectoryField.text = settings.jarDirectory
    }

    override fun getDisplayName(): String = "SQL Executor Settings"
}