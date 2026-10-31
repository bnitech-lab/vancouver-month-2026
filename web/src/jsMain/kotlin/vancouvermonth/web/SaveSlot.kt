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
internal class SaveSlot(private val memory: Memory) {
    /** The record kept in the slot, or null when there is none or it is not one this version reads. */
    fun record(): MonthRecord? = MonthRecord.decode(memory.read(KEY))

    fun keep(record: MonthRecord) = memory.write(KEY, record.encode())

    /** Empties the slot. The storage offers only get and set, so an empty line stands for no month. */
    fun clear() = memory.write(KEY, "")

    /**
     * The month kept for the run of [seed] (and of [today], for today's run), replayed, or null. A month kept for
     * another run is left for that run, until a new month takes the slot. A kept line that is not a record, or a
     * record of this run that does not replay, is dropped.
     */
    fun keptFor(seed: Int, today: TodaysRun?): KeptMonth? {
        val text = memory.read(KEY)
        if (text.isNullOrEmpty()) return null
        val record = MonthRecord.decode(text) ?: run {
            clear()
            return null
        }
        if (record.seed != seed || record.today != today) return null
        val replayed = record.replay() ?: run {
            clear()
            return null
        }
        return KeptMonth(record, replayed)
    }

    companion object {
        const val KEY = "vancouver-month.save"
    }
}
