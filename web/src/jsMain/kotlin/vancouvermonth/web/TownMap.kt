package vancouvermonth.web

import vancouvermonth.core.Action

/** The five buildings the character walks between, with the name each one shows in the town. */
enum class Place(val label: String) {
    HOME("내 방"),
    CAFE("카페"),
    LIBRARY("도서관"),
    COWORKING("코워킹"),
    OFFICE("회사"),
}

/** Where the character spends a day on [action]. */
fun placeFor(action: Action): Place = when (action) {
    Action.WORK -> Place.CAFE
    Action.STUDY_ENGLISH -> Place.LIBRARY
    Action.PRACTISE_CODING -> Place.COWORKING
    Action.APPLY -> Place.OFFICE
    Action.REST -> Place.HOME
}

/** Interviews are at the office, where the applications went. */
val INTERVIEW_PLACE: Place = Place.OFFICE

/** A spot on the ground. x runs east and z runs south, one unit per grid square, as in Three.js. */
data class Spot(val x: Double, val z: Double)

/** A point in the town: [y] is the height above the ground. */
data class Point(val x: Double, val y: Double, val z: Double)

/**
 * One box of the town. [x], [y] and [z] are its west, bottom and north sides, so it fills x to x + [width],
 * y to y + [height] and z to z + [depth]. [colour] is 0xRRGGBB, as in CSS.
 */
data class Block(
    val x: Double,
    val y: Double,
    val z: Double,
    val width: Double,
    val height: Double,
    val depth: Double,
    val colour: Int,
)

/** A building on the grid: its west and north sides and its size in grid squares, then its height and colours. */
data class Building(
    val place: Place,
    val x: Int,
    val z: Int,
    val width: Int,
    val depth: Int,
    val height: Double,
    val wall: Int,
    val roof: Int,
) {
    /** Where the character stands at this building: on the pavement, just in front of the door. */
    val door: Spot
        get() = Spot(x + width / 2.0, z + depth + TownMap.DOOR_STEP)

    /** Where the name sits: just above the middle of the roof. */
    val labelAnchor: Point
        get() = Point(x + width / 2.0, height + TownMap.ROOF + 0.2, z + depth / 2.0)
}

/**
 * The town, all drawn from boxes: five buildings in a row along a road, a SkyTrain station on an elevated
 * guideway behind them, the water, and the North Shore mountains across it. North is up the screen.
 */
object TownMap {
    /** How far in front of its building the character stands. */
    const val DOOR_STEP = 0.3

    /** The line along the pavement the character walks on, between the buildings and the road. */
    const val PAVEMENT_Z = 5.6

    const val ROOF = 0.2

    /** The town's own width, west to east. The ground and the water reach farther, so the frame has no edge. */
    const val WIDTH = 17

    val buildings: List<Building> = listOf(
        Building(Place.HOME, x = 1, z = 3, width = 2, depth = 2, height = 1.8, wall = 0xF2DFC4, roof = 0xB5523B),
        Building(Place.CAFE, x = 4, z = 3, width = 2, depth = 2, height = 1.3, wall = 0xE9A15F, roof = 0x7A4A2A),
        Building(Place.LIBRARY, x = 7, z = 3, width = 3, depth = 2, height = 2.2, wall = 0xD8C7A2, roof = 0x86684A),
        Building(Place.COWORKING, x = 11, z = 3, width = 2, depth = 2, height = 2.7, wall = 0xA9C3B8, roof = 0x3E6B63),
        Building(Place.OFFICE, x = 14, z = 3, width = 2, depth = 2, height = 4.5, wall = 0x7FA8CF, roof = 0x3A5878),
    )

    fun building(place: Place): Building = buildings.first { it.place == place }

