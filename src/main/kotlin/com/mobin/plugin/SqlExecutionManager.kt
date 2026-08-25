package com.mobin.plugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.ActivityTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel

class SqlExecutionSession(
    val project: Project,
    val sqlFilePath: String,
    val consoleView: ConsoleView
) {
    var processHandler: OSProcessHandler? = null

    val isRunning: Boolean
        get() {
            val handler = processHandler ?: return false
            return !handler.isProcessTerminated && !handler.isProcessTerminating
        }
}

class SqlExecutionManager(private val project: Project) {
    companion object {
        const val TOOL_WINDOW_ID = "SQL Execution"
        val SESSION_KEY = Key.create<SqlExecutionSession>("flinksql.execution.session")

        fun getInstance(project: Project): SqlExecutionManager =
            project.getService(SqlExecutionManager::class.java)
    }

    fun execute(sqlFilePath: String) {
        val commandLine = buildCommandLine(sqlFilePath) ?: return
        val session = createSession(sqlFilePath)
        startProcess(session, commandLine)
    }

    fun restart(session: SqlExecutionSession) {
        if (session.isRunning) return
        val commandLine = buildCommandLine(session.sqlFilePath) ?: return
        startProcess(session, commandLine)
    }

    fun stop(session: SqlExecutionSession) {
        session.processHandler?.destroyProcess()
    }

