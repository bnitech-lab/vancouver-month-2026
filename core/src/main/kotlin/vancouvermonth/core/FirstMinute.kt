package vancouvermonth.core

import kotlin.math.abs

/**
 * The first minute of a run: three rooms showing rent and two clues, a guess at the total monthly cost,
 * then the real total and a score. [rooms] can be given directly, which lets a test build a tie.
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
            balanceAfterFirstMonth = startingBalance - room.totalMonthlyCost,
        )
    }

    /** September after picking [roomIndex], with the first month's total already paid. */
    fun startMonth(roomIndex: Int, dice: Dice = RandomDice(seed xor MONTH_DICE_SALT)): Month {
        require(roomIndex in rooms.indices) { "No room at index $roomIndex" }
        val room = rooms[roomIndex]
        return Month(room, startingBalance - room.totalMonthlyCost, dice)
    }

    companion object {
        const val STARTING_BALANCE = 3_000

        /** Keeps the month's luck apart from the luck that dealt the rooms. */
        private const val MONTH_DICE_SALT = 0x5EED

        /** Points for picking the cheapest room, and the most a guess can earn: one point off per dollar missed. */
        const val MAX_POINTS_EACH = 100
        const val MAX_SCORE = MAX_POINTS_EACH * 2
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
    val balanceAfterFirstMonth: Int,
) {
    val score: Int
        get() = cheapestPoints + guessPoints
}
