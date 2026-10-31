package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import vancouvermonth.text.Passer

class PassersbyTest {
    private val grid = Neighbourhood.grid

    /** Every door and every place to talk in the town. */
    private val doors = Neighbourhood.venues.flatMap { listOf(it.door, it.talkSpot) }

    /** Spots every [step] units round [stroll], once. */
    private fun along(stroll: Stroll, step: Double = 0.05): List<Spot> =
        (0..(stroll.length / step).toInt()).map { stroll.spotAt(it * step) }

    @Test
    fun `a stroll goes round its points and comes back to the first`() {
        val square = Stroll(listOf(Spot(0.0, 0.0), Spot(4.0, 0.0), Spot(4.0, 3.0), Spot(0.0, 3.0)))
        assertEquals(14.0, square.length, 1e-9)
        assertEquals(Spot(2.0, 0.0), square.spotAt(2.0))
        assertEquals(Spot(4.0, 1.0), square.spotAt(5.0))
        assertEquals(Spot(0.0, 1.0), square.spotAt(13.0))
        assertEquals(Spot(2.0, 0.0), square.spotAt(16.0), "the second time round")
        assertEquals(PI / 2, square.headingAt(1.0), 1e-9, "east")
        assertEquals(0.0, square.headingAt(5.0), 1e-9, "south")
        // Two points: there and back.
        val line = Stroll(listOf(Spot(0.0, 0.0), Spot(10.0, 0.0)))
        assertEquals(20.0, line.length, 1e-9)
        assertEquals(Spot(6.0, 0.0), line.spotAt(14.0))
        assertEquals(-PI / 2, line.headingAt(14.0), 1e-9, "west on the way back")
    }

    @Test
    fun `every passerby walks on free squares, inside the town, never through a building`() {
        for (passerby in Strolls.all()) {
            for ((a, b) in passerby.stroll.legs) {
                assertTrue(grid.isClear(a, b), "${passerby.passer}: $a to $b")
            }
            for (spot in along(passerby.stroll)) {
                assertTrue(grid.isFree(grid.cellAt(spot)), "${passerby.passer} at $spot")
                for (wall in Neighbourhood.walls.filter { it.box.y == 0.0 }) {
                    val box = wall.box
                    val inside = spot.x > box.x && spot.x < box.x + box.width && spot.z > box.z && spot.z < box.z + box.depth
                    assertFalse(inside, "${passerby.passer} at $spot is inside $box")
                }
            }
        }
    }

    @Test
    fun `no passerby ever comes near a door or a place to talk, so none stands where a place opens`() {
        for (passerby in Strolls.all()) {
            for (spot in along(passerby.stroll)) {
                for (door in doors) {
                    val away = distanceBetween(spot, door)
                    assertTrue(away > Neighbourhood.REACH + 0.1, "${passerby.passer} at $spot is $away from $door")
                }
                assertEquals(null, Neighbourhood.placeAt(spot), "${passerby.passer} at $spot")
            }
        }
    }

    @Test
    fun `the passersby walk the pavement, the park, the seawall and the plaza, and start far apart`() {
        val all = Strolls.all()
        assertEquals(Passer.entries.toSet(), all.map { it.passer }.toSet())
        val seawall = all.first { it.passer == Passer.OLIVER }
        assertTrue(along(seawall.stroll).all { it.z < 2.0 }, "on the seawall")
        val pavement = all.first { it.passer == Passer.MINA }
        assertTrue(along(pavement.stroll).any { it.z in 15.0..17.0 }, "on the south pavement")
        val park = all.first { it.passer == Passer.PRIYA }
        assertTrue(along(park.stroll).all { it.z > Neighbourhood.STREET_SOUTH + 1 }, "behind the park fence")
        val home = Neighbourhood.venue(Place.HOME).door
        for ((index, a) in all.withIndex()) {
            assertTrue(distanceBetween(a.spot, home) > 10.0, "${a.passer} starts near 내 방")
            for (b in all.drop(index + 1)) assertTrue(distanceBetween(a.spot, b.spot) > 3.0, "${a.passer}, ${b.passer}")
        }
    }

