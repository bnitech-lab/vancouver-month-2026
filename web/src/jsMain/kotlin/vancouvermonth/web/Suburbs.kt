package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A detached house on one of the wide map's residential blocks ([Suburbs]): its footprint ([shape]), turned as its
 * block is with its width along the street, how tall its walls are, its colours, the side its door is on and how far
 * along the front, and where its walls are in [Neighbourhood.blocks] and it is in [Neighbourhood.foldables]. It is
 * drawn, walls, roof and door, only near the camera ([HouseDetail]); farther off its row is one low box.
 */
class House(
    val shape: TurnedBox,
    val wall: Double,
    val colour: Int,
    val roof: Int,
    /** 1.0 for a door on the side the house's depth runs toward, -1.0 for the side it runs from. */
    val front: Double,
    /** How far along the front from its middle the door is. */
    val doorAt: Double,
    val body: Int = -1,
    val foldable: Int = -1,
) {
    /** How far the ridge stands above the walls: the roof is pitched at 45 degrees over the house and its eaves. */
    val ridge: Double
        get() = shape.width / 2 + Suburbs.EAVE
}

/**
 * One residential [block], brought in from its streets: its houses, drawn one by one only near the camera
 * ([HouseDetail]) with its [lawn] (none on a block that reaches a lot kept clear) and the trees in its yards, each a
 * trunk and then a crown; farther off, each row of houses as one low box (each house one, out where the yards are
 * wide), [rows] of them in [Neighbourhood.blocks] from [firstRow]. Everything of it lies within [reach] of its [middle].
 */
class Yard(
    val block: TurnedBox,
    val lawn: Block?,
    val trees: List<Block>,
    val houses: List<House>,
    val firstRow: Int = -1,
    val rows: Int = 0,
) {
    val middle: Spot
        get() = Spot(block.x, block.z)

    /** Half the block's diagonal, and room for a tree's crown at a corner. */
    val reach: Double
        get() = hypot(block.width, block.depth) / 2 + 0.5
}

/**
 * How East Vancouver's and Burnaby's residential blocks are split into lots, one detached house on each: a row of
 * houses along the block's long side, or two back to back on a deep block, each with its door to the street. The
 * farther a block is from [DOWNTOWN], the smaller and lower its houses and the wider the yards between them, as the
 * city thins out away from its middle.
 */
object Suburbs {
    /** Downtown's middle, the business district round 회사, which the city thins out from. */
    val DOWNTOWN = Spot(160.0, 62.0)

    /** Houses are biggest and closest together this far from [DOWNTOWN] (the town's corner ends about here)... */
    const val NEAR = 90.0

    /** ...and smallest and farthest apart from this far on. */
    const val FAR = 480.0

    /** How far a roof reaches past the walls along the house's sides. */
    const val EAVE = 0.1

    /**
     * A block this far out or nearer is drawn far off as one low box a row, its houses in one colour; farther out, where
     * the yards are wide, as a low box a house.
     */
    const val ROWS_UP_TO = 0.5

    /** How far out from [DOWNTOWN] [spot] is, from 0.0 by the town's corner to 1.0 at the far end of Burnaby. */
    fun share(spot: Spot): Double = ((distanceBetween(spot, DOWNTOWN) - NEAR) / (FAR - NEAR)).coerceIn(0.0, 1.0)

    /** The lot's house: how wide along the street, how deep, how tall its walls, at [share] of the way out. */
    fun houseWidth(share: Double): Double = outward(WIDTH_NEAR, WIDTH_FAR, share)

    fun houseDepth(share: Double): Double = outward(DEPTH_NEAR, DEPTH_FAR, share)

    fun wallHeight(share: Double): Double = outward(WALL_NEAR, WALL_FAR, share)

    /**
     * The least yard between two houses in a row, at [share] of the way out: it widens slowly at first, through East
     * Vancouver, and faster out in Burnaby.
     */
    fun gap(share: Double): Double = outward(GAP_NEAR, GAP_FAR, share * share)

