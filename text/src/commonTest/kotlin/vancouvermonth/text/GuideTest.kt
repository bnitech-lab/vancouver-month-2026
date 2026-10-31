package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import vancouvermonth.core.Room
import vancouvermonth.core.Zone

class GuideTest {
    @Test
    fun `the guide's goal is the month's own goal line, with no new numbers`() {
        val room = Room(rent = 1_020, zone = Zone.TWO, utilitiesIncluded = true, utilities = 0)
        assertEquals("목표: 9월 30일까지 취업 제안 받기", goalLine())
        assertEquals(goalLine(), monthStartLines(room).first())
    }

    @Test
    fun `the guide says how to walk, dash, look about and spend a day, in four short lines`() {
        assertEquals(4, Guide.LINES.size)
        assertTrue("땅을 누르면" in Guide.LINES[0])
        assertTrue("두 번 누르면" in Guide.LINES[1])
        assertTrue("끌면" in Guide.LINES[2] && "오므리고" in Guide.LINES[2])
        assertTrue("말을 걸어요" in Guide.LINES[3] && "하루가 끝나요" in Guide.LINES[3])
        for (line in Guide.LINES) assertTrue(line.length <= 40, "Too long for a phone: $line")
    }

    @Test
    fun `the sound switch says what a tap does`() {
        assertEquals("소리 켜기", soundLabel(on = false))
        assertEquals("소리 끄기", soundLabel(on = true))
    }
}
