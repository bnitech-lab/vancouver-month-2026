package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import vancouvermonth.core.Action
import vancouvermonth.core.Dice
import vancouvermonth.core.Event
import vancouvermonth.core.InterviewStage
import vancouvermonth.core.Month
import vancouvermonth.core.Room
import vancouvermonth.core.Zone

/** Luck the test decides: every roll succeeds or every roll fails, and things land as soon as they can. */
private class SetDice(private val lucky: Boolean) : Dice {
    override fun succeeds(chance: Double): Boolean = lucky

    override fun daysLater(range: IntRange): Int = range.first
}

class TownsfolkTest {
    private val room = Room(rent = 1_020, zone = Zone.TWO, utilitiesIncluded = true, utilities = 0)

    private fun month(balance: Int = 5_000, lucky: Boolean = true) = Month(room, balance, SetDice(lucky))

    /** Plays [action] until the month reaches [day]. */
    private fun Month.playUntil(day: Int, action: Action = Action.REST) {
        while (this.day < day) take(action)
    }

    @Test
    fun `each person offers one action, and every action has one person`() {
        assertEquals(Action.entries.toSet(), Townsperson.entries.map { it.offers }.toSet())
        assertEquals(Action.entries.size, Townsperson.entries.size)
    }

    @Test
    fun `each action has its own keyword`() {
        assertEquals(listOf("알바하기", "영어 공부", "코딩 연습", "지원서 넣기", "쉬기"), Action.entries.map { keyword(it) })
    }

    @Test
    fun `an ordinary day is always among the situations, and it comes last`() {
        val month = month()
        month.playUntil(15)
        // Two weeks of rest leave the coding level low, and nothing else to notice.
        assertEquals(listOf(Situation.LOW_CODING, Situation.ORDINARY), situations(month, false, emptyList()))
        assertEquals("커피 내릴 줄 알지? 오늘도 한 시간 부탁해.", greeting(Townsperson.HANA, month, false, emptyList()))
        assertEquals(Situation.ORDINARY, situations(month(), true, emptyList()).last())
    }

    @Test
    fun `a first meeting comes before the first week`() {
        val month = month()
        assertEquals(
            listOf(Situation.FIRST_MEETING, Situation.LOW_CODING, Situation.FIRST_WEEK, Situation.ORDINARY),
            situations(month, firstMeeting = true, yesterday = emptyList()),
        )
        assertEquals("새로 왔구나? 오늘 손이 모자라. 한 시간만 도와줄래?", greeting(Townsperson.HANA, month, true, emptyList()))
        assertEquals("비 오는 날엔 손님이 두 배야. 우산 챙겨.", greeting(Townsperson.HANA, month, false, emptyList()))
    }

    @Test
    fun `low energy is noticed by the café and by the neighbour`() {
        val month = month()
        repeat(4) { month.take(Action.WORK) }
        assertTrue(Situation.LOW_ENERGY in situations(month, false, emptyList()))
        assertEquals("얼굴이 피곤해 보여. 오늘은 쉬어도 돼.", greeting(Townsperson.HANA, month, false, emptyList()))
        assertEquals("오늘은 그냥 쉬어. 쉬는 것도 일이야.", greeting(Townsperson.GRANDMA, month, false, emptyList()))
    }

    @Test
    fun `money is tight when the living costs to come leave less than October's total`() {
        // October's total is 1,020 + 145; 29 days of living cost 1,160.
        val tight = month(balance = 2_000)
        assertTrue(Situation.MONEY_TIGHT in situations(tight, false, emptyList()))
        assertEquals("월세 날 다가오지? 이번 주는 일 많이 줄게.", greeting(Townsperson.HANA, tight, false, emptyList()))
        val easy = month(balance = 2_400)
        assertTrue(Situation.MONEY_TIGHT !in situations(easy, false, emptyList()))
    }

    @Test
    fun `an application, a booked interview and the interview day each get their own line`() {
        val month = month()
        val applied = month.take(Action.APPLY)
        assertEquals("이력서는 지원하는 곳마다 조금씩 고쳐요. 그게 다예요.", greeting(Townsperson.SUJIN, month, false, applied))
        assertEquals(
            "면접에서는... 천천히 말해도 괜찮아요. 짧은 문장이 더 잘 들려요.",
            greeting(Townsperson.LEO, month, false, applied),
        )
        assertEquals("면접 날짜는 잡혀 있어요. 그날 다시 오세요.", greeting(Townsperson.MARCUS, month, false, applied))

        month.playUntil(checkNotNull(month.interview).day)
        assertTrue(month.hasInterviewToday)
        assertEquals("면접이시죠? 3층입니다. 행운을 빌어요.", greeting(Townsperson.MARCUS, month, false, emptyList()))
        // Everyone else sends the player to the office.
        assertEquals("오늘 면접이라며? 일은 내일 해. 회사로 가 봐.", greeting(Townsperson.HANA, month, false, emptyList()))

        month.attendInterview()
        month.playUntil(checkNotNull(month.interview).day)
        assertEquals(InterviewStage.TECHNICAL, month.interview?.stage)
        assertEquals(
            "또 오셨네요. 이번엔 기술 면접이죠? 물 한 잔 드릴게요.",
            greeting(Townsperson.MARCUS, month, false, emptyList()),
        )
    }

