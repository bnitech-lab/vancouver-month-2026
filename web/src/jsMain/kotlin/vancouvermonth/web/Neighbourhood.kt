package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import vancouvermonth.text.Townsperson

/**
 * One of the five places as a building the character walks to: its footprint on the grid, its height and colours,
 * the side its door is on, and where the place's person stands.
 */
data class Venue(
    val place: Place,
    val x: Int,
    val z: Int,
    val width: Int,
    val depth: Int,
    val height: Double,
    val wall: Int,
    val roof: Int,
    /** True for a door on the south side, facing the street from the north row; false for the north side. */
    val doorFacesSouth: Boolean,
    /** Where the place's person stands, by the door. */
    val person: Spot,
    /** A pitched roof, as on a cottage; false for a flat one. */
    val pitched: Boolean,
) {
    /** Where the character stands at the door: on the pavement, half a square out. Each day starts at 내 방's. */
    val door: Spot
        get() = Spot(x + width / 2.0, if (doorFacesSouth) z + depth + 0.5 else z - 0.5)

    /** Where the character stands to talk: one square from the person, on the street side. */
    val talkSpot: Spot
        get() = Spot(person.x, person.z + if (doorFacesSouth) 1.0 else -1.0)

    /** The way the person faces, toward the street, as a turn in radians: 0 faces south, PI faces north. */
    val facing: Double
        get() = if (doorFacesSouth) 0.0 else PI

    /** Where the name floats: above the roof, over the side that faces the street. */
    val labelAnchor: Point
        get() {
            val front = if (doorFacesSouth) z + depth - 0.5 else z + 0.5
            return Point(x + width / 2.0, height + Neighbourhood.roofHeight(this) + 0.5, front)
        }

    /** The whole building as one box, roof included, for taps and for keeping the camera out. */
    val box: Block
        get() {
            val top = height + Neighbourhood.roofHeight(this)
            return Block(x.toDouble(), 0.0, z.toDouble(), width.toDouble(), top, depth.toDouble(), wall)
        }
}

/** The person at [place]: the one who offers the action a day there is spent on. */
fun personAt(place: Place): Townsperson = Townsperson.entries.first { it.offers == actionAt(place) }

/**
 * A box a tap stops at. [place] is set when it belongs to one of the five places. The camera stays out of it, unless it
 * [folds]: one of the city's buildings, which fold down out of the camera's way instead (see [Cutaway]).
 */
data class Wall(val box: Block, val place: Place?, val folds: Boolean = false)

/**
 * One of the city's buildings, or a low block past the grid's edge: none of the places the month is about, so it folds
 * down out of the camera's way rather than push the camera in. [box] is the whole building, roof included. [body] and
 * [roof] are where its walls and the plant room on its roof are in [Neighbourhood.blocks], and [glassNorth] and
 * [glassSouth] where its glass is in [Neighbourhood.glowing]; -1 for a part it does not have.
 */
data class Foldable(val box: Block, val body: Int, val roof: Int, val glassNorth: Int, val glassSouth: Int)

/** A rectangle of grid squares: columns [x] to [x] + [width] - 1, and rows [z] to [z] + [depth] - 1. */
data class Lot(val x: Int, val z: Int, val width: Int, val depth: Int) {
    fun overlaps(other: Lot): Boolean =
        x < other.x + other.width && other.x < x + width && z < other.z + other.depth && other.z < z + depth

    /** What is left of this lot once [hole] is taken out: up to four lots round it, or this one when they miss. */
    fun minus(hole: Lot): List<Lot> {
        if (!overlaps(hole)) return listOf(this)
        val right = x + width
        val bottom = z + depth
        val top = max(z, hole.z)
        val under = min(bottom, hole.z + hole.depth)
        return listOf(
            Lot(x, z, width, hole.z - z),
            Lot(x, hole.z + hole.depth, width, bottom - hole.z - hole.depth),
            Lot(x, top, hole.x - x, under - top),
            Lot(hole.x + hole.width, top, right - hole.x - hole.width, under - top),
        ).filter { it.width > 0 && it.depth > 0 }
    }

    /** True when the point [px], [pz] lies within [margin] of this lot. */
    fun isNear(px: Double, pz: Double, margin: Double): Boolean =
        px > x - margin && px < x + width + margin && pz > z - margin && pz < z + depth + margin
}

/**
 * The town the player walks in: real Vancouver, traced from the City's open data ([RealVancouver]), all drawn from
 * boxes. The peninsula's tip is the big park, a forest of round trees with the seawall round its shore; downtown and
 * the West End are the real city blocks, taller in the business district and the middle, low in the West End; False
 * Creek lies to the south with its south shore past it, and the inlet to the north with the mountains far across it.
 * The five places stand where they were proposed, 내 방 with its garden, bench and campfire, and the SkyTrain runs on
 * its guideway along False Creek, out of its tunnel by the station. The walkable part is the map's grid of [COLUMNS]
 * by [ROWS] squares, one square for 25 m of the city; the land goes on past it into the haze.
 */
object Neighbourhood {
    const val COLUMNS = VancouverMapData.COLUMNS
    const val ROWS = VancouverMapData.ROWS

    /** How close to a door or a person the character must stop for the place to open. */
    const val REACH = 0.9

    /** Far things fade into the sky's colour from this far from the camera, and are gone into it by [HAZE_FAR]. */
    const val HAZE_NEAR = 70.0
    const val HAZE_FAR = 230.0

    val venues: List<Venue> = listOf(
        // In the West End's residential streets, its door to the south and its garden to the west, so the first
        // view looks over it toward downtown.
        Venue(Place.HOME, 113, 92, 5, 5, 4.2, 0xF6E7CB, 0xC0584A, true, Spot(112.6, 96.4), pitched = true),
        // By the seawall on False Creek's north shore, facing a small square.
        Venue(Place.CAFE, 177, 111, 5, 4, 3.2, 0xF2C28B, 0x8A5A3A, false, Spot(181.5, 110.5), pitched = true),
        // In the civic blocks in the middle of downtown, on its wide street.
        Venue(Place.LIBRARY, 172, 65, 8, 5, 5.0, 0xEADCB9, 0x6E7F99, true, Spot(177.5, 70.5), pitched = true),
        // In the old brick streets at the east end, by the inlet.
        Venue(Place.COWORKING, 185, 37, 6, 5, 7.5, 0xBFD8C9, 0x4E7D6E, true, Spot(189.5, 42.5), pitched = false),
        // Among the towers of the business district, facing the inlet.
        Venue(Place.OFFICE, 150, 53, 7, 5, 14.0, 0xA9C7E3, 0x5A7590, false, Spot(155.5, 52.5), pitched = false),
    )

