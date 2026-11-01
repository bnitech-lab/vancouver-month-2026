package vancouvermonth.web

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * East Vancouver's and Burnaby's residential blocks as detached houses: smaller, lower and farther apart the farther
 * from downtown, only on residential blocks and never on a street, the guideway, a park, the water or a lot kept clear,
 * none overlapping another, each blocking only its own squares.
 */
class SuburbsTest {
    private val houses = Neighbourhood.houses
    private val yards = Neighbourhood.yards

    /** Each house with the yard between it and the next one along its row, by how far out its block is. */
    private fun gaps(): List<Pair<Double, Double>> = yards.flatMap { yard ->
        val share = Suburbs.share(yard.middle)
        yard.houses.groupBy { it.front }.values.flatMap { row ->
            row.zipWithNext { a, b ->
                share to distanceBetween(Spot(a.shape.x, a.shape.z), Spot(b.shape.x, b.shape.z)) - (a.shape.width + b.shape.width) / 2
            }
        }
    }

    /** True when the two turned boxes on the ground overlap: tried along each one's two sides' ways. */
    private fun overlap(a: TurnedBox, b: TurnedBox): Boolean {
        val ways = listOf(a.turn, a.turn + kotlin.math.PI / 2, b.turn, b.turn + kotlin.math.PI / 2)
        return ways.all { turn ->
            val x = cos(turn)
            val z = -sin(turn)
            fun reach(box: TurnedBox): Double =
                abs(box.width / 2 * (cos(box.turn) * x - sin(box.turn) * z)) +
                    abs(box.depth / 2 * (sin(box.turn) * x + cos(box.turn) * z))
            abs((b.x - a.x) * x + (b.z - a.z) * z) < reach(a) + reach(b) - 1e-9
        }
    }

    @Test
    fun `away from downtown the blocks are detached houses, smaller, lower and farther apart the farther out`() {
        assertTrue(houses.size > 2_000, "only ${houses.size} houses")
        assertTrue(yards.size > 1_000, "only ${yards.size} blocks of houses")
        val near = houses.filter { Suburbs.share(Spot(it.shape.x, it.shape.z)) < 0.25 }
        val far = houses.filter { Suburbs.share(Spot(it.shape.x, it.shape.z)) > 0.75 }
        assertTrue(near.size > 200 && far.size > 200, "${near.size} near, ${far.size} far")
        // Smaller and lower out there.
        assertTrue(far.map { it.shape.width }.average() < near.map { it.shape.width }.average() - 0.15)
        assertTrue(far.map { it.wall }.average() < near.map { it.wall }.average() - 0.25)
        assertTrue(far.maxOf { it.wall + it.ridge } < near.maxOf { it.wall + it.ridge })
        // And the yards between them wider, on the whole twice as wide.
        val gaps = gaps()
        val nearGap = gaps.filter { it.first < 0.25 }.map { it.second }.average()
        val farGap = gaps.filter { it.first > 0.75 }.map { it.second }.average()
        println("Mean yard between houses: $nearGap near downtown, $farGap far out; ${houses.size} houses on ${yards.size} blocks")
        assertTrue(farGap > 2 * nearGap, "$nearGap near, $farGap far")
        assertTrue(gaps.all { (share, gap) -> gap >= Suburbs.gap(share) - 1e-9 }, "a yard narrower than its least")
        // Every house is lower than the city's mid-rise and a little taller than the character.
        assertTrue(houses.all { it.wall + it.ridge in 1.3..2.6 }, "${houses.minOf { it.wall + it.ridge }} to ${houses.maxOf { it.wall + it.ridge }}")
    }

