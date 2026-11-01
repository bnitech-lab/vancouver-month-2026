package vancouvermonth.web

import kotlin.js.json
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.browser.document
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import org.w3c.dom.CENTER
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.MIDDLE
import vancouvermonth.text.Transit
import vancouvermonth.text.Transport

/**
 * A SkyTrain ride under downtown as it is shown, worked out from the ride and its time alone, so a test can ask where
 * anything is without drawing. While the whole train is in the tunnel ([isUnder]) the town is not drawn; the platform of
 * 다운타운역 and the tunnel from it to the mouth by 터널 입구역 are ([UndergroundScene]). Getting on there, the train comes
 * in from the tunnel's far end, its doors open, the character steps in and it pulls out; stepping out, the doors open and
 * the character walks off along the platform. In between the camera rides behind the train, so the tunnel's lamps stream
 * past and the mouth comes up ahead; going out, it rides a while in the tunnel as the train runs out into the open, and
 * then cuts to the town ([cutMillis]). The tunnel runs straight along x from the mouth, as the guideway's first leg
 * does, so a point along the line is at [xAt] and [LINE_Z], at the guideway's own height.
 */
object UndergroundRide {
    /** Where the tunnel opens into daylight: the guideway's first point. */
    val MOUTH: Spot = Neighbourhood.guideway.points.first()
    val LINE_Z: Double = MOUTH.z

    /**
     * The station under downtown: its place among the stations, its name on the sign, and how far along the line the
     * train's middle stands at it.
     */
    val STATION: Int = TransitLines.stations.indexOfFirst { it.underground }
    val NAME: String = Transit.STATIONS[STATION]
    val PLATFORM: Double = TransitLines.stations[STATION].along


    /** Where along x a point [along] the line is, under downtown. */
    fun xAt(along: Double): Double = MOUTH.x + along

    /** Getting on at the platform: the train comes in, its doors open, the character steps in, and they close. */
    const val ARRIVE_MILLIS = 1_500.0
    const val DOORS_MILLIS = 400.0
    const val STEP_MILLIS = 600.0
    const val BOARD_MILLIS = ARRIVE_MILLIS + DOORS_MILLIS + STEP_MILLIS + DOORS_MILLIS

    /** Stepping out there: the doors open, the character steps out, they close, and it walks off along the platform. */
    const val ALIGHT_MILLIS = 1_900.0
    private const val DOORS_SHUT = DOORS_MILLIS + STEP_MILLIS + DOORS_MILLIS

    /** How far back down the tunnel the train comes in from, to stop at the platform. */
    const val APPROACH = 22.0

    /** How high the guideway runs, which the tunnel keeps, and the platform's floor above it. */
    const val DECK = Neighbourhood.DECK_HEIGHT
    const val FLOOR = DECK + 0.55

    /**
     * The train under the ground, drawn larger than up on the line so the character fits through its doors: its cars,
     * how far its two ends are from its middle, and its sides and roof from the line.
     */
    const val CARS = 3
    const val CAR_LENGTH = 3.6
    const val CAR_GAP = 0.15
    const val TRAIN_HALF = (CARS * CAR_LENGTH + (CARS - 1) * CAR_GAP) / 2
    const val CAR_SIDE = 0.95

    /**
     * Below this far along the line the whole train, as drawn under the ground, is in the tunnel: a ride into it from the
     * line is shown there from then on, and a ride starting there is one from under downtown.
     */
    const val UNDER = -(TRAIN_HALF + 1.0)
    const val CAR_BOTTOM = DECK + 0.35
    const val CAR_TOP = DECK + 2.75

    /**
     * The station's hall, [HALL_HALF] each way from the platform's middle: the track along its north side, the platform
     * from [EDGE] off the line to the south wall at [HALL_SOUTH], under a ceiling at [HALL_TOP]. The tunnel either side
     * is [TUNNEL_HALF] wide each way from the line and [TUNNEL_TOP] high.
     */
    val PLATFORM_X: Double = xAt(PLATFORM)
    const val HALL_HALF = 11.0
    val HALL_WEST: Double = PLATFORM_X - HALL_HALF
    val HALL_EAST: Double = PLATFORM_X + HALL_HALF
    const val EDGE = 1.05
    const val HALL_SOUTH = 5.0
    const val HALL_TOP = DECK + 4.6
    const val TUNNEL_HALF = 2.0
    const val TUNNEL_TOP = DECK + 3.6