    @Test
    fun `a failed interview yesterday comforts at the library`() {
        val month = month(lucky = false)
        val failed = listOf(Event.InterviewHeld(InterviewStage.PHONE_SCREEN, passed = false, next = null))
        assertEquals("한 번 떨어진 건... 연습 한 번 한 거예요.", greeting(Townsperson.LEO, month, false, failed))
    }

    @Test
    fun `the last week and closed applications have their own lines`() {
        val month = month()
        month.playUntil(Month.LAST_DAY - 6)
        assertEquals("벌써 그달 끝이네. 잘 버텼어.", greeting(Townsperson.HANA, month, false, emptyList()))
        month.playUntil(28)
        assertEquals("이번 달 채용은 마감됐어요. 다음 달에 또 오세요.", greeting(Townsperson.MARCUS, month, false, emptyList()))
    }

    @Test
    fun `on the day of the offer the neighbour says so`() {
        val month = month()
        month.take(Action.APPLY)
        while (!month.isOver) if (month.hasInterviewToday) month.attendInterview() else month.take(Action.REST)
        assertEquals("잘됐다! 오늘은 일찍 자.", greeting(Townsperson.GRANDMA, month, false, emptyList()))
    }

    @Test
    fun `small talk changes when asked again`() {
        val month = month()
        val first = smallTalk(Townsperson.GRANDMA, month, 0)
        assertNotEquals(first, smallTalk(Townsperson.GRANDMA, month, 1))
        assertEquals(first, smallTalk(Townsperson.GRANDMA, month, 3))
        for (person in Townsperson.entries) assertTrue(smallTalk(person, month, 0).isNotBlank())
    }

    @Test
    fun `every person has a line for an ordinary day`() {
        val month = month()
        month.playUntil(15)
        for (person in Townsperson.entries) assertTrue(greeting(person, month, false, emptyList()).isNotBlank(), "$person")
    }

    /** Each person's line for a rainy day; 수진 has none and goes on as on any day. */
    private val rainLines = mapOf(
        Townsperson.HANA to "오늘 같은 날엔 다들 따뜻한 걸 찾아. 손이 모자라.",
        Townsperson.LEO to "비 오는 날엔... 자리가 금방 차요. 창가로 가요.",
        Townsperson.MARCUS to "우산은 입구 통에 꽂아 주세요. 바닥이 미끄럽습니다.",
        Townsperson.GRANDMA to "여기 비는 조용히 오지. 금방 익숙해져.",
    )

    @Test
    fun `on a rainy day the people who have a rain line say it`() {
        val month = month()
        month.playUntil(15)
        assertEquals(
            listOf(Situation.RAINY, Situation.LOW_CODING, Situation.ORDINARY),
            situations(month, false, emptyList(), Weather.RAINY),
        )
        for (person in Townsperson.entries) {
            val rainy = greeting(person, month, false, emptyList(), Weather.RAINY)
            assertEquals(rainLines[person] ?: greeting(person, month, false, emptyList()), rainy, "$person")
        }
        // What matters more still comes first: a first meeting, or tired eyes.
        assertEquals(
            "새로 왔구나? 오늘 손이 모자라. 한 시간만 도와줄래?",
            greeting(Townsperson.HANA, month(), true, emptyList(), Weather.RAINY),
        )
        val tired = month()
        repeat(4) { tired.take(Action.WORK) }
        assertEquals("오늘은 그냥 쉬어. 쉬는 것도 일이야.", greeting(Townsperson.GRANDMA, tired, false, emptyList(), Weather.RAINY))
    }

    @Test
    fun `on a dry day nobody says a rain line, all month long`() {
        val month = month()
        for (day in Month.FIRST_DAY..Month.LAST_DAY) {
            for (weather in listOf(Weather.SUNNY, Weather.CLOUDY)) {
                assertTrue(Situation.RAINY !in situations(month, false, emptyList(), weather), "day $day, $weather")
                for (person in Townsperson.entries) {
                    for (first in listOf(true, false)) {
                        val line = greeting(person, month, first, emptyList(), weather)
                        assertTrue(line !in rainLines.values, "$person on day $day, $weather: $line")
                    }
                }
            }
            if (!month.isOver) month.take(Action.REST)
        }
    }
}
