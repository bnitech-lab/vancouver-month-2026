package vancouvermonth.core

import kotlin.math.abs

/**
 * The first minute of a run: three rooms showing rent and two clues, a guess at the total monthly cost,
 * then the real total.
 */
class FirstMinute(val seed: Int, val startingBalance: Int = STARTING_BALANCE) {
    val rooms: List<Room> = RoomDealer.deal(seed)

    /** Index of the room with the lowest total monthly cost. */
    val cheapestRoomIndex: Int
        get() = rooms.indices.minBy { rooms[it].totalMonthlyCost }

    /** [roomIndex] counts from 0. */
    fun reveal(roomIndex: Int, guess: Int): Reveal {
        require(roomIndex in rooms.indices) { "No room at index $roomIndex" }
        val room = rooms[roomIndex]
        return Reveal(
            room = room,
            guess = guess,
            guessGap = abs(guess - room.totalMonthlyCost),
            pickedCheapest = roomIndex == cheapestRoomIndex,
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
    val pickedCheapest: Boolean,
    val balanceAfterFirstMonth: Int,
)
