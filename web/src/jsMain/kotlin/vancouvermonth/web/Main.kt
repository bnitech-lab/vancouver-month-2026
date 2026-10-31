package vancouvermonth.web

import kotlin.js.Date
import kotlin.random.Random
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URLSearchParams
import vancouvermonth.core.TodaysRun

/**
 * Plays the run the address asks for (see [openingFor]), then makes the address name that run.
 * The month is played in a 3D town the player walks in, where the browser can draw one.
 */
fun main() {
    val root = document.getElementById("app") as? HTMLElement ?: return
    val opening = openingFor(window.location.search, Date(), newSeed())
    val playAgain = { window.location.search = "?seed=${newSeed()}" }
    val today = opening.today
    // The towns are made when the month starts, not now, so the room screen does not wait for WebGL.
    // The town to walk in comes first; the picture of a town is for a phone that asks for less motion.
    val makeTown = { openTown() }
    val makeWalkableTown = { openWalkableTown() }
    val game = if (today != null) {
        PhoneGame(root, today, playAgain, makeTown, makeWalkableTown)
    } else {
        PhoneGame(root, opening.seed, playAgain, makeTown, makeWalkableTown)
    }
    // A gentle sound the player can switch on.
    val memory = pageMemory()
    game.sound = pageSound(memory)
    game.show()
    // The game is drawn first, so the screen does not depend on the address changing.
    opening.address?.let { window.history.replaceState(null, "", it) }
}

/** The run a page plays, today's run when it is one, and the address to write, or null to keep the address. */
data class Opening(val seed: Int, val today: TodaysRun?, val address: String?)

/**
 * What a page opened with [query] plays. `?seed=1011` wins and keeps its address, so a link replays that run.
 * `?today` or `?today=2026-10-30` plays that date's run (the browser's date, [now], when none reads) and writes
 * the date into the address, so a reload or a shared link replays it even on another day. Anything else plays
 * [newSeed] and writes it into the address.
 */
fun openingFor(query: String, now: Date, newSeed: Int): Opening {
    val seed = seedFrom(query)
    if (seed != null) return Opening(seed, null, null)
    val today = todayFrom(query, now)
    if (today != null) return Opening(today.seed, today, todayQuery(today))
    return Opening(newSeed, null, "?seed=$newSeed")
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
