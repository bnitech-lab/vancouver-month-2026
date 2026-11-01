package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
            "밴쿠버 한 달 · 10월 30일 오늘의 판 · 짐작 보상 +\$55 · 9월 19일 취업 제안",
            todaysRunResult(run, 55, Ending.OFFER, 19),
        )
        assertEquals(
            "밴쿠버 한 달 · 10월 30일 오늘의 판 · 짐작 보상 +\$100 · 9월을 버팀",
            todaysRunResult(run, 100, Ending.SURVIVED, 30),
        )
        assertEquals(
            "밴쿠버 한 달 · 10월 30일 오늘의 판 · 짐작 패널티 -\$300 · 10월 총비용 모자람",
            todaysRunResult(run, -300, Ending.CANNOT_PAY_RENT, 30),
        )
        assertEquals(
            "밴쿠버 한 달 · 10월 30일 오늘의 판 · 짐작 보상 \$0 · 9월을 버팀",
            todaysRunResult(run, 0, Ending.SURVIVED, 30),
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
        // Seed 1011: the cheapest room ($1,165) needs 7 shifts, room 2 ($1,345) needs 13.
        val rooms = FirstMinute(1011).rooms
        assertEquals(7, shiftsToPayOctober(rooms[2]))
        assertEquals(13, shiftsToPayOctober(rooms[1]))
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
        val cheapest = "가장 싼 방이라 매달 나가는 돈이 가장 적어요."
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
                "하루는 아침 8시에 체력 5로 시작해요. 일을 하면 체력만큼 시간이 흘러요.",
                "체력을 다 쓰면 밤이에요. 내 방에서 자면 다음 날 아침이에요.",
                "면접은 두 번이에요. 1차 영어 면접은 영어 실력, 2차 기술 면접은 코딩 실력으로 붙어요.",
            ),
            monthStartLines(room),
        )
    }

    @Test
    fun `actions show their effect and their stamina`() {
        val month = month()
        assertEquals(listOf("카페 알바", "영어 공부", "코딩 연습", "지원서 넣기", "자기"), Action.entries.map { actionName(it) })
        assertEquals(
            listOf(
                "+\$70, 체력 -3",
                "레벨업까지 4번, 체력 -2",
                "레벨업까지 4번, 체력 -2",
                "면접이 잡힐 확률 27%, 체력 -1",
                "다음 날 아침, 체력 5",
            ),
            Action.entries.map { actionEffect(it, month) },
        )
        month.take(Action.STUDY_ENGLISH)
        assertEquals("레벨업까지 3번, 체력 -2", actionEffect(Action.STUDY_ENGLISH, month))
        assertEquals("최고 레벨", stepsLine(0))
    }

    @Test
    fun `every reason an action is blocked reads as Korean`() {
        assertEquals("9월이 끝났어요.", whyNot(Blocked.MONTH_OVER))
        assertEquals("오늘은 면접부터 봐요.", whyNot(Blocked.INTERVIEW_TODAY))
        assertEquals("체력이 없어요. 내 방에 가서 자요.", whyNot(Blocked.NO_ENERGY))
        assertEquals("체력이 모자라요.", whyNot(Blocked.LOW_ENERGY))
        assertEquals("이미 최고 레벨이에요.", whyNot(Blocked.SKILL_MAXED))
        assertEquals("면접이 잡혀 있어요.", whyNot(Blocked.INTERVIEW_BOOKED))
        assertEquals("지원서는 하루에 한 번이에요.", whyNot(Blocked.APPLIED_TODAY))
        assertEquals("지금 지원하면 9월 안에 면접을 다 볼 수 없어요.", whyNot(Blocked.APPLICATIONS_CLOSED))
        for (blocked in Blocked.entries) assertFalse('\u2014' in whyNot(blocked), "$blocked")
    }

    @Test
    fun `the day's clock reads as people say the time`() {
        assertEquals(
            listOf("아침 8시", "오전 11시", "오후 2시", "오후 5시", "저녁 8시", "밤 11시"),
            (0..Month.MAX_ENERGY).map { clockName(Month.DAY_STARTS_AT + it * Month.HOURS_PER_ENERGY) },
        )
        assertEquals("낮 12시", clockName(12))
        assertEquals("밤 12시", clockName(24))
    }

    @Test
    fun `when nothing but sleep is left, a line sends the player home`() {
        val month = month()
        assertNull(bedtimeLine(month))
        month.take(Action.STUDY_ENGLISH)
        month.take(Action.PRACTISE_CODING)
        assertNull(bedtimeLine(month), "an application still fits")
        month.take(Action.APPLY)
        assertEquals("밤이에요. 체력을 다 썼어요. 내 방에 가서 자요.", bedtimeLine(month))
        month.take(Action.REST)
        assertNull(bedtimeLine(month))
        // One stamina left after a shift and an application: nothing else fits.
        val tired = month()
        tired.take(Action.WORK)
        tired.take(Action.APPLY)
        assertEquals(1, tired.energy)
        assertEquals("오늘 할 수 있는 일은 다 했어요. 내 방에 가서 자요.", bedtimeLine(tired))
    }

    @Test
    fun `each event reads as one line without the terminal's indent`() {
        val month = month()
        assertEquals("카페에서 일했어요. +\$110", eventLine(Event.Earned(110), month))
        assertEquals("영어 실력이 올랐어요. 영어 2/10", eventLine(Event.SkillUp(Skill.ENGLISH, 2), month))
        assertEquals("영어 공부를 했어요. 레벨업까지 3번", eventLine(Event.Studied(Skill.ENGLISH, 1, 3), month))
        assertEquals("코딩 연습을 했어요. 레벨업까지 1번", eventLine(Event.Studied(Skill.CODING, 3, 1), month))
        assertEquals("지원서를 냈지만 아직 연락이 없어요.", eventLine(Event.Applied(null), month))
        assertEquals(
            "연락이 왔어요! 1차 영어 면접이 9월 6일에 잡혔어요.",
            eventLine(Event.Applied(Interview(InterviewStage.PHONE_SCREEN, 6)), month),
        )
        assertEquals("내 방에서 푹 잤어요. 체력 5/5", eventLine(Event.Rested(5), month))
        // After the last night there is no morning: no stamina is promised.
        val over = month()
        var last = emptyList<Event>()
        while (!over.isOver) last = over.take(Action.REST)
        assertEquals("내 방에서 푹 잤어요.", eventLine(last.first(), over))
        assertEquals(
            "1차 영어 면접 통과! 2차 기술 면접이 9월 9일이에요.",
            eventLine(Event.InterviewHeld(InterviewStage.PHONE_SCREEN, true, Interview(InterviewStage.TECHNICAL, 9)), month),
        )
        assertEquals("생활비 -\$40, 잔고 \$1,800", eventLine(Event.LivingCost(40), month))
    }

    @Test
    fun `the guess's money reads with its sign, as a reward or a penalty, with the balance it leaves`() {
        val month = month()
        assertEquals("짐작 보상 +\$100, 잔고 \$1,800", eventLine(Event.Guessed(0, 100), month))
        assertEquals("짐작 보상 +\$55, 잔고 \$1,800", eventLine(Event.Guessed(45, 55), month))
        assertEquals("짐작 보상 \$0, 잔고 \$1,800", eventLine(Event.Guessed(100, 0), month))
        assertEquals("짐작 패널티 -\$150, 잔고 \$1,800", eventLine(Event.Guessed(250, -150), month))
        assertEquals("짐작 패널티 -\$300, 잔고 \$1,800", eventLine(Event.Guessed(900, -300), month))
        assertEquals(listOf("+\$80", "\$0", "-\$1,234"), listOf(signedMoney(80), signedMoney(0), signedMoney(-1_234)))
    }

    @Test
    fun `how the guess pays is said in one line, and worked out from core for three guesses`() {
        assertEquals("맞힐수록 최대 \$100을 더 받고, \$100보다 더 틀리면 그만큼 잃어요(최대 \$300).", guessRuleLine())
        assertEquals("딱 맞히면 +\$100, \$100 틀리면 \$0, \$250 틀리면 -\$150", guessExamplesLine())
        assertEquals(Intro.GUESS_WHAT, "방을 고른 뒤 그 방의 한 달 총비용을 짐작해 적어요.")
        for (line in listOf(guessRuleLine(), guessExamplesLine(), Intro.GUESS_WHAT, Intro.GUESS, Intro.START)) {
            assertFalse('\u2014' in line, "Em dash in: $line")
            assertTrue(columns(line) <= 2 * MAX_COLUMNS, "More than two rows of a phone: $line")
        }
    }

    @Test
    fun `the plain line under the guess's money says why, at every edge of the rule`() {
        val lines = listOf(0, 1, 45, 99, 100, 101, 250, 399, 400, 10_000).associateWith { gap -> guessMoneyWhy(gap) }
        assertEquals("딱 맞혀서 \$100을 다 받아요.", lines[0])
        for (gap in listOf(1, 45, 99)) assertEquals("\$100에서 틀린 만큼 빼고 받아요.", lines[gap])
        assertEquals("\$100만큼 틀려서 받지도 잃지도 않아요.", lines[100])
        for (gap in listOf(101, 250, 399)) assertEquals("\$100보다 더 틀린 만큼 잃어요.", lines[gap])
        for (gap in listOf(400, 10_000)) assertEquals("많이 틀렸지만 잃는 돈은 \$300까지예요.", lines[gap])
        for (line in lines.values) assertFalse('\u2014' in line, line)
        assertEquals("짐작 보상", guessMoneyLabel(0))
        assertEquals("짐작 보상", guessMoneyLabel(55))
        assertEquals("짐작 패널티", guessMoneyLabel(-1))
        assertEquals("9월을 시작하는 잔고 \$1,710", startBalanceLine(1_710))
    }

    @Test
    fun `money spent in the town reads as what it bought or rented, and its price`() {
        val month = month()
        val lines = Spending.entries.map { eventLine(Event.Spent(it, it.price), month) }
        assertEquals(
            listOf(
                "자전거를 샀어요. -\$350",
                "퀵보드를 샀어요. -\$550",
                "공유 자전거를 빌렸어요. -\$1",
                "공유 퀵보드를 빌렸어요. -\$1",
                "수상버스를 탔어요. -\$4",
                "택시를 탔어요. -\$4",
            ),
            lines,
        )
        for (line in lines) assertFalse('\u2014' in line, line)
        assertEquals("택시를 탔어요. -\$23", eventLine(Event.Spent(Spending.TAXI_RIDE, 23), month))
        assertEquals("공유 퀵보드를 빌렸어요. -\$7", eventLine(Event.Spent(Spending.SHARED_E_SCOOTER_RIDE, 7), month))
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
            listOf("자전거를 샀어요. -\$350", "그 밖에 2건 -\$7", "내 방에서 푹 잤어요. 체력 5/5", "생활비 -\$40, 잔고 \$1,800"),
            dayLog(day, month),
        )
        assertEquals(listOf("내 방에서 푹 잤어요. 체력 5/5"), dayLog(listOf(Event.Rested(5)), month))
    }

    @Test
    fun `every reason money cannot be spent reads as Korean`() {
        assertEquals(
            listOf("9월이 끝났어요.", "이미 있어요.", "잔고가 모자라요.", "이걸 사면 9월 생활비가 모자라요."),
            SpendBlocked.entries.map { whyNotSpend(it) },
        )
    }
}
