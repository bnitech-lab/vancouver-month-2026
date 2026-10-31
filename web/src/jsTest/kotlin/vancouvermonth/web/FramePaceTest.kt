package vancouvermonth.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FramePaceTest {
    /** How many of the frames a screen offers at [hertz] for [seconds] the pace draws. */
    private fun drawn(hertz: Double, moving: Boolean, hidden: Boolean = false, seconds: Int = 1): Int {
        val pace = FramePace()
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
        val pace = FramePace()
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
        val pace = FramePace()
        assertTrue(pace.shouldDraw(0.0, moving = true, hidden = false))
        assertFalse(pace.shouldDraw(16.7, moving = false, hidden = false))
        assertTrue(pace.shouldDraw(33.4, moving = false, hidden = false))
        assertFalse(pace.shouldDraw(40.0, moving = false, hidden = false))
        pace.reset()
        assertTrue(pace.shouldDraw(41.0, moving = false, hidden = false))
    }
}
