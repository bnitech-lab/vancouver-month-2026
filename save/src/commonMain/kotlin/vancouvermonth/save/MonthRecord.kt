package vancouvermonth.save

import vancouvermonth.core.Action
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.core.Reveal
import vancouvermonth.core.TodaysRun

/** What one day of September was spent on: an action, or the interview that fell on it. */
sealed interface Choice {
    data class Take(val action: Action) : Choice

    data object AttendInterview : Choice
}

/** Can today be spent on [choice]? The same checks [play] makes, without spending the day. */
fun Month.allows(choice: Choice): Boolean = when (choice) {
    is Choice.Take -> canTake(choice.action)
    Choice.AttendInterview -> hasInterviewToday
}

/** Spends today on [choice], as the month's own [Month.take] or [Month.attendInterview] does. */
fun Month.play(choice: Choice): List<Event> = when (choice) {
    is Choice.Take -> take(choice.action)
    Choice.AttendInterview -> attendInterview()
}

/**
 * A month in progress, as little as rebuilds it: the run (its [seed], and its date when it is [today]'s run), the
 * [room] picked (from 0), the [guess] at its total, and what each day so far was spent on. Every piece of luck comes
 * from the seed and the choices, so playing the same choices again gives the same month. [encode] writes it as one
 * short line and [decode] reads it back.
 */
data class MonthRecord(
    val seed: Int,
    val today: TodaysRun?,
    val room: Int,
    val guess: Int,
    val days: List<Choice> = emptyList(),
) {
    /** The same month with one more day, spent on [choice]. */
    fun after(choice: Choice): MonthRecord = copy(days = days + choice)

    /** "1|1011||2|1165|ECRA": the version, the seed, the date of today's run or nothing, the room, the guess, the days. */
    fun encode(): String =
        listOf(VERSION, seed, today ?: "", room, guess, days.joinToString("") { letterOf(it).toString() })
            .joinToString(SEPARATOR)

    /**
     * The month rebuilt from the record, with the same rooms and the same luck a fresh start has, then each day
     * played again in order. Null when it cannot be: today's run under another seed, a room that is not there, a
     * negative guess, a day the rules refuse (the interview on a day without one, an action the player had no energy
     * for, a day after the month ended), or a month that ends with the last day, which leaves nothing to continue.
     */
    fun replay(): ReplayedMonth? {
        if (today != null && today.seed != seed) return null
        val firstMinute = FirstMinute(seed)
        if (room !in firstMinute.rooms.indices || guess < 0) return null
        val reveal = firstMinute.reveal(room, guess)
        val month = firstMinute.startMonth(room)
        var lastEvents = emptyList<Event>()
        for (choice in days) {
            if (!month.allows(choice)) return null
            lastEvents = month.play(choice)
        }
        if (month.isOver) return null
        return ReplayedMonth(firstMinute, reveal, month, lastEvents)
    }

    companion object {
        /** Goes up whenever this line or the rules change so that an older record would play out differently. */
        const val VERSION = 1

        private const val SEPARATOR = "|"

        private val LETTERS = mapOf(
            'W' to Choice.Take(Action.WORK),
            'E' to Choice.Take(Action.STUDY_ENGLISH),
            'C' to Choice.Take(Action.PRACTISE_CODING),
            'A' to Choice.Take(Action.APPLY),
            'R' to Choice.Take(Action.REST),
            'I' to Choice.AttendInterview,
        )

        private fun letterOf(choice: Choice): Char = LETTERS.entries.first { it.value == choice }.key

        /**
         * The record [text] holds, or null when it is not one this version wrote: another version, a missing or
         * extra part, a part that is not a number, a date that is not real, or a day it has no letter for. Only the
         * exact line [encode] writes is read, so nothing half-written or edited by hand passes.
         */
        fun decode(text: String?): MonthRecord? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split(SEPARATOR)
            if (parts.size != 6 || parts[0] != VERSION.toString()) return null
            val seed = parts[1].toIntOrNull() ?: return null
            val today = if (parts[2].isEmpty()) null else TodaysRun.parse(parts[2]) ?: return null
            val room = parts[3].toIntOrNull() ?: return null
            val guess = parts[4].toIntOrNull() ?: return null
            val days = parts[5].map { LETTERS[it] ?: return null }
            val record = MonthRecord(seed, today, room, guess, days)
            return record.takeIf { it.encode() == text }
        }
    }
}

/** A month rebuilt by [MonthRecord.replay], with what happened on its last day played, for the screen to show. */
class ReplayedMonth(
    val firstMinute: FirstMinute,
    val reveal: Reveal,
    val month: Month,
    /** What happened on the day before today, or nothing when the record has no day yet. */
    val lastEvents: List<Event>,
)
