package com.mobin.plugin

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.example.dome1.SqlExecutorSettings",
    storages = [Storage("SqlExecutorPlugin.xml")]
)
class SqlExecutorSettings : PersistentStateComponent<SqlExecutorSettings> {
    var jarDirectory: String = ""

    override fun getState(): SqlExecutorSettings = this

    override fun loadState(state: SqlExecutorSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): SqlExecutorSettings =
            project.getService(SqlExecutorSettings::class.java)
    }
}