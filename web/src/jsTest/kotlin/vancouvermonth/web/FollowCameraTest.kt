package vancouvermonth.web

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FollowCameraTest {
    private val start = Point(20.0, 1.0, 13.5)

    /** A camera on a tall phone, settled at [focus], turned to [yaw]. */
    private fun camera(focus: Point = start, yaw: Double = FollowCamera.START_YAW): FollowCamera {
        val camera = FollowCamera(focus)
        camera.aspect = 375.0 / 812
        camera.orbit((camera.yaw - yaw) / FollowCamera.TURN_PER_PIXEL, 0.0)
        camera.jumpTo(focus)
        return camera
    }

    @Test
    fun `dragging turns the camera, and the tilt stays between its limits`() {
        val camera = camera()
        val yaw = camera.yaw
        camera.orbit(100.0, 0.0)
        assertEquals(yaw - 100 * FollowCamera.TURN_PER_PIXEL, camera.yaw, 1e-9)
        camera.orbit(0.0, 10_000.0)
        assertEquals(FollowCamera.MAX_PITCH, camera.pitch)
        camera.orbit(0.0, -10_000.0)
        assertEquals(FollowCamera.MIN_PITCH, camera.pitch)
    }

    @Test
    fun `pinching brings the camera nearer or farther, within its limits`() {
        val camera = camera()
        camera.zoom(0.5)
        assertEquals(FollowCamera.START_DISTANCE * 0.5, camera.distance, 1e-9)
        camera.zoom(0.01)
        assertEquals(FollowCamera.NEAR, camera.distance)
        camera.zoom(100.0)
        assertEquals(FollowCamera.FAR, camera.distance)
    }

    @Test
    fun `the camera glides to where it was asked, then settles`() {
        val camera = camera()
        camera.orbit(50.0, 20.0)
        val subject = Point(22.0, 1.0, 13.5)
        assertTrue(camera.follow(subject, 16.0), "still moving after one frame")
        var frames = 0
        while (camera.follow(subject, 16.0)) frames++
        assertTrue(frames < 120, "settled in $frames frames")
        assertEquals(subject, camera.focus)
        assertFalse(camera.follow(subject, 16.0))
    }

    @Test
    fun `the focus is in the middle of the screen, and the eye is behind and above it`() {
        val camera = camera()
        val middle = assertNotNull(camera.screenPoint(camera.focus))
        assertEquals(0.5, middle.left, 1e-9)
        assertEquals(0.5, middle.top, 1e-9)
        assertTrue(camera.eye.y > camera.focus.y)
        // A point straight behind the eye is not on the screen at all.
        val behind = Point(2 * camera.eye.x - camera.focus.x, camera.eye.y, 2 * camera.eye.z - camera.focus.z)
        assertNull(camera.screenPoint(behind))
    }

    @Test
    fun `the ground is in view at the lowest and the highest tilt`() {
        for (tilt in listOf(-10_000.0, 10_000.0)) {
            val camera = camera()
            camera.orbit(0.0, tilt)
            camera.jumpTo(start)
            val bottom = camera.pick(0.5, 0.95)
            assertTrue(bottom is Picked.OnGround, "tilt $tilt: $bottom")
        }
    }

    @Test
    fun `a tap on the ground picks that spot`() {
        val camera = camera()
        val spot = Spot(23.0, 14.0)
        val onScreen = assertNotNull(camera.screenPoint(Point(spot.x, 0.0, spot.z)))
        val picked = camera.pick(onScreen.left, onScreen.top) as Picked.OnGround
        assertEquals(spot.x, picked.spot.x, 1e-6)
        assertEquals(spot.z, picked.spot.z, 1e-6)
    }

    @Test
    fun `a tap on a person or a building opens its place`() {
        for (venue in Neighbourhood.venues) {
            // Stand where the player talks, and look at the person from the street.
            val yaw = if (venue.doorFacesSouth) 0.0 else PI
            val camera = camera(Point(venue.talkSpot.x, 1.0, venue.talkSpot.z), yaw)
            val head = assertNotNull(camera.screenPoint(Point(venue.person.x, 1.2, venue.person.z)), "${venue.place}")
            assertEquals(Picked.AtPlace(venue.place), camera.pick(head.left, head.top), "${venue.place}")
            val wall = Point(venue.x + 0.6, venue.height - 0.5, if (venue.doorFacesSouth) venue.z + venue.depth + 0.0 else venue.z + 0.0)
            val onWall = camera.screenPoint(wall)
            if (onWall != null && onWall.left in 0.0..1.0 && onWall.top in 0.0..1.0) {
                assertEquals(Picked.AtPlace(venue.place), camera.pick(onWall.left, onWall.top), "${venue.place}'s wall")
            }
        }
    }

    @Test
    fun `the sky picks nothing`() {
        // Looking north over the water, as low as the camera goes.
        val camera = camera(yaw = 0.0)
        camera.orbit(0.0, -10_000.0)
        camera.jumpTo(start)
        assertNull(camera.pick(0.5, 0.0))
    }

    @Test
    fun `a building between the character and the camera never hides the character`() {
        // Backed against a building, looking from behind it: the camera comes in front of the wall or rises over it.
        for (venue in Neighbourhood.venues) {
            val yaw = if (venue.doorFacesSouth) PI else 0.0
            val camera = camera(Point(venue.door.x, 1.0, venue.door.z), yaw)
            val eye = camera.eye
            val toEye = Point(eye.x - camera.focus.x, eye.y - camera.focus.y, eye.z - camera.focus.z)
            val reach = kotlin.math.sqrt(toEye.x * toEye.x + toEye.y * toEye.y + toEye.z * toEye.z)
            val way = Point(toEye.x / reach, toEye.y / reach, toEye.z / reach)
            for (wall in Neighbourhood.walls) {
                val hit = distanceToBox(camera.focus, way, wall.box)
                assertTrue(hit == null || hit >= reach, "${venue.place}: ${wall.place ?: "a building"} stands between")
            }
            assertTrue(reach > 3.0, "${venue.place}: the camera is only $reach away")
        }
    }

    @Test
    fun `a tall phone sees more from top to bottom, and never too little across`() {
        assertEquals(FollowCamera.MAX_FOV, FollowCamera.fovFor(375.0 / 812))
        assertEquals(FollowCamera.MIN_FOV, FollowCamera.fovFor(16.0 / 9))
        val square = FollowCamera.fovFor(1.0)
        assertTrue(square in FollowCamera.MIN_FOV..FollowCamera.MAX_FOV)
    }

    @Test
    fun `a ray finds where it enters a box`() {
        val box = Block(2.0, 0.0, -1.0, 2.0, 2.0, 2.0, 0)
        assertEquals(2.0, distanceToBox(Point(0.0, 1.0, 0.0), Point(1.0, 0.0, 0.0), box))
        assertNull(distanceToBox(Point(0.0, 1.0, 0.0), Point(-1.0, 0.0, 0.0), box))
        assertNull(distanceToBox(Point(0.0, 5.0, 0.0), Point(1.0, 0.0, 0.0), box))
        assertEquals(0.0, distanceToBox(Point(3.0, 1.0, 0.0), Point(1.0, 0.0, 0.0), box))
    }
}
