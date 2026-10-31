package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import vancouvermonth.text.Weather

// The town's small motions, each worked out from the town's own time in milliseconds and nothing else, so a test can
// ask where anything is at any moment without drawing.

/**
 * The SkyTrain's round, every [PERIOD]: it stands at the station for [DWELL], pulls out east faster and faster until
 * it is far down the guideway, is away for a while, then comes in from the west, out of its tunnel, slowing all the
 * way into the station. Far down the line it fades into the haze, so it never pops out of sight or into it. How far it
 * has gone is counted along [Neighbourhood.guideway], which bends.
 */
object SkyTrainRun {
    const val PERIOD = 32_000.0
    const val DWELL = 8_000.0
    const val LEAVE = 10_000.0
    const val AWAY = 4_000.0
    const val ARRIVE = PERIOD - DWELL - LEAVE - AWAY

    /** How far down the guideway the train goes before it is away, either way from the station. */
    const val REACH = 155.0

    /** How far down the guideway the train starts to fade; it is gone by [REACH]. */
    const val FADE_FROM = 90.0

    /** How far east of its stop at the station the train is at [millis]; west is below 0. */
    fun shiftAt(millis: Double): Double {
        val t = ((millis % PERIOD) + PERIOD) % PERIOD
        return when {
            t < DWELL -> 0.0
            t < DWELL + LEAVE -> REACH * square((t - DWELL) / LEAVE)
            t < DWELL + LEAVE + AWAY -> REACH
            else -> -REACH * square(1 - (t - DWELL - LEAVE - AWAY) / ARRIVE)
        }
    }

    /** How much of the train shows at [millis]: all of it near the town, fading to none far down the line. */
    fun opacityAt(millis: Double): Double = ((REACH - abs(shiftAt(millis))) / (REACH - FADE_FROM)).coerceIn(0.0, 1.0)

    /** False while the train is away, faded out far down the guideway, and need not be drawn. */
    fun isShownAt(millis: Double): Boolean = opacityAt(millis) > 0.0

    /** How fast the train goes at [millis], in units a second, east or west: 0 while it stands or is away. */
    fun speedAt(millis: Double): Double {
        val t = ((millis % PERIOD) + PERIOD) % PERIOD
        return when {
            t < DWELL -> 0.0
            t < DWELL + LEAVE -> 2 * REACH * (t - DWELL) / LEAVE / LEAVE * 1_000
            t < DWELL + LEAVE + AWAY -> 0.0
            else -> 2 * REACH * (1 - (t - DWELL - LEAVE - AWAY) / ARRIVE) / ARRIVE * 1_000
        }
    }

    /** The train's speed as it goes farthest and fastest, at the end of pulling out and the start of coming in. */
    const val TOP_SPEED = 2 * REACH / LEAVE * 1_000

    /** How far [ear] is from the nearest part of the train at [millis], the train taken every half unit along it. */
    fun distanceTo(millis: Double, ear: Point): Double {
        val west = Neighbourhood.TRAIN_STOP + shiftAt(millis)
        val up = ear.y - (Neighbourhood.DECK_HEIGHT + 0.9)
        var nearest = Double.MAX_VALUE
        val steps = (Neighbourhood.TRAIN_LENGTH / 0.5).toInt()
        for (step in 0..steps) {
            val spot = Neighbourhood.guideway.spotAt(west + Neighbourhood.TRAIN_LENGTH * step / steps)
            val across = ear.x - spot.x
            val side = ear.z - spot.z
            nearest = minOf(nearest, sqrt(across * across + up * up + side * side))
        }
        return nearest
    }

    /** How loud a rumble [distance] away sounds: 1.0 right by it, half as loud [RUMBLE_NEAR] away, little far off. */
    fun loudnessAt(distance: Double): Double = 1 / (1 + square(distance / RUMBLE_NEAR))

