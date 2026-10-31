package vancouvermonth.terminal

import java.time.LocalDate
import kotlin.random.Random
import kotlin.system.exitProcess
import vancouvermonth.core.TodaysRun

/**
 * `--seed 4821` (or `--seed=4821`) replays the same rooms in the first round.
 * `--today` plays today's run, from this computer's date. Asking for both is an error.
 */
fun main(args: Array<String>) {
    val seed = parseSeed(args)
    val today = "--today" in args
    if (today && seed != null) {
        System.err.println("--today와 --seed는 함께 쓸 수 없어요. 하나만 골라 주세요.")
        exitProcess(2)
    }

    val game = TerminalGame(
        input = ::readlnOrNull,
        output = { text ->
            print(text)
            System.out.flush()
        },
    )

    var again = if (today) game.play(todaysRun(LocalDate.now())) else game.play(seed ?: newSeed())
    while (again) {
        again = game.play(newSeed())
    }
    println()
}

private fun newSeed(): Int = Random.nextInt(1_000, 10_000)

fun parseSeed(args: Array<String>): Int? {
    val joined = args.joinToString(" ").replace("=", " ").split(" ").filter { it.isNotBlank() }
    val at = joined.indexOf("--seed")
    return if (at >= 0) joined.getOrNull(at + 1)?.toIntOrNull() else null
}

/** Today's run for a date on this computer's calendar. */
fun todaysRun(date: LocalDate): TodaysRun = TodaysRun(date.year, date.monthValue, date.dayOfMonth)
