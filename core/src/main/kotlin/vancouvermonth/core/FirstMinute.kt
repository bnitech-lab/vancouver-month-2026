package vancouvermonth.core

import kotlin.math.abs

/**
 * The first minute of a run: three rooms showing rent and two clues, a guess at the total monthly cost,
 * then the real total. [rooms] can be given directly, which lets a test build a tie.
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

    /** [roomIndex] counts from 0. */
    fun reveal(roomIndex: Int, guess: Int): Reveal {
        require(roomIndex in rooms.indices) { "No room at index $roomIndex" }
        val room = rooms[roomIndex]
        return Reveal(
            room = room,
            guess = guess,
            guessGap = abs(guess - room.totalMonthlyCost),
            pickedCheapest = room.totalMonthlyCost == lowestTotal,
            balanceAfterFirstMonth = startingBalance - room.totalMonthlyCost,
        )
    }

    companion object {
        const val STARTING_BALANCE = 3_000
    }
}

data class Reveal(
    val room: Room,
    val guess: Int,
    val guessGap: Int,
    /** True when no room was cheaper, ties included. */
    val pickedCheapest: Boolean,
    val balanceAfterFirstMonth: Int,
)
