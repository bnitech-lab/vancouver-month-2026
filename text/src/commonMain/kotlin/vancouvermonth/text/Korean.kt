package vancouvermonth.text

import kotlin.math.roundToInt
import vancouvermonth.core.Action
import vancouvermonth.core.Blocked
import vancouvermonth.core.Ending
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Interview
import vancouvermonth.core.InterviewStage
import vancouvermonth.core.Month
import vancouvermonth.core.Reveal
import vancouvermonth.core.Room
import vancouvermonth.core.RoomDealer
import vancouvermonth.core.Skill
import vancouvermonth.core.TodaysRun
import vancouvermonth.core.Zone

/** "1구역 $105 · 2구역 $145 · 3구역 $195", from the zones themselves. */
fun passPrices(): String = Zone.entries.joinToString(" · ") { "${it.number}구역 ${money(it.monthlyPass)}" }

/** What utilities can cost, from the range the dealer uses. */
fun utilitiesPrices(): String {
    val range = RoomDealer.UTILITIES_RANGE
    return "포함이면 ${money(0)}, 별도면 한 달 ${money(range.first)}~${money(range.last)} (방마다 다름)"
}

fun utilitiesLabel(room: Room): String = if (room.utilitiesIncluded) "공과금 포함" else "공과금 별도"

/** "월세 $970 + 공과금 $180 + 교통 $195" */
fun breakdown(room: Room): String =
    "월세 ${money(room.rent)} + 공과금 ${money(room.utilities)} + 교통 ${money(room.zone.monthlyPass)}"

/** Whether the pick was the cheapest room, naming every room tied for the lowest total when it was not. */
fun cheapestLine(game: FirstMinute, reveal: Reveal): String =
    if (reveal.pickedCheapest) {
        "총비용이 가장 싼 방을 골랐어요."
    } else {
        val rooms = game.cheapestRoomIndices.joinToString(", ") { "${it + 1}번" }
        "총비용이 가장 싼 방은 ${rooms}이었어요 (${money(game.lowestTotal)})."
    }

fun guessLine(reveal: Reveal): String =
    if (reveal.guessGap == 0) "짐작이 정확해요." else "내 짐작과 차이 ${money(reveal.guessGap)}"

/** The month's goal, as the month's start and the town's first guide both say it. */
fun goalLine(): String = "목표: ${date(Month.LAST_DAY)}까지 취업 제안 받기"

/** What September asks of the player, shown once after the room is picked. */
fun monthStartLines(room: Room): List<String> = listOf(
    goalLine(),
    "매일 생활비 ${money(Month.DAILY_LIVING_COST)}이 나가고, " +
        "${date(Month.LAST_DAY + 1)}에는 이 방의 총비용 ${money(room.totalMonthlyCost)}를 또 내야 해요.",
    "면접은 두 번이에요. 1차 영어 면접은 영어 실력, 2차 기술 면접은 코딩 실력으로 붙어요.",
)

fun actionName(action: Action): String = when (action) {
    Action.WORK -> "카페 알바"
    Action.STUDY_ENGLISH -> "영어 공부"
    Action.PRACTISE_CODING -> "코딩 연습"
    Action.APPLY -> "지원서 넣기"
    Action.REST -> "쉬기"
}

fun actionEffect(action: Action, month: Month): String = when (action) {
    Action.WORK -> "+${money(Month.WORK_PAY)}, 체력 -1"
    Action.STUDY_ENGLISH -> "영어 +1, 체력 -1"
    Action.PRACTISE_CODING -> "코딩 +1, 체력 -1"
    Action.APPLY -> "면접이 잡힐 확률 ${percent(month.applyChance)}, 체력 -1"
    Action.REST -> "체력 +${Month.REST_GAIN}"
}

/** Why an action cannot be taken today, from the reason the month gives. */
fun whyNot(blocked: Blocked): String = when (blocked) {
    Blocked.MONTH_OVER -> "9월이 끝났어요."
    Blocked.INTERVIEW_TODAY -> "오늘은 면접 날이에요."
    Blocked.NO_ENERGY -> "체력이 없어요. 쉬어야 해요."
    Blocked.INTERVIEW_BOOKED -> "면접이 잡혀 있어요."
    Blocked.APPLICATIONS_CLOSED -> "지금 지원하면 9월 안에 면접을 다 볼 수 없어요."
}

