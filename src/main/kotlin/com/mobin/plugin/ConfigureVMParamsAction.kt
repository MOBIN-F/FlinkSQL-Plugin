package com.mobin.plugin

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class ConfigureVMParamsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val propertiesComponent = PropertiesComponent.getInstance(project)
        
        val currentParams = propertiesComponent.getValue("flinkSQL.vmParams", "")
        val newParams = Messages.showInputDialog(
            project,
            "Enter VM parameters:",
            "Configure VM Parameters",
            null,
            currentParams,
            null
        )
        
        if (newParams != null) {
            propertiesComponent.setValue("flinkSQL.vmParams", newParams.trim())
            Messages.showInfoMessage(project, "VM parameters have been updated to: $newParams", "Configuration Updated")
        }
    }

    override fun update(e: AnActionEvent) {
        // 确保只有在选中SQL文件时才启用此操作
        val virtualFile = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.extension?.equals("sql", ignoreCase = true) == true
    }
}