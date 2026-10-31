package vancouvermonth.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Room
import vancouvermonth.core.Zone
import vancouvermonth.text.date
import vancouvermonth.text.money
import vancouvermonth.text.percent

class TerminalGameTest {
    private fun playWith(seed: Int, vararg answers: String): Pair<Boolean, String> {
        val input = answers.iterator()
        val output = StringBuilder()
        // Each answer is echoed after its prompt, the way a terminal shows what the player typed.
        val again = TerminalGame(
            input = { if (input.hasNext()) input.next().also { output.append(it + "\n") } else null },
            output = { output.append(it) },
        ).play(seed)
        return again to output.toString()
    }

    @Test
    fun `a round opens with the title and shows the real total after the guess`() {
        val (again, screen) = playWith(4821, "1", "1200", "n")
        val total = FirstMinute(4821).rooms[0].totalMonthlyCost
        assertFalse(again)
        assertEquals("BNI Tech", screen.lines().first { it.isNotBlank() })
        assertTrue("판 번호 4821" in screen)
        assertTrue("한 달 총비용  ${money(total)}" in screen)
    }

    @Test
    fun `seed 1011 plays exactly as the README shows`() {
        val readme = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "README.md") }
            .first { it.exists() }
            .readText()
        val sample = readme.substringAfter("```\nBNI Tech").substringBefore("```")
        val (_, screen) = playWith(1011, "2", "1300", "2")
        val expectedLines = ("BNI Tech" + sample).lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val shownLines = screen.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        // The README stops after the first day; the screen goes on to the next day's question.
        assertEquals(expectedLines, shownLines.take(expectedLines.size))
    }

    @Test
    fun `a whole month of rest ends on the 30th and offers another run`() {
        val answers = arrayOf("3", "1165") + Array(29) { "5" } + arrayOf("y")
        val (again, screen) = playWith(1011, *answers)
        assertTrue("[9월 30일] 남은 날 1일" in screen)
        assertTrue("9월이 끝났어요." in screen)
        assertTrue(again)
    }

    @Test
    fun `an action the player cannot take is explained and asked again`() {
        val (_, screen) = playWith(1011, "3", "1165", "1", "1", "1", "1", "1", "1")
        assertTrue("체력 0/5" in screen)
        assertTrue("지금은 못 해요. 체력이 없어요. 쉬어야 해요." in screen)
    }

    @Test
    fun `dates, percentages and Korean columns`() {
        assertEquals("9월 2일", date(2))
        assertEquals("10월 1일", date(31))
        assertEquals("27%", percent(0.27))
        assertEquals("50%", percent(0.05 + 0.09 * 5))
        assertEquals("29%", percent(0.05 + 0.08 * 3))
        assertEquals("쉬기          ", padToColumns("쉬기", 14))
        assertEquals("지원서 넣기   ", padToColumns("지원서 넣기", 14))
    }

    @Test
    fun `the first screen says what to do and shows the prices`() {
        val (_, screen) = playWith(4821)
        assertTrue("할 일: 한 달 총비용이 가장 싼 방을 고르세요." in screen)
        assertTrue("1구역 \$105 · 2구역 \$145 · 3구역 \$195" in screen)
        assertTrue("별도면 한 달 \$120~\$190" in screen)
    }

    @Test
    fun `the reveal shows the points and the score`() {
        val game = FirstMinute(4821)
        val cheapest = game.cheapestRoomIndex
        val (_, screen) = playWith(4821, "${cheapest + 1}", "${game.lowestTotal}", "n")
        assertTrue("총비용이 가장 싼 방을 골랐어요.   +100" in screen)
        assertTrue("짐작이 정확해요.   +100" in screen)
        assertTrue("방 고르기 점수 200 / 200" in screen)
    }

    @Test
    fun `every room tied for the lowest total is named`() {
        val rooms = listOf(
            Room(rent = 1_140, zone = Zone.ONE, utilitiesIncluded = true, utilities = 0),
            Room(rent = 1_050, zone = Zone.TWO, utilitiesIncluded = false, utilities = 150),
            Room(rent = 930, zone = Zone.THREE, utilitiesIncluded = false, utilities = 120),
        )
        val answers = listOf("2", "1300", "n").iterator()
        val screen = StringBuilder()
        TerminalGame(
            input = { if (answers.hasNext()) answers.next() else null },
            output = { screen.append(it) },
        ).play(FirstMinute(seed = 0, rooms = rooms))
        assertTrue("총비용이 가장 싼 방은 1번, 3번이었어요 (\$1,245).   +0" in screen)
    }

    @Test
    fun `wrong answers are asked again`() {
        val (_, screen) = playWith(4821, "9", "two", "2", "abc", "1,150", "n")
        assertTrue("1부터 3 사이의 번호로 골라 주세요." in screen)
        assertTrue("숫자로 적어 주세요." in screen)
        assertTrue("한 달 총비용  " in screen)
    }

    @Test
    fun `the game ends quietly when the input ends`() {
        val (again, screen) = playWith(4821)
        assertFalse(again)
        assertTrue("어느 방?" in screen)
    }

    @Test
    fun `room choices count from one`() {
        assertEquals(0, parseRoomChoice("1", 3))
        assertEquals(2, parseRoomChoice(" 3 ", 3))
        assertNull(parseRoomChoice("0", 3))
        assertNull(parseRoomChoice("4", 3))
        assertNull(parseRoomChoice("x", 3))
    }

    @Test
    fun `a seed can be given in either form`() {
        assertEquals(4821, parseSeed(arrayOf("--seed", "4821")))
        assertEquals(4821, parseSeed(arrayOf("--seed=4821")))
        assertNull(parseSeed(arrayOf()))
    }
}
