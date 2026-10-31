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
}
