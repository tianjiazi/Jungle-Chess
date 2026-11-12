package com.pico.junglechess.content

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
    val navigator = LocalSpatialNavigator.current
    val scene = remember(vm) { JungleScene(vm) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    SideEffect { session.bind(PanelSession.Binding(vm, scene, loading, error)) }
    DisposableEffect(scene) {
        onDispose {
            session.release(scene)
            navigator.closeWindowContainer(PanelSession.WINDOW_ID, PanelSession.WINDOW_TAG)
            scene.close()
        }
    }
    fun openControls() {
        if (!navigator.restoreWindowContainer(PanelSession.WINDOW_ID, PanelSession.WINDOW_TAG)) {
            navigator.openWindowContainer(PanelSession.WINDOW_ID, PanelSession.WINDOW_TAG)
        } else {
            session.windowRestored()
        }
    }
    LaunchedEffect(loading) { if (!loading) openControls() }
    LaunchedEffect(scene) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) scene.frame(((now - previous) / 1_000_000_000f).coerceAtMost(.05f))
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
            scene.sync(state)
            attachments.entity("control")?.enabled = !panelVisible
        }
    )
}
