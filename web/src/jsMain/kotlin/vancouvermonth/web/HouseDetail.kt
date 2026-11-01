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
 * each block's lawn and yard trees. Far off, each row of houses is one low box (or each house, out where the yards are
 * wide), which the town always draws; within [REACH] of the camera and in its view, a block's houses stand one by one
 * instead ([shown]), each growing out of its share of its row's low box, the yards opening and its roof and door
 * growing on, over the last [GROW] as the camera comes nearer; the lawns reach on to [LAWN_REACH]. A house folds out
 * of the camera's way as the city's buildings do, its roof sinking with its walls. [fill] writes them into two draws:
 * the blocks near the character, which cast the sun's shadows ([casters], written into [casterNumbers]), and the rest in
 * view, which cast none ([others], into [numbers]), the nearest blocks first and never more than each holds, making
 * nothing new.
 */
internal class HouseDetail(private val yards: List<Yard>, casterNumbers: Float32Array, numbers: Float32Array) {
    /**
     * The blocks within [SHADOW_NEAR] of the character, all of them drawn one by one, in view or not, so the sun's
     * shadows round the character are the houses' own; only they go through the shadow pass.
     */
    val casters = NearDraw(casterNumbers, CASTER_CAPACITY)

    /** The other blocks in view and within reach, and the lawns past it, which cast no shadow. */
    val others = NearDraw(numbers, CAPACITY)

    /** The draw being written. */
    private var into = others

    /** How many boxes the last [fill] wrote, in both draws. */
    val count: Int
        get() = casters.count + others.count

    private val middleX = DoubleArray(yards.size) { yards[it].middle.x }
    private val middleZ = DoubleArray(yards.size) { yards[it].middle.z }
    private val reach = DoubleArray(yards.size) { yards[it].reach }

    /** How far each block is from the camera on the ground, its nearest edge, or -1.0 when it is out of the camera's view. */
    private val away = DoubleArray(yards.size)

    /** True for each block within [SHADOW_NEAR] of the character. */
    private val byCharacter = BooleanArray(yards.size)

    /** True for each block whose houses the last [fill] drew one by one, in place of its rows' low boxes. */
    val shown = BooleanArray(yards.size)

    /** The blocks the last [fill] began or stopped drawing one by one, the first [changedCount] of them. */
    val changed = IntArray(yards.size)
    var changedCount = 0
        private set

