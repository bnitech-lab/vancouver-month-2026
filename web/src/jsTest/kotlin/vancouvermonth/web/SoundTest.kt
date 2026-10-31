package vancouvermonth.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/** A speaker that only notes what it was asked to do, so a test needs no sound at all. */
internal class NotingSpeaker : Speaker {
    val asked = mutableListOf<String>()

    override fun on() {
        asked += "on"
    }

    override fun off() {
        asked += "off"
    }

    override fun hold(hidden: Boolean) {
        asked += "hold $hidden"
    }

    override fun blip() {
        asked += "blip"
    }

    override fun tick() {
        asked += "tick"
    }

    override fun chime() {
        asked += "chime"
    }
}

/** A storage that keeps what it is given, as localStorage does across a reload. */
internal class FakeStore : KeyValueStore {
    val items = mutableMapOf<String, String>()

    override fun getItem(key: String): String? = items[key]

    override fun setItem(key: String, value: String) {
        items[key] = value
    }
}

/** A storage the browser refuses, as in a private window or with site data blocked: every call throws. */
internal class RefusingStore : KeyValueStore {
    override fun getItem(key: String): String? = throw IllegalStateException("Storage is refused")

    override fun setItem(key: String, value: String) = throw IllegalStateException("Storage is refused")
}

/** The sound switch and when the sound is made, played, paused and quiet. No sound is made: the speaker only notes. */
class SoundTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val sounds = mutableListOf<Sound>()
    private val speaker = NotingSpeaker()
    private var made = 0
    private var clock = 0.0
    private var hidden = false

    @AfterTest
    fun removeScreen() {
        sounds.forEach { it.close() }
        root.remove()
    }

    /** A sound remembered in [store], whose speaker is made (and counted) only when asked for. */
    private fun sound(store: KeyValueStore = FakeStore(), webAudio: Boolean = true): Sound {
        val made = if (webAudio) ({ made += 1; speaker }) else null
        return Sound(Memory { store }, made, now = { clock }, hidden = { hidden }).also { sounds += it }
    }

    private fun switchIn(parent: HTMLElement = root): HTMLButtonElement =
        assertNotNull(parent.querySelector(".sound-switch") as? HTMLButtonElement, "No sound switch: ${parent.innerHTML}")

    @Test
    fun `a tick goes through at most once every 110 ms, so typing clicks softly`() {
        val limiter = TickLimiter()
        assertEquals(
            listOf(true, false, false, true, false, true, true),
            listOf(0.0, 35.0, 109.9, 110.0, 175.0, 220.0, 1_000.0).map { limiter.allow(it) },
        )
    }

    @Test
    fun `the switch starts off, and makes the sound only when it is tapped`() {
        val sound = sound()
        sound.button(root)
        val button = switchIn()
        assertEquals("소리 켜기", button.textContent)
        assertEquals("false", button.getAttribute("aria-pressed"))
        assertEquals("button", button.type)
        assertEquals(0, made, "The sound was made before a tap")

        button.click()
        assertEquals("소리 끄기", button.textContent)
        assertEquals("true", button.getAttribute("aria-pressed"))
        assertEquals(1, made)
        assertEquals(listOf("on"), speaker.asked)

        button.click()
        assertEquals("소리 켜기", button.textContent)
        assertEquals("false", button.getAttribute("aria-pressed"))
        button.click()
        assertEquals(1, made, "The same sound plays again")
        assertEquals(listOf("on", "off", "on"), speaker.asked)
    }

    @Test
    fun `the choice survives a reload, and the sound then waits for the first tap anywhere`() {
        val store = FakeStore()
        sound(store).button(root)
        switchIn().click()
        root.innerHTML = ""

        // The page opens again with the same storage: the switch is on, but nothing plays before a tap.
        sound(store).button(root)
        assertEquals("true", switchIn().getAttribute("aria-pressed"))
        assertEquals("소리 끄기", switchIn().textContent)
        made = 0
        speaker.asked.clear()
        document.body!!.dispatchEvent(Event("keydown", js("({ bubbles: true })")))
        assertEquals(1, made)
        assertEquals(listOf("on"), speaker.asked)
        document.body!!.dispatchEvent(Event("pointerup", js("({ bubbles: true })")))
        assertEquals(1, made, "Only the first tap wakes the sound")

        // Turned off, and the page opens once more: it stays off.
        switchIn().click()
        root.innerHTML = ""
        sound(store).button(root)
        assertEquals("false", switchIn().getAttribute("aria-pressed"))
    }

    @Test
    fun `a first tap on the switch itself, after a reload, only turns the sound off`() {
        val store = FakeStore()
        store.setItem("vancouver-month.sound", "on")
        sound(store).button(root)
        switchIn().click()
        assertEquals("false", switchIn().getAttribute("aria-pressed"))
        assertEquals(0, made, "The sound was made only to be turned off")
        assertEquals("off", store.items["vancouver-month.sound"])
    }

    @Test
    fun `storage that throws leaves the sound off, and the switch still works`() {
        for (memory in listOf(Memory { RefusingStore() }, Memory { throw IllegalStateException("No storage") })) {
            root.innerHTML = ""
            val sound = Sound(memory, { made += 1; speaker }).also { sounds += it }
            sound.button(root)
            assertEquals("false", switchIn().getAttribute("aria-pressed"))
            switchIn().click()
            assertEquals("true", switchIn().getAttribute("aria-pressed"))
        }
        assertEquals(2, made)
    }

    @Test
    fun `the sound pauses while the page is hidden and wakes when it is shown`() {
        sound().button(root)
        switchIn().click()
        hidden = true
        document.dispatchEvent(Event("visibilitychange"))
        hidden = false
        document.dispatchEvent(Event("visibilitychange"))
        assertEquals(listOf("on", "hold true", "hold false"), speaker.asked)
    }

    @Test
    fun `the small sounds play only while the sound is on, and the ticks are spaced out`() {
        val sound = sound()
        sound.button(root)
        sound.blip()
        sound.chime()
        sound.tick()
        assertEquals(emptyList(), speaker.asked)

        switchIn().click()
        sound.blip()
        sound.chime()
        for (millis in listOf(0.0, 35.0, 70.0, 105.0, 140.0, 175.0, 210.0, 245.0, 280.0)) {
            clock = 1_000.0 + millis
            sound.tick()
        }
        assertEquals(listOf("on", "blip", "chime", "tick", "tick", "tick"), speaker.asked)
    }

    @Test
    fun `without Web Audio there is no switch, and this browser has Web Audio`() {
        sound(webAudio = false).button(root)
        assertNull(root.querySelector(".sound-switch"))
        assertTrue(hasWebAudio())
    }
}