    @Test
    fun `the player's walks go straight through where the passersby are, since they are not in the grid`() {
        val home = Neighbourhood.venue(Place.HOME).door
        for (passerby in Strolls.all()) {
            val there = passerby.spot
            assertTrue(grid.isFree(grid.cellAt(there)), "${passerby.passer}")
            assertEquals(there, grid.route(home, there).last(), "${passerby.passer}")
        }
    }

    @Test
    fun `a passerby strolls at their speed, bobbing and swinging their legs`() {
        val passerby = Strolls.all().first()
        val far = Spot(-50.0, -50.0)
        val start = passerby.walked
        var pose = passerby.step(0.0, far)
        for (frame in 1..300) pose = passerby.step(33.0, far)
        assertEquals(start + 300 * 0.033 * passerby.speed, passerby.walked, 1e-6)
        assertFalse(passerby.isStanding)
        assertEquals(passerby.spot, pose.spot)
        val swings = (1..20).map { passerby.step(33.0, far).swing }
        assertTrue(swings.any { it > 0.1 } && swings.any { it < -0.1 }, "$swings")
        assertTrue(swings.all { abs(it) <= Passerby.STROLL_SWING })
    }

    @Test
    fun `a passerby stops for the player right in front, faces them, and walks on once they step aside`() {
        val passerby = Strolls.all().first { it.passer == Passer.MINA }
        val here = passerby.spot
        val ahead = passerby.stroll.headingAt(passerby.walked)
        val inFront = Spot(here.x + sin(ahead) * 1.0, here.z + cos(ahead) * 1.0)
        val walked = passerby.walked
        var pose = passerby.step(0.0, inFront)
        for (frame in 1..60) pose = passerby.step(33.0, inFront)
        assertTrue(passerby.isStanding)
        assertEquals(walked, passerby.walked, "walked on into the player")
        assertEquals(0.0, pose.swing)
        assertEquals(0.0, angleBetween(pose.heading, ahead), 0.02)
        // Behind them, or off to the side, the player is not in the way.
        val behind = Spot(here.x - sin(ahead) * 1.0, here.z - cos(ahead) * 1.0)
        assertFalse(passerby.isInTheWay(behind))
        passerby.step(33.0, behind)
        assertFalse(passerby.isStanding)
        assertTrue(passerby.walked > walked)
    }

    @Test
    fun `a passerby stopped for a word stands and faces the player until let go, then walks on`() {
        val passerby = Strolls.all().first { it.passer == Passer.THEO }
        val player = Spot(passerby.spot.x + 3.0, passerby.spot.z)
        passerby.hold()
        val walked = passerby.walked
        var pose = passerby.step(0.0, player)
        for (frame in 1..30) pose = passerby.step(33.0, player)
        assertTrue(passerby.isStanding)
        assertEquals(walked, passerby.walked)
        assertEquals(PI / 2, pose.heading, 0.02, "turned east, to the player")
        // However long the talk lasts.
        passerby.step(33.0, player)
        assertTrue(passerby.isStanding, "walked off while the talk was open")
        assertEquals(walked, passerby.walked)
        passerby.release()
        passerby.step(33.0, player)
        assertFalse(passerby.isStanding, "release() did not let them go")
        assertTrue(passerby.walked > walked)
    }

    @Test
    fun `the short way round between two turns`() {
        assertEquals(0.2, angleBetween(0.1, 0.3), 1e-9)
        assertEquals(-0.2, angleBetween(-PI + 0.1, PI - 0.1), 1e-9)
        assertEquals(0.2, angleBetween(PI - 0.1, -PI + 0.1), 1e-9)
        assertEquals(0.0, angleBetween(1.0, 1.0 + 4 * PI), 1e-9)
        assertEquals(1.0, turnToward(1.0, 2.0, 0.0), 1e-9, "no time, no turn")
        assertEquals(2.0, turnToward(1.0, 2.0, 10_000.0), 1e-6)
    }
}
