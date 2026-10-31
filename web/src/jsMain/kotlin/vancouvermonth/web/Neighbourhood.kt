package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
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

/** A box the camera stays out of and a tap stops at. [place] is set when it belongs to one of the five places. */
data class Wall(val box: Block, val place: Place?)

/**
 * The town the player walks in, all drawn from boxes. North is the water with the mountains across it; then a
 * seawall path, a meadow under the SkyTrain guideway, a row of buildings (도서관, 코워킹, the station plaza and
 * 회사), a stone street with brick pavements, and a second row (내 방 with its garden and campfire, the 카페, then a
 * park with a pond). The walkable part is a grid of [COLUMNS] by [ROWS] squares; the street and the ground go on
 * past it into the haze.
 */
object Neighbourhood {
    const val COLUMNS = 48
    const val ROWS = 32

    /** How close to a door or a person the character must stop for the place to open. */
    const val REACH = 0.9

    /** Far things fade into the sky's colour from this far from the camera, and are gone into it by [HAZE_FAR]. */
    const val HAZE_NEAR = 70.0
    const val HAZE_FAR = 230.0

    val venues: List<Venue> = listOf(
        Venue(Place.HOME, 5, 17, 5, 5, 4.2, 0xF6E7CB, 0xC0584A, false, Spot(4.6, 17.6), pitched = true),
        Venue(Place.CAFE, 13, 17, 5, 4, 3.2, 0xF2C28B, 0x8A5A3A, false, Spot(17.5, 16.5), pitched = true),
        Venue(Place.LIBRARY, 4, 5, 8, 5, 5.0, 0xEADCB9, 0x6E7F99, true, Spot(9.5, 10.5), pitched = true),
        Venue(Place.COWORKING, 15, 5, 6, 5, 7.5, 0xBFD8C9, 0x4E7D6E, true, Spot(19.5, 10.5), pitched = false),
        Venue(Place.OFFICE, 34, 5, 7, 5, 14.0, 0xA9C7E3, 0x5A7590, true, Spot(39.5, 10.5), pitched = false),
    )

    fun venue(place: Place): Venue = venues.first { it.place == place }

    /** A box around each place's person, so a tap on them talks to them. */
    val people: List<Wall> = venues.map { venue ->
        Wall(Block(venue.person.x - 0.4, 0.0, venue.person.z - 0.4, 0.8, 1.8, 0.8, 0), venue.place)
    }

    /** The bench in 내 방's garden, where the character sits by the campfire on a day of rest, facing the fire. */
    val benchSeat = Spot(2.3, 18.1)
    const val BENCH_FACING = 0.0
    const val SEAT_HEIGHT = 0.42
    val campfire = Spot(2.3, 20.0)

    /** How high the logs lift the flames off the ground. */
    const val FLAME_BASE = 0.12

    /** The SkyTrain guideway's deck, from its west end to its east end, at this height. */
    const val DECK_HEIGHT = 7.0
    const val GUIDEWAY_WEST = -140.0
    const val GUIDEWAY_EAST = 190.0

    /** Where the train's west end stands at the station, how long the train is, and the width of the deck it runs on. */
    const val TRAIN_STOP = 24.3
    const val TRAIN_LENGTH = 7.4
    const val DECK_NORTH = 3.0
    const val DECK_SOUTH = 4.0

    /** The street between the two rows, its pavements included, from the north row's fronts to the south row's. */
    const val STREET_NORTH = 10.0
    const val STREET_SOUTH = 17.0
    const val STREET_MIDDLE = 13.5

    /**
     * The point the camera looks at for a character at [spot]. On the street and its pavements it is the street's
     * middle, level with the character, so both rows are in view; past them the nudge fades out within a square.
     */
    fun cameraFocus(spot: Spot): Spot {
        val past = when {
            spot.z < STREET_NORTH -> STREET_NORTH - spot.z
            spot.z > STREET_SOUTH -> spot.z - STREET_SOUTH
            else -> 0.0
        }
        val share = (1 - past).coerceIn(0.0, 1.0)
        return Spot(spot.x, spot.z + (STREET_MIDDLE - spot.z) * share)
    }

    /** The place whose door or person [spot] is within [REACH] of, or null when it is near none. */
    fun placeAt(spot: Spot): Place? = venues.firstOrNull { venue ->
        listOf(venue.door, venue.talkSpot).any { hypot(it.x - spot.x, it.z - spot.z) <= REACH }
    }?.place

