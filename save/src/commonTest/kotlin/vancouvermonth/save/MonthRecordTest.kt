package vancouvermonth.save

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vancouvermonth.core.Action
import vancouvermonth.core.Ending
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Interview
import vancouvermonth.core.InterviewStage
import vancouvermonth.core.Item
import vancouvermonth.core.Month
import vancouvermonth.core.Skill
import vancouvermonth.core.Spending
import vancouvermonth.core.TodaysRun

class MonthRecordTest {
    private val english = Choice.Take(Action.STUDY_ENGLISH)
    private val coding = Choice.Take(Action.PRACTISE_CODING)
    private val rest = Choice.Take(Action.REST)
    private val apply = Choice.Take(Action.APPLY)
    private val work = Choice.Take(Action.WORK)
    private val bike = Spend(Spending.BIKE, 350)
    private val scooter = Spend(Spending.E_SCOOTER, 550)
    private val sharedBike = Spend(Spending.SHARED_BIKE_RIDE, 3)
    private val sharedScooter = Spend(Spending.SHARED_E_SCOOTER_RIDE, 4)
    private val waterBus = Spend(Spending.WATER_BUS_RIDE, 4)
    private fun taxi(fare: Int) = Spend(Spending.TAXI_RIDE, fare)

    /** Everything a player can see of a month, and what decides how it goes on. */
    private fun Month.state(): List<Any?> =
        listOf(room, day, balance, english, coding, energy, interview, ending, owned)

    @Test
    fun `a record is one short line, and reads back as the same record`() {
        val days = listOf(english, coding, rest, apply, work, Choice.AttendInterview)
        val record = MonthRecord(1011, null, 2, 1165, days)
        assertEquals("3|1011||2|1165|ECRAWI", record.encode())
        assertEquals(record, MonthRecord.decode(record.encode()))
        assertEquals(days, record.days)

        val today = TodaysRun(2026, 10, 30)
        val todays = MonthRecord(today.seed, today, 0, 0)
        assertEquals("3|${today.seed}|2026-10-30|0|0|", todays.encode())
        assertEquals(todays, MonthRecord.decode(todays.encode()))
    }

    @Test
    fun `money spent between days is kept in order, a small letter and its amount each`() {
        val moves = listOf(english, sharedScooter, work, bike, scooter, coding, sharedBike)
        val record = MonthRecord(1011, null, 2, 1165, moves)
        assertEquals("3|1011||2|1165|Eq4Wb350k550Cs3", record.encode())
        assertEquals(record, MonthRecord.decode(record.encode()))
        assertEquals(listOf(english, work, coding), record.days)
        assertEquals(record, MonthRecord(1011, null, 2, 1165).after(english).after(sharedScooter).after(work)
            .after(bike).after(scooter).after(coding).after(sharedBike))
    }

    @Test
    fun `a line this version did not write is not a record`() {
        val good = "3|1011||2|1165|ECRA"
        assertNotNull(MonthRecord.decode(good))
        assertNotNull(MonthRecord.decode("3|1011||2|1165|Eb350Rq4A"))
        val run = TodaysRun(2026, 1, 5)
        assertNotNull(MonthRecord.decode("3|${run.seed}|2026-01-05|2|1165|ECRA"))
        val bad = listOf(
            null,
            "",
            "garbage",
            "0|1011||2|1165|ECRA",
            // The first version's lines, kept before money could be spent, and the second's, kept before the guess
            // moved money, are dropped.
            "1|1011||2|1165|ECRA",
            "2|1011||2|1165|ECRA",
            "4|1011||2|1165|ECRA",
            "3|1011||2|1165",
            "3|1011||2|1165|ECRA|",
            "3|x||2|1165|ECRA",
            "3|1011||two|1165|ECRA",
            "3|1011||2||ECRA",
            "3|1011||2|1165|ECRZ",
            "3|1011||2|1165|ecra",
            "3|${run.seed}|2026-02-30|2|1165|ECRA",
            "3|${run.seed}|someday|2|1165|ECRA",
            "3|${run.seed + 1}|2026-01-05|2|1165|ECRA",
            // A spend needs its amount, in plain digits, after a letter this version knows.
            "3|1011||2|1165|EbRA",
            "3|1011||2|1165|ERAb",
            "3|1011||2|1165|Ex4RA",
            "3|1011||2|1165|EB350RA",
            "3|1011||2|1165|Eb-350RA",
            "3|1011||2|1165|Eb99999999999RA",
            // Only the exact line encode writes: no padding, no sign, no unpadded date, no space.
            " 3|1011||2|1165|ECRA",
            "3|1011||02|1165|ECRA",
            "3|1011||2|+1165|ECRA",
            "3|${run.seed}|2026-1-5|2|1165|ECRA",
            "3|1011||2|1165|ECRA ",
            "3|1011||2|1165|Eb0350RA",
            "3|1011||2|1165|Eb+350RA",
        )
        for (text in bad) assertNull(MonthRecord.decode(text), "Read as a record: $text")
    }

