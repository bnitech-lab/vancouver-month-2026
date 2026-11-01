package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A SkyTrain ride under downtown as it is shown, worked out without drawing: the platform at 다운타운역 when getting on
 * or stepping out there, the tunnel in between with the camera always moving, and the mouth's daylight ahead at
 * 터널 입구역.
 */
class UndergroundRideTest {
    private val stations = TransitLines.stations

    /** Every [STEP] milliseconds of [going] while it is under downtown, with the camera's eye and where it looks. */
    private fun under(going: Journey, calm: Boolean = false): List<Pair<Double, DoubleArray>> {
        val shots = mutableListOf<Pair<Double, DoubleArray>>()
        var elapsed = 0.0
        while (elapsed < going.millis) {
            if (UndergroundRide.isUnder(going, elapsed)) {
                shots += elapsed to DoubleArray(6).also { UndergroundRide.camera(going, elapsed, calm, it) }
            }
            elapsed += STEP
        }
        return shots
    }

    /** The longest time, in milliseconds, the camera's eye stood exactly still among [shots]. */
    private fun longestStill(shots: List<Pair<Double, DoubleArray>>): Double {
        var longest = 0.0
        var since = shots.first().first
        for ((before, after) in shots.zipWithNext()) {
            val moved = (0..2).any { before.second[it] != after.second[it] }
            if (moved) since = after.first else longest = maxOf(longest, after.first - since)
        }
        return longest
    }

    @Test
    fun `the tunnel runs straight along x from the mouth, past the platform under downtown`() {
        val mouth = UndergroundRide.MOUTH
        var along = 0.0
        while (along >= UndergroundRide.TAIL - mouth.x) {
            val spot = Neighbourhood.guideway.spotAt(along)
            assertEquals(UndergroundRide.xAt(along), spot.x, 1e-9, "$along")
            assertEquals(UndergroundRide.LINE_Z, spot.z, 1e-9, "$along")
            along -= 0.5
        }
        // The platform's middle is under the station's entrance on the street, and the train fits in the hall.
        val station = stations.first { it.underground }
        assertEquals(station, stations[UndergroundRide.STATION])
        assertEquals(station.along, UndergroundRide.PLATFORM)
        assertEquals("다운타운역", UndergroundRide.NAME)
        assertTrue(abs(station.door.x - UndergroundRide.PLATFORM_X) < 2.0, "${station.door}")
        assertTrue(UndergroundRide.PLATFORM_X - UndergroundRide.TRAIN_HALF > UndergroundRide.HALL_WEST)
        assertTrue(UndergroundRide.PLATFORM_X + UndergroundRide.TRAIN_HALF < UndergroundRide.HALL_EAST)
        assertTrue(UndergroundRide.HALL_EAST < mouth.x && UndergroundRide.TAIL < UndergroundRide.HALL_WEST)
    }

    @Test
    fun `getting on under downtown, the train comes in to the platform, opens its doors and takes the character`() {
        val going = Journey.train(stations[0], stations[1])
        assertTrue(UndergroundRide.startsUnder(going))
        assertFalse(UndergroundRide.endsUnder(going))
        assertEquals(UndergroundRide.BOARD_MILLIS, going.boardMillis)
        assertEquals(Journey.ALIGHT_MILLIS, going.alightMillis)
        // At first the character waits on the platform, facing the track, and the train is still down the tunnel.
        val waiting = assertNotNull(UndergroundRide.rider(going, 0.0))
        assertEquals(Spot(UndergroundRide.PLATFORM_X, UndergroundRide.LINE_Z + UndergroundRide.WAIT), waiting.spot)
        assertEquals(PI, waiting.heading)
        assertEquals(UndergroundRide.FLOOR, waiting.lift)
        assertEquals(UndergroundRide.PLATFORM - UndergroundRide.APPROACH, UndergroundRide.trainAlong(going, 0.0))
        assertEquals(0.0, UndergroundRide.doorsOpen(going, 0.0))
        // It slows all the way in, and stands at the platform when the doors open.
        var last = UndergroundRide.trainAlong(going, 0.0)
        var elapsed = STEP
        while (elapsed <= UndergroundRide.ARRIVE_MILLIS) {
            val along = UndergroundRide.trainAlong(going, elapsed)
            assertTrue(along >= last && along <= UndergroundRide.PLATFORM, "$elapsed: $along")
            last = along
            elapsed += STEP
        }
        assertEquals(UndergroundRide.PLATFORM, UndergroundRide.trainAlong(going, UndergroundRide.ARRIVE_MILLIS))
        val open = UndergroundRide.ARRIVE_MILLIS + UndergroundRide.DOORS_MILLIS
        assertEquals(1.0, UndergroundRide.doorsOpen(going, open))
        // The character steps across to the doors and is gone in; the doors shut before the train pulls out.
        val stepping = assertNotNull(UndergroundRide.rider(going, open + UndergroundRide.STEP_MILLIS / 2))
        assertTrue(stepping.spot.z < waiting.spot.z && stepping.swing != 0.0, "$stepping")
        assertNull(UndergroundRide.rider(going, open + UndergroundRide.STEP_MILLIS))
        assertEquals(0.0, UndergroundRide.doorsOpen(going, going.boardMillis))
        assertEquals(UndergroundRide.PLATFORM, going.alongAt(going.boardMillis))
        assertTrue(going.alongAt(going.boardMillis + 500) > UndergroundRide.PLATFORM)
        // Getting on, the camera is in the hall, behind the train, looking east along the platform.
        val view = DoubleArray(6).also { UndergroundRide.camera(going, 0.0, false, it) }
        assertTrue(view[0] in UndergroundRide.HALL_WEST..UndergroundRide.HALL_EAST, "${view[0]}")
        assertTrue(view[3] > view[0] && view[1] > UndergroundRide.FLOOR, view.toList().toString())
        assertTrue(view[2] - UndergroundRide.LINE_Z > UndergroundRide.EDGE, "over the platform: ${view[2]}")
    }

