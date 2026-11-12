package com.pico.junglechess.game

import kotlin.random.Random

enum class Difficulty(val label: String) { EASY("简单"), HARD("困难") }
data class SearchResult(val move: Move?, val depth: Int, val nodes: Int)

/** Iterative deepening minimax with alpha-beta; run on Dispatchers.Default. */
class JungleAI(private val random: Random = Random.Default) {
    private var nodes = 0
    private var deadline = 0L
    private var cancel: () -> Boolean = { false }
    private class SearchStopped : RuntimeException()
    private val values = intArrayOf(160, 210, 290, 360, 430, 620, 730, 830)

    fun choose(p: Position, difficulty: Difficulty, budgetMs: Long = 1100,
               cancelled: () -> Boolean = { false }): SearchResult {
        val moves = Jungle.legalMoves(p)
        if (moves.isEmpty()) return SearchResult(null, 0, 0)
        if (difficulty == Difficulty.EASY) return SearchResult(moves.random(random), 0, 1)
        nodes = 0
        deadline = System.nanoTime() + budgetMs * 1_000_000
        cancel = cancelled
        var best = order(p, moves).first()
        var completed = 0
        for (depth in 1..6) {
            try {
                var value = -2_000_000
                var iterationBest = best
                var alpha = -2_000_000
                val ordered = listOf(best) + order(p, moves).filter { it != best }
                for (move in ordered) {
                    checkTime()
                    val score = -search(Jungle.advance(p, move), depth - 1, -2_000_000, -alpha)
                    if (score > value) { value = score; iterationBest = move }
                    alpha = maxOf(alpha, score)
                }
                best = iterationBest
                completed = depth
                if (value > 900_000) break
            } catch (_: SearchStopped) { break }
        }
        return SearchResult(best, completed, nodes)
    }

    private fun checkTime() {
        if (cancel() || System.nanoTime() >= deadline) throw SearchStopped()
    }
    private fun search(p: Position, depth: Int, lo: Int, hi: Int): Int {
        nodes++
        if (nodes % 64 == 0) checkTime()
        p.outcome?.let {
            return if (it.winner == null) 0 else if (it.winner == p.turn) 1_000_000 + depth else -1_000_000 - depth
        }
        if (depth == 0) return evaluate(p)
        var alpha = lo
        var best = -2_000_000
        for (move in order(p, Jungle.legalMoves(p))) {
            val score = -search(Jungle.advance(p, move), depth - 1, -hi, -alpha)
            best = maxOf(best, score)
            alpha = maxOf(alpha, score)
            if (alpha >= hi) break
        }
        return best
    }

    private fun order(p: Position, moves: List<Move>) = moves.sortedByDescending { m ->
        (if (Jungle.den(m.to) == p.turn.other()) 1_000_000 else 0) +
            (p[m.to]?.let { values[it.animal.ordinal] * 10 } ?: 0) -
            Jungle.distance(m.to, Square(3, if (p.turn == Side.RED) 0 else 8)) * 5
    }

    private fun evaluate(p: Position): Int {
        var result = 0
        val myMoves = Jungle.legalMoves(p, p.turn)
        val enemyMoves = Jungle.legalMoves(p, p.turn.other())
        val threatened = enemyMoves.map { it.to }.toSet()
        val pressure = myMoves.map { it.to }.toSet()
        p.board.forEachIndexed { i, piece ->
            if (piece == null) return@forEachIndexed
            val s = Square(i % 7, i / 7)
            val mine = piece.side == p.turn
            val den = Square(3, if (piece.side == Side.RED) 0 else 8)
            val d = Jungle.distance(s, den)
            var score = values[piece.animal.ordinal] + (11 - d) * 11
            if (d == 1) score += 190
            if (d == 2) score += 60
            if (Jungle.trap(s) == piece.side.other()) score -= values[piece.animal.ordinal] * 3 / 4
            if (s in if (mine) threatened else pressure) score -= values[piece.animal.ordinal] / 3
            if (piece.animal == Animal.RAT && Jungle.water(s)) score += 25
            result += if (mine) score else -score
        }
        return result + (myMoves.size - enemyMoves.size) * 4
    }
}