    /**
     * Every box to draw that stands still, the people aside. [ground] holds what is too flat or too far to cast a
     * shadow (the ground, the street, the water and the mountains), [glowing] the windows and lamps, and [blocks] the
     * rest.
     */
    val blocks: List<Block>
    val ground: List<Block>
    val glowing: List<Block>

    /**
     * The SkyTrain, which runs: its body and its windows, which light up after dark, measured from where its west
     * end stands at the station ([TRAIN_STOP]) along x, and as they are on y and z.
     */
    val train: List<Block>
    val trainWindows: List<Block>

    /** The campfire's flames, which flicker: measured from the middle of the fire, [FLAME_BASE] above the ground. */
    val flames: List<Block>

    /** Where each blossom tree in the town stands; their petals drift down. */
    val blossoms: List<Spot>

    /** What stands on the ground and blocks the way: a footprint for each building, trunk, fence, bench and lamp. */
    private val footprints: List<Block>

    /**
     * What the camera stays out of and never looks through: the buildings (the five places among them), the
     * SkyTrain deck, the station's roof, the café's awning and the office's canopy.
     */
    val walls: List<Wall>

    val grid: Grid

    init {
        val town = Builder()
        town.addWaterAndMountains()
        town.addGround()
        town.addSkyTrain()
        for (venue in venues) town.addVenue(venue)
        town.addHomeGarden()
        town.addPark()
        town.addStreetLife()
        town.addNeighbours()
        town.addFlowers()
        blocks = town.blocks
        ground = town.ground
        glowing = town.glowing
        train = town.train
        trainWindows = town.trainWindows
        flames = town.flames
        blossoms = town.blossoms
        footprints = town.footprints
        walls = venues.map { Wall(it.box, it.place) } + town.otherWalls.map { Wall(it, null) }
        // Each person stands on one square, which the character walks around.
        val people = venues.map { Cell(floor(it.person.x).toInt(), floor(it.person.z).toInt()) }
        grid = Grid(COLUMNS, ROWS, footprints.flatMap { cellsUnder(it) } + people)
    }

    /** The extra height a roof adds on top of the walls. */
    fun roofHeight(venue: Venue): Double = if (venue.pitched) PITCHED_ROOF else FLAT_ROOF

    /** The squares a box stands on. A box from x = 6.0 to 13.0 stands on columns 6 to 12. */
    private fun cellsUnder(box: Block): List<Cell> {
        val cells = mutableListOf<Cell>()
        for (x in floor(box.x).toInt() until ceil(box.x + box.width).toInt()) {
            for (z in floor(box.z).toInt() until ceil(box.z + box.depth).toInt()) cells.add(Cell(x, z))
        }
        return cells
    }

    private const val PITCHED_ROOF = 1.5
    private const val FLAT_ROOF = 0.3

    /** Collects the boxes while the town is laid out. */
    private class Builder {
        val blocks = mutableListOf<Block>()
        val ground = mutableListOf<Block>()
        val glowing = mutableListOf<Block>()
        val train = mutableListOf<Block>()
        val trainWindows = mutableListOf<Block>()
        val flames = mutableListOf<Block>()
        val blossoms = mutableListOf<Spot>()
        val footprints = mutableListOf<Block>()
        val otherWalls = mutableListOf<Block>()
        private val random = Random(2026)

        fun add(x: Double, y: Double, z: Double, width: Double, height: Double, depth: Double, colour: Int) {
            blocks.add(Block(x, y, z, width, height, depth, colour))
        }

        /** A box that casts no shadow: flat, like the street, or far away, like the mountains. */
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

        /** The water north of the town, and stepped peaks across it: forest, rock, then snow on top. */
        fun addWaterAndMountains() {
            lay(-140.0, -0.7, -170.0, 330.0, 0.3, 170.0, WATER)
            for ((index, x) in (-90..140 step 23).withIndex()) {
                val height = 20.0 + (index * 7) % 13
                val z = -95.0 - (index * 11) % 25
                val colours = listOf(FOREST, FOREST, FOREST_DARK, ROCK, ROCK_LIGHT, SNOW)
                val step = height / colours.size
                for ((level, colour) in colours.withIndex()) {
                    val width = 34.0 * (1 - level / 6.5)
                    lay(x - width / 2, level * step - 0.6, z - width / 3, width, step, width / 1.5, colour)
                }
            }
        }

