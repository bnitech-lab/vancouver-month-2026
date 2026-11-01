package vancouvermonth.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Luck the test decides: every roll succeeds or every roll fails, and things land [days] later. */
private class FixedDice(private val lucky: Boolean, private val days: Int = 2) : Dice {
    override fun succeeds(chance: Double): Boolean = lucky

    override fun daysLater(range: IntRange): Int = days.coerceIn(range)
}

class MonthTest {
    private val room = Room(rent = 1_020, zone = Zone.TWO, utilitiesIncluded = true, utilities = 0)

    private fun month(balance: Int = 1_800, lucky: Boolean = true) = Month(room, balance, FixedDice(lucky))

    /** Takes [actions] in order, all on the same day. */
    private fun Month.takeAll(vararg actions: Action) = actions.flatMap { take(it) }

    @Test
    fun `the costs, the day's stamina and its clock are the plan's`() {
        assertEquals(5, Month.MAX_ENERGY)
        assertEquals(listOf(3, 2, 2, 1, 0), Action.entries.map { Month.cost(it) })
        assertEquals(2, Month.INTERVIEW_COST)
        assertEquals(8, Month.DAY_STARTS_AT)
        assertEquals(23, Month.NIGHT_AT)
        val month = month()
        assertEquals(listOf(Month.MAX_ENERGY, 0, 8), listOf(month.energy, month.energySpent, month.hour))
        assertEquals(0.0, month.dayShare)
        assertFalse(month.isNight)
    }

    @Test
    fun `work pays and costs stamina and time, and the day goes on`() {
        val month = month()
        assertEquals(listOf(Event.Earned(Month.WORK_PAY)), month.take(Action.WORK))
        assertEquals(1_800 + Month.WORK_PAY, month.balance, "no living cost until the night's sleep")
        assertEquals(Month.MAX_ENERGY - Month.WORK_COST, month.energy)
        assertEquals(Month.FIRST_DAY, month.day)
        assertEquals(8 + 3 * Month.HOURS_PER_ENERGY, month.hour)
        assertEquals(0.6, month.dayShare)
    }

    @Test
    fun `a day holds several actions while stamina lasts, and then it is night`() {
        val month = month()
        val hours = mutableListOf(month.hour)
        for (action in listOf(Action.STUDY_ENGLISH, Action.APPLY, Action.PRACTISE_CODING)) {
            month.take(action)
            hours += month.hour
        }
        assertEquals(listOf(8, 14, 17, 23), hours)
        assertEquals(Month.FIRST_DAY, month.day)
        assertEquals(0, month.energy)
        assertTrue(month.isNight)
        assertEquals(1.0, month.dayShare)
        assertEquals(1_800, month.balance)
    }

    @Test
    fun `sleep ends the day, the living cost comes off, and the next morning starts with full stamina`() {
        val month = month()
        month.takeAll(Action.WORK, Action.STUDY_ENGLISH)
        val night = month.take(Action.REST)
        assertEquals(listOf(Event.Rested(Month.MAX_ENERGY), Event.LivingCost(Month.DAILY_LIVING_COST)), night)
        assertEquals(Month.FIRST_DAY + 1, month.day)
        assertEquals(Month.MAX_ENERGY, month.energy)
        assertEquals(Month.DAY_STARTS_AT, month.hour)
        assertEquals(1_800 + Month.WORK_PAY - Month.DAILY_LIVING_COST, month.balance)
    }

    @Test
    fun `sleeping early with stamina left is allowed and costs nothing more`() {
        val early = month()
        early.take(Action.STUDY_ENGLISH)
        assertTrue(early.canTake(Action.REST))
        early.take(Action.REST)
        val late = month()
        late.takeAll(Action.STUDY_ENGLISH, Action.PRACTISE_CODING, Action.APPLY)
        late.take(Action.REST)
        for (month in listOf(early, late)) {
            assertEquals(Month.FIRST_DAY + 1, month.day)
            assertEquals(Month.MAX_ENERGY, month.energy)
            assertEquals(1_800 - Month.DAILY_LIVING_COST, month.balance)
        }
    }

