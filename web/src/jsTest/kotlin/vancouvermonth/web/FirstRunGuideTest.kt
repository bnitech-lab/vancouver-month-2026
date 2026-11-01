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
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import vancouvermonth.text.Guide
import vancouvermonth.text.goalLine

/** The card on how to play: from the "?" in the town's top card only, never by itself, and never on the button screen. */
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

    /** Opens seed 1011 and starts September in a town or with buttons. */
    private fun startMonth(inTown: Boolean = true) {
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { if (inTown) town else null })
        game.guide = FirstRunGuide().also { guides += it }
        game.talkSettleMillis = 0.0
        game.show()
        startSeed1011(root)
    }

    private val card: HTMLElement?
        get() = root.querySelector(".guide-card") as? HTMLElement

    private fun press(key: String, shift: Boolean = false) {
        val target = document.activeElement ?: document.body!!
        val init = KeyboardEventInit(key = key, shiftKey = shift, bubbles = true, cancelable = true)
        target.dispatchEvent(KeyboardEvent("keydown", init))
    }

    private fun helpButton(): HTMLButtonElement =
        assertNotNull(root.querySelector(".hud .guide-open") as? HTMLButtonElement, "No ? in the top card")

    @Test
    fun `the guide does not open by itself, and the ? opens it as a dialog with the focus on its button`() {
        startMonth()
        assertNull(card, "The guide opened by itself")
        assertNotNull(root.querySelector(".town-hint"), "The first day's hint is not there")
        helpButton().click()
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
        assertSame(root.button(Guide.CLOSE), document.activeElement)
        // The map's credit, as the two cities' licences ask, at the card's foot, after its button: Vancouver's line, then
        // Burnaby's right under it, each with its licence's name a link to the licence that opens in a new tab.
        val burnaby = assertNotNull(card.lastElementChild)
        val credit = assertNotNull(burnaby.previousElementSibling)
        assertEquals(Guide.MAP_CREDIT, credit.textContent)
        assertEquals("guide-credit", credit.className)
        val link = assertNotNull(credit.querySelector("a") as? HTMLAnchorElement, "No link to the licence")
        assertEquals(Guide.MAP_LICENCE, link.textContent)
        assertEquals(Guide.MAP_LICENCE_URL, link.href)
        assertEquals("_blank", link.target)
        assertEquals("noopener", link.rel)
        assertEquals("${Guide.MAP_LICENCE} (새 탭에서 열려요)", link.getAttribute("aria-label"))
        assertEquals(Guide.BURNABY_MAP_LINE, burnaby.textContent)
        assertEquals("guide-credit burnaby-credit", burnaby.className)
        val burnabyLink = assertNotNull(burnaby.querySelector("a") as? HTMLAnchorElement, "No link to Burnaby's licence")
        assertEquals(Guide.BURNABY_MAP_LICENCE, burnabyLink.textContent)
        assertEquals(Guide.BURNABY_MAP_LICENCE_URL, burnabyLink.href)
        assertEquals("_blank", burnabyLink.target)
        assertEquals("noopener", burnabyLink.rel)
        assertEquals("${Guide.BURNABY_MAP_LICENCE} (새 탭에서 열려요)", burnabyLink.getAttribute("aria-label"))
        // Over the town, its top card and its first-day hint.
        assertTrue(root.querySelector(".over-town") != null && root.querySelector(".guide") != null)
    }

    @Test
    fun `while the card is open the town and its top card are inert, and after it they are not`() {
        startMonth()
        assertNull(root.querySelector("[inert]"))
        helpButton().click()
        val under = listOf(".walk-town", ".over-town").map { assertNotNull(root.querySelector(it), it) }
        for (element in under) assertTrue(element.hasAttribute("inert"), "${element.className} is not inert")
        assertTrue(!card!!.hasAttribute("inert"))
        root.button(Guide.CLOSE).click()
        for (element in under) assertTrue(!element.hasAttribute("inert"), "${element.className} stayed inert")
        helpButton().click()
        for (element in under) assertTrue(element.hasAttribute("inert"))
        press("Escape")
        assertNull(root.querySelector("[inert]"))
    }

    @Test
    fun `the town counts as covered while the card is open, so its own life slows down`() {
        startMonth()
        assertTrue(!town.isCovered, "The town is covered before the card opens")
        helpButton().click()
        assertTrue(town.isCovered, "The town was not told the card covers it")
        root.button(Guide.CLOSE).click()
        assertTrue(!town.isCovered, "The town stayed covered after the card closed")
        helpButton().click()
        assertTrue(town.isCovered)
        press("Escape")
        assertTrue(!town.isCovered)
    }

    @Test
    fun `닫기 closes it, the focus goes back to the ?, and it opens by itself on no later visit either`() {
        startMonth()
        helpButton().click()
        root.button(Guide.CLOSE).click()
        assertNull(card)
        assertSame(helpButton(), document.activeElement)

        startMonth()
        assertNull(card, "The guide opened by itself on the second visit")
        assertNotNull(root.querySelector(".hud"))
    }

    @Test
    fun `Escape closes it, and the ? opens it again with the focus going there and back`() {
        startMonth()
        helpButton().click()
        press("Escape")
        assertNull(card)
        assertSame(helpButton(), document.activeElement)
        assertEquals(Guide.HELP, helpButton().getAttribute("aria-label"))

        helpButton().click()
        assertNotNull(card)
        assertSame(root.button(Guide.CLOSE), document.activeElement)
        // Tab goes round the card's button and its links to the two licences while it is open, either way.
        val link = assertNotNull(card!!.querySelector(".guide-credit a") as? HTMLElement)
        val burnaby = assertNotNull(card!!.querySelector(".burnaby-credit a") as? HTMLElement)
        press("Tab")
        assertSame(link, document.activeElement)
        press("Tab")
        assertSame(burnaby, document.activeElement)
        press("Tab")
        assertSame(root.button(Guide.CLOSE), document.activeElement)
        press("Tab", shift = true)
        assertSame(burnaby, document.activeElement)
        press("Tab", shift = true)
        assertSame(link, document.activeElement)
        press("Tab", shift = true)
        assertSame(root.button(Guide.CLOSE), document.activeElement)
        press("Escape")
        assertNull(card)
        assertSame(helpButton(), document.activeElement)
    }

    @Test
    fun `the button screen has no guide and no ?`() {
        startMonth(inTown = false)
        assertNotNull(root.querySelector(".topbar"))
        assertNull(card)
        assertNull(root.querySelector(".guide-open"))
    }

    @Test
    fun `the ? stays in the top card through a talk and a day, and the guide does not open by itself`() {
        startMonth()
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
        startMonth()
        helpButton().click()
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
