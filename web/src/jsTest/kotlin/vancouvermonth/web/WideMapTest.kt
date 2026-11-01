package vancouvermonth.web

import kotlin.js.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WideMapTest {
    private val map = WideVancouver.map
    private val town = RealVancouver.map

    private fun centre(cell: Cell) = Spot(cell.x + 0.5, cell.z + 0.5)

    private fun onMap(spot: Spot) = spot.x in 0.0..map.columns.toDouble() && spot.z in 0.0..map.rows.toDouble()

    /** How far [spot] lies from the nearest point of [line]. */
    private fun distanceTo(line: List<Spot>, spot: Spot): Double = line.zipWithNext().minOf { (a, b) ->
        val dx = b.x - a.x
        val dz = b.z - a.z
        val length = dx * dx + dz * dz
        val t = if (length == 0.0) 0.0 else (((spot.x - a.x) * dx + (spot.z - a.z) * dz) / length).coerceIn(0.0, 1.0)
        hypot(a.x + t * dx - spot.x, a.z + t * dz - spot.z)
    }

    /** Spots every half square along [line], its ends included. */
    private fun along(line: List<Spot>): List<Spot> = line.zipWithNext().flatMap { (a, b) ->
        val steps = (distanceBetween(a, b) / 0.5).toInt() + 1
        (0..steps).map { Spot(a.x + (b.x - a.x) * it / steps, a.z + (b.z - a.z) * it / steps) }
    }

    @Test
    fun `runs are spelt out square by square`() {
        assertEquals("~~~..s", unpackRuns("~3.2s"))
        assertEquals("p", unpackRuns("p"))
        assertEquals("~".repeat(12) + "s", unpackRuns("~12s"))
        assertEquals("", unpackRuns(""))
    }

    @Test
    fun `the wide map is as big as its data says, with every square one of the four kinds`() {
        assertEquals(WideMapData.COLUMNS, map.columns)
        assertEquals(WideMapData.ROWS, map.rows)
        assertEquals(map.columns * map.rows, Terrain.entries.sumOf { map.cellsOf(it).size })
        // The same rows as the town, carried on to the right well past twice the town's width.
        assertEquals(town.rows, map.rows)
        assertTrue(map.columns > 2.5 * town.columns, "${map.columns}")
        assertEquals(VancouverMapData.METRES_PER_UNIT, WideMapData.METRES_PER_UNIT)
        assertEquals(VancouverMapData.TURN_DEGREES, WideMapData.TURN_DEGREES)
    }

    @Test
    fun `the town's map is the wide map's top-left corner, square for square`() {
        assertEquals(town.columns, WideVancouver.TOWN_COLUMNS)
        val different = mutableListOf<Cell>()
        for (z in 0 until town.rows) {
            for (x in 0 until town.columns) if (map.terrainAt(x, z) != town.terrainAt(x, z)) different.add(Cell(x, z))
        }
        assertEquals(emptyList(), different)
        // The five places keep their squares, and 내 방 in zone 1 is the town's.
        assertEquals(RealVancouver.spots.getValue(Place.HOME), WideVancouver.homes.getValue(1))
        for ((place, cell) in RealVancouver.spots) assertEquals(Terrain.LAND, map.terrainAt(cell), "$place")
    }

    @Test
    fun `every walkable square can be reached from 내 방 in the West End`() {
        val grid = map.grid
        val notWater = Terrain.entries.filter { it != Terrain.WATER }.sumOf { map.cellsOf(it).size }
        val reached = grid.reachableFrom(WideVancouver.homes.getValue(1))
        assertEquals(notWater, reached.size)
        assertEquals(grid.freeCells().size, reached.size)
    }

    @Test
    fun `the shore is exactly the walkable squares beside the water`() {
        val wrong = mutableListOf<Cell>()
        for (z in 0 until map.rows) {
            for (x in 0 until map.columns) {
                val terrain = map.terrainAt(x, z)
                if (terrain == Terrain.WATER) continue
                val besideWater = (-1..1).any { dx ->
                    (-1..1).any { dz ->
                        val nx = x + dx
                        val nz = z + dz
                        nx in 0 until map.columns && nz in 0 until map.rows && map.terrainAt(nx, nz) == Terrain.WATER
                    }
                }
                if (besideWater != (terrain == Terrain.SHORE)) wrong.add(Cell(x, z))
            }
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `the rail runs on land from the tunnel's mouth through East Vancouver into Burnaby, to the map's edge`() {
        val rail = WideVancouver.rail
        // It starts where the town's rail starts, by the stadiums.
        assertEquals(RealVancouver.rail.first(), rail.first())
        // No bridge on the way: every spot along it is on land.
        for (spot in along(rail)) assertTrue(map.terrainAt(map.grid.cellAt(spot)) != Terrain.WATER, "$spot")
        // It leaves the grid across its top edge, far to the right of the town.
        assertEquals(0.0, rail.last().z, 1e-9)
        assertTrue(rail.last().x > 2.5 * town.columns, "${rail.last()}")
        for (spot in rail) assertTrue(onMap(spot), "$spot")
    }

    @Test
    fun `inside the town's corner the wide rail keeps to the town's rail`() {
        // So the guideway can move onto the wide rail without a jog: every point of the town's rail lies within the
        // town's own simplification of the wide one.
        for (spot in RealVancouver.rail) {
            assertTrue(distanceTo(WideVancouver.rail, spot) <= 0.35, "$spot")
        }
    }

    @Test
    fun `the stations stand on the rail in order, on land, with room between them`() {
        val stations = WideVancouver.stations
        assertEquals(9, stations.size)
        // The first by the tunnel's mouth, where the town's station is.
        assertTrue(distanceBetween(stations.first().spot, WideVancouver.rail.first()) < 2.0, "${stations.first()}")
        for ((a, b) in stations.zipWithNext()) assertTrue(b.along - a.along > 25.0, "$a then $b")
        for (stop in stations) {
            assertTrue(stop.spot in WideVancouver.rail, "$stop is not a point of the rail")
            assertTrue(map.terrainAt(map.grid.cellAt(stop.spot)) != Terrain.WATER, "$stop")
        }
        // How far along the line each says it is matches the line itself.
        val lengths = WideVancouver.rail.zipWithNext { a, b -> distanceBetween(a, b) }.runningFold(0.0, Double::plus)
        for (stop in stations) {
            assertEquals(stop.along, lengths[WideVancouver.rail.indexOf(stop.spot)], 0.3, "$stop")
        }
        // Two stand in zone 2, and none in zone 3: its rooms are off the line.
        assertEquals(2, stations.count { WideVancouver.zoneAt(it.spot) == 2 })
        assertEquals(0, stations.count { WideVancouver.zoneAt(it.spot) == 3 })
    }

    @Test
    fun `the big Burnaby park is a park, beside a station`() {
        // The park squares right of the town, grouped side to side.
        val park = map.cellsOf(Terrain.PARK).filter { it.x >= town.columns }.toSet()
        val groups = mutableListOf<List<Cell>>()
        val seen = mutableSetOf<Cell>()
        for (cell in park) {
            if (cell in seen) continue
            val group = mutableListOf<Cell>()
            val waiting = ArrayDeque(listOf(cell))
            seen.add(cell)
            while (waiting.isNotEmpty()) {
                val next = waiting.removeFirst()
                group.add(next)
                for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val side = Cell(next.x + dx, next.z + dz)
                    if (side in park && seen.add(side)) waiting.add(side)
                }
            }
            groups.add(group)
        }
        val biggest = groups.maxBy { it.size }
        val hectares = biggest.size * WideVancouver.METRES_PER_UNIT * WideVancouver.METRES_PER_UNIT / 10_000
        assertTrue(hectares in 65.0..100.0, "$hectares ha")
        val size = biggest.size.toDouble()
        val middle = Spot(biggest.sumOf { it.x } / size, biggest.sumOf { it.z } / size)
        assertTrue(WideVancouver.stations.any { distanceBetween(it.spot, middle) < 30.0 }, "$middle")
        assertTrue(groups.size > 20, "${groups.size} parks")
    }

    @Test
    fun `each zone has its room, on plain land with room for a home, off the main streets`() {
        assertEquals(setOf(1, 2, 3), WideVancouver.zones.keys)
        assertEquals(setOf(1, 2, 3), WideVancouver.homes.keys)
        for ((zone, cell) in WideVancouver.homes) {
            assertEquals(zone, WideVancouver.zoneAt(centre(cell)), "zone $zone")
            for (dx in -2..2) {
                for (dz in -2..2) assertEquals(Terrain.LAND, map.terrainAt(cell.x + dx, cell.z + dz), "zone $zone")
            }
            assertTrue(WideVancouver.streets.all { distanceTo(it, centre(cell)) >= 2.0 }, "zone $zone on a main street")
        }
        // Zone 1 is in the town's corner; zones 2 and 3 far to the right, in Burnaby.
        val homes = WideVancouver.homes
        assertTrue(homes.getValue(1).x < town.columns)
        val last = WideVancouver.stations.last().spot
        for (zone in listOf(2, 3)) {
            val cell = homes.getValue(zone)
            assertTrue(cell.x > town.columns + 200, "zone $zone")
            // The rule's marks: well inside the grid, away from the line's far end, and a walk from a station.
            assertTrue(cell.x in 5 until map.columns - 5 && cell.z in 5 until map.rows - 5, "zone $zone at $cell")
            assertTrue(distanceBetween(centre(cell), last) > 100.0, "zone $zone at $cell")
            assertTrue(WideVancouver.stations.any { distanceBetween(it.spot, centre(cell)) < 40.0 }, "zone $zone")
        }
        for (area in WideVancouver.zones.values.flatten()) for (spot in area) assertTrue(onMap(spot), "$spot")
    }

    @Test
    fun `the walk from the room in zone 2 to the office takes under two minutes`() {
        val grid = map.grid
        val from = centre(WideVancouver.homes.getValue(2))
        val to = centre(RealVancouver.spots.getValue(Place.OFFICE))
        val route = grid.route(from, to)
        assertEquals(to, route.last())
        assertTrue(route.zipWithNext().all { (a, b) -> grid.isClear(a, b) })
        val seconds = route.zipWithNext().sumOf { (a, b) -> distanceBetween(a, b) } / Trip.WALK_SPEED
        assertTrue(seconds in 92.0..110.0, "$seconds s")
    }

    @Test
    fun `the longest walk on the wide map is found quickly`() {
        // From the big park's western tip to the far corner by the Fraser in Burnaby.
        val grid = map.grid
        val free = grid.freeCells()
        val west = centre(free.minWith(compareBy({ it.x }, { it.z })))
        val far = centre(free.maxWith(compareBy({ it.x + it.z }, { it.x })))
        // The best of three, so a pause of the browser's own in one of them does not count against the search.
        val millis = (1..3).minOf {
            val start = Date().getTime()
            grid.route(west, far)
            Date().getTime() - start
        }
        val route = grid.route(west, far)
        assertEquals(far, route.last())
        val length = route.zipWithNext { a, b -> distanceBetween(a, b) }.sum()
        assertTrue(length > 650.0, "$length")
        val seconds = (length / Trip.WALK_SPEED).toInt()
        println("The longest walk on the wide map: ${length.toInt()} squares, $seconds s, found in $millis ms")
        assertTrue(millis < 300, "took $millis ms")
    }

    @Test
    fun `the blocks, streets and zones lie on the map, right where they belong`() {
        val blocks = WideVancouver.blocks
        assertTrue(blocks.size > 1_000, "${blocks.size}")
        for (lot in blocks) {
            assertTrue(lot.middle.x >= town.columns && onMap(lot.middle), "$lot")
            val ground = map.terrainAt(map.grid.cellAt(lot.middle))
            assertTrue(ground == Terrain.LAND || ground == Terrain.SHORE, "$lot on $ground")
            assertTrue(lot.width > 0 && lot.depth > 0 && lot.width * lot.depth < 1_000, "$lot")
            assertTrue(abs(lot.turn) <= PI / 4 + 0.01, "$lot")
        }
        assertTrue(WideVancouver.streets.size > 50)
        for (spot in WideVancouver.streets.flatten()) assertTrue(onMap(spot), "$spot")
        assertNotNull(WideVancouver.zoneAt(centre(RealVancouver.spots.getValue(Place.LIBRARY))))
        assertEquals(null, WideVancouver.zoneAt(Spot(400.0, 100.0)))
    }
}
