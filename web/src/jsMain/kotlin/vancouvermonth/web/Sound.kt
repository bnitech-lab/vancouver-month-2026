package vancouvermonth.web

import kotlin.js.Date
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import vancouvermonth.text.Weather
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
 * time, the switch shows it on and the first tap anywhere on the page brings the sound back. Until the browser really
 * plays it, every tap tries again, since a browser may not count a tap (the end of a scroll, the Escape key) as the
 * player's own. While the page is hidden the sound pauses, and when it is shown again it waits for a tap in the same
 * way. Where the browser has no Web Audio, [makeSpeaker] is null and the page has no switch.
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

    /** True on a rainy day, kept so a sound made later starts with the rain. */
    private var rainy = false

    /**
     * The hour on the month's own clock while a month is played, or null on the screens around it: the tune's mood
     * follows it, and the phone's own clock only when there is none. The game sets it.
     */
    var gameHour: () -> Int? = { null }

    /** The hour the tune's mood follows now: the month's, or else the phone's. */
    fun hour(): Int = gameHour() ?: Date().getHours()

    /** The switches on the page now, to show the sound off everywhere if it cannot be made. */
    private val switches = mutableListOf<HTMLButtonElement>()
    private val onFirstTap: (Event) -> Unit = { event -> if (countsAsTap(event)) wake() }
    private val onVisibility: (Event) -> Unit = { shownOrHidden() }

    init {
        if (makeSpeaker != null) {
            document.addEventListener("visibilitychange", onVisibility)
            if (isOn) waitForTap()
        }
    }

    /**
     * Adds the switch to [parent]: a plain button whose words say what a tap does, filled while the sound is on. It is
     * not a toggle button with aria-pressed, since its words change: ARIA's guidance for toggle buttons keeps the name
     * steady, or a screen reader would read two states at once.
     */
    fun button(parent: HTMLElement) {
        if (makeSpeaker == null) return
        val button = document.createElement("button") as HTMLButtonElement
        button.type = "button"
        button.className = "sound-switch"
        show(button)
        button.onclick = {
            toggle()
            switches.forEach(::show)
        }
        parent.appendChild(button)
        switches.removeAll { !it.isConnected }
        switches += button
    }

    private fun show(button: HTMLButtonElement) {
        button.textContent = soundLabel(isOn)
        button.classList.toggle("on", isOn)
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

    /** Today's weather: a soft bed of rain on a rainy day, heard while the sound is on. */
    fun weather(weather: Weather) {
        val rain = weather == Weather.RAINY
        if (rain == rainy) return
        rainy = rain
        // A sound that fails here is noted, and the game goes on.
        try {
            speaker?.weather(rain)
        } catch (error: Throwable) {
            console.warn("The rain could not be played:", error)
        }
    }

    /**
     * How loud the town's train and fire are where the player is, and how dark its night is, while the sound is on. A
     * silence always goes through, even while off: a train still rumbling when the sound was switched off must not come
     * back with the sound after the town has gone, with nothing left to quiet it.
     */
    fun nearby(train: Double, fire: Double, night: Double) {
        if (!isOn && (train != 0.0 || fire != 0.0)) return
        // A sound that fails here is noted, and the town goes on.
        try {
            speaker?.nearby(train, fire, night)
        } catch (error: Throwable) {
            console.warn("The town's sounds could not be played:", error)
        }
    }

    /** Stops waiting for a tap and watching whether the page is hidden, as a test does when it is over. */
    fun close() {
        stopWaiting()
        document.removeEventListener("visibilitychange", onVisibility)
    }

    /** Makes the sound on a tap that asks for it, and plays it. Taps are waited for until the browser plays it. */
    private fun wake() {
        if (!isOn) {
            stopWaiting()
            return
        }
        try {
            val made = speaker ?: checkNotNull(makeSpeaker).invoke().also {
                speaker = it
                it.weather(rainy)
            }
            waitForTap()
            made.on { if (isOn) stopWaiting() }
        } catch (error: Throwable) {
            console.warn("The sound could not start:", error)
            giveUp()
        }
    }

    /** The sound cannot be made or started: the switch goes back to off, here and on the next visit. */
    private fun giveUp() {
        isOn = false
        memory.write(KEY, OFF)
        stopWaiting()
        switches.forEach(::show)
    }

    /** Pauses while the page is hidden; once it is shown again, wakes the sound, waiting for a tap if the browser asks. */
    private fun shownOrHidden() {
        val made = speaker ?: return
        if (hidden()) {
            made.pause()
        } else if (isOn) {
            waitForTap()
            made.resume { if (isOn) stopWaiting() }
        }
    }

    private fun waitForTap() {
        for (type in FIRST_TAPS) document.addEventListener(type, onFirstTap, true)
    }

    private fun stopWaiting() {
        for (type in FIRST_TAPS) document.removeEventListener(type, onFirstTap, true)
    }

    /**
     * True for a tap or key that may wake the sound: not one on a sound switch, which switches the sound itself, and
     * not the Escape key, which a browser never counts as the player's own.
     */
    private fun countsAsTap(event: Event): Boolean {
        if ((event.target as? Element)?.closest(".sound-switch") != null) return false
        return event.type != "keydown" || (event as? KeyboardEvent)?.key != "Escape"
    }

    private companion object {
        const val KEY = "vancouver-month.sound"
        const val ON = "on"
        const val OFF = "off"

        /**
         * Events a browser may count as the player's own tap or key, after which a page may play sound. No touchend:
         * the end of a scroll sends one but does not count, and pointerup already covers a finger's tap.
         */
        val FIRST_TAPS = listOf("pointerup", "click", "keydown")
    }
}

/**
 * The page's sound: remembered in [memory], made with Web Audio where the browser has it, in the mood of the hour: the
 * month's own while a month is played ([Sound.gameHour]), the phone's around it.
 */
internal fun pageSound(memory: Memory): Sound {
    lateinit var sound: Sound
    sound = Sound(
        memory,
        if (hasWebAudio()) {
            { WebAudioSpeaker(AudioContext(), VillageTune.THEME_SEED) { sound.hour() } }
        } else {
            null
        },
    )
    return sound
}