    @Test
    fun `water bus and taxi fares are kept as w and t with their amounts, and replay off the balance alone`() {
        val moves = listOf(english, waterBus, taxi(12), work, taxi(4), waterBus, coding, taxi(60))
        val record = MonthRecord(1011, null, 2, 1165, moves)
        assertEquals("3|1011||2|1165|Ew4t12Wt4w4Ct60", record.encode())
        assertEquals(record, MonthRecord.decode(record.encode()))
        assertEquals(listOf(english, work, coding), record.days)
        val replayed = assertNotNull(record.replay())
        val plain = assertNotNull(MonthRecord(1011, null, 2, 1165, listOf(english, work, coding)).replay())
        assertEquals(plain.month.balance - 4 - 12 - 4 - 4 - 60, replayed.month.balance)
        assertEquals(plain.month.day, replayed.month.day)
        assertEquals(listOf(Event.Spent(Spending.TAXI_RIDE, 60)), replayed.spentToday)
        assertEquals(
            listOf(Event.Spent(Spending.TAXI_RIDE, 4), Event.Spent(Spending.WATER_BUS_RIDE, 4)),
            replayed.lastEvents.filterIsInstance<Event.Spent>(),
        )
        // A line with no water bus or taxi still reads, as their letters needed no new version.
        assertNotNull(MonthRecord.decode("3|1011||2|1165|Eq4Wb350Cs3")?.replay())
    }

    @Test
    fun `shared rides keep their fares by distance as s and q, and a line kept at the old flat fares still replays`() {
        val moves = listOf(english, Spend(Spending.SHARED_BIKE_RIDE, 1), work, Spend(Spending.SHARED_BIKE_RIDE, 15))
        val record = MonthRecord(1011, null, 2, 1165, moves + Spend(Spending.SHARED_E_SCOOTER_RIDE, 20))
        assertEquals("3|1011||2|1165|Es1Ws15q20", record.encode())
        assertEquals(record, MonthRecord.decode(record.encode()))
        val replayed = assertNotNull(record.replay())
        val plain = assertNotNull(MonthRecord(1011, null, 2, 1165, listOf(english, work)).replay())
        assertEquals(plain.month.balance - 1 - 15 - 20, replayed.month.balance)
        assertEquals(
            listOf(Event.Spent(Spending.SHARED_BIKE_RIDE, 15), Event.Spent(Spending.SHARED_E_SCOOTER_RIDE, 20)),
            replayed.spentToday,
        )
        // Kept when a shared bike was $3 and an e-scooter $4 a ride, whatever the way: both are fares the meters show.
        val old = assertNotNull(MonthRecord.decode("3|1011||2|1165|Eq4Wb350Cs3")?.replay())
        assertEquals(plain.month.balance - 4 - 350 - 3 - Month.DAILY_LIVING_COST, old.month.balance)
        for (past in listOf(Spend(Spending.SHARED_BIKE_RIDE, 16), Spend(Spending.SHARED_E_SCOOTER_RIDE, 21))) {
            assertNull(MonthRecord(1011, null, 2, 1165, listOf(english, past)).replay(), "$past")
        }
    }

