package vancouvermonth.text

import vancouvermonth.core.Action
import vancouvermonth.core.Ending
import vancouvermonth.core.Event
import vancouvermonth.core.InterviewStage
import vancouvermonth.core.Month

/**
 * The people of the town, one at each place, and the action each one offers: at 내 방 the neighbour's is sleep. The
 * first five keep downtown's places; the others the cafés, libraries, coworking space and offices out in East Vancouver
 * and Burnaby. Every name and every line is invented; none of them is a real person or a real shop.
 */
enum class Townsperson(val title: String, val role: String, val offers: Action) {
    HANA("하나", "카페 사장", Action.WORK),
    LEO("레오", "사서", Action.STUDY_ENGLISH),
    SUJIN("수진", "코워킹 매니저", Action.PRACTISE_CODING),
    MARCUS("마커스", "안내 데스크", Action.APPLY),
    GRANDMA("이웃 할머니", "옆집", Action.REST),
    AISHA("아이샤", "안내 데스크", Action.APPLY),
    ROSA("로사", "사서", Action.STUDY_ENGLISH),
    SOPHIE("소피", "카페 사장", Action.WORK),
    NINA("니나", "코워킹 매니저", Action.PRACTISE_CODING),
    JUNHO("준호", "사서", Action.STUDY_ENGLISH),
    KENJI("켄지", "안내 데스크", Action.APPLY),
    MATEO("마테오", "카페 사장", Action.WORK),
}

/** The word on the button that takes the place's action in a talk: "알바하기", "영어 공부", "자기" and so on. */
fun keyword(action: Action): String = when (action) {
    Action.WORK -> "알바하기"
    Action.STUDY_ENGLISH -> "영어 공부"
    Action.PRACTISE_CODING -> "코딩 연습"
    Action.APPLY -> "지원서 넣기"
    Action.REST -> "자기"
}

/** What a townsperson can notice about the player today. The order is the order they care about it. */
enum class Situation {
    OFFER,

    /**
     * Today's interview is at another office than this one: only the offices' people have a line for it, and they say it
     * even on a first meeting, as the player is at the wrong door.
     */
    INTERVIEW_ELSEWHERE,
    FIRST_MEETING_AT_NIGHT,
    FIRST_MEETING,
    NIGHT,
    TECHNICAL_TODAY,
    INTERVIEW_TODAY,
    FAILED_INTERVIEW,
    LOW_ENERGY,
    MONEY_TIGHT,
    INTERVIEW_BOOKED,
    APPLIED,
    APPLICATIONS_CLOSED,
    RAINY,
    GOOD_ENGLISH,
    LOW_CODING,
    LAST_WEEK,
    FIRST_WEEK,
    ORDINARY,
}

/**
 * Everything that is true about the player now, most telling first, always ending with [Situation.ORDINARY].
 * [firstMeeting] is true when the player has not talked to this person yet, [lastEvents] is what the player's last
 * action, interview or sleep brought, as the month told it, and [weather] is today's sky, which only changes what
 * people say. Night is when no stamina is left. [elsewhere] is true at an office other than the one today's interview
 * is held at.
 */
fun situations(
    month: Month,
    firstMeeting: Boolean,
    lastEvents: List<Event>,
    weather: Weather = Weather.SUNNY,
    elsewhere: Boolean = false,
): List<Situation> {
    val stageToday = if (month.hasInterviewToday) month.interview?.stage else null
    val holds = mapOf(
        Situation.OFFER to (month.ending == Ending.OFFER),
        Situation.INTERVIEW_ELSEWHERE to (stageToday != null && elsewhere),
        Situation.FIRST_MEETING_AT_NIGHT to (firstMeeting && month.isNight),
        Situation.FIRST_MEETING to firstMeeting,
        Situation.NIGHT to month.isNight,
        Situation.TECHNICAL_TODAY to (stageToday == InterviewStage.TECHNICAL),
        Situation.INTERVIEW_TODAY to (stageToday != null),
        Situation.FAILED_INTERVIEW to lastEvents.any { it is Event.InterviewHeld && !it.passed },
        Situation.LOW_ENERGY to (month.energy <= 1),
        Situation.MONEY_TIGHT to month.moneyTight,
        Situation.INTERVIEW_BOOKED to (month.interview != null),
        Situation.APPLIED to lastEvents.any { it is Event.Applied },
        Situation.APPLICATIONS_CLOSED to !month.canStillApplyThisMonth,
        Situation.RAINY to (weather == Weather.RAINY),
        Situation.GOOD_ENGLISH to (month.english >= 5),
        Situation.LOW_CODING to (month.coding <= 4),
        Situation.LAST_WEEK to (month.day >= Month.LAST_DAY - 6),
        Situation.FIRST_WEEK to (month.day <= Month.FIRST_DAY + 6),
        Situation.ORDINARY to true,
    )
    return Situation.entries.filter { holds.getValue(it) }
}