    /**
     * Splits [block] (a residential block, already brought in from its streets) into lots at [share] of the way out
     * from downtown, a house on each in one of [walls], and plants a tree or two in its widest yards, all by [chances].
     * The houses' indices are left unset, for the town to set as it builds them.
     */
    fun layOut(block: TurnedBox, share: Double, walls: List<Int>, chances: Random): Yard {
        val lawn = block.block(0.0, LAWN_HEIGHT, LAWN)
        // Lots run along the block's long side, so each house's width is along the street and its depth across it.
        val lengthwise = block.width >= block.depth
        val along = if (lengthwise) block.width else block.depth
        val across = if (lengthwise) block.depth else block.width
        val turn = if (lengthwise) block.turn else block.turn + PI / 2
        val cosine = cos(turn)
        val sine = sin(turn)
        fun spotAt(alongBy: Double, acrossBy: Double) =
            Spot(block.x + alongBy * cosine + acrossBy * sine, block.z - alongBy * sine + acrossBy * cosine)

        val depth = min(houseDepth(share), across - 2 * MARGIN)
        var width = houseWidth(share)
        val gap = gap(share)
        var count = floor((along + gap / 2) / (width + gap)).toInt()
        if (count == 0 && along - 2 * MARGIN >= LEAST) {
            count = 1
            width = along - 2 * MARGIN
        }
        if (count == 0 || depth < LEAST) return Yard(block, lawn, emptyList(), emptyList())
        // The yards between houses take what is left, and each end a quarter of one.
        val left = along - count * width
        val inner = if (count > 1) left / (count - 0.5) else 0.0
        val end = if (count > 1) inner / 4 else left / 2
        // A deep block has a row each side, back to back; a shallow one a single row facing one of its streets.
        val setback = outward(SETBACK_NEAR, SETBACK_FAR, share)
        val fronts = if (across >= 2 * (depth + setback) + BACK_TO_BACK) {
            listOf(1.0, -1.0)
        } else {
            listOf(if (chances.nextBoolean()) 1.0 else -1.0)
        }
        val houses = mutableListOf<House>()
        for (front in fronts) {
            val inFront = min(setback, (across - depth) / 2)
            val acrossBy = front * (across / 2 - inFront - depth / 2)
            // A row drawn far off as one box is one colour, so it keeps its colour as its houses come near.
            var rowColour = -1
            for (lot in 0 until count) {
                val alongBy = -along / 2 + end + width / 2 + lot * (width + inner)
                val spot = spotAt(alongBy, acrossBy)
                val wall = wallHeight(share) * (1 - WALL_SPREAD + 2 * WALL_SPREAD * chances.nextDouble())
                var colour = walls[(chances.nextDouble() * walls.size).toInt().coerceAtMost(walls.size - 1)]
                if (share <= ROWS_UP_TO) {
                    if (rowColour < 0) rowColour = colour
                    colour = rowColour
                }
                val roof = ROOFS[(chances.nextDouble() * ROOFS.size).toInt().coerceAtMost(ROOFS.size - 1)]
                val doorAt = (chances.nextDouble() - 0.5) * DOOR_SWAY * width
                houses.add(House(TurnedBox(spot.x, spot.z, width, depth, turn), wall, colour, roof, front, doorAt))
            }
        }
        // A tree in a yard wide enough for one: between two houses, at an end of the row, or behind a single row.
        val trees = mutableListOf<Block>()
        val rowAcross = if (fronts.size == 1) fronts[0] * (across / 2 - min(setback, (across - depth) / 2) - depth / 2) else 0.0
        val room = mutableListOf<Triple<Double, Double, Double>>()
        if (count > 1 && inner >= TREE_ROOM) {
            for (lot in 1 until count) room.add(Triple(-along / 2 + end + lot * (width + inner) - inner / 2, rowAcross, inner))
        }
        if (end >= TREE_ROOM) {
            room.add(Triple(-along / 2 + end / 2, rowAcross, end))
            room.add(Triple(along / 2 - end / 2, rowAcross, end))
        }
        val behind = across - depth - min(setback, (across - depth) / 2)
        if (fronts.size == 1 && behind >= TREE_ROOM) {
            room.add(Triple((chances.nextDouble() - 0.5) * (along - TREE_ROOM), rowAcross - fronts[0] * (depth / 2 + behind / 2), behind))
        }
        val keep = outward(TREES_NEAR, TREES_FAR, share)
        for ((alongBy, acrossBy, space) in room) {
            if (trees.size >= 2 * MOST_TREES) break
            if (chances.nextDouble() >= keep) continue
            val spot = spotAt(alongBy, acrossBy)
            val crown = min(CROWN, space - 0.1)
            trees.add(Block(spot.x - TRUNK / 2, 0.0, spot.z - TRUNK / 2, TRUNK, TRUNK_HEIGHT, TRUNK, TREE_TRUNK))
            val leaves = if (chances.nextBoolean()) TREE_LEAVES else TREE_LEAVES_LIGHT
            trees.add(Block(spot.x - crown / 2, TRUNK_HEIGHT - 0.1, spot.z - crown / 2, crown, crown * 0.95, crown, leaves))
        }
        return Yard(block, lawn, trees, houses)
    }

