package vancouvermonth.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import vancouvermonth.text.Continue

/** A kept month that can no longer be continued is dropped, and the player is told once, on the rooms screen. */
class DroppedSaveNoticeTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val store = FakeStore()
    private val guides = mutableListOf<FirstRunGuide>()

    @AfterTest
    fun removeScreen() {
        guides.forEach { it.close() }
        root.remove()
    }

    /** Opens the page for [seed] on [storage], as a reload does, on its first screen. */
    private fun open(storage: KeyValueStore = store, seed: Int = 1011): PhoneGame {
        root.innerHTML = ""
        val game = PhoneGame(root, seed, playAgain = {})
        game.slot = SaveSlot(Memory { storage })
        game.guide = FirstRunGuide().also { guides += it }
        game.show()
        return game
    }

    /** True when the notice is on the screen. */
    private val shown: Boolean
        get() = Continue.DROPPED in root.textContent.orEmpty()

    /** Opens the page and goes past the intro to the rooms. */
    private fun openAtRooms(storage: KeyValueStore = store, seed: Int = 1011) {
        open(storage, seed)
        passIntro(root)
        assertNotNull(root.querySelector(".rooms"), "Not at the rooms")
    }

    private val unreadable = listOf(
        "broken line" to "4|1011||2|1165|Z",
        "an older version" to "3|1011||2|1165|EWC",
        "a day the rules refuse" to "4|1011||2|1165|EWW",
        "not a record" to "garbage",
    )

    @Test
    fun `a line that cannot be continued is told once on the rooms screen and never again`() {
        for ((what, line) in unreadable) {
            store.items.clear()
            store.items[SaveSlot.KEY] = line
            open()
            assertFalse(shown, "Shown on the intro, not the rooms: $what")
            passIntro(root)
            assertTrue(shown, "Not told: $what")
            // A reload before anything else: told once, so not again.
            openAtRooms()
            assertFalse(shown, "Told twice: $what")
        }
    }

    @Test
    fun `the notice stays while the player picks a room on the same page`() {
        store.items[SaveSlot.KEY] = "3|1011||2|1165|EWC"
        open()
        passIntro(root)
        root.button("1번 방").click()
        assertTrue(shown)
    }

    @Test
    fun `a page opened straight to the rooms tells it too`() {
        store.items[SaveSlot.KEY] = "4|1011||2|1165|Z"
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {})
        game.slot = SaveSlot(Memory { store })
        game.guide = FirstRunGuide().also { guides += it }
        game.introPassed = true
        game.show()
        assertTrue(shown)
    }

    @Test
    fun `no kept month at all is told nothing`() {
        openAtRooms()
        assertFalse(shown)
        store.items[SaveSlot.KEY] = ""
        openAtRooms()
        assertFalse(shown)
    }

    @Test
    fun `a month kept for another run is left alone and nothing is told`() {
        val line = "4|1011||2|1165|EWRC"
        store.items[SaveSlot.KEY] = line
        openAtRooms(seed = 2022)
        assertFalse(shown)
        assertEquals(line, store.items[SaveSlot.KEY])
    }

    @Test
    fun `a line that does not read is dropped and told even on a page for another run`() {
        store.items[SaveSlot.KEY] = "garbage"
        openAtRooms(seed = 2022)
        assertTrue(shown)
    }

    @Test
    fun `a month that continues is told nothing`() {
        store.items[SaveSlot.KEY] = "4|1011||2|1165|EWRC"
        open()
        assertFalse(shown)
        root.button(Continue.NEW).click()
        assertFalse(shown)
    }

    @Test
    fun `where the browser refuses its storage nothing is told and the game starts`() {
        openAtRooms(storage = RefusingStore())
        assertFalse(shown)
    }

    @Test
    fun `a month dropped by the player's own new start is not told as a loss`() {
        store.items[SaveSlot.KEY] = "4|1011||2|1165|EWRC"
        open()
        root.button(Continue.NEW).click()
        openAtRooms()
        assertFalse(shown)
    }

    private class RefusingStore : KeyValueStore {
        override fun getItem(key: String): String? = error("Refused")

        override fun setItem(key: String, value: String) = error("Refused")
    }
}
