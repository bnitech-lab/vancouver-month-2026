package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** What a tap on the screen lands on: a place (its building or its person), a spot on the ground, or the sky. */
sealed interface Picked {
    data class AtPlace(val place: Place) : Picked

    data class OnGround(val spot: Spot) : Picked
}

/**
 * The camera behind and above the character, which the player turns by dragging and brings nearer or farther by
 * pinching. [yaw], [pitch] and [distance] are what the player asked for, already kept in their limits; the camera
 * moves toward them a little each frame, so it glides instead of jumping. It follows the character ([subject]) and
 * looks at [focus], which on the street is nudged toward the street's middle so both rows are in view. It never
 * looks at the character through anything: a wall, the SkyTrain deck or a canopy in the way pulls it in front, or
 * lifts it over when that would bring it too close.
 */
class FollowCamera(subject: Point) {
    /** The turn around the character, in radians. 0 puts the camera south of it; -PI / 2 puts it west, looking east. */
    var yaw = START_YAW
        private set

    /** How far above level the camera looks down, in radians. Kept between [MIN_PITCH] and [MAX_PITCH]. */
    var pitch = START_PITCH
        private set

    /** How far the camera is from the character. Kept between [NEAR] and [FAR]. */
    var distance = START_DISTANCE
        private set

    private var shownYaw = yaw
    private var shownPitch = pitch
    private var shownDistance = distance

    /** Where the camera has followed the character to: the point it keeps in sight. */
    var subject: Point = subject
        private set

    /**
     * The point in the middle of the screen. From far back, on the street, it is nudged toward the street's middle
     * to show both rows; the nearer the camera comes, the less, so close in the character stays in the picture.
     */
    val focus: Point
        get() {
            val nudged = Neighbourhood.cameraFocus(Spot(subject.x, subject.z))
            // How far the camera really is, after any wall pulled it in.
            val reach = length(eye - subject)
            val share = ((reach - NEAR) / (FULL_NUDGE_DISTANCE - NEAR)).coerceIn(0.0, 1.0)
            val x = subject.x + (nudged.x - subject.x) * share
            return Point(x, subject.y, subject.z + (nudged.z - subject.z) * share)
        }

    var eye: Point = eyeFor()
        private set

    /** The screen's width over its height. */
    var aspect: Double = 1.0

    /** How much the camera sees from top to bottom, in degrees: more on a tall phone, so it still sees enough across. */
    val fov: Double
        get() = fovFor(aspect)

    /** Turns the camera by a drag of [dx], [dy] pixels: sideways around the character, and up or down. */
    fun orbit(dx: Double, dy: Double) {
        yaw -= dx * TURN_PER_PIXEL
        pitch = (pitch + dy * TURN_PER_PIXEL).coerceIn(MIN_PITCH, MAX_PITCH)
    }

    /**
     * Points the camera on its own, as for the night by the campfire and back again in the morning. It glides there
     * like a drag, the short way round, and keeps to the same limits.
     */
    fun aim(yaw: Double, pitch: Double, distance: Double) {
        val turns = round((this.yaw - yaw) / (2 * PI))
        this.yaw = yaw + turns * 2 * PI
        this.pitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
        this.distance = distance.coerceIn(NEAR, FAR)
    }

    /** Moves the camera [factor] times as far away: under 1 comes nearer, over 1 goes farther. */
    fun zoom(factor: Double) {
        distance = (distance * factor).coerceIn(NEAR, FAR)
    }

    /** Moves straight to [subject] with no glide, as when the character wakes up at home. */
    fun jumpTo(subject: Point) {
        this.subject = subject
        shownYaw = yaw
        shownPitch = pitch
        shownDistance = distance
        eye = eyeFor()
    }

    /**
     * Moves the camera [millis] milliseconds' worth toward what the player asked for, and toward the character at
     * [subject]. True while it is still moving, so the town keeps drawing frames; false once it has settled.
     */
    fun follow(subject: Point, millis: Double): Boolean {
        val turn = 1 - exp(-millis / TURN_MILLIS)
        shownYaw += (yaw - shownYaw) * turn
        shownPitch += (pitch - shownPitch) * turn
        shownDistance += (distance - shownDistance) * turn
        val along = 1 - exp(-millis / FOLLOW_MILLIS)
        this.subject = this.subject + (subject - this.subject) * along
        val settled = abs(yaw - shownYaw) < SETTLED && abs(pitch - shownPitch) < SETTLED &&
            abs(distance - shownDistance) < SETTLED && length(subject - this.subject) < SETTLED
        if (settled) jumpTo(subject) else eye = eyeFor()
        return !settled
    }

    /** Where [point] is on the screen, from 0.0 to 1.0 from the left and from the top, or null when it is behind. */
    fun screenPoint(point: Point): FramePoint? {
        val (forward, right, up) = axes()
        val offset = point - eye
        val depth = offset dot forward
        if (depth <= NEAR_PLANE) return null
        val half = tan(fov * PI / 360)
        val across = (offset dot right) / (depth * half * aspect)
        val upward = (offset dot up) / (depth * half)
        return FramePoint((across + 1) / 2, (1 - upward) / 2)
    }

