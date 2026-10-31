package vancouvermonth.core

/**
 * Today's run (오늘의 판): the date picks the run number, so everyone who plays on the same date gets the same
 * rooms and the same luck, with no server. The date is three plain numbers rather than a java.time date,
 * so the JVM and a browser work out the same run number. Only real dates from 2000 to 2099 are runs.
 */
data class TodaysRun(val year: Int, val month: Int, val day: Int) {
    init {
        require(isRealDate(year, month, day)) { "Not a date from ${YEARS.first} to ${YEARS.last}: $year-$month-$day" }
    }

    /** The run number for this date, in the same range as any other run. */
    val seed: Int
        get() {
            // The date as 20261030, then mixed, so neighbouring days land far apart instead of one number apart.
            // A product too big for an Int wraps around at 32 bits. Kotlin/JS compiles Int multiplication to
            // Math.imul, so a browser wraps exactly as the JVM does; plain JavaScript numbers would not wrap.
            var x = year * 10_000 + month * 100 + day
            x = ((x ushr 16) xor x) * MIX
            x = ((x ushr 16) xor x) * MIX
            x = (x ushr 16) xor x
            // mod, not %: x can be negative here, and % keeps the sign (-7 % 3 is -1, while (-7).mod(3) is 2).
            return SEEDS.first + x.mod(SEEDS.last - SEEDS.first + 1)
        }

    /** "2026-10-30", the form [parse] reads back. */
    override fun toString(): String =
        "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {
        /** Every run number a run can have, today's or not. */
        val SEEDS = 1_000..9_999

        /** The years today's run covers, so a year always has four digits. */
        val YEARS = 2000..2099

        /** A well-known integer hash constant. */
        private const val MIX = 0x45d9f3b

        private val DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")

        /** "2026-10-30" (or "2026-1-5") as today's run, or null when it is not a real date from 2000 to 2099. */
        fun parse(text: String): TodaysRun? {
            val match = DATE.matchEntire(text.trim()) ?: return null
            val (year, month, day) = match.destructured.toList().map { it.toInt() }
            return if (isRealDate(year, month, day)) TodaysRun(year, month, day) else null
        }

        private fun isRealDate(year: Int, month: Int, day: Int): Boolean =
            year in YEARS && month in 1..12 && day in 1..daysIn(year, month)

        /** Days in [month] of [year], with February 29th in leap years. */
        private fun daysIn(year: Int, month: Int): Int = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}
