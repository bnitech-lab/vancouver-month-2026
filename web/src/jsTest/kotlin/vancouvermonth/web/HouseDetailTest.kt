package vancouvermonth.web

import kotlin.js.Promise
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get

/**
 * The suburbs' houses drawn one by one near the camera only: in its view and within reach, with roofs that grow on as
 * the camera comes near and sink as a house folds, the farthest going without when there are too many; and in the
 * town, the low rows of the blocks drawn one by one put away.
 */
class HouseDetailTest {
    private val numbers = Float32Array(HouseDetail.CAPACITY * 16)

    /** A camera's tan of half its view across, on a 375 by 812 phone, and up and down at 60 degrees. */
    private val tanUp = tan(PI / 6)
    private val tanAcross = tanUp * 375.0 / 812

    /** The camera [FollowCamera.START_DISTANCE] back from [focus], turned to [yaw] and tilted as at the first view. */
    private fun eyeFor(focus: Spot, yaw: Double): Point {
        val reach = FollowCamera.START_DISTANCE
        val pitch = FollowCamera.START_PITCH
        return Point(focus.x + cos(pitch) * sin(yaw) * reach, 1.0 + sin(pitch) * reach, focus.z + cos(pitch) * cos(yaw) * reach)
    }

    private fun HouseDetail.fillFor(eye: Point, focus: Spot, foldOf: (Int) -> Double = { 0.0 }) =
        fill(eye.x, eye.y, eye.z, focus.x, 1.0, focus.z, tanAcross, tanUp, foldOf)

    /** How many of the boxes written are tipped, as only a roof is. */
    private fun roofs(detail: HouseDetail): Int = (0 until detail.count).count { abs(numbers[it * 16 + 1]) > 1e-6 }

    /** A block of [count] houses in a row at [x], [z], straight on the grid, 1.2 wide with 1.5 between. */
    private fun yardAt(x: Double, z: Double, count: Int = 2, foldable: Int = 0): Yard {
        val houses = (0 until count).map {
            House(TurnedBox(x - (count - 1) * 1.35 + it * 2.7, z, 1.2, 1.3, 0.0), 1.2, 0xEEEEEE, 0x5E6B78, 1.0, 0.0, foldable = foldable + it)
        }
        return Yard(TurnedBox(x, z, count * 2.7, 2.0, 0.0), Block(x - count * 1.35, 0.0, z - 1.0, count * 2.7, 0.035, 2.0, 0xA4CC74), emptyList(), houses)
    }

    @Test
    fun `near the camera and in its view, a block's houses stand one by one, with roofs and doors`() {
        val focus = Neighbourhood.home(2).door
        val eye = eyeFor(focus, FollowCamera.START_YAW)
        val detail = HouseDetail(Neighbourhood.yards, numbers)
        detail.fillFor(eye, focus)
        assertTrue(detail.count in 100 until HouseDetail.CAPACITY, "${detail.count} boxes")
        val shown = Neighbourhood.yards.filterIndexed { index, _ -> detail.shown[index] }
        assertTrue(shown.size > 10, "only ${shown.size} blocks near zone 2's 내 방")
        // Each shown block is within reach, and in front of the camera; one well behind it never is.
        val ahead = Spot(focus.x - eye.x, focus.z - eye.z)
        for (yard in shown) {
            val away = hypot(yard.middle.x - eye.x, yard.middle.z - eye.z) - yard.reach
            assertTrue(away <= HouseDetail.REACH, "${yard.middle} is $away away")
            val toward = (yard.middle.x - eye.x) * ahead.x + (yard.middle.z - eye.z) * ahead.z
            assertTrue(toward > -yard.reach * hypot(ahead.x, ahead.z), "${yard.middle} is behind the camera")
        }
        assertTrue(shown.size < Neighbourhood.yards.size / 4)
        // A roof for each house in those blocks but the ones at the very edge of reach, which have not grown on yet.
        val houses = shown.sumOf { it.houses.size }
        assertTrue(roofs(detail) in houses * 3 / 4..houses, "${roofs(detail)} roofs for $houses houses")
        assertEquals(detail.changedCount, shown.size)
        // Turned the other way round, other blocks: those first shown go, and others come.
        detail.fillFor(eyeFor(focus, FollowCamera.START_YAW + PI), focus)
        assertTrue(detail.changedCount > shown.size / 2)
    }

