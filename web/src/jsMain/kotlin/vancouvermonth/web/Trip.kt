package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The character's walk along a [route] at a steady [speed] in units a second: a walk, or a dash after a double tap.
 * Everything is asked by the time since the trip began, in milliseconds.
 */
class Trip(val route: List<Spot>, val speed: Double) {
    init {
        require(route.isNotEmpty()) { "A trip needs somewhere to start" }
    }

    val length: Double = route.zipWithNext { a, b -> hypot(b.x - a.x, b.z - a.z) }.sum()

    val millis: Double = length / speed * 1_000

    fun isOverAfter(elapsed: Double): Boolean = elapsed >= millis

    /** How far along the route the character is after [elapsed] milliseconds. */
    private fun travelled(elapsed: Double): Double = (elapsed.coerceAtLeast(0.0) / 1_000 * speed).coerceAtMost(length)

    fun spotAfter(elapsed: Double): Spot {
        var left = travelled(elapsed)
        for ((a, b) in route.zipWithNext()) {
            val step = hypot(b.x - a.x, b.z - a.z)
            if (left <= step && step > 0.0) {
                val share = left / step
                return Spot(a.x + (b.x - a.x) * share, a.z + (b.z - a.z) * share)
            }
            left -= step
        }
        return route.last()
    }

    /**
     * Which way the character faces after [elapsed] milliseconds, as a turn in radians: 0 faces south, a quarter
     * turn (PI / 2) faces east. At the end it keeps the way it last walked.
     */
    fun headingAfter(elapsed: Double): Double {
        var left = travelled(elapsed)
        var heading = 0.0
        for ((a, b) in route.zipWithNext()) {
            val step = hypot(b.x - a.x, b.z - a.z)
            if (step == 0.0) continue
            heading = atan2(b.x - a.x, b.z - a.z)
            if (left < step) return heading
            left -= step
        }
        return heading
    }

    /** How high the character bobs off the ground: a small lift each step, none when standing. */
    fun liftAfter(elapsed: Double): Double = abs(sin(travelled(elapsed) / STEP_LENGTH * PI)) * STEP_HEIGHT

    /** How far the legs swing from the hip, in radians, one way and then the other with each step. */
    fun swingAfter(elapsed: Double): Double =
        if (isOverAfter(elapsed)) 0.0 else sin(travelled(elapsed) / STEP_LENGTH * PI) * SWING

    companion object {
        const val WALK_SPEED = 3.6
        const val DASH_SPEED = 8.0
        const val STEP_LENGTH = 0.55
        const val STEP_HEIGHT = 0.07
        const val SWING = 0.6
    }
}

/**
 * Tells a double tap from two single taps: the second tap comes within [DOUBLE_TAP_MILLIS] of the first and
 * within [DOUBLE_TAP_PIXELS] of where it was.
 */
class TapTimer {
    private var lastMillis = Double.NEGATIVE_INFINITY
    private var lastX = 0.0
    private var lastY = 0.0

    /** Notes a tap at [millis] and at [x], [y] in pixels, and says whether it is the second tap of a double tap. */
    fun isDouble(millis: Double, x: Double, y: Double): Boolean {
        val double = millis - lastMillis <= DOUBLE_TAP_MILLIS && hypot(x - lastX, y - lastY) <= DOUBLE_TAP_PIXELS
        // After a double tap the next tap starts afresh, so three quick taps are a double tap and a single one.
        lastMillis = if (double) Double.NEGATIVE_INFINITY else millis
        lastX = x
        lastY = y
        return double
    }

    companion object {
        const val DOUBLE_TAP_MILLIS = 300.0
        const val DOUBLE_TAP_PIXELS = 40.0
    }
}
