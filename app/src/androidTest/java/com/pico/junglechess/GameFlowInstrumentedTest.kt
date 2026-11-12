package com.pico.junglechess

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.junglechess.game.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.FutureTask

/** Device-side state/async integration tests; these do not simulate hand tracking. */
@RunWith(AndroidJUnit4::class)
class GameFlowInstrumentedTest {
    private val owner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    private lateinit var vm: GameViewModel
    private fun <T> main(block: () -> T): T {
        val task = FutureTask<T> { block() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
        return task.get()
    }
    @Before fun setup() { vm = main { ViewModelProvider(owner)[GameViewModel::class.java] } }
    @After fun teardown() { main { owner.viewModelStore.clear() } }
    private fun humanMove() = main {
        val move = Jungle.legalMoves(vm.state.value.position).first()
        assertTrue(vm.select(move.from))
        assertTrue(vm.drop(move.to))
    }
    private suspend fun awaitHumanTurn() = withTimeout(8000) {
        while (vm.state.value.thinking) delay(25)
    }
    @Test fun invalidDropDoesNotAdvanceAndAiReturnsExactlyOneLegalMove() = runBlocking {
        val start = vm.state.value.position
        main {
            assertFalse(vm.select(Square(0,0))) // Opponent piece.
            assertTrue(vm.select(Square(0,6)))
            assertFalse(vm.drop(Square(3,3)))
            assertEquals(start,vm.state.value.position)
            assertNull(vm.state.value.selected)
        }
        humanMove()
        val afterHuman = vm.state.value.position
        assertTrue(vm.state.value.thinking)
        main { assertFalse(vm.select(Square(6,8))) }
        awaitHumanTurn()
        val afterAi = vm.state.value.position
        assertEquals(2,afterAi.ply)
        assertEquals(Side.RED,afterAi.turn)
        assertTrue(Jungle.legalMoves(afterHuman).any { Jungle.play(afterHuman,it)==afterAi })
    }
    @Test fun changingDifficultyWhileWaitingCommitsOnlyOneAiMove() = runBlocking {
        humanMove()
        main {
            vm.setDifficulty(Difficulty.HARD)
            vm.setDifficulty(Difficulty.EASY)
        }
        awaitHumanTurn()
        delay(1600) // Wait beyond the old search budget to detect stale commits.
        assertEquals(2,vm.state.value.position.ply)
        assertEquals(Difficulty.EASY,vm.state.value.difficulty)
        assertFalse(vm.state.value.thinking)
    }
    @Test fun newGameDuringHardSearchDiscardsOldResult() = runBlocking {
        main { vm.setDifficulty(Difficulty.HARD) }
        humanMove()
        delay(600) // Search starts after the 500ms movement animation delay.
        main { vm.newGame() }
        delay(1700)
        assertEquals(Position.initial(),vm.state.value.position)
        assertEquals(Difficulty.HARD,vm.state.value.difficulty)
        assertFalse(vm.state.value.thinking)
        assertNull(vm.state.value.lastMove)
    }
}
