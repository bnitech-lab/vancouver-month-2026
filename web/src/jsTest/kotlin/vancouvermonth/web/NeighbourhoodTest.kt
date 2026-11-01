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
        // On the wide street between 회사 and 도서관, and on the West End street between 내 방 and the seawall.
        for (street in listOf(Spot(162.5, 70.5), Spot(140.5, 99.5))) {
            assertTrue(Neighbourhood.grid.isFree(Neighbourhood.grid.cellAt(street)), "$street is not open street")
            assertNull(Neighbourhood.placeAt(street))
        }
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
    fun `the way from the library door to the street behind it goes around the library`() {
        val library = Neighbourhood.venue(Place.LIBRARY)
        val behind = Spot(library.door.x, library.z - 0.5)
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
        // From the park's western tip to the far corner across False Creek: the way goes round the creek's head.
        val free = town.freeCells()
        val west = town.centreOf(free.minBy { it.x })
        val far = town.centreOf(free.maxBy { it.x + it.z })
        // The best of three, so a pause of the browser's own in one of them does not count against the search.
        val millis = (1..3).minOf {
            val start = Date().getTime()
            town.route(west, far)
            Date().getTime() - start
        }
        val route = town.route(west, far)
        assertEquals(far, route.last())
        assertTrue(route.zipWithNext { a, b -> hypot(b.x - a.x, b.z - a.z) }.sum() > 300.0, "$route")
        // About 5 ms on the laptop the town was built on; the bound leaves room for a slower machine.
        assertTrue(millis < 50, "took $millis ms")
    }

    @Test
    fun `every free square can be reached from 내 방's door, and only a few walled-in squares were blocked for it`() {
        val door = town.cellAt(Neighbourhood.venue(Place.HOME).door)
        val reached = town.reachableFrom(door)
        val free = town.freeCells()
        assertEquals(free.size, reached.size, "cut off: ${free.filter { it !in reached }}")
        // A square in the garden, by the fire.
        val home = Neighbourhood.venue(Place.HOME)
        assertTrue(Cell(home.x - 1, home.z + 1) in reached)
        // Squares the character could never reach are blocked; there must be only a few (the odd square between the wide
        // map's turned blocks), or the layout cut off a part of the town.
        assertTrue(Neighbourhood.pockets < free.size / 100, "${Neighbourhood.pockets} squares were walled in")
    }

    @Test
    fun `a tap on the water stops on the near shore, never leading round the creek to the far one`() {
        val cafe = Neighbourhood.venue(Place.CAFE).door
        // Out in False Creek, south of the café, nearer its south shore than its north one.
        val creek = Spot(180.0, 134.5)
        assertEquals(Terrain.WATER, RealVancouver.map.terrainAt(town.cellAt(creek)))
        val landing = Neighbourhood.landing(cafe, creek)
        assertTrue(town.isFree(town.cellAt(landing)), "$landing")
        assertTrue(landing.z > cafe.z && landing.z < creek.z, "$landing is not on the café's side of the creek")
        // Straight down to the water's edge, not round the head of the creek.
        val walk = town.route(cafe, landing)
        assertTrue(walk.zipWithNext { a, b -> hypot(b.x - a.x, b.z - a.z) }.sum() < 30.0, "$walk")
        // A tap on land goes where it was tapped.
        assertEquals(Spot(162.5, 70.5), Neighbourhood.landing(cafe, Spot(162.5, 70.5)))
    }

    @Test
    fun `the farthest walk between two places, along the streets, takes no more than 35 seconds`() {
        val spots = venues.flatMap { listOf(it.door, it.talkSpot) }
        val seconds = spots.flatMapIndexed { index, a ->
            spots.drop(index + 1).map { b ->
                town.route(a, b).zipWithNext { p, q -> hypot(q.x - p.x, q.z - p.z) }.sum() / Trip.WALK_SPEED
            }
        }
        assertTrue(seconds.max() <= 35.0, "${seconds.max()} s")
    }

    @Test
    fun `each place stands on open land near the square proposed for it on the real map`() {
        val map = RealVancouver.map
        for (venue in venues) {
            val proposed = RealVancouver.spots.getValue(venue.place)
            val middle = Spot(venue.x + venue.width / 2.0, venue.z + venue.depth / 2.0)
            assertTrue(distanceBetween(middle, map.grid.centreOf(proposed)) < 7.0, "${venue.place} at $middle")
            for (x in venue.x until venue.x + venue.width) {
                for (z in venue.z until venue.z + venue.depth) {
                    assertEquals(Terrain.LAND, map.terrainAt(Cell(x, z)), "${venue.place} at $x, $z")
                }
            }
        }
    }

    @Test
    fun `the city stands on the real blocks, off the water, clear of the places and the guideway, none overlapping`() {
        val map = RealVancouver.map
        val lots = Neighbourhood.cityLots
        assertTrue(lots.size > 300, "only ${lots.size} buildings")
        val places = venues.map { Lot(it.x, it.z, it.width, it.depth) }
        val rail = (0..((Neighbourhood.guideway.length) / 0.5).toInt()).map { Neighbourhood.guideway.spotAt(it * 0.5) }
            .filter { it.x < Neighbourhood.COLUMNS }
        for (lot in lots) {
            for (x in lot.x until lot.x + lot.width) {
                for (z in lot.z until lot.z + lot.depth) assertEquals(Terrain.LAND, map.terrainAt(Cell(x, z)), "$lot")
            }
            assertTrue(places.none { it.overlaps(lot) }, "$lot")
            assertTrue(rail.none { lot.isNear(it.x, it.z, 0.5) }, "$lot is under the guideway")
        }
        // No two overlap. Pieces of one block cut round a place may meet; between the real blocks a street runs (see
        // VancouverMapTest).
        for ((index, a) in lots.withIndex()) {
            for (b in lots.drop(index + 1)) assertFalse(a.overlaps(b), "$a and $b")
        }
    }

    @Test
    fun `the ground is a few hundred boxes over one plane of sea, and the mountains stand far across the inlet`() {
        assertEquals(1, Neighbourhood.ground.count { it.colour == Neighbourhood.WATER })
        assertTrue(Neighbourhood.ground.size < 1_200, "${Neighbourhood.ground.size} boxes of ground")
        // Every box of the mountains lies past the line in the middle of the inlet, and well past it.
        val line = RealVancouver.mountains
        val tall = Neighbourhood.ground.filter { it.y > 1.0 }
        assertTrue(tall.isNotEmpty())
        for (peak in tall) {
            val middle = peak.x + peak.width / 2
            val shore = line.zipWithNext().firstOrNull { (_, b) -> middle <= b.x }?.let { (a, b) ->
                if (middle <= a.x) a.z else a.z + (b.z - a.z) * (middle - a.x) / (b.x - a.x)
            } ?: line.last().z
            assertTrue(peak.z + peak.depth < shore - 20.0, "$peak")
        }
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