    @Test
    fun `a tampered fare does not replay, and a fare with no amount is not read`() {
        val tampered = mapOf(
            "a water bus for $5" to Spend(Spending.WATER_BUS_RIDE, 5),
            "a water bus for nothing" to Spend(Spending.WATER_BUS_RIDE, 0),
            "a taxi under its base fare" to taxi(3),
            "a taxi past what its meter shows" to taxi(61),
            "a taxi for nothing" to taxi(0),
        )
        for ((why, spend) in tampered) {
            val record = MonthRecord(1011, null, 2, 1165, listOf(english, spend, work))
            val line = record.encode()
            assertEquals(record, MonthRecord.decode(line), why)
            assertNull(record.replay(), "Replayed: $why ($line)")
        }
        for (text in listOf("3|1011||2|1165|EwW", "3|1011||2|1165|EtW", "3|1011||2|1165|Et012W", "3|1011||2|1165|ET12W")) {
            assertNull(MonthRecord.decode(text), "Read as a record: $text")
        }
    }

    @Test
    fun `a fresh record replays as a fresh start, with the same rooms and the same reveal`() {
        val replayed = assertNotNull(MonthRecord(1011, null, 2, 1165).replay())
        val fresh = FirstMinute(1011)
        assertEquals(fresh.rooms, replayed.firstMinute.rooms)
        assertEquals(fresh.reveal(2, 1165), replayed.reveal)
        assertEquals(fresh.startMonth(2, 1165).state(), replayed.month.state())
        assertEquals(listOf(Event.Guessed(0, FirstMinute.GUESS_REWARD)), replayed.month.opening)
        assertEquals(Month.FIRST_DAY, replayed.month.day)
        assertTrue(replayed.lastEvents.isEmpty())
    }

    @Test
    fun `a record the rules refuse does not replay`() {
        val today = TodaysRun(2026, 10, 30)
        val otherSeed = if (today.seed == 1011) 1012 else 1011
        val refused = mapOf(
            "today's run under another seed" to MonthRecord(otherSeed, today, 0, 1000),
            "a fourth room" to MonthRecord(1011, null, 3, 1000),
            "a negative guess" to MonthRecord(1011, null, 0, -1),
            "an interview on a day without one" to MonthRecord(1011, null, 0, 1000, listOf(Choice.AttendInterview)),
            "a sixth day of study with five energy" to MonthRecord(1011, null, 0, 1000, List(6) { english }),
            "a day after the month ended" to MonthRecord(1011, null, 0, 1000, List(Month.LAST_DAY) { rest }),
            "a month that has ended, with nothing left to continue" to
                MonthRecord(1011, null, 0, 1000, List(Month.LAST_DAY - 1) { rest }),
            "a bike bought twice" to MonthRecord(1011, null, 0, 1000, listOf(bike, english, bike)),
            "a bike for less than its price" to MonthRecord(1011, null, 0, 1000, listOf(Spend(Spending.BIKE, 300))),
            "a ride for nothing" to MonthRecord(1011, null, 0, 1000, listOf(Spend(Spending.SHARED_BIKE_RIDE, 0))),
            "an e-scooter with too little money left" to
                MonthRecord(1011, null, 0, 1000, List(27) { rest } + bike + scooter),
            "a ride after the month ended" to
                MonthRecord(1011, null, 0, 1000, List(Month.LAST_DAY - 1) { rest } + sharedBike),
        )
        for ((why, record) in refused) assertNull(record.replay(), "Replayed: $why")
        // One day short of the end still replays, on the 30th.
        val lastDay = assertNotNull(MonthRecord(1011, null, 0, 1000, List(Month.LAST_DAY - 2) { rest }).replay())
        assertEquals(Month.LAST_DAY, lastDay.month.day)
        // With the bike bought, what is left still buys a ride or two.
        val short = assertNotNull(MonthRecord(1011, null, 0, 1000, List(27) { rest } + bike + sharedBike).replay())
        assertTrue(short.month.balance < Spending.E_SCOOTER.price, "${short.month.balance}")
    }

