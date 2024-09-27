package com.mobin.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class ConfigureFlinkLibAction : AnAction("Configure Flink Lib Directory") {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return

        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle("Select Flink Lib Directory")
            .withDescription("Choose the directory containing Flink JAR files")

        val fileChooser: FileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
        val chosenFiles: Array<VirtualFile> = fileChooser.choose(project)

        if (chosenFiles.isNotEmpty()) {
            val selectedDir = chosenFiles[0]
            val settings = SqlExecutorSettings.getInstance(project)
            settings.jarDirectory = selectedDir.path
            Messages.showInfoMessage(project, "Flink Lib directory set to: ${selectedDir.path}", "Flink Lib Directory Configured")
        }
    }
}