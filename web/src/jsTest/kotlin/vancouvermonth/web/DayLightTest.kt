package vancouvermonth.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import vancouvermonth.text.Weather

class DayLightTest {
    @Test
    fun `a change of day starts and ends in the morning`() {
        assertEquals(DayLight.MORNING, DayLight.at(0.0))
        assertEquals(DayLight.MORNING, DayLight.at(1.0))
        assertEquals(DayLight.MORNING, DayLight.at(-1.0))
        assertEquals(DayLight.MORNING, DayLight.at(2.0))
    }

    @Test
    fun `evening glows gold before the night, and the night lights every window`() {
        val evening = DayLight.during(0.8)
        assertEquals(DayLight.EVENING, evening)
        assertTrue(red(evening.sky) > blue(evening.sky), "a golden sky")
        assertEquals(DayLight.NIGHT, DayLight.during(1.0))
        assertEquals(1.0, DayLight.at(DayLight.DARK_AT).glow)
        assertTrue(DayLight.at(DayLight.DARK_AT).ambient < DayLight.MORNING.ambient / 2)
    }

    @Test
    fun `through the day the light darkens little by little, and the lamps come on toward evening`() {
        assertEquals(DayLight.MORNING, DayLight.during(0.0))
        assertEquals(DayLight.MORNING, DayLight.during(-1.0))
        assertEquals(DayLight.NIGHT, DayLight.during(2.0))
        val samples = (0..100).map { DayLight.during(it / 100.0) }
        // The windows and lamps only ever brighten, off by day and full at night.
        assertTrue(samples.zipWithNext().all { (a, b) -> b.glow >= a.glow })
        assertEquals(0.0, samples[40].glow)
        // From the bright early afternoon on, the light from the sun and all round only ever fades.
        val afternoon = samples.drop(40)
        assertTrue(afternoon.zipWithNext().all { (a, b) -> b.sun + b.ambient <= a.sun + a.ambient + 1e-9 })
        assertTrue(samples.last().sun + samples.last().ambient < (DayLight.MORNING.sun + DayLight.MORNING.ambient) / 3)
        // Each stamina spent (a fifth of the day) moves the light visibly, the last ones most.
        val steps = (0..5).map { DayLight.during(it / 5.0) }
        for ((a, b) in steps.zipWithNext()) assertTrue(a != b, "$a and $b")
        assertTrue(DayLight.lightness(steps[5].sky) < DayLight.lightness(steps[4].sky) / 2)
    }

    @Test
    fun `a night's sleep carries the light on from the time it is to the night, then on to the morning`() {
        for (from in listOf(0.0, 0.2, 0.6, 0.8, 1.0)) {
            assertEquals(DayLight.during(from), DayLight.at(0.0, from), "from $from")
            assertEquals(DayLight.NIGHT, DayLight.at(DayLight.NIGHT_FALLS, from), "from $from")
            assertEquals(DayLight.NIGHT, DayLight.at(DayLight.DARK_AT, from), "from $from")
            assertEquals(DayLight.MORNING, DayLight.at(1.0, from), "from $from")
        }
        // Going to bed at night, the night simply goes on.
        for (progress in (0..42).map { it / 100.0 }) assertEquals(DayLight.NIGHT, DayLight.at(progress, 1.0))
    }

    @Test
    fun `the light never jumps, and the glow stays between off and full`() {
        for (from in listOf(0.0, 0.6, 1.0)) {
            val samples = (0..300).map { DayLight.at(it / 300.0, from) }
            for ((a, b) in samples.zipWithNext()) {
                assertTrue(kotlin.math.abs(a.ambient - b.ambient) < 0.1)
                assertTrue(kotlin.math.abs(a.sun - b.sun) < 0.2)
                assertTrue(kotlin.math.abs(red(a.sky) - red(b.sky)) <= 12)
            }
            assertTrue(samples.all { it.glow in 0.0..1.0 })
        }
    }