    /** Every box to draw except the character, from the mountains at the back to the kerb at the front. */
    val blocks: List<Block> = buildList {
        addWaterAndMountains()
        // The ground, with a seawall path along the shore.
        add(Block(-30.0, -0.4, 0.0, 80.0, 0.4, 14.0, GRASS))
        add(Block(-30.0, 0.0, 0.0, 80.0, 0.03, 0.6, SEAWALL))
        addSkyTrain()
        for (building in buildings) addBuilding(building)
        // Trees in the gaps between the buildings, where they hide nobody.
        for (x in listOf(0.5, 3.5, 6.5, 10.5, 13.5, 16.5)) addTree(x, 3.6)
        // Pavement, road and kerb, with a dashed line down the middle of the road.
        add(Block(-30.0, 0.0, 5.0, 80.0, 0.04, 1.0, PAVEMENT))
        add(Block(-30.0, 0.0, 6.0, 80.0, 0.04, 1.5, ROAD))
        add(Block(-30.0, 0.0, 7.5, 80.0, 0.04, 0.5, PAVEMENT))
        for (i in -4..24 step 2) add(Block(i.toDouble(), 0.04, 6.7, 1.0, 0.01, 0.1, ROAD_LINE))
        // Low hedges south of the road, too low to hide the character.
        for (x in listOf(1.0, 5.0, 9.0, 13.0)) add(Block(x, 0.0, 8.3, 2.2, 0.4, 0.5, HEDGE))
    }

    /** The character is drawn this much larger than life, so it reads on a phone. */
    const val CHARACTER_SCALE = 1.3

    /** The character before [CHARACTER_SCALE], standing on the spot (0, 0) and facing south, toward the viewer. */
    val character: List<Block> = listOf(
        Block(-0.15, 0.0, -0.08, 0.13, 0.36, 0.16, JEANS),
        Block(0.02, 0.0, -0.08, 0.13, 0.36, 0.16, JEANS),
        Block(-0.19, 0.36, -0.12, 0.38, 0.42, 0.24, JACKET),
        Block(-0.27, 0.4, -0.07, 0.08, 0.36, 0.14, JACKET),
        Block(0.19, 0.4, -0.07, 0.08, 0.36, 0.14, JACKET),
        Block(-0.14, 0.42, -0.24, 0.28, 0.32, 0.12, BACKPACK),
        Block(-0.15, 0.78, -0.15, 0.3, 0.3, 0.3, SKIN),
        Block(-0.16, 1.02, -0.16, 0.32, 0.1, 0.32, HAIR),
        Block(-0.16, 0.84, -0.16, 0.32, 0.18, 0.08, HAIR),
        Block(-0.09, 0.9, 0.15, 0.05, 0.05, 0.01, EYES),
        Block(0.04, 0.9, 0.15, 0.05, 0.05, 0.01, EYES),
    )

    private fun MutableList<Block>.addBuilding(building: Building) {
        val x = building.x.toDouble()
        val z = building.z.toDouble()
        val width = building.width.toDouble()
        val depth = building.depth.toDouble()
        val front = z + depth
        add(Block(x, 0.0, z, width, building.height, depth, building.wall))
        add(Block(x - 0.1, building.height, z - 0.1, width + 0.2, ROOF, depth + 0.2, building.roof))
        val doorLeft = x + width / 2 - 0.2
        add(Block(doorLeft, 0.0, front, 0.4, 0.62, 0.03, DOOR))
        // A window in each grid square of the front wall, a row for every storey that fits.
        var bottom = 0.25
        while (bottom + 0.4 < building.height - 0.15) {
            for (column in 0 until building.width) {
                val left = x + column + 0.3
                val groundFloor = bottom < 0.5
                val besideDoor = left + 0.4 <= doorLeft || left >= doorLeft + 0.4
                if (!groundFloor || besideDoor) add(Block(left, bottom, front, 0.4, 0.4, 0.03, WINDOW))
            }
            bottom += 0.85
        }
        when (building.place) {
            Place.CAFE -> add(Block(x - 0.05, 0.85, front, width + 0.1, 0.12, 0.45, AWNING))
            Place.LIBRARY -> add(Block(x + 0.6, 0.0, front, width - 1.2, 0.1, 0.25, STEPS))
            else -> {}
        }
    }

    private fun MutableList<Block>.addTree(x: Double, z: Double) {
        add(Block(x - 0.1, 0.0, z - 0.1, 0.2, 0.5, 0.2, TRUNK))
        add(Block(x - 0.4, 0.5, z - 0.4, 0.8, 0.7, 0.8, LEAVES))
        add(Block(x - 0.25, 1.2, z - 0.25, 0.5, 0.35, 0.5, LEAVES_LIGHT))
    }

