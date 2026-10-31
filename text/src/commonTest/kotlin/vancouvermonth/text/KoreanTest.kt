package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import vancouvermonth.core.Action
import vancouvermonth.core.Blocked
import vancouvermonth.core.Dice
import vancouvermonth.core.Ending
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Interview
import vancouvermonth.core.InterviewStage
import vancouvermonth.core.Month
import vancouvermonth.core.Room
import vancouvermonth.core.RoomDealer
import vancouvermonth.core.Skill
import vancouvermonth.core.SpendBlocked
import vancouvermonth.core.Spending
import vancouvermonth.core.TodaysRun
import vancouvermonth.core.Zone

/** Every roll succeeds, and things land as soon as they can. */
private object LuckyDice : Dice {
    override fun succeeds(chance: Double): Boolean = true

    override fun daysLater(range: IntRange): Int = range.first
}

/** Terminal columns: two for a Hangul syllable, one for anything else, as the terminal screen counts them. */
private fun columns(line: String): Int = line.sumOf { if (it in '가'..'힣') 2 else 1 }

/**
 * The widest first-screen line today, the story's first: 47 columns, 296 of the 313 points a 375-point phone gives
 * the story card at 15px. A longer line may take a second row.
 */
private const val MAX_COLUMNS = 47

class KoreanTest {
    private val room = Room(rent = 1_020, zone = Zone.TWO, utilitiesIncluded = true, utilities = 0)

    private fun month() = Month(room, 1_800, LuckyDice)

    @Test
    fun `each ending says what happened with the right amounts`() {
        assertEquals("축하해요! 9월 19일에 취업 제안을 받았어요.", endingLine(Ending.OFFER, 19, 1_175, 900))
        assertEquals(
            "9월이 끝났어요. 10월 총비용 \$1,175를 내기엔 \$510 모자라요 (잔고 \$665).",
            endingLine(Ending.CANNOT_PAY_RENT, 30, 1_175, 665),
        )
        assertEquals(
            "9월이 끝났어요. 제안은 없었지만 10월 총비용 \$1,175는 낼 수 있어요 (잔고 \$1,300).",
            endingLine(Ending.SURVIVED, 30, 1_175, 1_300),
        )
    }

    @Test
    fun `today's run result is one short line for each ending`() {
        val run = TodaysRun(2026, 10, 30)
        assertEquals("10월 30일 오늘의 판", todaysRunName(run))
        assertEquals(
            "밴쿠버 한 달 · 10월 30일 오늘의 판 · 방 고르기 155/200 · 9월 19일 취업 제안",
            todaysRunResult(run, 155, Ending.OFFER, 19),
        )
        assertEquals(
            "밴쿠버 한 달 · 10월 30일 오늘의 판 · 방 고르기 200/200 · 9월을 버팀",
            todaysRunResult(run, 200, Ending.SURVIVED, 30),
        )
        assertEquals(
            "밴쿠버 한 달 · 10월 30일 오늘의 판 · 방 고르기 0/200 · 10월 총비용 모자람",
            todaysRunResult(run, 0, Ending.CANNOT_PAY_RENT, 30),
        )
        assertEquals("1월 5일 오늘의 판", todaysRunName(TodaysRun(2027, 1, 5)))
    }

    @Test
    fun `a failed interview says whether the player can still apply`() {
        val failed = Event.InterviewHeld(InterviewStage.PHONE_SCREEN, passed = false, next = null)
        assertEquals("1차 영어 면접에서 떨어졌어요. 다시 지원할 수 있어요.", interviewResult(failed, canApplyAgain = true))
        assertEquals("1차 영어 면접에서 떨어졌어요. 9월 안에는 다시 지원할 수 없어요.", interviewResult(failed, canApplyAgain = false))
    }

    @Test
    fun `money is written with a dollar sign and commas`() {
        assertEquals("$1,245", money(1245))
        assertEquals("$960", money(960))
    }

    @Test
    fun `money groups every three digits and keeps the sign`() {
        assertEquals("$0", money(0))
        assertEquals("$999", money(999))
        assertEquals("$1,000", money(1_000))
        assertEquals("$1,234,567", money(1_234_567))
        assertEquals("-$40", money(-40))
        assertEquals("-$1,234", money(-1_234))
    }

    @Test
    fun `the price board is read from the zones and the dealer`() {
        assertEquals("1구역 \$105 · 2구역 \$145 · 3구역 \$195", passPrices())
        assertEquals("포함이면 \$0, 별도면 한 달 \$120~\$190 (방마다 다름)", utilitiesPrices())
    }

