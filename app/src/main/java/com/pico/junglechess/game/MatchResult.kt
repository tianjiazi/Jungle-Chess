package com.pico.junglechess.game

val Outcome.title: String
    get() = when (winner) {
        Side.RED -> "你赢了！"
        Side.BLUE -> "AI 获胜"
        null -> "本局和棋"
    }
