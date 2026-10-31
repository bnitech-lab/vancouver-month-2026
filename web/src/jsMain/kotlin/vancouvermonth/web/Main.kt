package vancouvermonth.web

import kotlin.js.Date
import kotlin.random.Random
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URLSearchParams
import vancouvermonth.core.TodaysRun

/**
 * `?seed=1011` replays the same rooms and the same luck. Without it, the page picks a new seed and writes it
 * into the address, so a reload or a shared link replays the same run.
 * `?today` plays today's run from the browser's date and writes the date into the address, as in
 * `?today=2026-10-30`, so a reload or a shared link replays that date's run. `?seed` wins over `?today`.
 */
fun main() {
    val root = document.getElementById("app") as? HTMLElement ?: return
    val query = window.location.search
    val fromQuery = seedFrom(query)
    val today = if (fromQuery == null) todayFrom(query, Date()) else null
    val seed = fromQuery ?: today?.seed ?: newSeed()
    PhoneGame(root, seed, playAgain = { window.location.search = "?seed=${newSeed()}" }, today = today).show()
    when {
        today != null -> window.history.replaceState(null, "", todayQuery(today))
        fromQuery == null -> window.history.replaceState(null, "", "?seed=$seed")
    }
}

/** The seed in a query such as "?seed=1011", or null when there is none. */
fun seedFrom(query: String): Int? = URLSearchParams(query).get("seed")?.trim()?.toIntOrNull()

/**
 * Today's run from a query such as "?today" or "?today=2026-10-30", or null when the query has no "today".
 * A bare "?today", or a date that is not a real one, means [now] on the browser's own calendar.
 * Null too when that calendar is outside 2000 to 2099, so the page plays a new run instead of failing.
 */
fun todayFrom(query: String, now: Date): TodaysRun? {
    val value = URLSearchParams(query).get("today") ?: return null
    // A JavaScript date counts months from 0.
    return TodaysRun.parse(value) ?: TodaysRun.parse("${now.getFullYear()}-${now.getMonth() + 1}-${now.getDate()}")
}

/** "?today=2026-10-30", the address of a date's run. */
fun todayQuery(run: TodaysRun): String = "?today=$run"

private fun newSeed(): Int = Random.nextInt(1_000, 10_000)
