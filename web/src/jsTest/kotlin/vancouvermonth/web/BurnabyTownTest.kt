package vancouvermonth.web

import kotlin.js.Date
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.save.MonthRecord
import vancouvermonth.text.Continue
import vancouvermonth.text.Transport

/**
 * The town on the wide map, drawn for real on SwiftShader at a phone's size: a room in zone 2 puts 내 방 in Burnaby,
 * where each morning starts; the SkyTrain runs from Burnaby to downtown; and the small map follows the character there.
 */
class BurnabyTownTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private var town: NeighbourhoodScene? = null

    @AfterTest
    fun removeScreen() {
        town?.close()
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    /**
     * Opens seed 1011's game, keeping its month in [store] when there is one; its towns are real, their life clocks
     * stand still, and a change of day takes [night] milliseconds.
     */
    private fun openGame(night: Double, store: FakeStore? = null) {
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { homeZone ->
            val opened = assertNotNull(openWalkableTown(lessMotion = false, homeZone = homeZone), "No WebGL 2")
            (opened as NeighbourhoodScene).also {
                it.lifeClock = { 0.0 }
                it.nightMillis = night
                town = it
            }
        })
        if (store != null) game.slot = SaveSlot(Memory { store })
        game.talkSettleMillis = 0.0
        game.show()
    }

    /** Pins [town] to a phone's 375 by 812 points. */
    private fun pinToPhone(town: NeighbourhoodScene) {
        val element = town.element
        element.style.setProperty("inset", "auto")
        element.style.left = "0px"
        element.style.top = "0px"
        element.style.width = "375px"
        element.style.height = "812px"
    }

    /**
     * Plays seed 1011 from its room in [zone], with an exact guess, in a real town pinned to a phone's 375 by 812
     * points; its life clock stands still, a change of day takes [night] milliseconds, and [store] keeps the month.
     */
    private fun startInZone(zone: Int, night: Double = 300.0, store: FakeStore? = null): NeighbourhoodScene {
        openGame(night, store)
        passIntro(root)
        val first = FirstMinute(1011)
        val index = first.rooms.indexOfFirst { it.zone.number == zone }
        root.button("${index + 1}번 방").click()
        val input = root.querySelector("input") as HTMLInputElement
        input.value = "${first.rooms[index].totalMonthlyCost}"
        input.dispatchEvent(Event("input"))
        root.button("총비용 확인하기").click()
        root.button("시작하기").click()
        return assertNotNull(town).also { pinToPhone(it) }
    }

    /** A step that waits: its name, when it is ready, and what it does. */
    private class Step(val name: String, val ready: () -> Boolean, val run: () -> Unit)

    /** Runs each step once it is ready, checking every 20 ms, and fails a step that waits more than [patience] ms. */
    private fun play(vararg steps: Step, patience: Double = 15_000.0): Promise<Unit> = Promise { resolve, reject ->
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
                        Date.now() - started > patience -> reject(AssertionError("${step.name}: waited too long"))
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
    fun `a room in zone 2 starts the month at 내 방 in Burnaby, and each morning starts there`(): Promise<Unit> {
        val town = startInZone(2)
        val home = Neighbourhood.home(2)
        return play(
            Step("the first morning", { town.isStill }) {
                assertEquals(2, town.homeZone)
                assertEquals(home.door, town.characterSpot)
                assertEquals(2, WideVancouver.zoneAt(home.door))
                // 내 방's name floats over it on the screen, in front of the camera.
                val name = assertNotNull(town.screenPoint(home.labelAnchor), "내 방 is behind the camera")
                assertTrue(name.left in 0.0..1.0 && name.top in 0.0..1.0, "내 방's name at $name")
                assertEquals("지도: 내 방 근처", town.minimapElement.getAttribute("aria-label"))
                // Downtown is far off: 회사 is an arrow on the small map's edge.
                assertTrue(Place.OFFICE in town.minimapPointsTo, "${town.minimapPointsTo}")
                town.walkFor(TransitLines.stations[7].door)
            },
            Step("walked toward the station, the day ends", { town.tripMillis == null && town.isStill }) {
                assertTrue(distanceBetween(town.characterSpot, home.door) > 5.0, "still at ${town.characterSpot}")
                town.endDay(Place.CAFE)
            },
            Step("the next morning", { !town.isChangingDay }) {
                assertEquals(home.door, town.characterSpot)
            },
        )
    }

    @Test
    fun `a month kept from a room in zone 2 goes on at 내 방 in Burnaby`(): Promise<Unit> {
        val store = FakeStore()
        val first = startInZone(2, store = store)
        val home = Neighbourhood.home(2)
        var again: NeighbourhoodScene? = null
        return play(
            Step("the first morning, then a day at 내 방", { first.isStill }) {
                val name = root.querySelectorAll(".place-name").asList().map { it as HTMLElement }
                    .first { it.textContent == Place.HOME.label }
                name.click()
                (root.querySelector(".talk .spend") as HTMLElement).click()
            },
            Step("the next morning, kept; the page opens again and goes on", { !first.isChangingDay }) {
                val kept = assertNotNull(MonthRecord.decode(store.items[SaveSlot.KEY]), "nothing kept")
                assertTrue(kept.days.isNotEmpty(), "the day was not kept")
                first.close()
                town = null
                openGame(300.0, store)
                root.button(Continue.CONTINUE).click()
                again = assertNotNull(town, "no town on going on").also { pinToPhone(it) }
                assertTrue(again !== first, "the same town")
            },
            Step("the kept month's morning", { again!!.isStill }) {
                val town = again!!
                assertEquals(2, town.homeZone)
                assertEquals(home.door, town.characterSpot)
                assertEquals("지도: 내 방 근처", town.minimapElement.getAttribute("aria-label"))
            },
        )
    }

    @Test
    fun `the SkyTrain takes the character from Burnaby to downtown`(): Promise<Unit> {
        val town = startInZone(2)
        val from = TransitLines.stations.size - 2
        val to = 1
        var arrived = false
        town.onTravelled = { arrived = true }
        return play(
            Step("at the big park's station", { town.isStill }) {
                town.standAt(TransitLines.stations[from].door)
            },
            Step("board the train", { town.isStill }) {
                assertTrue(town.canTravel(Transport.SKYTRAIN, from, to))
                town.travel(Transport.SKYTRAIN, from, to)
                assertEquals(Transport.SKYTRAIN, town.travelling)
            },
            Step("out at the station by the tunnel's mouth", { arrived }) {
                assertNull(town.travelling)
                assertEquals(TransitLines.stations[to].door, town.characterSpot)
            },
            patience = 30_000.0,
        )
    }

    @Test
    fun `in Burnaby the small map follows the character`(): Promise<Unit> {
        val town = startInZone(3)
        val map = town.minimapElement
        val target = TransitLines.busLines[1].stops.last().wait
        return play(
            Step("at 내 방 in zone 3", { town.isStill }) {
                assertEquals(Neighbourhood.home(3).door, town.characterSpot)
                assertEquals(map.clientWidth / 2.0 to map.clientHeight / 2.0, town.minimapDot)
                town.walkFor(target)
            },
            Step("at the bus stop", { town.tripMillis == null && town.isStill }) {
                assertTrue(distanceBetween(town.characterSpot, target) < 0.5, "at ${town.characterSpot}")
                // The arrow stays in the middle; 내 방 is still on the small map, a few squares behind.
                assertEquals(map.clientWidth / 2.0 to map.clientHeight / 2.0, town.minimapDot)
                assertTrue(Place.HOME !in town.minimapPointsTo)
            },
        )
    }
}
