package vancouvermonth.web

import kotlin.js.Date
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vancouvermonth.core.Action
import vancouvermonth.text.Townsperson

class NeighbourhoodTest {
    private val venues = Neighbourhood.venues
    private val town = Neighbourhood.grid

    /** True when every leg of [route] is a straight walk that keeps clear of blocked squares. */
    private fun Grid.walks(route: List<Spot>): Boolean = route.zipWithNext().all { (a, b) -> isClear(a, b) }

    @Test
    fun `each place has one building on the grid, and no two buildings touch`() {
        assertEquals(Place.entries.toSet(), venues.map { it.place }.toSet())
        assertEquals(Place.entries.size, venues.size)
        for (venue in venues) {
            assertTrue(venue.x >= 0 && venue.x + venue.width <= Neighbourhood.COLUMNS, "${venue.place}")
            assertTrue(venue.z >= 0 && venue.z + venue.depth <= Neighbourhood.ROWS, "${venue.place}")
        }
        for ((index, a) in venues.withIndex()) {
            for (b in venues.drop(index + 1)) {
                val apart = a.x + a.width <= b.x || b.x + b.width <= a.x || a.z + a.depth <= b.z || b.z + b.depth <= a.z
                assertTrue(apart, "${a.place} touches ${b.place}")
            }
        }
    }

    @Test
    fun `a door and a person each belong to their own place, and the street to none`() {
        for (venue in venues) {
            assertEquals(venue.place, Neighbourhood.placeAt(venue.door), "${venue.place}'s door")
            assertEquals(venue.place, Neighbourhood.placeAt(venue.talkSpot), "${venue.place}'s person")
        }
        assertNull(Neighbourhood.placeAt(Spot(28.0, 13.5)))
        assertNull(Neighbourhood.placeAt(Spot(20.0, 1.0)))
    }

    @Test
    fun `each place's person offers the action spent there`() {
        assertEquals(Townsperson.GRANDMA, personAt(Place.HOME))
        assertEquals(Townsperson.HANA, personAt(Place.CAFE))
        assertEquals(Townsperson.LEO, personAt(Place.LIBRARY))
        assertEquals(Townsperson.SUJIN, personAt(Place.COWORKING))
        assertEquals(Townsperson.MARCUS, personAt(Place.OFFICE))
        for (place in Place.entries) assertEquals(place, placeFor(actionAt(place)))
        assertEquals(Action.REST, actionAt(Place.HOME))
    }

    @Test
    fun `each person stands by the door, one square from where the player talks, on the street side`() {
        for (venue in venues) {
            assertTrue(distanceBetween(venue.person, venue.door) <= 3.5, "${venue.place}")
            assertEquals(1.0, distanceBetween(venue.person, venue.talkSpot), 1e-9)
            val south = venue.talkSpot.z > venue.person.z
            val streetSide = if (venue.doorFacesSouth) south else !south
            assertTrue(streetSide, "${venue.place}")
        }
    }

    @Test
    fun `the bench by the campfire is in 내 방's garden, next to the neighbour`() {
        val home = Neighbourhood.venue(Place.HOME)
        assertTrue(Neighbourhood.benchSeat.x < home.x, "the garden is west of 내 방")
        assertTrue(distanceBetween(Neighbourhood.benchSeat, home.person) < 3.0)
    }

    @Test
    fun `the camera keeps out of the five buildings and the others`() {
        assertEquals(Place.entries.toSet(), Neighbourhood.walls.mapNotNull { it.place }.toSet())
        assertTrue(Neighbourhood.walls.count { it.place == null } > 0)
    }

    @Test
    fun `each figure has a body, two legs and two arms, and a look for the talk box`() {
        for (parts in Townsperson.entries.map { Figures.townsperson(it) } + listOf(Figures.player)) {
            assertEquals(Limb.entries.toSet(), parts.map { it.limb }.toSet())
        }
        assertEquals(Townsperson.entries.size, Townsperson.entries.map { Figures.lookOf(it) }.toSet().size)
    }