    @Test
    fun `from under downtown the camera never stands still, and comes up behind the train to the mouth's daylight`() {
        for (to in listOf(1, 4, TransitLines.TOWN_CENTRE + 1)) {
            val going = Journey.train(stations[0], stations[to])
            val shots = under(going)
            assertTrue(longestStill(shots) <= MOMENT, "to ${stations[to]}: still ${longestStill(shots)} ms")
            // The last frame under the ground is in the tunnel, near the mouth, looking out at it.
            val (_, last) = shots.last()
            assertTrue(last[0] > UndergroundRide.HALL_EAST, "${last[0]}")
            assertTrue(last[3] > last[0], "looking back: ${last.toList()}")
            assertTrue(UndergroundRide.MOUTH.x - last[0] in 0.0..20.0, "${last[0]}")
            assertTrue(last[1] < UndergroundRide.TUNNEL_TOP, "through the tunnel's roof: ${last[1]}")
            // And then the town: the train's middle out past where the whole train was in the tunnel.
            assertFalse(UndergroundRide.isUnder(going, shots.last().first + STEP))
            // Every eye under the ground is inside the hall or the tunnel.
            for ((elapsed, eye) in shots) {
                assertTrue(eye[0] in UndergroundRide.EYE_WEST..UndergroundRide.EYE_EAST, "$elapsed: ${eye[0]}")
                assertTrue(abs(eye[2] - UndergroundRide.LINE_Z) < UndergroundRide.HALL_SOUTH, "$elapsed: ${eye[2]}")
            }
        }
    }

    @Test
    fun `going out from under downtown the camera rides most of a second in the tunnel, and the train comes out`() {
        for (to in listOf(1, 4, TransitLines.TOWN_CENTRE + 1)) {
            val going = Journey.train(stations[0], stations[to])
            val inTunnel = under(going).count { it.second[0] > UndergroundRide.HALL_EAST } * STEP
            println("TUNNEL from under downtown to station $to: $inTunnel ms in the tunnel")
            assertTrue(inTunnel >= 800.0, "to ${stations[to]}: $inTunnel ms in the tunnel")
            // At the cut the town's train is coming out of the mouth, and the ride has not yet stopped.
            val cut = UndergroundRide.cutMillis(going)
            assertTrue(going.alongAt(cut) > -Neighbourhood.TRAIN_LENGTH / 2, "to ${stations[to]}: ${going.alongAt(cut)}")
            assertTrue(cut < going.millis - going.alightMillis, "to ${stations[to]}")
            assertEquals(cut, UndergroundRide.cutMillis(going))
        }
    }