    /**
     * What a tap at [left], [top] (0.0 to 1.0 across and down the screen) lands on: the nearest building or
     * person of a place, else the ground. A tap on another building walks to its foot. Null for the sky and the far
     * distance.
     */
    fun pick(left: Double, top: Double): Picked? {
        val ray = rayThrough(left, top)
        var nearest = Double.MAX_VALUE
        var wallHit: Wall? = null
        for (wall in Neighbourhood.walls + Neighbourhood.people) {
            val hit = distanceToBox(eye, ray, wall.box) ?: continue
            if (hit < nearest) {
                nearest = hit
                wallHit = wall
            }
        }
        val ground = if (ray.y < 0) -eye.y / ray.y else Double.MAX_VALUE
        if (wallHit != null && nearest < ground) {
            val place = wallHit.place
            if (place != null) return Picked.AtPlace(place)
            val foot = eye + ray * nearest
            return Picked.OnGround(Spot(foot.x, foot.z))
        }
        if (ground > MAX_TAP_DISTANCE) return null
        val spot = eye + ray * ground
        return Picked.OnGround(Spot(spot.x, spot.z))
    }

    /** The way from the eye through the point [left], [top] of the screen, as a vector one unit long. */
    private fun rayThrough(left: Double, top: Double): Point {
        val (forward, right, up) = axes()
        val half = tan(fov * PI / 360)
        return (forward + right * ((left * 2 - 1) * half * aspect) + up * ((1 - top * 2) * half)).unit()
    }

    /** The camera's own forward, right and up, each one unit long. */
    private fun axes(): Triple<Point, Point, Point> {
        val forward = (focus - eye).unit()
        val right = (forward cross UP).unit()
        return Triple(forward, right, right cross forward)
    }

    /**
     * Behind and above the character at the shown turn, tilt and distance, with nothing between them: a wall in the
     * way pulls the camera in front of it, and when that would bring it too close, it rises over the wall instead.
     */
    private fun eyeFor(): Point {
        var tilt = shownPitch
        while (true) {
            val out = Point(cos(tilt) * sin(shownYaw), sin(tilt), cos(tilt) * cos(shownYaw))
            var reach = shownDistance
            for (wall in Neighbourhood.walls) {
                val hit = distanceToBox(subject, out, wall.box) ?: continue
                reach = min(reach, max(0.0, hit - WALL_GAP))
            }
            if (reach >= CLOSEST || tilt >= HIGHEST_TILT) return subject + out * reach
            tilt = min(HIGHEST_TILT, tilt + 0.1)
        }
    }

    companion object {
        /** The first view: from the street's west end, high and far enough to see four buildings and their names. */
        val START_YAW = -0.5 * PI
        const val START_PITCH = 0.7
        const val START_DISTANCE = 21.0
        const val MIN_PITCH = 0.2
        const val MAX_PITCH = 1.3
        const val NEAR = 4.5
        const val FAR = 30.0

        /** From this far back the focus is nudged all the way to the street's middle. */
        const val FULL_NUDGE_DISTANCE = 15.0

        const val TURN_PER_PIXEL = 0.008
        const val TURN_MILLIS = 60.0
        const val FOLLOW_MILLIS = 110.0
        private const val SETTLED = 0.001

        /** The camera stops this far in front of a wall, and rises rather than come nearer than [CLOSEST]. */
        private const val WALL_GAP = 0.3
        private const val CLOSEST = 3.5
        private const val HIGHEST_TILT = 1.5

        const val NEAR_PLANE = 0.1
        const val FAR_PLANE = 420.0

        /** Taps farther away than this, near the horizon, are ignored. */
        const val MAX_TAP_DISTANCE = 120.0

        const val MIN_FOV = 50.0
        const val MAX_FOV = 84.0

        /** The least the camera should see from side to side, in degrees. */
        const val MIN_ACROSS = 44.0

        private val UP = Point(0.0, 1.0, 0.0)

        /** The height of the view for a screen [aspect] wide, so a tall phone still sees [MIN_ACROSS] across. */
        fun fovFor(aspect: Double): Double {
            val tall = 2 * atan(tan(MIN_ACROSS * PI / 360) / aspect) * 180 / PI
            return tall.coerceIn(MIN_FOV, MAX_FOV)
        }
    }
}

/**
 * How far along a ray from [origin] in [direction] (one unit long) it enters [box], or null when it misses.
 * A ray that starts inside the box enters it at once, at 0.
 */
fun distanceToBox(origin: Point, direction: Point, box: Block): Double? {
    val (nearX, farX) = slab(origin.x, direction.x, box.x, box.x + box.width) ?: return null
    val (nearY, farY) = slab(origin.y, direction.y, box.y, box.y + box.height) ?: return null
    val (nearZ, farZ) = slab(origin.z, direction.z, box.z, box.z + box.depth) ?: return null
    val enter = maxOf(nearX, nearY, nearZ, 0.0)
    val leave = minOf(farX, farY, farZ)
    return if (enter <= leave) enter else null
}

/** Along a ray, where it is between [low] and [high] on one axis: from, to. Null when it never is. */
private fun slab(origin: Double, direction: Double, low: Double, high: Double): Pair<Double, Double>? {
    if (direction == 0.0) {
        return if (origin in low..high) Pair(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY) else null
    }
    val first = (low - origin) / direction
    val second = (high - origin) / direction
    return Pair(min(first, second), max(first, second))
}

private operator fun Point.plus(other: Point) = Point(x + other.x, y + other.y, z + other.z)

private operator fun Point.minus(other: Point) = Point(x - other.x, y - other.y, z - other.z)

private operator fun Point.times(scale: Double) = Point(x * scale, y * scale, z * scale)

private infix fun Point.dot(other: Point) = x * other.x + y * other.y + z * other.z

private infix fun Point.cross(other: Point) =
    Point(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)

private fun length(point: Point) = sqrt(point dot point)

private fun Point.unit(): Point = this * (1 / length(this))