    /** The tunnel's far end, past where the train comes in from. */
    val TAIL: Double = xAt(PLATFORM - APPROACH) - TRAIN_HALF - 4.0

    /** Where the character waits on the platform, off the line, and where it goes through the doors. */
    const val WAIT = 2.0
    const val DOORWAY = 1.0

    /** How fast the character walks off along the platform after stepping out, in units a second. */
    const val WALK_OFF = 2.4

    /**
     * The camera: how far behind the train's middle it rides, how far ahead of itself it looks, and how fast it drifts
     * on the platform while the train stands, in units a second; where it starts in the hall for getting on (behind
     * where the train will stand), the farthest west and east it goes, and how softly it comes to the east end.
     */
    const val BEHIND = TRAIN_HALF + 3.8
    const val LOOK = 7.0
    const val DRIFT = 0.45
    val BOARD_EYE: Double = PLATFORM_X - BEHIND
    val EYE_WEST: Double = HALL_WEST + 0.5
    val EYE_EAST: Double = MOUTH.x - 0.3
    private const val EASE_EAST = 2.0

    /** How long the camera rides out in the tunnel, east of the hall, before a ride from under downtown cuts to the town. */
    const val TUNNEL_MILLIS = 900.0

    /** How high the camera is and how far off the line, and where it looks, in the hall and out in the tunnel. */
    private const val HALL_EYE_Y = FLOOR + 2.2
    private const val HALL_EYE_Z = 3.4
    private const val HALL_LOOK_Y = FLOOR + 1.0
    private const val HALL_LOOK_Z = 1.6
    private const val TUNNEL_EYE_Y = CAR_TOP + 0.55
    private const val TUNNEL_EYE_Z = 0.3
    private const val TUNNEL_LOOK_Y = DECK + 1.6

    /** True when [going] is a SkyTrain ride from the station under downtown. */
    fun startsUnder(going: Journey): Boolean = going.transport == Transport.SKYTRAIN && going.railFrom < UNDER

    /** True when [going] is a SkyTrain ride to the station under downtown. */
    fun endsUnder(going: Journey): Boolean = going.transport == Transport.SKYTRAIN && going.railTo < UNDER

    /** True while [going], [elapsed] milliseconds in, is under downtown, and shown there rather than in the town. */
    fun isUnder(going: Journey, elapsed: Double): Boolean = when {
        going.transport != Transport.SKYTRAIN -> false
        // Going out from under downtown, the camera decides when the town comes back.
        startsUnder(going) -> elapsed < cutMillis(going)
        else -> going.alongAt(elapsed) < UNDER
    }

    /**
     * When a ride from under downtown cuts back to the town: once the camera behind the train has ridden out in the
     * tunnel, east of the hall, for [TUNNEL_MILLIS], or as it comes to the mouth, whichever is first. Meanwhile the train
     * runs on out through the mouth into the open. Worked out once for each ride.
     */
    fun cutMillis(going: Journey): Double {
        if (going === cutFor) return cut
        val inTunnel = millisAt(going, HALL_EAST + BEHIND - MOUTH.x)
        val atMouth = millisAt(going, EYE_EAST + BEHIND - MOUTH.x)
        cut = min(inTunnel + TUNNEL_MILLIS, atMouth)
        cutFor = going
        return cut
    }

    /** The ride [cut] was last worked out for. */
    private var cutFor: Journey? = null
    private var cut = 0.0

    /** When the train of [going], a ride east, first has its middle [along] the line; when it stops, if it never does. */
    private fun millisAt(going: Journey, along: Double): Double {
        var low = going.boardMillis
        var high = going.millis
        if (going.alongAt(high) < along) return high
        repeat(SEARCH_STEPS) {
            val middle = (low + high) / 2
            if (going.alongAt(middle) < along) low = middle else high = middle
        }
        return high
    }

    private const val SEARCH_STEPS = 40

    /**
     * How far along the line the train's middle is: as [Journey.alongAt] says, but while the character waits to get on
     * at the platform, the train is still coming in, slowing to a stop there.
     */
    fun trainAlong(going: Journey, elapsed: Double): Double {
        val along = going.alongAt(elapsed)
        if (!startsUnder(going) || elapsed >= ARRIVE_MILLIS) return along
        val left = 1 - elapsed.coerceAtLeast(0.0) / ARRIVE_MILLIS
        return along - APPROACH * left * left
    }

