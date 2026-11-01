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
    fun `how to play says how to walk, dash, look about, talk, spend the day, ride, find the way and the places, in eight lines`() {
        assertEquals(8, Guide.LINES.size)
        assertTrue("땅을 누르면" in Guide.LINES[0] && "두 번 누르면" in Guide.LINES[0])
        assertTrue("끌면" in Guide.LINES[1] && "벌리면" in Guide.LINES[1] && "모으면" in Guide.LINES[1])
        // A tap walks there, and a job waits for the character to arrive.
        assertTrue("걸어가서 말을 걸어요" in Guide.LINES[2] && "도착해서" in Guide.LINES[2])
        assertTrue("체력만큼 시간이 흘러요" in Guide.LINES[3] && "밤" in Guide.LINES[3] && "내 방에서 자면" in Guide.LINES[3])
        for (ride in listOf("스카이트레인", "버스", "수상버스")) assertTrue(ride in Guide.LINES[4], ride)
        for (ride in listOf("자전거", "택시")) assertTrue(ride in Guide.LINES[5], ride)
        // The shop sells; shared bikes are rented on the street.
        assertTrue("길에서 빌리고" in Guide.LINES[5])
        assertTrue("작은 지도" in Guide.LINES[6] && "지도 전체" in Guide.LINES[6])
        // The same action at every place of its kind, round the map.
        for (kind in listOf("카페", "도서관", "코워킹", "회사")) assertTrue(kind in Guide.LINES[7], kind)
        assertTrue("어디서나 같은 일" in Guide.LINES[7])
        // A day is no longer one action: nothing says a choice ends it.
        assertTrue(Guide.LINES.none { "하루가 끝나요" in it || "하나를 고르면" in it })
        for (line in Guide.LINES) {
            assertTrue(line.length <= 40, "More than 40 characters: $line")
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
    fun `the Burnaby map credit is its licence's own default statement, word for word, naming British Columbia`() {
        // The Open Government Licence - Burnaby keeps the provincial licence's default statement: it says British
        // Columbia, not Burnaby, and that is what the licence asks for (checked on its page on 2026-10-31).
        val statement = "Contains information licensed under the Open Government Licence \u2013 British Columbia."
        assertEquals(statement, Guide.BURNABY_MAP_CREDIT)
        assertEquals("City of Burnaby", Guide.BURNABY_MAP_SOURCE)
        assertEquals("Open Government Licence \u2013 Burnaby", Guide.BURNABY_MAP_LICENCE)
        assertEquals("https://data.burnaby.ca/pages/open-government-licence", Guide.BURNABY_MAP_LICENCE_URL)
        // On the page: the statement, then the source and the licence's name, the name once, to link it.
        assertEquals("$statement (City of Burnaby, Open Government Licence \u2013 Burnaby)", Guide.BURNABY_MAP_LINE)
        assertEquals(1, Guide.BURNABY_MAP_LINE.split(Guide.BURNABY_MAP_LICENCE).size - 1)
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
        assertEquals("지도: 큰 공원역 쪽", minimapLabel("큰 공원역", near = false))
    }
}
