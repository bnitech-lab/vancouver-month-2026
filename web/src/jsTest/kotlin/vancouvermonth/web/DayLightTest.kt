package vancouvermonth.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val evening = DayLight.at(0.25)
        assertEquals(DayLight.EVENING, evening)
        assertTrue(red(evening.sky) > blue(evening.sky), "a golden sky")
        assertEquals(1.0, DayLight.at(DayLight.HOME_AT).glow)
        assertTrue(DayLight.at(DayLight.HOME_AT).ambient < DayLight.MORNING.ambient / 2)
    }

    @Test
    fun `the light never jumps, and the glow stays between off and full`() {
        val samples = (0..300).map { DayLight.at(it / 300.0) }
        for ((a, b) in samples.zipWithNext()) {
            assertTrue(kotlin.math.abs(a.ambient - b.ambient) < 0.1)
            assertTrue(kotlin.math.abs(red(a.sky) - red(b.sky)) <= 12)
        }
        assertTrue(samples.all { it.glow in 0.0..1.0 })
    }

    @Test
    fun `two colours blend one channel at a time`() {
        assertEquals(0x808080, DayLight.mix(0x000000, 0xFFFFFF, 0.5))
        assertEquals(0x102030, DayLight.mix(0x102030, 0xF0E0D0, 0.0))
        assertEquals(0xF0E0D0, DayLight.mix(0x102030, 0xF0E0D0, 1.0))
    }

    private fun red(colour: Int) = (colour shr 16) and 0xFF

    private fun blue(colour: Int) = colour and 0xFF
}
