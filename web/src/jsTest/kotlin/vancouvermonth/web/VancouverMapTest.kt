package vancouvermonth.web

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VancouverMapTest {
    private val map = RealVancouver.map
    private val spots = RealVancouver.spots

    private fun centre(cell: Cell) = Spot(cell.x + 0.5, cell.z + 0.5)

    private fun onMap(cell: Cell) = cell.x in 0 until map.columns && cell.z in 0 until map.rows

    /** What lies from [from] in steps of [dx] and [dz] up to the map's edge, [from] included. */
    private fun lookingFrom(from: Cell, dx: Int, dz: Int): List<Terrain> =
        generateSequence(from) { Cell(it.x + dx, it.z + dz) }.takeWhile { onMap(it) }.map { map.terrainAt(it) }.toList()

    /** Where [line] is at [x], its ends carried on level past the first and last points. */
    private fun zOf(line: List<Spot>, x: Double): Double {
        if (x <= line.first().x) return line.first().z
        val (a, b) = line.zipWithNext().firstOrNull { (_, b) -> x <= b.x } ?: return line.last().z
        return a.z + (b.z - a.z) * (x - a.x) / (b.x - a.x)
    }

    @Test
    fun `the map is as big as its data says, with every square one of the four kinds`() {
        assertEquals(VancouverMapData.COLUMNS, map.columns)
        assertEquals(VancouverMapData.ROWS, map.rows)
        assertEquals(map.columns * map.rows, Terrain.entries.sumOf { map.cellsOf(it).size })
        // Far bigger than the 48 by 32 of now, as the walks are longer, but the ground still comes to a few hundred
        // boxes once each run of the same squares is one box.
        assertTrue(map.columns * map.rows < 50_000, "${map.columns} x ${map.rows}")
    }

    @Test
    fun `a map is read row by row from the north, and a strange square or a short row is refused`() {
        val small = TerrainMap.parse(listOf("~~s", "p.~"))
        assertEquals(3, small.columns)
        assertEquals(2, small.rows)
        assertEquals(Terrain.SHORE, small.terrainAt(Cell(2, 0)))
        assertEquals(Terrain.PARK, small.terrainAt(Cell(0, 1)))
        assertEquals(Terrain.LAND, small.terrainAt(Cell(1, 1)))
        assertEquals(Terrain.WATER, small.terrainAt(Cell(5, -1)))
        assertEquals(listOf(Cell(0, 0), Cell(1, 0), Cell(2, 1)), small.cellsOf(Terrain.WATER))
        assertEquals(setOf(Cell(2, 0), Cell(0, 1), Cell(1, 1)), small.grid.freeCells().toSet())
        assertFailsWith<IllegalArgumentException> { TerrainMap.parse(listOf("~x~")) }
        assertFailsWith<IllegalArgumentException> { TerrainMap.parse(listOf("~~~", "~~")) }
        assertEquals(listOf(Spot(1.0, 2.5), Spot(-3.0, 4.0)), pointsOf("1.0,2.5 -3.0,4.0"))
    }

    @Test
    fun `packed numbers are read as changes from the item before, and a broken packing is refused`() {
        // The changes 0, 1, -1, 15, 16 and -16 are A, C, D, e, gB and hB, so the numbers run 0, 1, 0, 15, 31, 15.
        assertEquals(listOf(0, 1, 0, 15, 31, 15), unpackNumbers("ACDegBhB", 1).map { it.single() })
        assertEquals(listOf(listOf(1, 2), listOf(1, 18)), unpackNumbers("CEAgB", 2).map { it.toList() })
        assertEquals(emptyList(), unpackNumbers("", 5))
        assertEquals(listOf(Spot(1.0, 2.5), Spot(-3.0, 4.0)), unpackPoints("UyBxCe"))
        assertFailsWith<IllegalArgumentException> { unpackNumbers("C,E", 2) }
        assertFailsWith<IllegalArgumentException> { unpackNumbers("CEA", 2) }
        assertFailsWith<IllegalArgumentException> { unpackNumbers("Cg", 1) }
    }

    /**
     * Each packed number is the change from the one before, so the last point of a line stands for every character
     * before it. The numbers are the ones `tools/vancouver_map/build_map.py` prints ("packed town shoreline", "packed
     * town park"); they change only when the map is made again.
     */
    @Test
    fun `the packed shoreline and park read back to the numbers the map script printed`() {
        val shore = RealVancouver.shoreLines
        assertEquals(listOf(2, 2, 420), shore.map { it.size })
        assertEquals(Spot(205.8, -1.0), shore.first().first())
        assertEquals(Spot(113.5, 156.0), shore.last().last())
        val park = RealVancouver.park
        assertEquals(133, park.size)
        assertEquals(Spot(93.6, 66.1), park.first())
        assertEquals(Spot(93.6, 66.1), park.last())
    }

    @Test
    fun `water lies north, west and south of the peninsula`() {
        // The inlet, north of the office towers and the old brick streets.
        for (place in listOf(Place.OFFICE, Place.COWORKING)) {
            assertTrue(Terrain.WATER in lookingFrom(spots.getValue(place), 0, -1), "north of $place")
        }
        // West of 내 방: the park, then English Bay out to the map's edge.
        val west = lookingFrom(spots.getValue(Place.HOME), -1, 0)
        assertTrue(Terrain.PARK in west, "$west")
        assertEquals(Terrain.WATER, west.last())
        // False Creek south of the café and the library, and its south shore past it.
        for (place in listOf(Place.CAFE, Place.LIBRARY)) {
            val south = lookingFrom(spots.getValue(place), 0, 1)
            val creek = south.indexOf(Terrain.WATER)
            assertTrue(creek > 0, "south of $place")
            assertTrue(south.drop(creek).any { it != Terrain.WATER }, "the south shore past $place")
        }
    }

    @Test
    fun `the big park fills the peninsula's tip, north-west of every place`() {
        val park = map.cellsOf(Terrain.PARK)
        // The real park is a little over 400 hectares; its edge squares count as shore.
        val hectares = park.size * RealVancouver.METRES_PER_UNIT * RealVancouver.METRES_PER_UNIT / 10_000
        assertTrue(hectares in 350.0..450.0, "$hectares ha")
        val middle = Spot(park.sumOf { it.x }.toDouble() / park.size, park.sumOf { it.z }.toDouble() / park.size)
        assertTrue(middle.x < map.columns / 3.0 && middle.z < map.rows / 2.0, "$middle")
        for ((place, cell) in spots) assertTrue(cell.x > middle.x + 30, "$place")
    }

    @Test
    fun `each place has its own square of open land, with room for a building around it`() {
        assertEquals(Place.entries.toSet(), spots.keys)
        for ((place, cell) in spots) {
            for (dx in -2..2) {
                for (dz in -2..2) assertEquals(Terrain.LAND, map.terrainAt(Cell(cell.x + dx, cell.z + dz)), "$place")
            }
        }
        val cells = spots.values.toList()
        for ((index, a) in cells.withIndex()) {
            for (b in cells.drop(index + 1)) assertTrue(hypot((a.x - b.x).toDouble(), (a.z - b.z).toDouble()) > 20)
        }
    }

    @Test
    fun `every walkable square can be reached from every other`() {
        val grid = map.grid
        val walkable = grid.freeCells().toSet()
        val notWater = Terrain.entries.filter { it != Terrain.WATER }.sumOf { map.cellsOf(it).size }
        assertEquals(notWater, walkable.size)
        assertEquals(walkable, grid.reachableFrom(spots.getValue(Place.HOME)))
    }

    @Test
    fun `the farthest walk between two of the five places takes about 25 seconds`() {
        val grid = map.grid
        val seconds = Place.entries.flatMapIndexed { index, a ->
            Place.entries.drop(index + 1).map { b ->
                val route = grid.route(centre(spots.getValue(a)), centre(spots.getValue(b)))
                assertEquals(centre(spots.getValue(b)), route.last(), "$a to $b")
                assertTrue(route.zipWithNext().all { (p, q) -> grid.isClear(p, q) }, "$a to $b")
                route.zipWithNext().sumOf { (p, q) -> distanceBetween(p, q) } / Trip.WALK_SPEED
            }
        }
        assertTrue(seconds.max() in 23.0..25.0, "${seconds.max()} s")
        assertTrue(seconds.min() > 5.0, "${seconds.min()} s")
    }

    @Test
    fun `the shore is exactly the walkable squares beside the water`() {
        val wrong = mutableListOf<Cell>()
        for (z in 0 until map.rows) {
            for (x in 0 until map.columns) {
                val terrain = map.terrainAt(Cell(x, z))
                if (terrain == Terrain.WATER) continue
                val besideWater = (-1..1).any { dx ->
                    (-1..1).any { dz ->
                        val next = Cell(x + dx, z + dz)
                        onMap(next) && map.terrainAt(next) == Terrain.WATER
                    }
                }
                if (besideWater != (terrain == Terrain.SHORE)) wrong.add(Cell(x, z))
            }
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `the rail runs on land from the stadiums east to the map's edge`() {
        val rail = RealVancouver.rail
        assertEquals(map.columns.toDouble(), rail.last().x, 1e-9)
        for ((a, b) in rail.zipWithNext()) {
            val steps = (distanceBetween(a, b) / 0.5).toInt() + 1
            for (step in 0 until steps) {
                val spot = Spot(a.x + (b.x - a.x) * step / steps, a.z + (b.z - a.z) * step / steps)
                assertTrue(map.terrainAt(map.grid.cellAt(spot)) != Terrain.WATER, "$spot")
            }
        }
        // It starts east of the library, in the old town's direction, and north of the café.
        assertTrue(rail.first().x > spots.getValue(Place.LIBRARY).x && rail.first().z < spots.getValue(Place.CAFE).z)
    }

    @Test
    fun `nothing walkable lies past the line the mountains stand behind`() {
        val line = RealVancouver.mountains
        val walkable = mutableListOf<Cell>()
        for (x in 0 until map.columns) {
            val edge = zOf(line, x + 0.5)
            for (z in 0 until map.rows) {
                if (z + 1 <= edge && map.terrainAt(Cell(x, z)) != Terrain.WATER) walkable.add(Cell(x, z))
            }
        }
        assertEquals(emptyList(), walkable)
        // And the line comes down to the water off the park's northern tip.
        assertTrue(line.first().z > 0 && line.last().z < 0, "$line")
    }

    @Test
    fun `a street at least a square wide runs between any two city blocks side by side`() {
        val lots = (VancouverMapData.BLOCKS + " " + VancouverMapData.GREENS).split(' ').filter { it.isNotEmpty() }.map {
            val (x, z, width, depth) = it.split(',').map(String::toInt)
            Lot(x, z, width, depth)
        }
        assertTrue(lots.size > 400)
        for ((index, a) in lots.withIndex()) {
            for (b in lots.drop(index + 1)) {
                val apartX = maxOf(b.x - (a.x + a.width), a.x - (b.x + b.width))
                val apartZ = maxOf(b.z - (a.z + a.depth), a.z - (b.z + b.depth))
                // Apart on one axis by a square or more, or meeting at a corner only.
                assertTrue(apartX >= 1 || apartZ >= 1 || (apartX == 0 && apartZ == 0), "$a and $b")
            }
        }
    }

    @Test
    fun `the traced lines lie on the map`() {
        val lines = RealVancouver.shoreLines + listOf(RealVancouver.park)
        assertTrue(RealVancouver.shoreLines.isNotEmpty() && RealVancouver.park.size > 20)
        for (point in lines.flatten()) {
            assertTrue(point.x in -1.0..map.columns + 1.0 && point.z in -1.0..map.rows + 1.0, "$point")
        }
    }
}