    fun venue(place: Place): Venue = venues.first { it.place == place }

    /** A box around each place's person, so a tap on them talks to them. */
    val people: List<Wall> = venues.map { venue ->
        Wall(Block(venue.person.x - 0.4, 0.0, venue.person.z - 0.4, 0.8, 1.8, 0.8, 0), venue.place)
    }

    private val home = venue(Place.HOME)

    /**
     * The bench in 내 방's garden, where the character sits by the campfire on a day of rest, facing north across
     * the fire.
     */
    val benchSeat = Spot(home.x - 2.7, home.z + home.depth - 1.1)
    const val BENCH_FACING = PI
    const val SEAT_HEIGHT = 0.42
    val campfire = Spot(home.x - 2.7, home.z + home.depth - 3.0)

    /** How high the logs lift the flames off the ground. */
    const val FLAME_BASE = 0.12

    /** The SkyTrain guideway's deck: how high it runs, and how wide. */
    const val DECK_HEIGHT = 7.0
    const val DECK_WIDTH = 1.0

    /**
     * The guideway's line: the real rail from where it comes out of its tunnel by the stadiums, east along False Creek
     * and on past the grid into the haze. Its first leg is laid straight along x, for the station.
     */
    val guideway: RailLine = RealVancouver.rail.let { rail ->
        val level = (rail[0].z + rail[1].z) / 2
        val line = listOf(Spot(rail[0].x, level), Spot(rail[1].x, level)) + rail.drop(2)
        val a = line[line.size - 2]
        val b = line.last()
        val leg = distanceBetween(a, b)
        val far = Spot(b.x + (b.x - a.x) / leg * PAST_THE_EDGE, b.z + (b.z - a.z) / leg * PAST_THE_EDGE)
        RailLine(line + far)
    }

    /** How far along the guideway the train's west end stands at the station, and the train's length. */
    const val TRAIN_STOP = 1.2
    const val TRAIN_LENGTH = 7.4

    /** Each of the train's three cars, and how far apart their west ends are along the guideway. */
    const val CARS = 3
    const val CAR_LENGTH = 2.4
    const val CAR_SPACING = 2.5

    /** One car, long along x around its own middle at the foot of the guideway: its body, and its windows. */
    val trainCar: List<Block> = listOf(
        Block(-CAR_LENGTH / 2, DECK_HEIGHT + 0.4, -0.45, CAR_LENGTH, 1.0, 0.9, TRAIN),
        Block(-CAR_LENGTH / 2, DECK_HEIGHT + 0.55, -0.48, CAR_LENGTH, 0.16, 0.96, SKYTRAIN_BLUE),
    )
    val trainCarWindows: List<Block> = listOf(Block(-0.95, DECK_HEIGHT + 0.8, -0.49, 1.9, 0.35, 0.98, WINDOW))

    /** The place whose door or person [spot] is within [REACH] of, or null when it is near none. */
    fun placeAt(spot: Spot): Place? = venues.firstOrNull { venue ->
        listOf(venue.door, venue.talkSpot).any { hypot(it.x - spot.x, it.z - spot.z) <= REACH }
    }?.place

    /**
     * Every box to draw that stands still, the people aside. [ground] holds what is too flat or too far to cast a
     * shadow (the ground, the water and the mountains), [glowing] the windows and lamps, and [blocks] the rest.
     */
    val blocks: List<Block>
    val ground: List<Block>
    val glowing: List<Block>

    /** The campfire's flames, which flicker: measured from the middle of the fire, [FLAME_BASE] above the ground. */
    val flames: List<Block>

    /** Where each blossom tree in the town stands; their petals drift down. */
    val blossoms: List<Spot>

    /** The city's buildings as built: a lot each, after the five places and the station took their room. */
    val cityLots: List<Lot>

    /** What stands on the ground and blocks the way: a footprint for each building, trunk, fence, bench and lamp. */
    private val footprints: List<Block>

    /**
     * What a tap stops at: the buildings (the five places among them), the SkyTrain deck, the station and its roof,
     * the tunnel's mouth, the café's awning and the office's canopy. The camera stays out of all but the city's
     * buildings, which fold down out of its way instead ([foldables]).
     */
    val walls: List<Wall>

    /**
     * The buildings that fold down out of the camera's way: the city's, and the low blocks past the grid's edges. The
     * five places, the station, the guideway and the train, 내 방's garden and the people never do.
     */
    val foldables: List<Foldable>

    val grid: Grid

    /** How many free squares were blocked because the character could never walk to them: walled-in pockets. */
    val pockets: Int

    // Set before the town is laid out, which picks from them.
    private val TOWER_COLOURS = listOf(0xA9C7E3, 0xBFD0DC, 0x9FB7C9, 0xC9D6DF, 0xB4C9D6)
    private val MID_COLOURS = listOf(0xE6DCCB, 0xD9CDB8, 0xCFD8DC, 0xE2E6E0, 0xD6E2EA)
    private val WEST_END_COLOURS = listOf(0xF0D7C8, 0xE9D3B4, 0xD6E2EA, 0xE2EBD3, 0xF2E3C6, 0xE4DDEB)
    private val BRICK_COLOURS = listOf(0xC98B6B, 0xB9785C, 0xD8A47F, 0xE0C9A6)
    private val LOW_COLOURS = listOf(0xE9D3B4, 0xD6E2EA, 0xF2E3C6, 0xE2EBD3, 0xD9CDB8)

