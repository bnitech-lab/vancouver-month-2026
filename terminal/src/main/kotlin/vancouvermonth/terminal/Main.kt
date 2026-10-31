package vancouvermonth.terminal

import java.time.LocalDate
import kotlin.random.Random
import kotlin.system.exitProcess
import vancouvermonth.core.TodaysRun

/**
 * `--seed 4821` (or `--seed=4821`) replays the same rooms in the first round.
 * `--today` plays today's run, from this computer's date, and `--today 2026-10-29` (or `--today=2026-10-29`)
 * plays that date's run. Asking for both a seed and today's run, or for a date that does not exist, is an error.
 */
fun main(args: Array<String>) {
    val seed = parseSeed(args)
    val todayText = parseToday(args)
    if (todayText != null && seed != null) {
        System.err.println("--today와 --seed는 함께 쓸 수 없어요. 하나만 골라 주세요.")
        exitProcess(2)
    }
    // A bare --today means this computer's date, which LocalDate writes as "2026-10-30".
    val dateText = if (todayText == "") LocalDate.now().toString() else todayText
    val today = dateText?.let { TodaysRun.parse(it) }
    if (dateText != null && today == null) {
        System.err.println("날짜를 읽지 못했어요: $dateText. 2000~2099년의 날짜로 적어 주세요. 예: --today=2026-10-30")
        exitProcess(2)
    }

    val game = TerminalGame(
        input = ::readlnOrNull,
        output = { text ->
            print(text)
            System.out.flush()
        },
    )

    var again = if (today != null) game.play(today) else game.play(seed ?: newSeed())
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

/**
 * The date after `--today`, as in `--today=2026-10-29` or `--today 2026-10-29`: "" for a bare `--today`,
 * and null when there is no `--today` at all.
 */
fun parseToday(args: Array<String>): String? {
    for ((index, arg) in args.withIndex()) {
        if (arg.startsWith("--today=")) return arg.removePrefix("--today=")
        if (arg == "--today") {
            val next = args.getOrNull(index + 1)
            return if (next == null || next.startsWith("--")) "" else next
        }
    }
    return null
}