    @Test
    fun `into the tunnel the camera starts at the mouth, and at 다운타운역 the character steps out on the platform`() {
        for (from in listOf(1, 3, TransitLines.TOWN_CENTRE + 1)) {
            val going = Journey.train(stations[from], stations[0])
            assertTrue(UndergroundRide.endsUnder(going))
            assertEquals(UndergroundRide.ALIGHT_MILLIS, going.alightMillis)
            assertEquals(Journey.BOARD_MILLIS, going.boardMillis)
            val shots = under(going)
            assertTrue(longestStill(shots) <= MOMENT, "from ${stations[from]}: still ${longestStill(shots)} ms")
            // The first frame under the ground is at the mouth, looking in after the train.
            val (_, first) = shots.first()
            assertTrue(UndergroundRide.EYE_EAST - first[0] < 2.0, "${first[0]}")
            assertTrue(first[3] < first[0], "looking out: ${first.toList()}")
            // Under the ground until the character steps out at the station's door.
            assertTrue(UndergroundRide.isUnder(going, going.millis - 1.0))
            assertEquals(stations[0].door, going.out)
            // At the end, in the hall: the doors open, the character steps out and walks off, the doors shut.
            val stopped = going.millis - going.alightMillis
            assertEquals(UndergroundRide.PLATFORM, going.alongAt(stopped), 1e-9)
            assertNull(UndergroundRide.rider(going, stopped))
            assertEquals(1.0, UndergroundRide.doorsOpen(going, stopped + UndergroundRide.DOORS_MILLIS))
            val out = assertNotNull(UndergroundRide.rider(going, stopped + UndergroundRide.DOORS_MILLIS + 300))
            assertEquals(0.0, out.heading)
            val off = assertNotNull(UndergroundRide.rider(going, going.millis - 1.0))
            assertEquals(UndergroundRide.LINE_Z + UndergroundRide.WAIT, off.spot.z)
            assertTrue(off.spot.x > UndergroundRide.PLATFORM_X, "${off.spot}")
            assertEquals(0.0, UndergroundRide.doorsOpen(going, going.millis - 1.0))
            val (_, last) = shots.last()
            assertTrue(last[0] in UndergroundRide.HALL_WEST..UndergroundRide.HALL_EAST, "${last[0]}")
            assertTrue(abs(off.spot.x - last[0]) > 3.0, "the character walked into the camera")
        }
    }

    @Test
    fun `with less motion the camera does not drift on the platform, though it still goes with the train`() {
        val going = Journey.train(stations[0], stations[1])
        val calm = under(going, calm = true)
        val waiting = calm.filter { it.first < going.boardMillis }
        assertTrue(waiting.all { it.second[0] == UndergroundRide.BOARD_EYE }, "it drifted")
        assertTrue(calm.last().second[0] > UndergroundRide.HALL_EAST, "it did not go with the train")
        val drifting = under(going).filter { it.first < going.boardMillis }
        assertTrue(drifting.last().second[0] > drifting.first().second[0])
    }

    @Test
    fun `a ride up on the line is never under the ground, and the train goes as the ride says`() {
        val going = Journey.train(stations[1], stations[3])
        assertFalse(UndergroundRide.startsUnder(going) || UndergroundRide.endsUnder(going))
        assertEquals(Journey.BOARD_MILLIS, going.boardMillis)
        assertEquals(Journey.ALIGHT_MILLIS, going.alightMillis)
        var elapsed = 0.0
        while (elapsed < going.goneMillis) {
            assertFalse(UndergroundRide.isUnder(going, elapsed), "$elapsed")
            assertEquals(going.alongAt(elapsed), UndergroundRide.trainAlong(going, elapsed))
            assertEquals(going.speedAt(elapsed), UndergroundRide.trainSpeed(going, elapsed))
            assertEquals(0.0, UndergroundRide.doorsOpen(going, elapsed))
            assertNull(UndergroundRide.rider(going, elapsed))
            elapsed += STEP
        }
    }

    @Test
    fun `the train coming in goes as fast as it moves, for its rumble`() {
        val going = Journey.train(stations[0], stations[2])
        var elapsed = 50.0
        while (elapsed < UndergroundRide.ARRIVE_MILLIS - 50) {
            val moved = UndergroundRide.trainAlong(going, elapsed + 5) - UndergroundRide.trainAlong(going, elapsed - 5)
            assertEquals(moved / 10 * 1_000, UndergroundRide.trainSpeed(going, elapsed), 0.05, "$elapsed")
            elapsed += 100
        }
        assertTrue(UndergroundRide.trainSpeed(going, 0.0) > 0.0)
        assertEquals(0.0, UndergroundRide.trainSpeed(going, UndergroundRide.ARRIVE_MILLIS + 100))
    }

    private companion object {
        /** How often a frame is asked for, about a phone's screen rate. */
        const val STEP = 16.0

        /** The longest the camera may stand still under the ground: a moment, no more. */
        const val MOMENT = 200.0
    }
}
