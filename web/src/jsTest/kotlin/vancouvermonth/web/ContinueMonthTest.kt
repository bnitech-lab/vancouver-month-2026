package vancouvermonth.web

import kotlin.js.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import vancouvermonth.core.Action
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.core.TodaysRun
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.text.Continue
import vancouvermonth.text.continueFrom
import vancouvermonth.text.date
import vancouvermonth.text.greeting
import vancouvermonth.text.money
import vancouvermonth.text.replacesKept
import vancouvermonth.text.weatherOn
import vancouvermonth.text.weatherWord

/**
 * A town whose evening does not end, as when the page is closed while the day changes. Everything else is
 * [QuietTown]'s.
 */
private class EveningTown(private val quiet: QuietTown = QuietTown()) : WalkableTown by quiet {
    var evening: Place? = null

    override fun endDay(place: Place) {
        evening = place
    }
}

/** The month in progress survives a reload: kept after every day, offered on opening, and let go at the ending. */
class ContinueMonthTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val other = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val store = FakeStore()
    private var playedAgain = false
    private val guides = mutableListOf<FirstRunGuide>()

    @AfterTest
    fun removeScreen() {
        guides.forEach { it.close() }
        root.remove()
        other.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    /**
     * Opens the page for a run on [storage], as a reload does: a new game on the same storage, in [town] when one is
     * given and with buttons when not.
     */
    private fun open(
        storage: KeyValueStore = store,
        town: WalkableTown? = null,
        seed: Int = 1011,
        today: TodaysRun? = null,
        into: HTMLElement = root,
    ): PhoneGame {
        into.innerHTML = ""
        val game = if (today == null) {
            PhoneGame(into, seed, playAgain = { playedAgain = true }, makeWalkableTown = { town })
        } else {
            PhoneGame(into, today, playAgain = { playedAgain = true }, makeWalkableTown = { town })
        }
        game.slot = SaveSlot(Memory { storage })
        game.guide = FirstRunGuide().also { guides += it }
        game.talkSettleMillis = 0.0
        game.show()
        return game
    }

    /** Past the intro, picks [seed]'s cheapest room in [into] and reveals it with an exact guess, up to the start card. */
    private fun revealRun(seed: Int, into: HTMLElement = root) {
        passIntro(into)
        val game = FirstMinute(seed)
        into.button("${game.cheapestRoomIndex + 1}번 방").click()
        val input = into.querySelector("input") as HTMLInputElement
        input.value = "${game.lowestTotal}"
        input.dispatchEvent(Event("input"))
        into.button("총비용 확인하기").click()
    }

    /** Starts September for [seed] in [into]: the cheapest room, an exact guess, and 시작하기. */
    private fun startRun(seed: Int, into: HTMLElement = root) {
        revealRun(seed, into)
        into.button("시작하기").click()
    }

    /** Seed 1011's September, after [actions], as core plays it. */
    private fun played(vararg actions: Action): Month {
        val game = FirstMinute(1011)
        val month = game.startMonth(game.cheapestRoomIndex, game.lowestTotal)
        for (action in actions) month.take(action)
        return month
    }

    private val card: HTMLElement?
        get() = root.querySelector(".kept") as? HTMLElement

    private fun text(selector: String, into: HTMLElement = root): String =
        assertNotNull(into.querySelector(selector), "No $selector in: ${into.textContent}").textContent.orEmpty()

    /** The kept record, as the page left it in [store]. */
    private val kept: MonthRecord?
        get() = SaveSlot(Memory { store }).record()

    /** One day with buttons, the same way every time: the interview when there is one, else apply, else rest. */
    private fun playDay(into: HTMLElement) {
        val buttons = into.querySelectorAll("button").asList().map { it as HTMLButtonElement }
        val choice = buttons.firstOrNull { "면접 보기" in it.textContent.orEmpty() }
            ?: buttons.first { "지원서 넣기" in it.textContent.orEmpty() }.takeIf { !it.disabled }
            ?: buttons.first { "쉬기" in it.textContent.orEmpty() }
        choice.click()
    }

    @Test
    fun `after a few days in the town, a reload offers 이어하기 on that day, and goes on there in the town`() {
        val town = QuietTown()
        open(town = town)
        startSeed1011(root)
        for (place in listOf(Place.LIBRARY, Place.CAFE, Place.COWORKING)) {
            town.onPlace(place)
            (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        }
        val month = played(Action.STUDY_ENGLISH, Action.WORK, Action.PRACTISE_CODING)
        assertEquals(5, month.day)
        val sky = town.weather
        assertEquals(weatherOn(1011, month.day), sky)

        val again = QuietTown()
        open(town = again)
        val offer = assertNotNull(card, "No card on opening again")
        assertEquals(Continue.TITLE, text(".kept h2"))
        assertEquals(continueFrom(month.day, month.balance), text(".kept-from"))
        assertTrue("9월 5일부터" in offer.textContent.orEmpty())
        assertNull(root.querySelector(".rooms"), "The rooms came before the card")
        // Each button says what it does, and 이어하기 is described by the day and balance it goes on from.
        val goOn = root.button(Continue.CONTINUE)
        assertEquals("button", goOn.type)
        assertEquals("kept-from", goOn.getAttribute("aria-describedby"))
        assertEquals("kept-note", root.button(Continue.NEW).getAttribute("aria-describedby"))
        assertEquals("kept-title", offer.getAttribute("aria-labelledby"))

        goOn.click()
        assertNull(card)
        assertSame(again.element, root.firstElementChild, "Not in the town")
        val hud = text(".hud")
        assertTrue(date(month.day) in hud && money(month.balance) in hud, hud)
        val skills = "영어 ${month.english}/10 · 코딩 ${month.coding}/10 · 체력 ${month.energy}/5"
        assertTrue(skills in hud, hud)
        // The same sky as before the reload: the weather comes from the run and the day alone.
        assertEquals(sky, again.weather)
        assertEquals(" 날씨 ${weatherWord(sky)}", text(".hud .weather"))
        assertTrue("${date(month.day - 1)}에 있었던 일" in text(".over-town .log"))
        assertNull(root.querySelector(".guide-card"), "The guide opened again")
        assertSame(root.querySelector(".over-town .log"), document.activeElement, "The focus is not on the log")
    }

    @Test
    fun `continued on the first day, the town has its hint and the focus is on its top card`() {
        open(town = QuietTown())
        startSeed1011(root)

        open(town = QuietTown())
        assertEquals(continueFrom(Month.FIRST_DAY, played().balance), text(".kept-from"))
        root.button(Continue.CONTINUE).click()
        assertNull(root.querySelector(".over-town .log"))
        assertNotNull(root.querySelector(".town-hint"))
        assertSame(root.querySelector(".hud"), document.activeElement, "The focus is not on the top card")
    }

    @Test
    fun `the people of the places where days were spent are not met for the first time again`() {
        val town = QuietTown()
        open(town = town)
        startSeed1011(root)
        town.onPlace(Place.LIBRARY)
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()

        val again = QuietTown()
        open(town = again)
        root.button(Continue.CONTINUE).click()
        again.onPlace(Place.LIBRARY)
        val month = played(Action.STUDY_ENGLISH)
        val yesterday = assertNotNull(kept?.replay()).lastEvents
        val leo = personAt(Place.LIBRARY)
        val sky = weatherOn(1011, month.day)
        val expected = greeting(leo, month, firstMeeting = false, yesterday = yesterday, weather = sky)
        assertTrue(expected != greeting(leo, month, firstMeeting = true, yesterday = yesterday, weather = sky))
        assertEquals(expected, text(".talk .talk-line .visually-hidden"))
    }

    @Test
    fun `with buttons, 이어하기 shows the same day, figures, log and interview as before the reload`() {
        open()
        startSeed1011(root)
        while ("오늘은 면접 날이에요." !in root.textContent.orEmpty()) playDay(root)
        val before = listOf(".topbar", ".stats", ".interview", ".log", ".actions").map { text(it) }

        open()
        assertNotNull(card)
        root.button(Continue.CONTINUE).click()
        assertEquals(before, listOf(".topbar", ".stats", ".interview", ".log", ".actions").map { text(it) })
        assertNotNull(root.button("면접 보기"))
    }

    @Test
    fun `a continued month plays on exactly as the same month never reloaded`() {
        open(storage = FakeStore(), into = other)
        startSeed1011(other)
        open()
        startSeed1011(root)
        // Four days, so not even the quickest offer can end the month before the reload.
        repeat(4) {
            playDay(other)
            playDay(root)
        }
        open()
        root.button(Continue.CONTINUE).click()
        assertEquals(text(".topbar", other), text(".topbar"))
        while ("다시 하기" !in other.textContent.orEmpty()) playDay(other)
        while ("다시 하기" !in root.textContent.orEmpty()) playDay(root)
        assertEquals(text(".ending", other), text(".ending"))
    }

    @Test
    fun `today's run continued plays on to the same ending and the same result line`() {
        val today = TodaysRun(2026, 10, 30)
        open(storage = FakeStore(), today = today, into = other)
        startRun(today.seed, other)
        open(today = today)
        startRun(today.seed)
        repeat(4) {
            playDay(other)
            playDay(root)
        }
        open(today = today)
        root.button(Continue.CONTINUE).click()
        while ("다시 하기" !in other.textContent.orEmpty()) playDay(other)
        while ("다시 하기" !in root.textContent.orEmpty()) playDay(root)
        assertTrue("10월 30일 오늘의 판" in text(".result-line"))
        assertEquals(text(".result-line", other), text(".result-line"))
        assertEquals(text(".ending", other), text(".ending"))
        assertNull(kept)
    }

    @Test
    fun `a reload while the day is changing goes on with the next day and that day's log`() {
        val town = EveningTown()
        open(town = town)
        startSeed1011(root)
        town.onPlace(Place.LIBRARY)
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        assertEquals(Place.LIBRARY, town.evening, "The day did not start to change")

        val again = QuietTown()
        open(town = again)
        val month = played(Action.STUDY_ENGLISH)
        assertEquals(continueFrom(month.day, month.balance), text(".kept-from"))
        root.button(Continue.CONTINUE).click()
        assertTrue("${date(Month.FIRST_DAY)}에 있었던 일" in text(".over-town .log"))
        assertTrue(date(month.day) in text(".hud"))
        // The new town is in the morning: it takes a tap, and a talk opens.
        again.onPlace(Place.CAFE)
        assertNotNull(root.querySelector(".talk"), "The continued day still waited for a morning")
    }

    @Test
    fun `the month is kept from its start and after every day`() {
        open()
        assertNull(kept, "Kept before the month started")
        startSeed1011(root)
        val game = FirstMinute(1011)
        assertEquals(MonthRecord(1011, null, game.cheapestRoomIndex, game.lowestTotal), kept)
        root.button("영어 공부").click()
        root.button("카페 알바").click()
        val days = listOf(Choice.Take(Action.STUDY_ENGLISH), Choice.Take(Action.WORK))
        assertEquals(days, kept?.days)
        assertEquals("vancouver-month.save", SaveSlot.KEY)
    }

    @Test
    fun `a slot given after the page first showed is still read`() {
        open()
        startSeed1011(root)
        root.button("영어 공부").click()

        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {})
        game.show()
        assertNull(card)
        game.slot = SaveSlot(Memory { store })
        game.show()
        assertNotNull(card, "The slot set late was never read")
    }

    @Test
    fun `새로 시작 starts the run over from its rooms and drops the kept month`() {
        open()
        startSeed1011(root)
        root.button("영어 공부").click()

        open()
        root.button(Continue.NEW).click()
        assertNull(card)
        assertNotNull(root.querySelector(".rooms"), "No rooms after 새로 시작")
        assertTrue("판 번호 1011" in root.textContent.orEmpty())
        assertNull(kept)
        assertFalse(playedAgain, "An address that names a run played a new one")

        open()
        assertNull(card, "The dropped month was offered again")
        startSeed1011(root)
        assertTrue(date(Month.FIRST_DAY) in text(".topbar"))
    }

    @Test
    fun `from an address with no run, 새로 시작 plays a new run, as such an address did before`() {
        open()
        startSeed1011(root)
        root.button("영어 공부").click()

        val game = open()
        game.startOverWithNewRun = true
        game.show()
        root.button(Continue.NEW).click()
        assertTrue(playedAgain, "No new run")
        assertNull(kept)
    }

    @Test
    fun `the ending lets the kept month go, so the next visit starts fresh`() {
        open()
        startSeed1011(root)
        while ("다시 하기" !in root.textContent.orEmpty()) root.button("쉬기").click()
        assertNull(kept)
        assertEquals("", store.items[SaveSlot.KEY])

        open()
        assertNull(card)
        assertNotNull(root.querySelector(".intro"))
        assertNull(root.querySelector(".rooms"))
    }

    @Test
    fun `storage that throws or is missing shows no card, and the month plays as before`() {
        for (storage in listOf<KeyValueStore?>(RefusingStore(), null)) {
            root.innerHTML = ""
            val game = PhoneGame(root, 1011, playAgain = {})
            game.slot = SaveSlot(Memory { storage })
            game.show()
            startSeed1011(root)
            root.button("영어 공부").click()
            assertTrue("${date(Month.FIRST_DAY)}에 있었던 일" in text(".log"))

            root.innerHTML = ""
            val again = PhoneGame(root, 1011, playAgain = {})
            again.slot = SaveSlot(Memory { storage })
            again.show()
            assertNull(card)
            assertNotNull(root.querySelector(".intro"))
        }
    }

    @Test
    fun `another run's month is not offered, and keeps the slot until a new month starts`() {
        open()
        startSeed1011(root)
        root.button("영어 공부").click()
        val first = kept

        open(seed = 2022)
        assertNull(card, "Seed 1011's month was offered on seed 2022")
        assertEquals(first, kept, "Opening another run dropped the kept month")
        revealRun(2022)
        // The start card says, by its day and not by a run number, which month starting drops.
        assertEquals(replacesKept(Month.FIRST_DAY + 1), text(".start .replaces"))
        assertEquals("replaces", root.button("시작하기").getAttribute("aria-describedby"))
        assertEquals(first, kept, "Revealing a room dropped the kept month")
        root.button("시작하기").click()
        assertEquals(2022, kept?.seed)

        open()
        assertNull(card, "Seed 1011's month came back after seed 2022's started")
    }

    @Test
    fun `the start card says nothing of a kept month when there is none, or it is this run's`() {
        open()
        revealRun(1011)
        assertNull(root.querySelector(".replaces"))
        assertNull(root.button("시작하기").getAttribute("aria-describedby"))
        root.button("시작하기").click()
        root.button("영어 공부").click()

        open()
        root.button(Continue.NEW).click()
        revealRun(1011)
        assertNull(root.querySelector(".replaces"), "The run's own month, just dropped, was named")
    }

    @Test
    fun `today's run is offered again on the same date only`() {
        val today = TodaysRun(2026, 10, 30)
        open(today = today)
        startRun(today.seed)
        root.button("영어 공부").click()
        assertEquals(today, kept?.today)

        open(today = TodaysRun(2026, 10, 31))
        assertNull(card, "Another date's run offered the month")
        open(seed = today.seed)
        assertNull(card, "The seed page offered today's run")
        open(today = today)
        assertNotNull(card)
        assertTrue("10월 30일 오늘의 판" in root.textContent.orEmpty())
        root.button(Continue.CONTINUE).click()
        assertTrue(date(Month.FIRST_DAY + 1) in text(".topbar"))
    }

    @Test
    fun `a kept line that does not read or does not replay is dropped without a word`() {
        // A bad letter, another version (the first, from before money could be spent, and the second, from before the
        // guess moved money), an interview on a day without one, a room that is not there, and a bike bought twice.
        val lines = listOf(
            "3|1011||2|1165|Z",
            "9|1011||2|1165|",
            "1|1011||2|1165|EWC",
            "2|1011||2|1165|EWC",
            "3|1011||2|1165|I",
            "3|1011||7|1165|",
            "3|1011||2|1165|b350Eb350",
        )
        for (line in lines) {
            store.items[SaveSlot.KEY] = line
            open()
            assertNull(card, "Offered: $line")
            assertNotNull(root.querySelector(".intro"))
            assertEquals("", store.items[SaveSlot.KEY], "Not dropped: $line")
        }
    }

    @Test
    fun `a replay that throws counts as one that does not replay, and the page starts fresh`() {
        open()
        startSeed1011(root)
        root.button("영어 공부").click()

        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {})
        game.slot = SaveSlot(Memory { store }) { error("A rule this line was not kept under") }
        game.show()
        assertNull(card)
        assertNotNull(root.querySelector(".intro"))
        assertEquals("", store.items[SaveSlot.KEY])
    }

    @Test
    fun `only a kept month that replays decides the run of an address with no run`() {
        val slot = SaveSlot(Memory { store })
        store.items[SaveSlot.KEY] = "3|1011||2|1165|EWC"
        assertEquals(1011, slot.continuable()?.seed)
        val notContinuable = listOf("3|1011||2|1165|I", "3|1011||2|1165|Z", "1|1011||2|1165|EWC", "2|1011||2|1165|EWC", "")
        for (line in notContinuable) {
            store.items[SaveSlot.KEY] = line
            assertNull(slot.continuable(), "Continuable: $line")
        }
        store.items[SaveSlot.KEY] = "3|1011||2|1165|EWC"
        assertNull(SaveSlot(Memory { store }) { error("A rule this line was not kept under") }.continuable())
    }

    @Test
    fun `an address with no run plays the kept month's run, so the card can offer it`() {
        val now = Date(2026, 9, 30) // October 30th
        val today = TodaysRun(2026, 10, 29)
        val bySeed = MonthRecord(1011, null, 0, 1000)
        val byDate = MonthRecord(today.seed, today, 0, 1000)
        val bySeedOpening = openingFor("", now, newSeed = 4242, kept = bySeed)
        assertEquals(Opening(1011, null, "?seed=1011", fromKept = true), bySeedOpening)
        val byDateOpening = openingFor("", now, newSeed = 4242, kept = byDate)
        assertEquals(Opening(today.seed, today, "?today=2026-10-29", fromKept = true), byDateOpening)
        // An address that names a run plays that run, kept month or not.
        assertEquals(Opening(5, null, null), openingFor("?seed=5", now, newSeed = 4242, kept = bySeed))
        val octoberThirtieth = TodaysRun(2026, 10, 30)
        assertEquals(
            Opening(octoberThirtieth.seed, octoberThirtieth, "?today=2026-10-30"),
            openingFor("?today", now, newSeed = 4242, kept = byDate),
        )
        assertEquals(Opening(4242, null, "?seed=4242"), openingFor("", now, newSeed = 4242, kept = null))
    }
}
