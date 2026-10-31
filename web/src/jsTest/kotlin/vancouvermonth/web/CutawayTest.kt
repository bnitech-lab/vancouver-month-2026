package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CutawayTest {
    /** A tower [height] tall, two units square, with its middle at [x], [z]. */
    private fun tower(x: Double, z: Double, height: Double = 10.0) = Block(x - 1.0, 0.0, z - 1.0, 2.0, height, 2.0, 0)

    /** A camera 20 units west of the character's head at the origin's east, 10 up, as a downtown look along x. */
    private val eye = Point(0.0, 10.0, 0.0)
    private val head = Point(20.0, 1.6, 0.0)

    private fun Cutaway.aimAt(eye: Point, head: Point) = aim(eye.x, eye.y, eye.z, head.x, head.y, head.z)

    /** Every point of the line from the eye to the head [along] of the way, 0.0 at the eye and 1.0 at the head. */
    private fun onLine(along: Double) = Point(
        eye.x + (head.x - eye.x) * along,
        eye.y + (head.y - eye.y) * along,
        eye.z + (head.z - eye.z) * along,
    )

    @Test
    fun `a building on the way from the camera to the character folds, and one well clear of it stands`() {
        val middle = onLine(0.5)
        val buildings = listOf(
            tower(middle.x, middle.z),
            // Beside the way, farther out than the ray is thick.
            tower(middle.x, middle.z + 1.0 + Cutaway.RAY_RADIUS + 0.4),
            // Beyond the character, which the camera sees past it.
            tower(head.x + 4.0, head.z),
            // Behind the camera, out of its sight.
            tower(eye.x - 8.0, eye.z),
        )
        val cutaway = Cutaway(buildings)
        cutaway.aimAt(eye, head)
        assertEquals(listOf(true, false, false, false), buildings.indices.map { cutaway.isFolding(it) })
    }

    @Test
    fun `a building just beside the way folds, as the ray is thick, but one beside the character itself stands`() {
        val middle = onLine(0.5)
        // Its side 1.2 from the line, within the ray's thickness, and lower than the line there by as much.
        val beside = Block(middle.x - 1.0, 0.0, middle.z + 1.2, 2.0, middle.y - 0.5, 2.0, 0)
        // Across a street from the character, 0.7 off and level with it, where the ray has narrowed to the head.
        val byTheCharacter = Block(head.x - 0.3, 0.0, head.z + 0.7, 2.0, 6.0, 2.0, 0)
        val cutaway = Cutaway(listOf(beside, byTheCharacter))
        cutaway.aimAt(eye, head)
        assertTrue(cutaway.isFolding(0), "the building beside the way stands")
        assertFalse(cutaway.isFolding(1), "the building beside the character folds")
    }

    @Test
    fun `a building that fills the near view in front of the camera folds`() {
        // 2.5 off the line, three units in front of the camera: in the near view, but not near the ray farther on.
        val near = onLine(3.0 / 21.8)
        val far = onLine(0.6)
        val buildings = listOf(
            Block(near.x - 1.0, 0.0, near.z + 2.5, 2.0, 12.0, 2.0, 0),
            Block(far.x - 1.0, 0.0, far.z + 2.5, 2.0, 12.0, 2.0, 0),
        )
        val cutaway = Cutaway(buildings)
        cutaway.aimAt(eye, head)
        assertTrue(cutaway.isFolding(0), "the building by the camera stands")
        assertFalse(cutaway.isFolding(1), "the building farther on folds")
    }

    @Test
    fun `the near view widens as the view does, so what stands beside or behind the camera, out of sight, stands`() {
        // 2.5 off the line, half a unit in front of the camera: beside it, out of the picture.
        val beside = onLine(0.5 / 21.8)
        // As far off the line behind the camera.
        val cutaway = Cutaway(listOf(
            Block(beside.x - 1.0, 0.0, beside.z + 2.5, 2.0, 12.0, 2.0, 0),
            Block(eye.x - 3.0, 0.0, eye.z + 2.5, 2.0, 12.0, 2.0, 0),
        ))
        cutaway.aimAt(eye, head)
        assertFalse(cutaway.isFolding(0), "the building beside the camera folds")
        assertFalse(cutaway.isFolding(1), "the building behind the camera folds")
    }

    @Test
    fun `brought in close, the camera has no wider near view to fold what stands round the character`() {
        val close = Point(head.x - 4.0, head.y + 2.5, head.z)
        // Beside the camera, 2.0 off the line: inside a near view, outside the ray.
        val cutaway = Cutaway(listOf(Block(close.x - 1.0, 0.0, head.z + 2.0, 2.0, 8.0, 2.0, 0)))
        cutaway.aimAt(close, head)
        assertFalse(cutaway.isFolding(0))
    }

    @Test
    fun `a folded building stays folded at the edge, and stands again only once clearly out of the way`() {
        val middle = onLine(0.5)
        // The tower's side is [off] from the line's middle, sideways.
        fun cameraWith(off: Double): Pair<Point, Point> {
            val shift = 1.0 + off
            return Point(eye.x, eye.y, eye.z - shift) to Point(head.x, head.y, head.z - shift)
        }
        val cutaway = Cutaway(listOf(tower(middle.x, middle.z, height = 30.0)))
        // Just outside the ray: it stands.
        val (outEye, outHead) = cameraWith(Cutaway.RAY_RADIUS + 0.2)
        cutaway.aimAt(outEye, outHead)
        assertFalse(cutaway.isFolding(0))
        // Straight through it: it folds.
        cutaway.aimAt(eye, head)
        assertTrue(cutaway.isFolding(0))
        // Back to just outside the ray, by less than the slack: it stays folded, with no flicker.
        cutaway.aimAt(outEye, outHead)
        assertTrue(cutaway.isFolding(0), "it stood again at the edge")
        // Farther out than the ray and the slack: it stands again.
        val (clearEye, clearHead) = cameraWith(Cutaway.RAY_RADIUS + Cutaway.SLACK + 0.2)
        cutaway.aimAt(clearEye, clearHead)
        assertFalse(cutaway.isFolding(0))
    }

    @Test
    fun `a building folds down to its plinth over a quarter of a second, and rises back the same way`() {
        val middle = onLine(0.5)
        val cutaway = Cutaway(listOf(tower(middle.x, middle.z, height = 12.0)))
        assertFalse(cutaway.step(16.0), "nothing moves before it is aimed")
        assertEquals(12.0, cutaway.heightOf(0))
        cutaway.aimAt(eye, head)
        assertTrue(cutaway.step(Cutaway.FOLD_MILLIS / 2))
        assertEquals(1, cutaway.changedCount)
        assertEquals(0, cutaway.changed[0])
        val halfway = cutaway.heightOf(0)
        assertTrue(halfway > Cutaway.PLINTH && halfway < 12.0, "halfway it is $halfway tall")
        assertFalse(cutaway.step(Cutaway.FOLD_MILLIS / 2))
        assertEquals(Cutaway.PLINTH, cutaway.heightOf(0), 1e-9)
        // Folded, nothing more changes.
        assertFalse(cutaway.step(16.0))
        assertEquals(0, cutaway.changedCount)
        // Out of the way, it rises.
        cutaway.aimAt(Point(eye.x, eye.y, eye.z + 10.0), Point(head.x, head.y, head.z + 10.0))
        assertTrue(cutaway.step(Cutaway.FOLD_MILLIS / 4))
        assertFalse(cutaway.step(Cutaway.FOLD_MILLIS))
        assertEquals(12.0, cutaway.heightOf(0), 1e-9)
    }

    @Test
    fun `a building turned back while folding rises from where it got to`() {
        val middle = onLine(0.5)
        val cutaway = Cutaway(listOf(tower(middle.x, middle.z, height = 12.0)))
        cutaway.aimAt(eye, head)
        cutaway.step(Cutaway.FOLD_MILLIS / 2)
        val got = cutaway.foldOf(0)
        cutaway.aimAt(Point(eye.x, eye.y, eye.z + 10.0), Point(head.x, head.y, head.z + 10.0))
        cutaway.step(10.0)
        assertTrue(cutaway.foldOf(0) < got && cutaway.foldOf(0) > 0.0, "${cutaway.foldOf(0)} after $got")
    }

    @Test
    fun `only the city's buildings and the blocks past the edge fold, never the places, the train's or the garden`() {
        val foldables = Neighbourhood.foldables
        assertTrue(foldables.size > 300, "only ${foldables.size}")
        // One for each building of the city, and the rest past the grid's edges.
        assertTrue(foldables.size >= Neighbourhood.cityLots.size)
        for (building in foldables) {
            val inside = building.box.x >= 0 && building.box.x < Neighbourhood.COLUMNS &&
                building.box.z >= 0 && building.box.z < Neighbourhood.ROWS
            val corner = Spot(building.box.x + 0.2, building.box.z + 0.2)
            if (inside) assertTrue(Neighbourhood.cityLots.any { it.isNear(corner.x, corner.z, 0.0) }, "${building.box}")
        }
        // Nothing that matters is among them: none overlaps a wall the camera keeps out of, or a place's lot.
        val important = Neighbourhood.walls.filter { !it.folds }
        assertTrue(important.mapNotNull { it.place }.toSet() == Place.entries.toSet())
        for (building in foldables) {
            val box = building.box
            for (wall in important) {
                val other = wall.box
                val apart = box.x + box.width <= other.x || other.x + other.width <= box.x ||
                    box.z + box.depth <= other.z || other.z + other.depth <= box.z ||
                    box.y + box.height <= other.y || other.y + other.height <= box.y
                assertTrue(apart, "$box overlaps ${wall.place ?: other}")
            }
        }
        // Each one's boxes are where it says, as tall as its walls.
        for (building in foldables) {
            val body = Neighbourhood.blocks[building.body]
            assertEquals(building.box.x, body.x)
            assertEquals(building.box.z, body.z)
            val roof = if (building.roof < 0) 0.0 else Neighbourhood.blocks[building.roof].height
            assertEquals(building.box.height, body.height + roof, 1e-9)
            if (building.glassNorth < 0) continue
            assertEquals(body.height - 1.6, Neighbourhood.glowing[building.glassNorth].height, 1e-9)
            assertEquals(building.glassNorth + 1, building.glassSouth)
        }
    }

    @Test
    fun `wherever the character stands and however the camera turns, no city building it looks through stands`() {
        val grid = Neighbourhood.grid
        val spots = grid.freeCells().filterIndexed { index, _ -> index % 23 == 0 }.map { grid.centreOf(it) }
        val boxes = Neighbourhood.foldables.map { it.box }
        val cutaway = Cutaway(boxes)
        var folded = 0
        for (spot in spots) {
            for (yaw in listOf(FollowCamera.START_YAW, 0.0, PI / 2, PI, -PI / 2)) {
                for (pitch in listOf(FollowCamera.MIN_PITCH, FollowCamera.START_PITCH, FollowCamera.MAX_PITCH)) {
                    // The camera where the player put it at the first view's distance: nothing of the city's moves it.
                    val reach = FollowCamera.START_DISTANCE
                    val eye = Point(
                        spot.x + cos(pitch) * sin(yaw) * reach,
                        1.0 + sin(pitch) * reach,
                        spot.z + cos(pitch) * cos(yaw) * reach,
                    )
                    val head = Point(spot.x, 1.6, spot.z)
                    cutaway.aimAt(eye, head)
                    val toHead = Point(head.x - eye.x, head.y - eye.y, head.z - eye.z)
                    val length = sqrt(toHead.x * toHead.x + toHead.y * toHead.y + toHead.z * toHead.z)
                    val way = Point(toHead.x / length, toHead.y / length, toHead.z / length)
                    for ((index, box) in boxes.withIndex()) {
                        val hit = distanceToBox(eye, way, box) ?: continue
                        if (hit >= length) continue
                        folded++
                        assertTrue(cutaway.isFolding(index), "$spot, turn $yaw, tilt $pitch: $box stands between")
                    }
                }
            }
        }
        assertTrue(folded > 1_000, "only $folded buildings were ever in the way")
    }

    @Test
    fun `the ray widens over the near view, then is as thick as asked, and narrows to the head`() {
        // At the eye as thick as the ray, widening with the view to the near view's widest.
        assertEquals(Cutaway.RAY_RADIUS, Cutaway.radiusAt(0.0, 21.0))
        assertEquals(2.0 * Cutaway.NEAR_SPREAD, Cutaway.radiusAt(2.0, 21.0), 1e-9)
        assertEquals(Cutaway.NEAR_RADIUS, Cutaway.radiusAt(Cutaway.NEAR_LENGTH, 21.0))
        assertEquals(Cutaway.RAY_RADIUS, Cutaway.radiusAt(Cutaway.NEAR_LENGTH + 0.5, 21.0))
        assertEquals(Cutaway.RAY_RADIUS, Cutaway.radiusAt(21.0 - Cutaway.HEAD_TAPER, 21.0), 1e-9)
        assertEquals(Cutaway.HEAD_RADIUS, Cutaway.radiusAt(21.0, 21.0))
        // Close in, no near view at all.
        assertEquals(Cutaway.RAY_RADIUS, Cutaway.radiusAt(0.0, 4.5))
        // A camera about five and a half units off, following the character a little behind, never folds and raises
        // what stands just beside it as the distance wavers: the near view starts from the ray's own thickness.
        val besideEye = Block(-0.5, 0.0, 2.0, 1.0, 12.0, 1.0, 0)
        var length = 5.0
        while (length < 6.5) {
            val cutaway = Cutaway(listOf(besideEye))
            cutaway.aim(0.0, 3.0, 0.0, length, 3.0, 0.0)
            assertFalse(cutaway.isFolding(0), "at $length the building beside the camera folds")
            length += 0.01
        }
    }
}