    fun getSelectedSession(): SqlExecutionSession? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return null
        return toolWindow.contentManager.selectedContent?.getUserData(SESSION_KEY)
    }

    private fun createSession(sqlFilePath: String): SqlExecutionSession {
        val toolWindow = getOrCreateToolWindow()
        val consoleView = ConsoleViewImpl(project, true)
        val session = SqlExecutionSession(project, sqlFilePath, consoleView)

        val customActionGroup = DefaultActionGroup().apply {
            add(RestartSqlAction(session))
            add(StopSqlAction(session))
        }
        val customActionToolbar = ActionManager.getInstance().createActionToolbar(
            "SQLExecutionConsoleCustom",
            customActionGroup,
            true
        )

        val topPanel = JPanel(BorderLayout())
        topPanel.add(customActionToolbar.component, BorderLayout.WEST)

        val mainPanel = object : JPanel(BorderLayout()), DataProvider {
            override fun getData(dataId: String): Any? {
                if (CommonDataKeys.PROJECT.`is`(dataId)) return project
                return null
            }
        }
        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(consoleView.component, BorderLayout.CENTER)

        customActionToolbar.targetComponent = mainPanel

        val defaultActionToolbar = ActionManager.getInstance().createActionToolbar(
            "SQLExecutionConsoleDefault",
            DefaultActionGroup(*consoleView.createConsoleActions()),
            false
        )
        defaultActionToolbar.targetComponent = mainPanel
        mainPanel.add(defaultActionToolbar.component, BorderLayout.EAST)

        val displayName = uniqueDisplayName(toolWindow, File(sqlFilePath).name)
        val content = toolWindow.contentManager.factory.createContent(mainPanel, displayName, false)
        content.isCloseable = true
        content.putUserData(SESSION_KEY, session)
        Disposer.register(content, consoleView)

        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
        toolWindow.show()
        return session
    }

    private fun startProcess(session: SqlExecutionSession, commandLine: GeneralCommandLine) {
        val processHandler = OSProcessHandler(commandLine)
        val consoleView = session.consoleView

        consoleView.clear()
        consoleView.print("Executing SQL file: ${session.sqlFilePath}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        consoleView.print("Command: ${commandLine.commandLineString}\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    if (event.exitCode == 0) {
                        consoleView.print("SQL file executed successfully.\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                    } else {
                        consoleView.print(
                            "Failed to execute SQL file. Exit code: ${event.exitCode}\n",
                            ConsoleViewContentType.ERROR_OUTPUT
                        )
                    }
                    if (session.processHandler === processHandler) {
                        session.processHandler = null
                    }
                    ActivityTracker.getInstance().inc()
                }
            }
        })

        consoleView.attachToProcess(processHandler)
        session.processHandler = processHandler
        processHandler.startNotify()
        ActivityTracker.getInstance().inc()
    }

    private fun buildCommandLine(sqlFilePath: String): GeneralCommandLine? {
        val settings = SqlExecutorSettings.getInstance(project)
        val jarDirectory = settings.jarDirectory
        if (jarDirectory.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "Flink Home directory is not set. Please configure it using the [flink-home] option.",
                "Error"
            )
            return null
        }

        val jarFiles = File(jarDirectory).listFiles { file -> file.extension.equals("jar", ignoreCase = true) }
        if (jarFiles.isNullOrEmpty()) {
            Messages.showErrorDialog(project, "No JAR files found in the specified Flink Home directory.", "Error")
            return null
        }

        val classpath = jarFiles.joinToString(File.pathSeparator) { it.absolutePath }
        val mainClass = "com.mobin.FlinkClient"
        val vmParams = PropertiesComponent.getInstance(project).getValue("flinkSQL.vmParams", "")

        val commandLine = GeneralCommandLine()
        commandLine.exePath = "java"

        if (vmParams.isNotEmpty()) {
            vmParams.split(" ").forEach { param ->
                if (param.isNotBlank()) {
                    commandLine.addParameter(param)
                }
            }
        }

        commandLine.addParameter("-cp")
        commandLine.addParameter(classpath)
        commandLine.addParameter(mainClass)

        val file = File(sqlFilePath)
        when {
            file.name.endsWith("_ddl.sql") -> {
                val dmlFile = File(file.parentFile, file.nameWithoutExtension.removeSuffix("_ddl") + "_dml.sql")
                if (!dmlFile.exists()) {
                    Messages.showErrorDialog(project, "Corresponding DML file not found: ${dmlFile.name}", "Error")
                    return null
                }
                commandLine.addParameter("--ddl")
                commandLine.addParameter(sqlFilePath)
                commandLine.addParameter("--dml")
                commandLine.addParameter(dmlFile.absolutePath)
            }
            file.name.endsWith("_dml.sql") -> {
                val ddlFile = File(file.parentFile, file.nameWithoutExtension.removeSuffix("_dml") + "_ddl.sql")
                if (!ddlFile.exists()) {
                    Messages.showErrorDialog(project, "Corresponding DDL file not found: ${ddlFile.name}", "Error")
                    return null
                }
                commandLine.addParameter("--ddl")
                commandLine.addParameter(ddlFile.absolutePath)
                commandLine.addParameter("--dml")
                commandLine.addParameter(sqlFilePath)
            }
            else -> {
                commandLine.addParameter("--sql")
                commandLine.addParameter(sqlFilePath)
            }
        }

        return commandLine
    }

    private fun getOrCreateToolWindow(): ToolWindow {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val existing = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)
        if (existing != null) return existing

        val toolWindow = toolWindowManager.registerToolWindow(
            RegisterToolWindowTask(
                id = TOOL_WINDOW_ID,
                anchor = ToolWindowAnchor.BOTTOM,
                canCloseContent = true
            )
        )
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                val session = event.content.getUserData(SESSION_KEY) ?: return
                session.processHandler?.let { handler ->
                    if (!handler.isProcessTerminated) {
                        handler.destroyProcess()
                    }
                }
                session.processHandler = null
                ActivityTracker.getInstance().inc()
            }
        })
        return toolWindow
    }

    private fun uniqueDisplayName(toolWindow: ToolWindow, baseName: String): String {
        val existingNames = toolWindow.contentManager.contents.map { it.displayName }.toSet()
        if (baseName !in existingNames) return baseName
        var index = 2
        while ("$baseName ($index)" in existingNames) {
            index++
        }
        return "$baseName ($index)"
    }
}
