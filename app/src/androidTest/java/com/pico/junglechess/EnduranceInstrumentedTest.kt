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
import kotlin.random.Random

/** On-headset integration/endurance checks of real rules and coroutine turns. */
@RunWith(AndroidJUnit4::class)
class EnduranceInstrumentedTest {
    private fun <T> main(block: () -> T): T {
        val task=FutureTask<T> { block() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
        return task.get()
    }

    @Test fun continuousTurnsInvalidDropsAndDifficultyChanges() = runBlocking {
        val owner=object: ViewModelStoreOwner { override val viewModelStore=ViewModelStore() }
        val vm=main { ViewModelProvider(owner)[GameViewModel::class.java] }
        val rng=Random(1103)
        var completed=0
        try {
            repeat(4) { match ->
                main {
                    vm.newGame()
                    vm.setDifficulty(if(match%2==0) Difficulty.EASY else Difficulty.HARD)
                }
                repeat(16) { round ->
                    val before=vm.state.value.position
                    if (before.outcome!=null) return@repeat
                    val move=Jungle.legalMoves(before).random(rng)
                    main {
                        assertTrue(vm.select(move.from))
                        assertFalse(vm.drop(null))
                        assertEquals(before,vm.state.value.position)
                        assertTrue(vm.select(move.from))
                        assertTrue(vm.drop(move.to))
                        assertFalse(vm.drop(move.to)) // Duplicate release cannot commit.
                        if(round%5==2) {
                            val chosen=vm.state.value.difficulty
                            vm.setDifficulty(if(chosen==Difficulty.HARD) Difficulty.EASY else Difficulty.HARD)
                            vm.setDifficulty(chosen)
                        }
                    }
                    val human=Jungle.play(before,move)
                    withTimeout(6000) { while(vm.state.value.thinking) delay(20) }
                    val after=vm.state.value.position
                    if(human.outcome!=null) assertEquals(human,after)
                    else {
                        assertEquals(before.ply+2,after.ply)
                        assertTrue(Jungle.legalMoves(human).any { Jungle.play(human,it)==after })
                    }
                    assertNull(vm.state.value.selected)
                    completed++
                }
            }
            Log.i("JungleEndurance","runtimeTurns=$completed restart=4 invalidAndDuplicateReleases=$completed PASS")
            assertTrue(completed>=32)
        } finally { main { owner.viewModelStore.clear() } }
    }

    @Test fun seededFullGamesWithBothAiLevels() {
        var plies=0; var captures=0; var jumps=0; var swims=0; var endings=0
        var hardSearches=0; var maxSearchMs=0L
        repeat(60) { seed ->
            var p=Position.initial()
            val ai=JungleAI(Random(seed+1100))
            var steps=0
            while(p.outcome==null && steps<600) {
                val level=if(seed<8 && p.turn==Side.BLUE) Difficulty.HARD else Difficulty.EASY
                val start=System.nanoTime()
                val result=ai.choose(p,level,budgetMs=35)
                val ms=(System.nanoTime()-start)/1_000_000
                maxSearchMs=maxOf(maxSearchMs,ms)
                if(level==Difficulty.HARD) hardSearches++
                val move=requireNotNull(result.move)
                assertTrue(move in Jungle.legalMoves(p))
                val moving=requireNotNull(p[move.from])
                val old=p
                if(p[move.to]!=null) captures++
                if(Jungle.distance(move.from,move.to)>1) jumps++
                if(Jungle.water(move.to)) swims++
                p=Jungle.play(p,move)
                assertEquals(old.ply+1,p.ply)
                assertEquals(old.turn.other(),p.turn)
                assertEquals(moving,p[move.to])
                assertNull(p[move.from])
                assertEquals(p.board.filterNotNull().size,p.board.filterNotNull().map { it.id }.toSet().size)
                p.board.forEachIndexed { i,piece ->
                    if(piece!=null && Jungle.water(Square(i%7,i/7))) assertEquals(Animal.RAT,piece.animal)
                }
                plies++; steps++
            }
            if(p.outcome!=null) {
                endings++
                assertTrue(Jungle.legalMoves(p).isEmpty())
                assertNull(ai.choose(p,Difficulty.HARD).move)
            }
        }
        assertTrue(captures>100)
        assertTrue(jumps>10)
        assertTrue(swims>10)
        assertTrue(endings>20)
        Log.i("JungleEndurance","games=60 plies=$plies captures=$captures jumps=$jumps waterMoves=$swims endings=$endings hardSearches=$hardSearches maxSearchMs=$maxSearchMs PASS")
    }
}
