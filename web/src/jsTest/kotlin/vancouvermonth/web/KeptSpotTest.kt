package vancouvermonth.web

import kotlin.js.Date
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import vancouvermonth.core.Action
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Spending
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.save.Spend
import vancouvermonth.text.Continue
import vancouvermonth.text.Riding
import vancouvermonth.text.Transit
import vancouvermonth.text.Transport

/**
 * Where the character was left is kept beside the month, and the page opened again stands it there, in a real town at a
 * phone's size; while the record has moved on since, or after a night, it starts at 내 방's door.
 */
class KeptSpotTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private var town: NeighbourhoodScene? = null

    @AfterTest
    fun removeScreen() {
        town?.close()
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    /**
     * Opens seed 1011's page on [store], as a reload does, with 내 방 in zone 3, in a real town at a phone's size: going on
     * with the month it keeps, or starting one. Its life clock stands still, and its nights and talks pass quickly.
     */
    private fun open(store: FakeStore): NeighbourhoodScene {
        town?.close()
        town = null
        root.innerHTML = ""
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = {
            val opened = assertNotNull(openWalkableTown(lessMotion = false, homeZone = 3), "No WebGL 2")
            (opened as NeighbourhoodScene).also {
                it.lifeClock = { 0.0 }
                it.passMillis = 60.0
                it.nightMillis = 120.0
                val element = it.element
                element.style.setProperty("inset", "auto")
                element.style.left = "0px"
                element.style.top = "0px"
                element.style.width = "375px"
                element.style.height = "812px"
                town = it
            }
        })
        game.talkSettleMillis = 0.0
        game.slot = SaveSlot(Memory { store })
        game.show()
        if (store.items[SaveSlot.KEY].isNullOrEmpty()) startSeed1011(root) else root.button(Continue.CONTINUE).click()
        return assertNotNull(town)
    }

    private val door: Spot
        get() = HomeSite.of(3).venue(Place.HOME).door

    private val cafe: Venue
        get() = HomeSite.of(3).venue(Place.SLOPE_CAFE)

    private val screen: String
        get() = root.textContent.orEmpty()

    private fun hud(): String = root.querySelector(".hud")?.textContent.orEmpty()

    private fun talk(): HTMLElement? = root.querySelector(".talk") as? HTMLElement

    private fun answer(): HTMLButtonElement =
        root.querySelector(".talk .spend") as? HTMLButtonElement ?: error("No talk is open: $screen")

    private fun tapName(place: Place) {
        val name = root.querySelectorAll(".place-name").asList().map { it as HTMLButtonElement }
            .first { it.textContent == place.label }
        name.click()
    }

    /** A place a card asks whether to go to, by its name. */
    private fun goButton(name: String): HTMLButtonElement =
        assertNotNull(root.querySelector(".chooser"), "No chooser: $screen").querySelectorAll(".go").asList()
            .map { it as HTMLButtonElement }
            .firstOrNull { it.querySelector(".action-name")?.textContent == name } ?: error("No $name: $screen")

    /** A step of a test that waits: its name in a failure, when it is ready to run, and what it does. */
    private class Step(val name: String, val ready: () -> Boolean, val run: () -> Unit)

    /** Runs each step once it is ready, checking every 20 ms, and fails a step that waits more than half a minute. */
    private fun play(vararg steps: Step): Promise<Unit> = Promise { resolve, reject ->
        fun run(index: Int) {
            if (index == steps.size) {
                resolve(Unit)
                return
            }
            val step = steps[index]
            val started = Date.now()
            fun check() {
                try {
                    when {
                        step.ready() -> {
                            step.run()
                            run(index + 1)
                        }
                        Date.now() - started > 30_000 -> reject(AssertionError("${step.name}: waited too long: $screen"))
                        else -> window.setTimeout({ check() }, 20)
                    }
                } catch (error: Throwable) {
                    reject(AssertionError("${step.name}: ${error.message}"))
                }
            }
            check()
        }
        run(0)
    }

    @Test
    fun `the page opened again stands the character where a walk left it, an action taken there or a walk begun`():
        Promise<Unit> {
        val store = FakeStore()
        var again = open(store)
        return play(
            Step("walk to the near café", { again.isStill }) {
                assertEquals(door, again.characterSpot)
                tapName(Place.SLOPE_CAFE)
            },
            Step("there, open the page again", { again.isStill && talk() != null }) {
                again = open(store)
                assertEquals(cafe.talkSpot, again.characterSpot)
                assertNull(talk(), "a talk on opening: $screen")
            },
            Step("its name opens the talk at once, and the shift is worked there", { again.isStill }) {
                tapName(Place.SLOPE_CAFE)
                answer().click()
                assertTrue("체력 2/5" in hud(), hud())
                // Still there, though the record has moved on by the shift.
                again = open(store)
                assertEquals(cafe.talkSpot, again.characterSpot)
                assertTrue("체력 2/5" in hud(), hud())
            },
            Step("a walk begun and not ended counts for nothing yet", { again.isStill }) {
                tapName(Place.HOME)
                assertNotNull(again.markerSpot, "no walk")
                again = open(store)
                assertEquals(cafe.talkSpot, again.characterSpot)
            },
        )
    }

    @Test
    fun `a move recorded after the spot was written falls back to 내 방's door`(): Promise<Unit> {
        val store = FakeStore()
        val first = open(store)
        return play(
            Step("walk to the near café", { first.isStill }) { tapName(Place.SLOPE_CAFE) },
            Step("there, another tab moves the month on", { first.isStill && talk() != null }) {
                val record = assertNotNull(MonthRecord.decode(store.items[SaveSlot.KEY]))
                store.items[SaveSlot.KEY] = record.after(Spend(Spending.SHARED_BIKE_RIDE, 3)).encode()
                assertEquals(door, open(store).characterSpot)
            },
        )
    }

    @Test
    fun `the morning after a night's sleep starts at 내 방's door, not where the sleep was`(): Promise<Unit> {
        val store = FakeStore()
        var again = open(store)
        val neighbour = HomeSite.of(3).venue(Place.HOME).talkSpot
        return play(
            Step("up to the neighbour", { again.isStill }) { tapName(Place.HOME) },
            Step("by the neighbour, sleep; opened again in the night", { again.isStill }) {
                assertEquals(neighbour, again.characterSpot)
                answer().click()
                assertTrue(again.isChangingDay, "no night")
                again = open(store)
                assertEquals(door, again.characterSpot)
            },
            Step("and slept through to the morning, opened again", { again.isStill }) {
                tapName(Place.HOME)
            },
            Step("up to the neighbour again, and sleep", { again.isStill }) {
                answer().click()
            },
            Step("the next morning", { !again.isChangingDay }) {
                assertEquals(door, again.characterSpot)
                assertEquals(door, open(store).also { again = it }.characterSpot)
            },
        )
    }

    @Test
    fun `a ride kept on its way stands the character where it was paid to, the fare paid, and 내리기 where it got out`():
        Promise<Unit> {
        val store = FakeStore()
        var again = open(store)
        var left = door
        return play(
            Step("a taxi to the near café, and the page opened again on the way", { again.isStill }) {
                root.button(Transit.CALL_TAXI).click()
                goButton(Place.SLOPE_CAFE.label).click()
                assertEquals(Transport.TAXI, again.travelling)
                val paid = root.querySelector(".hud-figures")?.textContent
                again = open(store)
                assertNull(again.travelling)
                assertEquals(cafe.door, again.characterSpot)
                assertEquals(paid, root.querySelector(".hud-figures")?.textContent, "the fare is not as it was paid")
                assertNull(talk(), "a talk on opening: $screen")
            },
            Step("a taxi toward a far office", { again.isStill }) {
                root.button(Transit.CALL_TAXI).click()
                goButton(Place.OFFICE.label).click()
            },
            Step("내리기 on the way, and opened again", { distanceBetween(again.characterSpot, cafe.door) > 5.0 }) {
                root.button(Riding.GET_OFF).click()
                left = again.characterSpot
                assertEquals(left, open(store).also { again = it }.characterSpot)
            },
        )
    }

    @Test
    fun `a month with no spot, an unreadable one or one in a wall starts at 내 방's door`() {
        val first = FirstMinute(1011)
        val record = MonthRecord(1011, null, first.cheapestRoomIndex, first.lowestTotal, listOf(Choice.Take(Action.STUDY_ENGLISH)))
        val moves = record.moves.size
        val inside = HomeSite.of(3).venue(Place.SLOPE_CAFE)
        val spots = listOf(null, "", "nonsense", "$moves|x|y", "$moves|1", "$moves|${inside.x + 2.5}|${inside.z + 2.0}")
        for (spot in spots) {
            val store = FakeStore()
            store.items[SaveSlot.KEY] = record.encode()
            if (spot != null) store.items[SaveSlot.SPOT_KEY] = spot
            assertEquals(door, open(store).characterSpot, "$spot")
        }
    }

    @Test
    fun `a new month takes nothing from where an earlier one was left`() {
        val store = FakeStore()
        store.items[SaveSlot.SPOT_KEY] = "0|${cafe.door.x}|${cafe.door.z}"
        assertEquals(door, open(store).characterSpot)
        // Opened again on its first morning, with no walk yet.
        assertFalse(store.items[SaveSlot.KEY].isNullOrEmpty(), "nothing kept")
        assertEquals(door, open(store).characterSpot)
    }

    @Test
    fun `the slot reads back the spot it was given, only at the same count, and clear takes it too`() {
        val store = FakeStore()
        val slot = SaveSlot(Memory { store })
        assertNull(slot.keptSpot(0))
        slot.keepSpot(Spot(153.5, 52.25), 12)
        assertEquals(Spot(153.5, 52.25), slot.keptSpot(12))
        assertNull(slot.keptSpot(11))
        assertNull(slot.keptSpot(13))
        slot.clear()
        assertNull(slot.keptSpot(12))
    }

    @Test
    fun `where the browser refuses its storage no spot is kept, and nothing breaks`() {
        val slot = SaveSlot(Memory { error("Refused") })
        slot.keepSpot(Spot(1.0, 2.0), 3)
        assertNull(slot.keptSpot(3))
        slot.forgetSpot()
    }
}
