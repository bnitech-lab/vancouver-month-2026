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
        assertEquals(listOf(0, 2), game.cheapestRoomIndices)
        assertTrue(game.reveal(0, 1_000).pickedCheapest)
        assertTrue(game.reveal(2, 1_000).pickedCheapest)
        assertFalse(game.reveal(1, 1_000).pickedCheapest)
        assertEquals(200, game.reveal(0, 1_245).score)
        assertEquals(200, game.reveal(2, 1_245).score)
        assertEquals(0, game.reveal(1, 1_000).cheapestPoints)
    }

    @Test
    fun `a negative guess is refused`() {
        assertFailsWith<IllegalArgumentException> { FirstMinute(seed = 1).reveal(0, -1) }
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
        assertEquals(1, game.reveal(0, total + 99).guessPoints)
        assertEquals(0, game.reveal(0, total + 100).guessPoints)
        assertEquals(0, game.reveal(0, total + 101).guessPoints)
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
    fun `the guess wins $100 less a dollar for each dollar missed, and loses no more than $300`() {
        val game = FirstMinute(seed = 4821)
        val total = game.rooms[0].totalMonthlyCost
        val expected = mapOf(
            0 to 100, 1 to 99, 45 to 55, 99 to 1, 100 to 0, 101 to -1, 250 to -150,
            399 to -299, 400 to -300, 401 to -300, 500 to -300, 10_000 to -300,
        )
        for ((gap, money) in expected) {
            assertEquals(money, FirstMinute.guessMoney(gap), "$gap off")
            assertEquals(money, game.reveal(0, total + gap).guessMoney, "$gap over")
            if (gap <= total) assertEquals(money, game.reveal(0, total - gap).guessMoney, "$gap under")
        }
        assertEquals(100, FirstMinute.GUESS_REWARD)
        assertEquals(300, FirstMinute.GUESS_PENALTY)
        assertFailsWith<IllegalArgumentException> { FirstMinute.guessMoney(-1) }
    }

    @Test
    fun `picking the cheapest room wins no extra money, only the guess does`() {
        for (seed in 1..200) {
            val game = FirstMinute(seed)
            for (index in game.rooms.indices) {
                val total = game.rooms[index].totalMonthlyCost
                assertEquals(FirstMinute.GUESS_REWARD, game.reveal(index, total).guessMoney, "seed $seed, room $index")
                assertEquals(-150, game.reveal(index, total + 250).guessMoney, "seed $seed, room $index")
            }
        }
    }

    @Test
    fun `the reveal gives the balance the month starts with, after the first total and the guess's money`() {
        val game = FirstMinute(seed = 4821)
        val total = game.rooms[1].totalMonthlyCost
        val close = game.reveal(1, total + 20)
        assertEquals(FirstMinute.STARTING_BALANCE - total, close.balanceAfterFirstMonth)
        assertEquals(FirstMinute.STARTING_BALANCE - total + 80, close.balanceAtStart)
        val far = game.reveal(1, total + 250)
        assertEquals(FirstMinute.STARTING_BALANCE - total - 150, far.balanceAtStart)
    }

    @Test
    fun `the month starts once with the guess's money, as its opening event`() {
        val game = FirstMinute(seed = 1011)
        for ((gap, money) in listOf(0 to 100, 45 to 55, 100 to 0, 250 to -150, 500 to -300)) {
            val total = game.rooms[2].totalMonthlyCost
            val month = game.startMonth(2, total + gap)
            val start = FirstMinute.STARTING_BALANCE - total + money
            assertEquals(start, month.balance, "$gap off")
            assertEquals(game.reveal(2, total + gap).balanceAtStart, month.balance)
            assertEquals(listOf(Event.Guessed(gap, money)), month.opening, "$gap off")
            // Days go by without the guess's money again: only each day's living cost comes off.
            month.take(Action.REST)
            month.take(Action.REST)
            assertEquals(start - 2 * Month.DAILY_LIVING_COST, month.balance)
            while (!month.isOver) month.take(Action.REST)
            assertEquals(start - (Month.LAST_DAY - Month.FIRST_DAY + 1) * Month.DAILY_LIVING_COST, month.balance)
            assertEquals(listOf(Event.Guessed(gap, money)), month.opening)
        }
        // A month made without a guess has no opening event and no money from it.
        assertEquals(emptyList(), Month(game.rooms[2], 1_000, RandomDice(1)).opening)
        assertEquals(1_000, Month(game.rooms[2], 1_000, RandomDice(1)).balance)
    }

    @Test
    fun `the ending is worked out with the guess's money in the balance`() {
        val game = FirstMinute(seed = 1011)
        val room = game.cheapestRoomIndex
        val total = game.lowestTotal

        /** Five café shifts, then rest to the end: enough for October with an exact guess, short of it $500 off. */
        fun endingAfter(guess: Int): Month {
            val month = game.startMonth(room, guess)
            repeat(5) { month.take(Action.WORK) }
            while (!month.isOver) month.take(Action.REST)
            return month
        }
        val exact = endingAfter(total)
        val far = endingAfter(total + 500)
        assertEquals(Ending.SURVIVED, exact.ending)
        assertEquals(Ending.CANNOT_PAY_RENT, far.ending)
        assertEquals(FirstMinute.GUESS_REWARD + FirstMinute.GUESS_PENALTY, exact.balance - far.balance)
        assertTrue(exact.balance >= total && far.balance < total, "${exact.balance}, ${far.balance}")
    }

    @Test
    fun `the month's luck is the same whatever the guess`() {
        for (seed in 1..100) {
            val game = FirstMinute(seed)
            val exact = game.startMonth(0, game.rooms[0].totalMonthlyCost)
            val far = game.startMonth(0, 0)
            while (!exact.isOver) {
                if (exact.hasInterviewToday) {
                    assertEquals(exact.attendInterview(), far.attendInterview(), "seed $seed")
                } else {
                    val action = if (exact.canTake(Action.APPLY)) Action.APPLY else Action.REST
                    assertEquals(exact.take(action), far.take(action), "seed $seed")
                }
            }
            assertEquals(exact.day, far.day, "seed $seed")
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
