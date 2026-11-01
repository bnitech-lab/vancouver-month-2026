package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `how to play says how to walk, dash, look about, talk, ride and find the way, in six short lines`() {
        assertEquals(6, Guide.LINES.size)
        assertTrue("땅을 누르면" in Guide.LINES[0] && "두 번 누르면" in Guide.LINES[0])
        assertTrue("끌면" in Guide.LINES[1] && "두 손가락" in Guide.LINES[1])
        assertTrue("말을 걸어요" in Guide.LINES[2] && "하루가 끝나요" in Guide.LINES[2])
        for (ride in listOf("스카이트레인", "버스", "수상버스")) assertTrue(ride in Guide.LINES[3], ride)
        for (ride in listOf("자전거", "택시")) assertTrue(ride in Guide.LINES[4], ride)
        assertTrue("작은 지도" in Guide.LINES[5])
        for (line in Guide.LINES) {
            assertTrue(line.length <= 40, "Too long for a phone: $line")
            assertFalse('\u2014' in line, "Em dash in: $line")
        }
    }

    @Test
    fun `the map's credit is the City's open data licence statement, word for word`() {
        val statement = "Contains information licensed under the Open Government Licence \u2013 Vancouver."
        assertEquals(statement, Guide.MAP_CREDIT)
        // The licence's name, which links to the licence, is within the statement as it stands.
        assertEquals("Open Government Licence \u2013 Vancouver", Guide.MAP_LICENCE)
        assertTrue(Guide.MAP_LICENCE in Guide.MAP_CREDIT)
        assertEquals("https://opendata.vancouver.ca/pages/licence/", Guide.MAP_LICENCE_URL)
    }

    @Test
    fun `the sound switch says what a tap does`() {
        assertEquals("소리 켜기", soundLabel(on = false))
        assertEquals("소리 끄기", soundLabel(on = true))
    }

    @Test
    fun `the small map tells a screen reader which place the character is near`() {
        assertEquals("지도: 내 방 근처", minimapLabel("내 방"))
        assertEquals("지도: 회사 근처", minimapLabel("회사"))
    }
}
