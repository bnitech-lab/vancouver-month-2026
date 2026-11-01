package vancouvermonth.web

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import vancouvermonth.core.TaxiFare

/**
 * The town on the wide map: its corner as it always was, 내 방 where the room's zone puts it, the taxi's ways across
 * the whole map, and the wide city's buildings off the streets.
 */
class HomeSiteTest {
    private fun length(route: List<Spot>): Double = route.zipWithNext { a, b -> distanceBetween(a, b) }.sum()

    /** What a way square by square costs: 1 for a straight step, the square root of 2 for a slanted one. */
    private fun cost(cells: List<Cell>): Double =
        cells.zipWithNext { a, b -> if (a.x != b.x && a.z != b.z) kotlin.math.sqrt(2.0) else 1.0 }.sum()

    @Test
    fun `the town stands on the wide map, its corner as it always was`() {
        assertEquals(WideVancouver.map.columns, Neighbourhood.COLUMNS)
        assertEquals(WideVancouver.map.rows, Neighbourhood.ROWS)
        val town = RealVancouver.map
        for (z in 0 until town.rows) {
            for (x in 0 until town.columns) assertEquals(town.terrainAt(x, z), Neighbourhood.map.terrainAt(x, z), "$x, $z")
        }
        // The five places keep their squares, by the ones the map proposed; the town's 내 방 is zone 1's; the corner's
        // ground is its own draw.
        for ((place, cell) in RealVancouver.spots) {
            val venue = Neighbourhood.venue(place)
            val middle = Spot(venue.x + venue.width / 2.0, venue.z + venue.depth / 2.0)
            assertTrue(distanceBetween(middle, Spot(cell.x + 0.5, cell.z + 0.5)) < 8.0, "$place")
        }
        assertEquals(Neighbourhood.venue(Place.HOME), Neighbourhood.home(1))
        assertTrue(Neighbourhood.corner.all { it.x + it.width <= Neighbourhood.TOWN_COLUMNS }, "the corner's ground spills over")
        assertTrue(Neighbourhood.groundTiles.flatten().all { it.x >= Neighbourhood.TOWN_COLUMNS })
        // The wide map's buildings are turned boxes east of the corner; the corner's are its own, straight on the grid.
        for (building in Neighbourhood.foldables) {
            val body = Neighbourhood.blocks[building.body]
            if (body.turn != 0.0 && body.z >= 0 && body.z < Neighbourhood.ROWS) {
                assertTrue(body.x + body.width / 2 > Neighbourhood.TOWN_COLUMNS, "$body")
            }
        }
    }

    @Test
    fun `the corner's buildings, trees and low blocks past its edge are just as they were on main`() {
        // Pinned from main at 68929ca, before the town reached Burnaby: the same lots, the same buildings with the same
        // heights and colours, the same low blocks past the south edge, the same tree trunks and blossom trees.
        val lots = Neighbourhood.cityLots
        assertEquals(listOf(402, 71521, 33328, 1489, 1331), listOf(lots.size, lots.sumOf { it.x }, lots.sumOf { it.z },
            lots.sumOf { it.width }, lots.sumOf { it.depth }))
        val corner = Neighbourhood.foldables.filter {
            val middleX = it.box.x + it.box.width / 2
            val middleZ = it.box.z + it.box.depth / 2
            middleX < Neighbourhood.TOWN_COLUMNS && middleZ >= 0 && middleZ < Neighbourhood.ROWS
        }
        assertEquals(402, corner.size)
        assertEquals(71581.3, corner.sumOf { it.box.x }, 1e-6)
        assertEquals(33388.3, corner.sumOf { it.box.z }, 1e-6)
        assertEquals(2244.1429907367474, corner.sumOf { it.box.height }, 1e-6)
        assertEquals(5683607188L, corner.sumOf { it.box.colour.toLong() })
        val beyond = Neighbourhood.foldables.filter { it.box.z >= Neighbourhood.ROWS && it.box.x < Neighbourhood.TOWN_COLUMNS }
        assertEquals(42, beyond.size)
        assertEquals(198.3841444445423, beyond.sumOf { it.box.height }, 1e-6)
        assertEquals(628649216L, beyond.sumOf { it.box.colour.toLong() })
        val trunks = Neighbourhood.blocks.filter { it.colour == 0x8A5F3D && it.width == 0.3 && it.x + 0.15 < Neighbourhood.TOWN_COLUMNS }
        assertEquals(226, trunks.size)
        assertEquals(18386.1, trunks.sumOf { it.x }, 1e-6)
        assertEquals(15587.1, trunks.sumOf { it.z }, 1e-6)
        assertEquals(25, Neighbourhood.blossoms.size)
        assertEquals(4095.5, Neighbourhood.blossoms.sumOf { it.x }, 1e-6)
        assertEquals(2655.5, Neighbourhood.blossoms.sumOf { it.z }, 1e-6)
    }