    /**
     * How loud the train rumbles at [millis] to an ear at [ear], the camera, from 0.0 to 1.0: louder the nearer it is,
     * and once it is under way, as loud as its distance lets it be. It is silent while it stands at the station and
     * while it is away, and fades with it far down the line.
     */
    fun rumbleAt(millis: Double, ear: Point): Double {
        val underWay = (speedAt(millis) / (TOP_SPEED * UNDER_WAY)).coerceIn(0.0, 1.0)
        if (underWay == 0.0) return 0.0
        return opacityAt(millis) * underWay * loudnessAt(distanceTo(millis, ear))
    }

    /** Half as loud this far from the train. */
    const val RUMBLE_NEAR = 18.0

    /** At this share of its top speed the train rumbles all it can; slower, less, down to nothing standing. */
    const val UNDER_WAY = 0.35

    private fun square(value: Double) = value * value
}

/** The campfire's flicker: how bright it burns and how tall its flames stand, as a share of steady, 0.75 to 1.25. */
object Campfire {
    fun flicker(millis: Double): Double =
        1 + 0.12 * sin(millis / 97) + 0.07 * sin(millis / 41 + 1.3) + 0.04 * sin(millis / 23 + 0.4)

    /** How much of the fire's crackle reaches someone [distance] from it: all beside it, none [HEARD_WITHIN] off. */
    fun nearness(distance: Double): Double {
        val near = (1 - distance / HEARD_WITHIN).coerceIn(0.0, 1.0)
        return near * near
    }

    /**
     * How loud the fire crackles, from 0.0 to 1.0, for someone [near] it as [nearness] says, when [night] is how dark
     * it is.
     */
    fun crackle(near: Double, night: Double): Double = near.coerceIn(0.0, 1.0) * night.coerceIn(0.0, 1.0)

    const val HEARD_WITHIN = 10.0
}

/**
 * Light glinting on the water: short pale streaks on the sea off the seawall near the five places, each coming and
 * going on its own beat and drifting a little. They fade away in the night, when [night] is 1.0.
 */
object Shimmer {
    private class Glint(val x: Double, val z: Double, val y: Double, val width: Double, val beat: Double, val phase: Double)

    /** The water squares the glints may be on: off the shore, past the seawall, near the places' doors. */
    val seaSquares: List<Cell> = RealVancouver.map.let { map ->
        val doors = Neighbourhood.venues.map { it.door }
        fun around(cell: Cell, reach: Int, terrain: Terrain): List<Boolean> =
            (-reach..reach).flatMap { dx ->
                (-reach..reach).map { dz -> map.terrainAt(Cell(cell.x + dx, cell.z + dz)) == terrain }
            }
        map.cellsOf(Terrain.WATER).filter { cell ->
            doors.any { hypot(it.x - cell.x, it.z - cell.z) < NEAR_DOORS } &&
                around(cell, 1, Terrain.WATER).all { it } && around(cell, SHORE_REACH, Terrain.SHORE).any { it }
        }
    }

    private val glints: List<Glint> = Random(11).let { random ->
        List(SEA_GLINTS) {
            val square = seaSquares[random.nextInt(seaSquares.size)]
            Glint(
                x = square.x + random.nextDouble(),
                z = square.z + random.nextDouble(),
                y = SEA_TOP,
                width = 0.6 + random.nextDouble() * 0.8,
                beat = 1_800 + random.nextDouble() * 1_400,
                phase = random.nextDouble() * 2 * PI,
            )
        }
    }

    val count: Int
        get() = glints.size

    /** Every glint at [millis]: a thin box on the water, no wider than [MAX_WIDTH], and next to nothing when out. */
    fun at(millis: Double, night: Double): List<Block> = glints.map { glint ->
        val out = max(0.0, sin(2 * PI * millis / glint.beat + glint.phase)) * (1 - 0.9 * night.coerceIn(0.0, 1.0))
        val width = max(MIN_WIDTH, glint.width * out)
        val drift = 0.25 * sin(millis / 4_000 + glint.phase)
        Block(glint.x + drift - width / 2, glint.y, glint.z - 0.04, width, 0.012, 0.08, GLINT)
    }