    @Test
    fun `the passersby go home through the evening, stay in all night, and are out again by morning`() {
        assertEquals(1.0, DayLight.outdoorsAt(0.0))
        assertEquals(1.0, DayLight.outdoorsAt(0.6))
        assertEquals(0.0, DayLight.outdoorsAt(1.0))
        assertTrue(DayLight.outdoorsAt(0.8) in 0.1..0.9, "all gone at once")
        assertEquals(1.0, DayLight.outdoors(0.0))
        assertEquals(1.0, DayLight.outdoors(1.0))
        for (progress in listOf(0.42, 0.5, DayLight.DARK_AT, 0.6, 0.7)) assertEquals(0.0, DayLight.outdoors(progress))
        for (from in listOf(0.0, 0.85, 1.0)) {
            assertEquals(DayLight.outdoorsAt(from), DayLight.outdoors(0.0, from), "from $from")
            val samples = (0..300).map { DayLight.outdoors(it / 300.0, from) }
            assertTrue(samples.all { it in 0.0..1.0 })
            val evening = samples.take(150)
            val dawn = samples.drop(150)
            assertTrue(evening.zipWithNext().all { (a, b) -> b <= a }, "someone came out in the evening")
            assertTrue(dawn.zipWithNext().all { (a, b) -> b >= a }, "someone went in at dawn")
        }
        assertTrue(DayLight.outdoors(0.34) in 0.1..0.9, "all gone at once")
    }

    @Test
    fun `two colours blend one channel at a time`() {
        assertEquals(0x808080, DayLight.mix(0x000000, 0xFFFFFF, 0.5))
        assertEquals(0x102030, DayLight.mix(0x102030, 0xF0E0D0, 0.0))
        assertEquals(0xF0E0D0, DayLight.mix(0x102030, 0xF0E0D0, 1.0))
    }

    private fun red(colour: Int) = (colour shr 16) and 0xFF

    private fun blue(colour: Int) = colour and 0xFF

    @Test
    fun `a sunny day keeps the light, a cloudy day greys and softens it, and a rainy day more so`() {
        for (progress in listOf(0.0, 0.25, 0.5, 0.85)) {
            val light = DayLight.at(progress)
            assertEquals(light, DayLight.weathered(light, Weather.SUNNY))
            val cloudy = DayLight.weathered(light, Weather.CLOUDY)
            val rainy = DayLight.weathered(light, Weather.RAINY)
            // Greyer skies, and a weaker sun, so softer shadows.
            assertTrue(DayLight.saturation(rainy.sky) < DayLight.saturation(cloudy.sky), "at $progress")
            assertTrue(DayLight.saturation(cloudy.sky) < DayLight.saturation(light.sky), "at $progress")
            assertTrue(rainy.sun < cloudy.sun && cloudy.sun < light.sun, "at $progress")
            assertTrue(DayLight.lightness(rainy.sky) <= DayLight.lightness(cloudy.sky), "at $progress")
            // The light from all round stays, so the town is soft, not dark.
            assertTrue(rainy.ambient >= light.ambient && cloudy.ambient >= light.ambient, "at $progress")
            assertTrue(rainy.glow >= DayLight.RAIN_GLOW && rainy.glow >= light.glow, "at $progress")
            assertEquals(light.glow, cloudy.glow, "at $progress")
        }
    }

    @Test
    fun `rain brings the haze nearer and darkens the sea and its glints`() {
        val (sunnyNear, sunnyFar) = DayLight.haze(Weather.SUNNY)
        val (rainyNear, rainyFar) = DayLight.haze(Weather.RAINY)
        assertEquals(Neighbourhood.HAZE_NEAR to Neighbourhood.HAZE_FAR, sunnyNear to sunnyFar)
        assertTrue(rainyNear < sunnyNear && rainyFar < DayLight.haze(Weather.CLOUDY).second)
        assertEquals(Neighbourhood.WATER, DayLight.sea(Weather.SUNNY))
        val sea = Weather.entries.map { DayLight.lightness(DayLight.sea(it)) }
        assertTrue(sea[2] < sea[1] && sea[1] < sea[0], "$sea")
        assertEquals(0.0, DayLight.dullness(Weather.SUNNY))
        assertTrue(DayLight.dullness(Weather.RAINY) > DayLight.dullness(Weather.CLOUDY))
    }

}
