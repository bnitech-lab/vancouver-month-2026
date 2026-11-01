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

/** How the guess pays, in one line: "맞힐수록 최대 $100을 더 받고, $100보다 더 틀리면 그만큼 잃어요(최대 $300)." */
fun guessRuleLine(): String {
    val reward = money(FirstMinute.GUESS_REWARD)
    return "맞힐수록 최대 ${reward}을 더 받고, ${reward}보다 더 틀리면 그만큼 잃어요(최대 ${money(FirstMinute.GUESS_PENALTY)})."
}

/** The rule worked out for three guesses, from core: "딱 맞히면 +$100, $100 틀리면 $0, $250 틀리면 -$150". */
fun guessExamplesLine(): String = listOf(0, 100, 250).joinToString(", ") { gap ->
    val amount = signedMoney(FirstMinute.guessMoney(gap))
    if (gap == 0) "딱 맞히면 $amount" else "${money(gap)} 틀리면 $amount"
}

/** One plain line under the guess's money: why a guess [gap] dollars off wins or loses what it does. */
fun guessMoneyWhy(gap: Int): String {
    val amount = FirstMinute.guessMoney(gap)
    val reward = money(FirstMinute.GUESS_REWARD)
    return when {
        gap == 0 -> "딱 맞혀서 ${reward}을 다 받아요."
        amount > 0 -> "${reward}에서 틀린 만큼 빼고 받아요."
        amount == 0 -> "${reward}만큼 틀려서 받지도 잃지도 않아요."
        amount > -FirstMinute.GUESS_PENALTY -> "${reward}보다 더 틀린 만큼 잃어요."
        else -> "많이 틀렸지만 잃는 돈은 ${money(FirstMinute.GUESS_PENALTY)}까지예요."
    }
}

/** The balance September starts with, after the first month's total and the guess's money. */
fun startBalanceLine(balance: Int): String = "9월을 시작하는 잔고 ${money(balance)}"

/** The month's goal, as the intro's story, the month's start and the "?" card on how to play all say it. */
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

/**
 * After the reveal, what the pick means for the month: how much more than the cheapest room it costs, if any. It speaks
 * of the room alone, so it comes before the guess's money, which can leave a dearer pick with more to start on.
 */
fun monthCostLine(game: FirstMinute, reveal: Reveal): String =
    if (reveal.pickedCheapest) {
        "가장 싼 방이라 매달 나가는 돈이 가장 적어요."
    } else {
        "가장 싼 방보다 한 달에 ${money(reveal.room.totalMonthlyCost - game.lowestTotal)} 더 내요. " +
            "${date(Month.LAST_DAY + 1)}에도 그만큼 더 나가요."
    }

/** What September asks of the player, shown once after the room is picked. */
fun monthStartLines(room: Room): List<String> = listOf(
    goalLine(),
    "매일 생활비 ${money(Month.DAILY_LIVING_COST)}이 나가고, " +
        "${date(Month.LAST_DAY + 1)}에는 이 방의 총비용 ${money(room.totalMonthlyCost)}를 또 내야 해요.",
    "하루는 ${clockName(Month.DAY_STARTS_AT)}에 체력 ${Month.MAX_ENERGY}로 시작해요. 일을 하면 체력만큼 시간이 흘러요.",
    "체력을 다 쓰면 밤이에요. 내 방에서 자면 다음 날 아침이에요.",
    "면접은 두 번이에요. 1차 영어 면접은 영어 실력, 2차 기술 면접은 코딩 실력으로 붙어요.",
)

fun actionName(action: Action): String = when (action) {
    Action.WORK -> "카페 알바"
    Action.STUDY_ENGLISH -> "영어 공부"
    Action.PRACTISE_CODING -> "코딩 연습"
    Action.APPLY -> "지원서 넣기"
    Action.REST -> "자기"
}

/** What [action] does now and what it costs: "+$70, 체력 -3", "레벨업까지 3번, 체력 -2", "다음 날 아침, 체력 5". */
fun actionEffect(action: Action, month: Month): String {
    val cost = "체력 -${Month.cost(action)}"
    return when (action) {
        Action.WORK -> "+${money(Month.WORK_PAY)}, $cost"
        Action.STUDY_ENGLISH -> "${stepsLine(month.stepsLeft(Skill.ENGLISH))}, $cost"
        Action.PRACTISE_CODING -> "${stepsLine(month.stepsLeft(Skill.CODING))}, $cost"
        Action.APPLY -> "면접이 잡힐 확률 ${percent(month.applyChance)}, $cost"
        Action.REST -> "다음 날 아침, 체력 ${Month.MAX_ENERGY}"
    }
}

/** How far a skill is from its next level: "레벨업까지 3번", or "최고 레벨" at the top ([left] 0). */
fun stepsLine(left: Int): String = if (left == 0) "최고 레벨" else "레벨업까지 ${left}번"

