package com.pico.junglechess.content

import kotlin.math.PI
import kotlin.math.sin

/** Metres and seconds, relative to each piece's unchanged board square. */
object VictoryMotion {
    const val SETTLE_SECONDS = .4f
    const val FIREWORK_SECONDS = 9f
    const val BURST_GROUPS = 3
    const val SPARKS_PER_BURST = 24

    fun height(id: Int) = .045f + (id % 8) * .011f
    fun frequency(id: Int) = .9f + (id % 8) * .09f
    fun fireworkAge(group: Int, seconds: Float): Float? {
        val delayed = seconds - group * .8f
        if (seconds < 0f || seconds >= FIREWORK_SECONDS || delayed < 0f) return null
        return delayed % 3f
    }
    fun jump(id: Int, seconds: Float): Float {
        if (seconds <= 0f) return 0f
        val phase = (id % 8) * .137f
        val wave = sin(PI.toFloat() * (seconds * frequency(id) + phase))
        return height(id) * wave * wave * (seconds / .25f).coerceAtMost(1f)
    }
}