    @Test
    fun `an application while an interview is booked does not replay`() {
        // Find a seed whose first application books a phone screen, then apply again the next day.
        val seed = (1..500).first { seed ->
            val month = FirstMinute(seed).startMonth(0, 1000)
            month.take(Action.APPLY)
            month.interview != null && month.interview!!.day > month.day
        }
        assertNotNull(MonthRecord(seed, null, 0, 1000, listOf(apply)).replay())
        assertNull(MonthRecord(seed, null, 0, 1000, listOf(apply, apply)).replay())
    }

    /**
     * Plays many months with random legal choices, and after every day checks that the record written so far reads
     * back and replays to exactly the month played live. At one random day of each month, the replayed month is then
     * played on beside the live one to the end, so the luck still to come (which nothing on screen shows) matches too.
     */
    @Test
    fun `replaying a record gives exactly the month played live, interviews and endings included`() {
        var interviews = 0
        var offers = 0
        var spends = 0
        val endings = mutableSetOf<Ending>()
        for (run in 1..400) {
            val random = Random(run)
            val today = if (run % 5 == 0) TodaysRun(2026, 1 + run % 12, 1 + run % 28) else null
            val seed = today?.seed ?: run
            val firstMinute = FirstMinute(seed)
            val room = random.nextInt(firstMinute.rooms.size)
            val guess = random.nextInt(0, 3_000)
            val live = firstMinute.startMonth(room, guess)
            var record = MonthRecord(seed, today, room, guess)
            val branchAt = random.nextInt(0, Month.LAST_DAY - Month.FIRST_DAY + 1)
            var branch: Month? = null
            var spentToday = emptyList<Event>()
            while (true) {
                val read = assertNotNull(MonthRecord.decode(record.encode()), "Run $run: ${record.encode()}")
                assertEquals(record, read)
                val replayed = read.replay()
                if (live.isOver) {
                    assertNull(replayed, "Run $run replayed a month that had ended")
                    break
                }
                assertNotNull(replayed, "Run $run did not replay: ${record.encode()}")
                assertEquals(live.state(), replayed.month.state(), "Run $run on day ${live.day}")
                assertEquals(firstMinute.reveal(room, guess), replayed.reveal)
                assertEquals(live.opening, replayed.month.opening, "Run $run")
                assertEquals(spentToday, replayed.spentToday, "Run $run on day ${live.day}")
                if (record.days.size == branchAt && branch == null) branch = replayed.month

                // Now and then money is spent first, as the town lets it be: a ride, or something to keep.
                val spend = randomSpend(live, random)
                if (spend != null) {
                    val spent = live.spend(spend.kind, spend.amount)
                    branch?.let { assertEquals(spent, it.spend(spend.kind, spend.amount), "Run $run, the branch") }
                    spentToday = spentToday + spent
                    spends += 1
                    record = record.after(spend)
                    continue
                }
                val choice = randomChoice(live, random)
                if (choice == Choice.AttendInterview) interviews += 1
                val events = live.play(choice)
                branch?.let { assertEquals(events, it.play(choice), "Run $run, the branch on day ${live.day}") }
                record = record.after(choice)
                if (!live.isOver) assertEquals(spentToday + events, assertNotNull(record.replay()).lastEvents)
                spentToday = emptyList()
            }
            branch?.let { assertEquals(live.state(), it.state(), "Run $run, the branch at the end") }
            val ending = assertNotNull(live.ending)
            endings += ending
            if (ending == Ending.OFFER) offers += 1
        }
        assertTrue(interviews > 50, "Only $interviews interviews")
        assertTrue(offers > 5, "Only $offers offers")
        assertTrue(spends > 1_000, "Only $spends spends")
        assertEquals(Ending.entries.toSet(), endings)
    }

