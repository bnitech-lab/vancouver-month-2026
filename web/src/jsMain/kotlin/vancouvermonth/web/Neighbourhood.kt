package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import vancouvermonth.text.Townsperson

/**
 * One of the places as a building the character walks to: its footprint on the grid, its height and colours, the side
 * its door is on, and where the place's person stands.
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

/** The person at [place], who offers the action a day there is spent on. */
fun personAt(place: Place): Townsperson = place.person

/**
 * The bike shop: a small building whose door faces the street to the south, and its keeper by the door. It is not one
 * of the places, as no day is spent there, but it matters as they do: it never folds, the camera keeps out of it,
 * and a tap on it or on its keeper talks to the keeper.
 */
data class Storefront(
    val x: Int,
    val z: Int,
    val width: Int,
    val depth: Int,
    val height: Double,
    val wall: Int,
    val roof: Int,
    /** Where the keeper stands, by the door. */
    val keeper: Spot,
) {
    /** Where the character stands at the door: on the pavement, half a square out. */
    val door: Spot
        get() = Spot(x + width / 2.0, z + depth + 0.5)

    /** Where the character stands to talk: one square from the keeper, on the street side. */
    val talkSpot: Spot
        get() = Spot(keeper.x, keeper.z + 1.0)

    /** The way the keeper faces, toward the street: south. */
    val facing: Double
        get() = 0.0

    /** Where the name floats: above the roof, over the front. */
    val labelAnchor: Point
        get() = Point(x + width / 2.0, height + Neighbourhood.SHOP_ROOF + 0.5, z + depth - 0.5)

    /** The whole building as one box, roof included, for taps and for keeping the camera out. */
    val box: Block
        get() {
            val top = height + Neighbourhood.SHOP_ROOF
            return Block(x.toDouble(), 0.0, z.toDouble(), width.toDouble(), top, depth.toDouble(), wall)
        }

    /** The room it takes from the city: its building and a square round it. */
    val lot: Lot
        get() = Lot(x - 1, z - 1, width + 2, depth + 2)
}

/**
 * A box a tap stops at. [place] is set when it belongs to one of the places, and [shop] when it is the bike shop
 * or its keeper. The camera stays out of it, unless it [folds]: one of the city's buildings, which fold down out of the
 * camera's way instead (see [Cutaway]).
 */
data class Wall(val box: Block, val place: Place?, val folds: Boolean = false, val shop: Boolean = false)

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
 * The SkyTrain's guideway: the real Expo Line on the wide map ([WideVancouver.rail]) from where it comes out of its
 * tunnel by the stadiums, its first leg laid straight along x for the station there, carried on [PAST_THE_EDGE] past
 * the grid into the haze. Made on its own, so the town and its stations can each be laid out first.
 */
internal val expoLine: RailLine by lazy {
    val rail = WideVancouver.rail
    val level = (rail[0].z + rail[1].z) / 2
    val line = listOf(Spot(rail[0].x, level), Spot(rail[1].x, level)) + rail.drop(2)
    val a = line[line.size - 2]
    val b = line.last()
    val leg = distanceBetween(a, b)
    val far = Spot(b.x + (b.x - a.x) / leg * PAST_THE_EDGE, b.z + (b.z - a.z) / leg * PAST_THE_EDGE)
    RailLine(line + far)
}

/** How far the guideway carries on past the grid's edge. */
private const val PAST_THE_EDGE = 170.0

/**
 * The town the player walks in: real Vancouver and south-west Burnaby, traced from the two cities' open data
 * ([WideVancouver]), all drawn from boxes. The peninsula's tip is the big park, a forest of round trees with the
 * seawall round its shore; downtown and the West End are the real city blocks, taller in the business district and the
 * middle, low in the West End; False Creek lies to the south with its south shore past it, and the inlet to the north
 * with the mountains far across it. East of downtown the city goes on through East Vancouver into Burnaby: a low,
 * turned box on each real block, taller round the Burnaby town centre, and its parks as forest. Downtown's places stand
 * where they were proposed, more of each kind out past it ([venues]), and the SkyTrain runs on its guideway from its
 * tunnel by the station out to Burnaby. 내 방,
 * with its garden, bench and campfire, stands where the room is ([HomeSite]): in the West End for a room in zone 1, in
 * Burnaby for the others. The walkable part is the map's grid of [COLUMNS] by [ROWS] squares, one square for 25 m of
 * the city, the town's own corner of it ([TOWN_COLUMNS] wide) just as it was; the land goes on past it into the haze.
 */
object Neighbourhood {
    const val COLUMNS = WideMapData.COLUMNS
    const val ROWS = WideMapData.ROWS

    /** The town's own corner: columns 0 until this are downtown, the West End and the big park, as they always were. */
    const val TOWN_COLUMNS = WideMapData.TOWN_COLUMNS