    const val MAX_WIDTH = 1.4
    private const val MIN_WIDTH = 0.001
    private const val SEA_GLINTS = 32

    /** How near the shore and the places' doors the glints keep, so they are where the player looks. */
    private const val SHORE_REACH = 6
    private const val NEAR_DOORS = 40.0

    /** The sea's surface. */
    const val SEA_TOP = -0.39
    private const val GLINT = 0xF4FBFF
}

/** Petals drifting down from the blossom trees, a few from each, all the time, round and round. */
object Petals {
    private const val PER_TREE = 3
    private const val FALL_MILLIS = 5_200.0

    /** How high a petal starts: inside the blossom, so it seems to come out of it. */
    const val START_HEIGHT = 2.0

    val count: Int
        get() = Neighbourhood.blossoms.size * PER_TREE

    /** Every petal at [millis]: a small pink square, between the blossom and the ground, near its tree. */
    fun at(millis: Double): List<Block> = Neighbourhood.blossoms.withIndex().flatMap { (tree, spot) ->
        List(PER_TREE) { index ->
            val phase = tree * 1_700.0 + index * FALL_MILLIS / PER_TREE
            val fall = (((millis + phase) % FALL_MILLIS) + FALL_MILLIS) % FALL_MILLIS / FALL_MILLIS
            val angle = tree * 2.1 + index * 2 * PI / PER_TREE
            val flutter = sin(fall * 4 * PI + angle)
            val x = spot.x + cos(angle) * 0.7 + 0.35 * flutter + 0.6 * fall
            val z = spot.z + sin(angle) * 0.7 + 0.2 * cos(fall * 3 * PI + angle)
            val y = START_HEIGHT * (1 - fall) + 0.02
            Block(x - 0.04, y, z - 0.04, 0.08, 0.02, 0.08, if (index % 2 == 0) PETAL else PETAL_LIGHT)
        }
    }

    private const val PETAL = 0xF7B6C8
    private const val PETAL_LIGHT = 0xFCE1E8
}

/**
 * A few flat clouds high over the water, drifting east and coming round again from the west. They come round only
 * where the haze hides them from anywhere the camera can be: past the grid and the camera's farthest reach, beyond
 * [Neighbourhood.HAZE_FAR].
 */
object Clouds {
    private class Cloud(val x: Double, val y: Double, val z: Double, val size: Double)

    private val clouds = listOf(
        Cloud(-250.0, 32.0, -55.0, 10.0),
        Cloud(-160.0, 30.0, -45.0, 9.0),
        Cloud(-60.0, 34.0, -70.0, 12.0),
        Cloud(-5.0, 28.0, -38.0, 7.0),
        Cloud(60.0, 33.0, -62.0, 11.0),
        Cloud(150.0, 31.0, -50.0, 8.0),
        Cloud(240.0, 29.0, -66.0, 10.0),
    )

    /** How fast they drift, in units a millisecond. */
    const val DRIFT = 0.0006

    /** The largest cloud, and where they come round: the haze's depth, plus the camera's reach, plus that cloud. */
    const val LARGEST = 12.0
    const val WEST = -(FollowCamera.FAR + Neighbourhood.HAZE_FAR + LARGEST)
    const val EAST = Neighbourhood.COLUMNS + FollowCamera.FAR + Neighbourhood.HAZE_FAR + LARGEST

    val count: Int
        get() = clouds.size * 3

