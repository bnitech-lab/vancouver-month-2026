package vancouvermonth.terminal

import vancouvermonth.core.Action
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.core.Reveal
import vancouvermonth.core.Room
import vancouvermonth.core.TodaysRun
import vancouvermonth.text.actionEffect
import vancouvermonth.text.actionName
import vancouvermonth.text.arrivalLines
import vancouvermonth.text.breakdown
import vancouvermonth.text.cheapestLine
import vancouvermonth.text.date
import vancouvermonth.text.endingLine
import vancouvermonth.text.eventLine
import vancouvermonth.text.Intro
import vancouvermonth.text.guessLine
import vancouvermonth.text.guessMoneyLabel
import vancouvermonth.text.guessMoneyWhy
import vancouvermonth.text.guessRuleLine
import vancouvermonth.text.money
import vancouvermonth.text.monthCostLine
import vancouvermonth.text.monthStartLines
import vancouvermonth.text.parseAmount
import vancouvermonth.text.passPrices
import vancouvermonth.text.percent
import vancouvermonth.text.signedMoney
import vancouvermonth.text.stageName
import vancouvermonth.text.todaysRunName
import vancouvermonth.text.todaysRunResult
import vancouvermonth.text.utilitiesLabel
import vancouvermonth.text.utilitiesPrices
import vancouvermonth.text.whyNot
import vancouvermonth.text.whyTotalLines

/**
 * Plays one run as text: the room and the guess at its total on September 1st, the guess's money as the month starts,
 * then one action a day until the month ends.
 * Input and output are passed in, so a test can play a whole run.
 * [input] returns null when the input ends (Ctrl+D), which ends the game.
 */
class TerminalGame(
    private val input: () -> String?,
    private val output: (String) -> Unit,
) {
    /** Plays one run and returns true when the player wants another. */
    fun play(seed: Int): Boolean = play(FirstMinute(seed))

    /** Plays today's run, which ends with its result as one line to copy. */
    fun play(today: TodaysRun): Boolean = play(FirstMinute(today.seed), today)

    /**
     * Plays one run of a game that is already set up, which lets a test hand in tied rooms.
     * [today] is set when this is today's run.
     */
    fun play(game: FirstMinute, today: TodaysRun? = null): Boolean {
        showRooms(game, today)
        val roomIndex = askRoom(game.rooms.size) ?: return false
        val guess = askAmount() ?: return false
        val reveal = showReveal(game, roomIndex, guess)

        val month = game.startMonth(roomIndex, guess)
        showOpening(month)
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
        showEnding(month, reveal, today)
        return askAgain()
    }

    private fun showRooms(game: FirstMinute, today: TodaysRun?) {
        line()
        line("BNI Tech")
        line()
        line("밴쿠버 한 달 (가제)                판 번호 ${game.seed}")
        if (today != null) line(todaysRunName(today))
        line()
        // The story says the money and the date, so no balance line repeats them.
        for (text in arrivalLines(game.startingBalance)) line(text)
        line()
        // Hangul takes two columns in a terminal, so the indents below count "할 일: " as 7 and "공과금" as 6.
        line("할 일: 한 달 총비용이 가장 싼 방을 고르세요.")
        line("       한 달 총비용 = 월세 + 공과금 + 교통 정기권")
        for (text in whyTotalLines()) line("       $text")
        line()
        line("요금표")
        line("  교통 정기권   " + passPrices())
        line("  공과금        " + utilitiesPrices())
        line()
        game.rooms.forEachIndexed { index, room -> line("  ${index + 1}) ${describe(room)}") }
        line()
        // "총비용 짐작: " takes 13 columns.
        line("총비용 짐작: ${Intro.GUESS_WHAT}")
        line("             ${guessRuleLine()}")
        line()
    }

    private fun showReveal(game: FirstMinute, roomIndex: Int, guess: Int): Reveal {
        val reveal = game.reveal(roomIndex, guess)
        val room = reveal.room
        line()
        line("  한 달 총비용  ${money(room.totalMonthlyCost)}")
        line("  ${breakdown(room)}")
        line()
        line("  ${cheapestLine(game, reveal)}")
        line("  ${guessLine(reveal)}")
        line("  ${monthCostLine(game, reveal)}")
        line()
        return reveal
    }

    /** The guess's money as the month starts, from the month's opening event, and why it is that much. */
    private fun showOpening(month: Month) {
        for (event in month.opening) {
            line("  " + eventLine(event, month))
            if (event is Event.Guessed) line("  ${guessMoneyWhy(event.gap, event.amount)}")
        }
        line()
    }

    private fun showMonthStart(month: Month) {
        line("9월 한 달이 시작돼요.")
        for (text in monthStartLines(month.room)) line("  $text")
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
        for (event in events) line("  " + eventLine(event, month))
        line()
    }

    private fun showEnding(month: Month, reveal: Reveal, today: TodaysRun?) {
        val ending = checkNotNull(month.ending) { "The month is not over" }
        line(endingLine(ending, month.day, month.room.totalMonthlyCost, month.balance))
        val guess = "${guessMoneyLabel(reveal.guessMoney)} ${signedMoney(reveal.guessMoney)}"
        line("  $guess · 영어 ${month.english}/${Month.MAX_SKILL} · 코딩 ${month.coding}/${Month.MAX_SKILL}")
        line()
        if (today != null) {
            // Not indented, so selecting the whole line copies nothing but the result.
            line("결과 한 줄 (복사해서 붙여 넣으세요)")
            line(todaysRunResult(today, reveal.guessMoney, ending, month.day))
            line()
        }
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
            val blocked = index?.let { month.blockedBy(actions[it]) }
            when {
                index == null -> line("1부터 ${actions.size} 사이의 번호로 골라 주세요.")
                blocked != null -> line("지금은 못 해요. ${whyNot(blocked)}")
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

private fun describe(room: Room): String =
    "월세 ${money(room.rent).padStart(6)}   교통 ${room.zone.number}구역   ${utilitiesLabel(room)}"

private fun describe(action: Action, month: Month): String {
    val note = month.blockedBy(action)?.let { "   (${whyNot(it)})" } ?: ""
    return padToColumns(actionName(action), 14) + actionEffect(action, month) + note
}

/** Pads [text] to [columns] terminal columns, counting each Hangul syllable as two. */
fun padToColumns(text: String, columns: Int): String {
    val width = text.sumOf { if (it in '가'..'힣') 2 else 1 }
    return text + " ".repeat(maxOf(1, columns - width))
}

/** "2" with three choices gives index 1. Anything else gives null. */
fun parseRoomChoice(text: String, roomCount: Int): Int? {
    val number = text.trim().toIntOrNull() ?: return null
    return if (number in 1..roomCount) number - 1 else null
}