/** One line for something that happened on a day. [month] is read after the day, for what it left. */
fun eventLine(event: Event, month: Month): String = when (event) {
    is Event.Earned -> "카페에서 일했어요. +${money(event.amount)}"
    is Event.SkillUp -> "${skillName(event.skill)} 실력이 올랐어요. ${skillName(event.skill)} ${event.level}/${Month.MAX_SKILL}"
    is Event.Applied -> applied(event.interview)
    is Event.Rested -> "푹 쉬었어요. 체력 ${event.energy}/${Month.MAX_ENERGY}"
    is Event.InterviewHeld -> interviewResult(event, month.canStillApplyThisMonth)
    is Event.LivingCost -> "생활비 -${money(event.amount)}, 잔고 ${money(month.balance)}"
}

private fun applied(interview: Interview?): String =
    if (interview == null) {
        "지원서를 냈지만 아직 연락이 없어요."
    } else {
        "연락이 왔어요! ${stageName(interview.stage)}이 ${date(interview.day)}에 잡혔어요."
    }

/** [canApplyAgain] is false late in the month, when applications have closed. */
fun interviewResult(event: Event.InterviewHeld, canApplyAgain: Boolean): String {
    val stage = stageName(event.stage)
    val next = event.next
    return when {
        !event.passed && canApplyAgain -> "${stage}에서 떨어졌어요. 다시 지원할 수 있어요."
        !event.passed -> "${stage}에서 떨어졌어요. 9월 안에는 다시 지원할 수 없어요."
        next != null -> "$stage 통과! ${stageName(next.stage)}이 ${date(next.day)}이에요."
        else -> "$stage 통과!"
    }
}

/** The first line of the ending screen. [rent] is October's total for the same room. */
fun endingLine(ending: Ending, day: Int, rent: Int, balance: Int): String = when (ending) {
    Ending.OFFER -> "축하해요! ${date(day)}에 취업 제안을 받았어요."
    Ending.CANNOT_PAY_RENT ->
        "9월이 끝났어요. 10월 총비용 ${money(rent)}를 내기엔 ${money(rent - balance)} 모자라요 (잔고 ${money(balance)})."
    Ending.SURVIVED -> "9월이 끝났어요. 제안은 없었지만 10월 총비용 ${money(rent)}는 낼 수 있어요 (잔고 ${money(balance)})."
}

/** "10월 30일 오늘의 판", the name of a date's run. */
fun todaysRunName(run: TodaysRun): String = "${run.month}월 ${run.day}일 오늘의 판"

/**
 * Today's run as one line to paste anywhere, with no run number, no link and nothing about the player:
 * "밴쿠버 한 달 · 10월 30일 오늘의 판 · 방 고르기 155/200 · 9월 19일 취업 제안". [day] is the day the month ended on.
 */
fun todaysRunResult(run: TodaysRun, score: Int, ending: Ending, day: Int): String {
    val result = when (ending) {
        Ending.OFFER -> "${date(day)} 취업 제안"
        Ending.SURVIVED -> "9월을 버팀"
        Ending.CANNOT_PAY_RENT -> "10월 총비용 모자람"
    }
    return "밴쿠버 한 달 · ${todaysRunName(run)} · 방 고르기 $score/${FirstMinute.MAX_SCORE} · $result"
}

fun stageName(stage: InterviewStage): String = when (stage) {
    InterviewStage.PHONE_SCREEN -> "1차 영어 면접"
    InterviewStage.TECHNICAL -> "2차 기술 면접"
}

fun skillName(skill: Skill): String = when (skill) {
    Skill.ENGLISH -> "영어"
    Skill.CODING -> "코딩"
}

/** Day 2 is "9월 2일". A day past the 30th falls in October. */
fun date(day: Int): String = if (day <= Month.LAST_DAY) "9월 ${day}일" else "10월 ${day - Month.LAST_DAY}일"

fun percent(chance: Double): String = "${(chance * 100).roundToInt()}%"

/** "$1,245". A negative amount keeps its minus sign after the dollar sign, as in "$-40". */
fun money(amount: Int): String {
    val digits = amount.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return "$" + (if (amount < 0) "-" else "") + grouped
}
