package vancouvermonth.terminal

import kotlin.random.Random

/** `--seed 4821` (or `--seed=4821`) replays the same rooms in the first round. */
fun main(args: Array<String>) {
    val game = TerminalGame(
        input = ::readlnOrNull,
        output = { text ->
            print(text)
            System.out.flush()
        },
    )

    var seed = parseSeed(args) ?: newSeed()
    while (game.play(seed)) {
        seed = newSeed()
    }
    println()
}

private fun newSeed(): Int = Random.nextInt(1_000, 10_000)

fun parseSeed(args: Array<String>): Int? {
    val joined = args.joinToString(" ").replace("=", " ").split(" ").filter { it.isNotBlank() }
    val at = joined.indexOf("--seed")
    return if (at >= 0) joined.getOrNull(at + 1)?.toIntOrNull() else null
}
