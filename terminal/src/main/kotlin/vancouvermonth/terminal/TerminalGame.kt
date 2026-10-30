package vancouvermonth.terminal

import java.util.Locale
import kotlin.math.roundToInt
import vancouvermonth.core.Action
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
import vancouvermonth.core.Zone

/**
 * Plays one run as text: the room on September 1st, then one action a day until the month ends.
 * Input and output are passed in, so a test can play a whole run.
 * [input] returns null when the input ends (Ctrl+D), which ends the game.
 */
class TerminalGame(
    private val input: () -> String?,
    private val output: (String) -> Unit,
) {
    /** Plays one run and returns true when the player wants another. */
    fun play(seed: Int): Boolean = play(FirstMinute(seed))

    /** Plays one run of a game that is already set up, which lets a test hand in tied rooms. */
    fun play(game: FirstMinute): Boolean {
        showRooms(game)
        val roomIndex = askRoom(game.rooms.size) ?: return false
        val guess = askAmount() ?: return false
        val reveal = showReveal(game, roomIndex, guess)

        val month = game.startMonth(roomIndex)
        showMonthStart(month)
        while (!month.isOver) {
            showDay(month)
            val events = if (month.hasInterviewToday) {
                ask("엔터를 누르면 면접을 봐요 > ") ?: return false
                month.attendInterview()
            } else {
                month.take(askAction(month) ?: return false)
            }
            showEvents(month, events)
        }
        showEnding(month, reveal)
        return askAgain()
    }

    private fun showRooms(game: FirstMinute) {
        line()
        line("BNI Tech")
        line()
        line("밴쿠버 한 달 (가제)                판 번호 ${game.seed}")
        line("잔고 ${money(game.startingBalance)} · 9월 1일")
        line()
        // Hangul takes two columns in a terminal, so the indents below count "할 일: " as 7 and "공과금" as 6.
        line("할 일: 한 달 총비용이 가장 싼 방을 고르세요.")
        line("       한 달 총비용 = 월세 + 공과금 + 교통 정기권")
        line()
        line("요금표")
        line("  교통 정기권   " + Zone.entries.joinToString(" · ") { "${it.number}구역 ${money(it.monthlyPass)}" })
        val utilities = RoomDealer.UTILITIES_RANGE
        line("  공과금        포함이면 ${money(0)}, 별도면 한 달 ${money(utilities.first)}~${money(utilities.last)} (방마다 다름)")
        line()
        game.rooms.forEachIndexed { index, room -> line("  ${index + 1}) ${describe(room)}") }
        line()
    }

    private fun showReveal(game: FirstMinute, roomIndex: Int, guess: Int): Reveal {
        val reveal = game.reveal(roomIndex, guess)
        val room = reveal.room
        line()
        line("  한 달 총비용  ${money(room.totalMonthlyCost)}")
        line("  월세 ${money(room.rent)} + 공과금 ${money(room.utilities)} + 교통 ${money(room.zone.monthlyPass)}")
        line()
        if (reveal.pickedCheapest) {
            line("  총비용이 가장 싼 방을 골랐어요.   +${reveal.cheapestPoints}")
        } else {
            val rooms = game.cheapestRoomIndices.joinToString(", ") { "${it + 1}번" }
            line("  총비용이 가장 싼 방은 ${rooms}이었어요 (${money(game.lowestTotal)}).   +${reveal.cheapestPoints}")
        }
        if (reveal.guessGap == 0) {
            line("  짐작이 정확해요.   +${reveal.guessPoints}")
        } else {
            line("  내 짐작과 차이 ${money(reveal.guessGap)}   +${reveal.guessPoints}")
        }
        line("  방 고르기 점수 ${reveal.score} / ${FirstMinute.MAX_SCORE}")
        line()
        line("  첫 달을 내고 남은 잔고 ${money(reveal.balanceAfterFirstMonth)}")
        line()
        return reveal
    }

    private fun showMonthStart(month: Month) {
        line("9월 한 달이 시작돼요.")
        line("  목표: 9월 30일까지 취업 제안 받기")
        line("  매일 생활비 ${money(Month.DAILY_LIVING_COST)}이 나가고, 10월 1일에는 이 방의 총비용 ${money(month.room.totalMonthlyCost)}를 또 내야 해요.")
        line("  면접은 두 번이에요. 1차 영어 면접은 영어 실력, 2차 기술 면접은 코딩 실력으로 붙어요.")
        line()
    }

    private fun showDay(month: Month) {
        line("[${date(month.day)}] 남은 날 ${month.daysLeft}일")
        line("  잔고 ${money(month.balance)} · 10월 1일에 낼 총비용 ${money(month.room.totalMonthlyCost)}")
        line("  영어 ${month.english}/${Month.MAX_SKILL} · 코딩 ${month.coding}/${Month.MAX_SKILL} · 체력 ${month.energy}/${Month.MAX_ENERGY}")
        month.interview?.let { line("  면접: ${stageName(it.stage)} ${date(it.day)} (통과 확률 ${percent(month.passChance(it.stage))})") }
        if (month.hasInterviewToday) {
            line("  오늘은 면접 날이에요.")
        } else {
            line("오늘 할 일 하나:")
            Action.entries.forEachIndexed { index, action -> line("  ${index + 1}) ${describe(action, month)}") }
        }
    }

    private fun showEvents(month: Month, events: List<Event>) {
        for (event in events) {
            when (event) {
                is Event.Earned -> line("  카페에서 일했어요. +${money(event.amount)}")
                is Event.SkillUp -> line("  ${skillName(event.skill)} 실력이 올랐어요. ${skillName(event.skill)} ${event.level}/${Month.MAX_SKILL}")
                is Event.Applied -> line(applied(event.interview))
                is Event.Rested -> line("  푹 쉬었어요. 체력 ${event.energy}/${Month.MAX_ENERGY}")
                is Event.InterviewHeld -> line(interviewResult(event))
                is Event.LivingCost -> line("  생활비 -${money(event.amount)}, 잔고 ${money(month.balance)}")
            }
        }
        line()
    }

    private fun showEnding(month: Month, reveal: Reveal) {
        val ending = checkNotNull(month.ending) { "The month is not over" }
        line(endingLine(ending, month.day, month.room.totalMonthlyCost, month.balance))
        line("  방 고르기 점수 ${reveal.score}/${FirstMinute.MAX_SCORE} · 영어 ${month.english}/${Month.MAX_SKILL} · 코딩 ${month.coding}/${Month.MAX_SKILL}")
        line()
    }

    private fun askRoom(roomCount: Int): Int? {
        while (true) {
            val answer = ask("어느 방? (1-$roomCount) > ") ?: return null
            parseRoomChoice(answer, roomCount)?.let { return it }
            line("1부터 ${roomCount} 사이의 번호로 골라 주세요.")
        }
    }

    private fun askAmount(): Int? {
        while (true) {
            val answer = ask("이 방의 한 달 총비용은 얼마일까요? ($) > ") ?: return null
            parseAmount(answer)?.let { return it }
            line("숫자로 적어 주세요. 예: 1200")
        }
    }

    private fun askAction(month: Month): Action? {
        val actions = Action.entries
        while (true) {
            val answer = ask("오늘 할 일? (1-${actions.size}) > ") ?: return null
            val index = parseRoomChoice(answer, actions.size)
            when {
                index == null -> line("1부터 ${actions.size} 사이의 번호로 골라 주세요.")
                !month.canTake(actions[index]) -> line("지금은 못 해요. ${whyNot(actions[index], month)}")
                else -> return actions[index]
            }
        }
    }

    private fun askAgain(): Boolean {
        val answer = ask("한 판 더? (y/n) > ") ?: return false
        return answer.trim().lowercase() in setOf("y", "yes", "ㅛ")
    }

    private fun ask(prompt: String): String? {
        output(prompt)
        return input()
    }

    private fun line(text: String = "") = output(text + "\n")
}

