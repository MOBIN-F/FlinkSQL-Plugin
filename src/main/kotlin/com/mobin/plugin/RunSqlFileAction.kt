package com.mobin.plugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel

class RunSqlFileAction : AnAction("Run FlinkSQL", "Run the selected SQL file", AllIcons.Actions.Execute) {
    companion object {
        private var currentProcessHandler: OSProcessHandler? = null
        var lastSqlFilePath: String? = null
        private var isRunning = false

        fun restart(project: Project) {
            lastSqlFilePath?.let { executeSqlFile(project, it) }
        }

        fun stop() {
            currentProcessHandler?.destroyProcess()
        }

        fun isRunning(): Boolean = isRunning

        private fun executeSqlFile(project: Project, sqlFilePath: String) {
            val settings = SqlExecutorSettings.getInstance(project)
            val jarDirectory = settings.jarDirectory
            if (jarDirectory.isEmpty()) {
                Messages.showErrorDialog(project, "Flink Home directory is not set. Please configure it using the [flink-home] option.", "Error")
                return
            }

            val jarFiles = File(jarDirectory).listFiles { file -> file.extension.equals("jar", ignoreCase = true) }
            if (jarFiles.isNullOrEmpty()) {
                Messages.showErrorDialog(project, "No JAR files found in the specified Flink Home directory.", "Error")
                return
            }

            val classpath = jarFiles.joinToString(File.pathSeparator) { it.absolutePath }
            val mainClass = "com.mobin.FlinkClient"

            val propertiesComponent = PropertiesComponent.getInstance(project)
            val vmParams = propertiesComponent.getValue("flinkSQL.vmParams", "")

            val commandLine = GeneralCommandLine()
            commandLine.exePath = "java"
            
            // Add VM parameters if they are set
            if (vmParams.isNotEmpty()) {
                vmParams.split(" ").forEach { param ->
                    commandLine.addParameter(param)
                }
            }

            commandLine.addParameter("-cp")
            commandLine.addParameter(classpath)
            commandLine.addParameter(mainClass)

            val file = File(sqlFilePath)
            when {
                file.name.endsWith("_ddl.sql") -> {
                    val dmlFile = File(file.parentFile, file.nameWithoutExtension.removeSuffix("_ddl") + "_dml.sql")
                    if (dmlFile.exists()) {
                        commandLine.addParameter("--ddl")
                        commandLine.addParameter(sqlFilePath)
                        commandLine.addParameter("--dml")
                        commandLine.addParameter(dmlFile.absolutePath)
                    } else {
                        Messages.showErrorDialog(project, "Corresponding DML file not found: ${dmlFile.name}", "Error")
                        return
                    }
                }
                file.name.endsWith("_dml.sql") -> {
                    val ddlFile = File(file.parentFile, file.nameWithoutExtension.removeSuffix("_dml") + "_ddl.sql")
                    if (ddlFile.exists()) {
                        commandLine.addParameter("--ddl")
                        commandLine.addParameter(ddlFile.absolutePath)
                        commandLine.addParameter("--dml")
                        commandLine.addParameter(sqlFilePath)
                    } else {
                        Messages.showErrorDialog(project, "Corresponding DDL file not found: ${ddlFile.name}", "Error")
                        return
                    }
                }
                else -> {
                    commandLine.addParameter("--sql")
                    commandLine.addParameter(sqlFilePath)
                }
            }

            val processHandler = OSProcessHandler(commandLine)

            val consoleView = getConsoleView(project)
            consoleView.clear()
            consoleView.print("Executing SQL file: $sqlFilePath\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            consoleView.print("Command: ${commandLine.commandLineString}\n", ConsoleViewContentType.SYSTEM_OUTPUT)

            processHandler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    if (event.exitCode == 0) {
                        consoleView.print("SQL file executed successfully.\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                    } else {
                        consoleView.print("Failed to execute SQL file. Exit code: ${event.exitCode}\n", ConsoleViewContentType.ERROR_OUTPUT)
                    }
                    currentProcessHandler = null
                    isRunning = false
                    updateActionStates(project)
                }
            })

            consoleView.attachToProcess(processHandler)
            currentProcessHandler = processHandler
            processHandler.startNotify()
            isRunning = true
            updateActionStates(project)
        }