    /**
     * Money to spend before today's day, about one time in four: mostly a ride's fare (a taxi's or a shared one's
     * anything its meter can show), sometimes something to keep.
     */
    private fun randomSpend(month: Month, random: Random): Spend? {
        if (random.nextInt(4) != 0) return null
        val kind = when (random.nextInt(10)) {
            0 -> Spending.BIKE
            1 -> Spending.E_SCOOTER
            in 2..4 -> Spending.SHARED_BIKE_RIDE
            in 5..6 -> Spending.SHARED_E_SCOOTER_RIDE
            7 -> Spending.WATER_BUS_RIDE
            else -> Spending.TAXI_RIDE
        }
        val amount = if (kind.most > kind.price) random.nextInt(kind.price, kind.most + 1) else kind.price
        return Spend(kind, amount).takeIf { month.canSpend(kind, amount) }
    }

    /** A legal choice for today, leaning on applications so that many months have interviews. */
    private fun randomChoice(month: Month, random: Random): Choice {
        if (month.hasInterviewToday) return Choice.AttendInterview
        if (month.canTake(Action.APPLY) && random.nextInt(3) == 0) return apply
        val legal = Action.entries.filter { month.canTake(it) }
        return Choice.Take(legal[random.nextInt(legal.size)])
    }

    /**
     * Lines kept by this version, with the month each one replays to, written down by hand. If core's rules change
     * (a price, a chance, a delay, the rooms, the guess's money, or Kotlin's Random), a kept line would replay to another
     * month than the player left, so this fails and asks for a new [MonthRecord.VERSION], which drops the old lines
     * instead.
     */
    @Test
    fun `kept lines replay to the very months they were kept from`() {
        val changed = "core's rules changed: bump MonthRecord.VERSION, then update these numbers"
        val phoneScreen = Interview(InterviewStage.PHONE_SCREEN, 11)
        val technical = Interview(InterviewStage.TECHNICAL, 13)
        // The guess's money first, then the month: day, balance, English, coding, energy, interview, total, last events.
        val golden = listOf(
            // The cheapest room, guessed exactly (+$100). Study, work, code.
            "3|1011||2|1165|EWC" to listOf(
                100, 5, 1_925, 2, 4, 2, null, 1_165,
                listOf(Event.SkillUp(Skill.CODING, 4), Event.LivingCost(40)),
            ),
            // A dearer room ($1,345), guessed $45 under (+$55). The same three days.
            "3|1011||1|1300|EWC" to listOf(
                55, 5, 1_700, 2, 4, 2, null, 1_345,
                listOf(Event.SkillUp(Skill.CODING, 4), Event.LivingCost(40)),
            ),
            // The cheapest room, guessed $250 over (-$150). The same three days.
            "3|1011||2|1415|EWC" to listOf(
                -150, 5, 1_675, 2, 4, 2, null, 1_165,
                listOf(Event.SkillUp(Skill.CODING, 4), Event.LivingCost(40)),
            ),
            // Four days of English, two of rest, and an application that books a phone screen.
            "3|1002||1|1325|EEEERRA" to listOf(
                100, 9, 1_495, 5, 3, 4, phoneScreen, 1_325,
                listOf(Event.Applied(phoneScreen), Event.LivingCost(40)),
            ),
            // Rest until the phone screen, pass it, and a technical interview is booked.
            "3|1002||1|1325|EEEERRARRI" to listOf(
                100, 12, 1_375, 5, 3, 5, technical, 1_325,
                listOf(Event.InterviewHeld(InterviewStage.PHONE_SCREEN, true, technical), Event.LivingCost(40)),
            ),
            // Rest a day and fail the technical interview.
            "3|1002||1|1325|EEEERRARRIRI" to listOf(
                100, 14, 1_295, 5, 3, 5, null, 1_325,
                listOf(Event.InterviewHeld(InterviewStage.TECHNICAL, false, null), Event.LivingCost(40)),
            ),
            // Study; a shared e-scooter, then work; a bike, then code; and a shared bike today, before today's day.
            "3|1011||2|1165|Eq4Wb350Cs3" to listOf(
                100, 5, 1_925 - 4 - 350 - 3, 2, 4, 2, null, 1_165,
                listOf(Event.Spent(Spending.BIKE, 350), Event.SkillUp(Skill.CODING, 4), Event.LivingCost(40)),
            ),
        )
        for ((line, expected) in golden) {
            val replayed = assertNotNull(MonthRecord.decode(line)?.replay(), "$changed ($line no longer replays)")
            val month = replayed.month
            val actual = listOf(
                replayed.reveal.guessMoney, month.day, month.balance, month.english, month.coding, month.energy,
                month.interview, month.room.totalMonthlyCost, replayed.lastEvents,
            )
            assertEquals(expected, actual, "$changed ($line)")
            assertEquals(listOf(Event.Guessed(replayed.reveal.guessGap, expected[0] as Int)), month.opening, line)
        }
        val spender = assertNotNull(MonthRecord.decode("3|1011||2|1165|Eq4Wb350Cs3")?.replay())
        assertEquals(setOf(Item.BIKE), spender.month.owned)
        assertEquals(listOf(Event.Spent(Spending.SHARED_BIKE_RIDE, 3)), spender.spentToday)
    }

