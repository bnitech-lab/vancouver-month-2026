package vancouvermonth.web

import kotlin.math.max
import kotlin.math.roundToInt
import vancouvermonth.text.Weather

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

    /**
     * [lighting] under [weather]'s sky. A sunny day keeps it as it is. A cloudy day greys the sky and softens the sun,
     * so shadows are faint. A rainy day greys the sky more and darkens it, the sun is only a soft glow through the
     * clouds and the light comes from all round, and the windows and lamps glow a little even by day.
     */
    fun weathered(lighting: Lighting, weather: Weather): Lighting = when (weather) {
        Weather.SUNNY -> lighting
        Weather.CLOUDY -> lighting.copy(
            sky = grey(lighting.sky, 0.55, 0.97),
            ambient = lighting.ambient * 1.05,
            ambientColour = grey(lighting.ambientColour, 0.3, 1.0),
            sun = lighting.sun * 0.55,
            sunColour = grey(lighting.sunColour, 0.5, 1.0),
        )
        Weather.RAINY -> lighting.copy(
            sky = grey(lighting.sky, 0.8, 0.82),
            ambient = lighting.ambient * 1.02,
            ambientColour = grey(lighting.ambientColour, 0.45, 0.95),
            sun = lighting.sun * 0.3,
            sunColour = grey(lighting.sunColour, 0.7, 1.0),
            glow = max(lighting.glow, RAIN_GLOW),
        )
    }

    /** How brightly the windows and lamps glow by day in the rain. */
    const val RAIN_GLOW = 0.22

    /** How near the haze starts and how far it hides everything under [weather]'s sky: nearer in the rain. */
    fun haze(weather: Weather): Pair<Double, Double> = when (weather) {
        Weather.SUNNY -> Neighbourhood.HAZE_NEAR to Neighbourhood.HAZE_FAR
        Weather.CLOUDY -> 62.0 to 215.0
        Weather.RAINY -> 48.0 to 190.0
    }

    /** The sea's colour under [weather]'s sky: darker and greyer in the rain. */
    fun sea(weather: Weather): Int = when (weather) {
        Weather.SUNNY -> Neighbourhood.WATER
        Weather.CLOUDY -> grey(Neighbourhood.WATER, 0.35, 0.88)
        Weather.RAINY -> grey(Neighbourhood.WATER, 0.5, 0.68)
    }

    /** How dull the glints on the water are under [weather]'s sky, as the night dulls them: not at all when sunny. */
    fun dullness(weather: Weather): Double = when (weather) {
        Weather.SUNNY -> 0.0
        Weather.CLOUDY -> 0.5
        Weather.RAINY -> 0.85
    }

    /** How strong a colour is, from 0.0 (grey) up: the gap between its strongest and weakest channel. */
    fun saturation(colour: Int): Int {
        val channels = listOf((colour shr 16) and 0xFF, (colour shr 8) and 0xFF, colour and 0xFF)
        return channels.max() - channels.min()
    }

    /** How light a colour is, from 0 to 255, as the eye sees it. */
    fun lightness(colour: Int): Double =
        0.299 * ((colour shr 16) and 0xFF) + 0.587 * ((colour shr 8) and 0xFF) + 0.114 * (colour and 0xFF)

    /** [colour] [share] of the way to the grey of its own lightness, then made [bright] times as light. */
    private fun grey(colour: Int, share: Double, bright: Double): Int {
        val level = lightness(colour)
        fun channel(shift: Int): Int {
            val value = (colour shr shift) and 0xFF
            return ((value + (level - value) * share) * bright).roundToInt().coerceIn(0, 255) shl shift
        }
        return channel(16) or channel(8) or channel(0)
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
