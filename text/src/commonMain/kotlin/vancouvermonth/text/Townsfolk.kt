package vancouvermonth.text

import vancouvermonth.core.Action
import vancouvermonth.core.Ending
import vancouvermonth.core.Event
import vancouvermonth.core.InterviewStage
import vancouvermonth.core.Month

/**
 * The people of the town, one at each place, and the day's action each one offers. Every name and every line is
 * invented; none of them is a real person or a real shop.
 */
enum class Townsperson(val title: String, val role: String, val offers: Action) {
    HANA("하나", "카페 사장", Action.WORK),
    LEO("레오", "사서", Action.STUDY_ENGLISH),
    SUJIN("수진", "코워킹 매니저", Action.PRACTISE_CODING),
    MARCUS("마커스", "안내 데스크", Action.APPLY),
    GRANDMA("이웃 할머니", "옆집", Action.REST),
}

/** The word on the button that takes the day's action in a talk: "알바하기", "영어 공부" and so on. */
fun keyword(action: Action): String = when (action) {
    Action.WORK -> "알바하기"
    Action.STUDY_ENGLISH -> "영어 공부"
    Action.PRACTISE_CODING -> "코딩 연습"
    Action.APPLY -> "지원서 넣기"
    Action.REST -> "쉬기"
}

/** What a townsperson can notice about the player today. The order is the order they care about it. */
enum class Situation {
    OFFER,
    FIRST_MEETING,
    TECHNICAL_TODAY,
    INTERVIEW_TODAY,
    FAILED_INTERVIEW,
    LOW_ENERGY,
    MONEY_TIGHT,
    INTERVIEW_BOOKED,
    APPLIED,
    APPLICATIONS_CLOSED,
    GOOD_ENGLISH,
    LOW_CODING,
    LAST_WEEK,
    FIRST_WEEK,
    ORDINARY,
}

/**
 * Everything that is true about the player today, most telling first, always ending with [Situation.ORDINARY].
 * [firstMeeting] is true when the player has not talked to this person yet, and [yesterday] is what happened on
 * the day before, as the month told it.
 */
fun situations(month: Month, firstMeeting: Boolean, yesterday: List<Event>): List<Situation> {
    val stageToday = if (month.hasInterviewToday) month.interview?.stage else null
    val holds = mapOf(
        Situation.OFFER to (month.ending == Ending.OFFER),
        Situation.FIRST_MEETING to firstMeeting,
        Situation.TECHNICAL_TODAY to (stageToday == InterviewStage.TECHNICAL),
        Situation.INTERVIEW_TODAY to (stageToday != null),
        Situation.FAILED_INTERVIEW to yesterday.any { it is Event.InterviewHeld && !it.passed },
        Situation.LOW_ENERGY to (month.energy <= 1),
        Situation.MONEY_TIGHT to month.moneyTight,
        Situation.INTERVIEW_BOOKED to (month.interview != null),
        Situation.APPLIED to yesterday.any { it is Event.Applied },
        Situation.APPLICATIONS_CLOSED to !month.canStillApplyThisMonth,
        Situation.GOOD_ENGLISH to (month.english >= 5),
        Situation.LOW_CODING to (month.coding <= 4),
        Situation.LAST_WEEK to (month.day >= Month.LAST_DAY - 6),
        Situation.FIRST_WEEK to (month.day <= Month.FIRST_DAY + 6),
        Situation.ORDINARY to true,
    )
    return Situation.entries.filter { holds.getValue(it) }
}

/** What [person] says on meeting the player: their line for the first situation in [situations] they have one for. */
fun greeting(person: Townsperson, month: Month, firstMeeting: Boolean, yesterday: List<Event>): String {
    val lines = greetings.getValue(person)
    return situations(month, firstMeeting, yesterday).firstNotNullOf { lines[it] }
}

/** A line of small talk, for the "이야기" button. [count] is how many times the player has asked today, from 0. */
fun smallTalk(person: Townsperson, month: Month, count: Int): String {
    val lines = smallTalks.getValue(person)
    return lines[(month.day + count) % lines.size]
}

