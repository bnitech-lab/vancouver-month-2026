package vancouvermonth.web

import kotlin.math.cos
import kotlin.math.sin
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import vancouvermonth.text.Passer
import vancouvermonth.text.Townsperson

/** The part of a person a box belongs to. A leg swings from the hip and an arm from the shoulder. */
enum class Limb(val pivot: Point) {
    BODY(Point(0.0, 0.0, 0.0)),
    LEFT_LEG(Point(-Figures.LEG_X, Figures.HIP, 0.0)),
    RIGHT_LEG(Point(Figures.LEG_X, Figures.HIP, 0.0)),
    LEFT_ARM(Point(-Figures.ARM_X, Figures.SHOULDER, 0.0)),
    RIGHT_ARM(Point(Figures.ARM_X, Figures.SHOULDER, 0.0)),
}

/** One box of a person, placed from its [limb]'s pivot, so a leg turns about the hip. */
data class Part(val limb: Limb, val block: Block)

/** The colours a person is drawn in; the talk box draws a small face from the same colours. */
data class Look(val skin: Int, val hair: Int, val top: Int, val bottom: Int, val shoes: Int)

/**
 * How a figure stands at one moment: where, which way it faces (0 faces south, as in [Trip]), how high it bobs, how far
 * its legs and arms swing, and how far its left arm is raised to the side for a wave (0 hangs down).
 */
data class Pose(
    val spot: Spot,
    val heading: Double,
    val lift: Double = 0.0,
    val swing: Double = 0.0,
    val wave: Double = 0.0,
)

/**
 * The player and the townspeople as small, round-headed figures: a big head on a small body, as in a cosy game.
 * Each one stands on the spot (0, 0) facing south. Left and right are as seen from the front.
 */
object Figures {
    const val HIP = 0.36
    const val SHOULDER = 0.72
    const val LEG_X = 0.11
    const val ARM_X = 0.29

    /** Every figure is drawn this much larger than its boxes, so it reads on a phone. */
    const val SCALE = 1.15

    val playerLook = Look(skin = 0xF6D2B0, hair = 0x3A2A20, top = 0xD9534A, bottom = 0x3F5A8A, shoes = 0x5A3E2B)

    /** The player: a red jacket and a backpack. */
    val player: List<Part> = body(playerLook) + Part(Limb.BODY, Block(-0.17, 0.4, -0.27, 0.34, 0.34, 0.13, 0xE0A43A))

    fun lookOf(person: Townsperson): Look = when (person) {
        Townsperson.HANA -> Look(skin = 0xF7D7BC, hair = 0x7A4128, top = 0xF4EDE1, bottom = 0x6D5847, shoes = 0x4A3426)
        Townsperson.LEO -> Look(skin = 0xF3D3B5, hair = 0xC9A66B, top = 0x7B8F5A, bottom = 0x5B4B3A, shoes = 0x3B2E24)
        Townsperson.SUJIN -> Look(skin = 0xF5D5B8, hair = 0x1E1A1A, top = 0x5B7DB1, bottom = 0x2E2E36, shoes = 0xEDEDED)
        Townsperson.MARCUS -> Look(skin = 0xB98258, hair = 0x241911, top = 0x2F3B52, bottom = 0x2F3B52, shoes = 0x1E1E1E)
        Townsperson.GRANDMA -> Look(skin = 0xF4D8C4, hair = 0xDCDCDC, top = 0xA0586B, bottom = 0x6E6259, shoes = 0x4A3B30)
    }