    init {
        val town = Builder()
        town.addWater()
        town.addGround()
        town.addMountains()
        for (venue in venues) town.addVenue(venue)
        town.addHomeGarden()
        town.addSkyTrain()
        town.addCity()
        town.addTrees()
        town.addStreetLife()
        blocks = town.blocks
        ground = town.ground
        glowing = town.glowing
        flames = town.flames
        blossoms = town.blossoms
        cityLots = town.cityLots
        footprints = town.footprints
        foldables = town.foldables
        walls = venues.map { Wall(it.box, it.place) } + town.otherWalls.map { Wall(it, null) } +
            foldables.map { Wall(it.box, null, folds = true) }
        // Each person stands on one square, which the character walks around; the water is never walked on.
        val people = venues.map { Cell(floor(it.person.x).toInt(), floor(it.person.z).toInt()) }
        val water = RealVancouver.map.cellsOf(Terrain.WATER)
        val blocked = water + footprints.flatMap { cellsUnder(it) } + people
        // A pocket of ground walled in by buildings, trees and the water is no ground to walk on.
        val open = Grid(COLUMNS, ROWS, blocked)
        val reached = open.reachableFrom(open.cellAt(home.door))
        val walledIn = open.freeCells().filter { it !in reached }
        pockets = walledIn.size
        grid = Grid(COLUMNS, ROWS, blocked + walledIn)
    }

    /** The extra height a roof adds on top of the walls. */
    fun roofHeight(venue: Venue): Double = if (venue.pitched) PITCHED_ROOF else FLAT_ROOF

    /**
     * Where a tap at [tapped] sends a character standing at [from]: the spot itself on land, and on the water the last
     * free square on the straight way from [from] toward it before the water, so a tap on False Creek stops on the near
     * shore rather than leading all the way round the creek's head to the far one.
     */
    fun landing(from: Spot, tapped: Spot): Spot {
        val map = RealVancouver.map
        if (map.terrainAt(grid.cellAt(tapped)) != Terrain.WATER) return tapped
        val steps = max(1, ceil(distanceBetween(from, tapped) / LANDING_STEP).toInt())
        var last: Cell? = null
        for (step in 0..steps) {
            val spot = Spot(from.x + (tapped.x - from.x) * step / steps, from.z + (tapped.z - from.z) * step / steps)
            val cell = grid.cellAt(spot)
            if (map.terrainAt(cell) == Terrain.WATER) break
            if (grid.isFree(cell)) last = cell
        }
        return last?.let { grid.centreOf(it) } ?: from
    }

    /** The squares a box stands on. A box from x = 6.0 to 13.0 stands on columns 6 to 12. */
    private fun cellsUnder(box: Block): List<Cell> {
        val cells = mutableListOf<Cell>()
        for (x in floor(box.x).toInt() until ceil(box.x + box.width).toInt()) {
            for (z in floor(box.z).toInt() until ceil(box.z + box.depth).toInt()) cells.add(Cell(x, z))
        }
        return cells
    }

    /** The room each place takes from the city: its building, a square or a pavement in front, 내 방's garden. */
    private fun lotFor(venue: Venue): Lot {
        val front = if (venue.doorFacesSouth) venue.z - 1 else venue.z - FORECOURT
        val lot = Lot(venue.x - 1, front, venue.width + 2, venue.depth + 1 + FORECOURT)
        return if (venue.place == Place.HOME) Lot(lot.x - GARDEN, lot.z, lot.width + GARDEN, lot.depth + 2) else lot
    }

    private const val PITCHED_ROOF = 1.5
    private const val FLAT_ROOF = 0.3

    /** How tall the plant room on a tall tower's roof is. */
    private const val PLANT_ROOM = 0.5

    /** How far the guideway carries on past the grid's edge, and how deep a place's square is in front of it. */
    private const val PAST_THE_EDGE = 170.0
    private const val FORECOURT = 4
    private const val GARDEN = 5

    /** How finely a tap's straight way is looked along for the water's edge. */
    private const val LANDING_STEP = 0.25

    /** How far past the grid the land goes on, and the mountains past the line across the inlet. */
    private const val LAND_BEYOND = 200.0
    private const val MOUNTAINS_BACK = 52.0

    /** How many trees of the park's forest are drawn in full, nearest 내 방; the rest are simpler. */
    private const val FULL_TREES = 60

    /** Collects the boxes while the town is laid out. */
    private class Builder {
        val blocks = mutableListOf<Block>()
        val ground = mutableListOf<Block>()
        val glowing = mutableListOf<Block>()
        val flames = mutableListOf<Block>()
        val blossoms = mutableListOf<Spot>()
        val footprints = mutableListOf<Block>()
        val otherWalls = mutableListOf<Block>()
        val foldables = mutableListOf<Foldable>()
        val cityLots = mutableListOf<Lot>()
        private val map = RealVancouver.map
        private val random = Random(2026)

        fun add(x: Double, y: Double, z: Double, width: Double, height: Double, depth: Double, colour: Int) {
            blocks.add(Block(x, y, z, width, height, depth, colour))
        }

        /** A box that casts no shadow: flat, like the ground, or far away, like the mountains. */
        fun lay(x: Double, y: Double, z: Double, width: Double, height: Double, depth: Double, colour: Int) {
            ground.add(Block(x, y, z, width, height, depth, colour))
        }

        /** A box that also blocks the way, such as a trunk or a fence. */
        fun addSolid(x: Double, y: Double, z: Double, width: Double, height: Double, depth: Double, colour: Int) {
            add(x, y, z, width, height, depth, colour)
            footprints.add(Block(x, y, z, width, height, depth, colour))
        }

        fun glow(x: Double, y: Double, z: Double, width: Double, height: Double, depth: Double, colour: Int) {
            glowing.add(Block(x, y, z, width, height, depth, colour))
        }

        /** The sea all round, as one plane under everything: the inlet, English Bay and False Creek. */
        fun addWater() {
            lay(-WATER_REACH, -0.7, -WATER_REACH, COLUMNS + 2 * WATER_REACH, 0.3, ROWS + 2 * WATER_REACH, WATER)
        }