/** Why an action cannot be taken now, from the reason the month gives. */
fun whyNot(blocked: Blocked): String = when (blocked) {
    Blocked.MONTH_OVER -> "9월이 끝났어요."
    Blocked.INTERVIEW_TODAY -> "오늘은 면접부터 봐요."
    Blocked.NO_ENERGY -> "체력이 없어요. 내 방에 가서 자요."
    Blocked.LOW_ENERGY -> "체력이 모자라요."
    Blocked.INTERVIEW_BOOKED -> "면접이 잡혀 있어요."
    Blocked.APPLIED_TODAY -> "지원서는 하루에 한 번이에요."
    Blocked.APPLICATIONS_CLOSED -> "지금 지원하면 9월 안에 면접을 다 볼 수 없어요."
}

/**
 * The time on today's clock as people say it: "아침 8시", "오전 11시", "낮 12시", "오후 2시", "저녁 8시", "밤 11시".
 */
fun clockName(hour: Int): String {
    val part = when (hour) {
        in 5..9 -> "아침"
        in 10..11 -> "오전"
        12 -> "낮"
        in 13..17 -> "오후"
        in 18..20 -> "저녁"
        else -> "밤"
    }
    val twelve = if (hour % 12 == 0) 12 else hour % 12
    return "$part ${twelve}시"
}

/** Under 자기 in 내 방's talk while the character is still on its way there: the sleep waits for it. */
const val SLEEP_ON_ARRIVAL = "내 방에 도착하면 잘 수 있어요."

/**
 * A short line telling the player to go home to sleep, when nothing but sleep is left today: no stamina at all, or
 * too little for anything still open. Null while something can still be done, on an interview day before the
 * interview, and after the month.
 */
fun bedtimeLine(month: Month): String? = when {
    month.isOver || month.hasInterviewToday -> null
    month.energy == 0 -> "밤이에요. 체력을 다 썼어요. 내 방에 가서 자요."
    Action.entries.none { it != Action.REST && month.canTake(it) } -> "오늘 할 수 있는 일은 다 했어요. 내 방에 가서 자요."
    else -> null
}

/** One line for something that happened on a day. [month] is read after the day, for what it left. */
fun eventLine(event: Event, month: Month): String = when (event) {
    is Event.Earned -> "카페에서 일했어요. +${money(event.amount)}"
    is Event.SkillUp -> "${skillName(event.skill)} 실력이 올랐어요. ${skillName(event.skill)} ${event.level}/${Month.MAX_SKILL}"
    is Event.Studied -> studied(event.skill) + " " +
        if (event.stepsLeft == 0) "이미 최고 레벨이에요." else stepsLine(event.stepsLeft)
    is Event.Applied -> applied(event.interview)
    // The month's last night has no morning after it, so it promises no stamina.
    is Event.Rested -> if (month.isOver) "내 방에서 푹 잤어요." else "내 방에서 푹 잤어요. 체력 ${event.energy}/${Month.MAX_ENERGY}"
    is Event.InterviewHeld -> interviewResult(event, month.canStillApplyThisMonth)
    is Event.LivingCost -> "생활비 -${money(event.amount)}, 잔고 ${money(month.balance)}"
    is Event.Spent -> spentLine(event)
    is Event.Guessed -> "${guessMoneyLabel(event.amount)} ${signedMoney(event.amount)}, 잔고 ${money(month.balance)}"
}

/** What the guess's money is called: 짐작 보상 when it wins or breaks even, 짐작 패널티 when it loses. */
fun guessMoneyLabel(amount: Int): String = if (amount < 0) "짐작 패널티" else "짐작 보상"

/** "+$55", "-$150" or "$0": an amount with its sign, for money won or lost. */
fun signedMoney(amount: Int): String = if (amount > 0) "+${money(amount)}" else money(amount)

/** Money spent in the town: "자전거를 샀어요. -$350", "공유 퀵보드를 빌렸어요. -$4", "택시를 탔어요. -$12". */
fun spentLine(event: Event.Spent): String {
    val what = when (event.kind) {
        Spending.BIKE -> "자전거를 샀어요."
        Spending.E_SCOOTER -> "퀵보드를 샀어요."
        Spending.SHARED_BIKE_RIDE -> "공유 자전거를 빌렸어요."
        Spending.SHARED_E_SCOOTER_RIDE -> "공유 퀵보드를 빌렸어요."
        Spending.WATER_BUS_RIDE -> "수상버스를 탔어요."
        Spending.TAXI_RIDE -> "택시를 탔어요."
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

/** "영어 공부를 했어요." or "코딩 연습을 했어요.", for a study that did not raise the level yet. */
private fun studied(skill: Skill): String = when (skill) {
    Skill.ENGLISH -> "영어 공부를 했어요."
    Skill.CODING -> "코딩 연습을 했어요."
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
 * "밴쿠버 한 달 · 10월 30일 오늘의 판 · 짐작 보상 +$55 · 9월 19일 취업 제안". [guessMoney] is what the guess won or
 * lost, and [day] the day the month ended on.
 */
fun todaysRunResult(run: TodaysRun, guessMoney: Int, ending: Ending, day: Int): String {
    val result = when (ending) {
        Ending.OFFER -> "${date(day)} 취업 제안"
        Ending.SURVIVED -> "9월을 버팀"
        Ending.CANNOT_PAY_RENT -> "10월 총비용 모자람"
    }
    return "밴쿠버 한 달 · ${todaysRunName(run)} · ${guessMoneyLabel(guessMoney)} ${signedMoney(guessMoney)} · $result"
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