    @Test
    fun `a record replays the guess's money once, a reward or a penalty, exactly as live play`() {
        val game = FirstMinute(1011)
        val room = game.cheapestRoomIndex
        val total = game.lowestTotal
        val days = listOf(english, work, sharedBike, rest, coding)
        for (guess in listOf(total, total - 20, total + 100, total + 250, total + 900, 0)) {
            val live = game.startMonth(room, guess)
            for (move in days) {
                if (move is Choice) live.play(move) else (move as Spend).let { live.spend(it.kind, it.amount) }
            }
            val replayed = assertNotNull(MonthRecord(1011, null, room, guess, days).replay(), "guess $guess")
            assertEquals(live.state(), replayed.month.state(), "guess $guess")
            assertEquals(live.opening, replayed.month.opening, "guess $guess")
            val money = FirstMinute.guessMoney(kotlin.math.abs(guess - total))
            val start = FirstMinute.STARTING_BALANCE - total + money
            assertEquals(start - 40 + (Month.WORK_PAY - 40) - 3 - 40 - 40, replayed.month.balance, "guess $guess")
        }
    }

    @Test
    fun `a line kept before the guess moved money is not read, so it never continues into another month`() {
        // Version 2 started this month at $1,835; version 3 starts it at $1,935.
        assertNull(MonthRecord.decode("2|1011||2|1165|EWC"))
        assertNull(MonthRecord.decode("2|1011||2|1165|Eq4Wb350Cs3"))
        assertEquals(3, MonthRecord.VERSION)
        assertEquals(1_935, assertNotNull(MonthRecord.decode("3|1011||2|1165|")?.replay()).month.balance)
    }

    @Test
    fun `the last day's events come back for the screen to show`() {
        val record = MonthRecord(1011, null, 0, 1000, listOf(work))
        val events = assertNotNull(record.replay()).lastEvents
        assertEquals(listOf(Event.Earned(Month.WORK_PAY), Event.LivingCost(Month.DAILY_LIVING_COST)), events)
        // Money spent on that day comes first; money spent today is not yet part of a day's log.
        val spent = assertNotNull(MonthRecord(1011, null, 0, 1000, listOf(sharedBike, work, sharedScooter)).replay())
        assertEquals(
            listOf(Event.Spent(Spending.SHARED_BIKE_RIDE, 3), Event.Earned(Month.WORK_PAY), Event.LivingCost(40)),
            spent.lastEvents,
        )
        assertEquals(listOf(Event.Spent(Spending.SHARED_E_SCOOTER_RIDE, 4)), spent.spentToday)
    }
}