        /**
         * The ground of the grid, square by square as the map has it but drawn as few boxes as can cover it, each run
         * of the same ground one box: the streets, the small parks, the big park and the seawall round the shore. The
         * land goes on past the grid's edges into the haze.
         */
        fun addGround() {
            val kinds = IntArray(COLUMNS * ROWS) { index ->
                when (map.terrainAt(Cell(index % COLUMNS, index / COLUMNS))) {
                    Terrain.WATER -> NONE
                    Terrain.LAND -> STREET_GROUND
                    Terrain.PARK -> PARK_GROUND
                    Terrain.SHORE -> SHORE_GROUND
                }
            }
            for (green in lotsOf(VancouverMapData.GREENS)) {
                for (x in green.x until green.x + green.width) {
                    for (z in green.z until green.z + green.depth) kinds[z * COLUMNS + x] = GREEN_GROUND
                }
            }
            for ((lot, kind) in mergedRuns(kinds)) {
                val top = when (kind) {
                    SHORE_GROUND -> 0.03
                    STREET_GROUND -> 0.02
                    else -> 0.0
                }
                val colour = when (kind) {
                    SHORE_GROUND -> SEAWALL
                    STREET_GROUND -> STONE_STREET
                    GREEN_GROUND -> GRASS_LIGHT
                    else -> GRASS
                }
                val (x, z) = lot.x.toDouble() to lot.z.toDouble()
                lay(x, -0.7, z, lot.width.toDouble(), 0.7 + top, lot.depth.toDouble(), colour)
            }
            addLandBeyond()
        }

        /**
         * Each run of the same ground, grown first across and then down as far as it goes, so the whole grid is
         * covered by a few hundred boxes instead of one a square.
         */
        fun mergedRuns(kinds: IntArray): List<Pair<Lot, Int>> {
            val used = BooleanArray(kinds.size)
            val runs = mutableListOf<Pair<Lot, Int>>()
            for (z in 0 until ROWS) {
                for (x in 0 until COLUMNS) {
                    val kind = kinds[z * COLUMNS + x]
                    if (kind == NONE || used[z * COLUMNS + x]) continue
                    var width = 1
                    while (x + width < COLUMNS && kinds[z * COLUMNS + x + width] == kind &&
                        !used[z * COLUMNS + x + width]
                    ) width++
                    var depth = 1
                    while (z + depth < ROWS && (0 until width).all { kinds[(z + depth) * COLUMNS + x + it] == kind &&
                            !used[(z + depth) * COLUMNS + x + it] }
                    ) depth++
                    for (dz in 0 until depth) for (dx in 0 until width) used[(z + dz) * COLUMNS + x + dx] = true
                    runs.add(Lot(x, z, width, depth) to kind)
                }
            }
            return runs
        }

        /**
         * Land past the grid wherever the land reaches its edge, east, south and north, with low blocks of the city
         * going on into the haze.
         */
        fun addLandBeyond() {
            val far = LAND_BEYOND
            fun landAt(x: Int, z: Int) = map.terrainAt(Cell(x, z)) != Terrain.WATER
            val east = (0 until ROWS).filter { landAt(COLUMNS - 1, it) }
            val south = (0 until COLUMNS).filter { landAt(it, ROWS - 1) }
            val north = (0 until COLUMNS).filter { landAt(it, 0) }
            for ((from, size) in runsOf(east)) {
                lay(COLUMNS.toDouble(), -0.7, from.toDouble(), far, 0.72, size.toDouble(), LAND_FAR)
            }
            for ((from, size) in runsOf(south)) {
                lay(from.toDouble(), -0.7, ROWS.toDouble(), size.toDouble(), 0.72, far, LAND_FAR)
            }
            for ((from, size) in runsOf(north)) {
                lay(from.toDouble(), -0.7, -far, size.toDouble(), 0.72, far, LAND_FAR)
            }
            if (landAt(COLUMNS - 1, ROWS - 1)) lay(COLUMNS.toDouble(), -0.7, ROWS.toDouble(), far, 0.72, far, LAND_FAR)
            if (landAt(COLUMNS - 1, 0)) lay(COLUMNS.toDouble(), -0.7, -far, far, 0.72, far, LAND_FAR)
            // Low blocks on the land past the east and south edges, as far as the eye goes before the haze.
            for (z in 2 until ROWS step BEYOND_STEP) {
                if (!landAt(COLUMNS - 1, z) || !landAt(COLUMNS - 1, z + BEYOND_BLOCK)) continue
                for (gap in 0 until BEYOND_ROWS) addBeyond(COLUMNS + 2.0 + gap * BEYOND_STEP, z.toDouble())
            }
            for (x in 2 until COLUMNS step BEYOND_STEP) {
                if (!landAt(x, ROWS - 1) || !landAt(x + BEYOND_BLOCK, ROWS - 1)) continue
                for (gap in 0 until BEYOND_ROWS) addBeyond(x.toDouble(), ROWS + 2.0 + gap * BEYOND_STEP)
            }
        }

        private fun addBeyond(x: Double, z: Double) {
            val height = 3.0 + random.nextDouble() * 3.5
            val colour = LOW_COLOURS[random.nextInt(LOW_COLOURS.size)]
            val body = blocks.size
            add(x, 0.0, z, BEYOND_BLOCK.toDouble(), height, BEYOND_BLOCK.toDouble(), colour)
            val box = Block(x, 0.0, z, BEYOND_BLOCK.toDouble(), height, BEYOND_BLOCK.toDouble(), colour)
            foldables.add(Foldable(box, body, roof = -1, glassNorth = -1, glassSouth = -1))
        }

        /** The runs of numbers that follow on from each other in [numbers]: where each starts, and how long it is. */
        fun runsOf(numbers: List<Int>): List<Pair<Int, Int>> {
            val runs = mutableListOf<Pair<Int, Int>>()
            for (number in numbers) {
                val last = runs.lastOrNull()
                if (last != null && last.first + last.second == number) {
                    runs[runs.lastIndex] = last.first to last.second + 1
                } else {
                    runs.add(number to 1)
                }
            }
            return runs
        }

        /**
         * The north shore across the inlet: a dark band of forest past the line in the middle of the inlet, and
         * stepped peaks behind it, forest, rock, then snow on top, pushed well back as the real ones stand.
         */
        fun addMountains() {
            val line = RealVancouver.mountains
            for ((index, x) in (-90..360 step 26).withIndex()) {
                val shore = zAlong(line, x.toDouble())
                lay(x - 14.0, -0.5, shore - MOUNTAINS_BACK + 4, 28.0, 1.2, MOUNTAINS_BACK - 18, FOREST_DARK)
                val height = 30.0 + (index * 7) % 17
                val z = shore - MOUNTAINS_BACK - (index * 11) % 20
                val colours = listOf(FOREST, FOREST, FOREST_DARK, ROCK, ROCK_LIGHT, SNOW)
                val step = height / colours.size
                for ((level, colour) in colours.withIndex()) {
                    val width = 52.0 * (1 - level / 6.5)
                    lay(x - width / 2, level * step - 0.6, z - width / 3, width, step, width / 1.5, colour)
                }
            }
        }