    /** A townsperson, with the one thing that marks them out. */
    fun townsperson(person: Townsperson): List<Part> {
        val look = lookOf(person)
        val extra = when (person) {
            // A green apron and a bun.
            Townsperson.HANA -> listOf(
                Part(Limb.BODY, Block(-0.2, 0.3, 0.15, 0.4, 0.44, 0.03, 0x3E8E6A)),
                Part(Limb.BODY, Block(-0.13, 1.42, -0.2, 0.26, 0.18, 0.26, look.hair)),
            )
            // Glasses, and a red book in one hand.
            Townsperson.LEO -> listOf(
                Part(Limb.BODY, Block(-0.24, 1.1, 0.305, 0.48, 0.03, 0.02, 0x2B2B2B)),
                Part(Limb.RIGHT_ARM, Block(-0.1, -0.46, 0.0, 0.2, 0.26, 0.07, 0x9A3B3B)),
            )
            // A ponytail and a yellow badge on a lanyard.
            Townsperson.SUJIN -> listOf(
                Part(Limb.BODY, Block(-0.1, 0.9, -0.44, 0.2, 0.4, 0.12, look.hair)),
                Part(Limb.BODY, Block(-0.05, 0.42, 0.15, 0.1, 0.13, 0.02, 0xF2C14E)),
            )
            // A white collar and a red tie.
            Townsperson.MARCUS -> listOf(
                Part(Limb.BODY, Block(-0.12, 0.68, 0.15, 0.24, 0.08, 0.02, 0xFFFFFF)),
                Part(Limb.BODY, Block(-0.04, 0.4, 0.15, 0.08, 0.3, 0.025, 0xB33A3A)),
            )
            // A bun, a shawl over the shoulders and a walking stick.
            Townsperson.GRANDMA -> listOf(
                Part(Limb.BODY, Block(-0.13, 1.42, -0.2, 0.26, 0.16, 0.26, look.hair)),
                Part(Limb.BODY, Block(-0.26, 0.6, -0.18, 0.52, 0.14, 0.36, 0xE8D7B0)),
                Part(Limb.RIGHT_ARM, Block(0.0, -0.72, 0.06, 0.05, 0.72, 0.05, 0x7A5233)),
            )
        }
        return body(look) + extra
    }

    fun lookOf(passer: Passer): Look = when (passer) {
        Passer.OLIVER -> Look(skin = 0xE8C3A0, hair = 0x8B5A2B, top = 0x4FA3A5, bottom = 0x2F3440, shoes = 0xF2F2F2)
        Passer.MINA -> Look(skin = 0xF6D6BD, hair = 0x2B1E1A, top = 0xF2D16B, bottom = 0x4A5568, shoes = 0x7A4E3A)
        Passer.PRIYA -> Look(skin = 0xC68E63, hair = 0x1F1712, top = 0xB65A7A, bottom = 0x3E3A5C, shoes = 0x5A3E2B)
        Passer.THEO -> Look(skin = 0xF1CFB0, hair = 0x6B4A2E, top = 0x8C9AA8, bottom = 0x3A3F4A, shoes = 0x2A2A2A)
    }

    /** A passerby, with what they carry or wear for the walk. */
    fun passer(passer: Passer): List<Part> {
        val look = lookOf(passer)
        val extra = when (passer) {
            // A headband for the run along the seawall.
            Passer.OLIVER -> listOf(Part(Limb.BODY, Block(-0.38, 1.1, -0.34, 0.76, 0.08, 0.68, 0xF06A5A)))
            // A yellow raincoat, and a closed umbrella in one hand.
            Passer.MINA -> listOf(Part(Limb.LEFT_ARM, Block(-0.03, -0.7, 0.03, 0.06, 0.4, 0.06, 0x3E6FB0)))
            // Long hair down the back, and a cream scarf.
            Passer.PRIYA -> listOf(
                Part(Limb.BODY, Block(-0.3, 0.6, -0.36, 0.6, 0.45, 0.08, look.hair)),
                Part(Limb.BODY, Block(-0.24, 0.66, -0.17, 0.48, 0.12, 0.34, 0xF1E3C8)),
            )
            // A knitted hat, and a cup of coffee with a lid.
            Passer.THEO -> listOf(
                Part(Limb.BODY, Block(-0.36, 1.36, -0.32, 0.72, 0.14, 0.64, 0x2F6B5A)),
                Part(Limb.RIGHT_ARM, Block(-0.07, -0.52, 0.02, 0.14, 0.16, 0.14, 0xF4EDE1)),
                Part(Limb.RIGHT_ARM, Block(-0.075, -0.37, 0.015, 0.15, 0.03, 0.15, 0x6B4630)),
            )
        }
        return body(look) + extra
    }

    /**
     * Each box of [parts] in [pose], as the 3 by 4 matrix (row by row: three turns and stretches, then the move) that
     * takes the unit cube, centred on (0, 0, 0), to where that box is drawn in the town. A leg turns about the hip and
     * an arm about the shoulder, as the player's do; the whole figure is drawn [SCALE] times its boxes. Plain and easy
     * to check, but it makes many small arrays: the town draws with [FigureBoxes], tested to give the same numbers.
     */
    fun place(parts: List<Part>, pose: Pose): List<DoubleArray> {
        val turn = turnY(pose.heading)
        return parts.map { part ->
            val block = part.block
            val limb = limbTurn(part.limb, pose)
            val centre = doubleArrayOf(block.x + block.width / 2, block.y + block.height / 2, block.z + block.depth / 2)
            val linear = times(turn, times(limb, diagonal(block.width, block.height, block.depth)))
            val pivot = part.limb.pivot
            val fromPivot = apply(limb, centre)
            val offset = apply(turn, doubleArrayOf(pivot.x + fromPivot[0], pivot.y + fromPivot[1], pivot.z + fromPivot[2]))
            val move = doubleArrayOf(pose.spot.x, pose.lift, pose.spot.z)
            DoubleArray(12) { index ->
                val row = index / 4
                val column = index % 4
                if (column < 3) linear[row * 3 + column] * SCALE else move[row] + offset[row] * SCALE
            }
        }
    }

