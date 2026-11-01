package vancouvermonth.web

import kotlin.js.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.asList
import vancouvermonth.core.Action
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.text.Continue
import vancouvermonth.text.Guide
import vancouvermonth.text.Intro
import vancouvermonth.text.PatchNotes
import vancouvermonth.text.arrivalLines
import vancouvermonth.text.continueFrom
import vancouvermonth.text.date
import vancouvermonth.text.goalLine
import vancouvermonth.text.guessExamplesLine
import vancouvermonth.text.guessRuleLine
import vancouvermonth.text.money

/**
 * The intro, the page's first screen: the story, how to play and how the guess pays, then 시작하기 to the rooms, or
 * 이어하기 over a kept month; laid out so its way in stays in sight on a small phone, upright or turned.
 */
class IntroTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val frames = mutableListOf<HTMLIFrameElement>()
    private val sounds = mutableListOf<Sound>()
    private val cards = mutableListOf<PatchNotesCard>()
    private val store = FakeStore()

    @AfterTest
    fun removeScreen() {
        sounds.forEach { it.close() }
        cards.forEach { it.close() }
        root.remove()
        frames.forEach { it.remove() }
    }

    /** Opens seed 1011's page in [into] as the page wires it: the sound switch, the update news and the month kept. */
    private fun open(into: HTMLElement = root, seed: Int = 1011, atRooms: Boolean = false): PhoneGame {
        into.innerHTML = ""
        val game = PhoneGame(into, seed, playAgain = {})
        game.sound = Sound(Memory { FakeStore() }, { NotingSpeaker() }).also { sounds += it }
        game.notes = PatchNotesCard(Memory { FakeStore() }).also { cards += it }
        game.slot = SaveSlot(Memory { store })
        game.introPassed = atRooms
        game.show()
        return game
    }

    private fun text(selector: String, into: HTMLElement = root): String =
        assertNotNull(into.querySelector(selector), "No $selector in: ${into.textContent}").textContent.orEmpty()

    private fun buttons(into: HTMLElement = root): List<String> =
        into.querySelectorAll("button").asList().map { it.textContent.orEmpty() }

    @Test
    fun `the intro has the title, the version, the update news and the sound switch, then the story`() {
        open()
        val title = assertNotNull(root.querySelector(".title"))
        assertEquals("BNI Tech", text(".title .brand"))
        assertEquals("밴쿠버 한 달 (가제)", text(".title h1"))
        assertEquals(PatchNotes.versionLabel(PatchNotes.CURRENT), text(".title .patch-current"))
        assertNotNull(title.querySelector(".patch-open"), "No 업데이트 소식")
        assertNotNull(title.querySelector(".sound-switch"), "No sound switch")
        assertEquals("판 번호 1011", text(".title .run"))
        val story = assertNotNull(root.querySelector(".intro .story"))
        assertEquals(arrivalLines(FirstMinute.STARTING_BALANCE).joinToString(""), story.textContent)
        assertEquals(goalLine(), text(".intro .story .goal"))
        assertTrue("9월 1일, 딱 \$3,000 들고 밴쿠버에 막 도착했어요." in story.textContent.orEmpty())
        assertNull(root.querySelector(".rooms"), "The rooms came with the intro")
        assertNull(root.querySelector(".kept"), "A kept month was offered with none kept")
    }

    @Test
    fun `the intro says how to play, how the guess pays, and the map's credit, in that order after the story`() {
        open()
        val lines = root.querySelectorAll(".intro .how-to .how-to-lines li").asList().map { it.textContent }
        assertEquals(Guide.LINES, lines)
        assertEquals(Guide.TITLE, text(".intro .how-to h2"))
        assertEquals(Intro.GUESS, text(".intro .guess-pays h2"))
        assertTrue(Intro.GUESS_WHAT in text(".intro .guess-pays"))
        assertEquals("맞힐수록 최대 \$100을 더 받고, \$100보다 더 틀리면 그만큼 잃어요(최대 \$300).", text(".intro .guess-rule-line"))
        assertEquals(guessRuleLine(), text(".intro .guess-rule-line"))
        assertEquals(guessExamplesLine(), text(".intro .guess-examples"))
        val intro = assertNotNull(root.querySelector(".intro"))
        val order = listOf(".story", ".how-to", ".guess-pays", ".intro-start", ".intro-credit").map { selector ->
            intro.children.asList().indexOfFirst { it.matches(selector) }
        }
        assertTrue(order.all { it >= 0 }, "$order")
        assertEquals(order.sorted(), order)
        // The map is the City's open data, and its licence asks for its statement with a link wherever it shows.
        val link = assertNotNull(root.querySelector(".intro-credit a") as? HTMLAnchorElement)
        assertEquals(Guide.MAP_CREDIT, text(".intro-credit"))
        assertEquals(Guide.MAP_LICENCE_URL, link.href)
        assertEquals("_blank", link.target)
        assertEquals("noopener", link.rel)
    }

    @Test
    fun `시작하기 leads to the rooms, which ask for the room and the guess as before`() {
        open()
        assertEquals(listOf(Intro.START), buttons(assertNotNull(root.querySelector(".intro-start") as? HTMLElement)))
        root.button(Intro.START).click()
        assertNull(root.querySelector(".intro"))
        assertNotNull(root.querySelector(".rooms"))
        assertNotNull(root.querySelector(".guess"))
        assertTrue("판 번호 1011" in text(".title"))
        startSeed1011(root)
        assertTrue(date(Month.FIRST_DAY) in text(".topbar"))
    }

    @Test
    fun `with a month kept, the intro opens with its day and balance, and 이어하기 goes on with it`() {
        open()
        startSeed1011(root)
        root.button("영어 공부").click()
        root.button("카페 알바").click()
        val game = FirstMinute(1011)
        val month = game.startMonth(game.cheapestRoomIndex, game.lowestTotal)
        month.take(Action.STUDY_ENGLISH)
        month.take(Action.WORK)

        open()
        val kept = assertNotNull(root.querySelector(".intro .kept"), "No kept month on the intro")
        assertSame(root.querySelector(".intro")?.firstElementChild, kept, "The kept month is not first")
        assertEquals(Continue.TITLE, text(".kept h2"))
        assertEquals(continueFrom(month.day, month.balance), text(".kept-from"))
        assertEquals(Continue.NOTE, text(".kept-note"))
        // The story and how to play are still there for whoever forgot.
        assertNotNull(root.querySelector(".intro .story"))
        assertNotNull(root.querySelector(".intro .how-to"))
        val foot = assertNotNull(root.querySelector(".intro-start") as? HTMLElement)
        assertEquals(listOf(Continue.CONTINUE, Continue.NEW), buttons(foot))
        assertEquals("kept-from", root.button(Continue.CONTINUE).getAttribute("aria-describedby"))
        assertEquals("kept-note", root.button(Continue.NEW).getAttribute("aria-describedby"))
        root.button(Continue.CONTINUE).click()
        assertNull(root.querySelector(".intro"))
        assertTrue(date(month.day) in text(".topbar") && money(month.balance) in text(".topbar"), text(".topbar"))
    }

    @Test
    fun `with a month kept, 새로 시작 drops it and goes to the rooms, not to the intro again`() {
        open()
        startSeed1011(root)
        root.button("영어 공부").click()

        open()
        root.button(Continue.NEW).click()
        assertNull(root.querySelector(".intro"))
        assertNotNull(root.querySelector(".rooms"))
        assertNull(SaveSlot(Memory { store }).record())
    }

    @Test
    fun `an address that asks for the rooms skips the intro, but a month kept for the run is still offered`() {
        open(atRooms = true)
        assertNull(root.querySelector(".intro"))
        assertNotNull(root.querySelector(".rooms"))

        store.items[SaveSlot.KEY] = MonthRecord(1011, null, 2, 1165, listOf(Choice.Take(Action.REST))).encode()
        open(atRooms = true)
        assertNotNull(root.querySelector(".intro .kept"), "The kept month was skipped with the intro")
    }

    @Test
    fun `다시 하기's address goes straight to the new run's rooms, and is written back without the word that asked`() {
        val now = Date(2026, 9, 31)
        assertEquals(Opening(4242, null, "?seed=4242", atRooms = true), openingFor("?seed=4242&start", now, 1))
        assertEquals(Opening(4242, null, null), openingFor("?seed=4242", now, 1))
        // Only with a run: "start" alone opens a new run on its intro, and today's run always opens on the intro.
        assertEquals(Opening(1, null, "?seed=1"), openingFor("?start", now, 1))
        assertFalse(openingFor("?today&start", now, 1).atRooms)
    }

    /**
     * A phone [width] by [height] points: a frame of that size holding a page with the game's own style sheet, as it
     * is published, and its #app, where the game is drawn.
     */
    private fun phone(width: Int, height: Int): HTMLElement {
        val frame = document.createElement("iframe") as HTMLIFrameElement
        frame.style.width = "${width}px"
        frame.style.height = "${height}px"
        frame.style.border = "0"
        document.body!!.appendChild(frame)
        frames += frame
        val style = js("window.__karma__.config.pageStyle") as String
        val page = frame.contentDocument!!.asDynamic()
        page.open()
        page.write("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><style>$style</style></head><body>")
        page.close()
        // Made by this page, as the game makes its elements, so Kotlin's casts know it; the frame adopts it.
        val app = document.createElement("main") as HTMLElement
        app.id = "app"
        frame.contentDocument!!.body!!.appendChild(app)
        return app
    }

    /**
     * True when [button] lies wholly inside a [width] by [height] screen, unscrolled, and a finger at its middle lands
     * on it, not on something over it.
     */
    private fun reachable(button: HTMLButtonElement, width: Int, height: Int): Boolean {
        val box = button.getBoundingClientRect()
        val inside = box.top >= 0 && box.left >= 0 && box.bottom <= height && box.right <= width && box.height >= 44
        val hit = button.ownerDocument!!.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2)
        return inside && (hit === button || button.contains(hit))
    }

    private val screens = listOf(375 to 812, 320 to 568, 812 to 375, 568 to 320)

    @Test
    fun `laid out as published, 시작하기 is in sight and takes a finger on every phone, upright or turned`() {
        for ((width, height) in screens) {
            val app = phone(width, height)
            open(into = app)
            val page = app.ownerDocument!!
            assertEquals("CSS1Compat", page.compatMode)
            val start = assertNotNull(app.querySelector(".intro-start .primary") as? HTMLButtonElement)
            assertEquals(Intro.START, start.textContent)
            assertTrue(reachable(start, width, height), "시작하기 is out of reach at $width x $height")
            assertTrue(page.documentElement!!.scrollWidth <= width, "The intro scrolls sideways at $width x $height")
            // The story starts on the first screen, under the title.
            assertTrue(app.querySelector(".story")!!.getBoundingClientRect().top < height * 0.6, "No story at $width")
            start.click()
            assertNotNull(app.querySelector(".rooms"), "시작하기 did not lead to the rooms at $width x $height")
        }
    }

    @Test
    fun `laid out as published, 이어하기 and 새로 시작 are both in sight on every phone`() {
        open()
        startSeed1011(root)
        root.button("영어 공부").click()
        for ((width, height) in screens) {
            val app = phone(width, height)
            open(into = app)
            for (name in listOf(Continue.CONTINUE, Continue.NEW)) {
                val button = assertNotNull(
                    app.querySelectorAll(".intro-start button").asList().firstOrNull { it.textContent == name }
                        as? HTMLButtonElement,
                    "No $name at $width x $height",
                )
                assertTrue(reachable(button, width, height), "$name is out of reach at $width x $height")
            }
        }
    }
}
