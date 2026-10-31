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
import vancouvermonth.core.Month
import vancouvermonth.core.TodaysRun

class MonthRecordTest {
    private val english = Choice.Take(Action.STUDY_ENGLISH)
    private val coding = Choice.Take(Action.PRACTISE_CODING)
    private val rest = Choice.Take(Action.REST)
    private val apply = Choice.Take(Action.APPLY)
    private val work = Choice.Take(Action.WORK)

    /** Everything a player can see of a month, and what decides how it goes on. */
    private fun Month.state(): List<Any?> = listOf(room, day, balance, english, coding, energy, interview, ending)

    @Test
    fun `a record is one short line, and reads back as the same record`() {
        val days = listOf(english, coding, rest, apply, work, Choice.AttendInterview)
        val record = MonthRecord(1011, null, 2, 1165, days)
        assertEquals("1|1011||2|1165|ECRAWI", record.encode())
        assertEquals(record, MonthRecord.decode(record.encode()))

        val today = TodaysRun(2026, 10, 30)
        val todays = MonthRecord(today.seed, today, 0, 0)
        assertEquals("1|${today.seed}|2026-10-30|0|0|", todays.encode())
        assertEquals(todays, MonthRecord.decode(todays.encode()))
    }

    @Test
    fun `a line this version did not write is not a record`() {
        val good = "1|1011||2|1165|ECRA"
        assertNotNull(MonthRecord.decode(good))
        val run = TodaysRun(2026, 1, 5)
        assertNotNull(MonthRecord.decode("1|${run.seed}|2026-01-05|2|1165|ECRA"))
        val bad = listOf(
            null,
            "",
            "garbage",
            "0|1011||2|1165|ECRA",
            "2|1011||2|1165|ECRA",
            "1|1011||2|1165",
            "1|1011||2|1165|ECRA|",
            "1|x||2|1165|ECRA",
            "1|1011||two|1165|ECRA",
            "1|1011||2||ECRA",
            "1|1011||2|1165|ECRZ",
            "1|1011||2|1165|ecra",
            "1|${run.seed}|2026-02-30|2|1165|ECRA",
            "1|${run.seed}|someday|2|1165|ECRA",
            "1|${run.seed + 1}|2026-01-05|2|1165|ECRA",
            // Only the exact line encode writes: no padding, no sign, no unpadded date, no space.
            " 1|1011||2|1165|ECRA",
            "1|1011||02|1165|ECRA",
            "1|1011||2|+1165|ECRA",
            "1|${run.seed}|2026-1-5|2|1165|ECRA",
            "1|1011||2|1165|ECRA ",
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
        )
        for ((why, record) in refused) assertNull(record.replay(), "Replayed: $why")
        // One day short of the end still replays, on the 30th.
        val lastDay = assertNotNull(MonthRecord(1011, null, 0, 1000, List(Month.LAST_DAY - 2) { rest }).replay())
        assertEquals(Month.LAST_DAY, lastDay.month.day)
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
                if (record.days.size == branchAt) branch = replayed.month

                val choice = randomChoice(live, random)
                if (choice == Choice.AttendInterview) interviews += 1
                val events = live.play(choice)
                branch?.let { assertEquals(events, it.play(choice), "Run $run, the branch on day ${live.day}") }
                record = record.after(choice)
                if (!live.isOver) assertEquals(events, assertNotNull(record.replay()).lastEvents)
            }
            branch?.let { assertEquals(live.state(), it.state(), "Run $run, the branch at the end") }
            val ending = assertNotNull(live.ending)
            endings += ending
            if (ending == Ending.OFFER) offers += 1
        }
        assertTrue(interviews > 50, "Only $interviews interviews")
        assertTrue(offers > 5, "Only $offers offers")
        assertEquals(Ending.entries.toSet(), endings)
    }

    /** A legal choice for today, leaning on applications so that many months have interviews. */
    private fun randomChoice(month: Month, random: Random): Choice {
        if (month.hasInterviewToday) return Choice.AttendInterview
        if (month.canTake(Action.APPLY) && random.nextInt(3) == 0) return apply
        val legal = Action.entries.filter { month.canTake(it) }
        return Choice.Take(legal[random.nextInt(legal.size)])
    }

    @Test
    fun `the last day's events come back for the screen to show`() {
        val record = MonthRecord(1011, null, 0, 1000, listOf(work))
        val events = assertNotNull(record.replay()).lastEvents
        assertEquals(listOf(Event.Earned(Month.WORK_PAY), Event.LivingCost(Month.DAILY_LIVING_COST)), events)
    }
}
