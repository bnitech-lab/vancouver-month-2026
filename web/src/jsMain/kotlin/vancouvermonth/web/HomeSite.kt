package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import vancouvermonth.web.Neighbourhood.REACH

/** 내 방 with its garden as boxes: the walls, roof, garden, bench and fire stones; its windows and lamps; and the squares it blocks. */
internal class HomeParts(val blocks: List<Block>, val glowing: List<Block>, val footprints: List<Cell>)

/**
 * The town as the player lives in it for a room in [zone]: the five places with 내 방 where the room is
 * ([Neighbourhood.homes]), its garden, bench and campfire with it and its neighbour at its door, and the ground to walk
 * on with them. Everything else is the same in every zone ([Neighbourhood]). Each zone's is made once, when first asked.
 */
class HomeSite private constructor(val zone: Int) {
    /** 내 방 for this zone. */
    val home: Venue = Neighbourhood.home(zone)

    /** The five places, this zone's 내 방 among them, in [Neighbourhood.venues]' order. */
    val venues: List<Venue> = Neighbourhood.venues.map { if (it.place == Place.HOME) home else it }

    fun venue(place: Place): Venue = venues.first { it.place == place }

    /** The bench in 내 방's garden, where the character sits by the campfire on a day of rest, facing north across the fire. */
    val benchSeat: Spot = benchSeatAt(home)

    /** The middle of the campfire in front of the bench. */
    val campfire: Spot = campfireAt(home)

    /**
     * The first view's turn round 내 방's door, as [FollowCamera.yaw] counts it. In zone 2 the guideway runs just south of
     * the door, so the first view looks along the street from the west, over the garden, the guideway to one side,
     * rather than across it from the south-west, as elsewhere.
     */
    val startYaw: Double = if (zone == 2) -PI / 2 else FollowCamera.START_YAW

    private val parts = Neighbourhood.homeParts(home)

    /** 내 방's boxes that stand still: the house, its roof and chimney, the garden's fence, bench, stones and lamps. */
    val blocks: List<Block>
        get() = parts.blocks

    /** 내 방's windows and its lamps, which glow after dark. */
    val glowing: List<Block>
        get() = parts.glowing

    /** A box around each place's person, and around the shop's keeper, so a tap on them talks to them. */
    val people: List<Wall> = venues.map { venue ->
        Wall(Block(venue.person.x - 0.4, 0.0, venue.person.z - 0.4, 0.8, 1.8, 0.8, 0), venue.place)
    } + Wall(Block(Neighbourhood.shop.keeper.x - 0.4, 0.0, Neighbourhood.shop.keeper.z - 0.4, 0.8, 1.8, 0.8, 0), null, shop = true)

    /**
     * What a tap stops at: the buildings (the five places among them), the SkyTrain deck, the stations and their roofs,
     * the tunnel's mouth, the café's awning and the office's canopy, and the city's buildings, which fold down out of the
     * camera's way ([Neighbourhood.foldables]) where the camera stays out of the rest.
     */
    val walls: List<Wall> = venues.map { Wall(it.box, it.place) } + Neighbourhood.sharedWalls

    /** The ground to walk on: the town's, with 내 방, its garden and its neighbour on it, and no walled-in pocket. */
    val grid: Grid

    /** How many free squares were blocked because the character could never walk to them: walled-in pockets. */
    val pockets: Int

    init {
        val person = Cell(floor(home.person.x).toInt(), floor(home.person.z).toInt())
        val standing = Neighbourhood.open.with(parts.footprints + person)
        val walledIn = standing.cutOffFrom(standing.cellAt(home.door))
        pockets = walledIn.size
        grid = standing.with(walledIn)
    }

    /** The place whose door or person [spot] is within [REACH] of, or null when it is near none. */
    fun placeAt(spot: Spot): Place? = venues.firstOrNull { venue ->
        hypot(venue.door.x - spot.x, venue.door.z - spot.z) <= REACH ||
            hypot(venue.talkSpot.x - spot.x, venue.talkSpot.z - spot.z) <= REACH
    }?.place

    /**
     * Where a tap at [tapped] sends a character standing at [from]: the spot itself on land, and on the water the last
     * free square on the straight way from [from] toward it before the water, so a tap on False Creek stops on the near
     * shore rather than leading all the way round the creek's head to the far one.
     */
    fun landing(from: Spot, tapped: Spot): Spot {
        val map = Neighbourhood.map
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

    companion object {
        private val made = mutableMapOf<Int, HomeSite>()

        /** The town for a room in [zone]: zone 1's for any number but 1 to 3. */
        fun of(zone: Int): HomeSite {
            val known = if (zone in Neighbourhood.homes) zone else 1
            return made.getOrPut(known) { HomeSite(known) }
        }

        /** Where the bench stands in [home]'s garden. */
        fun benchSeatAt(home: Venue): Spot = Spot(home.x - 2.7, home.z + home.depth - 1.1)

        /** Where the campfire burns in [home]'s garden, in front of the bench. */
        fun campfireAt(home: Venue): Spot = Spot(home.x - 2.7, home.z + home.depth - 3.0)

        /** How finely a tap's straight way is looked along for the water's edge. */
        private const val LANDING_STEP = 0.25
    }
}

/**
 * A box on the ground turned about its middle [x], [z]: [width] along its turn and [depth] across it, [turn] in radians
 * as [Block.turn] counts it. The wide map's buildings are such boxes.
 */
class TurnedBox(val x: Double, val z: Double, val width: Double, val depth: Double, val turn: Double) {
    private val cosine = cos(turn)
    private val sine = sin(turn)