        /** The grass, the seawall path with its railing, the street and the pavements, all reaching past the grid. */
        fun addGround() {
            lay(-140.0, -0.4, 0.0, 330.0, 0.4, 150.0, GRASS)
            lay(-140.0, -0.7, -0.4, 330.0, 0.72, 0.4, SEAWALL_EDGE)
            lay(-140.0, 0.0, 0.0, 330.0, 0.03, 2.0, SEAWALL)
            for (x in -40..90 step 2) add(x - 0.05, 0.0, -0.3, 0.1, 0.6, 0.1, WOOD_DARK)
            add(-40.0, 0.5, -0.32, 130.0, 0.08, 0.14, WOOD)
            lay(-140.0, 0.0, 10.0, 330.0, 0.05, 2.0, BRICK)
            lay(-140.0, 0.0, 12.0, 330.0, 0.04, 3.0, STONE_STREET)
            lay(-140.0, 0.0, 15.0, 330.0, 0.05, 2.0, BRICK)
            // Kerbs, and a light seam of stone down the middle of the street.
            lay(-140.0, 0.0, 11.9, 330.0, 0.08, 0.1, KERB)
            lay(-140.0, 0.0, 15.0, 330.0, 0.08, 0.1, KERB)
            for (i in -30..80 step 3) lay(i.toDouble(), 0.04, 13.45, 1.6, 0.01, 0.1, STONE_LIGHT)
            // Two crossings from one row to the other, five bands each.
            for (crossing in listOf(7.0, 28.0)) {
                for (band in 0..4) lay(crossing + band * 0.5, 0.045, 12.2, 0.28, 0.01, 2.6, STONE_LIGHT)
            }
        }

