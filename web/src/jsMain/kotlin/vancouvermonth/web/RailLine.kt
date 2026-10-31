package vancouvermonth.web

import kotlin.math.atan2

/**
 * A line on the ground from its first point to its last, as the SkyTrain's guideway runs. Everything is asked by how
 * far along it from the first point: before the first point and past the last, it carries on straight, as a tunnel
 * or a track out of sight would.
 */
class RailLine(val points: List<Spot>) {
    init {
        require(points.size >= 2) { "A line needs two points at least" }
    }

    private val legs: List<Double> = points.zipWithNext { a, b -> distanceBetween(a, b) }

    val length: Double = legs.sum()

    /** Where the line is [along] units from its first point. */
    fun spotAt(along: Double): Spot {
        val (index, into) = legAt(along)
        val a = points[index]
        val b = points[index + 1]
        val share = into / legs[index]
        return Spot(a.x + (b.x - a.x) * share, a.z + (b.z - a.z) * share)
    }

    /**
     * Which way the line runs [along] units from its first point, as a turn about the vertical as Three.js counts it:
     * 0 runs east, a quarter turn runs north. A box turned so is long along the line.
     */
    fun turnAt(along: Double): Double {
        val index = legAt(along).first
        val a = points[index]
        val b = points[index + 1]
        return atan2(a.z - b.z, b.x - a.x)
    }

    /** The leg [along] falls on, and how far into it: the first leg before the line starts, the last past its end. */
    private fun legAt(along: Double): Pair<Int, Double> {
        if (along < 0) return 0 to along
        var left = along
        for ((index, leg) in legs.withIndex()) {
            if (left <= leg || index == legs.lastIndex) return index to left
            left -= leg
        }
        return legs.lastIndex to left
    }
}