    @Test
    fun `houses stand only on residential blocks, inside them, off the streets, the guideway, parks and water`() {
        val streets = WideVancouver.streets + TransitLines.busLines.map { it.loop.points }
        val rail = Neighbourhood.guideway.points
        val stations = WideVancouver.stations
        val centre = stations[TransitLines.TOWN_CENTRE].spot
        for (yard in yards) {
            // Mid-rise round the stations and towers round the town centre stand on their own blocks.
            assertTrue(distanceBetween(yard.middle, centre) >= Neighbourhood.TOWN_CENTRE_REACH, "${yard.middle}")
            assertTrue(stations.all { distanceBetween(it.spot, yard.middle) >= Neighbourhood.STATION_REACH }, "${yard.middle}")
            for (house in yard.houses) {
                for (corner in Suburbs.cornersOf(house.shape)) {
                    assertEquals(0.0, yard.block.distanceTo(corner.x, corner.z), 1e-9, "$corner outside its block")
                    val terrain = Neighbourhood.map.terrainAt(floor(corner.x).toInt(), floor(corner.z).toInt())
                    assertTrue(terrain == Terrain.LAND || terrain == Terrain.SHORE, "$corner on $terrain")
                }
            }
        }
        for (house in houses) {
            val shape = house.shape
            for (line in streets + listOf(rail)) {
                val clear = if (line === rail) 1.5 else Neighbourhood.WIDE_STREET_CLEAR
                for ((a, b) in line.zipWithNext()) {
                    if (distanceBetween(a, Spot(shape.x, shape.z)) > 60 && distanceBetween(b, Spot(shape.x, shape.z)) > 60) continue
                    for (step in 0..20) {
                        val x = a.x + (b.x - a.x) * step / 20
                        val z = a.z + (b.z - a.z) * step / 20
                        assertTrue(shape.distanceTo(x, z) >= clear - 0.3, "${house.shape.x}, ${house.shape.z} on a way at $x, $z")
                    }
                }
            }
        }
    }

    @Test
    fun `no house stands on a lot kept clear, on the town's corner, on another house or a yard tree`() {
        val kept = Neighbourhood.venues.map { Neighbourhood.lotFor(it) } + Neighbourhood.shop.lot +
            Neighbourhood.homes.values.map { Neighbourhood.lotFor(it) } + TransitLines.keptForStops + Rides.keptForDocks +
            TransitLines.stations.mapNotNull { it.entrance } + Neighbourhood.cityLots
        for (house in houses) {
            assertTrue(house.shape.x > Neighbourhood.TOWN_COLUMNS, "${house.shape.x}")
            for (lot in kept) assertFalse(house.shape.overlaps(lot, 0.0), "${house.shape.x}, ${house.shape.z} on $lot")
        }
        // Houses in buckets of the ground, so each is tried only against those near it.
        val buckets = houses.groupBy { floor(it.shape.x / 8).toInt() to floor(it.shape.z / 8).toInt() }
        var tried = 0
        for (house in houses) {
            val column = floor(house.shape.x / 8).toInt()
            val row = floor(house.shape.z / 8).toInt()
            for (dx in -1..1) for (dz in -1..1) {
                for (other in buckets[column + dx to row + dz].orEmpty()) {
                    if (other === house) continue
                    tried++
                    assertFalse(overlap(house.shape, other.shape), "${house.shape.x}, ${house.shape.z} on ${other.shape.x}, ${other.shape.z}")
                }
            }
        }
        assertTrue(tried > houses.size, "only $tried pairs tried")
        // A yard tree's crown clear of every house, its own block's or a neighbour's.
        for (yard in yards) {
            for (crown in yard.trees.filterIndexed { index, _ -> index % 2 == 1 }) {
                val shape = TurnedBox(crown.x + crown.width / 2, crown.z + crown.depth / 2, crown.width, crown.depth, 0.0)
                val column = floor(shape.x / 8).toInt()
                val row = floor(shape.z / 8).toInt()
                for (dx in -1..1) for (dz in -1..1) {
                    for (house in buckets[column + dx to row + dz].orEmpty()) {
                        assertFalse(overlap(shape, house.shape), "a tree at ${shape.x}, ${shape.z} in a house")
                    }
                }
            }
        }
    }

