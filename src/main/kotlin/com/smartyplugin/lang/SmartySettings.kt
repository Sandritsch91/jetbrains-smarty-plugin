package com.smartyplugin.lang

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.FileContentUtil

/** Project settings, stored in .idea/smarty.xml so they can be shared with the team. */
@Service(Service.Level.PROJECT)
@State(name = "SmartySettings", storages = [Storage("smarty.xml")])
class SmartySettings : PersistentStateComponent<SmartySettings.State> {

    class State {
        @JvmField
        var leftDelimiter: String? = SmartyDelimiters.DEFAULT.left

        @JvmField
        var rightDelimiter: String? = SmartyDelimiters.DEFAULT.right
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun delimiters(): SmartyDelimiters {
        val left = state.leftDelimiter
        val right = state.rightDelimiter
        if (left.isNullOrEmpty() || right.isNullOrEmpty()) return SmartyDelimiters.DEFAULT
        return SmartyDelimiters(left, right)
    }

    /** Changes the delimiters and re-parses open files, so highlighting and the PSI follow. */
    fun setDelimiters(delimiters: SmartyDelimiters) {
        if (delimiters == delimiters()) return
        state.leftDelimiter = delimiters.left
        state.rightDelimiter = delimiters.right
        FileContentUtil.reparseOpenedFiles()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SmartySettings = project.getService(SmartySettings::class.java)
    }
}
