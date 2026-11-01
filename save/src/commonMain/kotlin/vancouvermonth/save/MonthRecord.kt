package vancouvermonth.save

import vancouvermonth.core.Action
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.core.Reveal
import vancouvermonth.core.Spending
import vancouvermonth.core.TodaysRun

/** What a record keeps, in order: something that takes time ([Choice]), or money spent between them ([Spend]). */
sealed interface Move

/** What the player did with some of a day's time: an action, the interview that fell on it, or the night's sleep. */
sealed interface Choice : Move {
    /**
     * [action], taken [at] one of the places of its kind, counted from 0: the town has several cafés, libraries,
     * coworking spaces and offices, the first of each downtown, and a game with one of each takes them all at 0. An
     * application's [at] is the office its interview is held at. Sleep is always at 0, at 내 방. A record cannot know
     * how many places of a kind the game has, so any digit reads: the game takes a number it has no place for as the
     * first of the kind, downtown's, and the rules never depend on it.
     */
    data class Take(val action: Action, val at: Int = 0) : Choice {
        init {
            require(at in 0..MOST_AT && (at == 0 || action != Action.REST)) { "No place $at for $action" }
        }

        companion object {
            /** The highest place number a record keeps: one digit. */
            const val MOST_AT = 9
        }
    }

    data object AttendInterview : Choice
}

/** Money spent in the town, as [Month.spend] took it: no time goes by. */
data class Spend(val kind: Spending, val amount: Int) : Move

/** Can [move] be played now? The same checks [play] and [Month.spend] make, without playing it. */
fun Month.allows(move: Move): Boolean = when (move) {
    is Choice.Take -> canTake(move.action)
    Choice.AttendInterview -> hasInterviewToday
    is Spend -> canSpend(move.kind, move.amount)
}

/** Plays [choice] now, as the month's own [Month.take] or [Month.attendInterview] does. */
fun Month.play(choice: Choice): List<Event> = when (choice) {
    is Choice.Take -> take(choice.action)
    Choice.AttendInterview -> attendInterview()
}

/**
 * A month in progress, as little as rebuilds it: the run (its [seed], and its date when it is [today]'s run), the
 * [room] picked (from 0), the [guess] at its total, and in order every action, interview and night's sleep so far and
 * the money spent between them ([moves]). A day is the choices up to its sleep, so the days need no mark of their own.
 * Every piece of luck comes from the seed and the choices, so playing the same moves again gives the same month.
 * [encode] writes it as one short line and [decode] reads it back.
 */
data class MonthRecord(
    val seed: Int,
    val today: TodaysRun?,
    val room: Int,
    val guess: Int,
    val moves: List<Move> = emptyList(),
) {
    /** Every action, interview and sleep so far, in order, the money spent between them left out. */
    val choices: List<Choice>
        get() = moves.filterIsInstance<Choice>()

    /** The same month with one more move: a [Choice], or money spent on a [Spend]. */
    fun after(move: Move): MonthRecord = copy(moves = moves + move)

    /**
     * Where the last application went, as its [Choice.Take.at] has it: the office an interview it booked is held at.
     * While an interview is booked no other application goes in, so it is always the one that booked it. 0 before any.
     */
    val appliedAt: Int
        get() = choices.filterIsInstance<Choice.Take>().lastOrNull { it.action == Action.APPLY }?.at ?: 0

    /**
     * "4|1011||2|1165|Eb350W2q4RA1": the version, the seed, today's run's date or nothing, the room, the guess, and the
     * moves: a capital letter for each choice (R for a night's sleep), followed by the place's number when it was not
     * the first of its kind ([Choice.Take.at]), and a small letter with its amount for each spend.
     */
    fun encode(): String =
        listOf(VERSION, seed, today ?: "", room, guess, moves.joinToString("") { textOf(it) }).joinToString(SEPARATOR)

    /**
     * The month rebuilt from the record, with the same rooms and the same luck a fresh start has, then each move
     * played again in order. Null when it cannot be: today's run under another seed, a room that is not there, a
     * negative guess, a move the rules refuse (the interview on a day without one, an action the player had too little
     * stamina for, a second application in a day, anything after the month ended, a spend for another amount than its
     * price or than a taxi's meter can show, on what was already bought, or with too little money), or a month that has
     * ended, which leaves nothing to continue.
     */
    fun replay(): ReplayedMonth? {
        if (today != null && today.seed != seed) return null
        val firstMinute = FirstMinute(seed)
        if (room !in firstMinute.rooms.indices || guess < 0) return null
        val reveal = firstMinute.reveal(room, guess)
        val month = firstMinute.startMonth(room, guess)
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
         * 3: the guess's money is added as the month starts, so a version 2 line would start with another balance.
         * 4: a day holds several actions until the night's sleep (R), each costing stamina, so a version 3 line, one
         * letter a day, would play out as another month.
         * The place an action was taken at, a digit after its letter, came later without a new version: a line without
         * one took every action at the first place of its kind, downtown, where every place then stood, and plays out
         * as before.
         */
        const val VERSION = 4

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
            is Choice -> {
                val place = (move as? Choice.Take)?.at ?: 0
                val first = if (move is Choice.Take) move.copy(at = 0) else move
                LETTERS.entries.first { it.value == first }.key + if (place == 0) "" else "$place"
            }
            is Spend -> letterOf(move.kind) + move.amount.toString()
        }

        /**
         * The moves [text] holds, or null when a part is not one: an unknown letter, a spend with no amount, or a place's
         * number after sleep or the interview.
         */
        private fun movesOf(text: String): List<Move>? {
            val moves = mutableListOf<Move>()
            var at = 0
            while (at < text.length) {
                val letter = text[at]
                at += 1
                val day = LETTERS[letter]
                if (day != null) {
                    val number = text.getOrNull(at)?.takeIf { it in '0'..'9' }?.minus('0')
                    if (number == null) {
                        moves += day
                        continue
                    }
                    if (day !is Choice.Take || day.action == Action.REST) return null
                    moves += day.copy(at = number)
                    at += 1
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

/** A month rebuilt by [MonthRecord.replay], with what its last choice brought, for the screen to show. */
class ReplayedMonth(
    val firstMinute: FirstMinute,
    val reveal: Reveal,
    val month: Month,
    /**
     * What the last action, interview or sleep brought, the money spent just before it first, or nothing when the
     * record has no choice yet.
     */
    val lastEvents: List<Event>,
    /** The money spent since the last choice, which heads the next one's log. */
    val spentToday: List<Event> = emptyList(),
)
