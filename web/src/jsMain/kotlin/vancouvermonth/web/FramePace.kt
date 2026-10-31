package vancouvermonth.web

/**
 * Decides which frames the town draws, and what each one does. The town's own life (people strolling, the train, the
 * fire, the water) moves on a steady beat of at most [AMBIENT_FPS] a second, whatever the screen's rate. While the
 * player or the camera moves, or the day changes, every frame the screen offers is drawn too, but the town's life
 * still moves on the beat only. The sun's shadows are drawn again on every moving frame and on every other frame of
 * the town's own life. While the page is hidden, nothing is drawn.
 */
class FramePace {
    /** When the next beat of the town's own life is due. */
    private var due = Double.NEGATIVE_INFINITY

    /** How many frames of the town's own life have been drawn since the last moving one. */
    private var quietFrames = 0

    /** Whether the frame [shouldDraw] last said yes to moves the town's own life on: it is on the beat. */
    var stepsLife = false
        private set

    /** Whether the frame [shouldDraw] last said yes to draws the sun's shadows again. */
    var drawsShadows = false
        private set

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
            due = if (now - due > BEAT) now + BEAT else due + BEAT
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

        /** How early for its beat a frame may come and still be on it. */
        const val EARLY = 4.0
    }
}
