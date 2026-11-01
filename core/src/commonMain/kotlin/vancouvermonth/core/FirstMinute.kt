package vancouvermonth.core

import kotlin.math.abs

/**
 * The first minute of a run: three rooms showing rent and two clues, a guess at the total monthly cost, then the real
 * total and the money the guess wins or loses ([guessMoney]), which the month starts with. [rooms] can be given
 * directly, which lets a test build a tie.
 */
class FirstMinute(
    val seed: Int,
    val rooms: List<Room> = RoomDealer.deal(seed),
    val startingBalance: Int = STARTING_BALANCE,
) {
    /** The lowest total monthly cost among the rooms. More than one room can have it. */
    val lowestTotal: Int
        get() = rooms.minOf { it.totalMonthlyCost }

    /** Index of the first room with the lowest total. */
    val cheapestRoomIndex: Int
        get() = rooms.indexOfFirst { it.totalMonthlyCost == lowestTotal }

    /** Every room with the lowest total: usually one, two or three when totals tie. */
    val cheapestRoomIndices: List<Int>
        get() = rooms.indices.filter { rooms[it].totalMonthlyCost == lowestTotal }

    /** [roomIndex] counts from 0. [guess] is in dollars and cannot be negative. */
    fun reveal(roomIndex: Int, guess: Int): Reveal {
        require(roomIndex in rooms.indices) { "No room at index $roomIndex" }
        require(guess >= 0) { "A guess cannot be negative: $guess" }
        val room = rooms[roomIndex]
        val pickedCheapest = room.totalMonthlyCost == lowestTotal
        val guessGap = abs(guess - room.totalMonthlyCost)
        return Reveal(
            room = room,
            guess = guess,
            guessGap = guessGap,
            pickedCheapest = pickedCheapest,
            cheapestPoints = if (pickedCheapest) MAX_POINTS_EACH else 0,
            guessPoints = maxOf(0, MAX_POINTS_EACH - guessGap),
            guessMoney = guessMoney(guessGap),
            balanceAfterFirstMonth = startingBalance - room.totalMonthlyCost,
        )
    }

    /** September after picking [roomIndex], with the first month's total already paid. */
    fun startMonth(roomIndex: Int, dice: Dice = RandomDice(seed xor MONTH_DICE_SALT)): Month {
        require(roomIndex in rooms.indices) { "No room at index $roomIndex" }
        val room = rooms[roomIndex]
        return Month(room, startingBalance - room.totalMonthlyCost, dice)
    }

    /**
     * September after picking [roomIndex] and guessing [guess] for its total: the first month's total already paid, and
     * the guess's money ([Reveal.guessMoney]) added once, as the month's opening event ([Month.opening]).
     */
    fun startMonth(roomIndex: Int, guess: Int, dice: Dice = RandomDice(seed xor MONTH_DICE_SALT)): Month {
        val reveal = reveal(roomIndex, guess)
        val guessed = Event.Guessed(reveal.guessGap, reveal.guessMoney)
        return Month(reveal.room, reveal.balanceAfterFirstMonth, dice, guessed)
    }

    companion object {
        const val STARTING_BALANCE = 3_000

        /** Keeps the month's luck apart from the luck that dealt the rooms. */
        private const val MONTH_DICE_SALT = 0x5EED

        /** Points for picking the cheapest room, and the most a guess can earn: one point off per dollar missed. */
        const val MAX_POINTS_EACH = 100
        const val MAX_SCORE = MAX_POINTS_EACH * 2

        /** The most a guess can win: an exact guess wins all of it, and each dollar it misses by takes one off. */
        const val GUESS_REWARD = 100

        /**
         * The most a guess can lose. Missed by more than [GUESS_REWARD], each dollar more is a dollar lost, up to this.
         * What is left after the dearest room and this still pays every day's living cost in September.
         */
        const val GUESS_PENALTY = 300

        /**
         * What a guess [gap] dollars away from the real total adds to the balance: [GUESS_REWARD] less the gap, never
         * below minus [GUESS_PENALTY]. Exact gives +100, $100 off gives 0, $250 off gives -150, and $400 off or more -300.
         */
        fun guessMoney(gap: Int): Int {
            require(gap >= 0) { "A gap cannot be negative: $gap" }
            return maxOf(GUESS_REWARD - gap, -GUESS_PENALTY)
        }
    }
}

data class Reveal(
    val room: Room,
    val guess: Int,
    val guessGap: Int,
    /** True when no room was cheaper, ties included. */
    val pickedCheapest: Boolean,
    val cheapestPoints: Int,
    val guessPoints: Int,
    /** What the guess adds to the balance as the month starts: [FirstMinute.guessMoney] of [guessGap]. */
    val guessMoney: Int,
    val balanceAfterFirstMonth: Int,
) {
    val score: Int
        get() = cheapestPoints + guessPoints

    /** The balance September starts with: what the first month's total left, with the guess's money. */
    val balanceAtStart: Int
        get() = balanceAfterFirstMonth + guessMoney
}
