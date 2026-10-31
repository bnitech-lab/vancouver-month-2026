package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where on the town's frame a point is drawn: 0.0 to 1.0 from the left edge and from the top edge. */
data class FramePoint(val left: Double, val top: Double)

/**
 * The camera the town is seen from: up in the sky south of the town, turned a little to the east, looking down.
 * It has no perspective, so a box far away is drawn as big as one nearby, as in an isometric game.
 * The same numbers place the camera in Three.js and the building names over the picture.
 */
object TownCamera {
    /** The frame's width over its height. style.css gives the town the same shape. */
    const val ASPECT = 16.0 / 9.0

    /** How far the camera is turned east of due south, and how far it looks down, in degrees. */
    const val TURN_DEGREES = 15.0
    const val TILT_DEGREES = 28.0

    /** Half the frame's width and height, in grid squares. */
    const val HALF_WIDTH = 9.0
    const val HALF_HEIGHT = HALF_WIDTH / ASPECT

    /** The point at the middle of the frame. */
    val target = Point(8.5, 1.8, 3.2)

    /** Far enough that the whole town is in front of the camera. */
    const val DISTANCE = 60.0

    private val turn = TURN_DEGREES * PI / 180
    private val tilt = TILT_DEGREES * PI / 180

    val position: Point = Point(
        target.x + DISTANCE * sin(turn) * cos(tilt),
        target.y + DISTANCE * sin(tilt),
        target.z + DISTANCE * cos(turn) * cos(tilt),
    )

    /** Where [point] is drawn in the frame. Outside 0.0 to 1.0, it is out of the picture. */
    fun framePoint(point: Point): FramePoint {
        val x = point.x - target.x
        val y = point.y - target.y
        val z = point.z - target.z
        // How far right and how far up the point is from the middle of the frame, in grid squares.
        val right = x * cos(turn) - z * sin(turn)
        val up = -x * sin(tilt) * sin(turn) + y * cos(tilt) - z * sin(tilt) * cos(turn)
        return FramePoint((right + HALF_WIDTH) / (2 * HALF_WIDTH), (HALF_HEIGHT - up) / (2 * HALF_HEIGHT))
    }
}
