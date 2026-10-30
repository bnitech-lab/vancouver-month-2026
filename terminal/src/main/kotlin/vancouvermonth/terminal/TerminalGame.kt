package vancouvermonth.terminal

import java.util.Locale
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Room
import vancouvermonth.core.RoomDealer
import vancouvermonth.core.Zone

/**
 * Plays one first minute as text. Input and output are passed in, so a test can play a whole round.
 * [input] returns null when the input ends (Ctrl+D), which ends the game.
 */
class TerminalGame(
    private val input: () -> String?,
    private val output: (String) -> Unit,
) {
    /** Plays one round and returns true when the player wants another. */
    fun play(seed: Int): Boolean {
        val game = FirstMinute(seed)
        showRooms(game)

        val roomIndex = askRoom(game.rooms.size) ?: return false
        val guess = askAmount() ?: return false
        showReveal(game, roomIndex, guess)

        return askAgain()
    }

    private fun showRooms(game: FirstMinute) {
        line()
        line("BNI Tech")
        line()
        line("밴쿠버 한 달 (가제)                판 번호 ${game.seed}")
        line("잔고 ${money(game.startingBalance)} · 9월 1일")
        line()
        line("할 일: 한 달 총비용이 가장 싼 방을 고르세요.")
        line("      한 달 총비용 = 월세 + 공과금 + 교통 정기권")
        line()
        line("요금표")
        line("  교통 정기권   " + Zone.entries.joinToString(" · ") { "${it.number}구역 ${money(it.monthlyPass)}" })
        val utilities = RoomDealer.UTILITIES_RANGE
        line("  공과금       포함이면 ${money(0)}, 별도면 한 달 ${money(utilities.first)}~${money(utilities.last)} (방마다 다름)")
        line()
        game.rooms.forEachIndexed { index, room -> line("  ${index + 1}) ${describe(room)}") }
        line()
    }

    private fun showReveal(game: FirstMinute, roomIndex: Int, guess: Int) {
        val reveal = game.reveal(roomIndex, guess)
        val room = reveal.room
        line()
        line("  한 달 총비용  ${money(room.totalMonthlyCost)}")
        line("  월세 ${money(room.rent)} + 공과금 ${money(room.utilities)} + 교통 ${money(room.zone.monthlyPass)}")
        line()
        if (reveal.pickedCheapest) {
            line("  총비용이 가장 싼 방을 골랐어요.   +${reveal.cheapestPoints}")
        } else {
            val cheapest = "${game.cheapestRoomIndex + 1}번이었어요 (${money(game.lowestTotal)})"
            line("  총비용이 가장 싼 방은 $cheapest.   +${reveal.cheapestPoints}")
        }
        if (reveal.guessGap == 0) {
            line("  짐작이 정확해요.   +${reveal.guessPoints}")
        } else {
            line("  내 짐작과 차이 ${money(reveal.guessGap)}   +${reveal.guessPoints}")
        }
        line("  이번 판 점수 ${reveal.score} / ${FirstMinute.MAX_SCORE}")
        line()
        line("  첫 달을 내고 남은 잔고 ${money(reveal.balanceAfterFirstMonth)}")
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

fun money(amount: Int): String = "$" + String.format(Locale.US, "%,d", amount)

/** "2" with three rooms gives index 1. Anything else gives null. */
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
