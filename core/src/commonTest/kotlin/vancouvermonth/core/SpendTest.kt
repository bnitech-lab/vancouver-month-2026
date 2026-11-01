package vancouvermonth.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Money spent in the town: off the balance, never a day, and never the month's luck. */
class SpendTest {
    private val room = Room(rent = 1_020, zone = Zone.TWO, utilitiesIncluded = true, utilities = 0)

    private fun month(balance: Int = 1_800, seed: Int = 7) = Month(room, balance, RandomDice(seed))

    /** Everything a month decides with, money and what was bought aside (and so how a month without an offer ends). */
    private fun Month.rest(): List<Any?> = listOf(day, english, coding, energy, interview, isOver, daysLeft)

    @Test
    fun `the prices are the plan's, and only a bike and an e-scooter are kept`() {
        assertEquals(listOf(350, 550, 1, 1, 4, 4), Spending.entries.map { it.price })
        assertEquals(Item.BIKE, Spending.BIKE.buys)
        assertEquals(Item.E_SCOOTER, Spending.E_SCOOTER.buys)
        for (kind in Spending.entries - Spending.BIKE - Spending.E_SCOOTER) assertNull(kind.buys, "$kind")
        val byDistance = setOf(Spending.TAXI_RIDE, Spending.SHARED_BIKE_RIDE, Spending.SHARED_E_SCOOTER_RIDE)
        for (kind in Spending.entries - byDistance) {
            assertEquals(kind.price, kind.most, "$kind")
            assertTrue(kind.costs(kind.price), "$kind")
            assertFalse(kind.costs(kind.price + 1), "$kind")
            assertFalse(kind.costs(0), "$kind")
        }
    }

    @Test
    fun `a water bus ride is $4 each time, and keeps nothing`() {
        val month = month()
        val before = month.rest()
        repeat(3) { assertEquals(Event.Spent(Spending.WATER_BUS_RIDE, 4), month.spend(Spending.WATER_BUS_RIDE)) }
        assertEquals(1_800 - 12, month.balance)
        assertEquals(emptySet(), month.owned)
        assertEquals(before, month.rest())
        assertFalse(month.canSpend(Spending.WATER_BUS_RIDE, 5))
        assertFailsWith<IllegalArgumentException> { month.spend(Spending.WATER_BUS_RIDE, 3) }
    }

    @Test
    fun `a taxi's fare is $4 and $1·50 for every ten squares, $0·70 past 160, to the dollar, and the meter stops at $60`() {
        assertEquals(4, TaxiFare.forDistance(0.0))
        assertEquals(4, TaxiFare.forDistance(3.0))
        assertEquals(5, TaxiFare.forDistance(4.0))
        assertEquals(6, TaxiFare.forDistance(10.0))
        assertEquals(7, TaxiFare.forDistance(20.0))
        assertEquals(19, TaxiFare.forDistance(100.0))
        assertEquals(28, TaxiFare.forDistance(160.0))
        // Out to Burnaby: 160 squares at the short rate, the rest at the long one.
        assertEquals(43, TaxiFare.forDistance(372.0))
        assertEquals(48, TaxiFare.forDistance(441.0))
        assertEquals(60, TaxiFare.forDistance(5_000.0))
        assertEquals(4, TaxiFare.forDistance(-5.0))
        var last = TaxiFare.BASE
        for (squares in 0..800) {
            val fare = TaxiFare.forDistance(squares.toDouble())
            assertTrue(fare >= last && Spending.TAXI_RIDE.costs(fare), "$squares squares: $fare")
            last = fare
        }
    }

