package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import vancouvermonth.core.Action
import vancouvermonth.core.Dice
import vancouvermonth.core.Ending
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Interview
import vancouvermonth.core.InterviewStage
import vancouvermonth.core.Month
import vancouvermonth.core.Room
import vancouvermonth.core.Skill
import vancouvermonth.core.Zone

/** Every roll succeeds, and things land as soon as they can. */
private object LuckyDice : Dice {
    override fun succeeds(chance: Double): Boolean = true

    override fun daysLater(range: IntRange): Int = range.first
}

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
        assertEquals("$-40", money(-40))
        assertEquals("$-1,234", money(-1_234))
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
    fun `actions show their effect and why they cannot be taken`() {
        val month = month()
        assertEquals("카페 알바", actionName(Action.WORK))
        assertEquals("면접이 잡힐 확률 27%, 체력 -1", actionEffect(Action.APPLY, month))
        assertEquals("체력 +${Month.REST_GAIN}", actionEffect(Action.REST, month))

        month.take(Action.APPLY)
        assertEquals("면접이 잡혀 있어요.", whyNot(Action.APPLY, month))

        val tired = month()
        repeat(Month.MAX_ENERGY) { tired.take(Action.WORK) }
        assertEquals("체력이 없어요. 쉬어야 해요.", whyNot(Action.WORK, tired))
    }

    @Test
    fun `applications closing late in the month are explained`() {
        val month = month()
        while (month.canStillApplyThisMonth) month.take(Action.REST)
        assertEquals("지금 지원하면 9월 안에 면접을 다 볼 수 없어요.", whyNot(Action.APPLY, month))
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
}
