package com.pico.junglechess

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.junglechess.game.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.FutureTask

/** Near-terminal seeds are confined to this test APK; production has no setup/debug controls. */
@RunWith(AndroidJUnit4::class)
class EndgameInstrumentedTest {
    private fun <T> main(block: () -> T): T {
        val task=FutureTask<T> { block() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
        return task.get()
    }
    private fun position(red: Square, blue: Square, quiet: Int = 0): Position {
        val board=MutableList<Piece?>(63) { null }
        board[red.index]=Piece(Side.RED,Animal.RAT)
        board[blue.index]=Piece(Side.BLUE,Animal.RAT)
        return Position(board,quiet=quiet)
    }
    private suspend fun verify(seed: Position, human: Move, winner: Side?, title: String) {
        val owner=object : ViewModelStoreOwner { override val viewModelStore=ViewModelStore() }
        val vm=main { ViewModelProvider(owner)[GameViewModel::class.java] }
        try {
            main {
                // Reflection is test-only and avoids adding a position injection API to the app.
                val field=GameViewModel::class.java.getDeclaredField("mutable").apply { isAccessible=true }
                @Suppress("UNCHECKED_CAST")
                val flow=field.get(vm) as MutableStateFlow<GameUiState>
                flow.value=GameUiState(position=seed,difficulty=Difficulty.HARD)
                assertTrue(vm.select(human.from))
                assertTrue(vm.drop(human.to))
            }
            withTimeout(8000) { while(vm.state.value.thinking) delay(20) }
            val ended=vm.state.value
            assertNotNull(ended.position.outcome)
            assertEquals(winner,ended.position.outcome!!.winner)
            assertEquals(title,ended.position.outcome!!.title)
            assertFalse(ended.thinking)
            main {
                assertFalse(vm.select(human.to))
                assertFalse(vm.drop(human.from))
                vm.newGame()
            }
            delay(1600)
            val fresh=vm.state.value
            assertEquals(Position.initial(),fresh.position)
            assertEquals(Difficulty.HARD,fresh.difficulty)
            assertNull(fresh.position.outcome)
            assertNull(fresh.lastMove)
            assertNull(fresh.selected)
            assertFalse(fresh.thinking)
            assertEquals(0,fresh.searchDepth)
            assertEquals(0,fresh.nodes)
        } finally { main { owner.viewModelStore.clear() } }
    }
    @Test fun playerVictoryThenNewGameClearsTerminalState() = runBlocking {
        verify(position(Square(3,1),Square(6,6)),Move(Square(3,1),Square(3,0)),Side.RED,"你赢了！")
    }
    @Test fun aiVictoryThenNewGameClearsTerminalState() = runBlocking {
        verify(position(Square(0,1),Square(3,7)),Move(Square(0,1),Square(0,2)),Side.BLUE,"AI 获胜")
    }
    @Test fun drawThenNewGameClearsTerminalState() = runBlocking {
        verify(position(Square(0,6),Square(6,2),quiet=99),Move(Square(0,6),Square(0,5)),null,"本局和棋")
    }
}
