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
    private val casterNumbers = Float32Array(HouseDetail.CASTER_CAPACITY * 16)
    private val casterColours = Float32Array(HouseDetail.CASTER_CAPACITY * 3)
    private val numbers = Float32Array(HouseDetail.CAPACITY * 16)
    private val colours = Float32Array(HouseDetail.CAPACITY * 3)

    /** A camera's tan of half its view across, on a 375 by 812 phone, and up and down at 60 degrees. */
    private val tanUp = tan(PI / 6)
    private val tanAcross = tanUp * 375.0 / 812

    /** The camera [FollowCamera.START_DISTANCE] back from [focus], turned to [yaw] and tilted as at the first view. */
    private fun eyeFor(focus: Spot, yaw: Double): Point {
        val reach = FollowCamera.START_DISTANCE
        val pitch = FollowCamera.START_PITCH
        return Point(focus.x + cos(pitch) * sin(yaw) * reach, 1.0 + sin(pitch) * reach, focus.z + cos(pitch) * cos(yaw) * reach)
    }

    private fun detailOf(yards: List<Yard>) = HouseDetail(yards, casterNumbers, casterColours, numbers, colours)

    private fun HouseDetail.fillFor(eye: Point, focus: Spot, foldOf: (Int) -> Double = { 0.0 }) =
        fill(eye.x, eye.y, eye.z, focus.x, 1.0, focus.z, tanAcross, tanUp, foldOf)

    /** One box the last fill wrote: its sixteen numbers, its colour, and whether it is in the draw that casts shadows. */
    private class Box(val numbers: DoubleArray, val colour: Int, val casts: Boolean) {
        val tipped: Boolean
            get() = abs(numbers[1]) > 1e-6
        /** How wide it is along its own width, on the ground. */
        val across: Double
            get() = hypot(numbers[0], numbers[2])
        val height: Double
            get() = numbers[5]
    }

    /** Every box the last fill wrote: those round the character first, then the others. */
    private fun boxes(detail: HouseDetail): List<Box> = listOf(detail.casters to casterNumbers, detail.others to numbers)
        .flatMap { (draw, array) ->
            (0 until draw.count).map { box ->
                Box(DoubleArray(16) { array[box * 16 + it].toDouble() }, draw.colours[box], draw === detail.casters)
            }
        }

    /** How many of the boxes written are tipped, as only a roof is. */
    private fun roofs(detail: HouseDetail): Int = boxes(detail).count { it.tipped }

    /** A block of [count] houses in a row at [x], [z], straight on the grid, 1.2 wide with 1.5 between. */
    private fun yardAt(x: Double, z: Double, count: Int = 2, foldable: Int = 0): Yard {
        val houses = (0 until count).map {
            House(TurnedBox(x - (count - 1) * 1.35 + it * 2.7, z, 1.2, 1.3, 0.0), 1.2, 0xEEEEEE, 0x5E6B78, 1.0, 0.0, foldable = foldable + it)
        }
        return Yard(TurnedBox(x, z, count * 2.7, 2.0, 0.0), Block(x - count * 1.35, 0.0, z - 1.0, count * 2.7, 0.035, 2.0, 0xA4CC74), emptyList(), houses)
    }

    /** The camera [away] east of [spot], looking west at the character standing 21 squares in front of it. */
    private fun HouseDetail.fillEastOf(spot: Spot, away: Double) =
        fillFor(Point(spot.x + away, 8.0, spot.z), Spot(spot.x + away - FollowCamera.START_DISTANCE, spot.z))

    @Test
    fun `near the camera and in its view, a block's houses stand one by one, with roofs and doors`() {
        val focus = Neighbourhood.home(2).door
        val eye = eyeFor(focus, FollowCamera.START_YAW)
        val detail = detailOf(Neighbourhood.yards)
        detail.fillFor(eye, focus)
        assertTrue(detail.count in 100 until HouseDetail.CAPACITY, "${detail.count} boxes")
        val shown = Neighbourhood.yards.filterIndexed { index, _ -> detail.shown[index] }
        assertTrue(shown.size > 10, "only ${shown.size} blocks near zone 2's 내 방")
        // Each shown block is within reach, and in front of the camera, or round the character.
        val ahead = Spot(focus.x - eye.x, focus.z - eye.z)
        for (yard in shown) {
            val away = hypot(yard.middle.x - eye.x, yard.middle.z - eye.z) - yard.reach
            assertTrue(away <= HouseDetail.REACH, "${yard.middle} is $away away")
            if (distanceBetween(yard.middle, focus) - yard.reach <= HouseDetail.SHADOW_NEAR) continue
            val toward = (yard.middle.x - eye.x) * ahead.x + (yard.middle.z - eye.z) * ahead.z
            assertTrue(toward > -yard.reach * hypot(ahead.x, ahead.z), "${yard.middle} is behind the camera")
        }
        assertTrue(shown.size < Neighbourhood.yards.size / 4)
        // Every block round the character is drawn one by one, in view or not, into the draw that casts shadows.
        val round = Neighbourhood.yards.indices.filter {
            distanceBetween(Neighbourhood.yards[it].middle, focus) - Neighbourhood.yards[it].reach <= HouseDetail.SHADOW_NEAR
        }
        assertTrue(round.isNotEmpty() && round.all { detail.shown[it] })
        assertTrue(detail.casters.count > 0 && detail.casters.count < detail.count)
        // A roof for each house in those blocks but the ones at the very edge of reach, which have not grown on yet.
        val houses = shown.sumOf { it.houses.size }
        assertTrue(roofs(detail) in houses * 3 / 4..houses, "${roofs(detail)} roofs for $houses houses")
        assertEquals(detail.changedCount, shown.size)
        // Turned the other way round, other blocks: those first shown go, and others come.
        detail.fillFor(eyeFor(focus, FollowCamera.START_YAW + PI), focus)
        assertTrue(detail.changedCount > (shown.size - round.size) / 2)
    }

    @Test
    fun `while the same blocks are drawn their colours are not written again, and each takes the same boxes`() {
        val focus = Neighbourhood.home(3).door
        val detail = detailOf(Neighbourhood.yards)
        detail.fillFor(eyeFor(focus, FollowCamera.START_YAW), focus)
        assertEquals(0, detail.others.coloursFrom)
        assertEquals(0, detail.casters.coloursFrom)
        val first = detail.count
        // Written again for the same camera, as for a house folding: the same blocks, so the same boxes, no colour to send.
        detail.fillFor(eyeFor(focus, FollowCamera.START_YAW), focus) { if (it == Neighbourhood.houses[0].foldable) 0.5 else 0.0 }
        assertEquals(first, detail.count)
        assertEquals(detail.others.count, detail.others.coloursFrom)
        assertEquals(detail.casters.count, detail.casters.coloursFrom)
        // Turned the other way, other blocks: their colours are written, and they are right.
        detail.fillFor(eyeFor(focus, FollowCamera.START_YAW + PI), focus)
        assertTrue(detail.others.coloursFrom < detail.others.count)
        val drawn = Neighbourhood.yards.filterIndexed { index, _ -> detail.shown[index] }
        val expected = drawn.flatMap { yard -> yard.houses.map { it.colour } }.toSet()
        assertTrue(boxes(detail).map { it.colour }.toSet().containsAll(expected))
        // Every house takes three boxes, a roof or door not yet grown at no size.
        assertTrue(boxes(detail).count { it.colour == 0x6B4630 } == drawn.sumOf { it.houses.size })
    }

    @Test
    fun `a roof grows on over the last stretch of reach, so nothing pops, and nothing is drawn past it`() {
        val yards = listOf(yardAt(500.0, 50.0, count = 1))
        val detail = detailOf(yards)
        val house = yards[0].houses[0]
        val spot = Spot(house.shape.x, house.shape.z)
        val full = (house.shape.width + 2 * Suburbs.EAVE) / kotlin.math.sqrt(2.0)
        // The camera east of the house, looking toward it, close by and halfway into the stretch.
        for ((away, share) in listOf(20.0 to 1.0, HouseDetail.REACH - HouseDetail.GROW / 2 to 0.5)) {
            detail.fillEastOf(spot, away)
            val roof = boxes(detail).first { it.tipped }
            assertEquals(full * share, hypot(roof.numbers[0], roof.numbers[1]), 1e-3, "at $away")
            assertTrue(detail.shown[0])
        }
        detail.fillEastOf(spot, HouseDetail.REACH + 5)
        assertFalse(detail.shown[0])
        // Past reach only the lawn, as far as the lawns go.
        assertEquals(1, detail.count)
        assertEquals(0, roofs(detail))
        detail.fillEastOf(spot, HouseDetail.LAWN_REACH + 5)
        assertEquals(0, detail.count)
    }

    @Test
    fun `at the edge of reach a row's houses are its low box, and coming near they grow into themselves`() {
        val yard = Neighbourhood.yards.first { yard -> yard.rows == 1 && yard.houses.size == 2 && yard.houses.all { it.slotShift != 0.0 } }
        val row = Neighbourhood.blocks[yard.firstRow]
        val detail = detailOf(listOf(yard))
        // Looking across the row from just inside reach: each house's walls are its share, the shares as wide as the box.
        val turn = yard.houses[0].shape.turn
        val far = HouseDetail.REACH - 0.01
        val eye = Point(yard.middle.x + sin(turn) * far, 8.0, yard.middle.z + cos(turn) * far)
        detail.fillFor(eye, yard.middle)
        assertTrue(detail.shown[0])
        val walls = boxes(detail).filter { abs(it.height - 0.035) > 1e-3 && !it.tipped && it.colour == yard.houses[0].colour }
        assertEquals(2, walls.size)
        assertEquals(row.width, walls.sumOf { it.across }, 0.01)
        for (box in walls) assertEquals(row.height, box.height, 0.01)
        // Close by, each is its own width, the yard between them open.
        detail.fillFor(Point(yard.middle.x + sin(turn) * 20.0, 8.0, yard.middle.z + cos(turn) * 20.0), yard.middle)
        val near = boxes(detail).filter { !it.tipped && it.colour == yard.houses[0].colour }
        for (box in near) assertEquals(yard.houses[0].shape.width, box.across, 1e-3)
    }

    @Test
    fun `a block drawn one by one stays so a little past reach, so one at the edge does not flicker`() {
        val yards = listOf(yardAt(500.0, 50.0, count = 1))
        val house = yards[0].houses[0]
        val spot = Spot(house.shape.x, house.shape.z)
        val edge = HouseDetail.REACH + yards[0].reach + HouseDetail.HOLD / 2
        val detail = detailOf(yards)
        // Coming from near, it is still drawn just past reach; coming from far, not yet.
        detail.fillEastOf(spot, 30.0)
        assertTrue(detail.shown[0])
        detail.fillEastOf(spot, edge)
        assertTrue(detail.shown[0], "dropped at the edge")
        detail.fillEastOf(spot, edge + HouseDetail.HOLD)
        assertFalse(detail.shown[0])
        detail.fillEastOf(spot, edge)
        assertFalse(detail.shown[0], "drawn again at the edge")
    }

    @Test
    fun `a folded house has no roof, and its walls stand at the plinth`() {
        val yards = listOf(yardAt(500.0, 50.0, count = 2, foldable = 10))
        val detail = detailOf(yards)
        val eye = Point(510.0, 8.0, 50.0)
        // The first house folded all the way, the second standing.
        detail.fillFor(eye, Spot(500.0, 50.0)) { if (it == 10) 1.0 else 0.0 }
        assertEquals(1, roofs(detail))
        val heights = boxes(detail).filter { !it.tipped }.map { it.height }
        assertTrue(heights.any { abs(it - Cutaway.PLINTH) < 1e-6 }, "$heights")
        assertTrue(heights.any { abs(it - 1.2) < 1e-6 }, "$heights")
    }

    @Test
    fun `with too many blocks in view the farthest go without, never more boxes than it holds`() {
        // Rows of blocks reaching away from a camera looking along them, far more than it holds, listed farthest first.
        val yards = (0 until 40).reversed().flatMap { row ->
            (0 until 20).map { across -> yardAt(500.0 + row * 1.8, 30.0 + across * 2.2, count = 4) }
        }
        val detail = detailOf(yards)
        detail.fillFor(Point(470.0, 12.0, 52.0), Spot(491.0, 52.0))
        assertTrue(detail.others.count <= HouseDetail.CAPACITY && detail.casters.count <= HouseDetail.CASTER_CAPACITY)
        assertTrue(detail.others.count > HouseDetail.CAPACITY - 17, "${detail.others.count}")
        // Along the middle of the view, those drawn are all nearer than those within reach left out.
        val middle = yards.indices.filter { abs(yards[it].middle.z - 52.0) < 2.0 && yards[it].middle.x - 470.0 < HouseDetail.REACH }
        val (drawn, left) = middle.partition { detail.shown[it] }
        assertTrue(drawn.isNotEmpty() && left.isNotEmpty(), "${drawn.size} drawn, ${left.size} left out")
        assertTrue(drawn.maxOf { yards[it].middle.x } < left.minOf { yards[it].middle.x }, "a nearer one left out")
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
