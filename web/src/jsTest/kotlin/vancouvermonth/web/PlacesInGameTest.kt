package vancouvermonth.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import vancouvermonth.core.Action
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.text.Continue
import vancouvermonth.text.interviewDayLine
import vancouvermonth.text.keyword

/** A town that notes which place the game marks, as on an interview day. Everything else is [QuietTown]'s. */
private class MarkingTown(val quiet: QuietTown = QuietTown()) : WalkableTown by quiet {
    var marked: Place? = null

    override fun mark(place: Place?) {
        marked = place
    }
}

/**
 * The places of each kind in the game: the same action at any of them, kept in the record with where it was taken; the
 * interview at the office applied at, named on the top card, kept over a reload; and the taxi's places nearest first.
 */
class PlacesInGameTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val store = FakeStore()
    private var town = MarkingTown()

    @AfterTest
    fun removeScreen() {
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    /** Opens seed 1011's page on [store], as a reload does, in a new town that draws nothing. */
    private fun open(): PhoneGame {
        root.innerHTML = ""
        town = MarkingTown()
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { town })
        game.slot = SaveSlot(Memory { store })
        game.talkSettleMillis = 0.0
        game.show()
        return game
    }

    private val kept: MonthRecord
        get() = assertNotNull(SaveSlot(Memory { store }).record(), "Nothing kept")

    private val month: Month
        get() = assertNotNull(kept.replay(), "The kept month does not replay").month

    private fun talk(): HTMLElement = assertNotNull(root.querySelector(".talk") as? HTMLElement, "No talk open")

    private fun talkButton(text: String): HTMLButtonElement? = talk().querySelectorAll("button").asList()
        .map { it as HTMLButtonElement }.firstOrNull { text in it.textContent.orEmpty() }

    /** Sleeps at 내 방: its talk, then 자기. */
    private fun sleep() {
        town.onPlace(Place.HOME)
        assertNotNull(talkButton(keyword(Action.REST))).click()
    }

    @Test
    fun `each action is taken at any place of its kind, and the record keeps which`() {
        open()
        startSeed1011(root)
        for (place in Place.entries.filter { it.kind != PlaceKind.HOME && it.kind != PlaceKind.OFFICE }) {
            val action = place.kind.action
            if (month.energy < Month.cost(action)) sleep()
            town.onPlace(place)
            // Its own person, by name, with the place's own name.
            assertTrue(place.person.title in talk().textContent.orEmpty(), "$place")
            assertEquals("${place.person.role} · ${place.label}", talk().querySelector(".talk-role")?.textContent)
            val answer = assertNotNull(talkButton(keyword(action)), "$place")
            assertFalse(answer.disabled, "$place")
            answer.click()
            assertEquals(Choice.Take(action, place.number), kept.choices.last(), "$place")
        }
        // Each office takes an application, and the record keeps the office.
        for (place in Place.entries.filter { it.kind == PlaceKind.OFFICE }) {
            sleep()
            town.onPlace(place)
            assertEquals("${place.person.role} · ${place.label}", talk().querySelector(".talk-role")?.textContent)
            if (month.interview != null) continue
            assertNotNull(talkButton(keyword(Action.APPLY))).click()
            assertEquals(Choice.Take(Action.APPLY, place.number), kept.choices.last())
        }
        // The line written: each place's number after its action's letter, none for the first of a kind.
        val line = kept.encode()
        for (written in listOf("E1", "W1", "C1", "E2", "W2")) assertTrue(written in line, "$written not in $line")
    }

    @Test
    fun `the interview is at the office applied at, named on the top card, and still there after a reload`() {
        open()
        startSeed1011(root)
        // An application each day at the town centre's office in Burnaby, until one books an interview.
        val office = Place.TOWN_CENTRE_OFFICE
        while (month.interview == null) {
            assertTrue(month.canStillApplyThisMonth, "No interview all month")
            town.onPlace(office)
            assertNotNull(talkButton(keyword(Action.APPLY))).click()
            if (month.interview == null) sleep()
        }
        assertEquals(office.number, kept.appliedAt)
        while (!month.hasInterviewToday) sleep()
        val hint = interviewDayLine("중심가 회사")
        assertEquals("오늘은 면접 날이에요. 중심가 회사로 가세요.", hint)
        assertTrue(hint in root.querySelector(".hud")?.textContent.orEmpty())
        assertEquals(office, town.marked)
        // At another office there is no interview today: its person sends the player on, and nothing else is taken.
        for (other in listOf(Place.OFFICE, Place.JUNCTION_OFFICE)) {
            town.onPlace(other)
            assertNull(talkButton("면접 보기"), "$other")
            assertTrue(assertNotNull(talkButton(keyword(Action.APPLY))).disabled, "$other")
            assertTrue("회사" in talk().querySelector(".talk-line")?.textContent.orEmpty())
        }
        // A reload goes on with the same day: the interview is still at the town centre's office.
        open()
        root.button(Continue.CONTINUE).click()
        assertTrue(hint in root.querySelector(".hud")?.textContent.orEmpty())
        assertEquals(office, town.marked)
        town.onPlace(Place.OFFICE)
        assertNull(talkButton("면접 보기"))
        town.onPlace(office)
        assertNotNull(talkButton("면접 보기")).click()
        assertEquals(Choice.AttendInterview, kept.choices.last())
        assertFalse(month.hasInterviewToday)
    }

    @Test
    fun `a month kept before the places came goes on, its interview at downtown's office`() {
        // A line as every one kept before the places came: letters with no place after them. Applications each morning
        // until one books an interview, then sleep up to its day.
        val game = FirstMinute(1011)
        var record = MonthRecord(1011, null, game.cheapestRoomIndex, game.lowestTotal)
        fun replayed(): Month = assertNotNull(record.replay()).month
        while (replayed().interview == null) {
            record = record.after(Choice.Take(Action.APPLY))
            if (replayed().interview == null) record = record.after(Choice.Take(Action.REST))
        }
        while (!replayed().hasInterviewToday) record = record.after(Choice.Take(Action.REST))
        val line = record.encode()
        assertNull(Regex("[A-Z][0-9]").find(line.substringAfterLast('|')), line)
        SaveSlot(Memory { store }).keep(assertNotNull(MonthRecord.decode(line)))
        open()
        root.button(Continue.CONTINUE).click()
        assertTrue(interviewDayLine(Place.OFFICE.label) in root.querySelector(".hud")?.textContent.orEmpty())
        assertEquals(Place.OFFICE, town.marked)
        town.onPlace(Place.TOWN_CENTRE_OFFICE)
        assertNull(talkButton("면접 보기"))
        town.onPlace(Place.OFFICE)
        assertNotNull(talkButton("면접 보기")).click()
        assertEquals(Choice.AttendInterview, kept.choices.last())
    }

}
