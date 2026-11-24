package com.pico.junglechess.content

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-owned immersion: Mixed Stage preserves foreground objects at every level. */
class SkySettings(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("sky_settings", 0)
    private val mutableLevel = MutableStateFlow(
        preferences.getInt("immersion", 100).coerceIn(0, 100))
    val level = mutableLevel.asStateFlow()

    fun setLevel(value: Int) {
        val next = value.coerceIn(0, 100)
        if (mutableLevel.value == next) return
        mutableLevel.value = next
        preferences.edit().putInt("immersion", next).apply()
    }
}
