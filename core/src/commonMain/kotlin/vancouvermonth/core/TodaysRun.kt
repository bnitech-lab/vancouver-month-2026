package vancouvermonth.core

/**
 * Today's run (오늘의 판): the date picks the run number, so everyone who plays on the same date gets the same
 * rooms and the same luck, with no server. The date is three plain numbers rather than a java.time date,
 * so the JVM and a browser work out the same run number.
 */
data class TodaysRun(val year: Int, val month: Int, val day: Int) {
    init {
        require(month in 1..12) { "No month $month" }
        require(day in 1..31) { "No day $day" }
    }

    /** The run number for this date, in the same range as any other run. */
    val seed: Int
        get() {
            // The date as 20261030, then mixed, so neighbouring days land far apart instead of one number apart.
            var x = year * 10_000 + month * 100 + day
            x = ((x ushr 16) xor x) * MIX
            x = ((x ushr 16) xor x) * MIX
            x = (x ushr 16) xor x
            return SEEDS.first + x.mod(SEEDS.last - SEEDS.first + 1)
        }

    companion object {
        /** Every run number a run can have, today's or not. */
        val SEEDS = 1_000..9_999

        /** A well-known integer hash constant. Int multiplication wraps the same way on the JVM and in a browser. */
        private const val MIX = 0x45d9f3b
    }
}
