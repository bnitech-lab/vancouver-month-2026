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
        for (seed in 1..200) {
            val game = FirstMinute(seed)
            for (index in game.rooms.indices) {
                val isCheapest = game.rooms[index].totalMonthlyCost == game.lowestTotal
                assertEquals(isCheapest, game.reveal(index, 1_000).pickedCheapest, "seed $seed, room $index")
            }
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
    fun `picking either of two tied cheapest rooms counts as the cheapest`() {
        val rooms = listOf(
            Room(rent = 1_140, zone = Zone.ONE, utilitiesIncluded = true, utilities = 0),
            Room(rent = 1_050, zone = Zone.TWO, utilitiesIncluded = false, utilities = 150),
            Room(rent = 930, zone = Zone.THREE, utilitiesIncluded = false, utilities = 120),
        )
        val game = FirstMinute(seed = 0, rooms = rooms)
        assertEquals(1_245, game.lowestTotal)
        assertTrue(game.reveal(0, 1_000).pickedCheapest)
        assertTrue(game.reveal(2, 1_000).pickedCheapest)
        assertFalse(game.reveal(1, 1_000).pickedCheapest)
    }

    @Test
    fun `the cheapest room and an exact guess score the full 200`() {
        val game = FirstMinute(seed = 4821)
        val reveal = game.reveal(game.cheapestRoomIndex, game.lowestTotal)
        assertEquals(100, reveal.cheapestPoints)
        assertEquals(100, reveal.guessPoints)
        assertEquals(FirstMinute.MAX_SCORE, reveal.score)
    }

    @Test
    fun `a guess loses one point per dollar and never goes below zero`() {
        val game = FirstMinute(seed = 4821)
        val total = game.rooms[0].totalMonthlyCost
        assertEquals(55, game.reveal(0, total + 45).guessPoints)
        assertEquals(55, game.reveal(0, total - 45).guessPoints)
        assertEquals(0, game.reveal(0, total + 100).guessPoints)
        assertEquals(0, game.reveal(0, total + 900).guessPoints)
    }

    @Test
    fun `a dearer room earns no points for the pick`() {
        for (seed in 1..200) {
            val game = FirstMinute(seed)
            for (index in game.rooms.indices) {
                val reveal = game.reveal(index, 0)
                assertEquals(if (reveal.pickedCheapest) 100 else 0, reveal.cheapestPoints, "seed $seed, room $index")
            }
        }
    }

    @Test
    fun `included utilities cannot cost extra`() {
        assertFailsWith<IllegalArgumentException> {
            Room(rent = 1_000, zone = Zone.ONE, utilitiesIncluded = true, utilities = 150)
        }
    }

    @Test
    fun `a room that does not exist is refused`() {
        assertFailsWith<IllegalArgumentException> { FirstMinute(seed = 1).reveal(3, 1_000) }
    }
}