    @Test
    fun `a house blocks only the squares it stands on, so the yards between houses are walked`() {
        val open = Neighbourhood.open
        for (house in houses) assertFalse(open.isFree(Cell(floor(house.shape.x).toInt(), floor(house.shape.z).toInt())))
        // Squares inside the blocks, clear of every house and yard tree near them, are free: the yards. A guideway pillar
        // or a stop's shelter may stand on the odd one.
        val near = yards.groupBy { floor(it.middle.x / 8).toInt() to floor(it.middle.z / 8).toInt() }
        var yardSquares = 0
        var free = 0
        for (yard in yards) {
            val round = (-1..1).flatMap { dx -> (-1..1).flatMap { dz ->
                near[floor(yard.middle.x / 8).toInt() + dx to floor(yard.middle.z / 8).toInt() + dz].orEmpty()
            } }
            val box = yard.block.bounds(0.0, 0)
            for (x in floor(box.x).toInt()..floor(box.x + box.width).toInt()) {
                for (z in floor(box.z).toInt()..floor(box.z + box.depth).toInt()) {
                    if (yard.block.distanceTo(x + 0.5, z + 0.5) > 0.0 || Neighbourhood.map.terrainAt(x, z) == Terrain.WATER) continue
                    val clear = round.all { other ->
                        other.houses.all { it.shape.distanceTo(x + 0.5, z + 0.5) >= Grid.CLEARANCE } &&
                            other.trees.none { it.x < x + 1 && it.x + it.width > x && it.z < z + 1 && it.z + it.depth > z }
                    }
                    if (!clear) continue
                    yardSquares++
                    if (open.isFree(Cell(x, z))) free++
                }
            }
        }
        println("Squares of yard: $free free of $yardSquares")
        assertTrue(yardSquares > 1_000, "only $yardSquares squares of yard")
        assertTrue(free > yardSquares * 0.97, "only $free of $yardSquares squares of yard are free")
    }

    @Test
    fun `far off, each row of houses is one low box as long as the row, and out where the yards are wide each house one`() {
        var rows = 0
        for (yard in yards) {
            assertTrue(yard.rows >= 1, "${yard.middle}")
            rows += yard.rows
            for (index in yard.firstRow until yard.firstRow + yard.rows) {
                val row = Neighbourhood.blocks[index]
                assertEquals(yard.houses.first().shape.turn, row.turn, 1e-9)
                assertTrue(row.height <= yard.houses.maxOf { it.wall } + 1e-9)
                // It stands over its houses' middles, inside the block.
                val middle = Spot(row.x + row.width / 2, row.z + row.depth / 2)
                assertEquals(0.0, yard.block.distanceTo(middle.x, middle.z), 1e-9)
            }
            for (house in yard.houses) {
                val rowOf = (yard.firstRow until yard.firstRow + yard.rows).map { Neighbourhood.blocks[it] }.any { row ->
                    TurnedBox(row.x + row.width / 2, row.z + row.depth / 2, row.width, row.depth, row.turn)
                        .distanceTo(house.shape.x, house.shape.z) == 0.0
                }
                assertTrue(rowOf, "${house.shape.x}, ${house.shape.z} has no low box far off")
            }
        }
        // In a row drawn as one box, each house's share of it runs from halfway to the house before to halfway to the
        // next: the shares fill the box end to end, as tall as it, so a house coming near grows out of it seamlessly.
        var shared = 0
        for (yard in yards) {
            for (row in yard.houses.groupBy { it.front }.values) {
                if (row.all { it.slotWidth == it.shape.width && it.slotShift == 0.0 && it.farWall == it.wall }) continue
                shared++
                val first = row.first().shape
                val last = row.last().shape
                val length = distanceBetween(Spot(first.x, first.z), Spot(last.x, last.z)) + first.width
                assertEquals(length, row.sumOf { it.slotWidth }, 1e-9)
                assertTrue(row.all { it.farWall == row.sumOf { house -> house.wall } / row.size })
                for ((a, b) in row.zipWithNext()) {
                    // Where a's share ends, from a's middle, is where b's begins, from b's middle and then on to a's.
                    val apart = distanceBetween(Spot(a.shape.x, a.shape.z), Spot(b.shape.x, b.shape.z))
                    assertEquals(a.slotShift + a.slotWidth / 2, apart + b.slotShift - b.slotWidth / 2, 1e-9)
                }
            }
        }
        assertTrue(shared > 300, "only $shared rows shared out")
        // Up to halfway out, about one for each block, as the city had before the houses; farther out, one a house, so
        // the wide yards show from far off too.
        val (near, far) = yards.partition { Suburbs.share(it.middle) <= Suburbs.ROWS_UP_TO }
        assertTrue(near.sumOf { it.rows } < near.size * 1.3, "${near.sumOf { it.rows }} low boxes for ${near.size} blocks")
        assertEquals(far.sumOf { it.houses.size }, far.sumOf { it.rows })
    }