    /** The ground the town stands on, square by square. */
    val map: TerrainMap
        get() = WideVancouver.map

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
        // Out past downtown, west to east, each in its kind's look and colours, on a lot the city keeps clear, its
        // square open to a main street, its lamps off the streets and the bus lines, a short walk from a station or a
        // stop: in East Vancouver an office by the junction's station, a library by the east village's and a café by
        // the small park's; in Burnaby a coworking space by the high street's stop, near zone 2's 내 방, a library by the
        // big park's stop, an office across the guideway from the town centre's station, and a café by the slope's
        // stop, beside zone 3's 내 방. Near a station, a low one's door faces away from its taller blocks.
        Venue(Place.JUNCTION_OFFICE, 323, 46, 7, 5, 14.0, 0xA9C7E3, 0x5A7590, false, Spot(328.5, 45.5), pitched = false),
        Venue(Place.EAST_VILLAGE_LIBRARY, 410, 53, 8, 5, 5.0, 0xEADCB9, 0x6E7F99, true, Spot(415.5, 58.5), pitched = true),
        Venue(Place.SMALL_PARK_CAFE, 451, 50, 5, 4, 3.2, 0xF2C28B, 0x8A5A3A, true, Spot(455.5, 54.5), pitched = true),
        Venue(Place.HIGH_STREET_COWORKING, 513, 51, 6, 5, 7.5, 0xBFD8C9, 0x4E7D6E, false, Spot(517.5, 50.5), pitched = false),
        Venue(Place.BIG_PARK_LIBRARY, 534, 61, 8, 5, 5.0, 0xEADCB9, 0x6E7F99, true, Spot(539.5, 66.5), pitched = true),
        Venue(Place.TOWN_CENTRE_OFFICE, 590, 2, 7, 5, 14.0, 0xA9C7E3, 0x5A7590, true, Spot(595.5, 7.5), pitched = false),
        Venue(Place.SLOPE_CAFE, 577, 104, 5, 4, 3.2, 0xF2C28B, 0x8A5A3A, false, Spot(581.5, 103.5), pitched = true),
    )

    fun venue(place: Place): Venue = venues.first { it.place == place }

    /**
     * 내 방 for a room in each zone (1 to 3): in zone 1 the town's own, in the West End; in zones 2 and 3 on the wide
     * map's room square for the zone ([WideVancouver.homes]), the house just north of it with its door on it, its garden
     * to the west, as the town's is.
     */
    val homes: Map<Int, Venue> = (1..3).associateWith { zone ->
        val home = venue(Place.HOME)
        if (zone == 1) {
            home
        } else {
            val room = WideVancouver.homes.getValue(zone)
            val x = room.x - home.width / 2
            val z = room.z - home.depth
            home.copy(x = x, z = z, person = Spot(x + home.person.x - home.x, z + home.person.z - home.z))
        }
    }

    /** 내 방 for a room in [zone]; zone 1's for any other number. */
    fun home(zone: Int): Venue = homes[zone] ?: homes.getValue(1)

    /**
     * The bike shop, on the West End's wide street toward downtown, on the way from 내 방 to the business district: a
     * block of the city cleared for it, as for the places. Not a real shop or address.
     */
    val shop = Storefront(142, 87, 5, 3, 3.4, 0xE9C46A, 0x7A5233, Spot(146.5, 90.5))

    /** How much the shop's flat roof adds on top of its walls. */
    const val SHOP_ROOF = 0.3

    /** A box around each place's person, and around the shop's keeper, so a tap on them talks to them: zone 1's. */
    val people: List<Wall>
        get() = HomeSite.of(1).people

    /** The bench in zone 1's garden, where the character sits by the campfire on a day of rest ([HomeSite.benchSeat]). */
    val benchSeat: Spot
        get() = HomeSite.of(1).benchSeat
    const val BENCH_FACING = PI
    const val SEAT_HEIGHT = 0.42

    /** Zone 1's campfire ([HomeSite.campfire]). */
    val campfire: Spot
        get() = HomeSite.of(1).campfire

    /** How high the logs lift the flames off the ground. */
    const val FLAME_BASE = 0.12

    /** The SkyTrain guideway's deck: how high it runs, and how wide. */
    const val DECK_HEIGHT = 7.0
    const val DECK_WIDTH = 1.0

    /**
     * The guideway's line: the real Expo Line from where it comes out of its tunnel by the stadiums, east along False
     * Creek, through East Vancouver into Burnaby and on past the grid into the haze ([expoLine]).
     */
    val guideway: RailLine
        get() = expoLine

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

    /** The place whose door or person [spot] is within [REACH] of in zone 1's town, or null ([HomeSite.placeAt]). */
    fun placeAt(spot: Spot): Place? = HomeSite.of(1).placeAt(spot)

    /** True when [spot] is within [REACH] of the shop's door or of where to stand to talk to its keeper. */
    fun isAtShop(spot: Spot): Boolean =
        distanceBetween(spot, shop.door) <= REACH || distanceBetween(spot, shop.talkSpot) <= REACH

    /**
     * Every box to draw that stands still, the people and 내 방 with its garden aside ([HomeSite.blocks]). [ground] holds
     * what is too flat or too far to cast a shadow (the ground, the water and the mountains), [glowing] the windows and
     * lamps, and [blocks] the rest. [corner] is the ground of the town's own corner, as it always was, and [groundTiles]
     * the ground of the rest of the wide map, a list for each [GROUND_TILE] by [GROUND_TILE] square of it, so the far
     * ground need not be drawn.
     */
    val blocks: List<Block>
    val ground: List<Block>
    val corner: List<Block>
    val groundTiles: List<List<Block>>
    val glowing: List<Block>

    /** The campfire's flames, which flicker: measured from the middle of the fire, [FLAME_BASE] above the ground. */
    val flames: List<Block>

    /** Where each blossom tree in the town stands; their petals drift down. */
    val blossoms: List<Spot>

    /** The town corner's buildings as built: a lot each, after the places and the station took their room. */
    val cityLots: List<Lot>

    /**
     * East Vancouver's and Burnaby's residential blocks, a detached house on each lot ([Suburbs]), and every one of the
     * houses. A house's walls are among [blocks], for the cutaway and taps, and it folds as the city's buildings do, but
     * it is drawn only near the camera ([HouseDetail]); farther off, each row of houses is one low box of [blocks], or
     * each house one out where the yards are wide ([Suburbs.ROWS_UP_TO]).
     */
    val yards: List<Yard>
    val houses: List<House>

    /** True for each square of the grid a building of the city stands on, square by square from the north row. */
    internal val built: BooleanArray

    /**
     * The ground every zone's town shares, before 내 방 and its garden stand on it ([HomeSite.grid] adds them): blocked
     * by the water, a footprint for each building, trunk, fence, bench and lamp but 내 방's, and the four other places'
     * people and the shop's keeper. Only the grid is kept, not the list of every blocked square it was made from.
     */
    internal val open: Grid

    /** What a tap stops at besides the places, which [HomeSite.walls] adds: see [walls]. */
    internal val sharedWalls: List<Wall>

    /**
     * What a tap stops at in zone 1's town: the buildings (the places among them), the SkyTrain deck, the stations
     * and their roofs, the tunnel's mouth, the café's awning and the office's canopy. The camera stays out of all but
     * the city's buildings, which fold down out of its way instead ([foldables]). Another zone's are its
     * [HomeSite.walls].
     */
    val walls: List<Wall>
        get() = HomeSite.of(1).walls

    /**
     * The buildings that fold down out of the camera's way: the city's, and the low blocks past the grid's edges. The
     * places, the station, the guideway and the train, 내 방's garden and the people never do.
     */
    val foldables: List<Foldable>

    /** The ground zone 1's character walks on ([HomeSite.grid]). */
    val grid: Grid
        get() = HomeSite.of(1).grid

    /** How many free squares zone 1's town blocked because the character could never walk to them ([HomeSite.pockets]). */
    val pockets: Int
        get() = HomeSite.of(1).pockets

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
        for (venue in venues) if (venue.place != Place.HOME) town.addVenue(venue)
        town.addShop()
        town.addSkyTrain()
        town.addStations()
        town.addCity()
        town.addTrees()
        town.addStreetLife()
        town.addStops()
        town.addFlames()
        blocks = town.blocks
        ground = town.ground
        corner = town.corner
        groundTiles = town.groundTiles
        glowing = town.glowing
        flames = town.flames
        blossoms = town.blossoms
        cityLots = town.cityLots
        yards = town.yards
        houses = yards.flatMap { it.houses }
        built = town.built
        foldables = town.foldables
        sharedWalls = listOf(Wall(shop.box, null, shop = true)) + town.otherWalls.map { Wall(it, null) } +
            foldables.map { Wall(it.box, null, folds = true) }
        // Each person stands on one square, which the character walks around; the water is never walked on.
        val people = (venues.filter { it.place != Place.HOME }.map { it.person } + shop.keeper).map {
            Cell(floor(it.x).toInt(), floor(it.z).toInt())
        }
        val blocked = map.cellsOf(Terrain.WATER) + town.footprints.flatMap { cellsUnder(it) } + town.turnedFootprints + people
        open = Grid(COLUMNS, ROWS, blocked)
    }

    /** 내 방, its garden, bench, campfire and lamps at [home], built as [HomeSite] keeps them. */
    internal fun homeParts(home: Venue): HomeParts {
        val parts = Builder()
        parts.addVenue(home)
        parts.addHomeGarden(home)
        parts.addLamps(home)
        return HomeParts(parts.blocks, parts.glowing, parts.footprints.flatMap { cellsUnder(it) })
    }

    /** The extra height a roof adds on top of the walls. */
    fun roofHeight(venue: Venue): Double = if (venue.pitched) PITCHED_ROOF else FLAT_ROOF

    /** Where a tap at [tapped] sends a character standing at [from] in zone 1's town ([HomeSite.landing]). */
    fun landing(from: Spot, tapped: Spot): Spot = HomeSite.of(1).landing(from, tapped)

    /** Where the line the mountains stand behind runs at the middle of each stretch of the north shore's forest. */
    private val northShoreLine = DoubleArray((NORTH_SHORE_TO - NORTH_SHORE_FROM) / NORTH_SHORE_STEP + 1) {
        zAlong(RealVancouver.mountains, (NORTH_SHORE_FROM + it * NORTH_SHORE_STEP).toDouble())
    }

    /**
     * Whether the spot [x], [z] lies on the dark band of forest the town lays along the north shore across the inlet,
     * in front of the mountains. The small map paints it where the town lays it, asking for every pixel past the grid.
     */
    fun isNorthShore(x: Double, z: Double): Boolean {
        // Only two stretches can reach x: the last whose middle is within half a stretch of it, and the one before.
        val last = floor((x + NORTH_SHORE_HALF - NORTH_SHORE_FROM) / NORTH_SHORE_STEP).toInt()
        for (stretch in last - 1..last) {
            if (stretch < 0 || stretch >= northShoreLine.size) continue
            val middle = NORTH_SHORE_FROM + stretch * NORTH_SHORE_STEP
            if (x < middle - NORTH_SHORE_HALF || x >= middle + NORTH_SHORE_HALF) continue
            val shore = northShoreLine[stretch]
            if (z >= shore - NORTH_SHORE_FAR && z < shore - NORTH_SHORE_NEAR) return true
        }
        return false
    }

    /** Where [line] runs at [x], carried on level past its ends. */
    private fun zAlong(line: List<Spot>, x: Double): Double {
        if (x <= line.first().x) return line.first().z
        val (a, b) = line.zipWithNext().firstOrNull { (_, b) -> x <= b.x } ?: return line.last().z
        return a.z + (b.z - a.z) * (x - a.x) / (b.x - a.x)
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
    internal fun lotFor(venue: Venue): Lot {
        val front = if (venue.doorFacesSouth) venue.z - 1 else venue.z - FORECOURT
        val lot = Lot(venue.x - 1, front, venue.width + 2, venue.depth + 1 + FORECOURT)
        return if (venue.place == Place.HOME) Lot(lot.x - GARDEN, lot.z, lot.width + GARDEN, lot.depth + 2) else lot
    }

    private const val PITCHED_ROOF = 1.5
    private const val FLAT_ROOF = 0.3

    /** How tall the plant room on a tall tower's roof is. */
    private const val PLANT_ROOM = 0.5

    /** How deep a place's square is in front of it. */
    private const val FORECOURT = 4
    private const val GARDEN = 5

    /** How finely a tap's straight way is looked along for the water's edge. */
    private const val LANDING_STEP = 0.25

    /** How far past the grid the land goes on, and the mountains past the line across the inlet. */
    private const val LAND_BEYOND = 200.0
    private const val MOUNTAINS_BACK = 52.0

    /**
     * The north shore's band of forest ([isNorthShore]): a stretch every [NORTH_SHORE_STEP] squares along x from
     * [NORTH_SHORE_FROM] to [NORTH_SHORE_TO], [NORTH_SHORE_HALF] each side of its middle, from [NORTH_SHORE_FAR] to
     * [NORTH_SHORE_NEAR] squares behind the line the mountains stand behind.
     */
    private const val NORTH_SHORE_FROM = -90
    private const val NORTH_SHORE_TO = 360
    private const val NORTH_SHORE_STEP = 26
    private const val NORTH_SHORE_HALF = 14.0
    private const val NORTH_SHORE_FAR = MOUNTAINS_BACK - 4
    private const val NORTH_SHORE_NEAR = 14.0

    /**
     * How many trees of the park's forest are drawn in full, nearest the town's 내 방; the rest are simpler, but for those
     * within [FULL_TREES_WITHIN] of 내 방 in another zone.
     */
    private const val FULL_TREES = 60
    private const val FULL_TREES_WITHIN = 24.0

    /** Collects the boxes while the town is laid out. */
    private class Builder {
        val blocks = mutableListOf<Block>()
        val ground = mutableListOf<Block>()
        val corner = mutableListOf<Block>()
        val groundTiles = mutableListOf<List<Block>>()
        val glowing = mutableListOf<Block>()
        val flames = mutableListOf<Block>()
        val blossoms = mutableListOf<Spot>()
        val footprints = mutableListOf<Block>()

        /** The squares the turned buildings of the wide map stand on, which [footprints] cannot say as boxes. */
        val turnedFootprints = mutableListOf<Cell>()
        val otherWalls = mutableListOf<Block>()
        val foldables = mutableListOf<Foldable>()
        val cityLots = mutableListOf<Lot>()
        val yards = mutableListOf<Yard>()
        val built = BooleanArray(COLUMNS * ROWS)
        private val map = WideVancouver.map

        /**
         * The town corner's chances, drawn in the order they always were, so downtown keeps every building's height and
         * colour and every tree's place; the rest of the wide map draws from its own.
         */
        private val random = Random(2026)
        private val wideRandom = Random(2427)

        /** The suburbs' houses draw from their own, so the rest of the wide map comes out as it did before them. */
        private val houseRandom = Random(2727)

        /** The houses laid out so far, in buckets of [HOUSE_BUCKET] squares, so where two real blocks overlap one wins. */
        private val housesNear = mutableMapOf<Int, MutableList<TurnedBox>>()

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
         * town's own corner is merged as it always was, into [corner]; the rest of the wide map tile by tile, into
         * [groundTiles], so a tile far off need not be drawn. The land goes on past the grid's edges into the haze.
         */
        fun addGround() {
            val kinds = IntArray(COLUMNS * ROWS) { index ->
                when (map.terrainAt(index % COLUMNS, index / COLUMNS)) {
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
            for ((lot, kind) in mergedRuns(kinds, Lot(0, 0, TOWN_COLUMNS, ROWS))) corner.add(groundBox(lot, kind))
            var west = TOWN_COLUMNS
            while (west < COLUMNS) {
                var north = 0
                while (north < ROWS) {
                    val tile = Lot(west, north, min(GROUND_TILE, COLUMNS - west), min(GROUND_TILE, ROWS - north))
                    val boxes = mergedRuns(kinds, tile).map { (lot, kind) -> groundBox(lot, kind) }
                    if (boxes.isNotEmpty()) groundTiles.add(boxes)
                    north += GROUND_TILE
                }
                west += GROUND_TILE
            }
            addLandBeyond()
        }

        private fun groundBox(lot: Lot, kind: Int): Block {
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
            return Block(lot.x.toDouble(), -0.7, lot.z.toDouble(), lot.width.toDouble(), 0.7 + top, lot.depth.toDouble(), colour)
        }

        /**
         * Each run of the same ground inside [area], grown first across and then down as far as it goes, so the whole
         * grid is covered by a few thousand boxes instead of one a square.
         */
        fun mergedRuns(kinds: IntArray, area: Lot): List<Pair<Lot, Int>> {
            val used = BooleanArray(kinds.size)
            val runs = mutableListOf<Pair<Lot, Int>>()
            val right = area.x + area.width
            val bottom = area.z + area.depth
            for (z in area.z until bottom) {
                for (x in area.x until right) {
                    val kind = kinds[z * COLUMNS + x]
                    if (kind == NONE || used[z * COLUMNS + x]) continue
                    var width = 1
                    while (x + width < right && kinds[z * COLUMNS + x + width] == kind &&
                        !used[z * COLUMNS + x + width]
                    ) width++
                    var depth = 1
                    while (z + depth < bottom && (0 until width).all { kinds[(z + depth) * COLUMNS + x + it] == kind &&
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
         * going on into the haze: past the south edge of the town's corner as it always was, and past the wide map's
         * edges turned as its streets are.
         */
        fun addLandBeyond() {
            val far = LAND_BEYOND
            fun landAt(x: Int, z: Int) = map.terrainAt(x, z) != Terrain.WATER
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
            // The town's corner once ended at its east edge, with low blocks past it; East Vancouver stands there now,
            // but those blocks' chances are still drawn, so the rest of the corner comes out as it always did.
            for (z in 2 until ROWS step BEYOND_STEP) {
                if (!landAt(TOWN_COLUMNS - 1, z) || !landAt(TOWN_COLUMNS - 1, z + BEYOND_BLOCK)) continue
                repeat(BEYOND_ROWS) {
                    random.nextDouble()
                    random.nextInt(LOW_COLOURS.size)
                }
            }
            // Low blocks on the land past the south edge of the town's corner, as far as the eye goes before the haze;
            // past the corner's east edge was water to the town, as it was then.
            fun cornerLandAt(x: Int, z: Int) = x < TOWN_COLUMNS && landAt(x, z)
            for (x in 2 until TOWN_COLUMNS step BEYOND_STEP) {
                if (!cornerLandAt(x, ROWS - 1) || !cornerLandAt(x + BEYOND_BLOCK, ROWS - 1)) continue
                for (gap in 0 until BEYOND_ROWS) addBeyond(x.toDouble(), ROWS + 2.0 + gap * BEYOND_STEP, random, 0.0)
            }
            // And past the wide map's edges: east, south and north, where the city goes on.
            for (z in 2 until ROWS step BEYOND_STEP) {
                if (!landAt(COLUMNS - 1, z) || !landAt(COLUMNS - 1, z + BEYOND_BLOCK)) continue
                for (gap in 0 until BEYOND_ROWS) addBeyond(COLUMNS + 2.0 + gap * BEYOND_STEP, z.toDouble(), wideRandom, TURNED)
            }
            for (x in TOWN_COLUMNS + 2 until COLUMNS step BEYOND_STEP) {
                if (landAt(x, ROWS - 1) && landAt(x + BEYOND_BLOCK, ROWS - 1)) {
                    for (gap in 0 until BEYOND_ROWS) addBeyond(x.toDouble(), ROWS + 2.0 + gap * BEYOND_STEP, wideRandom, TURNED)
                }
                if (landAt(x, 0) && landAt(x + BEYOND_BLOCK, 0)) {
                    for (gap in 0 until BEYOND_ROWS) {
                        addBeyond(x.toDouble(), -2.0 - BEYOND_BLOCK - gap * BEYOND_STEP, wideRandom, TURNED)
                    }
                }
            }
        }

        private fun addBeyond(x: Double, z: Double, chances: Random, turn: Double) {
            // Past the wide map's edges the city goes on as low as its far suburbs; past the corner's, as it always was.
            val tall = 3.0 + chances.nextDouble() * 3.5
            val height = if (turn == 0.0) tall else BEYOND_LOW + (tall - 3.0) / 3.5 * BEYOND_LOW_SPREAD
            val colour = LOW_COLOURS[chances.nextInt(LOW_COLOURS.size)]
            val body = blocks.size
            val size = BEYOND_BLOCK.toDouble()
            blocks.add(Block(x, 0.0, z, size, height, size, colour, turn))
            // Turned, it reaches this much farther each way than it is wide.
            val out = if (turn == 0.0) 0.0 else size * (SQRT_HALF - 0.5)
            val box = Block(x - out, 0.0, z - out, size + 2 * out, height, size + 2 * out, colour)
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
         * The north shore across the inlet: a dark band of forest past the line in the middle of the inlet
         * ([isNorthShore]), and stepped peaks behind it, forest, rock, then snow on top, pushed well back as the real
         * ones stand.
         */
        fun addMountains() {
            val line = RealVancouver.mountains
            for ((index, x) in (NORTH_SHORE_FROM..NORTH_SHORE_TO step NORTH_SHORE_STEP).withIndex()) {
                val shore = zAlong(line, x.toDouble())
                val depth = NORTH_SHORE_FAR - NORTH_SHORE_NEAR
                lay(x - NORTH_SHORE_HALF, -0.5, shore - NORTH_SHORE_FAR, 2 * NORTH_SHORE_HALF, 1.2, depth, FOREST_DARK)
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
            if (venue.place.kind == PlaceKind.OFFICE) {
                addWindowBands(x, z, width, depth, venue.height, doorX, venue.doorFacesSouth)
            } else {
                addWindows(x, z, width, depth, venue.height, doorX, venue.doorFacesSouth)
            }
            // What marks the kind out, on the side the door is on: [ahead] is how far out from the front wall a thing
            // [deep] squares deep starts.
            val south = venue.doorFacesSouth
            fun ahead(deep: Double, gap: Double = 0.0): Double = if (south) front + gap else front - gap - deep
            when (venue.place.kind) {
                PlaceKind.CAFE -> {
                    // An awning over the front in green and cream bands, and a small table outside. The awning stops
                    // short of the door's spot, so the character never stands under it.
                    for (band in 0 until (width * 2).toInt()) {
                        add(x + band * 0.5, 2.0, ahead(0.35), 0.5, 0.14, 0.35, if (band % 2 == 0) AWNING else AWNING_LIGHT)
                    }
                    otherWalls.add(Block(x, 2.0, ahead(0.35), width, 0.14, 0.35, AWNING))
                    addSolid(x + 0.6, 0.0, ahead(0.6, 0.7), 0.6, 0.7, 0.6, WOOD)
                }
                PlaceKind.LIBRARY -> add(x + 2.5, 0.0, ahead(0.4), width - 5.0, 0.15, 0.4, STONE_LIGHT)
                PlaceKind.COWORKING -> add(x + 0.4, 0.3, ahead(0.08), width - 0.8, 0.18, 0.08, SIGN_GREEN)
                PlaceKind.OFFICE -> {
                    // A canopy over the door, short of the door's spot, as the café's awning is.
                    val canopy = Block(doorX - 1.2, 2.2, ahead(0.35), 2.4, 0.15, 0.35, CONCRETE)
                    blocks.add(canopy)
                    otherWalls.add(canopy)
                }
                PlaceKind.HOME -> add(x + 3.6, venue.height, z + 1.2, 0.6, 1.8, 0.6, BRICK_DARK)
            }
        }

        /**
         * The bike shop: warm walls under a flat roof, a door with a lit shop window either side, and a sign over the
         * door with a wheel on it.
         */
        fun addShop() {
            val x = shop.x.toDouble()
            val z = shop.z.toDouble()
            val width = shop.width.toDouble()
            val depth = shop.depth.toDouble()
            add(x, 0.0, z, width, shop.height, depth, shop.wall)
            footprints.add(shop.box)
            add(x - 0.15, shop.height, z - 0.15, width + 0.3, SHOP_ROOF, depth + 0.3, shop.roof)
            val doorX = x + width / 2
            val front = z + depth
            add(doorX - 0.45, 0.0, front, 0.9, 1.6, 0.06, DOOR)
            add(doorX - 0.55, 1.6, front - 0.01, 1.1, 0.12, 0.08, WOOD_DARK)
            glow(x + 0.3, 0.55, front, doorX - 0.75 - x - 0.3, 1.3, 0.05, WINDOW)
            glow(doorX + 0.75, 0.55, front, x + width - 0.3 - doorX - 0.75, 1.3, 0.05, WINDOW)
            add(x + 0.5, 2.25, front, width - 1.0, 0.6, 0.1, SHOP_SIGN)
            add(doorX - 0.22, 2.33, front + 0.1, 0.44, 0.44, 0.04, TYRE)
            add(doorX - 0.12, 2.43, front + 0.12, 0.24, 0.24, 0.04, SHOP_SIGN)
            // Plain windows along its sides, as on the houses round it.
            for (east in listOf(false, true)) {
                val faceX = if (east) x + width else x - 0.05
                glow(faceX, 1.2, z + 0.7, 0.05, 0.7, depth - 1.4, WINDOW)
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
         * An office tower's windows: a band of glass round each storey, as the windows of [addWindows] would sit, the
         * door's storey open at the door; four boxes a storey rather than one a window, so three towers stay light.
         */
        fun addWindowBands(
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
                    if (bottom < 1.5 && south == doorSouth) {
                        glow(x + 0.2, bottom, faceZ, doorX - 1.0 - x - 0.2, 0.65, 0.05, WINDOW)
                        glow(doorX + 1.0, bottom, faceZ, x + width - 0.2 - doorX - 1.0, 0.65, 0.05, WINDOW)
                    } else {
                        glow(x + 0.2, bottom, faceZ, width - 0.4, 0.65, 0.05, WINDOW)
                    }
                }
                for (east in listOf(false, true)) {
                    glow(if (east) x + width else x - 0.05, bottom, z + 0.2, 0.05, 0.65, depth - 0.4, WINDOW)
                }
                bottom += 1.4
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
        fun addHomeGarden(home: Venue) {
            val west = home.x - GARDEN.toDouble()
            val north = home.z - 1.0
            for (row in 0..5) addSolid(west, 0.0, north + row, 0.15, 0.7, 1.0, WOOD)
            addSolid(west, 0.0, north, 5.0, 0.7, 0.15, WOOD)
            add(west - 0.02, 0.45, north, 0.19, 0.08, 6.0, WOOD_DARK)
            add(west, 0.45, north - 0.02, 5.0, 0.08, 0.19, WOOD_DARK)
            // A short bench, so there is a way past it into the garden.
            val seat = HomeSite.benchSeatAt(home)
            addBench(seat.x, seat.z, facingSouth = false, length = 1.3)
            // Stones in a ring, two crossed logs, and the fire, which glows after dark.
            val fire = HomeSite.campfireAt(home)
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
            for (x in listOf(0.6, 1.4, 3.6)) addFlowerBed(west + x, north + 1.4)
        }

        /** The campfire's flames, the same at every 내 방: measured from the middle of the fire. */
        fun addFlames() {
            flames.add(Block(-0.18, 0.0, -0.18, 0.36, 0.34, 0.36, FLAME))
            flames.add(Block(-0.1, 0.28, -0.1, 0.2, 0.26, 0.2, FLAME_LIGHT))
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
            // For the camera, the deck as short pieces along the line, each straight on the grid, as far as it can go.
            var along = 0.0
            while (along < guideway.length) {
                val spot = guideway.spotAt(along)
                if (spot.x > COLUMNS + HAZE_NEAR || spot.z < -HAZE_NEAR) break
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
            // Pillars from past the station all along, on into the haze; a station up on the line stands on its own, and
            // where a bus line passes under the deck, the deck spans it.
            val busWays = Samples(TransitLines.busLines.map { it.loop.points })
            along = STATION_LENGTH + 4.0
            while (along < guideway.length) {
                val spot = guideway.spotAt(along)
                if (spot.x > COLUMNS + HAZE_FAR) break
                if (TransitLines.stations.any { it.entrance != null && !it.underground && abs(it.along - along) < 5.0 } ||
                    busWays.within(spot, PILLAR_CLEAR_OF_BUSES)
                ) {
                    along += PILLAR_GAP
                    continue
                }
                val pillar = Block(spot.x - 0.3, 0.0, spot.z - 0.3, 0.6, deck, 0.6, PILLAR)
                blocks.add(pillar)
                if (spot.x < COLUMNS && spot.z >= 0) footprints.add(pillar)
                along += PILLAR_GAP
            }
        }

        /**
         * The SkyTrain's other stations: an entrance on the ground with a glass door to the south and the line's sign
         * over it; up on the line, a lift up to a platform either side of the deck under a roof on two posts. The
         * station under downtown has its entrance alone. The first station, under the deck by the tunnel's mouth, is
         * the one [addSkyTrain] builds, with a sign by its door.
         */
        fun addStations() {
            for (station in TransitLines.stations) {
                val lot = station.entrance
                val doorX = station.door.x
                if (lot == null) {
                    val door = station.door
                    add(door.x + 1.25, 0.0, door.z - 0.2, 0.08, 2.2, 0.08, IRON)
                    add(door.x + 1.05, 1.7, door.z - 0.24, 0.5, 0.42, 0.08, SKYTRAIN_BLUE)
                    continue
                }
                val x = lot.x + 0.2
                val z = lot.z + 0.2
                val width = lot.width - 0.4
                val depth = lot.depth - 0.4
                val tall = TransitLines.ENTRANCE_HEIGHT
                addSolid(x, 0.0, z, width, tall, depth, STATION)
                otherWalls.add(Block(x, 0.0, z, width, tall, depth, STATION))
                add(x - 0.15, tall, z - 0.15, width + 0.3, 0.2, depth + 0.3, SKYTRAIN_BLUE)
                add(doorX - 0.55, 0.0, z + depth, 1.1, 1.9, 0.05, DOOR_GLASS)
                add(doorX - 0.75, 2.2, z + depth, 1.5, 0.5, 0.08, SKYTRAIN_BLUE)
                add(doorX - 0.3, 2.32, z + depth + 0.07, 0.6, 0.26, 0.04, STATION)
                if (station.underground) continue
                val middle = guideway.spotAt(station.along)
                val turn = guideway.turnAt(station.along)
                add(middle.x - 0.45, tall + 0.2, middle.z - 0.45, 0.9, DECK_HEIGHT - tall - 0.3, 0.9, STATION)
                val half = STATION_LENGTH / 2
                blocks.add(Block(middle.x - half, DECK_HEIGHT - 0.1, middle.z - 1.4, 2 * half, 0.32, 2.8, STATION, turn))
                blocks.add(
                    Block(middle.x - half - 0.2, DECK_HEIGHT + 2.4, middle.z - 1.3, 2 * half + 0.4, 0.2, 2.6, SKYTRAIN_BLUE, turn),
                )
                for (end in listOf(-half + 0.2, half - 0.2)) {
                    val postX = middle.x + end * kotlin.math.cos(turn)
                    val postZ = middle.z - end * kotlin.math.sin(turn)
                    add(postX - 0.1, DECK_HEIGHT + 0.4, postZ - 0.1, 0.2, 2.0, 0.2, PILLAR)
                }
            }
        }

        /** The bus stops' shelters and signs and the water bus's piers: in the town's still boxes, blocking nothing. */
        fun addStops() {
            blocks.addAll(TransitLines.scenery)
        }

        /**
         * The city: a building on each real block, the places' lots and the station's kept clear, and none under the
         * guideway. Taller in the business district round 회사 and in the middle of downtown, mid-height by False Creek,
         * low in the West End, the old streets to the east and across the creek.
         */
        fun addCity() {
            val start = guideway.points.first()
            val entrances = TransitLines.stations.mapNotNull { it.entrance }.map { Lot(it.x - 1, it.z - 1, it.width + 2, it.depth + 2) }
            val (inTown, pastTown) = venues.partition { it.x < TOWN_COLUMNS }
            val kept = inTown.map { lotFor(it) } + shop.lot + entrances +
                Lot(floor(start.x).toInt() - 5, floor(start.z).toInt() - 4, STATION_LENGTH.toInt() + 8, 10) +
                homes.values.filter { it != venue(Place.HOME) }.map { lotFor(it) }
            var lots = lotsOf(VancouverMapData.BLOCKS)
            for (hole in kept) lots = lots.flatMap { it.minus(hole) }
            val rail = mutableListOf<Spot>()
            var along = -3.0
            while (along < guideway.length && guideway.spotAt(along).x < TOWN_COLUMNS + 1) {
                rail.add(guideway.spotAt(along))
                along += 0.5
            }
            for (lot in lots) {
                if (lot.width * lot.depth < 2) continue
                if (rail.any { lot.isNear(it.x, it.z, 1.0) }) continue
                cityLots.add(lot)
                for (x in lot.x until lot.x + lot.width) for (z in lot.z until lot.z + lot.depth) built[z * COLUMNS + x] = true
                val middleX = lot.x + lot.width / 2.0
                val middleZ = lot.z + lot.depth / 2.0
                var (height, colour) = storeys(middleX, middleZ)
                // Low beside the guideway, so the deck never runs into a wall.
                if (rail.any { lot.isNear(it.x, it.z, 4.0) }) height = min(height, DECK_HEIGHT - 1.5)
                addBuilding(lot, height, colour)
            }
            // The corner's own buildings are kept too, where a wide block reaches back over the corner's edge.
            addWideCity(kept + cityLots + TransitLines.keptForStops + Rides.keptForDocks, pastTown.map { lotFor(it) })
            plantYards()
        }

        /**
         * East Vancouver and Burnaby on the real blocks of the wide map ([WideVancouver.blocks]), each turned as the
         * block is and a little smaller, so the streets between are wide enough to walk ([WIDE_SHRINK]): a cluster of
         * towers round the Burnaby town centre, a box of mid-rise on each block round the other stations, and everywhere
         * else detached houses, one on each lot ([addHouses]). None on a park or the water, under or beside the
         * guideway, or on a main street; where [kept] or one of the [places] out past downtown keeps the ground clear, no
         * building, and only the houses clear of it.
         */
        fun addWideCity(kept: List<Lot>, places: List<Lot>) {
            val streets = Samples(WideVancouver.streets + TransitLines.busLines.map { it.loop.points })
            val rail = Samples(listOf(guideway.points))
            val stations = WideVancouver.stations
            val centre = stations[TransitLines.TOWN_CENTRE].spot
            for (lot in WideVancouver.blocks) {
                val width = max(min(lot.width, WIDE_SMALLEST), lot.width - 2 * WIDE_SHRINK)
                val depth = max(min(lot.depth, WIDE_SMALLEST), lot.depth - 2 * WIDE_SHRINK)
                if (width * depth < WIDE_LEAST_AREA) continue
                val middle = lot.middle
                val terrain = map.terrainAt(floor(middle.x).toInt(), floor(middle.z).toInt())
                if (terrain == Terrain.WATER || terrain == Terrain.PARK) continue
                val shape = TurnedBox(middle.x, middle.z, width, depth, lot.turn)
                if (rail.anyWithin(shape, WIDE_RAIL_CLEAR) || streets.anyWithin(shape, WIDE_STREET_CLEAR)) continue
                val inKept = kept.any { shape.overlaps(it, 0.5) }
                val onPlace = places.any { shape.overlaps(it, 0.5) }
                val fromCentre = distanceBetween(middle, centre)
                val nearStation = stations.minOf { distanceBetween(it.spot, middle) }
                val residential = fromCentre >= TOWN_CENTRE_REACH && nearStation >= STATION_REACH
                // Drawn for every block that stood before the houses and the places past downtown came, so the wide
                // map's other chances are as they were.
                val chance = if (inKept) 0.0 else wideRandom.nextDouble()
                val pick = if (inKept) 0.0 else wideRandom.nextDouble()
                if ((inKept || onPlace) && !residential) continue
                if (residential) {
                    val clear = if (inKept || onPlace) (kept + places).filter { shape.overlaps(it, 0.5) } else emptyList()
                    addHouses(shape, clear, streets)
                    continue
                }
                var (height, colours) = when {
                    fromCentre < TOWN_CENTRE_REACH -> 8.0 + chance * 12.0 * (1 - fromCentre / TOWN_CENTRE_REACH / 2) to TOWER_COLOURS
                    else -> 3.2 + chance * 3.0 to MID_COLOURS
                }
                // Low right beside the guideway, so the deck never runs into a wall.
                if (rail.anyWithin(shape, WIDE_RAIL_LOW)) height = min(height, DECK_HEIGHT - 1.5)
                addTurnedBuilding(shape, height, colours.pick(pick))
            }
        }

        /**
         * A residential block split into lots, a detached house on each ([Suburbs.layOut]), a single row's doors to
         * a main street beside it ([streets]) if there is one: each house folding as the city's buildings do and
         * blocking only the squares it stands on, so a yard wide enough is walked through; a yard tree's trunk blocks
         * its square. Far off, each row of houses is one low box, as long as the row and as tall as its houses on the
         * whole, out to [Suburbs.ROWS_UP_TO]; farther out, where the yards are wide enough to see, and on a row a house
         * is missing from, it is a box for each house. A house on a park, on the water, in the town's corner or on a
         * lot in [kept] is left out, and a block that reaches such a lot has no lawn or trees. The small map shows the
         * block as it did.
         */
        fun addHouses(block: TurnedBox, kept: List<Lot>, streets: Samples) {
            val walls = if (block.x < BOUNDARY_X) WEST_END_COLOURS else LOW_COLOURS
            val share = Suburbs.share(Spot(block.x, block.z))
            // A single row's doors face a main street if one runs along the block, and never a park or the water.
            val plan = Suburbs.layOut(block, share, walls, houseRandom) { spot ->
                val terrain = map.terrainAt(floor(spot.x).toInt(), floor(spot.z).toInt())
                when {
                    terrain == Terrain.PARK || terrain == Terrain.WATER -> -1
                    streets.within(spot, MAIN_STREET_NEAR) -> 1
                    else -> 0
                }
            }
            val standing = plan.houses.filter { house ->
                house.shape.x > TOWN_COLUMNS && kept.none { house.shape.overlaps(it, 0.5) } &&
                    Suburbs.cornersOf(house.shape).all { (x, z) ->
                        val terrain = map.terrainAt(floor(x).toInt(), floor(z).toInt())
                        terrain != Terrain.WATER && terrain != Terrain.PARK
                    } && nearHouses(house.shape).none { it.overlaps(house.shape, HOUSE_APART) }
            }
            if (standing.isEmpty()) return
            for (house in standing) {
                housesNear.getOrPut(houseBucket(house.shape.x, house.shape.z)) { mutableListOf() }.add(house.shape)
            }
            val firstRow = blocks.size
            // Each house's share of its row's low box: from halfway to the house before it to halfway to the next.
            val slots = mutableMapOf<House, Triple<Double, Double, Double>>()
            for (front in standing.map { it.front }.distinct()) {
                val row = standing.filter { it.front == front }
                if (share <= Suburbs.ROWS_UP_TO && row.size == plan.houses.count { it.front == front }) {
                    val first = row.first().shape
                    val last = row.last().shape
                    val length = hypot(last.x - first.x, last.z - first.z) + first.width
                    val far = TurnedBox((first.x + last.x) / 2, (first.z + last.z) / 2, length, first.depth, first.turn)
                    val farWall = row.sumOf { it.wall } / row.size
                    blocks.add(far.block(0.0, farWall, row.first().colour))
                    val along = row.map { (it.shape.x - first.x) * cos(first.turn) - (it.shape.z - first.z) * sin(first.turn) }
                    for ((at, house) in row.withIndex()) {
                        val from = if (at == 0) along[at] - first.width / 2 else (along[at - 1] + along[at]) / 2
                        val to = if (at == row.lastIndex) along[at] + first.width / 2 else (along[at] + along[at + 1]) / 2
                        slots[house] = Triple((from + to) / 2 - along[at], to - from, farWall)
                    }
                } else {
                    for (house in row) blocks.add(house.shape.block(0.0, house.wall, house.colour))
                }
            }
            val rows = blocks.size - firstRow
            val houses = standing.map { house ->
                val body = blocks.size
                blocks.add(house.shape.block(0.0, house.wall, house.colour))
                val foldable = foldables.size
                val box = house.shape.bounds(house.wall + house.ridge, house.colour)
                foldables.add(Foldable(box, body, roof = -1, glassNorth = -1, glassSouth = -1))
                claim(house.shape, box, marks = kept.isNotEmpty())
                // However thin, it blocks the square its middle is on.
                turnedFootprints.add(Cell(floor(house.shape.x).toInt(), floor(house.shape.z).toInt()))
                val (shift, width, farWall) = slots[house] ?: Triple(0.0, house.shape.width, house.wall)
                House(
                    house.shape, house.wall, house.colour, house.roof, house.front, house.doorAt, body, foldable,
                    shift, width, farWall,
                )
            }
            if (kept.isEmpty()) claim(block, block.bounds(0.0, 0), marks = true, blocking = false)
            val trees = if (kept.isEmpty()) plan.trees else emptyList()
            yards.add(Yard(block, if (kept.isEmpty()) plan.lawn else null, trees, houses, firstRow, rows))
        }

        /** The houses laid out so far near [shape]: in its bucket and the eight round it. */
        private fun nearHouses(shape: TurnedBox): List<TurnedBox> {
            val near = mutableListOf<TurnedBox>()
            val column = floor(shape.x / HOUSE_BUCKET).toInt()
            val row = floor(shape.z / HOUSE_BUCKET).toInt()
            for (dx in -1..1) for (dz in -1..1) housesNear[(column + dx) * HOUSE_BUCKETS + row + dz]?.let { near.addAll(it) }
            return near
        }

        private fun houseBucket(x: Double, z: Double): Int =
            floor(x / HOUSE_BUCKET).toInt() * HOUSE_BUCKETS + floor(z / HOUSE_BUCKET).toInt()

        /**
         * Once every house stands, the yard trees clear of all of them (two real blocks may overlap), each trunk
         * blocking its square.
         */
        fun plantYards() {
            for ((index, yard) in yards.withIndex()) {
                if (yard.trees.isEmpty()) continue
                // Trunk and crown in pairs, taken by index: chunked(2) would bring Kotlin's sequence code, about 8 KB,
                // into the page.
                val trees = mutableListOf<Block>()
                for (i in yard.trees.indices step 2) {
                    val crown = yard.trees[i + 1]
                    val spot = TurnedBox(crown.x + crown.width / 2, crown.z + crown.depth / 2, crown.width, crown.depth, 0.0)
                    if (nearHouses(spot).none { it.overlaps(spot, 0.0) }) {
                        trees.add(yard.trees[i])
                        trees.add(crown)
                    }
                }
                for (i in trees.indices step 2) footprints.add(trees[i])
                yards[index] = Yard(yard.block, yard.lawn, trees, yard.houses, yard.firstRow, yard.rows)
            }
        }

        /**
         * Blocks every square of the grid whose middle comes within the character's clearance of [shape] (held by [box]),
         * when [blocking]; and when [marks], marks every square [shape] stands over as built, for the small map.
         */
        private fun claim(shape: TurnedBox, box: Block, marks: Boolean, blocking: Boolean = true) {
            for (x in floor(box.x).toInt()..floor(box.x + box.width).toInt()) {
                for (z in floor(box.z).toInt()..floor(box.z + box.depth).toInt()) {
                    if (x !in 0 until COLUMNS || z !in 0 until ROWS) continue
                    val gap = shape.distanceTo(x + 0.5, z + 0.5)
                    if (blocking && gap < Grid.CLEARANCE) turnedFootprints.add(Cell(x, z))
                    if (marks && gap == 0.0) built[z * COLUMNS + x] = true
                }
            }
        }

        /** A building on a turned block: its walls, and on a tower glass on its two long sides, which glows after dark. */
        fun addTurnedBuilding(shape: TurnedBox, height: Double, colour: Int) {
            val body = blocks.size
            blocks.add(shape.block(0.0, height, colour))
            var glass = -1
            if (height >= 8.0 && shape.width > 1.5 && shape.depth > 1.5) {
                glass = glowing.size
                for (side in listOf(-1.0, 1.0)) {
                    val out = side * (shape.depth / 2 + 0.025)
                    val pane = TurnedBox(
                        shape.x + out * kotlin.math.sin(shape.turn), shape.z + out * kotlin.math.cos(shape.turn),
                        shape.width - 0.6, 0.05, shape.turn,
                    )
                    glowing.add(pane.block(1.0, height - 1.6, WINDOW))
                }
            }
            val box = shape.bounds(height, colour)
            foldables.add(Foldable(box, body, roof = -1, glassNorth = glass, glassSouth = if (glass < 0) -1 else glass + 1))
            // It blocks every square whose middle it comes within the character's clearance of.
            claim(shape, box, marks = true)
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
         * small parks near the places, some in blossom.
         */
        fun addTrees() {
            val forest = mutableListOf<Spot>()
            val wideForest = mutableListOf<Spot>()
            // The town's corner first, with its own chances, as it always was; then the rest of the wide map.
            for (row in 2 until ROWS step TREE_GAP) {
                for (column in 2 until TOWN_COLUMNS step TREE_GAP) {
                    val x = column + random.nextInt(-1, 2)
                    val z = row + random.nextInt(-1, 2)
                    if (map.terrainAt(x, z) == Terrain.PARK) forest.add(Spot(x + 0.5, z + 0.5))
                }
            }
            val firstWide = 2 + (TOWN_COLUMNS - 2 + TREE_GAP - 1) / TREE_GAP * TREE_GAP
            for (row in 2 until ROWS step TREE_GAP) {
                for (column in firstWide until COLUMNS step TREE_GAP) {
                    val x = column + wideRandom.nextInt(-1, 2)
                    val z = row + wideRandom.nextInt(-1, 2)
                    if (map.terrainAt(x, z) == Terrain.PARK) wideForest.add(Spot(x + 0.5, z + 0.5))
                }
            }
            // No trunk beside a passerby's way, so nobody walks through the leaves; and out on the wide map, none on a
            // main street, a stop, a dock or 내 방's garden.
            val ways = Strolls.all().flatMap { it.stroll.legs }
            forest.removeAll { tree -> ways.any { (a, b) -> distanceToLine(tree, a, b) < CLEAR_OF_WAYS } }
            val streets = Samples(WideVancouver.streets + TransitLines.busLines.map { it.loop.points })
            val clear = TransitLines.keptForStops + Rides.keptForDocks + homes.values.map { lotFor(it) } +
                venues.filter { it.x >= TOWN_COLUMNS }.map { lotFor(it) }
            wideForest.removeAll { tree ->
                ways.any { (a, b) -> distanceToLine(tree, a, b) < CLEAR_OF_WAYS } || streets.within(tree, CLEAR_OF_STREETS) ||
                    clear.any { it.isNear(tree.x, tree.z, 1.0) }
            }
            val door = venue(Place.HOME).door
            val nearest = forest.sortedBy { distanceBetween(it, door) }
            for ((index, spot) in nearest.withIndex()) {
                if (index < FULL_TREES) addTree(spot.x, spot.z, blossom = false) else addFarTree(spot.x, spot.z)
            }
            val otherDoors = homes.values.map { it.door }.filter { it != door }
            for (spot in wideForest) {
                if (otherDoors.any { distanceBetween(it, spot) < FULL_TREES_WITHIN }) addTree(spot.x, spot.z, blossom = false)
                else addFarTree(spot.x, spot.z)
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

        /** Lamps at the corners of each place's square, which light up after dark; 내 방's go with it ([addLamps]). */
        fun addStreetLife() {
            for (venue in venues) if (venue.place != Place.HOME) addLamps(venue)
        }

        /** The two lamps at the front corners of [venue]'s square. */
        fun addLamps(venue: Venue) {
            val lot = lotFor(venue)
            val z = if (venue.doorFacesSouth) lot.z + lot.depth - 0.5 else lot.z + 0.5
            addLamp(lot.x + 0.5, z)
            addLamp(lot.x + lot.width - 0.5, z)
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
    private const val PILLAR_CLEAR_OF_BUSES = 1.8
    private const val TREE_GAP = 6
    private const val CLEAR_OF_WAYS = 1.6

    /** How far a tree of the wide map's parks keeps from a main street's middle, so the street is clear to walk and drive. */
    private const val CLEAR_OF_STREETS = 1.6

    /**
     * How the wide map's blocks are built: each side brought in this much (but never below [WIDE_SMALLEST]), so a street
     * between two turned blocks is wide enough to walk along; none smaller than [WIDE_LEAST_AREA]; none within
     * [WIDE_STREET_CLEAR] of a main street's middle or [WIDE_RAIL_CLEAR] of the guideway's.
     */
    const val WIDE_SHRINK = 0.65
    private const val WIDE_SMALLEST = 1.0
    private const val WIDE_LEAST_AREA = 0.8
    const val WIDE_STREET_CLEAR = 0.8
    private const val WIDE_RAIL_CLEAR = 1.5
    private const val WIDE_RAIL_LOW = 1.8

    /** How far the Burnaby town centre's towers reach from its station, and the mid-rise round the other stations. */
    internal const val TOWN_CENTRE_REACH = 22.0
    internal const val STATION_REACH = 9.0

    /** Roughly where Vancouver ends and Burnaby begins along the line: East Vancouver's houses are in livelier colours. */
    private const val BOUNDARY_X = 505.0

    /** The wide map's ground is merged in squares this many squares wide, so a far one need not be drawn. */
    const val GROUND_TILE = 80

    /** The turn of the wide map's streets, for the low blocks past its edges. */
    private const val TURNED = PI / 4
    private const val SQRT_HALF = 0.7071067811865476
    private const val GREEN_TREES_WITHIN = 45.0
    private const val FLOWERS_PER_GREEN = 12
    private const val BEYOND_STEP = 9
    private const val BEYOND_BLOCK = 5
    private const val BEYOND_ROWS = 3

    /** A single row of houses faces a main street whose middle is this near the ground past its side. */
    private const val MAIN_STREET_NEAR = 2.0

    /** The houses are kept in buckets this many squares wide while laid out, and kept this far apart at least. */
    private const val HOUSE_BUCKET = 8.0
    private const val HOUSE_BUCKETS = 1_000
    private const val HOUSE_APART = 0.1

    /** How tall the low blocks past the wide map's edges stand: as low as the far suburbs' houses, and a little more. */
    private const val BEYOND_LOW = 1.3
    private const val BEYOND_LOW_SPREAD = 0.7

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
    private const val SHOP_SIGN = 0xF4EDE1
    private const val TYRE = 0x2B2B2B
    private const val FLAME = 0xF08A3C
    private const val FLAME_LIGHT = 0xFFD36B
    private const val ROOF_GREY = 0x8E959C
    private const val FOREST = 0x5E8F68
    private const val FOREST_DARK = 0x4D7A5E
    private const val ROCK = 0x8597A3
    private const val ROCK_LIGHT = 0xA8B6BF
    private const val SNOW = 0xF7F9FB
}