    /** How fast the train goes then, in units a second, coming in to the platform as well. */
    fun trainSpeed(going: Journey, elapsed: Double): Double {
        if (!startsUnder(going) || elapsed >= ARRIVE_MILLIS || elapsed < 0) return going.speedAt(elapsed)
        return 2 * APPROACH * (1 - elapsed / ARRIVE_MILLIS) / ARRIVE_MILLIS * 1_000
    }

    /** How far open the train's doors are at the platform, from 0.0 shut to 1.0 open. */
    fun doorsOpen(going: Journey, elapsed: Double): Double {
        if (startsUnder(going) && elapsed < going.boardMillis) {
            return (min(elapsed - ARRIVE_MILLIS, going.boardMillis - elapsed) / DOORS_MILLIS).coerceIn(0.0, 1.0)
        }
        val off = elapsed - (going.millis - going.alightMillis)
        if (!endsUnder(going) || off < 0) return 0.0
        return (min(off, DOORS_SHUT - off) / DOORS_MILLIS).coerceIn(0.0, 1.0)
    }

    /**
     * The character on the platform, or null while it is in the train: waiting by the doors of the middle car and
     * stepping in; or stepping out and walking off toward the stairs at the east end.
     */
    fun rider(going: Journey, elapsed: Double): Pose? {
        val door = PLATFORM_X
        if (startsUnder(going) && elapsed < going.boardMillis) {
            val stepped = (elapsed - ARRIVE_MILLIS - DOORS_MILLIS) / STEP_MILLIS
            return if (stepped >= 1.0) null else across(door, WAIT, DOORWAY, stepped, PI)
        }
        val off = elapsed - (going.millis - going.alightMillis)
        if (!endsUnder(going) || off < DOORS_MILLIS || elapsed >= going.millis) return null
        val stepped = (off - DOORS_MILLIS) / STEP_MILLIS
        if (stepped < 1.0) return across(door, DOORWAY, WAIT, stepped, 0.0)
        val gone = WALK_OFF * (off - DOORS_MILLIS - STEP_MILLIS) / 1_000
        return Pose(Spot(door + gone, LINE_Z + WAIT), PI / 2, FLOOR + lift(gone), swing(gone))
    }

    /** Walking [share] of the way across the platform at [x], from [from] off the line to [to], facing [heading]. */
    private fun across(x: Double, from: Double, to: Double, share: Double, heading: Double): Pose {
        val done = share.coerceIn(0.0, 1.0)
        val gone = abs(to - from) * done
        return Pose(Spot(x, LINE_Z + from + (to - from) * done), heading, FLOOR + lift(gone), swing(gone))
    }

    private fun swing(gone: Double): Double = sin(gone / Trip.STEP_LENGTH * PI) * Trip.SWING

    private fun lift(gone: Double): Double = abs(sin(gone / Trip.STEP_LENGTH * PI)) * Trip.STEP_HEIGHT

    /**
     * Where the camera is and where it looks, into [into] as the eye's x, y, z and then the point's: behind the train,
     * looking the way it goes. Getting on, it starts behind where the train will stand and drifts slowly east until the
     * train leaving pulls it along; stepping out, it rides in from the mouth (easing away from it, never standing there)
     * and, once the train stands, drifts slowly on along the platform. Over the platform it is higher and off to the
     * side; out in the tunnel, low over the train's roof. With [calm], as on a phone that asks for less motion, it does
     * not drift. It makes nothing new.
     */
    fun camera(going: Journey, elapsed: Double, calm: Boolean, into: DoubleArray) {
        val east = going.railTo >= going.railFrom
        val middle = xAt(trainAlong(going, elapsed))
        val drift = if (calm) 0.0 else DRIFT
        val x = if (east) {
            val start = if (startsUnder(going)) BOARD_EYE + drift * elapsed.coerceAtLeast(0.0) / 1_000 else EYE_WEST
            max(start, middle - BEHIND)
        } else {
            val standing = elapsed - (going.millis - going.alightMillis)
            val on = if (endsUnder(going)) drift * standing.coerceAtLeast(0.0) / 1_000 else 0.0
            // A soft minimum: it keeps moving as the train does, however near the mouth.
            val behind = middle + BEHIND
            EYE_EAST - EASE_EAST * ln(1 + exp((EYE_EAST - behind) / EASE_EAST)) - on
        }
        val eyeX = x.coerceIn(EYE_WEST, EYE_EAST)
        // 0.0 in the hall, 1.0 out in the tunnel, eased across the hall's east end.
        val out = ((eyeX - (HALL_EAST - 2.0)) / 4.0).coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
        into[0] = eyeX
        into[1] = HALL_EYE_Y + (TUNNEL_EYE_Y - HALL_EYE_Y) * out
        into[2] = LINE_Z + HALL_EYE_Z + (TUNNEL_EYE_Z - HALL_EYE_Z) * out
        into[3] = eyeX + if (east) LOOK else -LOOK
        into[4] = HALL_LOOK_Y + (TUNNEL_LOOK_Y - HALL_LOOK_Y) * out
        into[5] = LINE_Z + HALL_LOOK_Z * (1 - out)
    }
}

