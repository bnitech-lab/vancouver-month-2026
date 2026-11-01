package vancouvermonth.terminal

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Room
import vancouvermonth.core.TodaysRun
import vancouvermonth.core.Zone
import vancouvermonth.text.arrivalLines
import vancouvermonth.text.date
import vancouvermonth.text.goalLine
import vancouvermonth.text.money
import vancouvermonth.text.percent
import vancouvermonth.text.whyTotalLines

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
        // The README stops at the first day's second question, where the input ends.
        assertEquals(expectedLines, shownLines.take(expectedLines.size))
    }

    @Test
    fun `a whole month of rest ends on the 30th and offers another run`() {
        val answers = arrayOf("3", "1165") + Array(29) { "5" } + arrayOf("y")
        val (again, screen) = playWith(1011, *answers)
        assertTrue("[9월 30일 아침 8시] 남은 날 1일" in screen)
        assertTrue("9월이 끝났어요." in screen)
        assertTrue(again)
    }

    @Test
    fun `today's run is named on the first screen and ends with a result line to copy`() {
        val today = TodaysRun(2026, 10, 30)
        val game = FirstMinute(today.seed)
        // The cheapest room and an exact guess, then a month of rest, which never covers October.
        val answers = (listOf("${game.cheapestRoomIndex + 1}", "${game.lowestTotal}") + List(29) { "5" } + "n").iterator()
        val screen = StringBuilder()
        val again = TerminalGame(
            input = { if (answers.hasNext()) answers.next() else null },
            output = { screen.append(it) },
        ).play(today)
        val lines = screen.lines()
        assertFalse(again)
        assertTrue("판 번호 ${today.seed}" in screen)
        assertTrue("10월 30일 오늘의 판" in lines)
        val label = lines.indexOf("결과 한 줄 (복사해서 붙여 넣으세요)")
        assertTrue(label >= 0, "no result label in: $screen")
        assertEquals("밴쿠버 한 달 · 10월 30일 오늘의 판 · 짐작 보상 +\$100 · 10월 총비용 모자람", lines[label + 1])
        assertTrue("  짐작 보상 +\$100 · 영어 1/10 · 코딩 3/10" in lines, screen.toString())
    }

    @Test
    fun `a run from a seed has no result line`() {
        val answers = arrayOf("3", "1165") + Array(29) { "5" } + arrayOf("n")
        val (_, screen) = playWith(1011, *answers)
        assertTrue("9월이 끝났어요." in screen)
        assertFalse("오늘의 판" in screen)
        assertFalse("결과 한 줄" in screen)
    }

    @Test
    fun `--today may name a date`() {
        assertEquals("", parseToday(arrayOf("--today")))
        assertEquals("2026-10-29", parseToday(arrayOf("--today=2026-10-29")))
        assertEquals("2026-10-29", parseToday(arrayOf("--today", "2026-10-29")))
        assertEquals("", parseToday(arrayOf("--today", "--seed", "1011")))
        assertNull(parseToday(arrayOf("--seed", "1011")))
        assertNull(parseToday(arrayOf()))
        // A bare --today reads this computer's date in the form LocalDate writes it.
        assertEquals(TodaysRun(2026, 10, 30), TodaysRun.parse(LocalDate.of(2026, 10, 30).toString()))
        assertEquals(TodaysRun(2027, 1, 1), TodaysRun.parse(LocalDate.of(2027, 1, 1).toString()))
    }

    @Test
    fun `an action the player cannot take is explained and asked again`() {
        val (_, screen) = playWith(1011, "3", "1165", "1", "1", "2", "4", "4", "1")
        // A shift leaves two of five: another shift is short, a study fits, and then it is night.
        assertTrue("[9월 2일 오후 5시] 남은 날 29일" in screen, screen)
        assertTrue("체력 2/5" in screen)
        assertTrue("지금은 못 해요. 체력이 모자라요." in screen)
        assertTrue("[9월 2일 밤 11시] 남은 날 29일" in screen, screen)
        assertTrue("  밤이에요. 체력을 다 썼어요. 내 방에 가서 자요." in screen.lines(), screen)
        assertTrue("지금은 못 해요. 체력이 없어요. 내 방에 가서 자요." in screen)
    }

    @Test
    fun `a day holds several actions, the clock moves with the stamina, and sleep starts the next morning`() {
        val (_, screen) = playWith(1011, "3", "1165", "4", "2", "3", "5")
        val headers = screen.lines().filter { it.startsWith("[9월") }
        assertEquals(
            listOf(
                "[9월 2일 아침 8시] 남은 날 29일",
                "[9월 2일 오전 11시] 남은 날 29일",
                "[9월 2일 오후 5시] 남은 날 29일",
                "[9월 2일 밤 11시] 남은 날 29일",
                "[9월 3일 아침 8시] 남은 날 28일",
            ),
            headers,
        )
        val lines = screen.lines()
        for (line in listOf(
            "  연락이 왔어요! 1차 영어 면접이 9월 6일에 잡혔어요.",
            "  4) 지원서 넣기   면접이 잡힐 확률 27%, 체력 -1   (면접이 잡혀 있어요.)",
            "  영어 공부를 했어요. 레벨업까지 3번",
            "  코딩 연습을 했어요. 레벨업까지 3번",
            "  밤이에요. 체력을 다 썼어요. 내 방에 가서 자요.",
            "  5) 자기          다음 날 아침, 체력 5",
            "  내 방에서 푹 잤어요. 체력 5/5",
        )) {
            assertTrue(line in lines, "Missing: $line\n$screen")
        }
    }

    @Test
    fun `dates, percentages and Korean columns`() {
        assertEquals("9월 2일", date(2))
        assertEquals("10월 1일", date(31))
        assertEquals("27%", percent(0.27))
        assertEquals("50%", percent(0.5))
        assertEquals("12%", percent(0.12))
        assertEquals("19%", percent(0.185))
        assertEquals("자기          ", padToColumns("자기", 14))
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
    fun `the first screen tells the story, the goal and why the room matters before the rooms`() {
        val (_, screen) = playWith(4821)
        val lines = screen.lines()
        val story = arrivalLines(FirstMinute.STARTING_BALANCE)
        val why = whyTotalLines().map { "       $it" }
        val at = (story + why).map { lines.indexOf(it) }
        assertTrue(at.all { it >= 0 }, "missing a line in: $screen")
        assertEquals(at.sorted(), at)
        assertTrue(goalLine() in lines, screen)
        assertTrue(lines.indexOf("할 일: 한 달 총비용이 가장 싼 방을 고르세요.") in at.first()..at.last())
        assertTrue(at.last() < lines.indexOfFirst { it.startsWith("  1) ") })
        // The story says the money and the date, so the first screen has no balance line of its own.
        assertFalse(lines.any { it.startsWith("잔고") }, screen)
        // No room's total, and no word that names the cheapest room, is handed over before the guess.
        for (room in FirstMinute(4821).rooms) assertFalse(money(room.totalMonthlyCost) in screen)
        for (word in listOf("가장 싼 방은", "번이었어요", "가장 싼 방보다", "가장 싼 방이라")) assertFalse(word in screen, word)
    }

    @Test
    fun `the reveal ties the pick to the month, against the cheapest room`() {
        val game = FirstMinute(1011)
        val (_, dear) = playWith(1011, "2", "1300", "n")
        assertTrue("  가장 싼 방보다 한 달에 \$180 더 내요. 10월 1일에도 그만큼 더 나가요." in dear.lines())
        val (_, cheap) = playWith(1011, "${game.cheapestRoomIndex + 1}", "${game.lowestTotal}", "n")
        assertTrue("  가장 싼 방이라 매달 나가는 돈이 가장 적어요." in cheap.lines())
    }

    @Test
    fun `an exact guess wins $100 as the month starts, and the reveal has no score`() {
        val game = FirstMinute(4821)
        val cheapest = game.cheapestRoomIndex
        val (_, screen) = playWith(4821, "${cheapest + 1}", "${game.lowestTotal}", "n")
        val lines = screen.lines()
        val start = FirstMinute.STARTING_BALANCE - game.lowestTotal + 100
        assertTrue("  총비용이 가장 싼 방을 골랐어요." in lines, screen)
        assertTrue("  짐작이 정확해요." in lines, screen)
        assertTrue("  짐작 보상 +\$100, 잔고 ${money(start)}" in lines, screen)
        assertTrue("  딱 맞혀서 \$100을 다 받아요." in lines, screen)
        assertTrue("  잔고 ${money(start)} · 10월 1일에 낼 총비용 ${money(game.lowestTotal)}" in lines, screen)
        assertFalse("점수" in screen, screen)
        // The money line comes after the reveal and before the month's start.
        val money = lines.indexOf("  짐작 보상 +\$100, 잔고 ${money(start)}")
        assertTrue(lines.indexOf("  짐작이 정확해요.") < money && money < lines.indexOf("9월 한 달이 시작돼요."))
    }

    @Test
    fun `a guess far off loses money as the month starts, and the month plays on from there`() {
        val game = FirstMinute(1011)
        val total = game.lowestTotal
        val (_, screen) = playWith(1011, "${game.cheapestRoomIndex + 1}", "${total + 250}", "5")
        val start = FirstMinute.STARTING_BALANCE - total - 150
        val lines = screen.lines()
        assertTrue("  내 짐작과 차이 \$250" in lines, screen)
        assertTrue("  짐작 패널티 -\$150, 잔고 ${money(start)}" in lines, screen)
        assertTrue("  \$100보다 더 틀린 만큼 잃어요." in lines, screen)
        assertTrue("  생활비 -\$40, 잔고 ${money(start - 40)}" in lines, screen)
        val (_, worst) = playWith(1011, "${game.cheapestRoomIndex + 1}", "0", "n")
        assertTrue("  짐작 패널티 -\$300, 잔고 ${money(FirstMinute.STARTING_BALANCE - total - 300)}" in worst.lines())
        assertTrue("  많이 틀렸지만 잃는 돈은 \$300까지예요." in worst.lines())
    }

    @Test
    fun `the first screen says how the guess pays before asking for it`() {
        val (_, screen) = playWith(4821)
        val lines = screen.lines()
        val rule = lines.indexOf("             맞힐수록 최대 \$100을 더 받고, \$100보다 더 틀리면 그만큼 잃어요(최대 \$300).")
        assertTrue(rule > lines.indexOfFirst { it.startsWith("  3) ") }, screen)
        assertEquals("총비용 짐작: 방을 고른 뒤 그 방의 한 달 총비용을 짐작해 적어요.", lines[rule - 1])
        assertTrue(rule < lines.indexOfFirst { it.startsWith("어느 방?") }, screen)
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
        assertTrue("  총비용이 가장 싼 방은 1번, 3번이었어요 (\$1,245)." in screen.lines())
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
