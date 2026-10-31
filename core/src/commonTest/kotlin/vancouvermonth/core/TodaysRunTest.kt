package vancouvermonth.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every date of [year], from January 1st to December 31st. */
private fun daysOf(year: Int): List<TodaysRun> = (1..12).flatMap { month ->
    val days = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    (1..days).map { day -> TodaysRun(year, month, day) }
}

class TodaysRunTest {
    @Test
    fun `a date gives the same run number on the JVM and in a browser`() {
        // Fixed numbers, so the test fails on whichever platform works out a different one.
        assertEquals(3817, TodaysRun(2026, 10, 30).seed)
        assertEquals(7546, TodaysRun(2026, 10, 31).seed)
        assertEquals(9808, TodaysRun(2026, 11, 1).seed)
        assertEquals(5534, TodaysRun(2027, 1, 1).seed)
    }

    @Test
    fun `neighbouring days get different run numbers, far apart`() {
        val seeds = (daysOf(2026) + daysOf(2027)).map { it.seed }
        for (week in seeds.windowed(7)) {
            assertEquals(7, week.toSet().size, "a number came back within a week: $week")
        }
        // A number one step from yesterday's would let a player guess tomorrow's run.
        val close = seeds.zipWithNext().count { (today, tomorrow) -> abs(today - tomorrow) < 100 }
        assertTrue(close < seeds.size / 20, "$close of ${seeds.size} neighbouring days are within 100")
    }

    @Test
    fun `run numbers stay in the range every run uses`() {
        assertEquals(1_000..9_999, TodaysRun.SEEDS)
        val seeds = (2000..2099).flatMap { daysOf(it) }.map { it.seed }
        assertTrue(seeds.all { it in TodaysRun.SEEDS })
        assertTrue(seeds.min() < 1_010 && seeds.max() > 9_990, "from ${seeds.min()} to ${seeds.max()}")
    }

    @Test
    fun `a date that is not on the calendar is refused`() {
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 13, 1) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 0, 1) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 10, 0) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 10, 32) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 2, 30) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 4, 31) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 2, 29) }
        assertEquals(29, TodaysRun(2024, 2, 29).day)
        assertEquals(29, TodaysRun(2000, 2, 29).day)
    }

    @Test
    fun `only the years 2000 to 2099 have a run`() {
        assertEquals(2000..2099, TodaysRun.YEARS)
        assertFailsWith<IllegalArgumentException> { TodaysRun(1999, 12, 31) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2100, 1, 1) }
        assertEquals(2000, TodaysRun(2000, 1, 1).year)
        assertEquals(2099, TodaysRun(2099, 12, 31).year)
    }

    @Test
    fun `a date reads from text such as 2026-10-30`() {
        assertEquals(TodaysRun(2026, 10, 30), TodaysRun.parse("2026-10-30"))
        assertEquals(TodaysRun(2026, 1, 5), TodaysRun.parse(" 2026-1-5 "))
        assertEquals(TodaysRun(2024, 2, 29), TodaysRun.parse("2024-02-29"))
        for (bad in listOf("2026-02-30", "2026-02-29", "2026-13-01", "2026-00-10", "1999-12-31", "2100-01-01",
            "0999-01-01", "999-01-01", "20261030", "2026/10/30", "2026-10-30x", "soon", "")) {
            assertNull(TodaysRun.parse(bad), bad)
        }
    }

    @Test
    fun `every date writes as text that reads back as the same date`() {
        assertEquals("2026-10-30", TodaysRun(2026, 10, 30).toString())
        assertEquals("2027-01-05", TodaysRun(2027, 1, 5).toString())
        for (run in TodaysRun.YEARS.flatMap { daysOf(it) }) {
            assertEquals(run, TodaysRun.parse(run.toString()))
        }
    }
}
