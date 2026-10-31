package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * What a tap on the screen lands on: a place (its building or its person), a passerby (by their number in the list
 * the tap was checked against), a spot on the ground, or the sky.
 */
sealed interface Picked {
    data class AtPlace(val place: Place) : Picked

    data class AtPasserby(val index: Int) : Picked

    /** The bike shop's building or its keeper. */
    data object AtShop : Picked

    /** One of the shared bikes and e-scooters, by its place in the list of them a tap was given. */
    data class AtVehicle(val index: Int) : Picked

    data class OnGround(val spot: Spot) : Picked
}

/**
 * The camera behind and above the character, which the player turns by dragging and brings nearer or farther by
 * pinching. [yaw], [pitch] and [distance] are what the player asked for, already kept in their limits; the camera
 * moves toward them a little each frame, so it glides instead of jumping. It follows the character ([subject]) and
 * looks at it ([focus]). It never looks at the character through anything that matters: one of the five places, the
 * SkyTrain deck and station, or a canopy in the way pulls it in front, or lifts it over when that would bring it too
 * close. The city's buildings do not move it; they fold down out of its way instead ([Cutaway]).
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

    /**
     * The walls near the subject, by their number in [WALLS], and the ground they were gathered round. Set before
     * [eye], which is worked out from them.
     */
    private var near = IntArray(64)
    private var nearCount = 0
    private var nearX = Double.NaN
    private var nearZ = Double.NaN
    private var nearReach = -1.0

    /** Where the camera has followed the character to: the point it keeps in sight. */
    var subject: Point = subject
        private set

    /** The point in the middle of the screen: the character, as the camera has followed it. */
    val focus: Point
        get() = subject

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
     * person of a place, the bike shop or its keeper, one of [passersby] (each one's box where they are now) or of
     * [vehicles] (the shared bikes and e-scooters that can be rented), else the ground. A tap on another building walks
     * to its foot. The city's buildings are as tall as [cutaway] draws them: a folded one is only its low plinth, so a
     * tap meant for the ground behind it lands there. Null for the sky and the far distance.
     */
    fun pick(
        left: Double,
        top: Double,
        passersby: List<Block> = emptyList(),
        cutaway: Cutaway? = null,
        vehicles: List<Block> = emptyList(),
    ): Picked? {
        val ray = rayThrough(left, top)
        var nearest = Double.MAX_VALUE
        var wallHit: Wall? = null
        for (wall in TAPPABLE) {
            val hit = distanceToBox(eye, ray, wall.box) ?: continue
            // A building or a person no nearer than the farthest ground a tap reaches is too far to walk to.
            if (hit < nearest && hit <= MAX_TAP_DISTANCE) {
                nearest = hit
                wallHit = wall
            }
        }
        var cityHit = false
        for ((index, building) in Neighbourhood.foldables.withIndex()) {
            val box = building.box
            val height = cutaway?.heightOf(index) ?: box.height
            val hit = enterAt(
                eye.x, eye.y, eye.z, ray.x, ray.y, ray.z,
                box.x, box.y, box.z, box.x + box.width, box.y + height, box.z + box.depth,
            )
            // The camera may stand inside one, as it folds out of the way; it is seen through from in there.
            if (hit > 0.0 && hit < nearest && hit <= MAX_TAP_DISTANCE) {
                nearest = hit
                cityHit = true
            }
        }
        if (cityHit) wallHit = null
        val ground = if (ray.y < 0) -eye.y / ray.y else Double.MAX_VALUE
        var passerby: Int? = null
        for ((index, box) in passersby.withIndex()) {
            val hit = distanceToBox(eye, ray, box) ?: continue
            if (hit < nearest) {
                nearest = hit
                passerby = index
            }
        }
        var vehicle: Int? = null
        for ((index, box) in vehicles.withIndex()) {
            val hit = distanceToBox(eye, ray, box) ?: continue
            if (hit < nearest) {
                nearest = hit
                vehicle = index
                passerby = null
            }
        }
        if (vehicle != null && nearest < ground) return Picked.AtVehicle(vehicle)
        if (passerby != null && nearest < ground) return Picked.AtPasserby(passerby)
        if ((wallHit != null || cityHit) && nearest < ground) {
            val place = wallHit?.place
            if (place != null) return Picked.AtPlace(place)
            if (wallHit?.shop == true) return Picked.AtShop
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
     * Behind and above the character at the shown turn, tilt and distance, with nothing that matters between them: a
     * wall in the way pulls the camera in front of it. Where a slanting look meets such a wall at once, it first looks
     * along one of the two streets either side of its turn, if that keeps it [ALONG_A_STREET] away or more; and when it
     * would still come too close, it rises over the wall instead. The city's buildings are not walls to it. It makes
     * nothing new on the way, as it runs every frame the camera glides.
     */
    private fun eyeFor(): Point {
        refreshNear(subject, shownDistance)
        var yaw = shownYaw
        var tilt = shownPitch
        var reach = reachAlong(yaw, tilt)
        if (reach < ALONG_A_STREET && reach < shownDistance) {
            val below = floor(shownYaw / QUARTER) * QUARTER
            val above = below + QUARTER
            // Already along a street, there is no other to try.
            if (shownYaw - below > AXIS_SLACK && above - shownYaw > AXIS_SLACK) {
                val nearer = if (shownYaw - below <= above - shownYaw) below else above
                val other = if (nearer == below) above else below
                val alongNearer = reachAlong(nearer, tilt)
                if (alongNearer >= ALONG_A_STREET) {
                    yaw = nearer
                    reach = alongNearer
                } else {
                    val alongOther = reachAlong(other, tilt)
                    if (alongOther >= ALONG_A_STREET) {
                        yaw = other
                        reach = alongOther
                    }
                }
            }
        }
        while (reach < CLOSEST && tilt < HIGHEST_TILT) {
            tilt = min(HIGHEST_TILT, tilt + 0.1)
            reach = reachAlong(yaw, tilt)
        }
        return Point(
            subject.x + cos(tilt) * sin(yaw) * reach,
            subject.y + sin(tilt) * reach,
            subject.z + cos(tilt) * cos(yaw) * reach,
        )
    }

    /**
     * Gathers the walls within [reach] of [from] on the ground, with room to spare, unless those gathered last time
     * still cover them: the subject has moved less than a square and the camera has come no farther.
     */
    private fun refreshNear(from: Point, reach: Double) {
        val still = abs(from.x - nearX) <= NEAR_SLACK && abs(from.z - nearZ) <= NEAR_SLACK
        if (still && reach <= nearReach - 1.5 * NEAR_SLACK) return
        nearX = from.x
        nearZ = from.z
        nearReach = reach + 3 * NEAR_SLACK
        nearCount = 0
        val walls = WALLS
        for (index in 0 until walls.size / 6) {
            val at = index * 6
            val dx = max(0.0, max(walls[at] - from.x, from.x - walls[at + 3]))
            val dz = max(0.0, max(walls[at + 2] - from.z, from.z - walls[at + 5]))
            if (dx * dx + dz * dz > nearReach * nearReach) continue
            if (nearCount == near.size) near = near.copyOf(nearCount * 2)
            near[nearCount++] = index
        }
    }

    /** How far the camera can be from the subject turned [yaw] and tilted [tilt], short of a wall, at most as asked. */
    private fun reachAlong(yaw: Double, tilt: Double): Double {
        val dx = cos(tilt) * sin(yaw)
        val dy = sin(tilt)
        val dz = cos(tilt) * cos(yaw)
        var reach = shownDistance
        val walls = WALLS
        for (n in 0 until nearCount) {
            val at = near[n] * 6
            val hit = enterAt(
                subject.x, subject.y, subject.z, dx, dy, dz,
                walls[at], walls[at + 1], walls[at + 2], walls[at + 3], walls[at + 4], walls[at + 5],
            )
            if (hit >= 0.0) reach = min(reach, max(0.0, hit - WALL_GAP))
        }
        return reach
    }

    companion object {
        /**
         * The first view: from the south-west of 내 방, looking north-east over the West End to downtown, low and far
         * enough to see four buildings and their names.
         */
        val START_YAW = -0.32 * PI
        const val START_PITCH = 0.45
        const val START_DISTANCE = 21.0

        /**
         * The walls the camera keeps out of within [reach] of [from] on the ground: the only ones a camera that far off
         * can meet. For tests and one-off checks; the camera itself keeps its own list.
         */
        fun wallsNear(from: Point, reach: Double): List<Block> = Neighbourhood.walls.mapNotNull { wall ->
            val box = wall.box
            if (wall.folds) return@mapNotNull null
            val dx = max(0.0, max(box.x - from.x, from.x - box.x - box.width))
            val dz = max(0.0, max(box.z - from.z, from.z - box.z - box.depth))
            if (dx * dx + dz * dz <= reach * reach) box else null
        }
        const val MIN_PITCH = 0.2
        const val MAX_PITCH = 1.3
        const val NEAR = 4.5
        const val FAR = 30.0

        const val TURN_PER_PIXEL = 0.008
        const val TURN_MILLIS = 60.0
        const val FOLLOW_MILLIS = 110.0
        private const val SETTLED = 0.001

        /** The camera stops this far in front of a wall, and rises rather than come nearer than [CLOSEST]. */
        private const val WALL_GAP = 0.3
        private const val CLOSEST = 3.5
        private const val HIGHEST_TILT = 1.5

        /** How far the camera looks down when it comes round to a place's door as the character arrives there. */
        const val ARRIVAL_TILT = 0.6

        /** Pulled in nearer than this, the camera tries looking along a street instead. */
        const val ALONG_A_STREET = 8.0
        private const val QUARTER = PI / 2
        private const val AXIS_SLACK = 1e-6

        /** How far the subject may move before the walls near it are gathered again. */
        private const val NEAR_SLACK = 1.0

        /**
         * The box of every wall the camera keeps out of as six numbers, its low and high corners, gathered once: the
         * five places, the SkyTrain deck, station and tunnel mouth, the awning and the canopy, never the city's
         * buildings.
         */
        private val WALLS: DoubleArray by lazy {
            val walls = Neighbourhood.walls.filter { !it.folds }
            DoubleArray(walls.size * 6).also { numbers ->
                for ((index, wall) in walls.withIndex()) {
                    val box = wall.box
                    numbers[index * 6] = box.x
                    numbers[index * 6 + 1] = box.y
                    numbers[index * 6 + 2] = box.z
                    numbers[index * 6 + 3] = box.x + box.width
                    numbers[index * 6 + 4] = box.y + box.height
                    numbers[index * 6 + 5] = box.z + box.depth
                }
            }
        }

        /**
         * What a tap can land on besides the city's buildings, which fold: every other wall, and the five places'
         * people, gathered once.
         */
        private val TAPPABLE: List<Wall> by lazy { Neighbourhood.walls.filter { !it.folds } + Neighbourhood.people }

        const val NEAR_PLANE = 0.1
        /** Past the haze nothing shows, so nothing past it is drawn: a little beyond [Neighbourhood.HAZE_FAR]. */
        const val FAR_PLANE = Neighbourhood.HAZE_FAR + 20.0

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
    val enter = enterAt(
        origin.x, origin.y, origin.z, direction.x, direction.y, direction.z,
        box.x, box.y, box.z, box.x + box.width, box.y + box.height, box.z + box.depth,
    )
    return if (enter >= 0.0) enter else null
}

/**
 * How far along the ray from [ox], [oy], [oz] going [dx], [dy], [dz] it enters the box from its low corner to its high
 * corner, or -1 when it misses: the slabs between each pair of faces, worked out in place so nothing new is made.
 */
private fun enterAt(
    ox: Double, oy: Double, oz: Double,
    dx: Double, dy: Double, dz: Double,
    lowX: Double, lowY: Double, lowZ: Double,
    highX: Double, highY: Double, highZ: Double,
): Double {
    var enter = 0.0
    var leave = Double.POSITIVE_INFINITY
    if (dx == 0.0) {
        if (ox < lowX || ox > highX) return -1.0
    } else {
        val a = (lowX - ox) / dx
        val b = (highX - ox) / dx
        enter = max(enter, min(a, b))
        leave = min(leave, max(a, b))
    }
    if (dy == 0.0) {
        if (oy < lowY || oy > highY) return -1.0
    } else {
        val a = (lowY - oy) / dy
        val b = (highY - oy) / dy
        enter = max(enter, min(a, b))
        leave = min(leave, max(a, b))
    }
    if (dz == 0.0) {
        if (oz < lowZ || oz > highZ) return -1.0
    } else {
        val a = (lowZ - oz) / dz
        val b = (highZ - oz) / dz
        enter = max(enter, min(a, b))
        leave = min(leave, max(a, b))
    }
    return if (enter <= leave) enter else -1.0
}

private operator fun Point.plus(other: Point) = Point(x + other.x, y + other.y, z + other.z)

private operator fun Point.minus(other: Point) = Point(x - other.x, y - other.y, z - other.z)

private operator fun Point.times(scale: Double) = Point(x * scale, y * scale, z * scale)

private infix fun Point.dot(other: Point) = x * other.x + y * other.y + z * other.z

private infix fun Point.cross(other: Point) =
    Point(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)

private fun length(point: Point) = sqrt(point dot point)

private fun Point.unit(): Point = this * (1 / length(this))