    /** Every box of every cloud at [millis]: a wide body, a smaller top and a puff at one end. */
    fun at(millis: Double): List<Block> = clouds.flatMap { cloud ->
        val span = EAST - WEST
        val x = WEST + (((cloud.x - WEST + millis * DRIFT) % span) + span) % span
        val size = cloud.size
        listOf(
            Block(x - size / 2, cloud.y, cloud.z - size * 0.22, size, 1.4, size * 0.44, CLOUD),
            Block(x - size * 0.3, cloud.y + 1.2, cloud.z - size * 0.15, size * 0.55, 1.1, size * 0.3, CLOUD),
            Block(x + size * 0.15, cloud.y + 0.4, cloud.z - size * 0.18, size * 0.45, 1.2, size * 0.36, CLOUD),
        )
    }

    private const val CLOUD = 0xFFFFFF

    /** The clouds' colour under [weather]'s sky: white on a sunny day, grey on a cloudy one, darker in the rain. */
    fun colour(weather: Weather): Int = when (weather) {
        Weather.SUNNY -> CLOUD
        Weather.CLOUDY -> 0xDCE1E6
        Weather.RAINY -> 0xAEB6BF
    }
}

/**
 * Rain on a rainy day: thin streaks falling in a box of sky around [centre][write], a light wind from the west
 * slanting them, and small splashes flashing on the ground. Each streak keeps its own place in the town, so the rain
 * stays put as the camera moves; one that leaves the box on one side comes back on the other, so the box is always
 * full and [DROPS] streaks are enough. Worked out from the town's time alone, like the rest of its life. Each streak
 * and splash is drawn from a flat 1 by 1 square: a streak stands up turned to the camera, a splash lies on the ground.
 */
object Rain {
    const val DROPS = 420
    const val SPLASHES = 48

    /** How wide and deep the box of sky is, and how high it reaches from the ground. */
    const val SPAN = 40.0
    const val TOP = 14.0

    /** A streak's length and thickness. */
    const val STREAK = 0.8
    const val THIN = 0.03

    /** How far east a streak drifts for each unit it falls. */
    const val SLANT = 0.15

    /** How fast the rain falls, in units a millisecond, give or take a seventh. */
    const val FALL = 0.012

    /** How long one splash lasts, out of its round of [SPLASH_MILLIS], and how wide it is when it starts. */
    const val SPLASH_MILLIS = 650.0
    const val SPLASH_SHOWS = 0.18
    const val SPLASH_SIZE = 0.22

    /** How high a splash sits: just over the street and the pavements, or just over the sea. */
    const val SPLASH_HEIGHT = 0.09
    private const val SEA_SPLASH_HEIGHT = Shimmer.SEA_TOP + 0.02

    /** Each streak's place in the town, before the box is moved round it, its speed and where it starts its fall. */
    private val dropX = DoubleArray(DROPS)
    private val dropZ = DoubleArray(DROPS)
    private val dropSpeed = DoubleArray(DROPS)
    private val dropPhase = DoubleArray(DROPS)

    init {
        val random = Random(17)
        for (index in 0 until DROPS) {
            dropX[index] = random.nextDouble() * SPAN
            dropZ[index] = random.nextDouble() * SPAN
            dropSpeed[index] = FALL * (0.86 + 0.28 * random.nextDouble())
            dropPhase[index] = random.nextDouble()
        }
    }

    /** How far through its fall streak [index] is at [millis]: 0.0 at the top of the box, nearly 1.0 at the ground. */
    private fun fallen(index: Int, millis: Double): Double {
        val fall = millis * dropSpeed[index] / TOP + dropPhase[index]
        return fall - floor(fall)
    }

    /** [value] brought into the box of [SPAN] that starts at [from], round and round. */
    private fun wrap(value: Double, from: Double): Double {
        val inside = (value - from) % SPAN
        return from + if (inside < 0) inside + SPAN else inside
    }

    /** The top of streak [index] at [millis], with the box of sky centred on [centreX], [centreZ]. */
    fun dropAt(index: Int, millis: Double, centreX: Double, centreZ: Double): Point {
        val fall = fallen(index, millis)
        return Point(
            wrap(dropX[index] + SLANT * TOP * fall, centreX - SPAN / 2),
            TOP * (1 - fall),
            wrap(dropZ[index], centreZ - SPAN / 2),
        )
    }

