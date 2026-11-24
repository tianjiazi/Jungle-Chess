package com.pico.junglechess

import com.pico.junglechess.content.HomeStage
import com.pico.junglechess.content.PanelSession
import com.pico.junglechess.content.ControlWindow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.platform.resize.CaptionBarType
import com.pico.spatial.ui.platform.resize.ContainerResizeType

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        val session = PanelSession()
        WindowContainer(
            id = PanelSession.WINDOW_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(640.dp, PanelSession.HOME_HEIGHT.dp),
            resizeType = ContainerResizeType.ContentSize,
            enableMaterialBackground = true,
            defaultCaptionBarType = CaptionBarType.Default,
        ) {
            PicoTheme {
                ControlWindow(session)
            }
        }
        DefaultStage {
            PicoTheme {
                HomeStage(session)
            }
        }
    }
