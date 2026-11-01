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
 * A light written over and over rather than made anew: the town works its light out into one on every frame of a change
 * of light, so those frames make nothing new. [set] takes another light's values; [toLighting] gives them back.
 */
class TownLight {
    var sky = 0
    var ambient = 0.0
    var ambientColour = 0
    var sun = 0.0
    var sunColour = 0
    var glow = 0.0

    fun set(lighting: Lighting): TownLight {
        sky = lighting.sky
        ambient = lighting.ambient
        ambientColour = lighting.ambientColour
        sun = lighting.sun
        sunColour = lighting.sunColour
        glow = lighting.glow
        return this
    }

    fun set(light: TownLight): TownLight {
        sky = light.sky
        ambient = light.ambient
        ambientColour = light.ambientColour
        sun = light.sun
        sunColour = light.sunColour
        glow = light.glow
        return this
    }

    fun toLighting(): Lighting = Lighting(sky, ambient, ambientColour, sun, sunColour, glow)
}

/**
 * How the town is lit through a day and from one day into the next. Through the day the light follows how much of the
 * day has gone ([during], from the morning at 0.0 to the night at 1.0, as the month's stamina spent says): a soft
 * morning, a bright day, a warm late afternoon, a golden evening and a night with the windows, the lamps and the
 * campfire lit. A night's sleep ([at]) carries the light on from the time it is to the night, holds the night, then
 * brings dawn and a soft morning again, over [MILLIS]; progress runs from 0.0 to 1.0, and by [DARK_AT] it is deep night,
 * when the next day's weather comes in.
 */
object DayLight {
    /** How long a night's sleep takes on screen. */
    const val MILLIS = 1_500.0

    /** How long the time an action takes passes on screen, the light moving all the way. */
    const val PASS_MILLIS = 2_000.0

    const val DARK_AT = 0.55

    /** How far into a night's sleep the night has fallen, whatever the time it started from. */
    const val NIGHT_FALLS = 0.42