    /** Where splash [index] is at [millis] around [centreX], [centreZ], and how wide: next to nothing in between. */
    fun splashAt(index: Int, millis: Double, centreX: Double, centreZ: Double): Block {
        val beat = millis / SPLASH_MILLIS + index * 0.37
        val round = floor(beat)
        val through = beat - round
        // A new spot every round, from the round and the splash's number alone.
        val x = wrap(SPAN * scatter(index * 7.13 + round * 1.71), centreX - SPAN / 2)
        val z = wrap(SPAN * scatter(index * 3.31 + round * 2.93 + 0.5), centreZ - SPAN / 2)
        val size = if (through < SPLASH_SHOWS) SPLASH_SIZE * (0.4 + through / SPLASH_SHOWS) else MIN_SIZE
        val y = if (isSea(x, z)) SEA_SPLASH_HEIGHT else SPLASH_HEIGHT
        return Block(x - size / 2, y, z - size / 2, size, 0.01, size, 0)
    }

    /**
     * Writes every streak and then every splash at [millis] into [into], sixteen numbers each, column by column as
     * Three.js keeps a matrix, with the box of sky centred on [centreX], [centreZ]. Each streak's square is turned by
     * [turn] about the vertical, the camera's own turn, so it faces the camera. A streak within [CLEAR] of the camera
     * at [eye] is left out (shrunk to nothing): so close, it would be a pale bar across the screen, and costly to draw.
     * It makes nothing new, since it runs on every beat of the town's life.
     */
    fun write(
        millis: Double,
        centreX: Double,
        centreZ: Double,
        into: Float32Array,
        eye: Point? = null,
        turn: Double = 0.0,
    ) {
        val west = centreX - SPAN / 2
        val north = centreZ - SPAN / 2
        val across = cos(turn)
        val toward = sin(turn)
        for (index in 0 until DROPS) {
            val fall = fallen(index, millis)
            val at = index * 16
            val x = wrap(dropX[index] + SLANT * TOP * fall, west) + SLANT * STREAK / 2
            val y = TOP * (1 - fall) - STREAK / 2
            val z = wrap(dropZ[index], north)
            if (eye != null && isNear(x - eye.x, y - eye.y, z - eye.z)) {
                for (offset in 0 until 15) into[at + offset] = 0f
                into[at + 15] = 1f
                continue
            }
            // Thin across the camera's view, and long, with its top leaning west, as the wind blows it east on the way
            // down; its face turned to the camera.
            into[at] = (THIN * across).toFloat()
            into[at + 1] = 0f
            into[at + 2] = (-THIN * toward).toFloat()
            into[at + 3] = 0f
            into[at + 4] = (-SLANT * STREAK).toFloat()
            into[at + 5] = STREAK.toFloat()
            into[at + 6] = 0f
            into[at + 7] = 0f
            into[at + 8] = toward.toFloat()
            into[at + 9] = 0f
            into[at + 10] = across.toFloat()
            into[at + 11] = 0f
            into[at + 12] = x.toFloat()
            into[at + 13] = y.toFloat()
            into[at + 14] = z.toFloat()
            into[at + 15] = 1f
        }
        for (index in 0 until SPLASHES) {
            val splash = splashAt(index, millis, centreX, centreZ)
            val at = (DROPS + index) * 16
            // The square laid flat on the ground: its up runs north, its face up.
            for (offset in 0 until 16) into[at + offset] = 0f
            into[at] = splash.width.toFloat()
            into[at + 6] = (-splash.depth).toFloat()
            into[at + 9] = 1f
            into[at + 12] = (splash.x + splash.width / 2).toFloat()
            into[at + 13] = (splash.y + splash.height / 2).toFloat()
            into[at + 14] = (splash.z + splash.depth / 2).toFloat()
            into[at + 15] = 1f
        }
    }