        /** The guideway on pillars along the meadow, a station hall under it, and a train that stops at the platform. */
        fun addSkyTrain() {
            val deck = DECK_HEIGHT
            val span = GUIDEWAY_EAST - GUIDEWAY_WEST
            add(GUIDEWAY_WEST, deck, DECK_NORTH, span, 0.4, DECK_SOUTH - DECK_NORTH, CONCRETE)
            otherWalls.add(Block(GUIDEWAY_WEST, deck, DECK_NORTH, span, 0.4, DECK_SOUTH - DECK_NORTH, CONCRETE))
            for (x in listOf(-14.0, -6.0, 2.0, 10.0, 18.0, 38.0, 46.0, 54.0, 62.0)) {
                addSolid(x - 0.3, 0.0, 3.2, 0.6, deck, 0.6, PILLAR)
            }
            addSolid(25.0, 0.0, 2.0, 6.0, deck, 3.0, STATION)
            otherWalls.add(Block(25.0, 0.0, 2.0, 6.0, deck + 3.0, 3.0, STATION))
            add(27.2, 0.0, 4.98, 1.6, 2.0, 0.05, DOOR_GLASS)
            add(24.6, deck + 2.4, 2.3, 6.8, 0.2, 2.4, SKYTRAIN_BLUE)
            otherWalls.add(Block(24.6, deck + 2.4, 2.3, 6.8, 0.2, 2.4, SKYTRAIN_BLUE))
            for (x in listOf(24.8, 31.0)) add(x, deck + 0.4, 2.4, 0.2, 2.0, 0.2, PILLAR)
            for (car in 0..2) {
                val x = car * 2.5
                train.add(Block(x, deck + 0.4, 3.05, 2.4, 1.0, 0.9, TRAIN))
                train.add(Block(x, deck + 0.55, 3.02, 2.4, 0.16, 0.96, SKYTRAIN_BLUE))
                trainWindows.add(Block(x + 0.25, deck + 0.8, 3.01, 1.9, 0.35, 0.98, WINDOW))
            }
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
                    add(doorX - 1.2, 2.2, z + depth, 2.4, 0.15, 0.35, CONCRETE)
                    otherWalls.add(Block(doorX - 1.2, 2.2, z + depth, 2.4, 0.15, 0.35, CONCRETE))
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

        /** 내 방's garden to the west of it: a fence, a bench facing a campfire, and a flower bed. */
        fun addHomeGarden() {
            for (z in 17..22) addSolid(0.0, 0.0, z.toDouble(), 0.15, 0.7, 1.0, WOOD)
            addSolid(0.0, 0.0, 22.85, 5.0, 0.7, 0.15, WOOD)
            add(-0.02, 0.45, 17.0, 0.19, 0.08, 6.0, WOOD_DARK)
            add(0.0, 0.45, 22.83, 5.0, 0.08, 0.19, WOOD_DARK)
            // A short bench, so there is a way past it into the garden.
            addBench(benchSeat.x, benchSeat.z, facingSouth = true, length = 1.3)
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
            for (x in listOf(0.6, 1.4, 3.6)) addFlowerBed(x, 21.6)
        }

        /** The park east of the 카페: a fence along the pavement with a way in, stone paths, trees, benches and a pond. */
        fun addPark() {
            addSolid(21.0, 0.0, 17.0, 6.5, 0.6, 0.15, WOOD)
            addSolid(30.5, 0.0, 17.0, 17.5, 0.6, 0.15, WOOD)
            lay(28.0, 0.0, 17.0, 2.0, 0.035, 15.0, STONE_PATH)
            lay(21.0, 0.0, 23.0, 27.0, 0.035, 1.5, STONE_PATH)
            lay(34.0, 0.0, 24.6, 4.0, 0.04, 2.8, WATER)
            lay(33.8, 0.0, 24.4, 4.4, 0.08, 0.2, STONE)
            lay(33.8, 0.0, 27.4, 4.4, 0.08, 0.2, STONE)
            lay(33.8, 0.0, 24.4, 0.2, 0.08, 3.2, STONE)
            lay(38.0, 0.0, 24.4, 0.2, 0.08, 3.2, STONE)
            footprints.add(Block(34.0, 0.0, 24.6, 4.0, 0.2, 2.8, WATER))
            val trees = listOf(
                23.5 to 19.5, 26.0 to 21.5, 23.0 to 27.0, 26.5 to 29.5, 32.0 to 19.5, 36.0 to 21.0,
                40.5 to 19.5, 44.5 to 21.0, 41.0 to 28.5, 45.0 to 30.5, 31.5 to 27.5, 35.5 to 30.0,
            )
            for ((index, tree) in trees.withIndex()) addTree(tree.first, tree.second, blossom = index % 3 == 1)
            addBench(24.5, 24.9, facingSouth = false)
            addBench(41.0, 24.9, facingSouth = false)
        }

        /** Lamps along both pavements, benches on the seawall, and trees on the meadow and behind the houses. */
        fun addStreetLife() {
            for (x in listOf(2.5, 13.5, 24.5, 32.5, 44.5)) addLamp(x, 11.6)
            for (x in listOf(11.5, 20.5, 38.5, 46.5)) addLamp(x, 15.4)
            for (x in listOf(6.0, 16.0, 36.0, 44.0)) addBench(x, 1.3, facingSouth = false)
            for (x in listOf(6.0, 14.0, 22.0, 34.0, 42.0)) addTree(x, 4.0, blossom = x == 14.0 || x == 42.0)
            for ((x, z) in listOf(2.5 to 26.5, 8.0 to 28.5, 17.5 to 25.5, 19.0 to 30.0, 12.0 to 30.5)) {
                addTree(x, z, blossom = x == 8.0)
            }
            for (x in listOf(-6.0, -20.0, 52.0, 66.0)) addTree(x, 4.0, blossom = false)
        }

        /** Houses past both ends of the grid, so the street goes on into the haze. */
        fun addNeighbours() {
            val houses = listOf(
                House(-9.0, 5.0, 6.0, 5.0, 4.5, 0xE9D3B4, 0x8E5B4A, true),
                House(-17.0, 5.0, 6.0, 5.0, 6.0, 0xD6E2EA, 0x6B7F95, false),
                House(-8.0, 17.0, 6.0, 5.0, 4.0, 0xF0D7C8, 0xB0654F, true),
                House(-16.0, 17.0, 6.0, 5.0, 5.5, 0xE2EBD3, 0x7D8F5E, true),
                House(50.0, 5.0, 6.0, 5.0, 7.0, 0xE4DDEB, 0x7A6F92, false),
                House(58.0, 5.0, 7.0, 5.0, 4.5, 0xF2E3C6, 0xA0644B, true),
                House(51.0, 17.0, 6.0, 5.0, 4.0, 0xD9E7EE, 0x5F7F96, true),
                House(59.0, 17.0, 6.0, 5.0, 6.0, 0xF4DCCB, 0xB5523B, true),
            )
            for (house in houses) {
                add(house.x, 0.0, house.z, house.width, house.height, house.depth, house.wall)
                val (x, z, width, depth, height) = listOf(house.x, house.z, house.width, house.depth, house.height)
                if (house.pitched) addPitchedRoof(x, z, width, depth, height, house.roof)
                else add(x - 0.15, height, z - 0.15, width + 0.3, FLAT_ROOF, depth + 0.3, house.roof)
                addWindows(x, z, width, depth, height, x + width / 2, z < 10)
                otherWalls.add(Block(x, 0.0, z, width, height + PITCHED_ROOF, depth, house.wall))
            }
        }

        /** Small flowers over the open grass, placed by a fixed seed so the town is the same every time. */
        fun addFlowers() {
            val meadows = listOf(
                Block(0.0, 0.0, 2.3, 24.0, 0.0, 2.5, 0), Block(32.0, 0.0, 2.3, 16.0, 0.0, 2.5, 0),
                Block(0.5, 0.0, 23.5, 19.5, 0.0, 8.0, 0), Block(21.3, 0.0, 17.5, 6.5, 0.0, 5.3, 0),
                Block(30.2, 0.0, 17.5, 17.5, 0.0, 5.3, 0), Block(21.3, 0.0, 24.6, 6.5, 0.0, 7.0, 0),
                Block(38.5, 0.0, 24.6, 9.0, 0.0, 7.0, 0),
            )
            val colours = listOf(FLOWER_PINK, FLOWER_YELLOW, FLOWER_WHITE, FLOWER_LILAC)
            for (meadow in meadows) {
                repeat((meadow.width * meadow.depth * 0.35).toInt()) {
                    val x = meadow.x + random.nextDouble() * meadow.width
                    val z = meadow.z + random.nextDouble() * meadow.depth
                    add(x - 0.03, 0.0, z - 0.03, 0.06, 0.14, 0.06, LEAVES)
                    add(x - 0.09, 0.14, z - 0.09, 0.18, 0.1, 0.18, colours[random.nextInt(colours.size)])
                }
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

    private data class House(
        val x: Double,
        val z: Double,
        val width: Double,
        val depth: Double,
        val height: Double,
        val wall: Int,
        val roof: Int,
        val pitched: Boolean,
    )

    // A soft, warm palette, as in a storybook village.
    private const val WATER = 0x6FB3D9
    private const val GRASS = 0x9BCB6E
    private const val SEAWALL = 0xE6DCC6
    private const val SEAWALL_EDGE = 0xBFB39A
    private const val BRICK = 0xD8A47F
    private const val BRICK_DARK = 0xA35F45
    private const val STONE_STREET = 0xBDB5A6
    private const val STONE_LIGHT = 0xEDE6D6
    private const val STONE_PATH = 0xD9CFBC
    private const val STONE = 0x9C968C
    private const val KERB = 0xCFC7B6
    private const val WOOD = 0xB07D52
    private const val WOOD_DARK = 0x7A5233
    private const val SOIL = 0x7D5A3C
    private const val IRON = 0x4A4540
    private const val LAMP = 0xF8E7B0
    private const val TRUNK = 0x8A5F3D
    private const val LEAVES = 0x6FAE5A
    private const val LEAVES_LIGHT = 0x8CC56B
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
    private const val WINDOW = 0x5C7A96
    private const val DOOR = 0x6B4630
    private const val DOOR_GLASS = 0x7FA5C2
    private const val AWNING = 0x3E8E6A
    private const val AWNING_LIGHT = 0xF4EDE1
    private const val SIGN_GREEN = 0x2F6B5A
    private const val FLAME = 0xF08A3C
    private const val FLAME_LIGHT = 0xFFD36B
    private const val FOREST = 0x5E8F68
    private const val FOREST_DARK = 0x4D7A5E
    private const val ROCK = 0x8597A3
    private const val ROCK_LIGHT = 0xA8B6BF
    private const val SNOW = 0xF7F9FB
}