/**
 * The platform of 다운타운역 and its tunnel, drawn in a scene of their own while a ride is under downtown, in place of
 * the town: made once from the town's cube and shades, in eight draws, with no shadows. A dim light of its own and a dark
 * haze; the hall's ceiling lights, the tunnel's lamps and the train's windows glow, and the mouth is the sky's colour.
 * [place] puts the train, its doors, the character and the camera where [UndergroundRide] says; with less motion the
 * tunnel's lamps are not drawn, so nothing streams past.
 */
internal class UndergroundScene(private val cube: BoxGeometry, shades: DataTexture) {
    val scene = Scene()
    private val material = MeshToonMaterial(json("gradientMap" to shades))
    private val glow = MeshBasicMaterial(json("color" to 0xFFFFFF))
    private val daylight = MeshBasicMaterial(json("color" to 0xFFFFFF, "fog" to false))
    private val signPlane = PlaneGeometry(SIGN_WIDTH, SIGN_HEIGHT)
    /** The sign's picture, null where the browser has no 2D canvas. */
    internal val signPicture = signPicture()
    private val signMaterial = MeshBasicMaterial(json("map" to signPicture, "color" to 0xFFFFFF))
    private val meshes = mutableListOf<InstancedMesh>()
    private val lamps: InstancedMesh
    private val portal: InstancedMesh
    private val train: InstancedMesh
    private val trainLights: InstancedMesh
    private val rider: InstancedMesh
    private val riderBoxes = FigureBoxes(Figures.player)
    private val aim = DoubleArray(6)
    private var sky = -1

    /** Where the camera's eye was last put, for the train's rumble; and how far open the doors were drawn. */
    var eye = Point(0.0, 0.0, 0.0)
        private set
    var doorsDrawn = 0.0
        private set

    /** True when the character was last drawn on the platform. */
    var isRiderShown = false
        private set

    /** True while the tunnel's lamps are drawn: not with less motion. */
    val areLampsShown: Boolean
        get() = lamps.visible

    init {
        scene.background = Color(DARK)
        scene.fog = Fog(DARK, HAZE_NEAR, HAZE_FAR)
        scene.add(HemisphereLight(0xEEF0F2, 0x8C867C, 1.5))
        scene.add(DirectionalLight(0xFFF1DC, 0.8).also { it.position.set(0.6, 1.0, 0.8) })
        add(still(), material)
        add(hallLights(), glow)
        lamps = add(tunnelLamps(), glow)
        portal = add(listOf(box(MOUTH_X, MOUTH_X + 0.3, DECK, TUNNEL_TOP, -TUNNEL_HALF, TUNNEL_HALF, 0xFFFFFF)), daylight)
        train = add(List(CARS * CAR_BOXES.size) { CAR_BOXES[it % CAR_BOXES.size] }, material, moving = true)
        trainLights = add(List(CARS * CAR_LIGHTS.size) { CAR_LIGHTS[it % CAR_LIGHTS.size] } + END_LIGHTS, glow, moving = true)
        rider = add(Figures.player.map { it.block }, material, moving = true)
        addSign()
    }

    /**
     * Puts everything where [going] has it [elapsed] milliseconds in, and the [camera] with it; the mouth shows [skyHex],
     * the town's sky as it is now. [calm] leaves the lamps out and keeps the camera from drifting. It makes nothing new
     * but the eye's point.
     */
    fun place(going: Journey, elapsed: Double, calm: Boolean, skyHex: Int, camera: PerspectiveCamera) {
        if (skyHex != sky) {
            sky = skyHex
            daylight.color.setHex(skyHex)
        }
        lamps.visible = !calm
        val middle = UndergroundRide.xAt(UndergroundRide.trainAlong(going, elapsed))
        doorsDrawn = UndergroundRide.doorsOpen(going, elapsed)
        writeTrain(middle, doorsDrawn)
        val pose = UndergroundRide.rider(going, elapsed)
        val array = rider.instanceMatrix.array
        if (pose != null) {
            riderBoxes.write(pose, array, 0)
        } else {
            // Shrunk to nothing while it is in the train.
            for (index in 0 until array.length) array[index] = if (index % 16 == 15) 1f else 0f
        }
        rider.instanceMatrix.needsUpdate = true
        isRiderShown = pose != null
        UndergroundRide.camera(going, elapsed, calm, aim)
        camera.position.set(aim[0], aim[1], aim[2])
        camera.lookAt(aim[3], aim[4], aim[5])
        eye = Point(aim[0], aim[1], aim[2])
    }

