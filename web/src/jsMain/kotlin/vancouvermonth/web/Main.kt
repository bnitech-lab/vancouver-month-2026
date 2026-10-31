package vancouvermonth.web

import kotlin.random.Random
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URLSearchParams

/**
 * `?seed=1011` replays the same rooms and the same luck. Without it, the page picks a new seed and writes it
 * into the address, so a reload or a shared link replays the same run.
 */
fun main() {
    val root = document.getElementById("app") as? HTMLElement ?: return
    val fromQuery = seedFrom(window.location.search)
    val seed = fromQuery ?: newSeed()
    PhoneGame(root, seed, playAgain = { window.location.search = "?seed=${newSeed()}" }).show()
    if (fromQuery == null) window.history.replaceState(null, "", "?seed=$seed")
}

/** The seed in a query such as "?seed=1011", or null when there is none. */
fun seedFrom(query: String): Int? = URLSearchParams(query).get("seed")?.trim()?.toIntOrNull()

private fun newSeed(): Int = Random.nextInt(1_000, 10_000)