/** Each person's line for each situation they react to. Every person has an [Situation.ORDINARY] line. */
private val greetings: Map<Townsperson, Map<Situation, String>> = mapOf(
    Townsperson.HANA to mapOf(
        Situation.FIRST_MEETING to "새로 왔구나? 오늘 손이 모자라. 한 시간만 도와줄래?",
        Situation.INTERVIEW_TODAY to "오늘 면접이라며? 일은 내일 해. 회사로 가 봐.",
        Situation.LOW_ENERGY to "얼굴이 피곤해 보여. 오늘은 쉬어도 돼.",
        Situation.MONEY_TIGHT to "월세 날 다가오지? 이번 주는 일 많이 줄게.",
        Situation.LAST_WEEK to "벌써 그달 끝이네. 잘 버텼어.",
        Situation.FIRST_WEEK to "비 오는 날엔 손님이 두 배야. 우산 챙겨.",
        Situation.ORDINARY to "커피 내릴 줄 알지? 오늘도 한 시간 부탁해.",
    ),
    Townsperson.LEO to mapOf(
        Situation.FIRST_MEETING to "조용히... 영어 책은... 저쪽 창가에 있어요.",
        Situation.INTERVIEW_TODAY to "오늘은... 책 말고 면접이죠. 회사로 가요.",
        Situation.FAILED_INTERVIEW to "한 번 떨어진 건... 연습 한 번 한 거예요.",
        Situation.INTERVIEW_BOOKED to "면접에서는... 천천히 말해도 괜찮아요. 짧은 문장이 더 잘 들려요.",
        Situation.GOOD_ENGLISH to "요즘 말이... 많이 편해졌네요.",
        Situation.ORDINARY to "오늘도... 한 장씩 읽어 볼까요.",
    ),
    Townsperson.SUJIN to mapOf(
        Situation.FIRST_MEETING to "자리 비었어요. 와이파이 비번은 벽에 있어요.",
        Situation.INTERVIEW_TODAY to "오늘 면접이죠? 연습은 됐어요. 회사로 가요.",
        Situation.APPLIED to "이력서는 지원하는 곳마다 조금씩 고쳐요. 그게 다예요.",
        Situation.LOW_CODING to "하루 한 문제씩만 해도 한 달이면 서른 문제예요.",
        Situation.LAST_WEEK to "오늘 사람 많네요. 다들 마감이 있나 봐요.",
        Situation.ORDINARY to "창가 자리 비었어요. 오늘은 뭐 풀어요?",
    ),
    Townsperson.MARCUS to mapOf(
        Situation.FIRST_MEETING to "지원은 저쪽 화면에서 하시면 됩니다.",
        Situation.TECHNICAL_TODAY to "또 오셨네요. 이번엔 기술 면접이죠? 물 한 잔 드릴게요.",
        Situation.INTERVIEW_TODAY to "면접이시죠? 3층입니다. 행운을 빌어요.",
        Situation.INTERVIEW_BOOKED to "면접 날짜는 잡혀 있어요. 그날 다시 오세요.",
        Situation.APPLICATIONS_CLOSED to "이번 달 채용은 마감됐어요. 다음 달에 또 오세요.",
        Situation.ORDINARY to "어서 오세요. 지원서는 오늘도 받습니다.",
    ),
    Townsperson.GRANDMA to mapOf(
        Situation.OFFER to "잘됐다! 오늘은 일찍 자.",
        Situation.FIRST_MEETING to "새로 이사 왔구먼. 이 동네는 아침 안개가 예뻐.",
        Situation.INTERVIEW_TODAY to "오늘 면접이라며? 따뜻하게 입고 회사로 가.",
        Situation.LOW_ENERGY to "오늘은 그냥 쉬어. 쉬는 것도 일이야.",
        Situation.ORDINARY to "여기 비는 조용히 오지. 금방 익숙해져.",
    ),
)

/** A few lines of small talk each, about life in the town. */
private val smallTalks: Map<Townsperson, List<String>> = mapOf(
    Townsperson.HANA to listOf(
        "비 오는 날엔 손님이 두 배야. 우산 챙겨.",
        "아침엔 다들 라테만 찾아. 난 그냥 드립이 좋은데.",
        "가게 앞 벚나무, 봄에 오면 정말 예뻐.",
    ),
    Townsperson.LEO to listOf(
        "도서관 카드는... 공짜예요. 영어 모임도 있어요.",
        "창가 자리에서는... 바다가 보여요.",
        "모르는 단어는... 세 번 보면 친구가 돼요.",
    ),
    Townsperson.SUJIN to listOf(
        "커피는 무제한이에요. 대신 컵은 직접 씻어요.",
        "금요일 저녁엔 다 같이 피자 먹어요.",
        "모니터 하나 더 쓰고 싶으면 말해요.",
    ),
    Townsperson.MARCUS to listOf(
        "꼭대기 층에서는 산이 다 보입니다.",
        "로비 화분은 제가 키웁니다.",
        "엘리베이터가 느리면 계단이 빠릅니다.",
    ),
    Townsperson.GRANDMA to listOf(
        "저녁엔 여기 모닥불 앞이 제일 따뜻해.",
        "스카이트레인 소리가 들리면 아침이야.",
        "바닷가 길 따라 걸으면 마음이 편해져.",
    ),
)