    /** The train with its middle at [middle] along x, its doors [open] that far: each car's boxes, then its lights. */
    private fun writeTrain(middle: Double, open: Double) {
        val boxes = train.instanceMatrix.array
        val lights = trainLights.instanceMatrix.array
        for (car in 0 until CARS) {
            val carX = middle - TRAIN_HALF + CAR_LENGTH / 2 + car * (CAR_LENGTH + CAR_GAP)
            for ((index, box) in CAR_BOXES.withIndex()) {
                // The two doors slide apart, one each way.
                val slide = when (index) {
                    LEFT_DOOR -> -DOOR_SLIDE * open
                    RIGHT_DOOR -> DOOR_SLIDE * open
                    else -> 0.0
                }
                put(boxes, car * CAR_BOXES.size + index, box, carX + slide)
            }
            for ((index, box) in CAR_LIGHTS.withIndex()) put(lights, car * CAR_LIGHTS.size + index, box, carX)
        }
        for ((index, box) in END_LIGHTS.withIndex()) put(lights, CARS * CAR_LIGHTS.size + index, box, middle)
        train.instanceMatrix.needsUpdate = true
        trainLights.instanceMatrix.needsUpdate = true
    }

    /** Writes [box], measured from a point on the line, with that point at [x], into slot [index] of [into]. */
    private fun put(into: Float32Array, index: Int, box: Block, x: Double) {
        val at = index * 16
        for (offset in 0 until 16) into[at + offset] = 0f
        into[at] = box.width.toFloat()
        into[at + 5] = box.height.toFloat()
        into[at + 10] = box.depth.toFloat()
        into[at + 12] = (x + box.x + box.width / 2).toFloat()
        into[at + 13] = (box.y + box.height / 2).toFloat()
        into[at + 14] = (LINE_Z + box.z + box.depth / 2).toFloat()
        into[at + 15] = 1f
    }

    /** One draw of [blocks] in [material], placed once, or every frame when [moving]; none casts a shadow. */
    private fun add(blocks: List<Block>, material: Material, moving: Boolean = false): InstancedMesh {
        val mesh = InstancedMesh(cube, material, blocks.size)
        val placer = Object3D()
        val colour = Color(0)
        for ((index, block) in blocks.withIndex()) {
            placer.position.set(block.x + block.width / 2, block.y + block.height / 2, block.z + block.depth / 2)
            placer.scale.set(block.width, block.height, block.depth)
            placer.updateMatrix()
            mesh.setMatrixAt(index, placer.matrix)
            mesh.setColorAt(index, colour.setHex(block.colour))
        }
        if (moving) {
            mesh.frustumCulled = false
            mesh.instanceMatrix.setUsage(DynamicDrawUsage)
        }
        mesh.castShadow = false
        mesh.receiveShadow = false
        meshes.add(mesh)
        scene.add(mesh)
        return mesh
    }

    /** The sign with the station's name, hung over the platform: one face toward each end of the hall. */
    private fun addSign() {
        val faces = InstancedMesh(signPlane, signMaterial, 2)
        val placer = Object3D()
        for ((index, side) in listOf(-1.0, 1.0).withIndex()) {
            placer.position.set(SIGN_X + side * 0.04, SIGN_Y, LINE_Z + SIGN_Z)
            // A plane faces +z; turned a quarter round, it faces down the hall one way or the other.
            placer.rotation.y = side * PI / 2
            placer.updateMatrix()
            faces.setMatrixAt(index, placer.matrix)
        }
        faces.castShadow = false
        meshes.add(faces)
        scene.add(faces)
    }

    fun dispose() {
        for (mesh in meshes) mesh.dispose()
        material.dispose()
        glow.dispose()
        daylight.dispose()
        signMaterial.dispose()
        signPlane.dispose()
        signPicture?.dispose()
    }