    @Test
    fun `the reveal names every room tied for the lowest total`() {
        val rooms = listOf(
            Room(rent = 1_140, zone = Zone.ONE, utilitiesIncluded = true, utilities = 0),
            Room(rent = 1_050, zone = Zone.TWO, utilitiesIncluded = false, utilities = 150),
            Room(rent = 930, zone = Zone.THREE, utilitiesIncluded = false, utilities = 120),
        )
        val game = FirstMinute(seed = 0, rooms = rooms)
        assertEquals("총비용이 가장 싼 방은 1번, 3번이었어요 (\$1,245).", cheapestLine(game, game.reveal(1, 1_300)))
        assertEquals("총비용이 가장 싼 방을 골랐어요.", cheapestLine(game, game.reveal(2, 1_245)))
        assertEquals("짐작이 정확해요.", guessLine(game.reveal(2, 1_245)))
        assertEquals("내 짐작과 차이 \$45", guessLine(game.reveal(2, 1_200)))
        assertEquals("월세 \$1,050 + 공과금 \$150 + 교통 \$145", breakdown(rooms[1]))
        assertEquals("공과금 별도", utilitiesLabel(rooms[1]))
    }

    @Test
    fun `the first screen's story starts on the day of the pick with the starting money, the goal and a room first`() {
        val lines = arrivalLines(FirstMinute.STARTING_BALANCE)
        assertEquals(
            listOf(
                "${date(Month.FIRST_DAY - 1)}, 딱 ${money(FirstMinute.STARTING_BALANCE)} 들고 밴쿠버에 막 도착했어요.",
                goalLine(),
                "먼저 9월 한 달 지낼 방부터 구해야 해요.",
            ),
            lines,
        )
        assertEquals("9월 1일, 딱 \$3,000 들고 밴쿠버에 막 도착했어요.", lines[0])
        assertEquals("9월 1일, 딱 \$500 들고 밴쿠버에 막 도착했어요.", arrivalLines(500)[0])
        for (line in lines) assertTrue(columns(line) <= MAX_COLUMNS, "Wider than one row of a phone: $line")
    }

    @Test
    fun `why the total matters is said with October's date, and no room's total`() {
        val lines = whyTotalLines()
        assertEquals(listOf("총비용은 오늘 내고, ${date(Month.LAST_DAY + 1)}에 또 내요.", "방이 쌀수록 알바 대신 취업 준비할 날이 늘어요."), lines)
        assertEquals("총비용은 오늘 내고, 10월 1일에 또 내요.", lines[0])
        for (line in lines) assertTrue(columns(line) <= MAX_COLUMNS, "Wider than one row of a phone: $line")
    }

    @Test
    fun `a cheaper room never needs more café shifts to pay October, as the why line says`() {
        for (seed in 0 until 200) {
            val shifts = RoomDealer.deal(seed).sortedBy { it.totalMonthlyCost }.map { shiftsToPayOctober(it) }
            assertEquals(shifts.sorted(), shifts, "seed $seed")
        }
        // Seed 1011: the cheapest room ($1,165) needs 5 shifts, room 2 ($1,345) needs 8.
        val rooms = FirstMinute(1011).rooms
        assertEquals(5, shiftsToPayOctober(rooms[2]))
        assertEquals(8, shiftsToPayOctober(rooms[1]))
    }

    /** The fewest café shifts that end a month with no applications able to pay October's total, by core's rules. */
    private fun shiftsToPayOctober(room: Room): Int = (0..Month.LAST_DAY).first { shifts ->
        val month = Month(room, FirstMinute.STARTING_BALANCE - room.totalMonthlyCost, LuckyDice)
        var left = shifts
        while (!month.isOver) {
            if (left > 0 && month.canTake(Action.WORK)) {
                month.take(Action.WORK)
                left -= 1
            } else {
                month.take(Action.REST)
            }
        }
        month.ending == Ending.SURVIVED
    }

    @Test
    fun `after the reveal the pick is tied to the month, a tie counting as the cheapest`() {
        val rooms = listOf(
            Room(rent = 1_140, zone = Zone.ONE, utilitiesIncluded = true, utilities = 0),
            Room(rent = 1_050, zone = Zone.TWO, utilitiesIncluded = false, utilities = 150),
            Room(rent = 930, zone = Zone.THREE, utilitiesIncluded = false, utilities = 120),
        )
        val game = FirstMinute(seed = 0, rooms = rooms)
        val extra = rooms[1].totalMonthlyCost - game.lowestTotal
        assertEquals(
            "가장 싼 방보다 한 달에 ${money(extra)} 더 내요. ${date(Month.LAST_DAY + 1)}에도 그만큼 더 나가요.",
            monthCostLine(game, game.reveal(1, 1_300)),
        )
        assertEquals("가장 싼 방보다 한 달에 \$100 더 내요. 10월 1일에도 그만큼 더 나가요.", monthCostLine(game, game.reveal(1, 0)))
        // Rooms 1 and 3 tie for the lowest total: either is the cheapest.
        val cheapest = "가장 싼 방이라 9월을 버틸 돈이 가장 많이 남아요."
        assertEquals(cheapest, monthCostLine(game, game.reveal(0, 1_245)))
        assertEquals(cheapest, monthCostLine(game, game.reveal(2, 0)))
    }

