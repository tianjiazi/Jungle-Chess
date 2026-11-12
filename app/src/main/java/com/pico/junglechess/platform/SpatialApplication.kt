package com.pico.junglechess.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.pico.junglechess.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
