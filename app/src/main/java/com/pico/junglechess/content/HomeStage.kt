package com.pico.junglechess.content

import android.util.Log
import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pico.junglechess.game.*
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.*
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.CancellationException

@Composable
fun HomeStage(session: PanelSession) {
    val owner = requireNotNull(LocalViewModelStoreOwner.current)
    val vm = remember(owner) { ViewModelProvider(owner)[GameViewModel::class.java] }
    val state by vm.state.collectAsStateWithLifecycle()
    val panelVisible by session.panelVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val music = remember(owner) {
        ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application))[BackgroundMusic::class.java]
    }
    val navigator = LocalSpatialNavigator.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scene = remember(vm) { JungleScene(vm) }
    val skySettings = remember(owner) {
        ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application))[SkySettings::class.java]
    }
    val immersion by skySettings.level.collectAsStateWithLifecycle()
    LaunchedEffect(scene, immersion) { scene.setSkyImmersion(immersion) }
    val stageVisible by session.stageVisible.collectAsState()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    SideEffect { session.bind(PanelSession.Binding(vm, scene, loading, error, music, skySettings)) }
    DisposableEffect(scene, lifecycle, navigator) {
        session.attachStage(scene)
        session.setStageVisible(scene, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            Log.i("JungleStage", "lifecycle=$event")
            when (event) {
                Lifecycle.Event.ON_START -> session.setStageVisible(scene, true)
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                    music.setActive(false)
                    session.setStageVisible(scene, false)
                    navigator.closeWindowContainer(PanelSession.WINDOW_ID, PanelSession.WINDOW_TAG)
                    Log.i("JungleStage", "Stage hidden; controls closed")
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(scene) {
        onDispose {
            music.close()
            session.release(scene)
            navigator.closeWindowContainer(PanelSession.WINDOW_ID, PanelSession.WINDOW_TAG)
            scene.close()
        }
    }
    LaunchedEffect(loading, error, stageVisible) {
        music.setActive(!loading && error == null && stageVisible)
    }
    LaunchedEffect(vm, music, scene) {
        vm.selectedAnimals.collect { animal ->
            if (session.stageVisible.value && scene.ready) music.playAnimal(animal)
        }
    }
    fun openControls() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || !session.stageVisible.value) return
        if (!navigator.restoreWindowContainer(PanelSession.WINDOW_ID, PanelSession.WINDOW_TAG)) {
            navigator.openWindowContainer(PanelSession.WINDOW_ID, PanelSession.WINDOW_TAG)
        } else {
            session.windowRestored()
        }
    }
    LaunchedEffect(loading, stageVisible) { if (!loading && stageVisible) openControls() }
    // A finished match must announce its result even if the player hid the panel.
    LaunchedEffect(state.position.outcome, loading, stageVisible) {
        if (!loading && stageVisible && state.position.outcome != null) openControls()
    }
    LaunchedEffect(scene) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L && session.stageVisible.value)
                    scene.frame(((now - previous) / 1_000_000_000f).coerceAtMost(.05f))
                previous = now
            }
        }
    }
    SpatialView(
        modifier = Modifier.fillMaxSize()
            .pointerInput(scene) {
                detectSpatialDragGesture(context, TargetEntity.any { scene.isGameEntity(it) },
                    onDragStart = { _, v -> scene.beginDrag(v.targetEntity) },
                    onDragEnd = { scene.endDrag() },
                    onDragCancel = { scene.endDrag(true) }) { v ->
                    scene.drag(v.dragAmount.x, v.dragAmount.y, v.dragAmount.z)
                }
            }
            .pointerInput(scene) {
                detectSpatialTapGesture(context, TargetEntity.any { scene.isGameEntity(it) }) {
                    scene.tap(it.targetEntity)
                }
            },
        attachments = {
            AttachmentPanel("control") {
                PicoTheme {
                    Box(Modifier.width(256.dp).height(56.dp)) {
                        if (!panelVisible) {
                            Button(onClick = { openControls() }, modifier = Modifier.fillMaxSize()) {
                                Text("打开操作面板")
                            }
                        }
                    }
                }
            }
        },
        initial = { content, attachments ->
            try {
                scene.load(content, attachments)
                loading = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                scene.close()
                error = "场景加载失败，请退出后重新打开"
                loading = false
                Log.e("JungleScene", "Scene initialization failed", e)
            }
        },
        update = { _, attachments ->
            scene.setSkyImmersion(immersion)
            scene.sync(state)
            attachments.entity("control")?.enabled = !panelVisible
        }
    )
}
