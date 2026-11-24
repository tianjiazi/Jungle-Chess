package com.pico.junglechess.game

import org.junit.Assert.*
import org.junit.Test

class JungleAITest {
    private fun position(vararg pieces: Triple<Side, Animal, Square>): Position {
        val board = MutableList<Piece?>(63) { null }
        pieces.forEach { (side, animal, square) -> board[square.index] = Piece(side, animal) }
        return Position(board, turn = Side.BLUE)
    }
    private fun blue(animal: Animal, x: Int, y: Int) = Triple(Side.BLUE, animal, Square(x, y))
    private fun red(animal: Animal, x: Int, y: Int) = Triple(Side.RED, animal, Square(x, y))

    @Test fun bothLevelsTakeSafeMaterial() {
        val p = position(blue(Animal.DOG, 0, 4), blue(Animal.RAT, 6, 6),
            red(Animal.CAT, 0, 5), red(Animal.ELEPHANT, 6, 0))
        for (level in Difficulty.entries) {
            assertEquals(Move(Square(0, 4), Square(0, 5)), JungleAI().choose(p, level, 0).move)
        }
    }

    @Test fun bothLevelsAvoidLosingLionForCatBait() {
        val p = position(blue(Animal.LION, 0, 2), blue(Animal.RAT, 6, 2),
            red(Animal.CAT, 0, 1), red(Animal.ELEPHANT, 1, 1), red(Animal.DOG, 6, 6))
        val bait = Move(Square(0, 2), Square(0, 1))
        assertTrue(bait in Jungle.legalMoves(p))
        val afterBait = Jungle.play(p, bait)
        assertTrue(Move(Square(1, 1), Square(0, 1)) in Jungle.legalMoves(afterBait))
        for (level in Difficulty.entries) {
            val chosen = JungleAI().choose(p, level, 0).move
            assertTrue(chosen in Jungle.legalMoves(p))
            assertNotEquals(bait, chosen)
            // 狮子即使不吃诱饵，也必须避开象可立即吃掉的位置。
            val next = Jungle.play(p, chosen!!)
            assertFalse(Jungle.legalMoves(next).any { next[it.to] == Piece(Side.BLUE, Animal.LION) })
        }
    }

    @Test fun bothLevelsUseRatElephantRule() {
        val p = position(blue(Animal.RAT, 0, 4), blue(Animal.CAT, 6, 6),
            red(Animal.ELEPHANT, 0, 5), red(Animal.DOG, 6, 0))
        for (level in Difficulty.entries) {
            assertEquals(Move(Square(0, 4), Square(0, 5)), JungleAI().choose(p, level, 0).move)
        }
    }

    @Test fun bothLevelsUseLegalRiverJumpCapture() {
        val p = position(blue(Animal.LION, 0, 4), blue(Animal.ELEPHANT, 6, 2),
            red(Animal.DOG, 3, 4), red(Animal.RAT, 6, 6))
        for (level in Difficulty.entries) {
            assertEquals(Move(Square(0, 4), Square(3, 4)), JungleAI().choose(p, level, 0).move)
        }
    }

    @Test fun difficultyChangesDepthWithoutChangingBasicEvaluation() {
        val p = position(blue(Animal.CAT, 0, 0), red(Animal.CAT, 6, 8))
        val easy = JungleAI().choose(p, Difficulty.EASY, 3000)
        val hard = JungleAI().choose(p, Difficulty.HARD, 3000)
        assertEquals(2, easy.depth)
        assertEquals(6, hard.depth)
        assertTrue(hard.nodes > easy.nodes)
        assertTrue(easy.move in Jungle.legalMoves(p))
        assertTrue(hard.move in Jungle.legalMoves(p))
    }

    @Test fun exhaustedBudgetKeepsCompleteBasicSearch() {
        val p = Position.initial()
        val easy = JungleAI().choose(p, Difficulty.EASY, 0)
        val hard = JungleAI().choose(p, Difficulty.HARD, 0)
        val negative = JungleAI().choose(p, Difficulty.HARD, -1)
        assertEquals(2, easy.depth)
        assertEquals(2, hard.depth)
        assertEquals(easy.move, hard.move)
        assertEquals(hard.move, negative.move)
    }

    @Test fun cancellationDoesNotReturnAnUnfinishedMoveAndInstanceCanBeReused() {
        val ai = JungleAI()
        val p = Position.initial()
        for (level in Difficulty.entries) {
            val stopped = ai.choose(p, level, cancelled = { true })
            assertNull(stopped.move)
            assertEquals(0, stopped.nodes)
        }
        var checks = 0
        val interrupted = ai.choose(p, Difficulty.HARD, cancelled = { ++checks >= 10 })
        assertNull(interrupted.move)
        assertTrue(checks >= 10)
        assertEquals(2, ai.choose(p, Difficulty.EASY).depth)
    }

    @Test fun completedGameAndNoMovesReturnNoDecision() {
        val p = position(blue(Animal.RAT, 3, 7), red(Animal.ELEPHANT, 0, 6))
        val ended = Jungle.play(p, Move(Square(3, 7), Square(3, 8)))
        for (level in Difficulty.entries) {
            assertNull(JungleAI().choose(ended, level).move)
            assertNull(JungleAI().choose(Position(List(63) { null }), level).move)
        }
    }
}
