package vancouvermonth.web

import vancouvermonth.core.TodaysRun
import vancouvermonth.save.MonthRecord
import vancouvermonth.save.ReplayedMonth

/** A month kept from an earlier visit, as it was kept and as it replays. */
internal class KeptMonth(val record: MonthRecord, val replayed: ReplayedMonth)

/**
 * The page's one slot for the month in progress, in [memory]: written when the month starts and after every day,
 * and emptied at its ending. Where the browser refuses its storage, nothing is kept and nothing is offered.
 */
internal class SaveSlot(
    private val memory: Memory,
    /** How a kept record becomes a month again. A test may hand in one that throws. */
    private val replay: (MonthRecord) -> ReplayedMonth? = { it.replay() },
) {
    /** The record kept in the slot, or null when there is none or it is not one this version reads. */
    fun record(): MonthRecord? = MonthRecord.decode(memory.read(KEY))

    /** The record kept in the slot if it replays, so a page whose address names no run can continue it. */
    fun continuable(): MonthRecord? = record()?.takeIf { replayed(it) != null }

    fun keep(record: MonthRecord) = memory.write(KEY, record.encode())

    /** Empties the slot. The storage offers only get and set, so an empty line stands for no month. */
    fun clear() {
        memory.write(KEY, "")
        forgetSpot()
    }

    /**
     * Keeps where the character stands beside the record, which knows nothing of it, with how many [moves] the record
     * holds: "12|153.5|52.5". A month that moves on without it no longer finds it ([keptSpot]).
     */
    fun keepSpot(spot: Spot, moves: Int) = memory.write(SPOT_KEY, "$moves|${spot.x}|${spot.z}")

    /** Where the character stood when the record held [moves] moves, or null: none kept, an older one, or not one read. */
    fun keptSpot(moves: Int): Spot? {
        val parts = memory.read(SPOT_KEY)?.split('|') ?: return null
        if (parts.size != 3 || parts[0].toIntOrNull() != moves) return null
        val x = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val z = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return Spot(x, z)
    }

    fun forgetSpot() = memory.write(SPOT_KEY, "")

    /**
     * The month kept for the run of [seed] (and of [today], for today's run), replayed, or null. A month kept for
     * another run is left for that run, until a new month takes the slot. A kept line that is not a record, or a
     * record of this run that does not replay, is dropped, so a line that cannot be continued never blocks the page.
     */
    fun keptFor(seed: Int, today: TodaysRun?): KeptMonth? {
        val text = memory.read(KEY)
        if (text.isNullOrEmpty()) return null
        val record = MonthRecord.decode(text) ?: run {
            clear()
            return null
        }
        if (record.seed != seed || record.today != today) return null
        val replayed = replayed(record) ?: run {
            clear()
            return null
        }
        return KeptMonth(record, replayed)
    }

    /**
     * The day another run's kept month goes on from, or null when the slot holds none that replays. Starting the run
     * of [seed] (and [today]) would replace it.
     */
    fun otherRunDay(seed: Int, today: TodaysRun?): Int? {
        val record = record() ?: return null
        if (record.seed == seed && record.today == today) return null
        return replayed(record)?.month?.day
    }

    /**
     * [record] replayed, or null when it does not replay. A throw counts as not replaying: the rules check each day
     * before playing it, but a rule added to core later that they do not know of must not leave the page blank.
     */
    private fun replayed(record: MonthRecord): ReplayedMonth? = try {
        replay(record)
    } catch (error: Throwable) {
        null
    }

    companion object {
        const val KEY = "vancouver-month.save"
        const val SPOT_KEY = "vancouver-month.spot"
    }
}
