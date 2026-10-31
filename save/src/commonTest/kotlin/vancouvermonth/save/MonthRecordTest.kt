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

    /** Everything a player can see of a month, and what decides how it goes on. */
    private fun Month.state(): List<Any?> =
        listOf(room, day, balance, english, coding, energy, interview, ending, owned)

    @Test
    fun `a record is one short line, and reads back as the same record`() {
        val days = listOf(english, coding, rest, apply, work, Choice.AttendInterview)
        val record = MonthRecord(1011, null, 2, 1165, days)
        assertEquals("2|1011||2|1165|ECRAWI", record.encode())
        assertEquals(record, MonthRecord.decode(record.encode()))
        assertEquals(days, record.days)

        val today = TodaysRun(2026, 10, 30)
        val todays = MonthRecord(today.seed, today, 0, 0)
        assertEquals("2|${today.seed}|2026-10-30|0|0|", todays.encode())
        assertEquals(todays, MonthRecord.decode(todays.encode()))
    }

    @Test
    fun `money spent between days is kept in order, a small letter and its amount each`() {
        val moves = listOf(english, sharedScooter, work, bike, scooter, coding, sharedBike)
        val record = MonthRecord(1011, null, 2, 1165, moves)
        assertEquals("2|1011||2|1165|Eq4Wb350k550Cs3", record.encode())
        assertEquals(record, MonthRecord.decode(record.encode()))
        assertEquals(listOf(english, work, coding), record.days)
        assertEquals(record, MonthRecord(1011, null, 2, 1165).after(english).after(sharedScooter).after(work)
            .after(bike).after(scooter).after(coding).after(sharedBike))
    }

    @Test
    fun `a line this version did not write is not a record`() {
        val good = "2|1011||2|1165|ECRA"
        assertNotNull(MonthRecord.decode(good))
        assertNotNull(MonthRecord.decode("2|1011||2|1165|Eb350Rq4A"))
        val run = TodaysRun(2026, 1, 5)
        assertNotNull(MonthRecord.decode("2|${run.seed}|2026-01-05|2|1165|ECRA"))
        val bad = listOf(
            null,
            "",
            "garbage",
            "0|1011||2|1165|ECRA",
            // The first version's lines, kept before money could be spent, are dropped.
            "1|1011||2|1165|ECRA",
            "3|1011||2|1165|ECRA",
            "2|1011||2|1165",
            "2|1011||2|1165|ECRA|",
            "2|x||2|1165|ECRA",
            "2|1011||two|1165|ECRA",
            "2|1011||2||ECRA",
            "2|1011||2|1165|ECRZ",
            "2|1011||2|1165|ecra",
            "2|${run.seed}|2026-02-30|2|1165|ECRA",
            "2|${run.seed}|someday|2|1165|ECRA",
            "2|${run.seed + 1}|2026-01-05|2|1165|ECRA",
            // A spend needs its amount, in plain digits, after a letter this version knows.
            "2|1011||2|1165|EbRA",
            "2|1011||2|1165|ERAb",
            "2|1011||2|1165|Ex4RA",
            "2|1011||2|1165|EB350RA",
            "2|1011||2|1165|Eb-350RA",
            "2|1011||2|1165|Eb99999999999RA",
            // Only the exact line encode writes: no padding, no sign, no unpadded date, no space.
            " 2|1011||2|1165|ECRA",
            "2|1011||02|1165|ECRA",
            "2|1011||2|+1165|ECRA",
            "2|${run.seed}|2026-1-5|2|1165|ECRA",
            "2|1011||2|1165|ECRA ",
            "2|1011||2|1165|Eb0350RA",
            "2|1011||2|1165|Eb+350RA",
        )
        for (text in bad) assertNull(MonthRecord.decode(text), "Read as a record: $text")
    }

    @Test
    fun `a fresh record replays as a fresh start, with the same rooms and the same reveal`() {
        val replayed = assertNotNull(MonthRecord(1011, null, 2, 1165).replay())
        val fresh = FirstMinute(1011)
        assertEquals(fresh.rooms, replayed.firstMinute.rooms)
        assertEquals(fresh.reveal(2, 1165), replayed.reveal)
        assertEquals(fresh.startMonth(2).state(), replayed.month.state())
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
            val month = FirstMinute(seed).startMonth(0)
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
            val live = firstMinute.startMonth(room)
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

    /** Money to spend before today's day, about one time in four: mostly a ride, sometimes something to keep. */
    private fun randomSpend(month: Month, random: Random): Spend? {
        if (random.nextInt(4) != 0) return null
        val kind = when (random.nextInt(10)) {
            0 -> Spending.BIKE
            1 -> Spending.E_SCOOTER
            in 2..5 -> Spending.SHARED_BIKE_RIDE
            else -> Spending.SHARED_E_SCOOTER_RIDE
        }
        return Spend(kind, kind.price).takeIf { month.canSpend(kind, kind.price) }
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
     * (a price, a chance, a delay, the rooms, or Kotlin's Random), a kept line would replay to another month than the
     * player left, so this fails and asks for a new [MonthRecord.VERSION], which drops the old lines instead.
     */
    @Test
    fun `kept lines replay to the very months they were kept from`() {
        val changed = "core's rules changed: bump MonthRecord.VERSION, then update these numbers"
        val phoneScreen = Interview(InterviewStage.PHONE_SCREEN, 11)
        val technical = Interview(InterviewStage.TECHNICAL, 13)
        val golden = listOf(
            // Study, work, code.
            "2|1011||2|1165|EWC" to listOf(
                5, 1_825, 2, 4, 2, null, 1_165,
                listOf(Event.SkillUp(Skill.CODING, 4), Event.LivingCost(40)),
            ),
            // Four days of English, two of rest, and an application that books a phone screen.
            "2|1002||1|1325|EEEERRA" to listOf(
                9, 1_395, 5, 3, 4, phoneScreen, 1_325,
                listOf(Event.Applied(phoneScreen), Event.LivingCost(40)),
            ),
            // Rest until the phone screen, pass it, and a technical interview is booked.
            "2|1002||1|1325|EEEERRARRI" to listOf(
                12, 1_275, 5, 3, 5, technical, 1_325,
                listOf(Event.InterviewHeld(InterviewStage.PHONE_SCREEN, true, technical), Event.LivingCost(40)),
            ),
            // Rest a day and fail the technical interview.
            "2|1002||1|1325|EEEERRARRIRI" to listOf(
                14, 1_195, 5, 3, 5, null, 1_325,
                listOf(Event.InterviewHeld(InterviewStage.TECHNICAL, false, null), Event.LivingCost(40)),
            ),
            // Study; a shared e-scooter, then work; a bike, then code; and a shared bike today, before today's day.
            "2|1011||2|1165|Eq4Wb350Cs3" to listOf(
                5, 1_825 - 4 - 350 - 3, 2, 4, 2, null, 1_165,
                listOf(Event.Spent(Spending.BIKE, 350), Event.SkillUp(Skill.CODING, 4), Event.LivingCost(40)),
            ),
        )
        for ((line, expected) in golden) {
            val replayed = assertNotNull(MonthRecord.decode(line)?.replay(), "$changed ($line no longer replays)")
            val month = replayed.month
            val actual = listOf(
                month.day, month.balance, month.english, month.coding, month.energy, month.interview,
                month.room.totalMonthlyCost, replayed.lastEvents,
            )
            assertEquals(expected, actual, "$changed ($line)")
            assertEquals(FirstMinute.MAX_SCORE, replayed.reveal.score, "$changed ($line)")
        }
        val spender = assertNotNull(MonthRecord.decode("2|1011||2|1165|Eq4Wb350Cs3")?.replay())
        assertEquals(setOf(Item.BIKE), spender.month.owned)
        assertEquals(listOf(Event.Spent(Spending.SHARED_BIKE_RIDE, 3)), spender.spentToday)
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