        /** Where [line] runs at [x], carried on level past its ends. */
        private fun zAlong(line: List<Spot>, x: Double): Double {
            if (x <= line.first().x) return line.first().z
            val (a, b) = line.zipWithNext().firstOrNull { (_, b) -> x <= b.x } ?: return line.last().z
            return a.z + (b.z - a.z) * (x - a.x) / (b.x - a.x)
        }

        /** A building: walls, a door, windows on every side, a roof, and what marks the place out. */
        fun addVenue(venue: Venue) {
            val x = venue.x.toDouble()
            val z = venue.z.toDouble()
            val width = venue.width.toDouble()
            val depth = venue.depth.toDouble()
            add(x, 0.0, z, width, venue.height, depth, venue.wall)
            footprints.add(venue.box)
            if (venue.pitched) addPitchedRoof(x, z, width, depth, venue.height, venue.roof)
            else add(x - 0.15, venue.height, z - 0.15, width + 0.3, FLAT_ROOF, depth + 0.3, venue.roof)
            val doorX = x + width / 2
            val front = if (venue.doorFacesSouth) z + depth else z
            val out = if (venue.doorFacesSouth) 0.0 else -0.06
            add(doorX - 0.45, 0.0, front + out, 0.9, 1.6, 0.06, DOOR)
            add(doorX - 0.55, 1.6, front + out - 0.01, 1.1, 0.12, 0.08, WOOD_DARK)
            addWindows(x, z, width, depth, venue.height, doorX, venue.doorFacesSouth)
            when (venue.place) {
                Place.CAFE -> {
                    // An awning over the front in green and cream bands, and a small table outside. The awning stops
                    // short of the door's spot, so the character never stands under it.
                    for (band in 0 until (width * 2).toInt()) {
                        add(x + band * 0.5, 2.0, z - 0.35, 0.5, 0.14, 0.35, if (band % 2 == 0) AWNING else AWNING_LIGHT)
                    }
                    otherWalls.add(Block(x, 2.0, z - 0.35, width, 0.14, 0.35, AWNING))
                    addSolid(x + 0.6, 0.0, z - 1.3, 0.6, 0.7, 0.6, WOOD)
                }
                Place.LIBRARY -> add(x + 2.5, 0.0, z + depth, width - 5.0, 0.15, 0.4, STONE_LIGHT)
                Place.COWORKING -> add(x + 0.4, 0.3, z + depth, width - 0.8, 0.18, 0.08, SIGN_GREEN)
                Place.OFFICE -> {
                    // A canopy over the door, short of the door's spot, as the café's awning is.
                    val canopyZ = if (venue.doorFacesSouth) front else front - 0.35
                    val canopy = Block(doorX - 1.2, 2.2, canopyZ, 2.4, 0.15, 0.35, CONCRETE)
                    blocks.add(canopy)
                    otherWalls.add(canopy)
                }
                Place.HOME -> add(x + 3.6, venue.height, z + 1.2, 0.6, 1.8, 0.6, BRICK_DARK)
            }
        }

        /** A roof that steps up to a ridge running east to west, as a voxel cottage has. */
        fun addPitchedRoof(x: Double, z: Double, width: Double, depth: Double, top: Double, colour: Int) {
            val layers = 5
            for (layer in 0 until layers) {
                val inset = layer * (depth / 2) / layers
                add(x - 0.2, top + layer * 0.3, z - 0.25 + inset, width + 0.4, 0.3, depth + 0.5 - 2 * inset, colour)
            }
        }

        /**
         * Windows on all four sides, a row for each storey that fits. On the ground floor of the door's side they
         * leave the door clear.
         */
        fun addWindows(
            x: Double,
            z: Double,
            width: Double,
            depth: Double,
            height: Double,
            doorX: Double,
            doorSouth: Boolean,
        ) {
            var bottom = 0.75
            while (bottom + 0.7 < height - 0.2) {
                for (south in listOf(false, true)) {
                    val faceZ = if (south) z + depth else z - 0.05
                    for (column in 0 until (width / 1.4).toInt()) {
                        val centre = x + (column + 0.5) * width / (width / 1.4).toInt()
                        val nearDoor = bottom < 1.5 && south == doorSouth && abs(centre - doorX) < 1.0
                        if (!nearDoor) glow(centre - 0.28, bottom, faceZ, 0.56, 0.65, 0.05, WINDOW)
                    }
                }
                for (east in listOf(false, true)) {
                    val faceX = if (east) x + width else x - 0.05
                    for (column in 0 until (depth / 1.4).toInt()) {
                        val centre = z + (column + 0.5) * depth / (depth / 1.4).toInt()
                        glow(faceX, bottom, centre - 0.28, 0.05, 0.65, 0.56, WINDOW)
                    }
                }
                bottom += 1.4
            }
        }

