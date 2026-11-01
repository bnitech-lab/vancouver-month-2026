package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set

/**
 * The suburbs' houses as drawn near the camera ([Neighbourhood.yards]): each house's walls, pitched roof and door, and
 * each block's lawn and yard trees. Far off, each row of houses is one low box, which the town always draws; within
 * [REACH] of the camera and in its view, a block's houses stand one by one instead ([shown]), their roofs and doors
 * growing on over the last [GROW] as the camera comes nearer, and the lawns reach on to [LAWN_REACH]. A house folds out
 * of the camera's way as the city's buildings do, its roof sinking with its walls. [fill] writes them into [numbers]
 * (sixteen for each box, as a draw of many boxes keeps them) and [colours], the nearest blocks first and never more than
 * [CAPACITY] boxes, making nothing new.
 */
internal class HouseDetail(private val yards: List<Yard>, private val numbers: Float32Array) {
    /** Each box's colour, as 0xRRGGBB, for the first [count] boxes. */
    val colours = IntArray(CAPACITY)

    /** How many boxes the last [fill] wrote. */
    var count = 0
        private set

    private val middleX = DoubleArray(yards.size) { yards[it].middle.x }
    private val middleZ = DoubleArray(yards.size) { yards[it].middle.z }
    private val reach = DoubleArray(yards.size) { yards[it].reach }

    /** How far each block is from the camera on the ground, its nearest edge, or -1.0 when it is out of the camera's view. */
    private val away = DoubleArray(yards.size)

    /** True for each block whose houses the last [fill] drew one by one, in place of its rows' low boxes. */
    val shown = BooleanArray(yards.size)

    /** The blocks the last [fill] began or stopped drawing one by one, the first [changedCount] of them. */
    val changed = IntArray(yards.size)
    var changedCount = 0
        private set

    /**
     * Writes the roofs, doors, lawns and trees for a camera at [eyeX], [eyeY], [eyeZ] looking at [focusX], [focusY],
     * [focusZ], seeing [tanAcross] to each side and [tanUp] up and down for each unit ahead; [foldOf] says how far each
     * of [Neighbourhood.foldables] is folded as drawn.
     */
    fun fill(
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double,
        focusX: Double,
        focusY: Double,
        focusZ: Double,
        tanAcross: Double,
        tanUp: Double,
        foldOf: (Int) -> Double,
    ) {
        // The camera's own ways: ahead, to its right and its up, as Three.js turns a camera to look at a point.
        var aheadX = focusX - eyeX
        var aheadY = focusY - eyeY
        var aheadZ = focusZ - eyeZ
        val length = sqrt(aheadX * aheadX + aheadY * aheadY + aheadZ * aheadZ)
        aheadX /= length
        aheadY /= length
        aheadZ /= length
        val level = hypot(aheadX, aheadZ)
        val rightX = -aheadZ / level
        val rightZ = aheadX / level
        val upX = -aheadY * rightZ
        val upY = rightZ * aheadX - rightX * aheadZ
        val upZ = aheadY * rightX
        val acrossSlant = sqrt(1 + tanAcross * tanAcross)
        val upSlant = sqrt(1 + tanUp * tanUp)
        for (index in yards.indices) {
            val onGround = hypot(middleX[index] - eyeX, middleZ[index] - eyeZ) - reach[index]
            away[index] = -1.0
            if (onGround > LAWN_REACH) continue
            // A ball round the block and its houses, tried against the four sides of the camera's view.
            val ball = hypot(reach[index], BALL_HEIGHT) + VIEW_SLACK
            val dx = middleX[index] - eyeX
            val dy = BALL_HEIGHT - eyeY
            val dz = middleZ[index] - eyeZ
            val ahead = dx * aheadX + dy * aheadY + dz * aheadZ
            if (ahead < -ball) continue
            val side = dx * rightX + dz * rightZ
            if (kotlin.math.abs(side) > ahead * tanAcross + ball * acrossSlant) continue
            val up = dx * upX + dy * upY + dz * upZ
            if (kotlin.math.abs(up) > ahead * tanUp + ball * upSlant) continue
            away[index] = kotlin.math.max(0.0, onGround)
        }
        count = 0
        changedCount = 0
        // The nearest blocks first, so when there are too many boxes it is the farthest that go without.
        for (index in yards.indices) {
            if (away[index] >= 0.0 && away[index] <= REACH / 2) show(index, addYard(yards[index], eyeX, eyeZ, foldOf))
        }
        for (index in yards.indices) {
            val far = away[index]
            if (far >= 0.0 && far <= REACH / 2) continue
            show(index, far > REACH / 2 && far <= REACH && addYard(yards[index], eyeX, eyeZ, foldOf))
        }
        for (index in yards.indices) {
            val lawn = yards[index].lawn ?: continue
            if (away[index] > REACH && count < CAPACITY) addBox(lawn, 1.0, lawn.colour)
        }
    }

    /** Notes whether block [index]'s houses are now drawn one by one, and whether that changed. */
    private fun show(index: Int, houses: Boolean) {
        if (shown[index] == houses) return
        shown[index] = houses
        changed[changedCount++] = index
    }

