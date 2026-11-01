package vancouvermonth.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import vancouvermonth.core.FirstMinute
import vancouvermonth.text.Guide
import vancouvermonth.text.PatchNotes

/** A town to walk in whose nights last until the test says it is morning. */
private class NightLongTown(val quiet: QuietTown = QuietTown()) : WalkableTown by quiet {
    override fun endDay(place: Place) {}

    fun morning() = quiet.onMorning()
}

/**
 * The patch notes (업데이트 소식): the version and the button by the title, the card with every update, the dot for
 * news, the same button at the foot of the button screen, and the link at the foot of the guide in the town.
 */
class PatchNotesCardTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val cards = mutableListOf<PatchNotesCard>()
    private val guides = mutableListOf<FirstRunGuide>()
    private val sounds = mutableListOf<Sound>()
    private val styles = mutableListOf<HTMLElement>()

    @AfterTest
    fun removeScreen() {
        // A card left open would keep listening for keys on the page after the test.
        cards.forEach { it.close() }
        guides.forEach { it.close() }
        sounds.forEach { it.close() }
        root.remove()
        styles.forEach { it.remove() }
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    private fun notesFor(store: KeyValueStore) = PatchNotesCard(Memory { store }).also { cards += it }

    /** Opens seed 1011's first screen with the sound switch and the update news, the news kept in [store]. */
    private fun firstScreen(store: KeyValueStore = FakeStore()): PhoneGame {
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {})
        game.sound = Sound(Memory { FakeStore() }, { NotingSpeaker() }).also { sounds += it }
        game.notes = notesFor(store)
        game.show()
        return game
    }

    private fun notesButton(): HTMLButtonElement =
        assertNotNull(root.querySelector(".patch-open") as? HTMLButtonElement, "No 업데이트 소식: ${root.innerHTML}")

    private val card: HTMLElement?
        get() = root.querySelector(".patch-card") as? HTMLElement

    private fun press(key: String, shift: Boolean = false) {
        val target = document.activeElement ?: document.body!!
        val init = KeyboardEventInit(key = key, shiftKey = shift, bubbles = true, cancelable = true)
        target.dispatchEvent(KeyboardEvent("keydown", init))
    }

    private fun hasNews(button: HTMLElement) = button.classList.contains("patch-news")

    @Test
    fun `the first screen shows the version by the title and 업데이트 소식 next to the sound switch`() {
        firstScreen()
        val title = assertNotNull(root.querySelector(".title"))
        assertEquals(PatchNotes.versionLabel(PatchNotes.CURRENT), title.querySelector(".title-name .patch-current")?.textContent)
        assertEquals("v${PatchNotes.ALL.first().version}", title.querySelector(".patch-current")?.textContent)
        val button = notesButton()
        val sound = assertNotNull(title.querySelector(".sound-switch"))
        assertSame(sound, button.nextElementSibling, "The button is not next to the sound switch")
        assertTrue(title.contains(button))
        assertTrue(button.textContent.orEmpty().startsWith(PatchNotes.TITLE))
        assertEquals("dialog", button.getAttribute("aria-haspopup"))
        // The notes open only on a tap.
        assertNull(card)
    }

    @Test
    fun `laid out as published, a finger on 시작하기 after the reveal hits it, and it starts the month`() {
        usePageStyle()
        firstScreen()
        passIntro(root)
        val game = FirstMinute(1011)
        root.button("${game.cheapestRoomIndex + 1}번 방").click()
        val input = root.querySelector("input") as HTMLInputElement
        input.value = "${game.lowestTotal}"
        input.dispatchEvent(Event("input"))
        root.button("총비용 확인하기").click()
        assertNotNull(root.querySelector(".title .patch-open"))
        // The month-start card's own list stays in the page's flow, under nothing of the update news.
        val list = assertNotNull(root.querySelector(".start .notes") as? HTMLElement)
        assertEquals("static", window.getComputedStyle(list).position)
        val start = root.button("시작하기")
        start.asDynamic().scrollIntoView(js("({ block: 'center' })"))
        val box = start.getBoundingClientRect()
        val hit = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2)
        assertSame(start, hit, "A finger on 시작하기 hits ${(hit as? HTMLElement)?.className}")
        (hit as HTMLElement).click()
        assertNotNull(root.querySelector(".topbar"), "The month did not start")
        window.scrollTo(0.0, 0.0)
    }

    @Test
    fun `the notes open as a dialog, newest first with the newest open, and 닫기 gives the focus back`() {
        firstScreen()
        val button = notesButton()
        button.click()
        val card = assertNotNull(card, "No card after the tap")
        assertEquals("dialog", card.getAttribute("role"))
        assertEquals("true", card.getAttribute("aria-modal"))
        assertEquals(PatchNotes.TITLE, document.getElementById(card.getAttribute("aria-labelledby")!!)?.textContent)
        assertTrue(card.contains(document.activeElement), "The focus did not go into the card")

        val entries = card.querySelectorAll(".patch-entry").asList().map { it as HTMLElement }
        assertEquals(PatchNotes.ALL.size, entries.size)
        entries.forEachIndexed { index, entry ->
            val note = PatchNotes.ALL[index]
            val summary = entry.querySelector("summary")?.textContent.orEmpty()
            assertTrue(PatchNotes.versionLabel(note.version) in summary, summary)
            assertTrue(PatchNotes.dateLabel(note.date) in summary, summary)
            assertTrue(note.headline in summary, summary)
            assertEquals(index == 0, entry.hasAttribute("open"), "Only the newest is open: ${note.version}")
            val text = entry.textContent.orEmpty()
            for ((name, lines) in note.sections) {
                assertTrue(name in text, name)
                for (line in lines) assertTrue(line in text, line)
            }
        }
        // What lies under the card is out of reach meanwhile.
        val cover = assertNotNull(root.querySelector(".patch-cover"))
        for (under in root.children.asList()) {
            if (under !== cover) assertTrue(under.hasAttribute("inert"), "${under.className} is not inert")
        }

        root.button(PatchNotes.CLOSE).click()
        assertNull(this.card)
        assertNull(root.querySelector(".patch-cover"))
        assertSame(button, document.activeElement)
        assertNull(root.querySelector("[inert]"))
    }

    @Test
    fun `Escape closes it, and Tab goes round its versions and 닫기 without leaving it`() {
        firstScreen()
        val button = notesButton()
        button.click()
        val card = assertNotNull(card)
        val stops = card.querySelectorAll("summary, button").asList().map { it as HTMLElement }
        assertEquals(PatchNotes.ALL.size + 1, stops.size)
        press("Tab")
        assertSame(stops.first(), document.activeElement)
        press("Tab", shift = true)
        assertSame(stops.last(), document.activeElement)
        assertEquals(PatchNotes.CLOSE, document.activeElement?.textContent)
        press("Tab")
        assertSame(stops.first(), document.activeElement)
        press("Escape")
        assertNull(this.card)
        assertSame(button, document.activeElement)
    }

    @Test
    fun `the list scrolls inside the card on a phone, upright and turned, with 닫기 in sight`() {
        usePageStyle()
        firstScreen()
        notesButton().click()
        val cover = assertNotNull(root.querySelector(".patch-cover") as? HTMLElement)
        val card = assertNotNull(card)
        val list = assertNotNull(card.querySelector(".patch-list") as? HTMLElement)
        val close = root.button(PatchNotes.CLOSE)
        for ((width, height) in listOf(375 to 812, 320 to 568, 812 to 375, 568 to 320)) {
            pin(cover, width, height)
            val shade = cover.getBoundingClientRect()
            val box = card.getBoundingClientRect()
            assertTrue(box.top >= shade.top && box.bottom <= shade.bottom, "The card leaves a $width x $height screen")
            assertTrue(box.right - box.left <= width, "The card is wider than $width")
            assertTrue(card.scrollWidth <= card.clientWidth, "The card scrolls sideways at $width")
            assertTrue(close.getBoundingClientRect().bottom <= box.bottom, "닫기 is out of sight at $width x $height")
            assertTrue(list.scrollHeight > list.clientHeight, "Nothing to scroll at $width x $height")
            list.scrollTop = 120.0
            assertTrue(list.scrollTop > 0.0, "The list does not scroll at $width x $height")
            list.scrollTop = 0.0
        }
    }

    @Test
    fun `a dot marks news until the notes are opened, and an older kept version shows it again`() {
        val store = FakeStore()
        firstScreen(store)
        // A first-time player sees the dot too, and a screen reader hears it.
        assertTrue(hasNews(notesButton()))
        assertTrue(PatchNotes.NEWS in notesButton().textContent.orEmpty())
        notesButton().click()
        assertEquals(PatchNotes.CURRENT, store.items["vancouver-month.notes"])
        assertFalse(hasNews(notesButton()), "The dot stayed after opening")
        assertFalse(PatchNotes.NEWS in notesButton().textContent.orEmpty())
        press("Escape")

        firstScreen(store)
        assertFalse(hasNews(notesButton()), "The dot came back on the next visit")

        val older = FakeStore().also { it.items["vancouver-month.notes"] = "0.1" }
        firstScreen(older)
        assertTrue(hasNews(notesButton()), "No dot for a player last here on 0.1")

        val unreadable = FakeStore().also { it.items["vancouver-month.notes"] = "seen" }
        firstScreen(unreadable)
        assertTrue(hasNews(notesButton()))
    }

    @Test
    fun `storage that throws still opens and closes the notes, and the dot goes for this page`() {
        firstScreen(RefusingStore())
        val button = notesButton()
        assertTrue(hasNews(button))
        button.click()
        assertNotNull(card)
        assertFalse(hasNews(button))
        press("Escape")
        assertNull(card)
        assertSame(button, document.activeElement)
        // The next visit cannot know, so the dot is back.
        firstScreen(RefusingStore())
        assertTrue(hasNews(notesButton()))
    }

    @Test
    fun `the button screen has the same button at its foot, and it opens the same notes`() {
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {})
        game.notes = notesFor(FakeStore())
        game.show()
        startSeed1011(root)
        assertNotNull(root.querySelector(".topbar"))
        val button = assertNotNull(root.querySelector(".patch-foot .patch-open") as? HTMLButtonElement)
        assertSame(root.lastElementChild, button.parentElement)
        assertTrue(hasNews(button))
        button.click()
        assertEquals(PatchNotes.ALL.size, card?.querySelectorAll(".patch-entry")?.length)
        root.button(PatchNotes.CLOSE).click()
        assertNull(card)
        assertSame(button, document.activeElement)
        assertFalse(hasNews(button))
    }

    /** Starts seed 1011's September in [town] with the guide and the update news, as the page wires them. */
    private fun startInTown(town: WalkableTown, store: KeyValueStore = FakeStore()) {
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { town })
        val memory = Memory { store }
        game.guide = FirstRunGuide().also { guides += it }
        game.notes = PatchNotesCard(memory).also { cards += it }
        game.talkSettleMillis = 0.0
        game.show()
        startSeed1011(root)
    }

    private val guideLink: HTMLButtonElement?
        get() = root.querySelector(".guide-card .patch-link") as? HTMLButtonElement

    private fun helpButton(): HTMLButtonElement =
        assertNotNull(root.querySelector(".hud .guide-open") as? HTMLButtonElement, "No ? in the top card")

    @Test
    fun `the ? card's foot opens the same notes over it, and Escape goes back to the card`() {
        val town = QuietTown()
        startInTown(town)
        helpButton().click()
        val guide = assertNotNull(root.querySelector(".guide-card"), "No guide over the town")
        val link = assertNotNull(guideLink, "No 업데이트 소식 at the guide's foot")
        assertTrue(link.textContent.orEmpty().startsWith(PatchNotes.TITLE))
        assertTrue(hasNews(link))
        // Next to the map's credit, which stays the card's last line.
        assertEquals("guide-credit", guide.lastElementChild?.className)
        assertSame(guide.lastElementChild, link.parentElement?.nextElementSibling)

        link.click()
        val notes = assertNotNull(card, "The link opened nothing")
        assertEquals(PatchNotes.ALL.size, notes.querySelectorAll(".patch-entry").length)
        assertSame(root.lastElementChild, root.querySelector(".patch-cover"), "The notes are not over the guide")
        assertTrue(root.querySelector(".guide")!!.hasAttribute("inert"))
        assertTrue(town.isCovered)

        // Escape closes the notes only, and the guide takes the focus back on its link.
        press("Escape")
        assertNull(card)
        assertNotNull(root.querySelector(".guide-card"), "Escape closed the guide too")
        assertFalse(root.querySelector(".guide")!!.hasAttribute("inert"))
        assertSame(link, document.activeElement)
        assertFalse(hasNews(link))
        press("Escape")
        assertNull(root.querySelector(".guide-card"))
        assertSame(helpButton(), document.activeElement)
        assertFalse(town.isCovered)
        assertNull(root.querySelector("[inert]"))
    }

    @Test
    fun `the ? card has no link to the notes over a talk or while the day changes`() {
        val town = NightLongTown()
        startInTown(town)
        helpButton().click()
        assertNotNull(guideLink, "No link on the card")
        root.button(Guide.CLOSE).click()

        town.onPlace(Place.LIBRARY)
        assertNotNull(root.querySelector(".talk"))
        helpButton().click()
        assertNotNull(root.querySelector(".guide-card"))
        assertNull(guideLink, "The notes could open over a talk")
        press("Escape")
        root.button("그만").click()
        helpButton().click()
        assertNotNull(guideLink, "No link once the talk ended")
        press("Escape")

        // The day is spent, and the night has not ended yet.
        town.onPlace(Place.LIBRARY)
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        helpButton().click()
        assertNull(guideLink, "The notes could open during the change of day")
        press("Escape")
        town.morning()
        helpButton().click()
        assertNotNull(guideLink, "No link in the morning")
        press("Escape")
        assertNull(root.querySelector(".patch-cover"))
    }

    /** Lays the page out with its own style sheet, as it is published (web/karma.config.d/style.js hands it over). */
    private fun usePageStyle() {
        val style = document.createElement("style") as HTMLElement
        style.textContent = js("window.__karma__.config.pageStyle") as String
        document.head!!.appendChild(style)
        styles += style
    }

    /** Pins the notes' shade to a phone [width] by [height] points, in the test browser's window. */
    private fun pin(cover: HTMLElement, width: Int, height: Int) {
        cover.style.setProperty("inset", "auto")
        cover.style.left = "0px"
        cover.style.top = "0px"
        cover.style.width = "${width}px"
        cover.style.height = "${height}px"
    }
}