        /**
         * 내 방's garden to the west of it, open to the street on the south: a fence on the other sides, a bench
         * facing a campfire, and flower beds.
         */
        fun addHomeGarden() {
            val west = home.x - GARDEN.toDouble()
            val north = home.z - 1.0
            for (row in 0..5) addSolid(west, 0.0, north + row, 0.15, 0.7, 1.0, WOOD)
            addSolid(west, 0.0, north, 5.0, 0.7, 0.15, WOOD)
            add(west - 0.02, 0.45, north, 0.19, 0.08, 6.0, WOOD_DARK)
            add(west, 0.45, north - 0.02, 5.0, 0.08, 0.19, WOOD_DARK)
            // A short bench, so there is a way past it into the garden.
            addBench(benchSeat.x, benchSeat.z, facingSouth = false, length = 1.3)
            // Stones in a ring, two crossed logs, and the fire, which glows after dark.
            val fire = campfire
            val stones = listOf(
                -0.5 to 0.0, 0.5 to 0.0, 0.0 to -0.5, 0.0 to 0.5,
                -0.36 to -0.36, 0.36 to 0.36, -0.36 to 0.36, 0.36 to -0.36,
            )
            for ((dx, dz) in stones) {
                add(fire.x + dx - 0.12, 0.0, fire.z + dz - 0.12, 0.24, 0.16, 0.24, STONE)
            }
            footprints.add(Block(fire.x - 0.6, 0.0, fire.z - 0.6, 1.2, 0.2, 1.2, STONE))
            add(fire.x - 0.4, 0.05, fire.z - 0.07, 0.8, 0.14, 0.14, WOOD_DARK)
            add(fire.x - 0.07, 0.05, fire.z - 0.4, 0.14, 0.14, 0.8, WOOD_DARK)
            flames.add(Block(-0.18, 0.0, -0.18, 0.36, 0.34, 0.36, FLAME))
            flames.add(Block(-0.1, 0.28, -0.1, 0.2, 0.26, 0.2, FLAME_LIGHT))
            for (x in listOf(0.6, 1.4, 3.6)) addFlowerBed(west + x, north + 1.4)
        }

        /**
         * The SkyTrain: its deck on pillars along the guideway, the station under it by the tunnel's mouth with its
         * roof over the platform, and the tunnel's mouth itself, where the train comes and goes.
         */
        fun addSkyTrain() {
            val deck = DECK_HEIGHT
            val points = guideway.points
            for ((a, b) in points.zipWithNext()) {
                val length = distanceBetween(a, b) + DECK_WIDTH / 2
                val middle = Spot((a.x + b.x) / 2, (a.z + b.z) / 2)
                val turn = kotlin.math.atan2(a.z - b.z, b.x - a.x)
                val left = middle.x - length / 2
                blocks.add(Block(left, deck, middle.z - DECK_WIDTH / 2, length, 0.4, DECK_WIDTH, CONCRETE, turn))
            }
            // For the camera, the deck as short pieces along the line, each straight on the grid.
            var along = 0.0
            while (along < guideway.length) {
                val spot = guideway.spotAt(along)
                if (spot.x > COLUMNS + HAZE_NEAR) break
                otherWalls.add(Block(spot.x - 0.5, deck, spot.z - 0.5, 1.0, 0.4, 1.0, CONCRETE))
                along += 0.8
            }
            val start = points.first()
            // The station, under the deck from the tunnel's mouth east, and its roof over the platform.
            val west = start.x + 0.3
            val length = STATION_LENGTH
            addSolid(west, 0.0, start.z - 1.5, length, deck, 3.0, STATION)
            otherWalls.add(Block(west, 0.0, start.z - 1.5, length, deck + 3.0, 3.0, STATION))
            add(west + length / 2 - 0.8, 0.0, start.z + 1.48, 1.6, 2.0, 0.05, DOOR_GLASS)
            add(west - 0.4, deck + 2.4, start.z - 1.2, length + 0.8, 0.2, 2.4, SKYTRAIN_BLUE)
            otherWalls.add(Block(west - 0.4, deck + 2.4, start.z - 1.2, length + 0.8, 0.2, 2.4, SKYTRAIN_BLUE))
            for (x in listOf(west - 0.2, west + length)) add(x, deck + 0.4, start.z - 1.1, 0.2, 2.0, 0.2, PILLAR)
            // The tunnel's mouth: a concrete block the guideway runs out of, dark where the train goes in. Its faces
            // keep off the squares' middle lines, where a camera looking straight along one would only graze it.
            val mouth = Block(start.x - 2.95, 0.0, start.z - 1.3, 2.9, deck + 2.6, 2.6, CONCRETE)
            addSolid(mouth.x, mouth.y, mouth.z, mouth.width, mouth.height, mouth.depth, mouth.colour)
            otherWalls.add(mouth)
            add(start.x - 0.07, deck + 0.35, start.z - 0.6, 0.05, 1.5, 1.2, TUNNEL)
            // Pillars from past the station all along, on into the haze.
            along = STATION_LENGTH + 4.0
            while (along < guideway.length) {
                val spot = guideway.spotAt(along)
                if (spot.x > COLUMNS + HAZE_FAR) break
                val pillar = Block(spot.x - 0.3, 0.0, spot.z - 0.3, 0.6, deck, 0.6, PILLAR)
                blocks.add(pillar)
                if (spot.x < COLUMNS) footprints.add(pillar)
                along += PILLAR_GAP
            }
        }

        /**
         * The city: a building on each real block, the five places' lots and the station's kept clear, and none under
         * the guideway. Taller in the business district round 회사 and in the middle of downtown, mid-height by False
         * Creek, low in the West End, the old streets to the east and across the creek.
         */
        fun addCity() {
            val start = guideway.points.first()
            val kept = venues.map { lotFor(it) } +
                Lot(floor(start.x).toInt() - 5, floor(start.z).toInt() - 4, STATION_LENGTH.toInt() + 8, 10)
            var lots = lotsOf(VancouverMapData.BLOCKS)
            for (hole in kept) lots = lots.flatMap { it.minus(hole) }
            val rail = mutableListOf<Spot>()
            var along = -3.0
            while (along < guideway.length && guideway.spotAt(along).x < COLUMNS + 1) {
                rail.add(guideway.spotAt(along))
                along += 0.5
            }
            for (lot in lots) {
                if (lot.width * lot.depth < 2) continue
                if (rail.any { lot.isNear(it.x, it.z, 1.0) }) continue
                cityLots.add(lot)
                val middleX = lot.x + lot.width / 2.0
                val middleZ = lot.z + lot.depth / 2.0
                var (height, colour) = storeys(middleX, middleZ)
                // Low beside the guideway, so the deck never runs into a wall.
                if (rail.any { lot.isNear(it.x, it.z, 4.0) }) height = min(height, DECK_HEIGHT - 1.5)
                addBuilding(lot, height, colour)
            }
        }