    @Test
    fun `the town blocks every building and every person, and leaves each door and talk spot free`() {
        for (venue in Neighbourhood.venues) {
            for (x in venue.x until venue.x + venue.width) {
                for (z in venue.z until venue.z + venue.depth) {
                    assertFalse(town.isFree(Cell(x, z)), "${venue.place} at $x, $z")
                }
            }
            assertFalse(town.isFree(town.cellAt(venue.person)), "${venue.place}'s person")
            assertTrue(town.isFree(town.cellAt(venue.door)), "${venue.place}'s door")
            assertTrue(town.isFree(town.cellAt(venue.talkSpot)), "${venue.place}'s talk spot")
        }
    }

    @Test
    fun `from 내 방's door the character can walk to every door and every person`() {
        val home = Neighbourhood.venue(Place.HOME).door
        for (venue in Neighbourhood.venues) {
            for (target in listOf(venue.door, venue.talkSpot)) {
                val route = town.route(home, target)
                assertEquals(target, route.lastOrNull(), "${venue.place}: $route")
                assertTrue(town.walks(route), "${venue.place}: $route")
            }
        }
    }

    @Test
    fun `the way from the library door to the seawall behind it goes around the library`() {
        val library = Neighbourhood.venue(Place.LIBRARY)
        val behind = Spot(library.door.x, 1.0)
        val route = town.route(library.door, behind)
        assertEquals(behind, route.last())
        assertTrue(town.walks(route), "$route")
        val straight = library.door.z - behind.z
        val length = route.zipWithNext { a, b -> hypot(b.x - a.x, b.z - a.z) }.sum()
        assertTrue(length > straight + 2, "$length should be longer than the straight $straight")
        // Every turn is to the side of the library, never inside it.
        for (spot in route) assertTrue(town.isFree(town.cellAt(spot)), "$spot")
    }

    @Test
    fun `the longest walk across the town is found in a blink`() {
        val start = Date().getTime()
        val route = town.route(Spot(0.5, 0.5), Spot(47.5, 31.5))
        val millis = Date().getTime() - start
        assertTrue(route.size >= 2)
        assertTrue(millis < 200, "took $millis ms")
    }

    @Test
    fun `every free square can be reached from 내 방's door, the garden by the campfire included`() {
        val door = town.cellAt(Neighbourhood.venue(Place.HOME).door)
        val reached = town.reachableFrom(door)
        val free = town.freeCells()
        assertEquals(free.size, reached.size, "cut off: ${free.filter { it !in reached }}")
        // A square in the garden, by the fire.
        assertTrue(Cell(3, 20) in reached)
    }

    @Test
    fun `on the street the camera looks at its middle, and past the pavements at the character`() {
        assertEquals(Spot(7.5, Neighbourhood.STREET_MIDDLE), Neighbourhood.cameraFocus(Spot(7.5, 16.5)))
        assertEquals(Spot(9.5, Neighbourhood.STREET_MIDDLE), Neighbourhood.cameraFocus(Spot(9.5, 11.5)))
        assertEquals(Spot(20.0, 1.0), Neighbourhood.cameraFocus(Spot(20.0, 1.0)))
        assertEquals(Neighbourhood.benchSeat, Neighbourhood.cameraFocus(Neighbourhood.benchSeat))
        // Halfway out of the street, the nudge is half gone, so the camera never jumps.
        val halfway = 17.5 + (Neighbourhood.STREET_MIDDLE - 17.5) * 0.5
        assertEquals(halfway, Neighbourhood.cameraFocus(Spot(3.0, 17.5)).z, 1e-9)
    }

    @Test
    fun `each name floats above its building, over the side facing the street`() {
        for (venue in venues) {
            val anchor = venue.labelAnchor
            assertTrue(anchor.x > venue.x && anchor.x < venue.x + venue.width, "${venue.place}")
            assertTrue(anchor.z > venue.z && anchor.z < venue.z + venue.depth, "${venue.place}")
            assertTrue(anchor.y > venue.box.height, "${venue.place}")
            val middle = venue.z + venue.depth / 2.0
            val streetSide = if (venue.doorFacesSouth) anchor.z > middle else anchor.z < middle
            assertTrue(streetSide, "${venue.place}")
        }
    }
}
