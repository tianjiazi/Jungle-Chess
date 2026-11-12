package com.pico.junglechess.content

import com.pico.junglechess.game.GameViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shares the Stage-owned game with its separate window Activity; never owns a second game. */
class PanelSession {
    data class Binding(
        val vm: GameViewModel,
        val scene: JungleScene,
        val loading: Boolean,
        val error: String?,
    )
    private val mutable = MutableStateFlow<Binding?>(null)
    val binding = mutable.asStateFlow()
    private val visible = MutableStateFlow(false)
    val panelVisible = visible.asStateFlow()
    private var windowOwner: Any? = null

    fun attachWindow(owner: Any) { windowOwner = owner }
    fun setWindowVisible(owner: Any, isVisible: Boolean) {
        if (windowOwner === owner) visible.value = isVisible
    }
    fun detachWindow(owner: Any) {
        if (windowOwner === owner) {
            windowOwner = null
            visible.value = false
        }
    }
    fun windowRestored() { visible.value = true }

    fun bind(binding: Binding) { mutable.value = binding }
    fun release(scene: JungleScene) {
        if (mutable.value?.scene === scene) {
            mutable.value = null
            visible.value = false
        }
    }

    companion object {
        const val WINDOW_ID = "GameControls"
        const val WINDOW_TAG = "controls"
    }
}