        /** How tall a building at [x], [z] stands, and its colour, by the district it is in. */
        fun storeys(x: Double, z: Double): Pair<Double, Int> {
            val chance = random.nextDouble()
            val pick = random.nextDouble()
            return when {
                hypot(x - 157.0, z - 58.0) < 20.0 -> 9.0 + chance * 8.0 to TOWER_COLOURS.pick(pick)
                hypot(x - 172.0, z - 76.0) < 24.0 -> 6.5 + chance * 6.0 to MID_COLOURS.pick(pick)
                x in 160.0..205.0 && z in 84.0..118.0 -> 5.0 + chance * 6.0 to MID_COLOURS.pick(pick)
                x < 146.0 && z < 118.0 ->
                    if (chance < 0.1) 6.0 + chance * 25.0 to MID_COLOURS.pick(pick)
                    else 2.8 + chance * 2.4 to WEST_END_COLOURS.pick(pick)
                x > 196.0 -> 2.4 + chance * 2.0 to BRICK_COLOURS.pick(pick)
                else -> 2.6 + chance * 2.6 to LOW_COLOURS.pick(pick)
            }
        }

        private fun List<Int>.pick(share: Double): Int = this[(share * size).toInt().coerceAtMost(size - 1)]

        /** A building filling [lot] a little in from its edges: walls, a roof on a tall one, and glass that glows. */
        fun addBuilding(lot: Lot, height: Double, colour: Int) {
            val x = lot.x + 0.15
            val z = lot.z + 0.15
            val width = lot.width - 0.3
            val depth = lot.depth - 0.3
            val body = blocks.size
            add(x, 0.0, z, width, height, depth, colour)
            footprints.add(Block(x, 0.0, z, width, height, depth, colour))
            var top = height
            var roof = -1
            var glass = -1
            if (height >= 8.0 && width > 1.5 && depth > 1.5) {
                // The tallest towers have a plant room on the roof.
                if (height >= 11.0) {
                    roof = blocks.size
                    add(x + 0.35, height, z + 0.35, width - 0.7, PLANT_ROOM, depth - 0.7, ROOF_GREY)
                    top += PLANT_ROOM
                }
                // The glass of a tower, on its north and south sides, lit after dark.
                glass = glowing.size
                glow(x + 0.3, 1.0, z - 0.05, width - 0.6, height - 1.6, 0.05, WINDOW)
                glow(x + 0.3, 1.0, z + depth, width - 0.6, height - 1.6, 0.05, WINDOW)
            }
            val box = Block(x, 0.0, z, width, top, depth, colour)
            foldables.add(Foldable(box, body, roof, glassNorth = glass, glassSouth = if (glass < 0) -1 else glass + 1))
        }

        /**
         * The park's forest, a tree every few squares, nearest 내 방 in full and farther ones simpler; trees on the
         * small parks near the five places, some in blossom.
         */
        fun addTrees() {
            val forest = mutableListOf<Spot>()
            for (row in 2 until ROWS step TREE_GAP) {
                for (column in 2 until COLUMNS step TREE_GAP) {
                    val x = column + random.nextInt(-1, 2)
                    val z = row + random.nextInt(-1, 2)
                    if (map.terrainAt(Cell(x, z)) == Terrain.PARK) forest.add(Spot(x + 0.5, z + 0.5))
                }
            }
            // No trunk beside a passerby's way, so nobody walks through the leaves.
            val ways = Strolls.all().flatMap { it.stroll.legs }
            forest.removeAll { tree -> ways.any { (a, b) -> distanceToLine(tree, a, b) < CLEAR_OF_WAYS } }
            val door = home.door
            val nearest = forest.sortedBy { distanceBetween(it, door) }
            for ((index, spot) in nearest.withIndex()) {
                if (index < FULL_TREES) addTree(spot.x, spot.z, blossom = false) else addFarTree(spot.x, spot.z)
            }
            val doors = venues.map { it.door }
            for (green in lotsOf(VancouverMapData.GREENS)) {
                if (doors.none { hypot(it.x - green.x, it.z - green.z) < GREEN_TREES_WITHIN }) continue
                for (x in green.x until green.x + green.width step 3) {
                    for (z in green.z until green.z + green.depth step 3) {
                        addTree(x + 0.5, z + 0.5, blossom = (x + z) % 2 == 0)
                    }
                }
                addFlowers(green)
            }
        }

        /** Lamps at the corners of each place's square, which light up after dark. */
        fun addStreetLife() {
            for (venue in venues) {
                val lot = lotFor(venue)
                val z = if (venue.doorFacesSouth) lot.z + lot.depth - 0.5 else lot.z + 0.5
                addLamp(lot.x + 0.5, z)
                addLamp(lot.x + lot.width - 0.5, z)
            }
        }

        /** Small flowers over a small park, placed by a fixed seed so the town is the same every time. */
        private fun addFlowers(green: Lot) {
            val colours = listOf(FLOWER_PINK, FLOWER_YELLOW, FLOWER_WHITE, FLOWER_LILAC)
            repeat(min(FLOWERS_PER_GREEN, (green.width * green.depth * 0.3).toInt())) {
                val x = green.x + 0.2 + random.nextDouble() * (green.width - 0.4)
                val z = green.z + 0.2 + random.nextDouble() * (green.depth - 0.4)
                add(x - 0.03, 0.0, z - 0.03, 0.06, 0.14, 0.06, LEAVES)
                add(x - 0.09, 0.14, z - 0.09, 0.18, 0.1, 0.18, colours[random.nextInt(colours.size)])
            }
        }

        private fun addFlowerBed(x: Double, z: Double) {
            add(x - 0.35, 0.0, z - 0.35, 0.7, 0.18, 0.7, SOIL)
            for ((dx, dz) in listOf(-0.15 to -0.15, 0.15 to 0.1, -0.1 to 0.18, 0.18 to -0.16)) {
                add(x + dx - 0.08, 0.18, z + dz - 0.08, 0.16, 0.14, 0.16, if (dx < 0) FLOWER_PINK else FLOWER_YELLOW)
            }
        }

