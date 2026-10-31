package vancouvermonth.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import vancouvermonth.core.FirstMinute

/** A town to walk in that draws nothing, whose nights pass at once. */
internal class QuietTown : WalkableTown {
    override val element = (document.createElement("div") as HTMLElement).also { it.className = "walk-town" }
    override var onPlace: (Place?) -> Unit = {}
    override var onFail: () -> Unit = {}
    override var onMorning: () -> Unit = {}

    override fun mark(place: Place?) {}

    override fun endDay(place: Place) = onMorning()

    override fun close() {}
}

/** Starts seed 1011's September in [root]: the cheapest room, an exact guess, and 시작하기. */
internal fun startSeed1011(root: HTMLElement) {
    val game = FirstMinute(1011)
    root.button("${game.cheapestRoomIndex + 1}번 방").click()
    val input = root.querySelector("input") as HTMLInputElement
    input.value = "${game.lowestTotal}"
    input.dispatchEvent(Event("input"))
    root.button("총비용 확인하기").click()
    root.button("시작하기").click()
}

internal fun HTMLElement.button(text: String): HTMLButtonElement =
    querySelectorAll("button").asList().map { it as HTMLButtonElement }
        .firstOrNull { text in it.textContent.orEmpty() } ?: error("No button with \"$text\" in: $textContent")

/** The sound switch where the player sees it, and the game's small sounds at the moments they belong to. */
class SoundInGameTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val speaker = NotingSpeaker()
    private val sound = Sound(Memory { FakeStore() }, { speaker })

    @AfterTest
    fun removeScreen() {
        sound.close()
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    private fun switchIn(parent: HTMLElement = root): HTMLButtonElement =
        assertNotNull(parent.querySelector(".sound-switch") as? HTMLButtonElement, "No sound switch: ${parent.innerHTML}")

    @Test
    fun `the switch is on the room screen and the day's buttons, and a day changes with a chime`() {
        val game = PhoneGame(root, 1011, playAgain = {})
        game.sound = sound
        game.show()
        assertNotNull(root.querySelector(".title .sound-switch"))
        switchIn().click()
        startSeed1011(root)
        val topbar = assertNotNull(root.querySelector(".topbar") as? HTMLElement)
        assertEquals("소리 끄기", switchIn(topbar).textContent)
        root.button("영어 공부").click()
        assertEquals(listOf("on", "chime"), speaker.asked)
    }

    @Test
    fun `in the town the switch is in the top card, a talk opens with a blip and a day ends with a chime`() {
        val town = QuietTown()
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { town })
        game.sound = sound
        game.talkSettleMillis = 0.0
        game.show()
        startSeed1011(root)
        val hud = assertNotNull(root.querySelector(".hud") as? HTMLElement)
        switchIn(hud).click()
        assertEquals("소리 끄기", switchIn().textContent)

        // The town tells the game the player turned to the café's person, as a tap on her would.
        town.onPlace(Place.CAFE)
        assertNotNull(root.querySelector(".talk"))
        assertNotNull(root.querySelector(".hud .sound-switch"), "The top card lost its switch when the talk opened")
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        assertEquals(listOf("on", "blip", "chime"), speaker.asked.filter { it != "tick" })
    }

    @Test
    fun `a game with no sound has no switch anywhere`() {
        PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { QuietTown() }).show()
        startSeed1011(root)
        assertEquals(null, root.querySelector(".sound-switch"))
    }

    @Test
    fun `after a reload with the sound left on, the first tap still does what it was for`() {
        val store = FakeStore()
        store.setItem("vancouver-month.sound", "on")
        val remembered = Sound(Memory { store }, { speaker })
        try {
            val game = PhoneGame(root, 1011, playAgain = {})
            game.sound = remembered
            game.show()
            assertEquals(emptyList(), speaker.asked)
            root.button("1번 방").click()
            // The tap woke the sound and still picked the room.
            assertEquals(listOf("on"), speaker.asked)
            assertEquals("true", root.button("1번 방").getAttribute("aria-pressed"))
        } finally {
            remembered.close()
        }
    }
}
