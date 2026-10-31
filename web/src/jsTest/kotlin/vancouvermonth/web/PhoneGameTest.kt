package vancouvermonth.web

import kotlin.js.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.TodaysRun

/** A town that only notes where the character was sent, so a test needs no WebGL. */
private class NotingTown : Town {
    override val element = (document.createElement("div") as HTMLElement).also { it.className = "town" }
    val visits = mutableListOf<Place>()

    override fun walkTo(place: Place) {
        visits += place
    }
}

/** Plays the page in the test browser by tapping its buttons, the way a player would. */
class PhoneGameTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private var playedAgain = false

    @AfterTest
    fun removeScreen() {
        root.remove()
    }

    private fun open(seed: Int) = PhoneGame(root, seed, playAgain = { playedAgain = true }).show()

    private val screen: String
        get() = root.textContent.orEmpty()

    private fun buttons(): List<HTMLButtonElement> =
        root.querySelectorAll("button").asList().map { it as HTMLButtonElement }

    private fun button(text: String): HTMLButtonElement =
        buttons().firstOrNull { text in it.textContent.orEmpty() } ?: error("No button with \"$text\" in: $screen")

    private fun tap(text: String) = button(text).click()

    private fun type(amount: String) {
        val input = root.querySelector("input") as HTMLInputElement
        input.value = amount
        input.dispatchEvent(Event("input"))
    }

    /** Picks [room] (from 1), guesses [amount] and starts September. */
    private fun startMonth(room: Int, amount: String) {
        tap("${room}번 방")
        type(amount)
        tap("총비용 확인하기")
        tap("시작하기")
    }

    /** Plays [today]'s run with the cheapest room and an exact guess, then rests until September ends. */
    private fun restThrough(today: TodaysRun) {
        PhoneGame(root, today, playAgain = { playedAgain = true }).show()
        val game = FirstMinute(today.seed)
        startMonth(room = game.cheapestRoomIndex + 1, amount = "${game.lowestTotal}")
        while ("다시 하기" !in screen) tap("쉬기")
    }

    @Test
    fun `seed 1011 plays the first day the README shows`() {
        open(1011)
        assertTrue("판 번호 1011" in screen)
        assertTrue("잔고 \$3,000 · 9월 1일" in screen)
        assertTrue("1구역 \$105 · 2구역 \$145 · 3구역 \$195" in screen)
        assertTrue("별도면 한 달 \$120~\$190" in screen)

        tap("2번 방")
        type("1300")
        tap("총비용 확인하기")
        assertTrue("\$1,345" in screen)
        assertTrue("월세 \$970 + 공과금 \$180 + 교통 \$195" in screen)
        assertTrue("총비용이 가장 싼 방은 3번이었어요 (\$1,165).+0" in screen)
        assertTrue("내 짐작과 차이 \$45+55" in screen)
        assertTrue("방 고르기 점수 55 / 200" in screen)
        assertTrue("첫 달을 내고 남은 잔고 \$1,655" in screen)

        tap("9월 2일 시작하기")
        assertTrue("오늘9월 2일" in screen)
        assertTrue("남은 날29일" in screen)
        assertTrue("잔고\$1,655" in screen)
        assertTrue("10월 1일에 낼 총비용\$1,345" in screen)
        assertTrue("지원서 넣기면접이 잡힐 확률 27%, 체력 -1" in screen)

        tap("영어 공부")
        assertTrue("9월 2일에 있었던 일" in screen)
        assertEquals("polite", root.querySelector(".log")?.getAttribute("aria-live"))
        assertTrue("영어 실력이 올랐어요. 영어 2/10" in screen)
        assertTrue("생활비 -\$40, 잔고 \$1,615" in screen)
        assertTrue("오늘9월 3일" in screen)
    }

    @Test
    fun `the guess waits for a room and a whole number`() {
        open(1011)
        assertTrue(button("총비용 확인하기").disabled)
        assertTrue("방을 먼저 고르세요." in screen)

        tap("1번 방")
        assertEquals("true", button("1번 방").getAttribute("aria-pressed"))
        for (bad in listOf("-5", "abc", "12.5")) {
            type(bad)
            assertTrue(button("총비용 확인하기").disabled, bad)
            assertTrue("숫자로 적어 주세요. 예: 1200" in screen, bad)
        }

        type("1200")
        assertFalse(button("총비용 확인하기").disabled)
    }

    @Test
    fun `a tired player can only rest and is told why`() {
        open(1011)
        startMonth(room = 3, amount = "1165")
        repeat(5) { tap("카페 알바") }
        assertTrue("체력0/5" in screen)
        for (action in listOf("카페 알바", "영어 공부", "코딩 연습", "지원서 넣기")) {
            val button = button(action)
            assertTrue(button.disabled, action)
            assertTrue("체력이 없어요. 쉬어야 해요." in button.textContent.orEmpty(), action)
        }
        assertFalse(button("쉬기").disabled)
    }

    @Test
    fun `on an interview day the only button is the interview`() {
        open(1011)
        startMonth(room = 3, amount = "1165")
        while ("오늘은 면접 날이에요." !in screen && "다시 하기" !in screen) {
            val apply = button("지원서 넣기")
            if (apply.disabled) tap("쉬기") else apply.click()
        }
        assertTrue("오늘은 면접 날이에요." in screen, "seed 1011 should book an interview: $screen")
        assertEquals(listOf("면접 보기"), buttons().map { it.textContent })
        assertTrue("통과 확률" in screen)

        tap("면접 보기")
        assertTrue("1차 영어 면접 통과!" in screen || "1차 영어 면접에서 떨어졌어요." in screen, screen)
    }

    @Test
    fun `a month of rest ends with the ending line and another run`() {
        open(1011)
        startMonth(room = 3, amount = "1165")
        while ("다시 하기" !in screen) tap("쉬기")
        assertTrue("9월이 끝났어요." in screen)
        assertTrue("방 고르기 점수200/200" in screen)
        assertTrue("판 번호 1011" in screen)
        assertFalse(playedAgain)
        tap("다시 하기")
        assertTrue(playedAgain)
    }

    @Test
    fun `a seed comes from the query`() {
        assertEquals(1011, seedFrom("?seed=1011"))
        assertEquals(42, seedFrom("?lang=ko&seed=42"))
        assertNull(seedFrom(""))
        assertNull(seedFrom("?seed=abc"))
    }

    @Test
    fun `today's run comes from the query or the browser's date`() {
        val now = Date(2026, 9, 30) // A JavaScript date counts months from 0, so this is October 30th.
        assertEquals(TodaysRun(2026, 10, 30), todayFrom("?today", now))
        assertEquals(TodaysRun(2026, 10, 29), todayFrom("?today=2026-10-29", now))
        assertEquals(TodaysRun(2026, 10, 30), todayFrom("?today=soon", now))
        assertEquals(TodaysRun(2026, 10, 30), todayFrom("?today=2026-13-01", now))
        assertEquals(TodaysRun(2026, 10, 30), todayFrom("?today=2026-02-30", now))
        assertEquals(TodaysRun(2026, 10, 30), todayFrom("?today=0999-01-01", now))
        assertNull(todayFrom("?today", Date(2100, 0, 1)), "a clock past 2099 plays a new run instead")
        assertNull(todayFrom("?seed=1011", now))
        assertNull(todayFrom("", now))
    }

    @Test
    fun `the address picks the run, and is rewritten to name it`() {
        val now = Date(2026, 9, 30) // October 30th
        val today = TodaysRun(2026, 10, 30)
        val earlier = TodaysRun(2026, 10, 29)
        assertEquals(Opening(1011, null, null), openingFor("?seed=1011", now, newSeed = 4242))
        assertEquals(Opening(1011, null, null), openingFor("?seed=1011&today", now, newSeed = 4242))
        assertEquals(Opening(4242, null, "?seed=4242"), openingFor("", now, newSeed = 4242))
        assertEquals(Opening(4242, null, "?seed=4242"), openingFor("?seed=abc", now, newSeed = 4242))
        val todays = Opening(today.seed, today, "?today=2026-10-30")
        assertEquals(todays, openingFor("?today", now, newSeed = 4242))
        assertEquals(todays, openingFor("?today=2026-02-30", now, newSeed = 4242))
        val earliers = Opening(earlier.seed, earlier, "?today=2026-10-29")
        assertEquals(earliers, openingFor("?today=2026-10-29", now, newSeed = 4242))
        assertEquals(Opening(4242, null, "?seed=4242"), openingFor("?today", Date(2100, 0, 1), newSeed = 4242))
    }

    @Test
    fun `the address names the date of today's run`() {
        assertEquals("?today=2026-10-30", todayQuery(TodaysRun(2026, 10, 30)))
        assertEquals("?today=2027-01-05", todayQuery(TodaysRun(2027, 1, 5)))
        assertEquals(TodaysRun(2027, 1, 5), todayFrom(todayQuery(TodaysRun(2027, 1, 5)), Date()))
    }

    @Test
    fun `today's run is named under the title`() {
        val today = TodaysRun(2026, 10, 30)
        PhoneGame(root, today, playAgain = {}).show()
        assertTrue("10월 30일 오늘의 판 · 판 번호 ${today.seed}" in screen)
    }

    @Test
    fun `today's run ends with its result line and a copy button`() {
        restThrough(TodaysRun(2026, 10, 30))
        assertEquals(
            "밴쿠버 한 달 · 10월 30일 오늘의 판 · 방 고르기 200/200 · 10월 총비용 모자람",
            root.querySelector(".result-line")?.textContent,
        )
        assertFalse(button("결과 복사").disabled)
        assertEquals("polite", root.querySelector(".result-status")?.getAttribute("aria-live"))
        assertEquals("?today", root.querySelector(".ending a")?.getAttribute("href"))
        tap("다시 하기")
        assertTrue(playedAgain)
    }

    @Test
    fun `every ending offers today's run after 다시 하기`() {
        open(1011)
        startMonth(room = 3, amount = "1165")
        while ("다시 하기" !in screen) tap("쉬기")
        val link = root.querySelector(".ending a") ?: error("No link on the ending: $screen")
        assertEquals("오늘의 판", link.textContent)
        assertEquals("?today", link.getAttribute("href"))
        assertSame(button("다시 하기"), link.previousElementSibling)
    }

    @Test
    fun `the copy fallback selects the whole line`() {
        val line = document.createElement("p") as HTMLElement
        line.textContent = "밴쿠버 한 달 · 10월 30일 오늘의 판 · 방 고르기 170/200 · 9월 17일 취업 제안"
        root.appendChild(line)
        assertTrue(selectAll(line))
        assertEquals(line.textContent, getSelection().toString())
    }

    @Test
    fun `a run from a seed ends without a result line`() {
        open(1011)
        startMonth(room = 3, amount = "1165")
        while ("다시 하기" !in screen) tap("쉬기")
        assertNull(root.querySelector(".result-line"))
        assertTrue(buttons().none { "결과 복사" in it.textContent.orEmpty() })
    }

    @Test
    fun `a guess typed with a comma or a dollar sign reaches the reveal`() {
        for (typed in listOf("1,300", "\$1,300")) {
            open(1011)
            tap("2번 방")
            type(typed)
            assertFalse(button("총비용 확인하기").disabled, typed)
            tap("총비용 확인하기")
            assertTrue("내 짐작과 차이 \$45+55" in screen, typed)
        }
    }

    @Test
    fun `the town sits under the top bar, and each action sends the character to its building`() {
        val town = NotingTown()
        PhoneGame(root, 1011, playAgain = {}, town = town).show()
        assertNull(root.querySelector(".town"), "the room screen has no town")
        startMonth(room = 3, amount = "1165")
        assertSame(town.element, root.querySelector(".topbar")?.nextElementSibling)
        for (action in listOf("카페 알바", "영어 공부", "코딩 연습", "지원서 넣기", "쉬기")) tap(action)
        assertEquals(listOf(Place.CAFE, Place.LIBRARY, Place.COWORKING, Place.OFFICE, Place.HOME), town.visits)
        // Every day redraws the screen around the same town, so the character stays where it was.
        assertSame(town.element, root.querySelector(".topbar")?.nextElementSibling)
        assertEquals(1, root.querySelectorAll(".town").length)
    }

    @Test
    fun `on an interview day the character goes to the office`() {
        val town = NotingTown()
        PhoneGame(root, 1011, playAgain = {}, town = town).show()
        startMonth(room = 3, amount = "1165")
        while ("오늘은 면접 날이에요." !in screen) {
            val apply = button("지원서 넣기")
            if (apply.disabled) tap("쉬기") else apply.click()
        }
        assertSame(town.element, root.querySelector(".topbar")?.nextElementSibling)
        town.visits.clear()
        tap("면접 보기")
        assertEquals(listOf(Place.OFFICE), town.visits)
    }

    @Test
    fun `the ending has no town`() {
        PhoneGame(root, 1011, playAgain = {}, town = NotingTown()).show()
        startMonth(room = 3, amount = "1165")
        while ("다시 하기" !in screen) tap("쉬기")
        assertNull(root.querySelector(".town"))
    }
}
