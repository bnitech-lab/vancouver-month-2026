package vancouvermonth.web

import kotlin.js.Date
import kotlin.js.Promise
import kotlin.math.PI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import vancouvermonth.text.Transport

/**
 * What a frame of the town costs on a phone, drawn for real on SwiftShader at a phone's size: downtown as it always
 * was, East Vancouver, Burnaby, on a walk and on the train. Each prints its draws, triangles, boxes and milliseconds
 * (SwiftShader draws on the computer's processor, so the milliseconds are for comparing, not a phone's), and keeps
 * within the budget: the far city left to the haze, never drawn all at once.
 */
class TownBudgetTest {
    private var town: NeighbourhoodScene? = null

    @AfterTest
    fun close() {
        town?.close()
        town?.element?.remove()
        town = null
    }

    /** A town for a room in [zone], 375 by 812 points, its life standing still. */
    private fun open(zone: Int): NeighbourhoodScene {
        val opened = assertNotNull(openWalkableTown(lessMotion = false, homeZone = zone), "No WebGL 2") as NeighbourhoodScene
        opened.lifeClock = { 0.0 }
        val element = opened.element
        element.style.setProperty("inset", "auto")
        element.style.left = "0px"
        element.style.top = "0px"
        element.style.width = "375px"
        element.style.height = "812px"
        document.body!!.appendChild(element)
        town = opened
        return opened
    }

    /** Runs [then] once [ready], checking every 20 ms, or fails after fifteen seconds. */
    private fun whenReady(ready: () -> Boolean, then: () -> Unit): Promise<Unit> = Promise { resolve, reject ->
        val started = Date.now()
        fun check() {
            try {
                when {
                    ready() -> {
                        then()
                        resolve(Unit)
                    }
                    Date.now() - started > 15_000 -> reject(AssertionError("Waited too long"))
                    else -> window.setTimeout({ check() }, 20)
                }
            } catch (error: Throwable) {
                reject(error)
            }
        }
        check()
    }

    private fun report(name: String, cost: FrameCost) {
        println("BUDGET $name: ${cost.draws} draws, ${cost.triangles} triangles, ${cost.boxes} boxes, ${cost.millis} ms")
    }

    /** Within the phone's budget: about what downtown always cost, and never the whole wide map at once. */
    private fun withinBudget(cost: FrameCost) {
        assertTrue(cost.draws <= MOST_DRAWS, "${cost.draws} draws")
        assertTrue(cost.boxes <= MOST_BOXES, "${cost.boxes} boxes")
    }

    @Test
    fun `downtown, at the town's 내 방 in the first view`(): Promise<Unit> {
        val town = open(1)
        return whenReady({ town.isStill }) {
            val cost = town.measureFrames(FRAMES)
            report("downtown", cost)
            withinBudget(cost)
        }
    }

    @Test
    fun `downtown at night, the lamps and windows lit, costs what the day does`(): Promise<Unit> {
        val town = open(1)
        town.showTime(1.0)
        return whenReady({ town.isStill }) {
            val cost = town.measureFrames(FRAMES)
            report("downtown at night", cost)
            withinBudget(cost)
            assertEquals(DayLight.NIGHT, town.lightShown)
        }
    }

    @Test
    fun `East Vancouver, at the station by the junction`(): Promise<Unit> {
        val town = open(1)
        town.standAt(TransitLines.stations[3].door)
        return whenReady({ town.isStill }) {
            val cost = town.measureFrames(FRAMES)
            report("East Vancouver", cost)
            withinBudget(cost)
        }
    }

    @Test
    fun `Burnaby, at 내 방 in zone 2 and at the town centre`(): Promise<Unit> {
        val town = open(2)
        return whenReady({ town.isStill }) {
            val atHome = town.measureFrames(FRAMES)
            report("Burnaby, 내 방 zone 2", atHome)
            withinBudget(atHome)
            town.standAt(TransitLines.stations[TransitLines.stations.lastIndex].door)
        }.then {
            whenReady({ town.isStill }) {
                val cost = town.measureFrames(FRAMES)
                report("Burnaby town centre", cost)
                withinBudget(cost)
            }
        }.then { }
    }

    @Test
    fun `downtown's east end, by the tunnel's station, looking east`(): Promise<Unit> {
        val town = open(1)
        town.standAt(TransitLines.stations[1].door)
        town.aimCamera(-PI / 2, 0.45, 21.0)
        return whenReady({ town.isStill }) {
            val cost = town.measureFrames(FRAMES)
            report("downtown's east end, looking east", cost)
            withinBudget(cost)
        }
    }

    @Test
    fun `on a walk downtown`(): Promise<Unit> {
        val town = open(1)
        return whenReady({ town.isStill }) {
            town.walkFor(Neighbourhood.venue(Place.CAFE).talkSpot)
        }.then {
            whenReady({ town.tripMillis != null }) {
                val cost = town.measureFrames(FRAMES)
                report("walking", cost)
                withinBudget(cost)
            }
        }.then { }
    }

    @Test
    fun `on the SkyTrain through East Vancouver`(): Promise<Unit> {
        val town = open(1)
        town.standAt(TransitLines.stations[1].door)
        var started = 0.0
        return whenReady({ town.isStill }) {
            town.travel(Transport.SKYTRAIN, 1, 6)
            started = Date.now()
        }.then {
            whenReady({ Date.now() - started > RIDE_MILLIS }) {
                assertTrue(town.travelling == Transport.SKYTRAIN, "the ride is over")
                val cost = town.measureFrames(FRAMES)
                report("riding the train", cost)
                withinBudget(cost)
            }
        }.then { }
    }

    private companion object {
        const val FRAMES = 4
        const val RIDE_MILLIS = 7_000.0
        /**
         * The busiest frame measured, downtown's east end looking east, takes 48 draws and 4,131 boxes; the budget is that
         * and about a sixth more, so a frame much dearer than today's fails.
         */
        const val MOST_DRAWS = 56
        const val MOST_BOXES = 4_800
    }
}
