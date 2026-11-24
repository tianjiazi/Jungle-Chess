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
        val audio: BackgroundMusic,
        val sky: SkySettings,
    )
    private val mutable = MutableStateFlow<Binding?>(null)
    val binding = mutable.asStateFlow()
    private val visible = MutableStateFlow(false)
    val panelVisible = visible.asStateFlow()
    private val stageVisibility = MutableStateFlow(false)
    val stageVisible = stageVisibility.asStateFlow()
    private var stageOwner: JungleScene? = null
    private var windowOwner: Any? = null

    fun attachStage(scene: JungleScene) { stageOwner = scene }
    fun setStageVisible(scene: JungleScene, isVisible: Boolean) {
        if (stageOwner !== scene) return
        stageVisibility.value = isVisible
        if (!isVisible) visible.value = false
    }
    fun attachWindow(owner: Any) { windowOwner = owner }
    fun setWindowVisible(owner: Any, isVisible: Boolean) {
        if (windowOwner === owner) visible.value = isVisible && stageVisibility.value
    }
    fun detachWindow(owner: Any) {
        if (windowOwner === owner) {
            windowOwner = null
            visible.value = false
        }
    }
    fun windowRestored() { visible.value = stageVisibility.value }

    fun bind(binding: Binding) { mutable.value = binding }
    fun release(scene: JungleScene) {
        if (stageOwner === scene) {
            stageOwner = null
            stageVisibility.value = false
        }
        if (mutable.value?.scene === scene) {
            mutable.value = null
            visible.value = false
        }
    }

    companion object {
        const val WINDOW_ID = "GameControls"
        const val WINDOW_TAG = "controls"
        const val HOME_HEIGHT = 608
    }
}
