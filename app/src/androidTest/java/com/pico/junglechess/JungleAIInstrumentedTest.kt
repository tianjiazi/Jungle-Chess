package com.pico.junglechess

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pico.junglechess.game.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** Runs the production AI on the headset; does not simulate hand gestures. */
@RunWith(AndroidJUnit4::class)
class JungleAIInstrumentedTest {
    private fun position(vararg pieces: Triple<Side, Animal, Square>): Position {
        val board = MutableList<Piece?>(63) { null }
        pieces.forEach { (side, animal, square) -> board[square.index] = Piece(side, animal) }
        return Position(board, turn = Side.BLUE)
    }
    private fun b(a: Animal, x: Int, y: Int) = Triple(Side.BLUE, a, Square(x,y))
    private fun r(a: Animal, x: Int, y: Int) = Triple(Side.RED, a, Square(x,y))
    private fun choose(name: String, p: Position, level: Difficulty, budget: Long = 1100): SearchResult {
        val start = System.nanoTime()
        val result = JungleAI().choose(p, level, budget)
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        assertTrue(result.move in Jungle.legalMoves(p))
        Log.i("JungleDeviceAI", "case=$name level=$level depth=${result.depth} nodes=${result.nodes} ms=$elapsed move=${result.move}")
        return result
    }

    @Test fun bothLevelsTakeSafeMaterialAndRespectSpecialCaptures() {
        val cases = listOf(
            Triple("safeCapture", position(b(Animal.DOG,0,4),b(Animal.RAT,6,6),
                r(Animal.CAT,0,5),r(Animal.ELEPHANT,6,0)), Move(Square(0,4),Square(0,5))),
            Triple("ratElephant", position(b(Animal.RAT,0,4),b(Animal.CAT,6,6),
                r(Animal.ELEPHANT,0,5),r(Animal.DOG,6,0)), Move(Square(0,4),Square(0,5))),
            Triple("riverJump", position(b(Animal.LION,0,4),b(Animal.ELEPHANT,6,2),
                r(Animal.DOG,3,4),r(Animal.RAT,6,6)), Move(Square(0,4),Square(3,4)))
        )
        for ((name,p,expected) in cases) for (level in Difficulty.entries) {
            assertEquals(expected, choose(name,p,level).move)
        }
    }

    @Test fun bothLevelsAvoidLionBaitAndImmediateRecapture() {
        val p = position(b(Animal.LION,0,2),b(Animal.RAT,6,2),
            r(Animal.CAT,0,1),r(Animal.ELEPHANT,1,1),r(Animal.DOG,6,6))
        val bait = Move(Square(0,2),Square(0,1))
        assertTrue(bait in Jungle.legalMoves(p))
        for (level in Difficulty.entries) {
            val result = choose("lionBait",p,level)
            assertNotEquals(bait,result.move)
            val next = Jungle.play(p,result.move!!)
            assertFalse(Jungle.legalMoves(next).any { next[it.to] == Piece(Side.BLUE,Animal.LION) })
        }
    }

    @Test fun bothLevelsWinDenAndDefendImmediateDenThreat() {
        val win = position(b(Animal.RAT,3,7),r(Animal.ELEPHANT,0,6))
        val defend = position(r(Animal.RAT,3,1),b(Animal.CAT,2,1),b(Animal.ELEPHANT,6,2))
        for (level in Difficulty.entries) {
            assertEquals(Move(Square(3,7),Square(3,8)),choose("winDen",win,level).move)
            assertEquals(Move(Square(2,1),Square(3,1)),choose("defendDen",defend,level).move)
        }
    }

    @Test fun twoVsSixLayersAndExhaustedBudgetPreservesBasicSearch() {
        val p = position(b(Animal.CAT,0,0),r(Animal.CAT,6,8))
        val easy = choose("depth",p,Difficulty.EASY,3000)
        val hard = choose("depth",p,Difficulty.HARD,3000)
        assertEquals(2,easy.depth)
        assertEquals(6,hard.depth)
        assertTrue(hard.nodes > easy.nodes)
        val initial = Position.initial()
        val zeroEasy = choose("zeroBudget",initial,Difficulty.EASY,0)
        val zeroHard = choose("zeroBudget",initial,Difficulty.HARD,0)
        assertEquals(2,zeroEasy.depth)
        assertEquals(2,zeroHard.depth)
        assertEquals(zeroEasy.move,zeroHard.move)
    }

    @Test fun representativePositionsProduceLegalMovesAndMeasuredDepths() {
        repeat(12) { sample ->
            var p = Position.initial()
            val rng = Random(1600+sample)
            repeat(sample*3) {
                if (p.outcome == null) p = Jungle.play(p,Jungle.legalMoves(p).random(rng))
            }
            assertNull(p.outcome)
            for (level in Difficulty.entries) {
                val result = choose("sample$sample",p,level)
                assertTrue(result.depth in 1..level.maxDepth)
                if (level == Difficulty.EASY && result.depth != 2) {
                    assertNotNull(Jungle.play(p,result.move!!).outcome)
                }
            }
        }
    }
}