    private companion object {
        val MOUTH_X = UndergroundRide.MOUTH.x
        val LINE_Z = UndergroundRide.LINE_Z
        const val DECK = UndergroundRide.DECK
        const val FLOOR = UndergroundRide.FLOOR
        const val TUNNEL_HALF = UndergroundRide.TUNNEL_HALF
        const val TUNNEL_TOP = UndergroundRide.TUNNEL_TOP
        const val HALL_TOP = UndergroundRide.HALL_TOP
        const val HALL_SOUTH = UndergroundRide.HALL_SOUTH
        const val EDGE = UndergroundRide.EDGE
        const val CARS = UndergroundRide.CARS
        const val CAR_LENGTH = UndergroundRide.CAR_LENGTH
        const val CAR_GAP = UndergroundRide.CAR_GAP
        const val TRAIN_HALF = UndergroundRide.TRAIN_HALF
        const val CAR_SIDE = UndergroundRide.CAR_SIDE
        val HALL_WEST = UndergroundRide.HALL_WEST
        val HALL_EAST = UndergroundRide.HALL_EAST
        val PLATFORM_X = UndergroundRide.PLATFORM_X
        val TAIL = UndergroundRide.TAIL

        /** The dark of the tunnel, behind everything and in the haze, which closes in from near to far. */
        const val DARK = 0x0D1014
        const val HAZE_NEAR = 7.0
        const val HAZE_FAR = 40.0

        /** How far each door slides open, and which of a car's boxes the two doors are. */
        const val DOOR_SLIDE = 0.55
        const val LEFT_DOOR = 4
        const val RIGHT_DOOR = 5

        /** The station's sign: its size, and where it hangs over the platform. */
        const val SIGN_WIDTH = 2.2
        const val SIGN_HEIGHT = 0.55
        val SIGN_X = PLATFORM_X + 2.5
        const val SIGN_Y = FLOOR + 3.0
        const val SIGN_Z = 1.7

        /**
         * A box by its west, east, bottom, top, north and south sides; z is measured from the line, as the tunnel's
         * boxes are, and x along it (from a car's middle for a car's boxes).
         */
        fun box(x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double, colour: Int, onLine: Boolean = true) =
            Block(x0, y0, z0 + if (onLine) LINE_Z else 0.0, x1 - x0, y1 - y0, z1 - z0, colour)

        /**
         * One car under the ground, from its middle: the body, its blue band, the roof, the dark doorway on the platform
         * side, its two doors, and two bogies. The doors are [LEFT_DOOR] and [RIGHT_DOOR].
         */
        val CAR_BOXES: List<Block> = listOf(
            box(-1.8, 1.8, DECK + 0.35, DECK + 2.6, -CAR_SIDE, CAR_SIDE, TRAIN, onLine = false),
            box(-1.81, 1.81, DECK + 0.95, DECK + 1.15, -CAR_SIDE - 0.02, CAR_SIDE + 0.02, BLUE, onLine = false),
            box(-1.75, 1.75, DECK + 2.6, DECK + 2.75, -CAR_SIDE + 0.05, CAR_SIDE - 0.05, ROOF, onLine = false),
            box(-0.62, 0.62, FLOOR, DECK + 2.45, CAR_SIDE, CAR_SIDE + 0.02, DOORWAY, onLine = false),
            box(-0.6, 0.0, FLOOR, DECK + 2.45, CAR_SIDE + 0.02, CAR_SIDE + 0.05, DOOR, onLine = false),
            box(0.0, 0.6, FLOOR, DECK + 2.45, CAR_SIDE + 0.02, CAR_SIDE + 0.05, DOOR, onLine = false),
            box(-1.4, -0.7, DECK + 0.2, DECK + 0.4, -0.6, 0.6, BOGIE, onLine = false),
            box(0.7, 1.4, DECK + 0.2, DECK + 0.4, -0.6, 0.6, BOGIE, onLine = false),
        )

        /** A car's lit windows, either side of its doors, through the car so they show from both sides. */
        val CAR_LIGHTS: List<Block> = listOf(
            box(-1.6, -0.8, DECK + 1.4, DECK + 2.1, -CAR_SIDE - 0.01, CAR_SIDE + 0.01, WINDOWS, onLine = false),
            box(0.8, 1.6, DECK + 1.4, DECK + 2.1, -CAR_SIDE - 0.01, CAR_SIDE + 0.01, WINDOWS, onLine = false),
        )

        /** The lamps at the train's two ends, from its middle. */
        val END_LIGHTS: List<Block> = listOf(-1.0, 1.0).flatMap { end ->
            val face = end * (TRAIN_HALF + 0.005)
            listOf(-0.6, 0.6).map { side ->
                box(face - 0.02, face + 0.02, DECK + 0.75, DECK + 0.95, side - 0.12, side + 0.12, HEADLIGHT, onLine = false)
            }
        }

        /**
         * The boxes that stand still: the track bed and rails from the tunnel's far end to its mouth, the tunnel's walls
         * and roof either side of the hall (lighter near the mouth, where the daylight reaches), and the hall: its walls
         * with a blue band, ceiling, platform with its yellow edge, end walls, pillars, benches, posters on the far wall,
         * and the sign's frame on its two rods.
         */
        fun still(): List<Block> = buildList {
            add(box(TAIL, MOUTH_X, DECK, DECK + 0.3, -TUNNEL_HALF, TUNNEL_HALF, BED))
            for (side in listOf(-1.0, 1.0)) add(box(TAIL, MOUTH_X, DECK + 0.3, DECK + 0.36, side * 0.6 - 0.04, side * 0.6 + 0.04, RAIL))
            for ((west, east) in listOf(TAIL to HALL_WEST, HALL_EAST to MOUTH_X - LIT_REACH, MOUTH_X - LIT_REACH to MOUTH_X)) {
                val colour = if (east == MOUTH_X) WALL_LIT else WALL
                add(box(west, east, DECK, TUNNEL_TOP, -TUNNEL_HALF - 0.3, -TUNNEL_HALF, colour))
                add(box(west, east, DECK, TUNNEL_TOP, TUNNEL_HALF, TUNNEL_HALF + 0.3, colour))
                add(box(west, east, TUNNEL_TOP, TUNNEL_TOP + 0.3, -TUNNEL_HALF - 0.3, TUNNEL_HALF + 0.3, colour))
            }
            add(box(TAIL - 0.3, TAIL, DECK, TUNNEL_TOP, -TUNNEL_HALF, TUNNEL_HALF, WALL))
            val west = HALL_WEST
            val east = HALL_EAST
            add(box(west, east, DECK, HALL_TOP, -TUNNEL_HALF - 0.3, -TUNNEL_HALF, TILE))
            add(box(west, east, DECK + 2.95, DECK + 3.25, -TUNNEL_HALF, -TUNNEL_HALF + 0.02, BLUE))
            add(box(west, east, DECK, HALL_TOP, HALL_SOUTH, HALL_SOUTH + 0.3, TILE))
            add(box(west, east, FLOOR + 1.7, FLOOR + 2.0, HALL_SOUTH - 0.02, HALL_SOUTH, BLUE))
            add(box(west, east, HALL_TOP, HALL_TOP + 0.3, -TUNNEL_HALF - 0.3, HALL_SOUTH + 0.3, CEILING))
            add(box(west, east, DECK, FLOOR, EDGE, HALL_SOUTH, PLATFORM))
            add(box(west, east, FLOOR, FLOOR + 0.02, EDGE, EDGE + 0.25, EDGE_LINE))
            for ((x0, x1) in listOf(west - 0.3 to west, east to east + 0.3)) {
                add(box(x0, x1, DECK, HALL_TOP, TUNNEL_HALF, HALL_SOUTH + 0.3, TILE))
                add(box(x0, x1, TUNNEL_TOP, HALL_TOP, -TUNNEL_HALF - 0.3, TUNNEL_HALF, TILE))
            }
            for (x in listOf(PLATFORM_X - 6.5, PLATFORM_X, PLATFORM_X + 6.5)) {
                add(box(x - 0.2, x + 0.2, FLOOR, HALL_TOP, HALL_SOUTH - 0.9, HALL_SOUTH - 0.5, PILLAR))
            }
            for (x in listOf(PLATFORM_X - 3.2, PLATFORM_X + 3.2)) {
                add(box(x - 0.8, x + 0.8, FLOOR, FLOOR + 0.45, HALL_SOUTH - 0.6, HALL_SOUTH - 0.1, BENCH))
            }
            for ((index, x) in listOf(PLATFORM_X - 6.0, PLATFORM_X - 1.2, PLATFORM_X + 3.6).withIndex()) {
                add(box(x, x + 2.2, DECK + 1.2, DECK + 2.6, -TUNNEL_HALF, -TUNNEL_HALF + 0.03, POSTERS[index]))
            }
            add(box(SIGN_X - 0.03, SIGN_X + 0.03, SIGN_Y - 0.33, SIGN_Y + 0.33, SIGN_Z - 1.16, SIGN_Z + 1.16, FRAME))
            for (side in listOf(-1.0, 1.0)) {
                val z = SIGN_Z + side * 1.0
                add(box(SIGN_X - 0.02, SIGN_X + 0.02, SIGN_Y + 0.33, HALL_TOP, z - 0.02, z + 0.02, FRAME))
            }
        }

        /** The hall's two rows of ceiling lights, and a green exit sign over each end. */
        fun hallLights(): List<Block> = buildList {
            var x = HALL_WEST + 1.0
            while (x + 1.6 <= HALL_EAST - 1.0) {
                for (z in listOf(-0.2, 3.0)) add(box(x, x + 1.6, HALL_TOP - 0.06, HALL_TOP, z - 0.15, z + 0.15, LIGHT))
                x += 2.4
            }
            add(box(HALL_WEST, HALL_WEST + 0.04, FLOOR + 2.4, FLOOR + 2.75, 2.6, 3.6, EXIT))
            add(box(HALL_EAST - 0.04, HALL_EAST, FLOOR + 2.4, FLOOR + 2.75, 2.6, 3.6, EXIT))
        }

        /** The tunnel's lamps on both walls, every [LAMP_GAP], either side of the hall. */
        fun tunnelLamps(): List<Block> = buildList {
            for ((west, east) in listOf(TAIL to HALL_WEST, HALL_EAST to MOUTH_X)) {
                var x = west + 1.0
                while (x + 0.7 <= east - 0.5) {
                    add(box(x, x + 0.7, DECK + 2.85, DECK + 3.0, -TUNNEL_HALF, -TUNNEL_HALF + 0.06, LAMP))
                    add(box(x, x + 0.7, DECK + 2.85, DECK + 3.0, TUNNEL_HALF - 0.06, TUNNEL_HALF, LAMP))
                    x += LAMP_GAP
                }
            }
        }

        /**
         * The sign's picture: the station's made-up name in white on the line's blue. Null where the browser has no 2D
         * canvas; the sign is then a plain board.
         */
        fun signPicture(): CanvasTexture? {
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.width = 512
            canvas.height = 128
            val pen = canvas.getContext("2d") as? CanvasRenderingContext2D ?: return null
            pen.fillStyle = "#2f5596"
            pen.fillRect(0.0, 0.0, 512.0, 128.0)
            pen.fillStyle = "#f2c94c"
            pen.fillRect(0.0, 112.0, 512.0, 16.0)
            pen.fillStyle = "#ffffff"
            pen.font = "bold 64px sans-serif"
            pen.textAlign = CanvasTextAlign.CENTER
            pen.textBaseline = CanvasTextBaseline.MIDDLE
            pen.fillText(UndergroundRide.NAME, 256.0, 58.0)
            return CanvasTexture(canvas).also {
                it.colorSpace = SRGB
                it.needsUpdate = true
            }
        }

        /** How the sign's picture's colours are written: as a screen shows them, as the boxes' colours are given. */
        const val SRGB = "srgb"

        /** How far the daylight reaches in from the mouth, lighting the walls. */
        const val LIT_REACH = 3.0
        const val LAMP_GAP = 3.0

        const val BED = 0x3B3835
        const val RAIL = 0x8E959C
        const val WALL = 0x55585C
        const val WALL_LIT = 0x8A8D8E
        const val TILE = 0xE6E0D2
        const val CEILING = 0xC9C5BB
        const val PLATFORM = 0xA8A39A
        const val EDGE_LINE = 0xF2C94C
        const val PILLAR = 0xD8D2C4
        const val BENCH = 0x8B6A4B
        const val FRAME = 0x2A2E33
        const val BLUE = 0x3C6CB4
        val POSTERS = listOf(0xD9673F, 0x5BA37C, 0xE0B24A)
        const val TRAIN = 0xF2F2EE
        const val ROOF = 0xC9CCCF
        const val DOORWAY = 0x2B3138
        const val DOOR = 0xDADDDF
        const val BOGIE = 0x2B2B2B
        const val WINDOWS = 0xFFE7B0
        const val HEADLIGHT = 0xFFFFFF
        const val LIGHT = 0xFFF6DE
        const val EXIT = 0x3DBB6E
        const val LAMP = 0xFFD27A
    }
}
