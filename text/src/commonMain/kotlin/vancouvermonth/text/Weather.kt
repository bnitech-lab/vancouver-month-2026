package vancouvermonth.text

import vancouvermonth.core.Month

/**
 * The sky over the town on a day of September. It is for the eyes and the ears only: how the town looks and sounds,
 * and what people say about it. No rule reads it, so money, energy and every chance are the same in any weather.
 */
enum class Weather {
    SUNNY,
    CLOUDY,
    RAINY,
}

/**
 * The weather on [day] of the run with [seed]. The same run always has the same sky, so everyone who plays today's
 * run gets the same weather. As in a Vancouver September, about three days in ten are rainy and two cloudy, and a day
 * often keeps the sky of the day before. The first day in town, September 2nd, is never rainy.
 */
fun weatherOn(seed: Int, day: Int): Weather {
    if (day > Month.FIRST_DAY && roll(seed, day, KEEP) < KEEP_CHANCE) return weatherOn(seed, day - 1)
    val sky = roll(seed, day, SKY)
    return when {
        sky < RAIN_CHANCE -> if (day <= Month.FIRST_DAY) Weather.SUNNY else Weather.RAINY
        sky < RAIN_CHANCE + CLOUD_CHANCE -> Weather.CLOUDY
        else -> Weather.SUNNY
    }
}

/** The weather in a word, for the top of the screen: "맑음", "흐림" or "비". */
fun weatherWord(weather: Weather): String = when (weather) {
    Weather.SUNNY -> "맑음"
    Weather.CLOUDY -> "흐림"
    Weather.RAINY -> "비"
}

/**
 * A number from 0.0 up to 1.0 that only [seed], [day] and [salt] decide, the same on the JVM and in a browser.
 * Kotlin/JS wraps Int sums and products at 32 bits as the JVM does, as the run number for a date relies on too.
 */
private fun roll(seed: Int, day: Int, salt: Int): Double {
    var x = seed * 0x01000193 + day * 0x2C1B3C6D + salt
    x = ((x ushr 16) xor x) * MIX
    x = ((x ushr 16) xor x) * MIX
    x = (x ushr 16) xor x
    return (x ushr 8) / 16_777_216.0
}

private const val RAIN_CHANCE = 0.3
private const val CLOUD_CHANCE = 0.2

/** How often a day keeps the sky of the day before. Kept or drawn afresh, a day is rainy three times in ten. */
private const val KEEP_CHANCE = 0.4

/** Two separate rolls for each day: whether it keeps yesterday's sky, and which sky it gets if not. */
private const val KEEP = 0x5EED
private const val SKY = 0x5C1E5

/** A well-known integer hash constant. */
private const val MIX = 0x45d9f3b
