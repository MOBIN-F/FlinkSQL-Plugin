package com.mobin.plugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem

class RunSqlFileAction : AnAction("Run FlinkSQL", "Run the selected SQL file", AllIcons.Actions.Execute), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!virtualFile.name.endsWith(".sql", ignoreCase = true)) {
            Messages.showErrorDialog(project, "Please select a SQL file.", "Error")
            return
        }

        FileDocumentManager.getInstance().saveAllDocuments()
        virtualFile.refresh(false, false)
        SqlExecutionManager.getInstance(project).execute(virtualFile.path)
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.name?.endsWith(".sql", ignoreCase = true) == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class StopSqlAction @JvmOverloads constructor(
    private val session: SqlExecutionSession? = null
) : AnAction("Stop SQL", "Stop SQL execution", AllIcons.Actions.Suspend), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val target = resolveSession(e) ?: return
        SqlExecutionManager.getInstance(target.project).stop(target)
    }

    override fun update(e: AnActionEvent) {
        val running = resolveSession(e)?.isRunning == true
        if (session != null) {
            e.presentation.isVisible = true
            e.presentation.isEnabled = running
        } else {
            e.presentation.isEnabledAndVisible = running
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    private fun resolveSession(e: AnActionEvent): SqlExecutionSession? {
        return session ?: e.project?.let { SqlExecutionManager.getInstance(it).getSelectedSession() }
    }
}

class RestartSqlAction @JvmOverloads constructor(
    private val session: SqlExecutionSession? = null
) : AnAction("Restart SQL", "Restart SQL execution", AllIcons.Actions.Restart), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val target = resolveSession(e) ?: return

        FileDocumentManager.getInstance().saveAllDocuments()
        LocalFileSystem.getInstance().findFileByPath(target.sqlFilePath)?.refresh(false, false)
        SqlExecutionManager.getInstance(target.project).restart(target)
    }

    override fun update(e: AnActionEvent) {
        val target = resolveSession(e)
        val canRestart = target != null && !target.isRunning
        if (session != null) {
            e.presentation.isVisible = true
            e.presentation.isEnabled = canRestart
        } else {
            e.presentation.isEnabledAndVisible = canRestart
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    private fun resolveSession(e: AnActionEvent): SqlExecutionSession? {
        return session ?: e.project?.let { SqlExecutionManager.getInstance(it).getSelectedSession() }
    }
}
