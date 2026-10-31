package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import vancouvermonth.core.Month
import vancouvermonth.core.TodaysRun

class WeatherTest {
    private val seeds = 1..2_000
    private val days = Month.FIRST_DAY..Month.LAST_DAY

    /** A run's September 2nd to 30th, one letter a day: S sunny, C cloudy, R rainy. */
    private fun september(seed: Int): String = days.joinToString("") { weatherOn(seed, it).name.take(1) }

    @Test
    fun `each run's September has the skies written down here, on the JVM and in a browser alike`() {
        // Written down once from this rule. The test runs on the JVM and in a browser, so the two must agree, and a
        // change to the rule's numbers, which would move everyone's weather, has to change these lines on purpose.
        val skies = mapOf(
            1011 to "SSSRRRSSSSRRSSSCSSSSSRCSSSRRS",
            1012 to "SRRRRSRSCRSSSRRSRRRRCCRSSSRSS",
            4242 to "SCSSCCSSRRCSSSSSSRCSSSSSCCCCC",
            -1 to "SSCSSSCSSSSSCRRRRRRCCSSCCSSRR",
            Int.MIN_VALUE to "SCCCRSSSSSRRRRRRCRSSRSCCSCSCS",
            Int.MAX_VALUE to "SSSCSSSSSSSSRSRRSSSRRCRRSRSRR",
        )
        for ((seed, sky) in skies) assertEquals(sky, september(seed), "the weather changed: run $seed")
        assertNotEquals(september(1011), september(1012))
        // Every day of runs 1 to 500, counted by sky: a change too small to move the runs above still moves these.
        val counts = (1..500).flatMap { september(it).toList() }.groupingBy { it }.eachCount()
        assertEquals(mapOf('S' to 7_410, 'C' to 2_863, 'R' to 4_227), counts, "the weather changed: runs 1 to 500")
    }

    @Test
    fun `today's run has the skies of its run number, so everyone playing it on that date gets the same weather`() {
        val today = TodaysRun(2026, 10, 31)
        assertEquals(7546, today.seed)
        val sky = "CSRRSSRRRRSRRSCSCCSSSSSSSSSSS"
        assertEquals(sky, september(today.seed), "the weather changed: today's run 2026-10-31")
    }

    @Test
    fun `about three days in ten are rainy, some are cloudy, and the rest sunny`() {
        val all = seeds.flatMap { seed -> days.map { day -> weatherOn(seed, day) } }
        val share = { weather: Weather -> all.count { it == weather }.toDouble() / all.size }
        assertTrue(share(Weather.RAINY) in 0.25..0.33, "rain on ${share(Weather.RAINY)} of the days")
        assertTrue(share(Weather.CLOUDY) in 0.15..0.25, "clouds on ${share(Weather.CLOUDY)} of the days")
        assertTrue(share(Weather.SUNNY) in 0.45..0.58, "sun on ${share(Weather.SUNNY)} of the days")
        // Every month has some of each, nearly always.
        val months = seeds.map { seed -> days.map { day -> weatherOn(seed, day) }.toSet() }
        assertTrue(months.count { it.size == 3 } > seeds.count() * 9 / 10, "months with all three skies")
    }

    @Test
    fun `the first day in town is never rainy`() {
        for (seed in seeds) assertNotEquals(Weather.RAINY, weatherOn(seed, Month.FIRST_DAY), "$seed")
        assertTrue(seeds.any { weatherOn(it, Month.FIRST_DAY) == Weather.CLOUDY }, "never cloudy on the first day")
    }

    @Test
    fun `rain often lasts more than one day, but not all month`() {
        var rainy = 0
        var rainyAfterRain = 0
        for (seed in seeds) {
            for (day in Month.FIRST_DAY + 1..Month.LAST_DAY) {
                if (weatherOn(seed, day - 1) != Weather.RAINY) continue
                rainy += 1
                if (weatherOn(seed, day) == Weather.RAINY) rainyAfterRain += 1
            }
        }
        val share = rainyAfterRain.toDouble() / rainy
        assertTrue(share in 0.45..0.65, "after a rainy day, rain again on $share of the days")
        assertTrue(seeds.none { seed -> days.all { weatherOn(seed, it) == Weather.RAINY } })
    }

    @Test
    fun `each sky has its own word`() {
        assertEquals(listOf("맑음", "흐림", "비"), Weather.entries.map { weatherWord(it) })
    }
}