    @Test
    fun `study raises a skill a level every few studies, and stops at the top`() {
        val month = month(balance = 5_000)
        val events = mutableListOf<Event>()
        while (!month.isOver) {
            val study = month.canTake(Action.STUDY_ENGLISH)
            if (study) events += month.take(Action.STUDY_ENGLISH) else month.take(Action.REST)
        }
        val ups = events.filterIsInstance<Event.SkillUp>()
        assertEquals((Month.START_ENGLISH + 1..Month.MAX_SKILL).map { Event.SkillUp(Skill.ENGLISH, it) }, ups)
        // Before each level, the studies toward it count down what is left.
        val toLevelTwo = events.take(Month.STEPS_PER_LEVEL)
        assertEquals(
            (Month.STEPS_PER_LEVEL - 1 downTo 1).map { Event.Studied(Skill.ENGLISH, Month.START_ENGLISH, it) } +
                Event.SkillUp(Skill.ENGLISH, Month.START_ENGLISH + 1),
            toLevelTwo,
        )
        assertEquals(Event.Studied(Skill.ENGLISH, Month.MAX_SKILL, 0), events.last(), "the month should outlast it")
        assertEquals(Month.MAX_SKILL, month.english)
        assertEquals(0, month.stepsLeft(Skill.ENGLISH))
    }

    @Test
    fun `progress toward a level is kept across days`() {
        val month = month()
        month.take(Action.PRACTISE_CODING)
        month.take(Action.REST)
        assertEquals(1, month.codingProgress)
        assertEquals(Month.STEPS_PER_LEVEL - 1, month.stepsLeft(Skill.CODING))
        repeat(Month.STEPS_PER_LEVEL - 1) {
            if (!month.canTake(Action.PRACTISE_CODING)) month.take(Action.REST)
            month.take(Action.PRACTISE_CODING)
        }
        assertEquals(Month.START_CODING + 1, month.coding)
        assertEquals(0, month.codingProgress)
        assertEquals(Month.START_ENGLISH, month.english)
    }

    @Test
    fun `without stamina it is night, and only sleep is allowed`() {
        val month = month()
        month.takeAll(Action.WORK, Action.STUDY_ENGLISH)
        assertEquals(0, month.energy)
        for (action in Action.entries) {
            assertEquals(if (action == Action.REST) null else Blocked.NO_ENERGY, month.blockedBy(action), "$action")
        }
        assertFailsWith<IllegalArgumentException> { month.take(Action.WORK) }
    }

    @Test
    fun `an action costing more than the stamina left is blocked as too little, and a cheaper one is not`() {
        val month = month()
        month.takeAll(Action.STUDY_ENGLISH, Action.PRACTISE_CODING)
        assertEquals(1, month.energy)
        assertEquals(Blocked.LOW_ENERGY, month.blockedBy(Action.WORK))
        assertEquals(Blocked.LOW_ENERGY, month.blockedBy(Action.STUDY_ENGLISH))
        assertEquals(Blocked.LOW_ENERGY, month.blockedBy(Action.PRACTISE_CODING))
        assertNull(month.blockedBy(Action.APPLY))
        assertNull(month.blockedBy(Action.REST))
    }

    @Test
    fun `one application a day`() {
        val month = month(lucky = false)
        month.take(Action.APPLY)
        assertEquals(Blocked.APPLIED_TODAY, month.blockedBy(Action.APPLY))
        month.take(Action.REST)
        assertNull(month.blockedBy(Action.APPLY))
    }

    @Test
    fun `a booked interview blocks only another application`() {
        val month = month(lucky = true)
        month.take(Action.APPLY)
        assertEquals(Blocked.INTERVIEW_BOOKED, month.blockedBy(Action.APPLY))
        for (action in Action.entries - Action.APPLY) assertNull(month.blockedBy(action), "$action")
    }

    @Test
    fun `late in the month applications are blocked as closed`() {
        val month = month(balance = 5_000, lucky = false)
        while (month.canStillApplyThisMonth) month.take(Action.REST)
        assertEquals(Blocked.APPLICATIONS_CLOSED, month.blockedBy(Action.APPLY))
        for (action in Action.entries - Action.APPLY) assertNull(month.blockedBy(action), "$action")
    }