    @Test
    fun `a shared bike is $1 and $0·40 for every ten squares up to $15, an e-scooter $1 and $0·60 up to $20`() {
        assertEquals(1, SharedFare.BIKE.forDistance(0.0))
        assertEquals(1, SharedFare.BIKE.forDistance(12.0))
        assertEquals(2, SharedFare.BIKE.forDistance(13.0))
        assertEquals(2, SharedFare.BIKE.forDistance(25.0))
        assertEquals(5, SharedFare.BIKE.forDistance(100.0))
        assertEquals(15, SharedFare.BIKE.forDistance(350.0))
        assertEquals(15, SharedFare.BIKE.forDistance(5_000.0))
        assertEquals(1, SharedFare.E_SCOOTER.forDistance(0.0))
        assertEquals(2, SharedFare.E_SCOOTER.forDistance(10.0))
        assertEquals(7, SharedFare.E_SCOOTER.forDistance(100.0))
        assertEquals(20, SharedFare.E_SCOOTER.forDistance(317.0))
        assertEquals(20, SharedFare.E_SCOOTER.forDistance(5_000.0))
        assertEquals(1, SharedFare.E_SCOOTER.forDistance(-5.0))
        val kinds = listOf(
            SharedFare.BIKE to Spending.SHARED_BIKE_RIDE,
            SharedFare.E_SCOOTER to Spending.SHARED_E_SCOOTER_RIDE,
        )
        for ((fare, kind) in kinds) {
            assertEquals(fare, SharedFare.of(kind))
            assertEquals(fare.base, kind.price)
            assertEquals(fare.most, kind.most)
            var last = fare.base
            for (squares in 0..800) {
                val amount = fare.forDistance(squares.toDouble())
                assertTrue(amount >= last && kind.costs(amount), "$kind, $squares squares: $amount")
                last = amount
            }
            assertEquals(fare.most, last)
            // The old flat fares, $3 and $4, are still fares the meter can show, so a month kept before still plays.
            assertTrue(kind.costs(3) && kind.costs(4), "$kind")
            assertFalse(kind.costs(0) || kind.costs(fare.most + 1), "$kind")
        }
        // An e-scooter costs more than a bike for the same way, past the base.
        for (squares in listOf(50.0, 100.0, 200.0)) {
            assertTrue(SharedFare.E_SCOOTER.forDistance(squares) > SharedFare.BIKE.forDistance(squares), "$squares")
        }
        assertNull(SharedFare.of(Spending.TAXI_RIDE))
    }

    @Test
    fun `any fare the taxi's meter can show is paid, and no other`() {
        val month = month()
        assertEquals(TaxiFare.BASE, Spending.TAXI_RIDE.price)
        assertEquals(TaxiFare.MOST, Spending.TAXI_RIDE.most)
        assertEquals(Event.Spent(Spending.TAXI_RIDE, 12), month.spend(Spending.TAXI_RIDE, 12))
        assertEquals(Event.Spent(Spending.TAXI_RIDE, 60), month.spend(Spending.TAXI_RIDE, 60))
        assertEquals(1_800 - 72, month.balance)
        for (wrong in listOf(0, 3, 61, -12)) {
            assertFalse(month.canSpend(Spending.TAXI_RIDE, wrong), "$wrong")
            assertFailsWith<IllegalArgumentException>("$wrong") { month.spend(Spending.TAXI_RIDE, wrong) }
        }
        assertEquals(1_800 - 72, month.balance)
    }

    @Test
    fun `a fare that would leave too little for September's living cost is refused, as a purchase is`() {
        // On the 2nd, 29 days of living cost are still to come: $1,160.
        val living = 29 * Month.DAILY_LIVING_COST
        val month = month(balance = living + 10)
        assertNull(month.spendBlockedBy(Spending.TAXI_RIDE, 10))
        assertEquals(SpendBlocked.LIVING_COST, month.spendBlockedBy(Spending.TAXI_RIDE, 11))
        // A shared ride's fare by distance, the same: a short ride can be paid, a long one cannot.
        assertNull(month.spendBlockedBy(Spending.SHARED_E_SCOOTER_RIDE, 10))
        assertEquals(SpendBlocked.LIVING_COST, month.spendBlockedBy(Spending.SHARED_E_SCOOTER_RIDE, 11))
        assertFalse(month.canSpend(Spending.SHARED_BIKE_RIDE, 15))
        assertFailsWith<IllegalArgumentException> { month.spend(Spending.SHARED_BIKE_RIDE, 15) }
        assertEquals(living + 10, month.balance)
        assertNull(month.spendBlockedBy(Spending.WATER_BUS_RIDE))
        month.spend(Spending.TAXI_RIDE, 7)
        assertEquals(SpendBlocked.LIVING_COST, month.spendBlockedBy(Spending.WATER_BUS_RIDE))
        assertEquals(living + 3, month.balance)
    }