    /** How [limb] is turned in [pose]: legs and arms swing as in a walk, and the left arm rises to the side to wave. */
    private fun limbTurn(limb: Limb, pose: Pose): DoubleArray = when (limb) {
        Limb.BODY -> diagonal(1.0, 1.0, 1.0)
        Limb.LEFT_LEG -> turnX(pose.swing)
        Limb.RIGHT_LEG -> turnX(-pose.swing)
        Limb.LEFT_ARM -> times(turnZ(-pose.wave), turnX(-pose.swing * 0.8))
        Limb.RIGHT_ARM -> turnX(pose.swing * 0.8)
    }

    // 3 by 3 matrices, row by row, as Three.js turns things: about x, y and z, each by the right hand.
    private fun turnX(angle: Double) =
        doubleArrayOf(1.0, 0.0, 0.0, 0.0, cos(angle), -sin(angle), 0.0, sin(angle), cos(angle))

    private fun turnY(angle: Double) =
        doubleArrayOf(cos(angle), 0.0, sin(angle), 0.0, 1.0, 0.0, -sin(angle), 0.0, cos(angle))

    private fun turnZ(angle: Double) =
        doubleArrayOf(cos(angle), -sin(angle), 0.0, sin(angle), cos(angle), 0.0, 0.0, 0.0, 1.0)

    private fun diagonal(x: Double, y: Double, z: Double) = doubleArrayOf(x, 0.0, 0.0, 0.0, y, 0.0, 0.0, 0.0, z)

    private fun times(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { index ->
        val row = index / 3
        val column = index % 3
        (0..2).sumOf { a[row * 3 + it] * b[it * 3 + column] }
    }

    private fun apply(matrix: DoubleArray, point: DoubleArray) = DoubleArray(3) { row ->
        (0..2).sumOf { matrix[row * 3 + it] * point[it] }
    }

    /** The shared body: legs and shoes, a small torso, arms with hands, and a big head with hair, eyes and cheeks. */
    private fun body(look: Look): List<Part> = buildList {
        for (leg in listOf(Limb.LEFT_LEG, Limb.RIGHT_LEG)) {
            add(Part(leg, Block(-0.08, -HIP, -0.09, 0.16, HIP, 0.18, look.bottom)))
            add(Part(leg, Block(-0.09, -HIP, -0.1, 0.18, 0.09, 0.22, look.shoes)))
        }
        for (arm in listOf(Limb.LEFT_ARM, Limb.RIGHT_ARM)) {
            add(Part(arm, Block(-0.06, -0.34, -0.07, 0.12, 0.36, 0.14, look.top)))
            add(Part(arm, Block(-0.06, -0.41, -0.07, 0.12, 0.08, 0.14, look.skin)))
        }
        add(Part(Limb.BODY, Block(-0.22, 0.32, -0.15, 0.44, 0.44, 0.3, look.top)))
        add(Part(Limb.BODY, Block(-0.34, 0.76, -0.3, 0.68, 0.6, 0.6, look.skin)))
        add(Part(Limb.BODY, Block(-0.37, 1.24, -0.33, 0.74, 0.2, 0.66, look.hair)))
        add(Part(Limb.BODY, Block(-0.37, 0.86, -0.33, 0.74, 0.4, 0.14, look.hair)))
        add(Part(Limb.BODY, Block(-0.35, 1.15, 0.22, 0.7, 0.12, 0.1, look.hair)))
        for (side in listOf(-0.37, 0.31)) add(Part(Limb.BODY, Block(side, 0.95, -0.33, 0.06, 0.32, 0.5, look.hair)))
        for (side in listOf(-0.17, 0.08)) add(Part(Limb.BODY, Block(side, 0.97, 0.3, 0.09, 0.14, 0.02, EYES)))
        for (side in listOf(-0.27, 0.17)) add(Part(Limb.BODY, Block(side, 0.9, 0.3, 0.1, 0.05, 0.015, CHEEKS)))
    }

    private const val EYES = 0x2A2220
    private const val CHEEKS = 0xF4A3A0
}

/**
 * A figure's boxes made ready to be placed many times a second: each box's middle, size and limb pivot are worked out
 * once, and [write] puts them in a pose with plain arithmetic, making nothing new. It gives the same numbers as
 * [Figures.place], which is its tested reference.
 */
class FigureBoxes(parts: List<Part>) {
    val count = parts.size
    private val limbs = IntArray(count) { parts[it].limb.ordinal }
    private val middles = DoubleArray(count * 3)
    private val sizes = DoubleArray(count * 3)
    private val pivots = DoubleArray(count * 3)

