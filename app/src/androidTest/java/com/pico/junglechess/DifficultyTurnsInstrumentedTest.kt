package com.pico.junglechess

import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.junglechess.game.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.FutureTask

@RunWith(AndroidJUnit4::class)
class DifficultyTurnsInstrumentedTest {
    private fun <T> main(block: () -> T): T {
        val task = FutureTask<T> { block() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
        return task.get()
    }
    @Test fun alternatingLevelsUseSelectedSearchAndCommitOneLegalReply() = runBlocking {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val vm = main { ViewModelProvider(owner)[GameViewModel::class.java] }
        try {
            repeat(8) { round ->
                val level = if (round%2 == 0) Difficulty.EASY else Difficulty.HARD
                main { vm.setDifficulty(level) }
                val before = vm.state.value.position
                assertNull(before.outcome)
                val human = Jungle.legalMoves(before).first()
                main {
                    assertTrue(vm.select(human.from))
                    assertTrue(vm.drop(human.to))
                }
                val afterHuman = Jungle.play(before,human)
                val start = System.nanoTime()
                withTimeout(8000) { while (vm.state.value.thinking) delay(20) }
                val after = vm.state.value
                assertEquals(level,after.difficulty)
                assertEquals(before.ply+2,after.position.ply)
                assertEquals(Side.RED,after.position.turn)
                assertTrue(Jungle.legalMoves(afterHuman).any { Jungle.play(afterHuman,it) == after.position })
                if (level == Difficulty.EASY) assertEquals(2,after.searchDepth)
                else assertTrue(after.searchDepth in 2..6)
                Log.i("JungleDeviceTurns","round=$round level=$level ply=${after.position.ply} depth=${after.searchDepth} nodes=${after.nodes} replyMs=${(System.nanoTime()-start)/1_000_000.0} PASS")
            }
        } finally { main { owner.viewModelStore.clear() } }
    }
}
