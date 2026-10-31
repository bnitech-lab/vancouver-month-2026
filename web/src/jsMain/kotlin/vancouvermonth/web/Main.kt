package vancouvermonth.web

import kotlin.random.Random
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URLSearchParams

/** `?seed=1011` replays the same rooms and the same luck. Without it, every page load is a new run. */
fun main() {
    val root = document.getElementById("app") as? HTMLElement ?: return
    val seed = seedFrom(window.location.search) ?: newSeed()
    PhoneGame(root, seed, playAgain = { window.location.search = "?seed=${newSeed()}" }).show()
}

/** The seed in a query such as "?seed=1011", or null when there is none. */
fun seedFrom(query: String): Int? = URLSearchParams(query).get("seed")?.trim()?.toIntOrNull()

private fun newSeed(): Int = Random.nextInt(1_000, 10_000)