    /**
     * Writes the houses, lawns and trees for a camera at [eyeX], [eyeY], [eyeZ] looking at the character's [focusX],
     * [focusY], [focusZ], seeing [tanAcross] to each side and [tanUp] up and down for each unit ahead; [foldOf] says how
     * far each of [Neighbourhood.foldables] is folded as drawn.
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
            // A block drawn one by one stays so until it is [HOLD] farther out, so one at the edge does not flicker.
            val hold = if (shown[index]) HOLD else 0.0
            val onGround = hypot(middleX[index] - eyeX, middleZ[index] - eyeZ) - reach[index] - hold
            away[index] = -1.0
            // Round the character, in view or not, as the sun's shadows fall there.
            byCharacter[index] = hypot(middleX[index] - focusX, middleZ[index] - focusZ) - reach[index] <= SHADOW_NEAR
            if (byCharacter[index]) {
                away[index] = kotlin.math.max(0.0, onGround)
                continue
            }
            if (onGround > LAWN_REACH) continue
            // A ball round the block and its houses, tried against the four sides of the camera's view.
            val ball = hypot(reach[index], BALL_HEIGHT) + VIEW_SLACK + hold
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
        casters.count = 0
        others.count = 0
        changedCount = 0
        // The blocks round the character into the draw that casts shadows, or the other should it be full; then the
        // nearest blocks first, so when there are too many boxes it is the farthest that go without.
        for (index in yards.indices) {
            if (!byCharacter[index]) continue
            into = casters
            var drawn = addYard(yards[index], eyeX, eyeZ, foldOf)
            if (!drawn) {
                into = others
                drawn = addYard(yards[index], eyeX, eyeZ, foldOf)
            }
            show(index, drawn)
        }
        into = others
        for (index in yards.indices) {
            if (!byCharacter[index] && away[index] >= 0.0 && away[index] <= REACH / 2) {
                show(index, addYard(yards[index], eyeX, eyeZ, foldOf))
            }
        }
        for (index in yards.indices) {
            val far = away[index]
            if (byCharacter[index] || (far >= 0.0 && far <= REACH / 2)) continue
            show(index, far > REACH / 2 && far <= REACH && addYard(yards[index], eyeX, eyeZ, foldOf))
        }
        for (index in yards.indices) {
            val lawn = yards[index].lawn ?: continue
            if (away[index] > REACH && others.count < others.capacity) addBox(lawn, 1.0, lawn.colour)
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
        if (into.count + yard.trees.size + 3 * yard.houses.size + 1 > into.capacity) return false
        val lawn = yard.lawn
        if (lawn != null) addBox(lawn, 1.0, lawn.colour)
        for (box in yard.trees) addBox(box, grown(box.x + box.width / 2, box.z + box.depth / 2, eyeX, eyeZ), box.colour)
        for (house in yard.houses) {
            val shape = house.shape
            val grown = grown(shape.x, shape.z, eyeX, eyeZ)
            val fold = foldOf(house.foldable)
            val cosine = cos(shape.turn)
            val sine = sin(shape.turn)
            // At the edge of reach the house is its share of its row's low box, which it grows out of into itself as the
            // camera comes near, the yards opening on either side.
            val width = house.slotWidth + (shape.width - house.slotWidth) * grown
            val shift = house.slotShift * (1 - grown)
            val x = shape.x + shift * cosine
            val z = shape.z - shift * sine
            val tall = house.farWall + (house.wall - house.farWall) * grown
            // The walls as the cutaway draws them, and the roof on top of them, as much smaller as they are folded.
            val walls = if (tall > Cutaway.PLINTH) tall - (tall - Cutaway.PLINTH) * fold else tall
            put(x, walls / 2, z, width, walls, shape.depth, cosine, sine, 0.0, house.colour)
            val size = grown * (1 - fold)
            if (size <= 0.0) continue
            // A box turned 45 degrees about the ridge, its middle at the eaves, so its lower half stays inside the walls.
            val span = (width + 2 * Suburbs.EAVE) / SQRT_TWO * size
            put(x, walls, z, span, span, shape.depth - 0.02, cosine, sine, ROOF_PITCH, house.roof)
            // The door, on the front, as tall as the walls let it be.
            val door = kotlin.math.min(DOOR_HEIGHT, house.wall * DOOR_SHARE) * grown * walls / house.wall
            val out = house.front * (shape.depth / 2 + DOOR_DEPTH / 2)
            val doorX = x + house.doorAt * cosine + out * sine
            val doorZ = z - house.doorAt * sine + out * cosine
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
        val draw = into
        if (draw.count >= draw.capacity) return
        val numbers = draw.numbers
        val tipCosine = cos(tilt)
        val tipSine = sin(tilt)
        val at = draw.count * 16
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
        draw.colours[draw.count] = colour
        draw.count += 1
    }

    companion object {
        /** The most boxes drawn near the camera at once, besides those round the character, and the most of those. */
        const val CAPACITY = 2_400
        const val CASTER_CAPACITY = 800

        /**
         * The blocks within this of the character on the ground cast the sun's shadows: the sun's shadow box reaches 15
         * squares round it (the town's `SHADOW_REACH`), and this a little more.
         */
        const val SHADOW_NEAR = 20.0

        /** Roofs, doors and trees are drawn this far from the camera, grown on over the last [GROW] of it. */
        const val REACH = 75.0
        const val GROW = 12.0

        /** A block drawn one by one is held so until it is this much farther off, or this far out of the view. */
        const val HOLD = 2.0

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

/** One draw of the near houses: [capacity] boxes' numbers, sixteen each, and their colours, the first [count] in use. */
internal class NearDraw(val numbers: Float32Array, val capacity: Int) {
    /** Each box's colour, as 0xRRGGBB. */
    val colours = IntArray(capacity)
    var count = 0
}