/**
 * What [person] says on meeting the player: their line for the first situation in [situations] they have one for.
 * [elsewhere] is true at an office other than the one today's interview is held at.
 */
fun greeting(
    person: Townsperson,
    month: Month,
    firstMeeting: Boolean,
    lastEvents: List<Event>,
    weather: Weather = Weather.SUNNY,
    elsewhere: Boolean = false,
): String {
    val lines = greetings.getValue(person)
    return situations(month, firstMeeting, lastEvents, weather, elsewhere).firstNotNullOf { lines[it] }
}

/** A line of small talk, for the "이야기" button. [count] is how many times the player has asked today, from 0. */
fun smallTalk(person: Townsperson, month: Month, count: Int): String {
    val lines = smallTalks.getValue(person)
    return lines[(month.day + count) % lines.size]
}

/** Each person's line for each situation they react to. Every person has an [Situation.ORDINARY] line. */
private val greetings: Map<Townsperson, Map<Situation, String>> = mapOf(
    Townsperson.HANA to mapOf(
        Situation.FIRST_MEETING_AT_NIGHT to "새로 왔구나? 오늘은 문 닫았어. 내일 아침에 와 줄래?",
        Situation.FIRST_MEETING to "새로 왔구나? 오늘 손이 모자라. 오후까지만 도와줄래?",
        Situation.NIGHT to "오늘은 문 닫았어. 들어가서 푹 자.",
        Situation.INTERVIEW_TODAY to "오늘 면접이라며? 일은 면접 보고 와서 해. 회사로 가 봐.",
        Situation.LOW_ENERGY to "얼굴이 피곤해 보여. 오늘은 들어가서 쉬어.",
        Situation.MONEY_TIGHT to "월세 날 다가오지? 이번 주는 일 많이 줄게.",
        Situation.RAINY to "오늘 같은 날엔 다들 따뜻한 걸 찾아. 손이 모자라.",
        Situation.LAST_WEEK to "벌써 그달 끝이네. 잘 버텼어.",
        Situation.FIRST_WEEK to "비 오는 날엔 손님이 두 배야. 우산 챙겨.",
        Situation.ORDINARY to "커피 내릴 줄 알지? 오늘도 한나절 부탁해.",
    ),
    Townsperson.LEO to mapOf(
        Situation.FIRST_MEETING to "조용히... 영어 책은... 저쪽 창가에 있어요.",
        Situation.NIGHT to "곧... 문 닫아요. 책은 내일 또 읽어요.",
        Situation.INTERVIEW_TODAY to "오늘은... 책 말고 면접이죠. 회사로 가요.",
        Situation.FAILED_INTERVIEW to "한 번 떨어진 건... 연습 한 번 한 거예요.",
        Situation.INTERVIEW_BOOKED to "면접에서는... 천천히 말해도 괜찮아요. 짧은 문장이 더 잘 들려요.",
        Situation.RAINY to "비 오는 날엔... 자리가 금방 차요. 창가로 가요.",
        Situation.GOOD_ENGLISH to "요즘 말이... 많이 편해졌네요.",
        Situation.ORDINARY to "오늘도... 한 장씩 읽어 볼까요.",
    ),
    Townsperson.SUJIN to mapOf(
        Situation.FIRST_MEETING to "자리 비었어요. 와이파이 비번은 벽에 있어요.",
        Situation.NIGHT to "늦었네요. 오늘은 그만하고 자요.",
        Situation.INTERVIEW_TODAY to "오늘 면접이죠? 연습은 됐어요. 회사로 가요.",
        Situation.APPLIED to "이력서는 지원하는 곳마다 조금씩 고쳐요. 그게 다예요.",
        Situation.LOW_CODING to "하루 한 문제씩만 해도 한 달이면 서른 문제예요.",
        Situation.LAST_WEEK to "오늘 사람 많네요. 다들 마감이 있나 봐요.",
        Situation.ORDINARY to "창가 자리 비었어요. 오늘은 뭐 풀어요?",
    ),
    Townsperson.MARCUS to mapOf(
        Situation.FIRST_MEETING to "지원은 저쪽 화면에서 하시면 됩니다.",
        Situation.NIGHT to "오늘 업무는 끝났습니다. 조심히 들어가세요.",
        Situation.INTERVIEW_ELSEWHERE to "오늘 면접은 다른 회사죠? 그쪽으로 가셔야 합니다.",
        Situation.TECHNICAL_TODAY to "또 오셨네요. 이번엔 기술 면접이죠? 물 한 잔 드릴게요.",
        Situation.INTERVIEW_TODAY to "면접이시죠? 3층입니다. 행운을 빌어요.",
        Situation.INTERVIEW_BOOKED to "면접 날짜는 잡혀 있어요. 그날 다시 오세요.",
        Situation.APPLICATIONS_CLOSED to "이번 달 채용은 마감됐어요. 다음 달에 또 오세요.",
        Situation.RAINY to "우산은 입구 통에 꽂아 주세요. 바닥이 미끄럽습니다.",
        Situation.ORDINARY to "어서 오세요. 지원서는 오늘도 받습니다.",
    ),
    Townsperson.GRANDMA to mapOf(
        Situation.OFFER to "잘됐다! 오늘은 일찍 자.",
        Situation.FIRST_MEETING to "새로 이사 왔구먼. 이 동네는 아침 안개가 예뻐.",
        Situation.NIGHT to "늦었구먼. 얼른 들어가 자.",
        Situation.INTERVIEW_TODAY to "오늘 면접이라며? 따뜻하게 입고 회사로 가.",
        Situation.LOW_ENERGY to "오늘은 그만 자. 쉬는 것도 일이야.",
        Situation.RAINY to "여기 비는 조용히 오지. 금방 익숙해져.",
        Situation.ORDINARY to "밥은 챙겨 먹었어? 든든히 먹고 다녀.",
    ),
    Townsperson.AISHA to mapOf(
        Situation.FIRST_MEETING to "어서 오세요. 지원은 1층 화면에서 받습니다.",
        Situation.NIGHT to "오늘은 문을 닫았습니다. 내일 다시 오세요.",
        Situation.INTERVIEW_ELSEWHERE to "오늘 면접은 여기가 아니에요. 지원하신 회사로 가 보세요.",
        Situation.TECHNICAL_TODAY to "기술 면접이시죠? 회의실은 2층입니다. 침착하게 하세요.",
        Situation.INTERVIEW_TODAY to "면접 보러 오셨죠? 잠깐 앉아 계세요. 곧 부릅니다.",
        Situation.INTERVIEW_BOOKED to "면접 날짜가 잡혀 있네요. 그날 뵙겠습니다.",
        Situation.APPLICATIONS_CLOSED to "이번 달 채용은 끝났습니다. 다음 달에 또 오세요.",
        Situation.ORDINARY to "역이 가까워서 지원하는 분이 많아요. 오늘도 받습니다.",
    ),
    Townsperson.ROSA to mapOf(
        Situation.FIRST_MEETING to "어서 와요. 영어 공부할 자리는 2층 끝에 있어요.",
        Situation.NIGHT to "곧 문 닫아요. 빌린 책은 내일 가져와도 돼요.",
        Situation.INTERVIEW_TODAY to "오늘은 면접 보는 날이죠? 공부는 내일 해요. 회사로 가요.",
        Situation.INTERVIEW_BOOKED to "면접 전엔 소리 내서 읽는 연습이 좋아요.",
        Situation.RAINY to "비 오는 날은 아이들 이야기 시간이 있어서 조금 시끄러워요.",
        Situation.GOOD_ENGLISH to "발음이 처음보다 훨씬 또렷해졌어요.",
        Situation.ORDINARY to "오늘은 어떤 걸로 할까요? 짧은 기사부터 해 봐요.",
    ),
    Townsperson.SOPHIE to mapOf(
        Situation.FIRST_MEETING_AT_NIGHT to "처음 보네요. 오늘은 문 닫았어요. 내일 낮에 들러요.",
        Situation.FIRST_MEETING to "처음 보네요? 마침 일손이 필요했어요. 같이 해 볼래요?",
        Situation.NIGHT to "오늘 장사는 끝났어요. 조심히 들어가요.",
        Situation.INTERVIEW_TODAY to "오늘 면접이죠? 커피는 면접 끝나고 마셔요. 회사로 가요.",
        Situation.MONEY_TIGHT to "요즘 빠듯하죠? 일할 시간은 얼마든지 있어요.",
        Situation.RAINY to "비 오면 공원 산책하던 손님들이 다 여기로 와요.",
        Situation.ORDINARY to "오늘도 공원 쪽 창가가 제일 바빠요. 앞치마 여기 있어요.",
    ),
    Townsperson.NINA to mapOf(
        Situation.FIRST_MEETING to "처음이죠? 아무 자리나 앉아요. 콘센트는 책상 밑에 있어요.",
        Situation.NIGHT to "오늘은 여기까지예요. 불은 제가 끌게요.",
        Situation.INTERVIEW_TODAY to "면접 날이네요. 코드는 내일 봐요. 회사로 가요.",
        Situation.APPLIED to "지원했어요? 그럼 오늘은 문제 하나만 더 풀어요.",
        Situation.LOW_CODING to "어려운 문제 말고 쉬운 것부터 매일 하나씩 해요.",
        Situation.ORDINARY to "오늘은 큰길 쪽 창가 자리가 비었어요.",
    ),
    Townsperson.JUNHO to mapOf(
        Situation.FIRST_MEETING to "처음 오셨죠? 공원 쪽 창가 자리가 조용해요.",
        Situation.NIGHT to "문 닫을 시간이에요. 내일 또 와요.",
        Situation.INTERVIEW_TODAY to "오늘은 면접부터예요. 회사로 가요.",
        Situation.FAILED_INTERVIEW to "면접에서 받은 질문, 적어 두면 다음에 써먹어요.",
        Situation.RAINY to "비 오는 날엔 빗소리 들으면서 책 읽기 좋아요.",
        Situation.ORDINARY to "오늘도 한 시간만 집중해 봐요.",
    ),
    Townsperson.KENJI to mapOf(
        Situation.FIRST_MEETING to "처음 오셨군요. 지원서는 로비 화면에서 넣으시면 됩니다.",
        Situation.NIGHT to "로비 문 닫을 시간입니다. 조심히 가세요.",
        Situation.INTERVIEW_ELSEWHERE to "오늘 면접은 이 건물이 아니네요. 지원한 회사로 가세요.",
        Situation.TECHNICAL_TODAY to "기술 면접 맞으시죠? 12층으로 올라가세요.",
        Situation.INTERVIEW_TODAY to "면접이시군요. 엘리베이터 타고 올라가세요. 잘 보세요.",
        Situation.INTERVIEW_BOOKED to "면접 날짜는 잡혀 있습니다. 그날 오세요.",
        Situation.APPLICATIONS_CLOSED to "이번 달 채용은 닫혔습니다. 다음 달을 기다려 주세요.",
        Situation.RAINY to "비 오는 날엔 로비가 우산 터는 소리로 가득합니다.",
        Situation.ORDINARY to "어서 오세요. 오늘도 지원서 받습니다.",
    ),
    Townsperson.MATEO to mapOf(
        Situation.FIRST_MEETING_AT_NIGHT to "이 동네 새로 왔어요? 오늘은 닫았어요. 내일 와요.",
        Situation.FIRST_MEETING to "이 동네 새로 왔어요? 비탈길 올라오느라 힘들었죠. 일 좀 도와줄래요?",
        Situation.NIGHT to "불 끄는 중이에요. 언덕길 어두우니까 천천히 가요.",
        Situation.INTERVIEW_TODAY to "면접 날엔 면접이 먼저예요. 회사 다녀와서 봐요.",
        Situation.LOW_ENERGY to "지쳐 보여요. 오늘은 그냥 집에 가서 쉬어요.",
        Situation.ORDINARY to "오늘은 손님이 천천히 와요. 그래도 손은 필요해요.",
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
    Townsperson.AISHA to listOf(
        "갈림길역은 아침마다 사람이 정말 많습니다.",
        "점심엔 길 건너에 푸드트럭이 와요.",
        "계단 벽에 직원들이 그린 그림이 걸려 있습니다.",
    ),
    Townsperson.ROSA to listOf(
        "저녁엔 동네 사람들이 모여서 영어로 수다를 떨어요.",
        "신문은 매일 아침 새로 들어와요.",
        "역이 가까워서 오가는 길에 들르는 사람이 많아요.",
    ),
    Townsperson.SOPHIE to listOf(
        "공원에서 강아지 산책하는 사람들이 단골이에요.",
        "케이크는 아침에 굽고, 오후면 다 팔려요.",
        "공원 벤치에서 커피 마시는 손님이 많아요.",
    ),
    Townsperson.NINA to listOf(
        "큰길 차 소리가 들려도 금방 익숙해져요.",
        "목요일엔 다 같이 모여서 서로 코드를 봐 줘요.",
        "간식 바구니는 다 같이 채워요.",
    ),
    Townsperson.JUNHO to listOf(
        "창밖으로 공원 연못이 보여요.",
        "영어 동화책도 꽤 도움이 돼요.",
        "버스 정류장이 가까워서 오가기 편해요.",
    ),
    Townsperson.KENJI to listOf(
        "창밖으로 스카이트레인이 지나가는 게 보입니다.",
        "점심시간엔 중심가 식당들이 붐빕니다.",
        "로비 의자는 생각보다 편합니다.",
    ),
    Townsperson.MATEO to listOf(
        "여기서 내려다보면 강이 살짝 보여요.",
        "정류장 옆이라 아침엔 버스 기다리는 손님이 많아요.",
        "언덕 위라 자전거 손님은 다 숨이 차서 들어와요.",
    ),
)