    /** The guideway on pillars, a station under it, and a three-car train just leaving the station. */
    private fun MutableList<Block>.addSkyTrain() {
        val deck = 2.3
        add(Block(-30.0, deck, 1.3, 80.0, 0.3, 0.6, CONCRETE))
        for (x in listOf(-3.0, 1.0, 8.0, 12.0, 16.0, 20.0)) add(Block(x - 0.2, 0.0, 1.4, 0.4, deck, 0.4, PILLAR))
        // The station: a hall under the guideway and a roof over the platform.
        add(Block(2.0, 0.0, 0.8, 4.0, deck, 1.6, STATION))
        add(Block(2.2, 0.6, 2.4, 3.6, 0.5, 0.02, WINDOW))
        add(Block(1.8, deck + 1.1, 0.7, 4.4, 0.2, 1.8, SKYTRAIN_BLUE))
        for (x in listOf(2.0, 5.8)) add(Block(x, deck + 0.3, 0.75, 0.2, 0.8, 0.2, PILLAR))
        for (car in 0..2) {
            val x = 6.8 + car * 2.0
            add(Block(x, deck + 0.3, 1.32, 1.9, 0.62, 0.56, TRAIN))
            add(Block(x, deck + 0.42, 1.88, 1.9, 0.12, 0.01, SKYTRAIN_BLUE))
            add(Block(x + 0.2, deck + 0.6, 1.88, 1.5, 0.2, 0.01, WINDOW))
        }
    }

    /**
     * The water north of the town and stepped peaks across it: forest low down, then rock, then snow on top.
     * The range runs square to the camera, so each peak sits the same way under the top of the picture; that is
     * why the eastern ones are farther away. The water stops behind each peak, so the sky shows above them.
     */
    private fun MutableList<Block>.addWaterAndMountains() {
        // Each peak as a point whose y is its height.
        val peaks = listOf(
            Point(x = -4.0, y = 2.0, z = -2.4),
            Point(x = 0.0, y = 2.2, z = -3.2),
            Point(x = 4.0, y = 2.05, z = -4.1),
            Point(x = 8.0, y = 2.2, z = -4.9),
            Point(x = 12.0, y = 2.0, z = -5.8),
            Point(x = 16.0, y = 2.15, z = -6.6),
            Point(x = 20.0, y = 2.05, z = -7.5),
        )
        val widths = listOf(5.6, 4.4, 3.2, 2.0, 1.0)
        val colours = listOf(FOREST, FOREST_DARK, ROCK, ROCK_LIGHT, SNOW)
        for ((index, peak) in peaks.withIndex()) {
            // Each peak's water reaches halfway to the next peak; the first and the last reach past the picture.
            val west = if (index == 0) -30.0 else peak.x - 2.0
            val east = if (index == peaks.lastIndex) 50.0 else peak.x + 2.0
            add(Block(west, -0.4, peak.z, east - west, 0.3, -peak.z, WATER))
            val stepHeight = peak.y / widths.size
            for (step in widths.indices) {
                val width = widths[step]
                val depth = width / 1.5
                val bottom = step * stepHeight - 0.3
                add(Block(peak.x - width / 2, bottom, peak.z - depth / 2, width, stepHeight, depth, colours[step]))
            }
        }
    }

    private const val WATER = 0x3E8EC0
    private const val GRASS = 0x86B86A
    private const val SEAWALL = 0xDDD6C4
    private const val PAVEMENT = 0xCFCAC0
    private const val ROAD = 0x50565D
    private const val ROAD_LINE = 0xF2EFE6
    private const val HEDGE = 0x4C8C4A
    private const val TRUNK = 0x7A5536
    private const val LEAVES = 0x3F8A4A
    private const val LEAVES_LIGHT = 0x5FA65A
    private const val CONCRETE = 0xD6D9DC
    private const val PILLAR = 0xBFC4C8
    private const val STATION = 0xE6EAEE
    private const val SKYTRAIN_BLUE = 0x2A5CAA
    private const val TRAIN = 0xF5F7F9
    private const val WINDOW = 0x34495E
    private const val DOOR = 0x4A3426
    private const val AWNING = 0x2F7D5B
    private const val STEPS = 0xE4DCC8
    private const val FOREST = 0x4F7F5E
    private const val FOREST_DARK = 0x3F6B55
    private const val ROCK = 0x6E8591
    private const val ROCK_LIGHT = 0x8EA3AD
    private const val SNOW = 0xF4F7FA
    private const val JEANS = 0x2E3A59
    private const val JACKET = 0xD9483B
    private const val BACKPACK = 0xE0A43A
    private const val SKIN = 0xF2C9A0
    private const val HAIR = 0x2B2118
    private const val EYES = 0x1C1D1F
}