        /** A round tree: a trunk, then leaves in stacked blobs, wide in the middle and smaller on top. */
        fun addTree(x: Double, z: Double, blossom: Boolean) {
            if (blossom) blossoms.add(Spot(x, z))
            val dark = if (blossom) BLOSSOM else LEAVES
            val light = if (blossom) BLOSSOM_LIGHT else LEAVES_LIGHT
            addSolid(x - 0.15, 0.0, z - 0.15, 0.3, 1.1, 0.3, TRUNK)
            add(x - 0.8, 1.0, z - 0.8, 1.6, 1.0, 1.6, dark)
            add(x - 1.05, 1.2, z - 0.55, 2.1, 0.65, 1.1, dark)
            add(x - 0.55, 1.2, z - 1.05, 1.1, 0.65, 2.1, dark)
            add(x - 0.6, 1.95, z - 0.6, 1.2, 0.5, 1.2, light)
            add(x - 0.3, 2.4, z - 0.3, 0.6, 0.25, 0.6, light)
        }

        /** A tree deep in the forest, seen only from afar: a trunk and one round of leaves. */
        fun addFarTree(x: Double, z: Double) {
            addSolid(x - 0.15, 0.0, z - 0.15, 0.3, 1.1, 0.3, TRUNK)
            add(x - 0.9, 1.0, z - 0.9, 1.8, 1.4, 1.8, LEAVES_DEEP)
        }

        /** A wooden bench [length] long, its back on the side away from where it faces. */
        fun addBench(x: Double, z: Double, facingSouth: Boolean, length: Double = 1.7) {
            val back = if (facingSouth) z - 0.25 else z + 0.17
            val half = length / 2
            addSolid(x - half, 0.0, z - 0.25, length, SEAT_HEIGHT - 0.08, 0.5, WOOD_DARK)
            add(x - half - 0.05, SEAT_HEIGHT - 0.08, z - 0.27, length + 0.1, 0.08, 0.54, WOOD)
            add(x - half - 0.05, SEAT_HEIGHT, back, length + 0.1, 0.4, 0.08, WOOD)
        }

        /** A street lamp: an iron post and a lamp that lights up after dark. */
        fun addLamp(x: Double, z: Double) {
            addSolid(x - 0.07, 0.0, z - 0.07, 0.14, 2.6, 0.14, IRON)
            add(x - 0.22, 2.55, z - 0.22, 0.44, 0.08, 0.44, IRON)
            glow(x - 0.17, 2.2, z - 0.17, 0.34, 0.36, 0.34, LAMP)
            add(x - 0.24, 2.56, z - 0.24, 0.48, 0.1, 0.48, IRON)
        }
    }

    /** How far [spot] is from the line between [a] and [b], ends included. */
    private fun distanceToLine(spot: Spot, a: Spot, b: Spot): Double {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val length = dx * dx + dz * dz
        val share = if (length == 0.0) 0.0 else ((spot.x - a.x) * dx + (spot.z - a.z) * dz) / length
        val along = share.coerceIn(0.0, 1.0)
        return hypot(spot.x - (a.x + dx * along), spot.z - (a.z + dz * along))
    }

    /** Rectangles written as "x,z,width,depth x,z,width,depth ...", as [VancouverMapData] keeps its blocks. */
    private fun lotsOf(text: String): List<Lot> = text.split(' ').filter { it.isNotEmpty() }.map { group ->
        val (x, z, width, depth) = group.split(',').map { it.toInt() }
        Lot(x, z, width, depth)
    }

    private const val NONE = 0
    private const val STREET_GROUND = 1
    private const val GREEN_GROUND = 2
    private const val PARK_GROUND = 3
    private const val SHORE_GROUND = 4

    /** How far the sea's plane reaches past the grid, every way. */
    private const val WATER_REACH = 500.0

    private const val STATION_LENGTH = 8.0
    private const val PILLAR_GAP = 7.0
    private const val TREE_GAP = 6
    private const val CLEAR_OF_WAYS = 1.6
    private const val GREEN_TREES_WITHIN = 45.0
    private const val FLOWERS_PER_GREEN = 12
    private const val BEYOND_STEP = 9
    private const val BEYOND_BLOCK = 5
    private const val BEYOND_ROWS = 3

    // A soft, warm palette, as in a storybook village.
    /** The sea's colour on a sunny day. */
    const val WATER = 0x6FB3D9
    private const val GRASS = 0x8FC165
    private const val GRASS_LIGHT = 0x9BCB6E
    private const val LAND_FAR = 0xCFC8B5
    private const val SEAWALL = 0xE6DCC6
    private const val BRICK_DARK = 0xA35F45
    private const val STONE_STREET = 0xC9C2B3
    private const val STONE_LIGHT = 0xEDE6D6
    private const val STONE = 0x9C968C
    private const val WOOD = 0xB07D52
    private const val WOOD_DARK = 0x7A5233
    private const val SOIL = 0x7D5A3C
    private const val IRON = 0x4A4540
    private const val LAMP = 0xF8E7B0
    private const val TRUNK = 0x8A5F3D
    private const val LEAVES = 0x6FAE5A
    private const val LEAVES_LIGHT = 0x8CC56B
    private const val LEAVES_DEEP = 0x5E9A52
    private const val BLOSSOM = 0xF2A7BE
    private const val BLOSSOM_LIGHT = 0xF8C8D6
    private const val FLOWER_PINK = 0xF38BA8
    private const val FLOWER_YELLOW = 0xF7D35C
    private const val FLOWER_WHITE = 0xFBF7EE
    private const val FLOWER_LILAC = 0xB9A3E3
    private const val CONCRETE = 0xDCDDD8
    private const val PILLAR = 0xC9CBC6
    private const val STATION = 0xEFEDE6
    private const val SKYTRAIN_BLUE = 0x3C6CB4
    private const val TRAIN = 0xF7F7F4
    private const val TUNNEL = 0x3A3F45
    private const val WINDOW = 0x5C7A96
    private const val DOOR = 0x6B4630
    private const val DOOR_GLASS = 0x7FA5C2
    private const val AWNING = 0x3E8E6A
    private const val AWNING_LIGHT = 0xF4EDE1
    private const val SIGN_GREEN = 0x2F6B5A
    private const val FLAME = 0xF08A3C
    private const val FLAME_LIGHT = 0xFFD36B
    private const val ROOF_GREY = 0x8E959C
    private const val FOREST = 0x5E8F68
    private const val FOREST_DARK = 0x4D7A5E
    private const val ROCK = 0x8597A3
    private const val ROCK_LIGHT = 0xA8B6BF
    private const val SNOW = 0xF7F9FB
}
