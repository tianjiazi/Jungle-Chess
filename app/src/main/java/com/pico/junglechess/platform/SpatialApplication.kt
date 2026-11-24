package com.pico.junglechess.platform

import android.app.Application
import android.app.ActivityManager
import android.util.Log
import com.pico.junglechess.content.PanelSession
import com.pico.spatial.ui.platform.containers.closeStage
import com.pico.spatial.ui.platform.containers.closeWindowContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch as launchCoroutine
import com.pico.spatial.ui.foundation.dsl.launch
import com.pico.junglechess.mainApp

class SpatialApplication : Application() {
    private val exitScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var exiting = false

    /** Outlives the window's Composition, which is disposed as soon as closing starts. */
    fun exitGame(session: PanelSession) {
        if (exiting) return
        exiting = true
        session.binding.value?.let {
            it.scene.endDrag(true)
            session.setStageVisible(it.scene, false)
        }
        exitScope.launchCoroutine {
            try {
                closeWindowContainer(PanelSession.WINDOW_ID, PanelSession.WINDOW_TAG)
                closeStage()
            } catch (error: Exception) {
                Log.e("JungleExit", "Container shutdown failed; finishing application tasks", error)
            } finally {
                getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
                exiting = false
                Log.i("JungleExit", "Game closed; application tasks removed")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
