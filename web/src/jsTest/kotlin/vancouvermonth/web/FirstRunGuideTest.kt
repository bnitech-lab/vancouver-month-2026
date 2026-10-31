package vancouvermonth.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import vancouvermonth.text.Guide
import vancouvermonth.text.goalLine

/** The card on how to play: the first time the town opens, from the "?" after that, and never on the button screen. */
class FirstRunGuideTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }

    private val guides = mutableListOf<FirstRunGuide>()

    @AfterTest
    fun removeScreen() {
        // A card left open would keep listening for keys on the page after the test.
        guides.forEach { it.close() }
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    private val town = QuietTown()

    /** Opens seed 1011 with [store] as the page's storage, and starts September in a town or with buttons. */
    private fun startMonth(store: KeyValueStore, inTown: Boolean = true) {
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { if (inTown) town else null })
        game.guide = FirstRunGuide(Memory { store }).also { guides += it }
        game.talkSettleMillis = 0.0
        game.show()
        startSeed1011(root)
    }

    private val card: HTMLElement?
        get() = root.querySelector(".guide-card") as? HTMLElement

    private fun press(key: String) {
        val target = document.activeElement ?: document.body!!
        target.dispatchEvent(KeyboardEvent("keydown", KeyboardEventInit(key = key, bubbles = true, cancelable = true)))
    }

    private fun helpButton(): HTMLButtonElement =
        assertNotNull(root.querySelector(".hud .guide-open") as? HTMLButtonElement, "No ? in the top card")

    @Test
    fun `the guide shows the first time the town opens, as a dialog with the focus on its button`() {
        startMonth(FakeStore())
        val card = assertNotNull(card, "No guide over the town")
        assertEquals("dialog", card.getAttribute("role"))
        assertEquals("true", card.getAttribute("aria-modal"))
        val title = assertNotNull(document.getElementById(card.getAttribute("aria-labelledby")!!))
        assertEquals(Guide.TITLE, title.textContent)
        val text = card.textContent.orEmpty()
        for (line in Guide.LINES) assertTrue(line in text, "Missing: $line")
        assertTrue(goalLine() in text)
        // The lines and the goal describe the card, since the focus lands on its button.
        val described = card.getAttribute("aria-describedby")!!.split(" ").map { document.getElementById(it)!!.textContent }
        assertEquals(listOf(Guide.LINES.joinToString(""), goalLine()), described)
        assertSame(root.button(Guide.START), document.activeElement)
        // The map's credit, as its licence asks, at the card's foot, after its button.
        val credit = assertNotNull(card.lastElementChild)
        assertEquals(Guide.MAP_CREDIT, credit.textContent)
        assertEquals("guide-credit", credit.className)
        // Over the town, its top card and its first-day hint.
        assertTrue(root.querySelector(".over-town") != null && root.querySelector(".guide") != null)
    }

    @Test
    fun `while the card is open the town and its top card are inert, and after it they are not`() {
        startMonth(FakeStore())
        val under = listOf(".walk-town", ".over-town").map { assertNotNull(root.querySelector(it), it) }
        for (element in under) assertTrue(element.hasAttribute("inert"), "${element.className} is not inert")
        assertTrue(!card!!.hasAttribute("inert"))
        root.button(Guide.START).click()
        for (element in under) assertTrue(!element.hasAttribute("inert"), "${element.className} stayed inert")
        helpButton().click()
        for (element in under) assertTrue(element.hasAttribute("inert"))
        press("Escape")
        assertNull(root.querySelector("[inert]"))
    }

    @Test
    fun `the town counts as covered while the card is open, so its own life slows down`() {
        startMonth(FakeStore())
        assertTrue(town.isCovered, "The town was not told the card covers it")
        root.button(Guide.START).click()
        assertTrue(!town.isCovered, "The town stayed covered after the card closed")
        helpButton().click()
        assertTrue(town.isCovered)
        press("Escape")
        assertTrue(!town.isCovered)
    }

    @Test
    fun `시작 closes it, the focus goes back to the ?, and the next visit has no guide`() {
        val store = FakeStore()
        startMonth(store)
        root.button(Guide.START).click()
        assertNull(card)
        assertSame(helpButton(), document.activeElement)

        startMonth(store)
        assertNull(card, "The guide opened again on the second visit")
        assertNotNull(root.querySelector(".hud"))
    }

    @Test
    fun `Escape closes it, and the ? opens it again with the focus going there and back`() {
        startMonth(FakeStore())
        press("Escape")
        assertNull(card)
        assertSame(helpButton(), document.activeElement)
        assertEquals(Guide.HELP, helpButton().getAttribute("aria-label"))

        helpButton().click()
        assertNotNull(card)
        assertSame(root.button(Guide.START), document.activeElement)
        // Tab stays in the card while it is open.
        press("Tab")
        assertSame(root.button(Guide.START), document.activeElement)
        press("Escape")
        assertNull(card)
        assertSame(helpButton(), document.activeElement)
    }

    @Test
    fun `where storage throws, the guide shows on every visit and still closes`() {
        repeat(2) {
            startMonth(RefusingStore())
            assertNotNull(card, "No guide on visit ${it + 1}")
            root.button(Guide.START).click()
            assertNull(card)
        }
    }

    @Test
    fun `the button screen has no guide and no ?`() {
        startMonth(FakeStore(), inTown = false)
        assertNotNull(root.querySelector(".topbar"))
        assertNull(card)
        assertNull(root.querySelector(".guide-open"))
    }

    @Test
    fun `the ? stays in the top card through a talk and a day, and the guide does not open again by itself`() {
        startMonth(FakeStore())
        root.button(Guide.START).click()
        town.onPlace(Place.LIBRARY)
        assertNotNull(root.querySelector(".talk"))
        assertNotNull(helpButton())
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        assertTrue("9월 2일에 있었던 일" in root.textContent.orEmpty())
        assertNotNull(helpButton())
        assertNull(card)
    }

    @Test
    fun `a plain event named keydown leaves the card open and breaks nothing`() {
        startMonth(FakeStore())
        // An error in a listener does not reach the one who sent the event; the page reports it to the window.
        val errors = mutableListOf<String>()
        val onError: (Event) -> Unit = { errors += it.asDynamic().message.toString() }
        window.addEventListener("error", onError)
        try {
            document.dispatchEvent(Event("keydown"))
            document.body!!.dispatchEvent(Event("keydown", js("({ bubbles: true })")))
        } finally {
            window.removeEventListener("error", onError)
        }
        assertEquals(emptyList(), errors)
        assertNotNull(card)
        press("Escape")
        assertNull(card)
    }
}