    /** It as a [Block] from [bottom], [height] tall, in [colour]. */
    fun block(bottom: Double, height: Double, colour: Int): Block =
        Block(x - width / 2, bottom, z - depth / 2, width, height, depth, colour, turn)

    /** The box straight on the grid that holds it, [height] tall: for the camera's cutaway, which knows no turns. */
    fun bounds(height: Double, colour: Int): Block {
        val halfX = abs(width / 2 * cosine) + abs(depth / 2 * sine)
        val halfZ = abs(width / 2 * sine) + abs(depth / 2 * cosine)
        return Block(x - halfX, 0.0, z - halfZ, 2 * halfX, height, 2 * halfZ, colour)
    }

    /** How far the spot [px], [pz] is from it on the ground: 0.0 inside. */
    fun distanceTo(px: Double, pz: Double): Double {
        val dx = px - x
        val dz = pz - z
        val along = abs(dx * cosine - dz * sine) - width / 2
        val across = abs(dx * sine + dz * cosine) - depth / 2
        return hypot(max(0.0, along), max(0.0, across))
    }

    /** True when it comes within [margin] of [lot]'s squares: the two are tried along each of their four sides' ways. */
    fun overlaps(lot: Lot, margin: Double): Boolean {
        val halfX = lot.width / 2.0 + margin
        val halfZ = lot.depth / 2.0 + margin
        val dx = x - (lot.x + lot.width / 2.0)
        val dz = z - (lot.z + lot.depth / 2.0)
        // Its own two ways on the ground, in x and z: along it, and across it.
        val alongX = cosine
        val alongZ = -sine
        val acrossX = sine
        val acrossZ = cosine
        val reachX = abs(width / 2 * alongX) + abs(depth / 2 * acrossX)
        val reachZ = abs(width / 2 * alongZ) + abs(depth / 2 * acrossZ)
        if (abs(dx) > halfX + reachX || abs(dz) > halfZ + reachZ) return false
        if (abs(dx * alongX + dz * alongZ) > width / 2 + halfX * abs(alongX) + halfZ * abs(alongZ)) return false
        return abs(dx * acrossX + dz * acrossZ) <= depth / 2 + halfX * abs(acrossX) + halfZ * abs(acrossZ)
    }
}

/**
 * Points every half square along [lines], kept in buckets of the ground, to ask quickly what comes near them: a box
 * ([anyWithin]) or a spot ([within]). Made once, as the town is laid out.
 */
internal class Samples(lines: List<List<Spot>>) {
    private val buckets = mutableMapOf<Int, MutableList<Spot>>()

    init {
        for (line in lines) {
            for ((a, b) in line.zipWithNext()) {
                val steps = max(1, ceil(distanceBetween(a, b) / STEP).toInt())
                for (step in 0..steps) {
                    val spot = Spot(a.x + (b.x - a.x) * step / steps, a.z + (b.z - a.z) * step / steps)
                    buckets.getOrPut(keyOf(floor(spot.x / BUCKET).toInt(), floor(spot.z / BUCKET).toInt())) { mutableListOf() }
                        .add(spot)
                }
            }
        }
    }

    /** True when a point of the lines lies within [reach] of [shape]. */
    fun anyWithin(shape: TurnedBox, reach: Double): Boolean {
        val out = max(shape.width, shape.depth) / 2 + reach
        return near(shape.x, shape.z, out) { shape.distanceTo(it.x, it.z) < reach }
    }

    /** True when a point of the lines lies within [reach] of [spot]. */
    fun within(spot: Spot, reach: Double): Boolean = near(spot.x, spot.z, reach) { distanceBetween(it, spot) < reach }

    private fun near(x: Double, z: Double, out: Double, hit: (Spot) -> Boolean): Boolean {
        for (column in floor((x - out) / BUCKET).toInt()..floor((x + out) / BUCKET).toInt()) {
            for (row in floor((z - out) / BUCKET).toInt()..floor((z + out) / BUCKET).toInt()) {
                val points = buckets[keyOf(column, row)] ?: continue
                for (point in points) if (hit(point)) return true
            }
        }
        return false
    }

    private fun keyOf(column: Int, row: Int): Int = (column + OFFSET) * SPAN + row + OFFSET

    private companion object {
        const val STEP = 0.5
        const val BUCKET = 4.0
        const val OFFSET = 1_000
        const val SPAN = 4_096
    }
}
