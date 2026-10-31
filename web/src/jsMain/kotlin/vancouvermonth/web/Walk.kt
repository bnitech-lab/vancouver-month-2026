package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The character's walk from one building to another: out of the door, along the pavement, in at the other door.
 * A walk to the building it is already at is a small hop on the spot. Progress runs from 0.0 (start) to 1.0 (there).
 */
class Walk(val from: Place, val to: Place) {
    val path: List<Spot> = pathBetween(TownMap.building(from).door, TownMap.building(to).door)

    val length: Double = path.zipWithNext { a, b -> hypot(b.x - a.x, b.z - a.z) }.sum()

    /** How long the walk takes: longer for a longer walk, but never more than [MAX_MILLIS]. */
    val millis: Double = if (length == 0.0) HOP_MILLIS else min(MAX_MILLIS, MIN_MILLIS + length * MILLIS_PER_UNIT)

    /** How far along the walk is [elapsed] milliseconds after it began: slow to start, quick, then slow to stop. */
    fun progressAfter(elapsed: Double): Double {
        val t = (elapsed / millis).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** Where the character is at [progress]. */
    fun spotAt(progress: Double): Spot {
        var left = progress.coerceIn(0.0, 1.0) * length
        for ((a, b) in path.zipWithNext()) {
            val step = hypot(b.x - a.x, b.z - a.z)
            if (left <= step) {
                val share = if (step == 0.0) 0.0 else left / step
                return Spot(a.x + (b.x - a.x) * share, a.z + (b.z - a.z) * share)
            }
            left -= step
        }
        return path.last()
    }

    /**
     * Which way the character faces at [progress], as a turn in radians: 0 faces south, toward the viewer,
     * and a quarter turn (PI / 2) faces east. Standing still, it faces the viewer.
     */
    fun headingAt(progress: Double): Double {
        if (length == 0.0) return 0.0
        var left = progress.coerceIn(0.0, 1.0) * length
        for ((a, b) in path.zipWithNext()) {
            val step = hypot(b.x - a.x, b.z - a.z)
            if (left < step) return atan2(b.x - a.x, b.z - a.z)
            left -= step
        }
        val (a, b) = path.takeLast(2)
        return atan2(b.x - a.x, b.z - a.z)
    }

    /** How high the character is off the ground at [progress]: a small bounce each step, none at either end. */
    fun liftAt(progress: Double): Double {
        val steps = if (length == 0.0) 1 else maxOf(1, (length / STEP_LENGTH).roundToInt())
        val bounce = if (length == 0.0) HOP_HEIGHT else STEP_HEIGHT
        return abs(sin(progress.coerceIn(0.0, 1.0) * PI * steps)) * bounce
    }

    companion object {
        const val MIN_MILLIS = 300.0
        const val MILLIS_PER_UNIT = 40.0
        const val MAX_MILLIS = 900.0
        const val HOP_MILLIS = 300.0
        const val STEP_LENGTH = 0.8
        const val STEP_HEIGHT = 0.08
        const val HOP_HEIGHT = 0.3

        /** Out of [start]'s door onto the pavement, along it, and in at [end]'s door. */
        private fun pathBetween(start: Spot, end: Spot): List<Spot> =
            if (start == end) {
                listOf(start)
            } else {
                listOf(start, Spot(start.x, TownMap.PAVEMENT_Z), Spot(end.x, TownMap.PAVEMENT_Z), end)
            }
    }
}
