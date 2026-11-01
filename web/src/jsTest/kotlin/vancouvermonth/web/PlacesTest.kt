package vancouvermonth.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The places of each kind round the map: three cafés, three libraries, two coworking spaces and three offices, the
 * first of each downtown and the others in East Vancouver and Burnaby, each on a lot the city keeps clear, open to a
 * street, reached from every station and every 내 방, and looking like its kind.
 */
class PlacesTest {
    /** The places out past downtown: all but the first of each kind, and 내 방. */
    private val outside = Neighbourhood.venues.filter { it.place != Place.HOME && it.place.number > 0 }

    private fun length(route: List<Spot>): Double = route.zipWithNext { a, b -> distanceBetween(a, b) }.sum()

    @Test
    fun `out past downtown, east of the town's corner and west of where the map avoids, in East Vancouver and Burnaby`() {
        assertEquals(7, outside.size)
        for (venue in outside) {
            val lot = Neighbourhood.lotFor(venue)
            assertTrue(lot.x >= Neighbourhood.TOWN_COLUMNS, "${venue.place} in the town's corner")
            // Zone 2's east edge, past which nothing is placed, runs no nearer than x 602 within the grid.
            assertTrue(lot.x + lot.width <= 600, "${venue.place} toward the area the map avoids")
            assertTrue(lot.z >= 0 && lot.z + lot.depth <= Neighbourhood.ROWS, "${venue.place} off the grid")
            // On plain land: no park, no water, no seawall under the lot.
            for (x in lot.x until lot.x + lot.width) {
                for (z in lot.z until lot.z + lot.depth) {
                    assertEquals(Terrain.LAND, Neighbourhood.map.terrainAt(x, z), "${venue.place} at $x, $z")
                }
            }
        }
        // East Vancouver has a café, a library and an office; Burnaby one of each kind, past the city line at x 490.
        val (east, burnaby) = outside.partition { it.x < 490 }
        assertEquals(setOf(PlaceKind.CAFE, PlaceKind.LIBRARY, PlaceKind.OFFICE), east.map { it.place.kind }.toSet())
        assertEquals(PlaceKind.entries.toSet() - PlaceKind.HOME, burnaby.map { it.place.kind }.toSet())
    }

    @Test
    fun `each stands on a lot no building of the city and no house stands on, its square open to a main street`() {
        val streets = WideVancouver.streets + TransitLines.busLines.map { it.loop.points }
        for (venue in outside) {
            val lot = Neighbourhood.lotFor(venue)
            for (building in Neighbourhood.foldables) {
                val box = building.box
                val apart = box.x + box.width <= lot.x || lot.x + lot.width <= box.x ||
                    box.z + box.depth <= lot.z || lot.z + lot.depth <= box.z
                assertTrue(apart, "$box on ${venue.place}'s lot $lot")
            }
            for (house in Neighbourhood.houses) assertTrue(!house.shape.overlaps(lot, 0.0), "a house on ${venue.place}'s lot")
            // The building keeps off the main streets, the bus lines and the guideway.
            val middle = Spot(venue.x + venue.width / 2.0, venue.z + venue.depth / 2.0)
            val body = TurnedBox(middle.x, middle.z, venue.width.toDouble(), venue.depth.toDouble(), 0.0)
            for (line in streets) {
                for ((a, b) in line.zipWithNext()) {
                    for (step in 0..20) {
                        val x = a.x + (b.x - a.x) * step / 20
                        val z = a.z + (b.z - a.z) * step / 20
                        assertTrue(body.distanceTo(x, z) >= 1.0, "${venue.place} on a street at $x, $z")
                    }
                }
            }
            for (step in 0..Neighbourhood.guideway.length.toInt() * 2) {
                val on = Neighbourhood.guideway.spotAt(step / 2.0)
                assertTrue(body.distanceTo(on.x, on.z) >= 2.5, "${venue.place} under the guideway at $on")
            }
            // Its square's far edge, out from the door, lies within a few squares of a main street or a bus line.
            val out = Spot(venue.door.x, venue.door.z + if (venue.doorFacesSouth) 3.5 else -3.5)
            val street = streets.minOf { line -> line.zipWithNext().minOf { (a, b) -> distanceToSegment(out, a, b) } }
            assertTrue(street <= 4.5, "${venue.place}'s square is $street squares from a street")
        }
    }

    @Test
    fun `the rest of the wide map keeps its buildings and houses as they stood before the places came`() {
        // Pinned from main at 3b95ffe (v0.19): every building and house of the wide map two squares or more clear of the
        // places' lots stands as it did, as the city's chances are drawn as they were before the lots were cleared.
        val lots = outside.map { Neighbourhood.lotFor(it) }.map { Lot(it.x - 2, it.z - 2, it.width + 4, it.depth + 4) }
        val wide = Neighbourhood.foldables.filter { building ->
            val box = building.box
            box.x + box.width / 2 >= Neighbourhood.TOWN_COLUMNS && lots.none { lot ->
                box.x < lot.x + lot.width && lot.x < box.x + box.width && box.z < lot.z + lot.depth && lot.z < box.z + box.depth
            }
        }
        assertEquals(3623, wide.size)
        assertEquals(1531250.1429584178, wide.sumOf { it.box.x }, 1e-6)
        assertEquals(7407.166404596671, wide.sumOf { it.box.height }, 1e-6)
        assertEquals(54651483102L, wide.sumOf { it.box.colour.toLong() })
        val houses = Neighbourhood.houses.filter { house -> lots.none { house.shape.overlaps(it, 0.0) } }
        assertEquals(3305, houses.size)
        assertEquals(1383716.6697575846, houses.sumOf { it.shape.x }, 1e-6)
        assertEquals(4205.64196010185, houses.sumOf { it.wall }, 1e-6)
        assertEquals(49975483676L, houses.sumOf { it.colour.toLong() })
    }