    @Test
    fun `a block is laid out the same every time, and a narrow one holds a single house or none`() {
        val block = TurnedBox(300.0, 50.0, 3.5, 1.9, 0.78)
        val one = Suburbs.layOut(block, 0.1, listOf(0xEEEEEE), Random(5))
        val again = Suburbs.layOut(block, 0.1, listOf(0xEEEEEE), Random(5))
        assertEquals(one.houses.map { it.shape.x to it.wall }, again.houses.map { it.shape.x to it.wall })
        assertEquals(2, one.houses.size)
        // Out in Burnaby the same block holds one house, with wide yards either side.
        assertEquals(1, Suburbs.layOut(block, 0.9, listOf(0xEEEEEE), Random(5)).houses.size)
        assertTrue(Suburbs.layOut(TurnedBox(300.0, 50.0, 3.5, 0.6, 0.78), 0.1, listOf(0xEEEEEE), Random(5)).houses.isEmpty())
    }

    @Test
    fun `a house between the camera and the character folds, one behind it stands, and a tap on a house stops at its wall`() {
        val foldables = Neighbourhood.foldables
        val cutaway = Cutaway(foldables.map { it.box }, foldables.map { Neighbourhood.blocks[it.body] })
        // A turned house out in Burnaby, and the character on the street three squares out from its door.
        val house = houses.first { Suburbs.share(Spot(it.shape.x, it.shape.z)) > 0.6 && it.shape.turn % (kotlin.math.PI / 2) != 0.0 }
        val shape = house.shape
        val outX = sin(shape.turn) * house.front
        val outZ = cos(shape.turn) * house.front
        val out = shape.depth / 2 + 3.0
        val head = Point(shape.x + outX * out, 1.6, shape.z + outZ * out)
        // The camera behind the house, looking over it at the character: the house folds.
        cutaway.aim(shape.x - outX * 12, 6.0, shape.z - outZ * 12, head.x, head.y, head.z)
        assertTrue(cutaway.isFolding(house.foldable), "the house in the way stands")
        // The camera out on the street, the house behind the character: it stands.
        val street = Point(shape.x + outX * (out + 18), 9.0, shape.z + outZ * (out + 18))
        cutaway.aim(street.x, street.y, street.z, head.x, head.y, head.z)
        assertFalse(cutaway.isFolding(house.foldable), "the house behind the character folds")
        // From there, a tap on the house's front stops at its wall, not on the ground behind it.
        val camera = FollowCamera(head)
        camera.aspect = 375.0 / 812
        val yaw = kotlin.math.atan2(outX, outZ)
        camera.orbit((camera.yaw - yaw) / FollowCamera.TURN_PER_PIXEL, 0.0)
        camera.jumpTo(head)
        val wall = kotlin.test.assertNotNull(camera.screenPoint(Point(shape.x, house.wall / 2, shape.z)))
        val picked = camera.pick(wall.left, wall.top, cutaway = cutaway) as Picked.OnGround
        assertTrue(shape.distanceTo(picked.spot.x, picked.spot.z) < 0.05, "${picked.spot} is not at the house")
    }

    @Test
    fun `내 방 in zones 2 and 3 keeps its lot, and its neighbours are houses`() {
        for (zone in 2..3) {
            val home = Neighbourhood.home(zone)
            val lot = Neighbourhood.lotFor(home)
            assertTrue(houses.none { it.shape.overlaps(lot, 0.0) }, "zone $zone")
            val round = houses.count { distanceBetween(Spot(it.shape.x, it.shape.z), home.door) < 20 }
            assertTrue(round >= 6, "only $round houses round zone $zone's 내 방")
        }
    }
}