    /** Each limb's turn in the pose being written, nine numbers row by row, in the order of [Limb]. */
    private val turns = DoubleArray(Limb.entries.size * 9)

    init {
        for ((index, part) in parts.withIndex()) {
            val block = part.block
            val at = index * 3
            middles[at] = block.x + block.width / 2
            middles[at + 1] = block.y + block.height / 2
            middles[at + 2] = block.z + block.depth / 2
            sizes[at] = block.width
            sizes[at + 1] = block.height
            sizes[at + 2] = block.depth
            pivots[at] = part.limb.pivot.x
            pivots[at + 1] = part.limb.pivot.y
            pivots[at + 2] = part.limb.pivot.z
        }
    }

    /**
     * Writes every box in [pose] into [into] the way Three.js keeps a matrix, sixteen numbers column by column, the
     * first box at box number [first].
     */
    fun write(pose: Pose, into: Float32Array, first: Int) {
        turn(Limb.BODY, 0.0, 0.0)
        turn(Limb.LEFT_LEG, pose.swing, 0.0)
        turn(Limb.RIGHT_LEG, -pose.swing, 0.0)
        turn(Limb.LEFT_ARM, -pose.swing * 0.8, -pose.wave)
        turn(Limb.RIGHT_ARM, pose.swing * 0.8, 0.0)
        val ch = cos(pose.heading)
        val sh = sin(pose.heading)
        val k = Figures.SCALE
        for (box in 0 until count) {
            val r = limbs[box] * 9
            val at = box * 3
            val cx = middles[at]
            val cy = middles[at + 1]
            val cz = middles[at + 2]
            // Where the box's middle is on the figure: its limb's pivot, plus the middle turned with the limb.
            val q0 = pivots[at] + turns[r] * cx + turns[r + 1] * cy + turns[r + 2] * cz
            val q1 = pivots[at + 1] + turns[r + 3] * cx + turns[r + 4] * cy + turns[r + 5] * cz
            val q2 = pivots[at + 2] + turns[r + 6] * cx + turns[r + 7] * cy + turns[r + 8] * cz
            val o = (first + box) * 16
            for (column in 0..2) {
                // The limb's turn, then the figure's turn about the vertical, stretched to the box and drawn larger.
                val stretch = k * sizes[at + column]
                val top = turns[r + column]
                val middle = turns[r + 3 + column]
                val bottom = turns[r + 6 + column]
                into[o + column * 4] = ((ch * top + sh * bottom) * stretch).toFloat()
                into[o + column * 4 + 1] = (middle * stretch).toFloat()
                into[o + column * 4 + 2] = ((-sh * top + ch * bottom) * stretch).toFloat()
                into[o + column * 4 + 3] = 0f
            }
            into[o + 12] = (pose.spot.x + k * (ch * q0 + sh * q2)).toFloat()
            into[o + 13] = (pose.lift + k * q1).toFloat()
            into[o + 14] = (pose.spot.z + k * (-sh * q0 + ch * q2)).toFloat()
            into[o + 15] = 1f
        }
    }

    /** Sets [limb]'s turn: [x] about the side-to-side axis (a swing), then [z] about the front-to-back one (a wave). */
    private fun turn(limb: Limb, x: Double, z: Double) {
        val cx = cos(x)
        val sx = sin(x)
        val cz = cos(z)
        val sz = sin(z)
        val r = limb.ordinal * 9
        turns[r] = cz
        turns[r + 1] = -sz * cx
        turns[r + 2] = sz * sx
        turns[r + 3] = sz
        turns[r + 4] = cz * cx
        turns[r + 5] = -cz * sx
        turns[r + 6] = 0.0
        turns[r + 7] = sx
        turns[r + 8] = cx
    }
}
