package com.pico.junglechess.game

import kotlin.math.abs

enum class Side { RED, BLUE; fun other() = if (this == RED) BLUE else RED }
enum class Animal(val rank: Int, val label: String) {
    RAT(1, "鼠"), CAT(2, "猫"), DOG(3, "狗"), WOLF(4, "狼"),
    LEOPARD(5, "豹"), TIGER(6, "虎"), LION(7, "狮"), ELEPHANT(8, "象")
}
data class Square(val x: Int, val y: Int) {
    val valid get() = x in 0..6 && y in 0..8
    val index get() = y * 7 + x
    val label get() = "${('A'.code + x).toChar()}${9 - y}"
}
data class Piece(val side: Side, val animal: Animal) {
    val id get() = side.ordinal * 8 + animal.ordinal
}
data class Move(val from: Square, val to: Square)
data class Outcome(val winner: Side?, val reason: String)
data class Position(
    val board: List<Piece?>,
    val turn: Side = Side.RED,
    val quiet: Int = 0,
    val ply: Int = 0,
    val outcome: Outcome? = null,
    val repetitions: Map<String, Int> = emptyMap()
) {
    operator fun get(s: Square): Piece? = if (s.valid) board[s.index] else null
    fun key(): String = buildString {
        append(turn.ordinal)
        board.forEach { append(if (it == null) '.' else ('A'.code + it.id).toChar()) }
    }
    companion object {
        fun initial(): Position {
            val board = MutableList<Piece?>(63) { null }
            // Blue: lion A9, tiger G9, dog B8, cat F8, rat A7,
            // leopard C7, wolf E7, elephant G7. Red is a 180° rotation.
            val setup = listOf(
                Triple(0, 0, Animal.LION), Triple(6, 0, Animal.TIGER),
                Triple(1, 1, Animal.DOG), Triple(5, 1, Animal.CAT),
                Triple(0, 2, Animal.RAT), Triple(2, 2, Animal.LEOPARD),
                Triple(4, 2, Animal.WOLF), Triple(6, 2, Animal.ELEPHANT)
            )
            setup.forEach { (x, y, a) ->
                board[y * 7 + x] = Piece(Side.BLUE, a)
                board[(8 - y) * 7 + 6 - x] = Piece(Side.RED, a)
            }
            val p = Position(board)
            return p.copy(repetitions = mapOf(p.key() to 1))
        }
    }
}

object Jungle {
    fun water(s: Square) = s.y in 3..5 && (s.x in 1..2 || s.x in 4..5)
    fun den(s: Square): Side? = when (s) {
        Square(3, 0) -> Side.BLUE
        Square(3, 8) -> Side.RED
        else -> null
    }
    fun trap(s: Square): Side? = when (s) {
        Square(2, 0), Square(4, 0), Square(3, 1) -> Side.BLUE
        Square(2, 8), Square(4, 8), Square(3, 7) -> Side.RED
        else -> null
    }
    private val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    fun canCapture(p: Position, from: Square, to: Square): Boolean {
        val attacker = p[from] ?: return false
        val target = p[to] ?: return true
        if (attacker.side == target.side || water(from) != water(to)) return false
        // Enemy traps reduce the current occupant to rank zero.
        if (trap(to) == attacker.side) return true
        if (trap(from) == target.side) return false
        if (attacker.animal == Animal.ELEPHANT && target.animal == Animal.RAT) return false
        if (attacker.animal == Animal.RAT && target.animal == Animal.ELEPHANT) return true
        return attacker.animal.rank >= target.animal.rank
    }

    fun legalMoves(p: Position, side: Side = p.turn): List<Move> {
        if (p.outcome != null) return emptyList()
        val moves = ArrayList<Move>(36)
        p.board.forEachIndexed { i, piece ->
            if (piece?.side != side) return@forEachIndexed
            val from = Square(i % 7, i / 7)
            for ((dx, dy) in directions) {
                var to = Square(from.x + dx, from.y + dy)
                if (!to.valid) continue
                if (water(to) && piece.animal != Animal.RAT) {
                    if (piece.animal != Animal.LION && piece.animal != Animal.TIGER) continue
                    var blocked = false
                    while (to.valid && water(to)) {
                        if (p[to] != null) blocked = true
                        to = Square(to.x + dx, to.y + dy)
                    }
                    if (blocked || !to.valid) continue
                }
                if (den(to) == side || p[to]?.side == side) continue
                if (canCapture(p, from, to)) moves += Move(from, to)
            }
        }
        return moves
    }

    fun play(p: Position, move: Move): Position {
        require(move in legalMoves(p)) { "非法走子：${move.from.label} → ${move.to.label}" }
        return advance(p, move)
    }

    /** Only callers that already enumerated legal moves may use this. */
    internal fun advance(p: Position, move: Move): Position {
        val piece = requireNotNull(p[move.from])
        val captured = p[move.to] != null
        val board = p.board.toMutableList()
        board[move.to.index] = piece
        board[move.from.index] = null
        var next = Position(board, p.turn.other(), if (captured) 0 else p.quiet + 1, p.ply + 1)
        val key = next.key()
        val visits = (p.repetitions[key] ?: 0) + 1
        next = next.copy(repetitions = p.repetitions + (key to visits))
        val outcome = when {
            den(move.to) == piece.side.other() -> Outcome(piece.side, "占领敌方兽穴")
            board.none { it?.side == next.turn } -> Outcome(piece.side, "吃掉全部敌方棋子")
            legalMoves(next).isEmpty() -> Outcome(piece.side, "对方无合法走法")
            visits >= 3 -> Outcome(null, "同一局面出现三次")
            next.quiet >= 100 -> Outcome(null, "连续100步未吃子")
            else -> null
        }
        return next.copy(outcome = outcome)
    }

    fun distance(a: Square, b: Square) = abs(a.x - b.x) + abs(a.y - b.y)
}