    @Test
    fun `buying a bike takes its price off the balance and keeps the bike, and the day goes on`() {
        val month = month()
        val before = month.rest()
        val event = month.spend(Spending.BIKE, 350)
        assertEquals(Event.Spent(Spending.BIKE, 350), event)
        assertEquals(1_800 - 350, month.balance)
        assertEquals(setOf(Item.BIKE), month.owned)
        assertTrue(month.owns(Item.BIKE))
        assertFalse(month.owns(Item.E_SCOOTER))
        assertEquals(before, month.rest())
        assertEquals(Month.FIRST_DAY, month.day)
    }

    @Test
    fun `a ride's fare can be paid again and again, and keeps nothing`() {
        val month = month()
        repeat(5) {
            assertEquals(Event.Spent(Spending.SHARED_E_SCOOTER_RIDE, 4), month.spend(Spending.SHARED_E_SCOOTER_RIDE, 4))
        }
        month.spend(Spending.SHARED_BIKE_RIDE, 3)
        assertEquals(Event.Spent(Spending.SHARED_BIKE_RIDE, 1), month.spend(Spending.SHARED_BIKE_RIDE))
        assertEquals(1_800 - 5 * 4 - 3 - 1, month.balance)
        assertEquals(emptySet(), month.owned)
    }

    @Test
    fun `a bike already bought is not bought again`() {
        val month = month(balance = 3_000)
        month.spend(Spending.BIKE)
        assertEquals(SpendBlocked.ALREADY_OWNED, month.spendBlockedBy(Spending.BIKE))
        assertFalse(month.canSpend(Spending.BIKE, 350))
        assertFailsWith<IllegalArgumentException> { month.spend(Spending.BIKE) }
        assertEquals(3_000 - 350, month.balance)
        // The other one can still be bought.
        assertNull(month.spendBlockedBy(Spending.E_SCOOTER))
        month.spend(Spending.E_SCOOTER)
        assertEquals(setOf(Item.BIKE, Item.E_SCOOTER), month.owned)
    }

    @Test
    fun `a short balance refuses, and so does a spend that would leave too little for September's living cost`() {
        val month = month(balance = 549)
        assertEquals(SpendBlocked.NOT_ENOUGH_MONEY, month.spendBlockedBy(Spending.E_SCOOTER))
        assertFailsWith<IllegalArgumentException> { month.spend(Spending.E_SCOOTER) }
        assertEquals(549, month.balance)
        assertEquals(emptySet(), month.owned)
        // On the first day, 29 days of living cost must be left after it, today's included.
        val living = month.daysLeft * Month.DAILY_LIVING_COST
        assertEquals(29 * 40, living)
        val tight = month(balance = 550 + living - 1)
        assertEquals(SpendBlocked.LIVING_COST, tight.spendBlockedBy(Spending.E_SCOOTER))
        assertFalse(tight.canSpend(Spending.E_SCOOTER))
        assertFailsWith<IllegalArgumentException> { tight.spend(Spending.E_SCOOTER) }
        val exact = month(balance = 550 + living)
        exact.spend(Spending.E_SCOOTER)
        assertEquals(living, exact.balance)
        assertEquals(SpendBlocked.LIVING_COST, exact.spendBlockedBy(Spending.SHARED_BIKE_RIDE))
        // Resting to the end of the month, the living cost is paid to the last day, and nothing goes below zero.
        while (!exact.isOver) {
            exact.take(Action.REST)
            assertTrue(exact.balance >= 0, "${exact.balance} on day ${exact.day}")
        }
        assertEquals(0, exact.balance)
    }

