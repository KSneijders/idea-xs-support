package com.xscheck

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@State(
    name = "XsCheckSettings",
    storages = [Storage("xs-check.xml")]
)
@Service(Service.Level.PROJECT)
class XsSettings : PersistentStateComponent<XsSettings.State> {

    class State {
        var extraPreludePath: String = ""
        var includeDirectories: MutableList<String> = mutableListOf()
        var ignores: MutableList<String> = mutableListOf()
        var flavour: String = "AoE2:DE"
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(project: Project): XsSettings = project.getService(XsSettings::class.java)
    }
}