        private fun updateActionStates(project: Project) {
            val event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, DataContext { dataId ->
                when {
                    CommonDataKeys.PROJECT.`is`(dataId) -> project
                    else -> null
                }
            })

            ActionManager.getInstance().getAction("com.mobin.plugin.RunSqlFileAction").update(event)
            ActionManager.getInstance().getAction("com.mobin.plugin.StopSqlAction").update(event)
            ActionManager.getInstance().getAction("com.mobin.plugin.RestartSqlAction").update(event)
        }

        private fun getConsoleView(project: Project): ConsoleView {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow("SQL Execution") 
                ?: toolWindowManager.registerToolWindow(RegisterToolWindowTask(
                    id = "SQL Execution",
                    anchor = ToolWindowAnchor.BOTTOM,
                    canCloseContent = true
                ))
            
            val existingContent = toolWindow.contentManager.findContent("SQL Execution")
            if (existingContent != null) {
                val component = existingContent.component
                if (component is JPanel) {
                    // Find the ConsoleView within the JPanel
                    val consoleView = component.components.filterIsInstance<ConsoleView>().firstOrNull()
                    if (consoleView != null) {
                        return consoleView
                    }
                } else if (component is ConsoleView) {
                    return component
                }
            }

            // If we couldn't find an existing ConsoleView, create a new one
            val consoleView = ConsoleViewImpl(project, true)
            val content = toolWindow.contentManager.factory.createContent(null, "SQL Execution", false)
            
            // Create custom action group for Restart and Stop buttons
            val customActionGroup = DefaultActionGroup().apply {
                add(RestartSqlAction())
                add(StopSqlAction())
            }

            // Create action toolbar with custom actions
            val customActionToolbar = ActionManager.getInstance().createActionToolbar(
                "SQLExecutionConsoleCustom",
                customActionGroup,
                true // horizontal
            )

            val topPanel = JPanel(BorderLayout())
            topPanel.add(customActionToolbar.component, BorderLayout.WEST)

            val mainPanel = JPanel(BorderLayout())
            mainPanel.add(topPanel, BorderLayout.NORTH)
            mainPanel.add(consoleView.component, BorderLayout.CENTER)

            content.component = mainPanel

            toolWindow.contentManager.addContent(content)
            toolWindow.show()

            // Create action toolbar with default console actions after the console is added to the panel
            val defaultActionToolbar = ActionManager.getInstance().createActionToolbar(
                "SQLExecutionConsoleDefault",
                DefaultActionGroup(*consoleView.createConsoleActions()),
                false // vertical
            )
            mainPanel.add(defaultActionToolbar.component, BorderLayout.EAST)

            return consoleView
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!virtualFile.name.endsWith(".sql", ignoreCase = true)) {
            Messages.showErrorDialog(project, "Please select a SQL file.", "Error")
            return
        }

        // Save all modified documents
        FileDocumentManager.getInstance().saveAllDocuments()

        // Refresh the virtual file to ensure we have the latest content
        virtualFile.refresh(false, false)

        lastSqlFilePath = virtualFile.path
        executeSqlFile(project, virtualFile.path)
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.name?.endsWith(".sql", ignoreCase = true) == true && !isRunning()
    }
}

class StopSqlAction : AnAction("Stop SQL", "Stop SQL execution", AllIcons.Actions.Suspend) {
    override fun actionPerformed(e: AnActionEvent) {
        RunSqlFileAction.stop()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = RunSqlFileAction.isRunning()
    }
}

class RestartSqlAction : AnAction("Restart SQL", "Restart SQL execution", AllIcons.Actions.Restart) {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { project ->
            // Save all modified documents
            FileDocumentManager.getInstance().saveAllDocuments()

            // Refresh the virtual file to ensure we have the latest content
            RunSqlFileAction.lastSqlFilePath?.let { path ->
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(path)
                virtualFile?.refresh(false, false)
            }

            RunSqlFileAction.restart(project)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = !RunSqlFileAction.isRunning()
    }
}