private fun describe(room: Room): String {
    val utilities = if (room.utilitiesIncluded) "공과금 포함" else "공과금 별도"
    return "월세 ${money(room.rent).padStart(6)}   교통 ${room.zone.number}구역   $utilities"
}

private fun describe(action: Action, month: Month): String {
    val (name, effect) = when (action) {
        Action.WORK -> "카페 알바" to "+${money(Month.WORK_PAY)}, 체력 -1"
        Action.STUDY_ENGLISH -> "영어 공부" to "영어 +1, 체력 -1"
        Action.PRACTISE_CODING -> "코딩 연습" to "코딩 +1, 체력 -1"
        Action.APPLY -> "지원서 넣기" to "면접이 잡힐 확률 ${percent(month.applyChance)}, 체력 -1"
        Action.REST -> "쉬기" to "체력 +${Month.REST_GAIN}"
    }
    val note = if (month.canTake(action)) "" else "   (${whyNot(action, month)})"
    return padToColumns(name, 14) + effect + note
}

/** The first line of the ending screen. [rent] is October's total for the same room. */
fun endingLine(ending: Ending, day: Int, rent: Int, balance: Int): String = when (ending) {
    Ending.OFFER -> "축하해요! ${date(day)}에 취업 제안을 받았어요."
    Ending.CANNOT_PAY_RENT ->
        "9월이 끝났어요. 10월 총비용 ${money(rent)}를 내기엔 ${money(rent - balance)} 모자라요 (잔고 ${money(balance)})."
    Ending.SURVIVED -> "9월이 끝났어요. 제안은 없었지만 10월 총비용 ${money(rent)}는 낼 수 있어요 (잔고 ${money(balance)})."
}

