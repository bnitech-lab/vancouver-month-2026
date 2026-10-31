package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PassersbyTest {
    @Test
    fun `the passersby are four people of their own, none of them one of the five at the places`() {
        assertEquals(4, Passer.entries.size)
        val names = Passer.entries.map { it.title }
        assertEquals(names.size, names.toSet().size)
        for (name in names) assertTrue(name !in Townsperson.entries.map { it.title }, name)
        for (passer in Passer.entries) assertTrue(passer.role.isNotBlank(), "$passer")
    }

    @Test
    fun `a passerby says something else each time they are stopped, and comes round again`() {
        for (passer in Passer.entries) {
            val first = passerLine(passer, day = 2, count = 0)
            assertNotEquals(first, passerLine(passer, day = 2, count = 1), "$passer")
            val lines = (0 until 30).map { passerLine(passer, day = 2, count = it) }.toSet()
            assertTrue(lines.size >= 2, "$passer")
            assertEquals(first, passerLine(passer, day = 2, count = lines.size), "$passer")
        }
    }

    @Test
    fun `every line is short, and the next day starts on another one`() {
        for (passer in Passer.entries) {
            for (count in 0 until 3) {
                val line = passerLine(passer, day = 10, count = count)
                assertTrue(line.length in 5..40, "$passer: $line")
                assertTrue(Char(0x2014) !in line, "$passer: $line")
            }
            assertNotEquals(passerLine(passer, day = 10, count = 0), passerLine(passer, day = 11, count = 0))
        }
    }

    @Test
    fun `on a rainy day a passerby speaks of the rain first, then as on any day, and never so on a dry day`() {
        for (passer in Passer.entries) {
            val rain = passerLine(passer, day = 10, count = 0, weather = Weather.RAINY)
            assertTrue("비" in rain || "우산" in rain, "$passer: $rain")
            assertTrue(rain.length in 5..40 && Char(0x2014) !in rain, "$passer: $rain")
            for (count in 1 until 4) {
                assertEquals(
                    passerLine(passer, day = 10, count = count - 1),
                    passerLine(passer, day = 10, count = count, weather = Weather.RAINY),
                    "$passer",
                )
            }
            for (weather in listOf(Weather.SUNNY, Weather.CLOUDY)) {
                for (day in 2..30) {
                    for (count in 0 until 4) assertNotEquals(rain, passerLine(passer, day, count, weather))
                }
            }
        }
    }
}
