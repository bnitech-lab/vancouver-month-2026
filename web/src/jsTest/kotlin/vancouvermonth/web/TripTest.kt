package vancouvermonth.web

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripTest {
    private val route = listOf(Spot(0.0, 0.0), Spot(0.0, 4.0), Spot(6.0, 4.0))

    @Test
    fun `a trip moves along its route at its speed`() {
        val trip = Trip(route, Trip.WALK_SPEED)
        assertEquals(10.0, trip.length, 1e-9)
        assertEquals(Spot(0.0, 0.0), trip.spotAfter(0.0))
        assertEquals(Spot(0.0, 0.0), trip.spotAfter(-100.0))
        val afterOneSecond = trip.spotAfter(1_000.0)
        assertEquals(0.0, afterOneSecond.x, 1e-9)
        assertEquals(Trip.WALK_SPEED, afterOneSecond.z, 1e-9)
        assertEquals(Spot(6.0, 4.0), trip.spotAfter(trip.millis))
        assertEquals(Spot(6.0, 4.0), trip.spotAfter(trip.millis * 2))
        assertFalse(trip.isOverAfter(trip.millis - 1))
        assertTrue(trip.isOverAfter(trip.millis))
    }

    @Test
    fun `a dash covers the same route in well under half the time`() {
        val walk = Trip(route, Trip.WALK_SPEED)
        val dash = Trip(route, Trip.DASH_SPEED)
        assertTrue(dash.millis < walk.millis / 2, "${dash.millis} against ${walk.millis}")
    }

    @Test
    fun `the character faces the way it walks, and keeps facing that way at the end`() {
        val trip = Trip(route, Trip.WALK_SPEED)
        assertEquals(0.0, trip.headingAfter(100.0), 1e-9)
        assertEquals(PI / 2, trip.headingAfter(trip.millis * 0.9), 1e-9)
        assertEquals(PI / 2, trip.headingAfter(trip.millis * 3), 1e-9)
        assertEquals(-PI / 2, Trip(listOf(Spot(5.0, 0.0), Spot(1.0, 0.0)), Trip.WALK_SPEED).headingAfter(10.0), 1e-9)
        assertEquals(PI, Trip(listOf(Spot(0.0, 5.0), Spot(0.0, 1.0)), Trip.WALK_SPEED).headingAfter(10.0), 1e-9)
    }

    @Test
    fun `the character bobs and swings its legs while walking, and stands still at the end`() {
        val trip = Trip(route, Trip.WALK_SPEED)
        assertEquals(0.0, trip.liftAfter(0.0), 1e-9)
        val lifts = (1..20).map { trip.liftAfter(trip.millis * it / 21) }
        assertTrue(lifts.all { it in 0.0..Trip.STEP_HEIGHT })
        assertTrue(lifts.any { it > 0.0 })
        val swings = (1..20).map { trip.swingAfter(trip.millis * it / 21) }
        assertTrue(swings.any { it > 0.1 } && swings.any { it < -0.1 }, "$swings")
        assertEquals(0.0, trip.swingAfter(trip.millis))
    }

    @Test
    fun `a trip that goes nowhere is over at once`() {
        val trip = Trip(listOf(Spot(3.0, 3.0), Spot(3.0, 3.0)), Trip.WALK_SPEED)
        assertTrue(trip.isOverAfter(0.0))
        assertEquals(Spot(3.0, 3.0), trip.spotAfter(50.0))
    }
}

class TapTimerTest {
    @Test
    fun `two quick taps in the same place are a double tap`() {
        val taps = TapTimer()
        assertFalse(taps.isDouble(1_000.0, 100.0, 200.0))
        assertTrue(taps.isDouble(1_000.0 + TapTimer.DOUBLE_TAP_MILLIS, 110.0, 210.0))
    }

    @Test
    fun `taps too slow or too far apart are single taps`() {
        val taps = TapTimer()
        assertFalse(taps.isDouble(1_000.0, 100.0, 200.0))
        assertFalse(taps.isDouble(1_000.0 + TapTimer.DOUBLE_TAP_MILLIS + 1, 100.0, 200.0))
        assertFalse(taps.isDouble(1_400.0, 100.0 + TapTimer.DOUBLE_TAP_PIXELS + 1, 200.0))
    }

    @Test
    fun `a third quick tap starts again`() {
        val taps = TapTimer()
        assertFalse(taps.isDouble(0.0, 50.0, 50.0))
        assertTrue(taps.isDouble(150.0, 50.0, 50.0))
        assertFalse(taps.isDouble(250.0, 50.0, 50.0))
        assertTrue(taps.isDouble(400.0, 50.0, 50.0))
    }
}