    @Test
    fun `on an interview day the interview comes first, and then the rest of the day is free`() {
        val month = month(lucky = true)
        month.take(Action.APPLY)
        month.take(Action.REST)
        month.take(Action.REST)
        assertTrue(month.hasInterviewToday)
        for (action in Action.entries) assertEquals(Blocked.INTERVIEW_TODAY, month.blockedBy(action), "$action")
        month.attendInterview()
        assertFalse(month.hasInterviewToday)
        assertEquals(Month.MAX_ENERGY - Month.INTERVIEW_COST, month.energy)
        assertEquals(8 + Month.INTERVIEW_COST * Month.HOURS_PER_ENERGY, month.hour)
        assertEquals(Month.FIRST_DAY + 2, month.day, "the day goes on after the interview")
        assertTrue(month.canTake(Action.WORK))
        assertTrue(month.canTake(Action.REST))
        month.take(Action.WORK)
        assertTrue(month.isNight)
    }

    @Test
    fun `a finished month blocks every action`() {
        val month = month(balance = 5_000)
        while (!month.isOver) month.take(Action.REST)
        for (action in Action.entries) assertEquals(Blocked.MONTH_OVER, month.blockedBy(action), "$action")
    }

    @Test
    fun `a lucky application books a phone screen and blocks another application`() {
        val month = month(lucky = true)
        val events = month.take(Action.APPLY)
        val booked = Interview(InterviewStage.PHONE_SCREEN, Month.FIRST_DAY + 2)
        assertEquals(Event.Applied(booked), events.first())
        assertEquals(booked, month.interview)
        assertFalse(month.canTake(Action.APPLY))
    }

    @Test
    fun `an unlucky application books nothing, and the next day can try again`() {
        val month = month(lucky = false)
        assertEquals(listOf(Event.Applied(null)), month.take(Action.APPLY))
        assertNull(month.interview)
        month.take(Action.REST)
        assertTrue(month.canTake(Action.APPLY))
    }

    @Test
    fun `passing both interviews ends the month with an offer`() {
        val month = month(lucky = true)
        month.take(Action.APPLY)
        month.take(Action.REST)
        month.take(Action.REST)
        assertTrue(month.hasInterviewToday)
        assertFalse(month.canTake(Action.REST))

        val held = month.attendInterview()
        val first = held.single() as Event.InterviewHeld
        assertTrue(first.passed)
        assertEquals(InterviewStage.TECHNICAL, first.next?.stage)

        month.take(Action.REST)
        month.take(Action.REST)
        val balanceBefore = month.balance
        val second = month.attendInterview()
        assertEquals(
            listOf(
                Event.InterviewHeld(InterviewStage.TECHNICAL, passed = true, next = null),
                Event.LivingCost(Month.DAILY_LIVING_COST),
            ),
            second,
        )
        assertEquals(Ending.OFFER, month.ending)
        assertEquals(balanceBefore - Month.DAILY_LIVING_COST, month.balance)
    }

    @Test
    fun `applications stop once both interviews could not finish in September`() {
        val month = Month(room, 5_000, FixedDice(lucky = true, days = 4))
        while (month.day < Month.LAST_PHONE_SCREEN_DAY - Month.PHONE_SCREEN_DELAY.first) month.take(Action.REST)
        assertEquals(27, month.day)
        assertTrue(month.canTake(Action.APPLY))

        month.take(Action.APPLY)
        assertEquals(Interview(InterviewStage.PHONE_SCREEN, Month.LAST_PHONE_SCREEN_DAY), month.interview)
        month.take(Action.REST)
        assertFalse(month.canStillApplyThisMonth)

        month.take(Action.REST)
        month.attendInterview()
        assertEquals(Interview(InterviewStage.TECHNICAL, Month.LAST_DAY), month.interview)
        month.take(Action.REST)
        month.attendInterview()
        assertEquals(Ending.OFFER, month.ending)
    }

