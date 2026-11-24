package com.pico.junglechess.content

import org.junit.Assert.*
import org.junit.Test

class VictoryMotionTest {
    @Test fun everyAnimalHasItsOwnFrequencyAndHeightOnEitherSide() {
        assertEquals(8, (0..7).map(VictoryMotion::height).toSet().size)
        assertEquals(8, (0..7).map(VictoryMotion::frequency).toSet().size)
        for (id in 0..7) {
            assertEquals(VictoryMotion.height(id), VictoryMotion.height(id+8), 0f)
            assertEquals(VictoryMotion.frequency(id), VictoryMotion.frequency(id+8), 0f)
        }
    }

    @Test fun jumpsStartAtBoardLevelStayAboveItAndReachDifferentPeaks() {
        for (id in 0..15) {
            assertEquals(0f,VictoryMotion.jump(id,0f),0f)
            assertEquals(0f,VictoryMotion.jump(id,-1f),0f)
            var peak=0f
            for (frame in 1..9000) {
                val y=VictoryMotion.jump(id,frame/1000f)
                assertTrue(y.isFinite() && y>=0f && y<=VictoryMotion.height(id)+.000001f)
                peak=maxOf(peak,y)
            }
            assertEquals(VictoryMotion.height(id),peak,.00001f)
        }
    }

    @Test fun animationsRemainAsynchronousRatherThanJumpingTogether() {
        for (time in listOf(.3f,.8f,1.1f,2.7f)) {
            assertTrue((0..7).map { VictoryMotion.jump(it,time) }.toSet().size>6)
        }
    }

    @Test fun fireworkLaunchesAreStaggeredBoundedAndRestartable() {
        assertEquals(0f,VictoryMotion.fireworkAge(0,0f)!!,0f)
        assertNull(VictoryMotion.fireworkAge(1,0f))
        assertNull(VictoryMotion.fireworkAge(2,.8f))
        assertEquals(0f,VictoryMotion.fireworkAge(2,1.6f)!!,0f)
        for (group in 0 until VictoryMotion.BURST_GROUPS) {
            assertNull(VictoryMotion.fireworkAge(group,-1f))
            assertNull(VictoryMotion.fireworkAge(group,9f))
            assertNull(VictoryMotion.fireworkAge(group,90f))
            for (frame in 0..900) {
                VictoryMotion.fireworkAge(group,frame/100f)?.let { assertTrue(it>=0f && it<3f) }
            }
        }
        assertEquals(VictoryMotion.fireworkAge(0,.9f)!!,VictoryMotion.fireworkAge(0,3.9f)!!,.000001f)
    }
}