    /**
     * A block's lawn, trees, and each house's walls, roof and door, the roof, door and trees grown as far as the
     * camera is near; false, drawing nothing, when they would not all fit.
     */
    private fun addYard(yard: Yard, eyeX: Double, eyeZ: Double, foldOf: (Int) -> Double): Boolean {
        if (count + yard.trees.size + 3 * yard.houses.size + 1 > CAPACITY) return false
        val lawn = yard.lawn
        if (lawn != null) addBox(lawn, 1.0, lawn.colour)
        for (box in yard.trees) addBox(box, grown(box.x + box.width / 2, box.z + box.depth / 2, eyeX, eyeZ), box.colour)
        for (house in yard.houses) {
            val shape = house.shape
            val grown = grown(shape.x, shape.z, eyeX, eyeZ)
            val fold = foldOf(house.foldable)
            val cosine = cos(shape.turn)
            val sine = sin(shape.turn)
            // The walls as the cutaway draws them, and the roof on top of them, as much smaller as they are folded.
            val walls = if (house.wall > Cutaway.PLINTH) house.wall - (house.wall - Cutaway.PLINTH) * fold else house.wall
            put(shape.x, walls / 2, shape.z, shape.width, walls, shape.depth, cosine, sine, 0.0, house.colour)
            val size = grown * (1 - fold)
            if (size <= 0.0) continue
            // A box turned 45 degrees about the ridge, its middle at the eaves, so its lower half stays inside the walls.
            val span = (shape.width + 2 * Suburbs.EAVE) / SQRT_TWO * size
            put(shape.x, walls, shape.z, span, span, shape.depth - 0.02, cosine, sine, ROOF_PITCH, house.roof)
            // The door, on the front, as tall as the walls let it be.
            val door = kotlin.math.min(DOOR_HEIGHT, house.wall * DOOR_SHARE) * grown * walls / house.wall
            val out = house.front * (shape.depth / 2 + DOOR_DEPTH / 2)
            val doorX = shape.x + house.doorAt * cosine + out * sine
            val doorZ = shape.z - house.doorAt * sine + out * cosine
            put(doorX, door / 2, doorZ, DOOR_WIDTH, door, DOOR_DEPTH, cosine, sine, 0.0, DOOR)
        }
        return true
    }

    /** How far grown a roof, a door or a tree at [x], [z] is: whole near the camera, nothing at [REACH]. */
    private fun grown(x: Double, z: Double, eyeX: Double, eyeZ: Double): Double =
        ((REACH - hypot(x - eyeX, z - eyeZ)) / GROW).coerceIn(0.0, 1.0)

    /** [box], [share] of its size, grown from the ground up about its middle. */
    private fun addBox(box: Block, share: Double, colour: Int) {
        if (share <= 0.0) return
        val height = box.height * share
        put(
            box.x + box.width / 2, box.y * share + height / 2, box.z + box.depth / 2,
            box.width * share, height, box.depth * share, cos(box.turn), sin(box.turn), 0.0, colour,
        )
    }

    /**
     * One box with its middle at [x], [y], [z], [width] by [height] by [depth], turned on the ground by the turn whose
     * [cosine] and [sine] are given, and first tipped by [tilt] about its depth's way: the numbers of turn times tip
     * times size, column by column.
     */
    private fun put(
        x: Double,
        y: Double,
        z: Double,
        width: Double,
        height: Double,
        depth: Double,
        cosine: Double,
        sine: Double,
        tilt: Double,
        colour: Int,
    ) {
        if (count >= CAPACITY) return
        val tipCosine = cos(tilt)
        val tipSine = sin(tilt)
        val at = count * 16
        numbers[at] = (cosine * tipCosine * width).toFloat()
        numbers[at + 1] = (tipSine * width).toFloat()
        numbers[at + 2] = (-sine * tipCosine * width).toFloat()
        numbers[at + 3] = 0f
        numbers[at + 4] = (-cosine * tipSine * height).toFloat()
        numbers[at + 5] = (tipCosine * height).toFloat()
        numbers[at + 6] = (sine * tipSine * height).toFloat()
        numbers[at + 7] = 0f
        numbers[at + 8] = (sine * depth).toFloat()
        numbers[at + 9] = 0f
        numbers[at + 10] = (cosine * depth).toFloat()
        numbers[at + 11] = 0f
        numbers[at + 12] = x.toFloat()
        numbers[at + 13] = y.toFloat()
        numbers[at + 14] = z.toFloat()
        numbers[at + 15] = 1f
        colours[count] = colour
        count += 1
    }

    companion object {
        /** The most boxes drawn near the camera at once. */
        const val CAPACITY = 2_400

        /** Roofs, doors and trees are drawn this far from the camera, grown on over the last [GROW] of it. */
        const val REACH = 75.0
        const val GROW = 12.0

        /** The lawns reach this far, past the roofs, so the green does not come in where the eye is on it. */
        const val LAWN_REACH = 100.0

        /** How high round a block the ball tried against the camera's view reaches, and a little more all round. */
        private const val BALL_HEIGHT = 1.4
        private const val VIEW_SLACK = 1.0

        private const val SQRT_TWO = 1.4142135623730951
        private const val ROOF_PITCH = PI / 4
        private const val DOOR_WIDTH = 0.34
        private const val DOOR_HEIGHT = 0.75
        private const val DOOR_SHARE = 0.5
        private const val DOOR_DEPTH = 0.04
        private const val DOOR = 0x6B4630
    }
}
