package com.pico.junglechess.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class JungleRulesTest {
    private fun pos(vararg pieces: Triple<Side, Animal, Square>, turn: Side = Side.RED): Position {
        val board = MutableList<Piece?>(63) { null }
        pieces.forEach { (side, animal, square) -> board[square.index] = Piece(side, animal) }
        return Position(board, turn)
    }
    private fun r(a: Animal, x: Int, y: Int) = Triple(Side.RED, a, Square(x, y))
    private fun b(a: Animal, x: Int, y: Int) = Triple(Side.BLUE, a, Square(x, y))
    private fun legal(p: Position, x: Int, y: Int, tx: Int, ty: Int) =
        Move(Square(x, y), Square(tx, ty)) in Jungle.legalMoves(p)

    @Test fun standardSetup() {
        val p = Position.initial()
        assertEquals(63, p.board.size)
        assertEquals(16, p.board.count { it != null })
        for (side in Side.entries) assertEquals(Animal.entries.toSet(), p.board.filterNotNull().filter { it.side == side }.map { it.animal }.toSet())
        assertEquals(Animal.TIGER, p[Square(0,8)]!!.animal)
        assertEquals(Animal.LION, p[Square(6,8)]!!.animal)
        assertEquals(Animal.ELEPHANT, p[Square(0,6)]!!.animal)
        assertEquals(12, (0..62).count { Jungle.water(Square(it%7,it/7)) })
        assertEquals(6, (0..62).count { Jungle.trap(Square(it%7,it/7)) != null })
        assertEquals(24, Jungle.legalMoves(p).size)
    }
    @Test fun noDiagonalAndNoFriendlyCapture() {
        val p = pos(r(Animal.CAT,3,4),r(Animal.DOG,3,3),b(Animal.RAT,6,0))
        assertFalse(legal(p,3,4,2,3))
        assertFalse(legal(p,3,4,3,3))
        assertTrue(legal(p,3,4,3,5))
    }
    @Test fun rankMatrixOnLand() {
        for (a in Animal.entries) for (b in Animal.entries) {
            val p = pos(r(a,0,3),b(b,0,4))
            val expected = when {
                a == Animal.ELEPHANT && b == Animal.RAT -> false
                a == Animal.RAT && b == Animal.ELEPHANT -> true
                else -> a.rank >= b.rank
            }
            assertEquals("$a vs $b", expected, legal(p,0,3,0,4))
        }
    }
    @Test fun onlyRatSwims() {
        for (a in Animal.entries) {
            val p = pos(r(a,0,4), b(Animal.CAT,6,0))
            assertEquals(a == Animal.RAT, legal(p,0,4,1,4))
        }
    }
    @Test fun ratCannotCaptureAcrossEitherBankDirection() {
        assertFalse(legal(pos(r(Animal.RAT,0,3),b(Animal.RAT,1,3)),0,3,1,3))
        assertFalse(legal(pos(r(Animal.RAT,1,3),b(Animal.RAT,0,3)),1,3,0,3))
        assertFalse(legal(pos(r(Animal.RAT,1,3),b(Animal.ELEPHANT,1,2)),1,3,1,2))
        assertTrue(legal(pos(r(Animal.RAT,1,3),b(Animal.RAT,2,3)),1,3,2,3))
    }
    @Test fun bothJumpersBothAxesAndCapture() {
        for (a in listOf(Animal.LION,Animal.TIGER)) {
            assertTrue(legal(pos(r(a,0,4),b(Animal.CAT,3,4)),0,4,3,4))
            assertTrue(legal(pos(r(a,1,2),b(Animal.DOG,1,6)),1,2,1,6))
            assertFalse(legal(pos(r(a,1,2),b(Animal.ELEPHANT,1,6)),1,2,1,6))
        }
    }
    @Test fun anyRatInJumpPathBlocksButNotOutsidePath() {
        for (side in Side.entries) for (y in 3..5) {
            val p = pos(r(Animal.TIGER,1,2),Triple(side,Animal.RAT,Square(1,y)),b(Animal.CAT,6,0))
            assertFalse(legal(p,1,2,1,6))
        }
        assertTrue(legal(pos(r(Animal.LION,1,2),b(Animal.RAT,2,4)),1,2,1,6))
    }
    @Test fun enemyTrapWeakensEveryRankIncludingElephantRatException() {
        for (a in Animal.entries) for (target in Animal.entries) {
            assertTrue(legal(pos(r(a,1,8),b(target,2,8)),1,8,2,8))
        }
    }
    @Test fun ownTrapDoesNotWeakenAndTrapExitRestoresRank() {
        assertFalse(legal(pos(r(Animal.CAT,1,0),b(Animal.ELEPHANT,2,0)),1,0,2,0))
        val trapped = pos(r(Animal.ELEPHANT,2,0),b(Animal.CAT,1,0),b(Animal.DOG,6,6))
        assertFalse(legal(trapped,2,0,1,0))
        val escaped = Jungle.play(trapped,Move(Square(2,0),Square(2,1))).copy(turn=Side.RED)
        val adjacent = escaped.board.toMutableList().also { it[1] = null; it[Square(1,1).index] = Piece(Side.BLUE,Animal.CAT) }
        assertTrue(legal(escaped.copy(board=adjacent),2,1,1,1))
    }
    @Test fun ownDenForbiddenEnemyDenWins() {
        assertFalse(legal(pos(r(Animal.CAT,3,7),b(Animal.CAT,0,0)),3,7,3,8))
        val next = Jungle.play(pos(r(Animal.RAT,3,1),b(Animal.ELEPHANT,6,0)),Move(Square(3,1),Square(3,0)))
        assertEquals(Side.RED,next.outcome!!.winner)
        assertTrue(Jungle.legalMoves(next).isEmpty())
    }
    @Test fun lastCaptureWinsAndImmutableBoard() {
        val p = pos(r(Animal.DOG,0,3),b(Animal.CAT,0,4))
        val next = Jungle.play(p,Move(Square(0,3),Square(0,4)))
        assertEquals(Side.RED,next.outcome!!.winner)
        assertEquals(Animal.CAT,p[Square(0,4)]!!.animal)
        assertEquals(0,next.quiet)
    }
    @Test fun immobilizationWins() {
        val p = pos(b(Animal.CAT,0,0),r(Animal.ELEPHANT,0,1),r(Animal.DOG,1,1),r(Animal.RAT,6,7))
        val next = Jungle.play(p,Move(Square(1,1),Square(1,0)))
        assertEquals("对方无合法走法",next.outcome!!.reason)
    }
    @Test fun quietMoveAndRepetitionDraws() {
        val p = pos(r(Animal.CAT,0,6),b(Animal.CAT,6,2))
        val m = Move(Square(0,6),Square(0,5))
        assertNull(Jungle.play(p.copy(quiet=99),m).outcome!!.winner)
        var repeated = p
        repeat(2) {
            for (move in listOf(m,Move(Square(6,2),Square(6,1)),Move(Square(0,5),Square(0,6)),Move(Square(6,1),Square(6,2))))
                repeated = Jungle.play(repeated,move)
        }
        // Start position must be registered to count the first occurrence.
        var initial = p.copy(repetitions=mapOf(p.key() to 1))
        repeat(2) {
            for (move in listOf(m,Move(Square(6,2),Square(6,1)),Move(Square(0,5),Square(0,6)),Move(Square(6,1),Square(6,2))))
                initial = Jungle.play(initial,move)
        }
        assertEquals("同一局面出现三次",initial.outcome!!.reason)
    }
    @Test(expected=IllegalArgumentException::class) fun illegalMoveRejected() {
        Jungle.play(Position.initial(),Move(Square(0,6),Square(3,3)))
    }
    @Test fun easyUsesReplySearchAndIsDeterministic() {
        val p = Position.initial()
        val ai = JungleAI()
        val choices = (1..4).map { ai.choose(p,Difficulty.EASY) }
        assertTrue(choices.all { it.move in Jungle.legalMoves(p) && it.depth == 2 && it.nodes > 24 })
        assertEquals(1, choices.map { it.move }.toSet().size)
    }
    @Test fun minimaxTakesDenAndDefendsImmediateThreat() {
        val win = pos(b(Animal.RAT,3,7),r(Animal.ELEPHANT,0,6),turn=Side.BLUE)
        val defend = pos(r(Animal.RAT,3,1),b(Animal.CAT,2,1),b(Animal.ELEPHANT,6,2),turn=Side.BLUE)
        for (difficulty in Difficulty.entries) {
            assertEquals(Move(Square(3,7),Square(3,8)),JungleAI().choose(win,difficulty,0).move)
            assertEquals(Move(Square(2,1),Square(3,1)),JungleAI().choose(defend,difficulty,0).move)
        }
    }
    @Test fun randomGamesMaintainInvariants() {
        val random = Random(31)
        repeat(12) {
            var p = Position.initial()
            repeat(240) {
                if (p.outcome == null) {
                    val moves = Jungle.legalMoves(p)
                    assertTrue(moves.isNotEmpty())
                    val oldCount = p.board.count { it != null }
                    p = Jungle.play(p,moves.random(random))
                    assertTrue(p.board.count { it != null } in (oldCount-1)..oldCount)
                    assertEquals(p.board.filterNotNull().size,p.board.filterNotNull().map { it.id }.toSet().size)
                    p.board.forEachIndexed { i,piece ->
                        if (piece != null && Jungle.water(Square(i%7,i/7))) assertEquals(Animal.RAT,piece.animal)
                    }
                }
            }
        }
    }
}