private fun whyNot(action: Action, month: Month): String = when {
    action != Action.REST && month.energy == 0 -> "체력이 없어요. 쉬어야 해요."
    action == Action.APPLY && month.interview != null -> "면접이 잡혀 있어요."
    action == Action.APPLY && !month.canStillApplyThisMonth -> "지금 지원하면 9월 안에 면접을 다 볼 수 없어요."
    else -> "오늘은 할 수 없어요."
}

private fun applied(interview: Interview?): String =
    if (interview == null) {
        "  지원서를 냈지만 아직 연락이 없어요."
    } else {
        "  연락이 왔어요! ${stageName(interview.stage)}이 ${date(interview.day)}에 잡혔어요."
    }

private fun interviewResult(event: Event.InterviewHeld): String {
    val stage = stageName(event.stage)
    val next = event.next
    return when {
        !event.passed -> "  ${stage}에서 떨어졌어요. 다시 지원할 수 있어요."
        next != null -> "  $stage 통과! ${stageName(next.stage)}이 ${date(next.day)}이에요."
        else -> "  $stage 통과!"
    }
}

private fun stageName(stage: InterviewStage): String = when (stage) {
    InterviewStage.PHONE_SCREEN -> "1차 영어 면접"
    InterviewStage.TECHNICAL -> "2차 기술 면접"
}

private fun skillName(skill: Skill): String = when (skill) {
    Skill.ENGLISH -> "영어"
    Skill.CODING -> "코딩"
}

/** Day 2 is "9월 2일". A day past the 30th falls in October. */
fun date(day: Int): String = if (day <= Month.LAST_DAY) "9월 ${day}일" else "10월 ${day - Month.LAST_DAY}일"

fun percent(chance: Double): String = "${(chance * 100).roundToInt()}%"

/** Pads [text] to [columns] terminal columns, counting each Hangul syllable as two. */
fun padToColumns(text: String, columns: Int): String {
    val width = text.sumOf { if (it in '가'..'힣') 2 else 1 }
    return text + " ".repeat(maxOf(1, columns - width))
}

fun money(amount: Int): String = "$" + String.format(Locale.US, "%,d", amount)

/** "2" with three choices gives index 1. Anything else gives null. */
fun parseRoomChoice(text: String, roomCount: Int): Int? {
    val number = text.trim().toIntOrNull() ?: return null
    return if (number in 1..roomCount) number - 1 else null
}

/** Reads amounts such as "1200", "1,200" or "$1200". Anything else, or a negative number, gives null. */
fun parseAmount(text: String): Int? {
    val digits = text.trim().removePrefix("$").replace(",", "")
    val amount = digits.toIntOrNull() ?: return null
    return if (amount >= 0) amount else null
}