    /** True when a streak's middle, [dx], [dy], [dz] from the camera, is within [CLEAR] of it. */
    private fun isNear(dx: Double, dy: Double, dz: Double): Boolean = dx * dx + dy * dy + dz * dz < CLEAR * CLEAR

    /** How far from the camera the nearest streak may be. */
    const val CLEAR = 4.0

    /** A number from 0.0 to 1.0 that looks random but comes from [seed] alone. */
    private fun scatter(seed: Double): Double {
        val value = sin(seed * 12.9898) * 43_758.5453
        return value - floor(value)
    }

    /** True where the map has sea under [x], [z]: a splash there flashes on the water. */
    private fun isSea(x: Double, z: Double): Boolean =
        RealVancouver.map.terrainAt(Cell(floor(x).toInt(), floor(z).toInt())) == Terrain.WATER

    /**
     * Puddles left on the street in front of each place on a rainy day, catching the grey sky: flat boxes just over
     * the stone, which stay where they are. Two for each place, on the first open pairs of squares in front of its
     * door.
     */
    val puddles: List<Block> = Neighbourhood.venues.flatMap { venue ->
        val grid = Neighbourhood.grid
        val out = if (venue.doorFacesSouth) 1 else -1
        val door = grid.cellAt(venue.door)
        val found = mutableListOf<Block>()
        for (step in 1..PUDDLES_WITHIN) {
            for (dx in listOf(-3, 2, -1, 3, -2, 1)) {
                val x = door.x + dx
                val z = door.z + out * step
                val open = (0..1).map { Cell(x + it, z) }.all { cell ->
                    grid.isFree(cell) && RealVancouver.map.terrainAt(cell) == Terrain.LAND
                }
                val apart = found.none { abs(it.x - x) < 3.0 && abs(it.z - z) < 2.0 }
                if (open && apart && found.size < 2) {
                    found.add(Block(x + 0.25, PUDDLE_HEIGHT, z + 0.15, 1.5, 0.012, 0.7, PUDDLE))
                }
            }
        }
        found
    }

    /** How many rows out from a door the puddles may lie. */
    private const val PUDDLES_WITHIN = 4

    /** How high a puddle lies: just over the street's stone. */
    const val PUDDLE_HEIGHT = 0.032
    private const val MIN_SIZE = 0.001
    private const val PUDDLE = 0xA3AEB6
}

/**
 * Where the boxes of the train's cars are drawn. Each car's boxes are long along x round the car's middle; a car is
 * turned along the guideway where it bends, so its boxes are written as Three.js keeps a matrix, sixteen numbers each,
 * column by column. It makes nothing new, since it runs on every beat of the town's life.
 */
object TrainCars {
    /**
     * Writes [boxes], car number [car]'s, turned by [turn] about the vertical and set down at [spot], into their
     * slots of [into]: from car times the number of boxes on. A car [hidden] in the tunnel is shrunk to nothing.
     */
    fun write(boxes: List<Block>, car: Int, spot: Spot, turn: Double, hidden: Boolean, into: Float32Array) {
        val across = cos(turn)
        val toward = sin(turn)
        for ((index, box) in boxes.withIndex()) {
            val at = (car * boxes.size + index) * 16
            for (offset in 0 until 15) into[at + offset] = 0f
            into[at + 15] = 1f
            if (hidden) continue
            val middleX = box.x + box.width / 2
            val middleZ = box.z + box.depth / 2
            into[at] = (across * box.width).toFloat()
            into[at + 2] = (-toward * box.width).toFloat()
            into[at + 5] = box.height.toFloat()
            into[at + 8] = (toward * box.depth).toFloat()
            into[at + 10] = (across * box.depth).toFloat()
            into[at + 12] = (spot.x + middleX * across + middleZ * toward).toFloat()
            into[at + 13] = (box.y + box.height / 2).toFloat()
            into[at + 14] = (spot.z - middleX * toward + middleZ * across).toFloat()
        }
    }
}