    val MORNING = Lighting(
        sky = 0xBFE3F5, ambient = 1.5, ambientColour = 0xFFF8EC, sun = 2.3, sunColour = 0xFFF1D6, glow = 0.0,
    )
    val DAY = Lighting(
        sky = 0xB0DCF4, ambient = 1.55, ambientColour = 0xFFFCF4, sun = 2.4, sunColour = 0xFFF7E6, glow = 0.0,
    )
    val LATE_AFTERNOON = Lighting(
        sky = 0xEBD8B4, ambient = 1.42, ambientColour = 0xFFE4C2, sun = 2.25, sunColour = 0xFFC88A, glow = 0.12,
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

    /**
     * The light at each step of the day, by how much of it has gone: 8:00, 14:00, 17:00, 20:00 and 23:00 on the
     * month's clock. Between two steps it blends from one to the next.
     */
    private val day = listOf(
        0.0 to MORNING,
        0.4 to DAY,
        0.6 to LATE_AFTERNOON,
        0.8 to EVENING,
        1.0 to NIGHT,
    )

    /** The rest of a night's sleep once the night has fallen: the night held, then dawn, then the morning. */
    private val sleep = listOf(
        NIGHT_FALLS to NIGHT,
        0.7 to NIGHT,
        0.85 to DAWN,
        1.0 to MORNING,
    )

    /** The light when [share] of the day has gone, from 0.0 in the morning to 1.0 at night. */
    fun during(share: Double): Lighting = during(share, TownLight()).toLighting()

    /** The same, written into [into], which is returned. */
    fun during(share: Double, into: TownLight): TownLight = blend(day, share.coerceIn(0.0, 1.0), into)

    /**
     * The light at [progress] of a night's sleep begun when [from] of the day had gone: the day's own light carried on
     * to the night by [NIGHT_FALLS], then the night, dawn and the morning.
     */
    fun at(progress: Double, from: Double = 0.0): Lighting = at(progress, from, TownLight()).toLighting()

    /** The same, written into [into], which is returned. */
    fun at(progress: Double, from: Double, into: TownLight): TownLight {
        val p = progress.coerceIn(0.0, 1.0)
        return if (p < NIGHT_FALLS) during(timeOfSleep(p, from), into) else blend(sleep, p, into)
    }

    /** How much of the day has gone at [progress] of a night's sleep begun at [from], until the night has fallen. */
    private fun timeOfSleep(progress: Double, from: Double): Double {
        val start = from.coerceIn(0.0, 1.0)
        return start + (1 - start) * (progress / NIGHT_FALLS).coerceIn(0.0, 1.0)
    }

    /**
     * How much of the passersby is out when [share] of the day has gone, from 1.0 (all out) to 0.0: they go home
     * through the evening and are all in by night.
     */
    fun outdoorsAt(share: Double): Double = when {
        share < GO_HOME_FROM -> 1.0
        share < GO_HOME_BY -> 1 - (share - GO_HOME_FROM) / (GO_HOME_BY - GO_HOME_FROM)
        else -> 0.0
    }

    /**
     * How much of the passersby is out at [progress] of a night's sleep begun at [from]: as the day has them until the
     * night falls, in through the night, and out again by the end of dawn.
     */
    fun outdoors(progress: Double, from: Double = 0.0): Double = when {
        progress < NIGHT_FALLS -> outdoorsAt(timeOfSleep(progress, from))
        progress < COME_OUT_FROM -> 0.0
        progress < COME_OUT_BY -> (progress - COME_OUT_FROM) / (COME_OUT_BY - COME_OUT_FROM)
        else -> 1.0
    }

    private const val GO_HOME_FROM = 0.7
    private const val GO_HOME_BY = 0.9
    private const val COME_OUT_FROM = 0.75
    private const val COME_OUT_BY = 0.95

    /** The light at [at] along [steps], blended between the two steps it falls between, written into [into]. */
    private fun blend(steps: List<Pair<Double, Lighting>>, at: Double, into: TownLight): TownLight {
        val p = at.coerceIn(steps.first().first, steps.last().first)
        val (endAt, end) = steps.first { it.first >= p }
        val (startAt, start) = steps.last { it.first <= p }
        if (endAt == startAt) return into.set(end)
        val share = (p - startAt) / (endAt - startAt)
        into.sky = mix(start.sky, end.sky, share)
        into.ambient = start.ambient + (end.ambient - start.ambient) * share
        into.ambientColour = mix(start.ambientColour, end.ambientColour, share)
        into.sun = start.sun + (end.sun - start.sun) * share
        into.sunColour = mix(start.sunColour, end.sunColour, share)
        into.glow = start.glow + (end.glow - start.glow) * share
        return into
    }

    /**
     * [lighting] under [weather]'s sky. A sunny day keeps it as it is. A cloudy day greys the sky and softens the sun,
     * so shadows are faint. A rainy day greys the sky more and darkens it, the sun is only a soft glow through the
     * clouds and the light comes from all round, and the windows and lamps glow a little even by day.
     */
    fun weathered(lighting: Lighting, weather: Weather): Lighting =
        if (weather == Weather.SUNNY) lighting else underSky(TownLight().set(lighting), weather).toLighting()

    /** The same, greying [light] itself, which is returned. */
    fun underSky(light: TownLight, weather: Weather): TownLight {
        when (weather) {
            Weather.SUNNY -> {}
            Weather.CLOUDY -> {
                light.sky = grey(light.sky, 0.55, 0.97)
                light.ambient *= 1.05
                light.ambientColour = grey(light.ambientColour, 0.3, 1.0)
                light.sun *= 0.55
                light.sunColour = grey(light.sunColour, 0.5, 1.0)
            }
            Weather.RAINY -> {
                light.sky = grey(light.sky, 0.8, 0.82)
                light.ambient *= 1.02
                light.ambientColour = grey(light.ambientColour, 0.45, 0.95)
                light.sun *= 0.3
                light.sunColour = grey(light.sunColour, 0.7, 1.0)
                light.glow = max(light.glow, RAIN_GLOW)
            }
        }
        return light
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
