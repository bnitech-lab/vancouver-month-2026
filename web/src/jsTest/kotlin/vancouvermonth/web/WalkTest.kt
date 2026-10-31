package vancouvermonth.web

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalkTest {
    private fun door(place: Place) = TownMap.building(place).door

    @Test
    fun `a walk leaves one door, follows the pavement, and ends at the other door`() {
        val walk = Walk(Place.HOME, Place.OFFICE)
        assertEquals(door(Place.HOME), walk.path.first())
        assertEquals(door(Place.OFFICE), walk.path.last())
        assertTrue(walk.path.drop(1).dropLast(1).all { it.z == TownMap.PAVEMENT_Z }, "${walk.path}")
        // Each leg runs straight east to west or north to south, so no leg cuts through a building.
        for ((a, b) in walk.path.zipWithNext()) assertTrue(a.x == b.x || a.z == b.z, "$a to $b")
    }

    @Test
    fun `progress 0 is the start, 1 is the end, and halfway is halfway along the pavement`() {
        val walk = Walk(Place.HOME, Place.OFFICE)
        assertEquals(door(Place.HOME), walk.spotAt(0.0))
        assertEquals(door(Place.OFFICE), walk.spotAt(1.0))
        assertEquals(door(Place.HOME), walk.spotAt(-0.5))
        assertEquals(door(Place.OFFICE), walk.spotAt(1.5))
        val halfway = walk.spotAt(0.5)
        assertEquals((door(Place.HOME).x + door(Place.OFFICE).x) / 2, halfway.x, 1e-9)
        assertEquals(TownMap.PAVEMENT_Z, halfway.z, 1e-9)
    }

    @Test
    fun `a walk to the building the character is at is a hop on the spot`() {
        val hop = Walk(Place.CAFE, Place.CAFE)
        assertEquals(0.0, hop.length)
        assertEquals(door(Place.CAFE), hop.spotAt(0.5))
        assertTrue(hop.liftAt(0.5) > 0.0)
        assertEquals(0.0, hop.headingAt(0.5))
    }

    @Test
    fun `every walk takes under a second, and a longer walk never takes less time`() {
        val walks = Place.entries.flatMap { from -> Place.entries.map { to -> Walk(from, to) } }
        for (walk in walks) assertTrue(walk.millis < 1_000.0, "${walk.from} to ${walk.to}: ${walk.millis}")
        val byLength = walks.sortedBy { it.length }
        for ((shorter, longer) in byLength.zipWithNext()) assertTrue(shorter.millis <= longer.millis)
    }

    @Test
    fun `progress starts at 0, rises without going back, and stays at 1`() {
        val walk = Walk(Place.CAFE, Place.COWORKING)
        assertEquals(0.0, walk.progressAfter(0.0))
        assertEquals(0.0, walk.progressAfter(-50.0))
        assertEquals(0.5, walk.progressAfter(walk.millis / 2), 1e-9)
        assertEquals(1.0, walk.progressAfter(walk.millis))
        assertEquals(1.0, walk.progressAfter(walk.millis * 3))
        val samples = (0..20).map { walk.progressAfter(walk.millis * it / 20) }
        for ((earlier, later) in samples.zipWithNext()) assertTrue(earlier <= later)
    }

    @Test
    fun `the character faces the way it walks`() {
        // Out of the door it walks south, toward the viewer; along the pavement east or west.
        assertEquals(0.0, Walk(Place.HOME, Place.OFFICE).headingAt(0.001), 1e-9)
        assertEquals(PI / 2, Walk(Place.HOME, Place.OFFICE).headingAt(0.5), 1e-9)
        assertEquals(-PI / 2, Walk(Place.OFFICE, Place.HOME).headingAt(0.5), 1e-9)
    }

    @Test
    fun `the bounce is on the ground at both ends`() {
        for (walk in listOf(Walk(Place.HOME, Place.LIBRARY), Walk(Place.LIBRARY, Place.LIBRARY))) {
            assertEquals(0.0, walk.liftAt(0.0), 1e-9)
            assertEquals(0.0, walk.liftAt(1.0), 1e-9)
        }
    }
}