    /** The middle and the four corners of [shape] on the ground. */
    fun cornersOf(shape: TurnedBox): List<Spot> {
        val cosine = cos(shape.turn)
        val sine = sin(shape.turn)
        return listOf(Spot(shape.x, shape.z)) + listOf(-1.0 to -1.0, -1.0 to 1.0, 1.0 to -1.0, 1.0 to 1.0).map { (along, across) ->
            val x = along * shape.width / 2
            val z = across * shape.depth / 2
            Spot(shape.x + x * cosine + z * sine, shape.z - x * sine + z * cosine)
        }
    }

    private fun outward(near: Double, far: Double, share: Double): Double = near + (far - near) * share

    // Each house's size, wall height and the yards round it, by the town's corner and at the far end of Burnaby.
    private const val WIDTH_NEAR = 1.4
    private const val WIDTH_FAR = 1.15
    private const val DEPTH_NEAR = 1.55
    private const val DEPTH_FAR = 1.3
    private const val WALL_NEAR = 1.45
    private const val WALL_FAR = 1.05
    private const val GAP_NEAR = 0.3
    private const val GAP_FAR = 1.9
    private const val SETBACK_NEAR = 0.12
    private const val SETBACK_FAR = 0.4

    /** Houses' walls differ by up to this share either way. */
    private const val WALL_SPREAD = 0.08

    /** A house keeps this far in from its block's edges; none is narrower or shallower than [LEAST]. */
    private const val MARGIN = 0.1
    private const val LEAST = 0.8

    /** The room between two rows of houses back to back. */
    private const val BACK_TO_BACK = 0.3

    /** How far either way of the front's middle the door may stand, as a share of the house's width. */
    private const val DOOR_SWAY = 0.4

    /** A yard holds a tree when it is this wide; this share of such yards have one, and a block at most this many. */
    private const val TREE_ROOM = 0.9
    private const val TREES_NEAR = 0.3
    private const val TREES_FAR = 0.6
    private const val MOST_TREES = 2
    private const val TRUNK = 0.22
    private const val TRUNK_HEIGHT = 0.75
    private const val CROWN = 1.0

    private const val LAWN_HEIGHT = 0.035
    private const val LAWN = 0xA4CC74
    private const val TREE_TRUNK = 0x7E5A3A
    private const val TREE_LEAVES = 0x6FAE5A
    private const val TREE_LEAVES_LIGHT = 0x86BE64

    /** Roofs: slate, charcoal, brick, brown and moss, as on the city's older houses. */
    private val ROOFS = listOf(0x5E6B78, 0x4F5257, 0xA0503F, 0x7A5A44, 0x5F7A5A)
}