    @Test
    fun `every place is reached on foot from every station and every 내 방, in every zone's town`() {
        for (zone in 1..3) {
            val site = HomeSite.of(zone)
            val starts = TransitLines.stations.map { it.door } + site.home.door
            for (venue in site.venues) {
                val ways = site.grid.routes(venue.talkSpot, starts)
                for ((index, way) in ways.withIndex()) {
                    assertEquals(starts[index], way.lastOrNull(), "zone $zone: ${venue.place} from ${starts[index]}")
                }
                assertTrue(site.grid.isFree(site.grid.cellAt(venue.door)), "zone $zone: ${venue.place}'s door")
                assertEquals(venue.place, site.placeAt(venue.door), "zone $zone: ${venue.place}'s door")
            }
        }
    }

    @Test
    fun `a room in any zone has a place of each kind within a short ride of its 내 방`() {
        for (zone in 1..3) {
            val site = HomeSite.of(zone)
            for (kind in PlaceKind.entries - PlaceKind.HOME) {
                val places = site.venues.filter { it.place.kind == kind }
                val ways = site.grid.routes(site.home.door, places.map { it.door }).map { length(it) }
                val nearest = ways.min()
                val label = places[ways.indexOf(nearest)].place.label
                println("Zone $zone's 내 방 to the nearest ${kind.label}: $label, $nearest squares")
                // Out past downtown, about two and a half kilometres on foot at most: a short bus or bike ride. (In zone 1
                // downtown's are as they always were, the coworking space nearly three kilometres off.)
                if (zone > 1) assertTrue(nearest <= 110.0, "zone $zone's nearest ${kind.label} is $nearest squares away")
            }
        }
    }

    @Test
    fun `each looks like its kind, in its kind's colours, with its kind's mark on the side its door is on`() {
        for (venue in outside) {
            val first = Neighbourhood.venue(Place.of(venue.place.kind))
            assertEquals(first.wall, venue.wall, "${venue.place}")
            assertEquals(first.roof, venue.roof, "${venue.place}")
            assertEquals(first.pitched, venue.pitched, "${venue.place}")
            // The mark stands just out from the front wall, in front of the door.
            val front = if (venue.doorFacesSouth) (venue.z + venue.depth).toDouble() else venue.z.toDouble()
            val colour = when (venue.place.kind) {
                PlaceKind.CAFE -> AWNING
                PlaceKind.LIBRARY -> STONE_LIGHT
                PlaceKind.COWORKING -> SIGN_GREEN
                else -> CONCRETE
            }
            val marks = Neighbourhood.blocks.filter { block ->
                val over = block.x >= venue.x - 0.1 && block.x + block.width <= venue.x + venue.width + 0.1
                val out = if (venue.doorFacesSouth) {
                    block.z >= front - 0.01 && block.z < front + 0.5
                } else {
                    block.z + block.depth <= front + 0.01 && block.z + block.depth > front - 0.5
                }
                block.colour == colour && over && out
            }
            assertTrue(marks.isNotEmpty(), "${venue.place} has no ${venue.place.kind.label} mark at its front")
        }
        // Downtown's look as it was: the café's awning to the north, the library's steps to the south.
        val cafe = Neighbourhood.venue(Place.CAFE)
        assertTrue(Neighbourhood.blocks.any { it.colour == AWNING && it.z == cafe.z - 0.35 && it.x == cafe.x.toDouble() })
        val library = Neighbourhood.venue(Place.LIBRARY)
        assertTrue(Neighbourhood.blocks.any { it.colour == STONE_LIGHT && it.z == (library.z + library.depth).toDouble() })
    }

    @Test
    fun `each has its own person by its door, who keeps that place`() {
        assertEquals(Place.entries.size, Place.entries.map { it.person }.toSet().size)
        for (venue in Neighbourhood.venues) {
            assertEquals(venue.place.kind.action, venue.place.person.offers, "${venue.place}")
            // 내 방's neighbour stands in the garden by the bench, a little farther off.
            if (venue.place != Place.HOME) assertTrue(distanceBetween(venue.person, venue.door) <= 2.5, "${venue.place}")
        }
    }

    private companion object {
        // The kinds' marks' colours, as the town paints them.
        const val AWNING = 0x3E8E6A
        const val STONE_LIGHT = 0xEDE6D6
        const val SIGN_GREEN = 0x2F6B5A
        const val CONCRETE = 0xDCDDD8

        fun distanceToSegment(spot: Spot, a: Spot, b: Spot): Double {
            val dx = b.x - a.x
            val dz = b.z - a.z
            val length = dx * dx + dz * dz
            val share = if (length == 0.0) 0.0 else (((spot.x - a.x) * dx + (spot.z - a.z) * dz) / length).coerceIn(0.0, 1.0)
            return distanceBetween(spot, Spot(a.x + dx * share, a.z + dz * share))
        }
    }
}
