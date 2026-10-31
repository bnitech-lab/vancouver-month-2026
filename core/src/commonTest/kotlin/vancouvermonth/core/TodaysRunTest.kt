package vancouvermonth.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `a month or day that cannot exist is refused`() {
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 13, 1) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 0, 1) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 10, 0) }
        assertFailsWith<IllegalArgumentException> { TodaysRun(2026, 10, 32) }
    }
}
