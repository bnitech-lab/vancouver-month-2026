package vancouvermonth.web

import kotlin.js.Date
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import vancouvermonth.text.soundLabel

/** Lets a tick through at most once every [gapMillis], so a line typed out clicks softly instead of buzzing. */
internal class TickLimiter(private val gapMillis: Double = 110.0) {
    private var last: Double? = null

    fun allow(now: Double): Boolean {
        val before = last
        if (before != null && now - before < gapMillis) return false
        last = now
        return true
    }
}

/**
 * The sound switch, off until the player turns it on, and remembered for the next visit where the browser allows it.
 * The sound itself is made only on a tap, as browsers require: [makeSpeaker] is called then. When it was left on last
 * time, the switch shows it on and the first tap anywhere on the page brings the sound back. While the page is hidden
 * the sound pauses. Where the browser has no Web Audio, [makeSpeaker] is null and the page has no switch.
 * A test hands in its own clock ([now], in milliseconds) and its own idea of whether the page is [hidden].
 */
internal class Sound(
    private val memory: Memory,
    private val makeSpeaker: (() -> Speaker)?,
    private val now: () -> Double = { window.performance.now() },
    private val hidden: () -> Boolean = { document.asDynamic().hidden == true },
) {
    var isOn: Boolean = makeSpeaker != null && memory.read(KEY) == ON
        private set

    private var speaker: Speaker? = null
    private val ticks = TickLimiter()
    private val onFirstTap: (Event) -> Unit = { event -> if (!onSwitch(event)) wake() }
    private val onVisibility: (Event) -> Unit = { speaker?.hold(hidden()) }

    init {
        if (makeSpeaker != null) {
            document.addEventListener("visibilitychange", onVisibility)
            if (isOn) for (type in FIRST_TAPS) document.addEventListener(type, onFirstTap, true)
        }
    }

    /** Adds the switch to [parent]: its words say what a tap does, and it is pressed while the sound is on. */
    fun button(parent: HTMLElement) {
        if (makeSpeaker == null) return
        val button = document.createElement("button") as HTMLButtonElement
        button.type = "button"
        button.className = "sound-switch"
        show(button)
        button.onclick = {
            toggle()
            show(button)
        }
        parent.appendChild(button)
    }

    private fun show(button: HTMLButtonElement) {
        button.textContent = soundLabel(isOn)
        button.setAttribute("aria-pressed", isOn.toString())
    }

    private fun toggle() {
        isOn = !isOn
        memory.write(KEY, if (isOn) ON else OFF)
        if (isOn) {
            wake()
        } else {
            stopWaiting()
            speaker?.off()
        }
    }

    fun blip() {
        if (isOn) speaker?.blip()
    }

    fun tick() {
        if (isOn && ticks.allow(now())) speaker?.tick()
    }

    fun chime() {
        if (isOn) speaker?.chime()
    }

    /** Stops waiting for a tap and watching whether the page is hidden, as a test does when it is over. */
    fun close() {
        stopWaiting()
        document.removeEventListener("visibilitychange", onVisibility)
    }

    /** Makes the sound on the first tap that asks for it, then plays it. */
    private fun wake() {
        stopWaiting()
        if (!isOn) return
        val made = speaker ?: make() ?: return
        speaker = made
        made.on()
    }

    private fun make(): Speaker? = try {
        makeSpeaker?.invoke()
    } catch (error: Throwable) {
        console.warn("The sound could not start:", error)
        null
    }

    private fun stopWaiting() {
        for (type in FIRST_TAPS) document.removeEventListener(type, onFirstTap, true)
    }

    /** True for a tap on a sound switch, which switches the sound itself. */
    private fun onSwitch(event: Event): Boolean =
        (event.target as? Element)?.closest(".sound-switch") != null

    private companion object {
        const val KEY = "vancouver-month.sound"
        const val ON = "on"
        const val OFF = "off"

        /** The events a browser counts as the player's own tap or key, after which a page may play sound. */
        val FIRST_TAPS = listOf("pointerup", "touchend", "click", "keydown")
    }
}

/** The page's sound: remembered in [memory], made with Web Audio where the browser has it, in the mood of the hour. */
internal fun pageSound(memory: Memory): Sound = Sound(
    memory,
    if (hasWebAudio()) {
        { WebAudioSpeaker(AudioContext(), VillageTune.THEME_SEED) { Date().getHours() } }
    } else {
        null
    },
)
