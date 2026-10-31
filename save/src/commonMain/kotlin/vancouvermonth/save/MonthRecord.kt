package vancouvermonth.save

import vancouvermonth.core.Action
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.core.Reveal
import vancouvermonth.core.Spending
import vancouvermonth.core.TodaysRun

/** What a record keeps, in order: a day spent ([Choice]), or money spent during a day ([Spend]). */
sealed interface Move

/** What one day of September was spent on: an action, or the interview that fell on it. */
sealed interface Choice : Move {
    data class Take(val action: Action) : Choice

    data object AttendInterview : Choice
}

/** Money spent in the town during a day, as [Month.spend] took it: no day goes by. */
data class Spend(val kind: Spending, val amount: Int) : Move

/** Can [move] be played now? The same checks [play] and [Month.spend] make, without playing it. */
fun Month.allows(move: Move): Boolean = when (move) {
    is Choice.Take -> canTake(move.action)
    Choice.AttendInterview -> hasInterviewToday
    is Spend -> canSpend(move.kind, move.amount)
}

/** Spends today on [choice], as the month's own [Month.take] or [Month.attendInterview] does. */
fun Month.play(choice: Choice): List<Event> = when (choice) {
    is Choice.Take -> take(choice.action)
    Choice.AttendInterview -> attendInterview()
}

/**
 * A month in progress, as little as rebuilds it: the run (its [seed], and its date when it is [today]'s run), the
 * [room] picked (from 0), the [guess] at its total, and in order what each day so far was spent on and what money was
 * spent between them ([moves]). Every piece of luck comes from the seed and the days, so playing the same moves again
 * gives the same month. [encode] writes it as one short line and [decode] reads it back.
 */
data class MonthRecord(
    val seed: Int,
    val today: TodaysRun?,
    val room: Int,
    val guess: Int,
    val moves: List<Move> = emptyList(),
) {
    /** What each day so far was spent on, in order, the money spent between them left out. */
    val days: List<Choice>
        get() = moves.filterIsInstance<Choice>()

    /** The same month with one more move: a day spent on a [Choice], or money spent on a [Spend]. */
    fun after(move: Move): MonthRecord = copy(moves = moves + move)

    /**
     * "2|1011||2|1165|Eb350Wq4C": the version, the seed, today's run's date or nothing, the room, the guess, and the
     * moves: a capital letter for each day, and a small letter with its amount for each spend.
     */
    fun encode(): String =
        listOf(VERSION, seed, today ?: "", room, guess, moves.joinToString("") { textOf(it) }).joinToString(SEPARATOR)

    /**
     * The month rebuilt from the record, with the same rooms and the same luck a fresh start has, then each move
     * played again in order. Null when it cannot be: today's run under another seed, a room that is not there, a
     * negative guess, a move the rules refuse (the interview on a day without one, an action the player had no energy
     * for, a day after the month ended, a spend for another amount than its price or than a taxi's meter can show, on
     * what was already bought, or with too little money), or a month that ends with the last day, which leaves nothing
     * to continue.
     */
    fun replay(): ReplayedMonth? {
        if (today != null && today.seed != seed) return null
        val firstMinute = FirstMinute(seed)
        if (room !in firstMinute.rooms.indices || guess < 0) return null
        val reveal = firstMinute.reveal(room, guess)
        val month = firstMinute.startMonth(room)
        var lastEvents = emptyList<Event>()
        var spentToday = emptyList<Event>()
        for (move in moves) {
            if (!month.allows(move)) return null
            when (move) {
                is Choice -> {
                    lastEvents = spentToday + month.play(move)
                    spentToday = emptyList()
                }
                is Spend -> spentToday = spentToday + month.spend(move.kind, move.amount)
            }
        }
        if (month.isOver) return null
        return ReplayedMonth(firstMinute, reveal, month, lastEvents, spentToday)
    }

    companion object {
        /**
         * Goes up whenever this line or the rules change so that an older record would play out differently. A new
         * kind of spend with a letter of its own does not: every older line still reads and plays out as before.
         */
        const val VERSION = 2

        private const val SEPARATOR = "|"

        private val LETTERS = mapOf(
            'W' to Choice.Take(Action.WORK),
            'E' to Choice.Take(Action.STUDY_ENGLISH),
            'C' to Choice.Take(Action.PRACTISE_CODING),
            'A' to Choice.Take(Action.APPLY),
            'R' to Choice.Take(Action.REST),
            'I' to Choice.AttendInterview,
        )

        /**
         * The small letter of each kind of spend, followed in the line by its amount. A `when`, so a kind added
         * without a letter does not compile, rather than throw after its money was spent.
         */
        private fun letterOf(kind: Spending): Char = when (kind) {
            Spending.BIKE -> 'b'
            Spending.E_SCOOTER -> 'k'
            Spending.SHARED_BIKE_RIDE -> 's'
            Spending.SHARED_E_SCOOTER_RIDE -> 'q'
            Spending.WATER_BUS_RIDE -> 'w'
            Spending.TAXI_RIDE -> 't'
        }

        private val SPEND_LETTERS = Spending.entries.associateBy { letterOf(it) }

        private fun textOf(move: Move): String = when (move) {
            is Choice -> LETTERS.entries.first { it.value == move }.key.toString()
            is Spend -> letterOf(move.kind) + move.amount.toString()
        }

        /** The moves [text] holds, or null when a part is not one: an unknown letter, or a spend with no amount. */
        private fun movesOf(text: String): List<Move>? {
            val moves = mutableListOf<Move>()
            var at = 0
            while (at < text.length) {
                val letter = text[at]
                at += 1
                val day = LETTERS[letter]
                if (day != null) {
                    moves += day
                    continue
                }
                val kind = SPEND_LETTERS[letter] ?: return null
                val start = at
                while (at < text.length && text[at] in '0'..'9') at += 1
                val amount = text.substring(start, at).toIntOrNull() ?: return null
                moves += Spend(kind, amount)
            }
            return moves
        }

        /**
         * The record [text] holds, or null when it is not one this version wrote: another version, a missing or
         * extra part, a part that is not a number, a date that is not real or whose run has another seed, a day it
         * has no letter for, or a spend with no amount. Only the exact line [encode] writes is read, so nothing
         * half-written or edited by hand passes.
         */
        fun decode(text: String?): MonthRecord? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split(SEPARATOR)
            if (parts.size != 6 || parts[0] != VERSION.toString()) return null
            val seed = parts[1].toIntOrNull() ?: return null
            val today = if (parts[2].isEmpty()) null else TodaysRun.parse(parts[2]) ?: return null
            if (today != null && today.seed != seed) return null
            val room = parts[3].toIntOrNull() ?: return null
            val guess = parts[4].toIntOrNull() ?: return null
            val moves = movesOf(parts[5]) ?: return null
            val record = MonthRecord(seed, today, room, guess, moves)
            return record.takeIf { it.encode() == text }
        }
    }
}

/** A month rebuilt by [MonthRecord.replay], with what happened on its last day played, for the screen to show. */
class ReplayedMonth(
    val firstMinute: FirstMinute,
    val reveal: Reveal,
    val month: Month,
    /**
     * What happened on the day before today, the money spent during it first, or nothing when the record has no day
     * yet.
     */
    val lastEvents: List<Event>,
    /** The money spent today so far, before today's day is spent. */
    val spentToday: List<Event> = emptyList(),
)
