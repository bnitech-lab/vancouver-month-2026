package vancouvermonth.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstMinuteTest {
    @Test
    fun `total monthly cost adds rent, utilities and the pass`() {
        val room = Room(rent = 870, zone = Zone.THREE, utilitiesIncluded = false, utilities = 180)
        assertEquals(870 + 180 + 195, room.totalMonthlyCost)
    }

    @Test
    fun `the reveal measures how far the guess was in either direction`() {
        val game = FirstMinute(seed = 4821)
        val total = game.rooms[0].totalMonthlyCost
        assertEquals(50, game.reveal(0, total - 50).guessGap)
        assertEquals(50, game.reveal(0, total + 50).guessGap)
        assertEquals(0, game.reveal(0, total).guessGap)
    }

    @Test
    fun `the first month's total comes off the starting balance`() {
        val game = FirstMinute(seed = 4821)
        val reveal = game.reveal(1, 1_000)
        assertEquals(FirstMinute.STARTING_BALANCE - game.rooms[1].totalMonthlyCost, reveal.balanceAfterFirstMonth)
    }

    @Test
    fun `picking the cheapest room is recognised and any other room is not`() {
        val game = FirstMinute(seed = 4821)
        for (index in game.rooms.indices) {
            val reveal = game.reveal(index, 1_000)
            if (index == game.cheapestRoomIndex) assertTrue(reveal.pickedCheapest) else assertFalse(reveal.pickedCheapest)
        }
    }

    @Test
    fun `the cheapest room has the lowest total`() {
        for (seed in 1..200) {
            val game = FirstMinute(seed)
            val lowest = game.rooms.minOf { it.totalMonthlyCost }
            assertEquals(lowest, game.rooms[game.cheapestRoomIndex].totalMonthlyCost, "seed $seed")
        }
    }

    @Test
    fun `a room that does not exist is refused`() {
        assertFailsWith<IllegalArgumentException> { FirstMinute(seed = 1).reveal(3, 1_000) }
    }
}
