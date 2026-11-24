package com.pico.junglechess.game

import org.junit.Assert.*
import org.junit.Test

class MatchResultTest {
    @Test fun redBlueAndDrawHaveDistinctResultTitlesAndKeepReason() {
        val red=Outcome(Side.RED,"占领对方兽穴")
        val blue=Outcome(Side.BLUE,"对方无合法走法")
        val draw=Outcome(null,"同一局面出现三次")
        assertEquals("你赢了！",red.title)
        assertEquals("AI 获胜",blue.title)
        assertEquals("本局和棋",draw.title)
        assertEquals("占领对方兽穴",red.reason)
        assertEquals("对方无合法走法",blue.reason)
        assertEquals("同一局面出现三次",draw.reason)
    }
}