    @Test
    fun `at the corner's edge, no building of the wide map stands in one of the corner's`() {
        val turned = Neighbourhood.foldables.map { Neighbourhood.blocks[it.body] }.filter { it.turn != 0.0 && it.z >= 0 }
        val nearEdge = turned.filter { it.x < Neighbourhood.TOWN_COLUMNS + 10 }
        assertTrue(nearEdge.isNotEmpty())
        for (body in nearEdge) {
            val shape = TurnedBox(body.x + body.width / 2, body.z + body.depth / 2, body.width, body.depth, body.turn)
            for (lot in Neighbourhood.cityLots) assertFalse(shape.overlaps(lot, 0.0), "$body on the corner's $lot")
        }
    }

    @Test
    fun `내 방 stands in its room's zone, on open ground every station can reach`() {
        for (zone in 1..3) {
            val site = HomeSite.of(zone)
            val door = site.home.door
            assertTrue(site.grid.isFree(site.grid.cellAt(door)), "zone $zone's door")
            if (zone > 1) {
                // On the room's square the wide map gives the zone, inside the zone.
                assertEquals(WideVancouver.homes.getValue(zone), site.grid.cellAt(door))
                assertEquals(zone, WideVancouver.zoneAt(door))
            }
            for ((index, station) in TransitLines.stations.withIndex()) {
                val way = site.grid.route(station.door, door)
                assertEquals(door, way.lastOrNull(), "zone $zone from station $index")
            }
            // Its garden, bench and fire with it, and its neighbour by its door.
            assertEquals(site.home.x - 2.7, site.benchSeat.x, 1e-9)
            assertEquals(site.home.x - 2.7, site.campfire.x, 1e-9)
            assertTrue(distanceBetween(site.venue(Place.HOME).person, door) < 4.0, "zone $zone's neighbour")
            assertEquals(Place.HOME, site.placeAt(door))
            // The other four places are downtown in every zone.
            for (place in Place.entries.filter { it != Place.HOME }) assertEquals(Neighbourhood.venue(place), site.venue(place))
        }
        // Zone 3's 내 방 is farther from 회사 than zone 2's.
        val office = Neighbourhood.venue(Place.OFFICE).door
        val far = (2..3).map { zone -> length(HomeSite.of(zone).grid.route(Neighbourhood.home(zone).door, office)) }
        assertTrue(far[1] > far[0], "$far")
    }

    @Test
    fun `내 방 and its garden stand only in its own zone's town, on ground the city keeps clear`() {
        for (zone in 2..3) {
            val site = HomeSite.of(zone)
            val lot = Neighbourhood.lotFor(site.home)
            // No building of the city stands on its lot, in any zone's town.
            for (building in Neighbourhood.foldables) {
                val box = building.box
                val apart = box.x + box.width <= lot.x || lot.x + lot.width <= box.x ||
                    box.z + box.depth <= lot.z || lot.z + lot.depth <= box.z
                assertTrue(apart, "$box on zone $zone's lot $lot")
            }
            // The house is in its own town's boxes and blocks the way there, not in zone 1's.
            assertTrue(site.blocks.any { it.x == site.home.x.toDouble() && it.z == site.home.z.toDouble() })
            val inside = Cell(site.home.x + 2, site.home.z + 2)
            assertFalse(site.grid.isFree(inside))
            assertTrue(HomeSite.of(1).grid.isFree(inside) || !Neighbourhood.open.isFree(inside))
            assertFalse(HomeSite.of(1).blocks.any { it.x == site.home.x.toDouble() && it.z == site.home.z.toDouble() })
        }
    }