    @Test
    fun `a roof grows on over the last stretch of reach, so nothing pops, and nothing is drawn past it`() {
        val yards = listOf(yardAt(500.0, 50.0, count = 1))
        val detail = HouseDetail(yards, numbers)
        val house = yards[0].houses[0]
        val full = (house.shape.width + 2 * Suburbs.EAVE) / kotlin.math.sqrt(2.0)
        // The camera east of the house, looking at it, close by, halfway into the stretch, and past reach.
        for ((away, share) in listOf(20.0 to 1.0, HouseDetail.REACH - HouseDetail.GROW / 2 to 0.5)) {
            val eye = Point(house.shape.x + away, 8.0, house.shape.z)
            detail.fillFor(eye, Spot(house.shape.x, house.shape.z))
            val roof = (0 until detail.count).first { abs(numbers[it * 16 + 1]) > 1e-6 }
            val size = hypot(numbers[roof * 16].toDouble(), numbers[roof * 16 + 1].toDouble())
            assertEquals(full * share, size, 1e-3, "at $away")
            assertTrue(detail.shown[0])
        }
        detail.fillFor(Point(house.shape.x + HouseDetail.REACH + 5, 8.0, house.shape.z), Spot(house.shape.x, house.shape.z))
        assertFalse(detail.shown[0])
        // Past reach only the lawn, as far as the lawns go.
        assertEquals(1, detail.count)
        assertEquals(0, roofs(detail))
        detail.fillFor(Point(house.shape.x + HouseDetail.LAWN_REACH + 5, 8.0, house.shape.z), Spot(house.shape.x, house.shape.z))
        assertEquals(0, detail.count)
    }

    @Test
    fun `a folded house has no roof, and its walls stand at the plinth`() {
        val yards = listOf(yardAt(500.0, 50.0, count = 2, foldable = 10))
        val detail = HouseDetail(yards, numbers)
        val eye = Point(510.0, 8.0, 50.0)
        // The first house folded all the way, the second standing.
        detail.fillFor(eye, Spot(500.0, 50.0)) { if (it == 10) 1.0 else 0.0 }
        assertEquals(1, roofs(detail))
        val heights = (0 until detail.count).filter { abs(numbers[it * 16 + 1]) < 1e-6 }.map { numbers[it * 16 + 5].toDouble() }
        assertTrue(heights.any { abs(it - Cutaway.PLINTH) < 1e-6 }, "$heights")
        assertTrue(heights.any { abs(it - 1.2) < 1e-6 }, "$heights")
    }

    @Test
    fun `with too many blocks in view the farthest go without, never more boxes than it holds`() {
        // Rows of blocks reaching away from a camera looking along them: far more than it holds.
        val yards = (0 until 40).flatMap { row -> (0 until 12).map { across -> yardAt(500.0 + row * 1.8, 40.0 + across * 2.2, count = 3) } }
        val detail = HouseDetail(yards, numbers)
        detail.fillFor(Point(470.0, 12.0, 52.0), Spot(520.0, 52.0))
        assertTrue(detail.count <= HouseDetail.CAPACITY)
        assertTrue(detail.count > HouseDetail.CAPACITY - 12, "${detail.count}")
        // Along the middle of the view: the near ones all drawn, and some within reach left out.
        val middle = yards.indices.filter { abs(yards[it].middle.z - 52.0) < 2.0 }
        assertTrue(middle.filter { yards[it].middle.x < 507.0 }.all { detail.shown[it] })
        assertTrue(middle.any { !detail.shown[it] && yards[it].middle.x - 470.0 < HouseDetail.REACH })
    }

    private var town: NeighbourhoodScene? = null

    @AfterTest
    fun close() {
        town?.close()
        town?.element?.remove()
        town = null
    }

    @Test
    fun `at 내 방 in zone 3 the houses round it are drawn one by one, and their blocks' low rows are put away`(): Promise<Unit> {
        val opened = assertNotNull(openWalkableTown(lessMotion = false, homeZone = 3), "No WebGL 2") as NeighbourhoodScene
        opened.lifeClock = { 0.0 }
        val element = opened.element
        element.style.setProperty("inset", "auto")
        element.style.left = "0px"
        element.style.top = "0px"
        element.style.width = "375px"
        element.style.height = "812px"
        document.body!!.appendChild(element)
        town = opened
        return Promise { resolve, reject ->
            fun check() {
                try {
                    if (!opened.isStill) {
                        window.setTimeout({ check() }, 20)
                        return
                    }
                    opened.measureFrames(1)
                    assertTrue(opened.houseDetailBoxes > 50, "${opened.houseDetailBoxes} boxes near 내 방")
                    var putAway = 0
                    var standing = 0
                    for ((index, yard) in Neighbourhood.yards.withIndex()) {
                        for (row in yard.firstRow until yard.firstRow + yard.rows) {
                            val height = opened.drawnHeight(row)
                            if (opened.isHouseDrawn(index)) {
                                assertEquals(0.0, height, 1e-9)
                                putAway++
                            } else {
                                assertEquals(Neighbourhood.blocks[row].height, height, 1e-4)
                                standing++
                            }
                        }
                    }
                    assertTrue(putAway > 10 && standing > putAway, "$putAway put away, $standing standing")
                    resolve(Unit)
                } catch (error: Throwable) {
                    reject(error)
                }
            }
            check()
        }
    }
}
