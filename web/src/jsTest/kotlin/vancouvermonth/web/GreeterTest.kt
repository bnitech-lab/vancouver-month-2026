package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GreeterTest {
    private val spot = Spot(10.0, 10.0)
    private val far = Spot(30.0, 30.0)

    /** Steps [greeter] frame by frame, 33 ms apart, from [from] for [millis], with the player at [player]. */
    private fun run(greeter: Greeter, from: Double, millis: Double, player: Spot): List<Pose> =
        (1..(millis / 33).toInt()).map { greeter.step(from + it * 33.0, 33.0, player) }

    @Test
    fun `with nobody near, a townsperson stays at their place and looks about, toward the street`() {
        val greeter = Greeter(spot, facing = PI, phase = 0.0)
        val poses = run(greeter, 0.0, 20_000.0, far)
        assertTrue(poses.all { it.spot == spot }, "left their place")
        val turns = poses.map { angleBetween(PI, it.heading) }
        assertTrue(turns.all { abs(it) <= Greeter.LOOK_AROUND + 1e-9 }, "looked away from the street")
        assertTrue(turns.max() > 0.3 && turns.min() < -0.3, "never looked about")
        assertTrue(poses.all { it.wave == 0.0 && it.swing == 0.0 })
    }

    @Test
    fun `a townsperson turns to face the player who comes near`() {
        val greeter = Greeter(spot, facing = 0.0, phase = 1.0)
        run(greeter, 0.0, 2_000.0, far)
        val player = Spot(spot.x - 3.0, spot.z - 2.0)
        val poses = run(greeter, 2_000.0, 1_000.0, player)
        assertEquals(0.0, angleBetween(atan2(-3.0, -2.0), poses.last().heading), 0.02)
        assertTrue(poses.all { it.spot == spot }, "walked off")
    }

    @Test
    fun `a townsperson waves once as the player comes up, and again only after the player has gone and come back`() {
        val greeter = Greeter(spot, facing = 0.0, phase = 0.0)
        assertTrue(run(greeter, 0.0, 1_000.0, far).none { it.wave != 0.0 })
        val near = Spot(spot.x, spot.z + 2.0)
        val wave = run(greeter, 1_000.0, Greeter.WAVE_MILLIS - 100, near)
        assertTrue(wave.all { it.wave > 1.0 }, "the arm is not up")
        val after = run(greeter, 1_000.0 + Greeter.WAVE_MILLIS + 100, 3_000.0, near)
        assertTrue(after.all { it.wave == 0.0 }, "waved on and on")
        // Away and back: a second wave.
        run(greeter, 5_000.0, 500.0, far)
        assertFalse(greeter.isWaving(5_500.0))
        val again = run(greeter, 5_500.0, 500.0, near)
        assertTrue(again.all { it.wave > 1.0 }, "no wave when the player came back")
    }

    @Test
    fun `the player who stays near is not waved at again`() {
        val greeter = Greeter(spot, facing = 0.0, phase = 0.0)
        val near = Spot(spot.x + 1.0, spot.z + 2.0)
        run(greeter, 0.0, 2_000.0, near)
        // Stepping back a little, but not away, is no reason for another wave.
        val back = Spot(spot.x + 1.0, spot.z + Greeter.WAVE_DISTANCE + 0.5)
        assertTrue(run(greeter, 2_000.0, 2_000.0, back).none { it.wave != 0.0 })
        assertTrue(run(greeter, 4_000.0, 2_000.0, near).none { it.wave != 0.0 })
    }
}
