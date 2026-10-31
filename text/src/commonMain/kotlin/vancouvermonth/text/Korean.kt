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
import vancouvermonth.core.SpendBlocked
import vancouvermonth.core.Spending
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

/** The month's goal, as the first screen, the month's start and the town's first guide all say it. */
fun goalLine(): String = "목표: ${date(Month.LAST_DAY)}까지 취업 제안 받기"

/**
 * Who the player is and why a room comes first, at the top of the first screen. [balance] is the starting money.
 * The story starts on the day the room is picked, the day before the month's first day, so "오늘" below means it.
 */
fun arrivalLines(balance: Int): List<String> = listOf(
    "${date(Month.FIRST_DAY - 1)}, 딱 ${money(balance)} 들고 밴쿠버에 막 도착했어요.",
    goalLine(),
    "먼저 9월 한 달 지낼 방부터 구해야 해요.",
)

/**
 * Why the cheapest total matters, under the formula, without naming any room's total. The total is paid twice, so
 * a cheaper room needs fewer café shifts to pay October and leaves more days for English, coding and applications.
 */
fun whyTotalLines(): List<String> = listOf(
    "총비용은 오늘 내고, ${date(Month.LAST_DAY + 1)}에 또 내요.",
    "방이 쌀수록 알바 대신 취업 준비할 날이 늘어요.",
)

/** After the reveal, what the pick means for the month: how much more than the cheapest room it costs, if any. */
fun monthCostLine(game: FirstMinute, reveal: Reveal): String =
    if (reveal.pickedCheapest) {
        "가장 싼 방이라 9월을 버틸 돈이 가장 많이 남아요."
    } else {
        "가장 싼 방보다 한 달에 ${money(reveal.room.totalMonthlyCost - game.lowestTotal)} 더 내요. " +
            "${date(Month.LAST_DAY + 1)}에도 그만큼 더 나가요."
    }

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
    is Event.Spent -> spentLine(event)
}

/** Money spent in the town: "자전거를 샀어요. -$350", "공유 퀵보드를 빌렸어요. -$4". */
fun spentLine(event: Event.Spent): String {
    val what = when (event.kind) {
        Spending.BIKE -> "자전거를 샀어요."
        Spending.E_SCOOTER -> "퀵보드를 샀어요."
        Spending.SHARED_BIKE_RIDE -> "공유 자전거를 빌렸어요."
        Spending.SHARED_E_SCOOTER_RIDE -> "공유 퀵보드를 빌렸어요."
    }
    return "$what -${money(event.amount)}"
}

/**
 * The money spent during a day, for that day's log, in at most two lines: one line each when there are one or two,
 * else the first and "그 밖에 3건 -$11" for the rest, so a busy day never pushes the log down the screen.
 */
fun spentLines(spends: List<Event.Spent>): List<String> = when {
    spends.size <= 2 -> spends.map { spentLine(it) }
    else -> {
        val rest = spends.drop(1)
        listOf(spentLine(spends.first()), "그 밖에 ${rest.size}건 -${money(rest.sumOf { it.amount })}")
    }
}

/**
 * A day's log as the screen shows it: the money spent during the day first, in at most two lines ([spentLines]), then
 * one line for each thing the day itself brought. [month] is read after the day.
 */
fun dayLog(events: List<Event>, month: Month): List<String> =
    spentLines(events.filterIsInstance<Event.Spent>()) + events.filter { it !is Event.Spent }.map { eventLine(it, month) }

/** Why money cannot be spent now, from the reason the month gives. */
fun whyNotSpend(blocked: SpendBlocked): String = when (blocked) {
    SpendBlocked.MONTH_OVER -> "9월이 끝났어요."
    SpendBlocked.ALREADY_OWNED -> "이미 있어요."
    SpendBlocked.NOT_ENOUGH_MONEY -> "잔고가 모자라요."
    SpendBlocked.LIVING_COST -> "이걸 사면 9월 생활비가 모자라요."
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

/** "$1,245". A negative amount has its minus sign first, as in "-$40". */
fun money(amount: Int): String {
    val digits = amount.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return (if (amount < 0) "-" else "") + "$" + grouped
}