    @Test
    fun `no line of the first screen or the reveal has an em dash`() {
        val game = FirstMinute(1011)
        val lines = arrivalLines(game.startingBalance) + whyTotalLines() +
            game.rooms.indices.map { monthCostLine(game, game.reveal(it, 0)) }
        for (line in lines) assertFalse('\u2014' in line, line)
    }

    @Test
    fun `the month start names the last day, the living cost and October's total`() {
        assertEquals(
            listOf(
                "목표: 9월 30일까지 취업 제안 받기",
                "매일 생활비 \$40이 나가고, 10월 1일에는 이 방의 총비용 \$1,165를 또 내야 해요.",
                "면접은 두 번이에요. 1차 영어 면접은 영어 실력, 2차 기술 면접은 코딩 실력으로 붙어요.",
            ),
            monthStartLines(room),
        )
    }

    @Test
    fun `actions show their effect`() {
        val month = month()
        assertEquals("카페 알바", actionName(Action.WORK))
        assertEquals("면접이 잡힐 확률 27%, 체력 -1", actionEffect(Action.APPLY, month))
        assertEquals("체력 +${Month.REST_GAIN}", actionEffect(Action.REST, month))
    }

    @Test
    fun `every reason an action is blocked reads as Korean`() {
        assertEquals("9월이 끝났어요.", whyNot(Blocked.MONTH_OVER))
        assertEquals("오늘은 면접 날이에요.", whyNot(Blocked.INTERVIEW_TODAY))
        assertEquals("체력이 없어요. 쉬어야 해요.", whyNot(Blocked.NO_ENERGY))
        assertEquals("면접이 잡혀 있어요.", whyNot(Blocked.INTERVIEW_BOOKED))
        assertEquals("지금 지원하면 9월 안에 면접을 다 볼 수 없어요.", whyNot(Blocked.APPLICATIONS_CLOSED))
    }

    @Test
    fun `each event reads as one line without the terminal's indent`() {
        val month = month()
        assertEquals("카페에서 일했어요. +\$110", eventLine(Event.Earned(110), month))
        assertEquals("영어 실력이 올랐어요. 영어 2/10", eventLine(Event.SkillUp(Skill.ENGLISH, 2), month))
        assertEquals("지원서를 냈지만 아직 연락이 없어요.", eventLine(Event.Applied(null), month))
        assertEquals(
            "연락이 왔어요! 1차 영어 면접이 9월 6일에 잡혔어요.",
            eventLine(Event.Applied(Interview(InterviewStage.PHONE_SCREEN, 6)), month),
        )
        assertEquals("푹 쉬었어요. 체력 5/5", eventLine(Event.Rested(5), month))
        assertEquals(
            "1차 영어 면접 통과! 2차 기술 면접이 9월 9일이에요.",
            eventLine(Event.InterviewHeld(InterviewStage.PHONE_SCREEN, true, Interview(InterviewStage.TECHNICAL, 9)), month),
        )
        assertEquals("생활비 -\$40, 잔고 \$1,800", eventLine(Event.LivingCost(40), month))
    }

    @Test
    fun `money spent in the town reads as what it bought or rented, and its price`() {
        val month = month()
        val lines = Spending.entries.map { eventLine(Event.Spent(it, it.price), month) }
        assertEquals(
            listOf(
                "자전거를 샀어요. -\$350",
                "퀵보드를 샀어요. -\$550",
                "공유 자전거를 빌렸어요. -\$3",
                "공유 퀵보드를 빌렸어요. -\$4",
            ),
            lines,
        )
        for (line in lines) assertFalse('\u2014' in line, line)
    }

    @Test
    fun `a day's spends take at most two lines at the top of its log`() {
        val month = month()
        val bike = Event.Spent(Spending.BIKE, 350)
        val ride = Event.Spent(Spending.SHARED_BIKE_RIDE, 3)
        val scooter = Event.Spent(Spending.SHARED_E_SCOOTER_RIDE, 4)
        assertEquals(emptyList(), spentLines(emptyList()))
        assertEquals(listOf("자전거를 샀어요. -\$350", "공유 자전거를 빌렸어요. -\$3"), spentLines(listOf(bike, ride)))
        assertEquals(
            listOf("자전거를 샀어요. -\$350", "그 밖에 5건 -\$17"),
            spentLines(listOf(bike, ride, ride, ride, scooter, scooter)),
        )
        val day = listOf(bike, ride, scooter, Event.Rested(5), Event.LivingCost(40))
        assertEquals(
            listOf("자전거를 샀어요. -\$350", "그 밖에 2건 -\$7", "푹 쉬었어요. 체력 5/5", "생활비 -\$40, 잔고 \$1,800"),
            dayLog(day, month),
        )
        assertEquals(listOf("푹 쉬었어요. 체력 5/5"), dayLog(listOf(Event.Rested(5)), month))
    }

    @Test
    fun `every reason money cannot be spent reads as Korean`() {
        assertEquals(
            listOf("9월이 끝났어요.", "이미 있어요.", "잔고가 모자라요.", "이걸 사면 9월 생활비가 모자라요."),
            SpendBlocked.entries.map { whyNotSpend(it) },
        )
    }
}
