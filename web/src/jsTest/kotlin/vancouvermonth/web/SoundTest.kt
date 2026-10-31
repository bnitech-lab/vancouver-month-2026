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
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import vancouvermonth.text.Weather

/**
 * A speaker that only notes what it was asked to do, so a test needs no sound at all. While [plays] is false it acts
 * as a browser that refuses to play: it never says it is playing.
 */
internal class NotingSpeaker : Speaker {
    val asked = mutableListOf<String>()
    var plays = true

    override fun on(whenPlaying: () -> Unit) {
        asked += "on"
        if (plays) whenPlaying()
    }

    override fun off() {
        asked += "off"
    }

    override fun pause() {
        asked += "pause"
    }

    override fun resume(whenPlaying: () -> Unit) {
        asked += "resume"
        if (plays) whenPlaying()
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

    /** Whether the rain plays, as last asked, and each time the town's sounds were set: noted apart from [asked]. */
    var rain: Boolean? = null
    val nearby = mutableListOf<Triple<Double, Double, Double>>()

    override fun weather(rain: Boolean) {
        this.rain = rain
    }

    override fun nearby(train: Double, fire: Double, night: Double) {
        nearby += Triple(train, fire, night)
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
        assertEquals("button", button.type)
        // A plain action button: its words say what a tap does, so it carries no pressed state.
        assertNull(button.getAttribute("aria-pressed"))
        assertEquals(0, made, "The sound was made before a tap")

        button.click()
        assertEquals("소리 끄기", button.textContent)
        assertNull(button.getAttribute("aria-pressed"))
        assertTrue(button.classList.contains("on"))
        assertEquals(1, made)
        assertEquals(listOf("on"), speaker.asked)

        button.click()
        assertEquals("소리 켜기", button.textContent)
        assertTrue(!button.classList.contains("on"))
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
        assertEquals("소리 켜기", switchIn().textContent)
    }

    @Test
    fun `a first tap on the switch itself, after a reload, only turns the sound off`() {
        val store = FakeStore()
        store.setItem("vancouver-month.sound", "on")
        sound(store).button(root)
        switchIn().click()
        assertEquals("소리 켜기", switchIn().textContent)
        assertEquals(0, made, "The sound was made only to be turned off")
        assertEquals("off", store.items["vancouver-month.sound"])
    }

    @Test
    fun `storage that throws leaves the sound off, and the switch still works`() {
        for (memory in listOf(Memory { RefusingStore() }, Memory { throw IllegalStateException("No storage") })) {
            root.innerHTML = ""
            val sound = Sound(memory, { made += 1; speaker }).also { sounds += it }
            sound.button(root)
            assertEquals("소리 켜기", switchIn().textContent)
            switchIn().click()
            assertEquals("소리 끄기", switchIn().textContent)
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
        assertEquals(listOf("on", "pause", "resume"), speaker.asked)
    }

    /** Dispatches [type] on the page as a tap or key from the player would arrive, with [key] for a keydown. */
    private fun press(type: String, key: String? = null) {
        val event = if (key == null) {
            Event(type, js("({ bubbles: true })"))
        } else {
            KeyboardEvent(type, KeyboardEventInit(key = key, bubbles = true))
        }
        document.body!!.dispatchEvent(event)
    }

    @Test
    fun `a tap the browser does not count leaves the sound waiting, and the next tap tries again`() {
        val store = FakeStore()
        store.setItem("vancouver-month.sound", "on")
        sound(store).button(root)
        speaker.plays = false
        press("pointerup")
        assertEquals(1, made)
        assertEquals(listOf("on"), speaker.asked)
        press("click")
        assertEquals(listOf("on", "on"), speaker.asked, "The next tap did not try again")
        speaker.plays = true
        press("keydown", key = "a")
        assertEquals(listOf("on", "on", "on"), speaker.asked)
        press("pointerup")
        assertEquals(listOf("on", "on", "on"), speaker.asked, "Taps still wake a sound that plays")
        assertEquals(1, made, "The sound was made more than once")
    }

    @Test
    fun `neither the Escape key nor the end of a touch counts as the first tap`() {
        val store = FakeStore()
        store.setItem("vancouver-month.sound", "on")
        sound(store).button(root)
        press("keydown", key = "Escape")
        press("touchend")
        assertEquals(0, made)
        press("keydown", key = "Enter")
        assertEquals(1, made)
        assertEquals(listOf("on"), speaker.asked)
    }

    @Test
    fun `shown again, a sound the browser will not wake waits for the next tap`() {
        sound().button(root)
        switchIn().click()
        hidden = true
        document.dispatchEvent(Event("visibilitychange"))
        speaker.plays = false
        hidden = false
        document.dispatchEvent(Event("visibilitychange"))
        assertEquals(listOf("on", "pause", "resume"), speaker.asked)
        press("pointerup")
        assertEquals(listOf("on", "pause", "resume", "on"), speaker.asked, "The tap after showing did not try again")
        speaker.plays = true
        press("pointerup")
        press("pointerup")
        assertEquals(listOf("on", "pause", "resume", "on", "on"), speaker.asked)
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
    fun `a sound that cannot be made, or will not start, turns the switch back off and remembers off`() {
        val store = FakeStore()
        val broken = object : Speaker by speaker {
            override fun on(whenPlaying: () -> Unit) = throw IllegalStateException("The sound would not start")
        }
        for (maker in listOf<() -> Speaker>({ throw IllegalStateException("No sound here") }, { broken })) {
            root.innerHTML = ""
            val sound = Sound(Memory { store }, maker).also { sounds += it }
            sound.button(root)
            switchIn().click()
            assertEquals("소리 켜기", switchIn().textContent)
            assertEquals(false, sound.isOn)
            assertEquals("off", store.items["vancouver-month.sound"])
        }

        // Left on from a visit before, the first tap finds it cannot be made, and every switch shows it off.
        store.setItem("vancouver-month.sound", "on")
        root.innerHTML = ""
        Sound(Memory { store }, { throw IllegalStateException("No sound here") }).also { sounds += it }.let {
            it.button(root)
            it.button(root)
        }
        press("pointerup")
        assertEquals(listOf("소리 켜기", "소리 켜기"), root.querySelectorAll(".sound-switch").asList().map { it.textContent })
        assertEquals("off", store.items["vancouver-month.sound"])
    }

    @Test
    fun `without Web Audio there is no switch, and this browser has Web Audio`() {
        sound(webAudio = false).button(root)
        assertNull(root.querySelector(".sound-switch"))
        assertTrue(hasWebAudio())
    }

    @Test
    fun `the rain and the town's sounds reach the sound only while it is on, and a sound made later starts with the rain`() {
        val sound = sound()
        sound.button(root)
        // Told before the sound is made: nothing is made, and the rain is kept for later.
        sound.weather(Weather.RAINY)
        sound.nearby(0.5, 0.3, 0.0)
        assertEquals(0, made)
        switchIn().click()
        assertEquals(true, speaker.rain, "The sound made on the tap starts without the rain")
        assertTrue(speaker.nearby.isEmpty(), "The town's sounds from before were played")
        sound.nearby(0.5, 0.3, 0.0)
        assertEquals(listOf(Triple(0.5, 0.3, 0.0)), speaker.nearby)
        sound.weather(Weather.CLOUDY)
        assertEquals(false, speaker.rain, "Still raining on a cloudy day")
        sound.weather(Weather.RAINY)
        assertEquals(true, speaker.rain)
        // Off: the town's sounds are no longer passed on; the rain is kept, and fades out with the rest.
        switchIn().click()
        sound.nearby(0.9, 0.9, 1.0)
        assertEquals(1, speaker.nearby.size, "The town's sounds went on while off")
        assertEquals(listOf("on", "off"), speaker.asked)
    }
}
