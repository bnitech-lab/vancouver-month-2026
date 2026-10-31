package vancouvermonth.web

/**
 * Decides which frames the town draws, and what each one does. The town's own life (people strolling, the train, the
 * fire, the water) moves on a steady beat of at most [AMBIENT_FPS] a second, whatever the screen's rate, and only
 * [RESTFUL_FPS] a second once nobody has touched the town for [QUIET_AFTER_MILLIS] or while a card covers it, to spare
 * the battery. While the player or the camera moves, or the day changes, every frame the screen offers is drawn too,
 * but the town's life still moves on the beat only. The sun's shadows are drawn again on every moving frame and on
 * every other frame of the town's own life. While the page is hidden, nothing is drawn. [openedAt] is when the town
 * opened, which counts as a touch.
 */
class FramePace(openedAt: Double) {
    /** When the next beat of the town's own life is due. */
    private var due = Double.NEGATIVE_INFINITY

    /** When the player last touched the town, or the game last turned to it. */
    private var lastInput = openedAt

    /** True while a card covers most of the town, as a talk does. */
    var isCovered = false

    /** How many frames of the town's own life have been drawn since the last moving one. */
    private var quietFrames = 0

    /** Whether the frame [shouldDraw] last said yes to moves the town's own life on: it is on the beat. */
    var stepsLife = false
        private set

    /** Whether the frame [shouldDraw] last said yes to draws the sun's shadows again. */
    var drawsShadows = false
        private set

    /** Notes a touch at [now]: the quicker beat comes back at once, not after the slower one runs out. */
    fun noteInput(now: Double) {
        lastInput = now
        if (due > now + BEAT) due = now + BEAT
    }

    /** The time between two beats of the town's own life at [now]: longer while covered or untouched for a while. */
    fun beatAt(now: Double): Double = if (isCovered || now - lastInput >= QUIET_AFTER_MILLIS) RESTFUL_BEAT else BEAT

    /**
     * Whether to draw the frame the screen offers at [now] milliseconds: [moving] when the player or the camera
     * moves, [hidden] when the page is not on screen. A frame it says yes to counts as drawn, and [stepsLife] and
     * [drawsShadows] say what it does.
     */
    fun shouldDraw(now: Double, moving: Boolean, hidden: Boolean): Boolean {
        if (hidden) return false
        // A screen's frames do not come exactly on time, so one a little early for its beat is on the beat too.
        val onBeat = now >= due - EARLY
        if (!moving && !onBeat) return false
        stepsLife = onBeat
        if (onBeat) {
            // The beat goes on from where it was, so early frames never add up to more than the rate; after a long
            // pause it starts again from now.
            val beat = beatAt(now)
            due = if (now - due > beat) now + beat else due + beat
        }
        if (moving) {
            drawsShadows = true
            quietFrames = 0
        } else {
            drawsShadows = quietFrames % 2 == 0
            quietFrames += 1
        }
        return true
    }

    /** Forgets the beat, as when the page comes back on screen, so the next frame is drawn at once. */
    fun reset() {
        due = Double.NEGATIVE_INFINITY
    }

    companion object {
        const val AMBIENT_FPS = 30.0

        /** The time between two beats of the town's own life: on a 60 Hz screen, every other frame. */
        const val BEAT = 1_000 / AMBIENT_FPS

        /** The slower beat, once the town is left alone or covered. */
        const val RESTFUL_FPS = 15.0
        const val RESTFUL_BEAT = 1_000 / RESTFUL_FPS

        /** How long the town waits with no touch before its life slows to [RESTFUL_FPS]. */
        const val QUIET_AFTER_MILLIS = 20_000.0

        /** How early for its beat a frame may come and still be on it. */
        const val EARLY = 4.0
    }
}
