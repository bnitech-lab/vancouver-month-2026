package vancouvermonth.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FramePaceTest {
    /** How many of the frames a screen offers at [hertz] for [seconds] the pace draws. */
    private fun drawn(hertz: Double, moving: Boolean, hidden: Boolean = false, seconds: Int = 1): Int {
        val pace = FramePace(openedAt = 0.0)
        val frames = (0 until hertz.toInt() * seconds).map { 1_000.0 + it * 1_000.0 / hertz }
        return frames.count { pace.shouldDraw(it, moving, hidden) }
    }

    @Test
    fun `with only the town's life moving, at most thirty frames a second are drawn on any screen`() {
        assertEquals(30, drawn(60.0, moving = false))
        for (hertz in listOf(30.0, 50.0, 60.0, 75.0, 90.0, 120.0, 144.0, 165.0, 240.0)) {
            val count = drawn(hertz, moving = false, seconds = 10)
            assertTrue(count <= 10 * FramePace.AMBIENT_FPS, "$count frames in ten seconds at $hertz Hz")
            assertTrue(count >= 10 * 24, "only $count frames in ten seconds at $hertz Hz")
        }
        // A screen whose frames come a little early or late now and then still gets thirty a second.
        val pace = FramePace(openedAt = 0.0)
        val jittery = (0 until 600).map { 1_000.0 + it * 1_000.0 / 60 + if (it % 7 == 0) -1.5 else 0.8 }
        assertEquals(300, jittery.count { pace.shouldDraw(it, moving = false, hidden = false) })
    }

    @Test
    fun `while the player or the camera moves, every frame is drawn`() {
        assertEquals(60, drawn(60.0, moving = true))
        assertEquals(120, drawn(120.0, moving = true))
    }

    @Test
    fun `nothing is drawn while the page is hidden, moving or not`() {
        assertEquals(0, drawn(60.0, moving = false, hidden = true))
        assertEquals(0, drawn(60.0, moving = true, hidden = true))
    }

    @Test
    fun `once the moving stops, the next frame of the town's life waits its turn, and a reset draws at once`() {
        val pace = FramePace(openedAt = 0.0)
        assertTrue(pace.shouldDraw(0.0, moving = true, hidden = false))
        assertFalse(pace.shouldDraw(16.7, moving = false, hidden = false))
        assertTrue(pace.shouldDraw(33.4, moving = false, hidden = false))
        assertFalse(pace.shouldDraw(40.0, moving = false, hidden = false))
        pace.reset()
        assertTrue(pace.shouldDraw(41.0, moving = false, hidden = false))
    }

    @Test
    fun `while moving, every frame is drawn, but the town's life moves at most thirty times a second`() {
        for (hertz in listOf(60.0, 120.0, 144.0, 165.0, 240.0)) {
            val pace = FramePace(openedAt = 0.0)
            var drawn = 0
            var steps = 0
            for (frame in 0 until hertz.toInt() * 10) {
                if (pace.shouldDraw(1_000.0 + frame * 1_000.0 / hertz, moving = true, hidden = false)) drawn++
                if (pace.stepsLife) steps++
            }
            assertEquals(hertz.toInt() * 10, drawn, "$hertz Hz")
            assertTrue(steps in 240..300, "the life moved $steps times in ten seconds at $hertz Hz")
        }
    }

    @Test
    fun `the shadows are drawn again on every moving frame, and on every other frame of the town's own life`() {
        val pace = FramePace(openedAt = 0.0)
        for (frame in 0 until 30) {
            assertTrue(pace.shouldDraw(1_000.0 + frame * 1_000.0 / 60, moving = true, hidden = false))
            assertTrue(pace.drawsShadows, "moving frame $frame")
        }
        var drawn = 0
        var withShadows = 0
        for (frame in 30 until 150) {
            if (pace.shouldDraw(1_000.0 + frame * 1_000.0 / 60, moving = false, hidden = false)) {
                drawn++
                if (pace.drawsShadows) withShadows++
            }
        }
        assertTrue(drawn in 59..61, "$drawn quiet frames in two seconds")
        assertEquals((drawn + 1) / 2, withShadows)
    }

    /** How many of the frames a 60 Hz screen offers from [from] for [seconds] [pace] draws while nothing moves. */
    private fun quietFrames(pace: FramePace, from: Double, seconds: Int): Int =
        (0 until 60 * seconds).count { pace.shouldDraw(from + it * 1_000.0 / 60, moving = false, hidden = false) }

    @Test
    fun `left alone for twenty seconds, the town's life slows to fifteen a second, and a touch brings thirty back`() {
        val pace = FramePace(openedAt = 0.0)
        assertEquals(30, quietFrames(pace, 1_000.0, 1))
        val quiet = quietFrames(pace, FramePace.QUIET_AFTER_MILLIS + 1_000, 10)
        assertTrue(quiet in 140..150, "$quiet frames in ten quiet seconds")
        pace.noteInput(31_000.0)
        assertEquals(30, quietFrames(pace, 31_000.0, 1))
    }

    @Test
    fun `while a card covers the town, fifteen a second even just after a touch, and thirty once it is gone`() {
        val pace = FramePace(openedAt = 0.0)
        pace.isCovered = true
        pace.noteInput(1_000.0)
        val covered = quietFrames(pace, 1_000.0, 10)
        assertTrue(covered in 140..150, "$covered frames in ten covered seconds")
        pace.isCovered = false
        pace.noteInput(11_000.0)
        assertEquals(30, quietFrames(pace, 11_000.0, 1))
    }

    @Test
    fun `left alone, even frames drawn for the player move the town's life only fifteen times a second`() {
        val pace = FramePace(openedAt = 0.0)
        var steps = 0
        for (frame in 0 until 600) {
            assertTrue(pace.shouldDraw(30_000.0 + frame * 1_000.0 / 60, moving = true, hidden = false))
            if (pace.stepsLife) steps++
        }
        assertTrue(steps in 140..150, "the life moved $steps times in ten seconds")
    }
}