    /**
     * A spend the month allows can still cost October: a month that would have ended with October's total in hand ends
     * without it after a bike, though September's living cost is paid to the end.
     */
    @Test
    fun `a spend that is allowed can turn a month that would be survived into one that cannot pay October`() {
        val plain = month(balance = 2_500)
        val spender = month(balance = 2_500)
        assertNull(spender.spendBlockedBy(Spending.BIKE))
        spender.spend(Spending.BIKE)
        while (!plain.isOver) {
            plain.take(Action.REST)
            spender.take(Action.REST)
        }
        assertEquals(Ending.SURVIVED, plain.ending)
        assertEquals(Ending.CANNOT_PAY_RENT, spender.ending)
        assertEquals(2_500 - 350 - 29 * Month.DAILY_LIVING_COST, spender.balance)
        assertTrue(spender.balance in 0 until room.totalMonthlyCost)
    }

    @Test
    fun `a spend for another amount than the price is refused`() {
        val month = month()
        assertFalse(month.canSpend(Spending.BIKE, 300))
        assertFailsWith<IllegalArgumentException> { month.spend(Spending.BIKE, 300) }
        assertFailsWith<IllegalArgumentException> { month.spend(Spending.SHARED_BIKE_RIDE, 0) }
        assertEquals(1_800, month.balance)
        assertEquals(emptySet(), month.owned)
    }

    @Test
    fun `nothing is spent once the month is over`() {
        val month = month()
        while (!month.isOver) month.take(Action.REST)
        for (kind in Spending.entries) {
            assertEquals(SpendBlocked.MONTH_OVER, month.spendBlockedBy(kind), "$kind")
            assertFalse(month.canSpend(kind), "$kind")
        }
        assertFailsWith<IllegalArgumentException> { month.spend(Spending.SHARED_BIKE_RIDE) }
    }

    @Test
    fun `money can be spent on an interview day, and the interview still takes the day`() {
        val month = Month(room, 1_800, object : Dice {
            override fun succeeds(chance: Double) = true
            override fun daysLater(range: IntRange) = range.first
        })
        month.take(Action.APPLY)
        while (!month.hasInterviewToday) month.take(Action.REST)
        assertNull(month.spendBlockedBy(Spending.BIKE))
        month.spend(Spending.BIKE)
        assertTrue(month.hasInterviewToday)
        assertFalse(month.canTake(Action.WORK))
        month.attendInterview()
        assertTrue(month.owns(Item.BIKE))
    }

    /**
     * Two months on the same luck, one spending between its days and one not: every day, interview and ending is the
     * same, so spending uses no luck and changes nothing but the balance.
     */
    @Test
    fun `spending between days changes the balance only, never the days or the luck`() {
        for (seed in 1..60) {
            val plain = FirstMinute(seed).startMonth(0, guess = 1_000)
            val spender = FirstMinute(seed).startMonth(0, guess = 1_000)
            var spent = 0
            var turn = 0
            while (!plain.isOver) {
                // A ride most days, and a bike once there is room for it.
                if (turn % 2 == 0 && spender.canSpend(Spending.SHARED_BIKE_RIDE)) {
                    spent += spender.spend(Spending.SHARED_BIKE_RIDE).amount
                }
                if (turn == 5 && spender.canSpend(Spending.BIKE)) spent += spender.spend(Spending.BIKE).amount
                val events = if (plain.hasInterviewToday) {
                    plain.attendInterview().also { assertEquals(it, spender.attendInterview()) }
                } else {
                    val action = listOf(Action.APPLY, Action.STUDY_ENGLISH, Action.PRACTISE_CODING, Action.REST)
                        .first { plain.canTake(it) }
                    plain.take(action).also { assertEquals(it, spender.take(action)) }
                }
                assertTrue(events.none { it is Event.Spent }, "a day's own events hold no spending")
                assertEquals(plain.rest(), spender.rest(), "seed $seed, day ${plain.day}")
                assertEquals(plain.balance - spent, spender.balance, "seed $seed, day ${plain.day}")
                turn += 1
            }
            if (plain.ending == Ending.OFFER) assertEquals(Ending.OFFER, spender.ending, "seed $seed")
        }
    }
}
