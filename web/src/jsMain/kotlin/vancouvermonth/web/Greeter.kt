package vancouvermonth.web

import kotlin.math.atan2
import kotlin.math.sin

/**
 * One of the five townspeople, who keeps to their [spot] so they are always found there, but is alive: they look
 * around while nobody is near, turn to face the player who comes close, and wave once as the player comes up to them.
 * [facing] is the way they rest, toward the street, and [phase] keeps the five from moving in step.
 */
class Greeter(val spot: Spot, val facing: Double, private val phase: Double) {
    /** The way they face as drawn. */
    var heading = facing
        private set

    /** When the last wave began, in the town's own milliseconds. */
    private var waveStarted = Double.NEGATIVE_INFINITY

    /** True while the player is near enough to have been waved at, until they walk well away again. */
    private var greeted = false

    /** True while a wave goes on at [now]. */
    fun isWaving(now: Double): Boolean = now - waveStarted in 0.0..WAVE_MILLIS

    /** Moves them on by [millis] at the town's time [now], with the player at [player], and says how they stand. */
    fun step(now: Double, millis: Double, player: Spot): Pose {
        val away = distanceBetween(spot, player)
        if (!greeted && away <= WAVE_DISTANCE) {
            greeted = true
            waveStarted = now
        } else if (greeted && away > WAVE_DISTANCE + 1.5) {
            greeted = false
        }
        val target = if (away <= NOTICE_DISTANCE) {
            atan2(player.x - spot.x, player.z - spot.z)
        } else {
            facing + LOOK_AROUND * sin(now / LOOK_MILLIS + phase)
        }
        heading = turnToward(heading, target, millis)
        val wave = if (isWaving(now)) WAVE_RAISE + WAVE_SWAY * sin((now - waveStarted) / 90) else 0.0
        // A slow breath: the body rises and sinks by a hair.
        val breath = BREATH * (1 + sin(now / 650 + phase)) / 2
        return Pose(spot, heading, lift = breath, wave = wave)
    }

    companion object {
        /** They turn to the player within this distance, and wave once within the nearer one. */
        const val NOTICE_DISTANCE = 5.0
        const val WAVE_DISTANCE = 3.5
        const val WAVE_MILLIS = 1_400.0

        /** A wave lifts the arm up beside the head and sways it a little. */
        const val WAVE_RAISE = 2.6
        const val WAVE_SWAY = 0.3

        /** While nobody is near they look this far to each side of the street, slowly. */
        const val LOOK_AROUND = 0.45
        const val LOOK_MILLIS = 1_900.0

        const val BREATH = 0.02
    }
}
