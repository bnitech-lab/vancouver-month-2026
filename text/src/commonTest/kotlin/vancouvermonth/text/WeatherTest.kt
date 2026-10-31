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

    @Test
    fun `the same run on the same day always has the same weather, and runs differ`() {
        for (seed in listOf(1, 1011, 9_999)) {
            for (day in days) assertEquals(weatherOn(seed, day), weatherOn(seed, day), "$seed, $day")
        }
        // Today's run is a run number like any other, so its weather is everyone's.
        val today = TodaysRun(2026, 10, 31).seed
        assertEquals(days.map { weatherOn(today, it) }, days.map { weatherOn(TodaysRun(2026, 10, 31).seed, it) })
        assertNotEquals(days.map { weatherOn(1011, it) }, days.map { weatherOn(1012, it) })
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
