package vancouvermonth.web

import kotlin.math.roundToInt

/**
 * How the town is lit: the colour of the sky (and of the haze far away), the soft light from all round and its
 * colour, the sun's strength and colour, and how brightly the windows, lamps and campfire glow, from 0.0 (off) to 1.0.
 */
data class Lighting(
    val sky: Int,
    val ambient: Double,
    val ambientColour: Int,
    val sun: Double,
    val sunColour: Int,
    val glow: Double,
)

/**
 * The light as one day ends and the next begins, over [MILLIS]: a golden evening, a night with the windows, the
 * lamps and the campfire lit, then dawn and a soft morning again. Progress runs from 0.0 to 1.0, and the character
 * goes home at [HOME_AT], in the dark.
 */
object DayLight {
    const val MILLIS = 1_500.0
    const val HOME_AT = 0.55

    val MORNING = Lighting(
        sky = 0xBFE3F5, ambient = 1.5, ambientColour = 0xFFF8EC, sun = 2.3, sunColour = 0xFFF1D6, glow = 0.0,
    )
    val EVENING = Lighting(
        sky = 0xF4A86E, ambient = 1.3, ambientColour = 0xFFB27A, sun = 2.2, sunColour = 0xFF8A3D, glow = 0.45,
    )
    val NIGHT = Lighting(
        sky = 0x22305A, ambient = 0.55, ambientColour = 0x7083C4, sun = 0.3, sunColour = 0x9DB0E0, glow = 1.0,
    )
    val DAWN = Lighting(
        sky = 0xF3CDB8, ambient = 1.1, ambientColour = 0xFFD8C2, sun = 1.6, sunColour = 0xFFC99A, glow = 0.3,
    )

    /** The light at each step of the change, by progress. Between two steps it blends from one to the next. */
    private val steps = listOf(
        0.0 to MORNING,
        0.25 to EVENING,
        0.42 to NIGHT,
        0.7 to NIGHT,
        0.85 to DAWN,
        1.0 to MORNING,
    )

    /**
     * How much of the passersby is out at [progress] of the change of day, from 1.0 (all out) to 0.0: they go home
     * through the evening, stay in through the night, and are out again by the end of dawn.
     */
    fun outdoors(progress: Double): Double = when {
        progress < GO_HOME_FROM -> 1.0
        progress < GO_HOME_BY -> 1 - (progress - GO_HOME_FROM) / (GO_HOME_BY - GO_HOME_FROM)
        progress < COME_OUT_FROM -> 0.0
        progress < COME_OUT_BY -> (progress - COME_OUT_FROM) / (COME_OUT_BY - COME_OUT_FROM)
        else -> 1.0
    }

    private const val GO_HOME_FROM = 0.15
    private const val GO_HOME_BY = 0.35
    private const val COME_OUT_FROM = 0.75
    private const val COME_OUT_BY = 0.95

    fun at(progress: Double): Lighting {
        val p = progress.coerceIn(0.0, 1.0)
        val (endAt, end) = steps.first { it.first >= p }
        val (startAt, start) = steps.last { it.first <= p }
        if (endAt == startAt) return end
        val share = (p - startAt) / (endAt - startAt)
        return Lighting(
            sky = mix(start.sky, end.sky, share),
            ambient = start.ambient + (end.ambient - start.ambient) * share,
            ambientColour = mix(start.ambientColour, end.ambientColour, share),
            sun = start.sun + (end.sun - start.sun) * share,
            sunColour = mix(start.sunColour, end.sunColour, share),
            glow = start.glow + (end.glow - start.glow) * share,
        )
    }

    /** The colour [share] of the way from [from] to [to], each of red, green and blue blended on its own. */
    fun mix(from: Int, to: Int, share: Double): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * share).roundToInt() shl shift
        }
        return channel(16) or channel(8) or channel(0)
    }
}
