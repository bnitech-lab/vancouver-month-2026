package vancouvermonth.web

/**
 * Decides which frames the town draws. While the player or the camera moves, or the day changes, every frame the
 * screen offers is drawn. When only the town's own life moves (people strolling, the train, the fire, the water), at
 * most [AMBIENT_FPS] frames a second are drawn, whatever the screen's rate. While the page is hidden, none.
 */
class FramePace {
    /** When the next frame of the town's own life is due, on a steady beat. */
    private var due = Double.NEGATIVE_INFINITY

    /**
     * Whether to draw the frame the screen offers at [now] milliseconds: [moving] when the player or the camera
     * moves, [hidden] when the page is not on screen. A frame it says yes to counts as drawn.
     */
    fun shouldDraw(now: Double, moving: Boolean, hidden: Boolean): Boolean {
        if (hidden) return false
        if (moving) {
            due = now + BEAT
            return true
        }
        // A screen's frames do not come exactly on time, so one a little early for its beat is drawn too.
        if (now < due - EARLY) return false
        // The beat goes on from where it was, so early frames never add up to more than the rate; after a long
        // pause it starts again from now.
        due = if (now - due > BEAT) now + BEAT else due + BEAT
        return true
    }

    /** Forgets the beat, as when the page comes back on screen, so the next frame is drawn at once. */
    fun reset() {
        due = Double.NEGATIVE_INFINITY
    }

    companion object {
        const val AMBIENT_FPS = 30.0

        /** The time between two frames of the town's own life: on a 60 Hz screen, every other frame. */
        const val BEAT = 1_000 / AMBIENT_FPS

        /** How early for its beat a frame may come and still be drawn. */
        const val EARLY = 4.0
    }
}
