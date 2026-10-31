package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// The town's small motions, each worked out from the town's own time in milliseconds and nothing else, so a test can
// ask where anything is at any moment without drawing.

/**
 * The SkyTrain's round, every [PERIOD]: it stands at the station for [DWELL], pulls out east faster and faster until
 * it is far down the guideway, is away for a while, then comes in from the west, slowing all the way into the station.
 */
object SkyTrainRun {
    const val PERIOD = 32_000.0
    const val DWELL = 8_000.0
    const val LEAVE = 10_000.0
    const val AWAY = 4_000.0
    const val ARRIVE = PERIOD - DWELL - LEAVE - AWAY

    /** How far down the guideway the train goes before it is away, either way from the station. */
    const val REACH = 155.0

    /** How far east of its stop at the station the train is at [millis]; west is below 0. */
    fun shiftAt(millis: Double): Double {
        val t = ((millis % PERIOD) + PERIOD) % PERIOD
        return when {
            t < DWELL -> 0.0
            t < DWELL + LEAVE -> REACH * square((t - DWELL) / LEAVE)
            t < DWELL + LEAVE + AWAY -> REACH
            else -> -REACH * square(1 - (t - DWELL - LEAVE - AWAY) / ARRIVE)
        }
    }

    /** False while the train is away, beyond the guideway's ends in the haze, and should not be drawn. */
    fun isShownAt(millis: Double): Boolean {
        val t = ((millis % PERIOD) + PERIOD) % PERIOD
        return t !in (DWELL + LEAVE)..(DWELL + LEAVE + AWAY)
    }

    private fun square(value: Double) = value * value
}

/** The campfire's flicker: how bright it burns and how tall its flames stand, as a share of steady, 0.75 to 1.25. */
object Campfire {
    fun flicker(millis: Double): Double =
        1 + 0.12 * sin(millis / 97) + 0.07 * sin(millis / 41 + 1.3) + 0.04 * sin(millis / 23 + 0.4)
}

/**
 * Light glinting on the water: short pale streaks on the sea by the seawall and on the pond, each coming and going on
 * its own beat and drifting a little. They fade away in the night, when [night] is 1.0.
 */
object Shimmer {
    private class Glint(val x: Double, val z: Double, val y: Double, val width: Double, val beat: Double, val phase: Double)

    private val glints: List<Glint> = Random(11).let { random ->
        val sea = List(SEA_GLINTS) {
            Glint(
                x = SEA_WEST + random.nextDouble() * (SEA_EAST - SEA_WEST),
                z = SEA_NORTH + random.nextDouble() * (SEA_SOUTH - SEA_NORTH),
                y = SEA_TOP,
                width = 0.6 + random.nextDouble() * 0.8,
                beat = 1_800 + random.nextDouble() * 1_400,
                phase = random.nextDouble() * 2 * PI,
            )
        }
        val pond = List(POND_GLINTS) {
            Glint(
                x = 34.8 + random.nextDouble() * 2.4,
                z = 25.2 + random.nextDouble() * 1.6,
                y = POND_TOP,
                width = 0.3 + random.nextDouble() * 0.3,
                beat = 1_500 + random.nextDouble() * 1_000,
                phase = random.nextDouble() * 2 * PI,
            )
        }
        sea + pond
    }

    val count: Int
        get() = glints.size

    /** Every glint at [millis]: a thin box on the water, no wider than [MAX_WIDTH], and next to nothing when out. */
    fun at(millis: Double, night: Double): List<Block> = glints.map { glint ->
        val out = max(0.0, sin(2 * PI * millis / glint.beat + glint.phase)) * (1 - 0.9 * night.coerceIn(0.0, 1.0))
        val width = max(MIN_WIDTH, glint.width * out)
        val drift = 0.25 * sin(millis / 4_000 + glint.phase)
        Block(glint.x + drift - width / 2, glint.y, glint.z - 0.04, width, 0.012, 0.08, GLINT)
    }

    const val MAX_WIDTH = 1.4
    private const val MIN_WIDTH = 0.001
    private const val SEA_GLINTS = 28
    private const val POND_GLINTS = 4

    /** Where the sea is in view from the town: north of the seawall's edge, and its surface. */
    const val SEA_WEST = -8.0
    const val SEA_EAST = 56.0
    const val SEA_NORTH = -22.0
    const val SEA_SOUTH = -1.2
    const val SEA_TOP = -0.39
    const val POND_TOP = 0.045
    private const val GLINT = 0xF4FBFF
}

/** Petals drifting down from the blossom trees, a few from each, all the time, round and round. */
object Petals {
    private const val PER_TREE = 3
    private const val FALL_MILLIS = 5_200.0

    /** How high a petal starts: inside the blossom, so it seems to come out of it. */
    const val START_HEIGHT = 2.0

    val count: Int
        get() = Neighbourhood.blossoms.size * PER_TREE

    /** Every petal at [millis]: a small pink square, between the blossom and the ground, near its tree. */
    fun at(millis: Double): List<Block> = Neighbourhood.blossoms.withIndex().flatMap { (tree, spot) ->
        List(PER_TREE) { index ->
            val phase = tree * 1_700.0 + index * FALL_MILLIS / PER_TREE
            val fall = (((millis + phase) % FALL_MILLIS) + FALL_MILLIS) % FALL_MILLIS / FALL_MILLIS
            val angle = tree * 2.1 + index * 2 * PI / PER_TREE
            val flutter = sin(fall * 4 * PI + angle)
            val x = spot.x + cos(angle) * 0.7 + 0.35 * flutter + 0.6 * fall
            val z = spot.z + sin(angle) * 0.7 + 0.2 * cos(fall * 3 * PI + angle)
            val y = START_HEIGHT * (1 - fall) + 0.02
            Block(x - 0.04, y, z - 0.04, 0.08, 0.02, 0.08, if (index % 2 == 0) PETAL else PETAL_LIGHT)
        }
    }

    private const val PETAL = 0xF7B6C8
    private const val PETAL_LIGHT = 0xFCE1E8
}

/** A few flat clouds high over the water, drifting east and coming round again from the west. */
object Clouds {
    private class Cloud(val x: Double, val y: Double, val z: Double, val size: Double)

    private val clouds = listOf(
        Cloud(-60.0, 30.0, -45.0, 9.0),
        Cloud(-5.0, 34.0, -70.0, 12.0),
        Cloud(40.0, 28.0, -38.0, 7.0),
        Cloud(95.0, 33.0, -62.0, 11.0),
        Cloud(150.0, 31.0, -50.0, 8.0),
    )

    /** How fast they drift, in units a millisecond. */
    const val DRIFT = 0.0006
    const val WEST = -120.0
    const val EAST = 170.0

    val count: Int
        get() = clouds.size * 3

    /** Every box of every cloud at [millis]: a wide body, a smaller top and a puff at one end. */
    fun at(millis: Double): List<Block> = clouds.flatMap { cloud ->
        val span = EAST - WEST
        val x = WEST + (((cloud.x - WEST + millis * DRIFT) % span) + span) % span
        val size = cloud.size
        listOf(
            Block(x - size / 2, cloud.y, cloud.z - size * 0.22, size, 1.4, size * 0.44, CLOUD),
            Block(x - size * 0.3, cloud.y + 1.2, cloud.z - size * 0.15, size * 0.55, 1.1, size * 0.3, CLOUD),
            Block(x + size * 0.15, cloud.y + 0.4, cloud.z - size * 0.18, size * 0.45, 1.2, size * 0.36, CLOUD),
        )
    }

    private const val CLOUD = 0xFFFFFF
}
