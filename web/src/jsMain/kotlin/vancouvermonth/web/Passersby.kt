package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sin
import vancouvermonth.text.Passer

/**
 * A walk that comes back to where it began: from each of [points] to the next, and from the last back to the first.
 * Two points make a walk there and back. Everything is asked by how far along the loop the walker has gone.
 */
class Stroll(val points: List<Spot>) {
    init {
        require(points.size >= 2) { "A stroll needs two points at least" }
    }

    /** Each leg of the loop, the one from the last point back to the first included. */
    val legs: List<Pair<Spot, Spot>> = points.indices.map { points[it] to points[(it + 1) % points.size] }

    val length: Double = legs.sumOf { (a, b) -> distanceBetween(a, b) }

    /** Where a walker is after [walked] units, round and round the loop. */
    fun spotAt(walked: Double): Spot {
        val (leg, along) = legAt(walked)
        val (a, b) = legs[leg]
        val share = along / distanceBetween(a, b)
        return Spot(a.x + (b.x - a.x) * share, a.z + (b.z - a.z) * share)
    }

    /** Which way a walker faces after [walked] units, as a turn in radians: 0 faces south, as in [Trip]. */
    fun headingAt(walked: Double): Double {
        val (a, b) = legs[legAt(walked).first]
        return atan2(b.x - a.x, b.z - a.z)
    }

    /** The leg a walker is on after [walked] units, and how far along it. */
    private fun legAt(walked: Double): Pair<Int, Double> {
        var left = ((walked % length) + length) % length
        for ((index, leg) in legs.withIndex()) {
            val step = distanceBetween(leg.first, leg.second)
            if (left < step) return index to left
            left -= step
        }
        return legs.lastIndex to distanceBetween(legs.last().first, legs.last().second)
    }
}

/**
 * Someone who walks round their [stroll] at a slow [speed], in units a second, starting [start] units along it.
 * They stop and turn to the player when the player stands right in front of them, or for a while after the player
 * stops them for a word, then walk on. They are not in the grid: the player's walks go through them.
 */
class Passerby(val passer: Passer, val stroll: Stroll, val speed: Double, start: Double) {
    /** How far round the loop they have walked. */
    var walked = start
        private set

    /** The way they face as drawn, turning smoothly toward where they walk or toward the player. */
    var heading = stroll.headingAt(start)
        private set

    /** Until when they stay to talk, in the town's own milliseconds. */
    private var heldUntil = Double.NEGATIVE_INFINITY

    val spot: Spot
        get() = stroll.spotAt(walked)

    /** True while they stand still: the player is in their way, or they were stopped for a word. */
    var isStanding = false
        private set

    /** Stops them for a word at [now]: they stand and face the player for [HOLD_MILLIS]. */
    fun hold(now: Double) {
        heldUntil = now + HOLD_MILLIS
    }

    /** Lets them walk on at once, as when the player walks off. */
    fun release() {
        heldUntil = Double.NEGATIVE_INFINITY
    }

    /** True when [player] stands right in front of them, close and ahead of the way they walk. */
    fun isInTheWay(player: Spot): Boolean {
        val here = spot
        if (distanceBetween(here, player) > BLOCK_DISTANCE) return false
        val toPlayer = atan2(player.x - here.x, player.z - here.z)
        return abs(angleBetween(stroll.headingAt(walked), toPlayer)) < BLOCK_ANGLE
    }

    /**
     * Moves them on by [millis] at the town's time [now], with the player at [player], and says how they stand: walking
     * with a bob and a swing, or standing and turned to the player.
     */
    fun step(now: Double, millis: Double, player: Spot): Pose {
        isStanding = now < heldUntil || isInTheWay(player)
        if (!isStanding) walked += millis.coerceAtLeast(0.0) / 1_000 * speed
        val here = spot
        val target = if (isStanding) atan2(player.x - here.x, player.z - here.z) else stroll.headingAt(walked)
        heading = turnToward(heading, target, millis)
        if (isStanding) return Pose(here, heading)
        val stride = walked / Trip.STEP_LENGTH * PI
        return Pose(here, heading, lift = abs(sin(stride)) * Trip.STEP_HEIGHT, swing = sin(stride) * STROLL_SWING)
    }

    companion object {
        /** How long someone stays to talk after the player stops them. */
        const val HOLD_MILLIS = 10_000.0

        /** The player is in their way within this distance, and within this turn of the way they walk. */
        const val BLOCK_DISTANCE = 1.4
        const val BLOCK_ANGLE = 0.9

        /** A stroll swings the legs less than the player's walk. */
        const val STROLL_SWING = 0.45

        /** The tap box around a passerby: as wide as a townsperson's, and as tall. */
        fun boxAround(spot: Spot): Block = Block(spot.x - 0.4, 0.0, spot.z - 0.4, 0.8, 1.8, 0.8, 0)
    }
}

/** The way from [from] to [to], the short way round, from -PI to PI. */
fun angleBetween(from: Double, to: Double): Double {
    var turn = (to - from) % (2 * PI)
    if (turn > PI) turn -= 2 * PI
    if (turn < -PI) turn += 2 * PI
    return turn
}

/** Turns [from] toward [to], the short way round, as far as [millis] allows: most of the way in a fifth of a second. */
fun turnToward(from: Double, to: Double, millis: Double): Double =
    from + angleBetween(from, to) * (1 - exp(-millis.coerceAtLeast(0.0) / TURN_MILLIS))

private const val TURN_MILLIS = 120.0

/**
 * Who walks where, all off the grid's blocked squares and well away from every door and every place to talk, so none
 * of them ever stands where the player opens a place:
 * - 올리버 jogs along the seawall and back.
 * - 미나 walks the south pavement east of the café, crosses the street, comes back along its north side and crosses
 *   again at the zebra.
 * - 프리야 walks the park's paths, past the pond.
 * - 테오 walks the plaza in front of the station, out of its door and back round.
 * They start far apart and away from 내 방, where each day begins.
 */
object Strolls {
    val all: List<Passerby> get() = listOf(
        Passerby(Passer.OLIVER, Stroll(listOf(Spot(1.0, 0.5), Spot(47.0, 0.5))), speed = 1.7, start = 30.0),
        Passerby(
            Passer.MINA,
            Stroll(listOf(Spot(29.2, 16.3), Spot(45.5, 16.3), Spot(45.5, 12.6), Spot(29.2, 12.6))),
            speed = 1.15,
            start = 4.0,
        ),
        Passerby(
            Passer.PRIYA,
            Stroll(listOf(Spot(29.0, 18.5), Spot(29.0, 23.5), Spot(42.5, 23.5), Spot(42.5, 18.5))),
            speed = 0.9,
            start = 15.0,
        ),
        Passerby(
            Passer.THEO,
            Stroll(listOf(Spot(22.6, 6.0), Spot(27.9, 5.5), Spot(32.6, 6.0), Spot(32.6, 9.6), Spot(22.6, 9.6))),
            speed = 1.05,
            start = 2.0,
        ),
    )
}