    @Test
    fun `no interview is ever booked after the 30th`() {
        for (seed in 1..500) {
            val game = FirstMinute(seed)
            val month = game.startMonth(0, guess = 1_000)
            while (!month.isOver) {
                if (month.hasInterviewToday) {
                    month.attendInterview()
                } else {
                    month.take(if (month.canTake(Action.APPLY)) Action.APPLY else Action.REST)
                }
                month.interview?.let { assertTrue(it.day <= Month.LAST_DAY, "seed $seed: $it") }
            }
        }
    }

    @Test
    fun `a failed interview ends the process and lets the player apply again`() {
        val month = Month(room, 1_800, object : Dice {
            var rolls = 0

            override fun succeeds(chance: Double): Boolean = rolls++ == 0 // the application works, the interview fails

            override fun daysLater(range: IntRange): Int = range.first
        })
        month.take(Action.APPLY)
        month.take(Action.REST)
        month.take(Action.REST)
        val held = month.attendInterview().first() as Event.InterviewHeld
        assertFalse(held.passed)
        assertNull(month.interview)
        assertTrue(month.canTake(Action.APPLY))
    }

    @Test
    fun `the month ends on the 30th with or without next month's total`() {
        val rich = month(balance = 5_000)
        while (!rich.isOver) rich.take(Action.REST)
        assertEquals(Ending.SURVIVED, rich.ending)
        assertEquals(Month.LAST_DAY, rich.day)

        val tight = month(balance = Month.DAILY_LIVING_COST * 29 + 100)
        while (!tight.isOver) tight.take(Action.REST)
        assertEquals(Ending.CANNOT_PAY_RENT, tight.ending)
    }

    @Test
    fun `the forecast takes the living cost of every day left, and is tight below October's total`() {
        // October's total is 1,020 + 145 = 1,165, and the 29 days from the 2nd cost 1,160 to live.
        val month = month(balance = 2_400)
        assertEquals(2_400 - 1_160, month.balanceForecast)
        assertFalse(month.moneyTight)
        month.take(Action.REST)
        assertEquals(2_400 - 1_160, month.balanceForecast, "a day of rest was already in the forecast")
        assertTrue(month(balance = 2_324).moneyTight)
        assertFalse(month(balance = 2_325).moneyTight)
        month.take(Action.WORK)
        assertEquals(2_400 - 1_160 + Month.WORK_PAY, month.balanceForecast)
    }

    @Test
    fun `what is left after any first room and the most a guess can lose covers the month's living cost`() {
        val livingCost = Month.DAILY_LIVING_COST * (Month.LAST_DAY - Month.FIRST_DAY + 1)
        for (seed in 1..2_000) {
            for (room in RoomDealer.deal(seed)) {
                val left = FirstMinute.STARTING_BALANCE - room.totalMonthlyCost - FirstMinute.GUESS_PENALTY
                assertTrue(left >= livingCost, "seed $seed: $room")
            }
        }
        // The dearest room the dealer can deal, in zone 1 with the highest rent and utilities, leaves enough too.
        val dearest = RoomDealer.BASE_RENT - RoomDealer.RENT_DROP_PER_ZONE + RoomDealer.RENT_SPREAD +
            RoomDealer.UTILITIES_RANGE.last + Zone.ONE.monthlyPass
        assertTrue(Zone.entries.all { zone ->
            RoomDealer.BASE_RENT - zone.number * RoomDealer.RENT_DROP_PER_ZONE + RoomDealer.RENT_SPREAD +
                RoomDealer.UTILITIES_RANGE.last + zone.monthlyPass <= dearest
        })
        assertTrue(FirstMinute.STARTING_BALANCE - dearest - FirstMinute.GUESS_PENALTY >= livingCost, "dearest $dearest")
    }

    @Test
    fun `chances never go above the cap`() {
        val month = month(balance = 50_000)
        while (month.coding < Month.MAX_SKILL || month.english < Month.MAX_SKILL) {
            when {
                month.energy < Month.STUDY_COST -> month.take(Action.REST)
                month.coding < Month.MAX_SKILL -> month.take(Action.PRACTISE_CODING)
                else -> month.take(Action.STUDY_ENGLISH)
            }
            if (month.isOver) break
        }
        assertTrue(month.applyChance <= Month.MAX_CHANCE)
        for (stage in InterviewStage.entries) assertTrue(month.passChance(stage) <= Month.MAX_CHANCE)
    }
}
