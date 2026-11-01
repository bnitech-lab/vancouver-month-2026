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
 * was, East Vancouver, Burnaby, on a walk and on the train, and with the camera turning, so the near houses are written
 * again each frame. Each prints its draws, triangles, boxes and milliseconds, and the sun's shadows' draws and boxes
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
        println(
            "BUDGET $name: ${cost.draws} draws, ${cost.triangles} triangles, ${cost.boxes} boxes, ${cost.millis} ms; " +
                "shadows ${cost.shadowDraws} draws, ${cost.shadowBoxes} boxes; near houses ${town?.houseDetailBoxes}",
        )
    }

    /**
     * Within the phone's budget: about what downtown always cost, and never the whole wide map at once, for the picture
     * and with the sun's shadows counted too.
     */
    private fun withinBudget(cost: FrameCost) {
        assertTrue(cost.draws <= MOST_DRAWS, "${cost.draws} draws")
        assertTrue(cost.boxes <= MOST_BOXES, "${cost.boxes} boxes")
        assertTrue(cost.allDraws <= MOST_DRAWS_WITH_SHADOWS, "${cost.allDraws} draws with the shadows")
        assertTrue(cost.allBoxes <= MOST_BOXES_WITH_SHADOWS, "${cost.allBoxes} boxes with the shadows")
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
    fun `Burnaby at night, at zone 2's 내 방 among the houses, costs what the day does`(): Promise<Unit> {
        val town = open(2)
        town.showTime(1.0)
        return whenReady({ town.isStill }) {
            val cost = town.measureFrames(FRAMES)
            report("Burnaby at night, 내 방 zone 2", cost)
            withinBudget(cost)
            assertEquals(DayLight.NIGHT, town.lightShown)
            assertTrue(town.houseDetailBoxes > 0, "no houses drawn near 내 방 at night")
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
    fun `Burnaby, at 내 방 in zone 3`(): Promise<Unit> {
        val town = open(3)
        return whenReady({ town.isStill }) {
            val cost = town.measureFrames(FRAMES)
            report("Burnaby, 내 방 zone 3", cost)
            withinBudget(cost)
        }
    }

    @Test
    fun `East Vancouver, the camera turning round the character, each frame a moving one`(): Promise<Unit> {
        val town = open(1)
        town.standAt(TransitLines.stations[3].door)
        return whenReady({ town.isStill }) {
            val cost = town.measureFrames(FRAMES, turning = true)
            report("East Vancouver, turning", cost)
            withinBudget(cost)
        }
    }

    @Test
    fun `Burnaby, at 내 방 in zone 2, the camera turning round the character`(): Promise<Unit> {
        val town = open(2)
        return whenReady({ town.isStill }) {
            val cost = town.measureFrames(FRAMES, turning = true)
            report("Burnaby, 내 방 zone 2, turning", cost)
            withinBudget(cost)
        }
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

    @Test
    fun `under downtown, on the platform and in the tunnel, a few draws and no town`(): Promise<Unit> {
        val town = open(1)
        town.standAt(TransitLines.stations[0].door)
        var writes = 0
        // Its shaders and sign are readied ahead, while the town idles after its first frame.
        return whenReady({ town.isStill && town.isUndergroundReady }) {
            town.travel(Transport.SKYTRAIN, 0, 1)
        }.then {
            whenReady({ town.isUnderground && town.undergroundShown.isRiderShown }) {
                val cost = town.measureFrames(FRAMES)
                report("under downtown, on the platform", cost)
                withinBudget(cost)
                assertTrue(cost.draws <= UNDERGROUND_DRAWS, "${cost.draws} draws")
                writes = town.houseDetailWrites
            }
        }.then {
            // Out in the tunnel, or just short of it: the camera is there for most of a second, and a slow test browser
            // may draw only a few frames in that time.
            whenReady({ town.isUnderground && town.undergroundShown.eye.x > UndergroundRide.HALL_EAST - 2.0 }) {
                val cost = town.measureFrames(FRAMES)
                report("under downtown, in the tunnel", cost)
                withinBudget(cost)
                assertTrue(cost.draws <= UNDERGROUND_DRAWS, "${cost.draws} draws")
                // The town is not drawn down here, so its near houses were not written again as the camera moved.
                assertEquals(writes, town.houseDetailWrites, "the near houses were written under the ground")
            }
        }.then { }
    }

    private companion object {
        /** Under downtown only the platform and the tunnel are drawn: a handful of draws, the town none. */
        const val UNDERGROUND_DRAWS = 10
        const val FRAMES = 4
        const val RIDE_MILLIS = 7_000.0
        /**
         * The busiest frame measured, downtown's east end looking east, takes 48 draws and 4,131 boxes; the budget is that
         * and about a sixth more, so a frame much dearer than today's fails.
         */
        const val MOST_DRAWS = 56
        const val MOST_BOXES = 4_800

        /**
         * The same frame with the sun's shadows counted (Three.js counts afresh after drawing them, so the picture's
         * numbers leave them out) took 68 draws and 5,839 boxes on main at v0.16; the budget is that and about a sixth.
         */
        const val MOST_DRAWS_WITH_SHADOWS = 80
        const val MOST_BOXES_WITH_SHADOWS = 6_800
    }
}
