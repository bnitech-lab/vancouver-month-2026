package vancouvermonth.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.TaxiFare
import vancouvermonth.save.MonthRecord
import vancouvermonth.text.Transit
import vancouvermonth.text.Transport
import vancouvermonth.text.money

/** The SkyTrain, the bus, the water bus and the taxi as the game plays them, in a town that draws nothing. */
class TransitInGameTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val store = FakeStore()
    private val start = FirstMinute(1011).let { it.startMonth(it.cheapestRoomIndex, it.lowestTotal).balance }

    @AfterTest
    fun removeScreen() {
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    /** Opens the page on [store] in [town] (or with buttons for null) and starts seed 1011's September. */
    private fun started(town: WalkableTown?): PhoneGame {
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { town })
        game.slot = SaveSlot(Memory { store })
        game.talkSettleMillis = 0.0
        game.show()
        startSeed1011(root)
        return game
    }

    private fun hud(): String = root.querySelector(".hud")?.textContent.orEmpty()

    private fun goButtons(): List<HTMLButtonElement> =
        root.querySelectorAll(".chooser .go").asList().map { it as HTMLButtonElement }

    private fun goButton(name: String): HTMLButtonElement =
        goButtons().firstOrNull { it.querySelector(".action-name")?.textContent == name } ?: error("No $name: ${root.textContent}")

    @Test
    fun `a stop offers every other stop of its line, on the pass or for its fare, and 그만 closes it`() {
        val town = QuietTown()
        started(town)
        town.onStop(Stop(Transport.SKYTRAIN, 1))
        assertEquals(listOf("다운타운역", "크릭 끝역", "동쪽역"), goButtons().map { it.querySelector(".action-name")?.textContent })
        assertTrue(goButtons().all { Transit.PASS in it.textContent.orEmpty() && !it.disabled })
        assertEquals("스카이트레인 · 터널 입구역", root.querySelector(".chooser")?.getAttribute("aria-label"))
        // No taxi while the chooser lies over the town.
        assertFalse(Transit.CALL_TAXI in hud())
        root.button(Transit.STOP).click()
        assertNull(root.querySelector(".chooser"))
        assertTrue(Transit.CALL_TAXI in hud())
        town.onStop(Stop(Transport.WATER_BUS, 2))
        assertEquals(2, goButtons().size)
        assertTrue(goButtons().all { "\$4" in it.textContent.orEmpty() })
    }

    @Test
    fun `a fare is paid once, as the ride sets off, and heads the next day's log; the pass pays nothing`() {
        val town = QuietTown()
        started(town)
        town.onStop(Stop(Transport.BUS, 4))
        goButton("공원 입구").click()
        assertEquals(Transport.BUS, town.travelling)
        assertTrue(money(start) in hud())
        assertTrue(Transit.riding(Transport.BUS, null) in hud(), hud())
        town.arrive()
        town.onStop(Stop(Transport.WATER_BUS, 0))
        goButton("남쪽 기슭").click()
        assertTrue(money(start - 4) in hud(), hud())
        assertTrue(Transit.riding(Transport.WATER_BUS, 4) in hud(), hud())
        town.arrive()
        assertTrue(money(start - 4) in hud(), hud())
        assertFalse("타는 중" in hud())
        val kept = assertNotNull(MonthRecord.decode(store.items[SaveSlot.KEY]))
        assertTrue(kept.encode().endsWith("|w4"), kept.encode())
    }

    @Test
    fun `when the town cannot set off, a fare is not paid, kept or logged`() {
        val town = QuietTown()
        started(town)
        val keptBefore = store.items[SaveSlot.KEY]
        town.canTravelNow = false
        town.onStop(Stop(Transport.WATER_BUS, 0))
        goButton("남쪽 기슭").click()
        root.button(Transit.STOP).click()
        root.button(Transit.CALL_TAXI).click()
        goButton("회사").click()
        assertNull(town.travelling)
        assertTrue(money(start) in hud(), hud())
        assertEquals(keptBefore, store.items[SaveSlot.KEY])
        // Nothing heads the next day's log either.
        root.button(Transit.STOP).click()
        town.onPlace(Place.HOME)
        assertNotNull(root.querySelector(".talk .spend") as? HTMLButtonElement).click()
        val log = root.querySelector(".log")?.textContent.orEmpty()
        assertFalse("탔어요" in log, log)
    }

    @Test
    fun `with nowhere a taxi can go, its card says so`() {
        val town = QuietTown()
        town.taxiSquares = null
        started(town)
        root.button(Transit.CALL_TAXI).click()
        assertTrue(goButtons().isEmpty())
        assertTrue(Transit.NOWHERE in root.querySelector(".chooser")?.textContent.orEmpty())
    }

    @Test
    fun `a taxi shows each fare from where the character stands, and a place it cannot reach is left out`() {
        val town = QuietTown()
        town.taxiSquares = 33.0
        started(town)
        root.button(Transit.CALL_TAXI).click()
        val fare = TaxiFare.forDistance(33.0)
        assertEquals(TransitLines.taxiStops.size, goButtons().size)
        assertTrue(goButtons().all { money(fare) in it.textContent.orEmpty() })
        goButton("회사").click()
        assertEquals(Transport.TAXI, town.travelling)
        assertTrue(money(start - fare) in hud(), hud())
        assertTrue(Transit.riding(Transport.TAXI, fare) in hud(), hud())
        // Out on the way: on foot, nothing more paid.
        root.button("내리기").click()
        assertNull(town.travelling)
        assertTrue(money(start - fare) in hud(), hud())
        town.taxiSquares = null
        root.button(Transit.CALL_TAXI).click()
        assertTrue(goButtons().isEmpty())
    }

    @Test
    fun `while travelling no talk opens and no day ends, and the button screen has no transport at all`() {
        val town = QuietTown()
        started(town)
        town.onStop(Stop(Transport.SKYTRAIN, 1))
        goButton("동쪽역").click()
        town.onPlace(Place.HOME)
        town.onShop()
        town.onStop(Stop(Transport.BUS, 0))
        assertNull(root.querySelector(".talk"), root.textContent)
        assertEquals(Transport.SKYTRAIN, town.travelling)
        town.arrive()
        town.onPlace(Place.HOME)
        assertNotNull(root.querySelector(".talk .spend"))

        root.innerHTML = ""
        store.items.remove(SaveSlot.KEY)
        started(null)
        assertFalse(Transit.CALL_TAXI in root.textContent.orEmpty())
        assertNull(root.querySelector(".chooser"))
    }
}