    @Test
    fun `a taxi goes everywhere a taxi goes, across the whole map, for a fare under the meter's most`() {
        for (zone in 1..3) {
            val site = HomeSite.of(zone)
            val places = TransitLines.taxiStopsFor(zone)
            assertEquals(TransitLines.taxiStops.map { it.first }, places.map { it.first })
            assertEquals(site.home.door, places.first().second)
            // One search for all of them finds ways as cheap as one search each, square by square: the two may pick
            // different squares among ways of the same cost, which then straighten a little differently (zone 2's way
            // to 큰 공원 도서관 by 4.4%), so it is the squares' cost that is the same, and straightening never lengthens it.
            val targets = places.map { it.second }
            val ways = site.grid.routes(site.home.door, targets)
            val squares = site.grid.cellRoutes(site.home.door, targets)
            for ((index, way) in ways.withIndex()) {
                val (name, spot) = places[index]
                if (spot == site.home.door) continue
                assertEquals(spot, way.last(), "zone $zone to $name")
                val alone = assertNotNull(site.grid.cellRoute(site.home.door, spot), "zone $zone to $name")
                assertEquals(cost(alone), cost(squares[index]), 1e-9, "zone $zone to $name")
                assertTrue(length(way) <= cost(squares[index]) + 1.5, "zone $zone to $name: ${length(way)}")
                assertTrue(TaxiFare.forDistance(length(way)) < TaxiFare.MOST, "zone $zone to $name")
            }
        }
        // Burnaby to downtown is about two days' living money, not the meter's most.
        val far = length(HomeSite.of(3).grid.route(Neighbourhood.home(3).door, Neighbourhood.venue(Place.OFFICE).door))
        assertTrue(TaxiFare.forDistance(far) in 40..55, "${TaxiFare.forDistance(far)} for $far squares")
    }

    @Test
    fun `the taxi's fares from the farthest 내 방 are worked out in a blink`() {
        // Zone 3's 내 방, the farthest from downtown: every place a taxi goes, found and straightened, as the taxi's card
        // asks for them when it opens.
        val site = HomeSite.of(3)
        val places = TransitLines.taxiStopsFor(3).map { it.second }
        // The best of three, so a pause of the browser's own in one of them does not count against the search.
        val millis = (1..3).minOf {
            val start = Date().getTime()
            site.grid.routes(site.home.door, places)
            Date().getTime() - start
        }
        println("The taxi's ways from zone 3's 내 방: $millis ms")
        // About 50 ms on the laptop the town was built on; the bound leaves room for a slower machine.
        assertTrue(millis < 300, "took $millis ms")
    }

    @Test
    fun `the wide city's buildings keep off the main streets, the bus lines and the guideway`() {
        val streets = WideVancouver.streets + TransitLines.busLines.map { it.loop.points }
        val turned = Neighbourhood.foldables.map { Neighbourhood.blocks[it.body] }.filter { it.turn != 0.0 && it.z >= 0 }
        assertTrue(turned.size > 1_000, "only ${turned.size}")
        for (body in turned) {
            val shape = TurnedBox(body.x + body.width / 2, body.z + body.depth / 2, body.width, body.depth, body.turn)
            for (line in streets) {
                for ((a, b) in line.zipWithNext()) {
                    if (distanceBetween(a, Spot(shape.x, shape.z)) > 40 && distanceBetween(b, Spot(shape.x, shape.z)) > 40) continue
                    for (step in 0..10) {
                        val x = a.x + (b.x - a.x) * step / 10
                        val z = a.z + (b.z - a.z) * step / 10
                        assertTrue(shape.distanceTo(x, z) >= Neighbourhood.WIDE_STREET_CLEAR - 0.3, "$body on a street at $x, $z")
                    }
                }
            }
        }
        // Taller round the Burnaby town centre than out in the houses.
        val centre = WideVancouver.stations[TransitLines.TOWN_CENTRE].spot
        val near = turned.filter { distanceBetween(Spot(it.x + it.width / 2, it.z + it.depth / 2), centre) < 15 }
        val houses = turned.filter { distanceBetween(Spot(it.x + it.width / 2, it.z + it.depth / 2), centre) > 60 }
        assertTrue(near.count { it.height >= 8.0 } * 2 >= near.size, "${near.map { it.height }}")
        assertTrue(near.maxOf { it.height } >= 12.0, "${near.map { it.height }}")
        assertTrue(houses.maxOf { it.height } < 6.5)
    }
}
