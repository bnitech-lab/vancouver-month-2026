package vancouvermonth.web

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Which of the town's unimportant buildings ([Neighbourhood.foldables]) fold down out of the way, and how far each is
 * folded as drawn. A building folds when it stands in a thick ray from the camera's eye to the character's head, or
 * fills the near view just in front of the camera; it folds over [FOLD_MILLIS] down to a plinth [PLINTH] tall, keeping
 * its footprint and colour, and rises back the same way once clear. The ray is [RAY_RADIUS] thick from the eye and
 * narrows to [HEAD_RADIUS] over the last [HEAD_TAPER] before the head, so the buildings level with the character and
 * behind it stand. Over the first [NEAR_LENGTH] in front of the camera it widens as the view does, up to
 * [NEAR_RADIUS], so what fills the near view folds but what stands beside or behind the camera, out of sight, does not.
 * A building already folded stays folded until it is [SLACK] farther out than that, so one at the edge does not
 * flicker. One the camera's eye is inside, or nearly, folds at once rather than over time, so the camera is never seen
 * inside a tower still standing. [buildings] are the boxes, each from the ground up, in the order the drawing knows
 * them by; [shapes] are the buildings themselves, the same as [buildings] but for one turned on the ground, whose
 * straight box would reach well past its walls: the ray is tried against such a building in its own frame.
 *
 * Nothing new is made while the camera moves: the buildings are kept in buckets of [BUCKET] units on the ground, and
 * [aim] looks only at those near the ray.
 */
class Cutaway(buildings: List<Block>, shapes: List<Block> = buildings) {
    private val count = buildings.size

    // A building turned on the ground ([Block.turn], as the wide map's are) is looked at in its own frame, not by the
    // straight box round it: its middle, its turn's cosine and sine, and half its width and depth.
    private val turned = BooleanArray(count) { shapes[it].turn != 0.0 }
    private val middleX = DoubleArray(count) { shapes[it].x + shapes[it].width / 2 }
    private val middleZ = DoubleArray(count) { shapes[it].z + shapes[it].depth / 2 }
    private val cosine = DoubleArray(count) { cos(shapes[it].turn) }
    private val sine = DoubleArray(count) { sin(shapes[it].turn) }
    private val halfWidth = DoubleArray(count) { shapes[it].width / 2 }
    private val halfDepth = DoubleArray(count) { shapes[it].depth / 2 }
    private val lowX = DoubleArray(count) { buildings[it].x }
    private val lowZ = DoubleArray(count) { buildings[it].z }
    private val highX = DoubleArray(count) { buildings[it].x + buildings[it].width }
    private val highZ = DoubleArray(count) { buildings[it].z + buildings[it].depth }
    private val tops = DoubleArray(count) { buildings[it].y + buildings[it].height }

    // The buckets: a grid of [BUCKET]-unit squares over all the buildings, each listing the buildings over it.
    private val originX = (buildings.minOfOrNull { it.x } ?: 0.0) - BUCKET
    private val originZ = (buildings.minOfOrNull { it.z } ?: 0.0) - BUCKET
    private val columns = bucketOf((buildings.maxOfOrNull { it.x + it.width } ?: 0.0) + BUCKET, originX) + 1
    private val rows = bucketOf((buildings.maxOfOrNull { it.z + it.depth } ?: 0.0) + BUCKET, originZ) + 1
    private val bucketStart = IntArray(columns * rows + 1)
    private val bucketItems: IntArray

    /** Which [aim] last looked at each building, so one over several buckets is looked at once. */
    private val seen = IntArray(count)
    private var looks = 0

    /** True for each building that should be folded down now. */
    private val wanted = BooleanArray(count)

    /** How far each building is folded as drawn, from 0.0 standing to 1.0 down to its plinth. */
    private val folded = DoubleArray(count)

    /** True for each building to fold all the way at the next [step]: the camera's eye is inside it. */
    private val snaps = BooleanArray(count)

    /** The buildings folding or rising, by number, and a mark on each so it is listed once. */
    private val moving = IntArray(count)
    private var movingCount = 0
    private val isListed = BooleanArray(count)

    /** The buildings whose drawn height changed at the last [step], by number, for the drawing to put again. */
    val changed = IntArray(count)
    var changedCount = 0
        private set

    init {
        // Count the buildings over each bucket, then lay them out bucket by bucket.
        val counts = IntArray(columns * rows)
        forEachBucket { _, bucket -> counts[bucket]++ }
        for (bucket in counts.indices) bucketStart[bucket + 1] = bucketStart[bucket] + counts[bucket]
        bucketItems = IntArray(bucketStart.last())
        val filled = IntArray(columns * rows)
        forEachBucket { index, bucket -> bucketItems[bucketStart[bucket] + filled[bucket]++] = index }
    }

    private fun forEachBucket(each: (index: Int, bucket: Int) -> Unit) {
        for (index in 0 until count) {
            for (column in bucketOf(lowX[index], originX)..bucketOf(highX[index], originX)) {
                for (row in bucketOf(lowZ[index], originZ)..bucketOf(highZ[index], originZ)) {
                    each(index, row * columns + column)
                }
            }
        }
    }

    private fun bucketOf(at: Double, origin: Double): Int = floor((at - origin) / BUCKET).toInt()

    /** True while any building is still folding down or rising back. */
    val isMoving: Boolean
        get() = movingCount > 0

    /** True when building [index] is folded down, or folding: it stands in the way. */
    fun isFolding(index: Int): Boolean = wanted[index]

    /** How far building [index] is folded as drawn, eased: 0.0 standing, 1.0 down to its plinth. */
    fun foldOf(index: Int): Double {
        val share = folded[index]
        return share * share * (3 - 2 * share)
    }

    /** How tall building [index] stands as drawn, roof included: its full height, down to [PLINTH] when folded. */
    fun heightOf(index: Int): Double {
        val top = tops[index]
        return if (top <= PLINTH) top else top - (top - PLINTH) * foldOf(index)
    }

    /**
     * Works out which buildings stand in the way of a camera at [eyeX], [eyeY], [eyeZ] looking at the character's head
     * at [headX], [headY], [headZ], and starts each one that changed folding down or rising back.
     */
    fun aim(eyeX: Double, eyeY: Double, eyeZ: Double, headX: Double, headY: Double, headZ: Double) {
        val reach = NEAR_RADIUS + SLACK
        val fromColumn = max(0, bucketOf(min(eyeX, headX) - reach, originX))
        val toColumn = min(columns - 1, bucketOf(max(eyeX, headX) + reach, originX))
        val fromRow = max(0, bucketOf(min(eyeZ, headZ) - reach, originZ))
        val toRow = min(rows - 1, bucketOf(max(eyeZ, headZ) + reach, originZ))
        looks += 1
        val look = looks
        // Every building that was in the way and may be no longer, then every one near the ray now.
        for (index in 0 until count) {
            if (!wanted[index] || seen[index] == look) continue
            seen[index] = look
            settle(index, eyeX, eyeY, eyeZ, headX, headY, headZ)
        }
        for (row in fromRow..toRow) {
            for (column in fromColumn..toColumn) {
                val bucket = row * columns + column
                for (at in bucketStart[bucket] until bucketStart[bucket + 1]) {
                    val index = bucketItems[at]
                    if (seen[index] == look) continue
                    seen[index] = look
                    settle(index, eyeX, eyeY, eyeZ, headX, headY, headZ)
                }
            }
        }
    }

    private fun settle(
        index: Int,
        eyeX: Double, eyeY: Double, eyeZ: Double,
        headX: Double, headY: Double, headZ: Double,
    ) {
        val inWay = isInWay(index, eyeX, eyeY, eyeZ, headX, headY, headZ, if (wanted[index]) SLACK else 0.0)
        // With the eye inside it, or nearly, a building still standing even in part folds at once.
        val snap = inWay && folded[index] < 1.0 && holdsEye(index, eyeX, eyeY, eyeZ)
        if (inWay == wanted[index] && !snap) return
        wanted[index] = inWay
        if (snap) snaps[index] = true
        if (!isListed[index]) {
            isListed[index] = true
            moving[movingCount++] = index
        }
    }

    /** True when the eye at [eyeX], [eyeY], [eyeZ] is inside building [index], grown by [SNAP_MARGIN] all round. */
    private fun holdsEye(index: Int, eyeX: Double, eyeY: Double, eyeZ: Double): Boolean =
        eyeX >= lowX[index] - SNAP_MARGIN && eyeX <= highX[index] + SNAP_MARGIN &&
            eyeZ >= lowZ[index] - SNAP_MARGIN && eyeZ <= highZ[index] + SNAP_MARGIN &&
            eyeY <= tops[index] + SNAP_MARGIN

    /**
     * True when building [index] comes within the ray's thickness, plus [slack], of the way from the eye to the head:
     * looked at every [STEP] along it, each point's distance to the building's box worked out in place.
     */
    private fun isInWay(
        index: Int,
        eyeX: Double, eyeY: Double, eyeZ: Double,
        headX: Double, headY: Double, headZ: Double,
        slack: Double,
    ): Boolean {
        // The building's box, grown by the thickest the ray gets, missed by the line's box: nothing to look along.
        val widest = NEAR_RADIUS + slack
        if (highX[index] < min(eyeX, headX) - widest || lowX[index] > max(eyeX, headX) + widest) return false
        if (highZ[index] < min(eyeZ, headZ) - widest || lowZ[index] > max(eyeZ, headZ) + widest) return false
        if (tops[index] < min(eyeY, headY) - widest) return false
        val dx = headX - eyeX
        val dy = headY - eyeY
        val dz = headZ - eyeZ
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length == 0.0) return false
        val steps = (length / STEP).toInt() + 1
        for (step in 0..steps) {
            val along = min(length, step * STEP)
            val radius = radiusAt(along, length) + slack
            val share = along / length
            val x = eyeX + dx * share
            val y = eyeY + dy * share
            val z = eyeZ + dz * share
            val outY = max(0.0, max(-y, y - tops[index]))
            val outX: Double
            val outZ: Double
            if (turned[index]) {
                // In the building's own frame: along it, and across it.
                val fromX = x - middleX[index]
                val fromZ = z - middleZ[index]
                outX = max(0.0, abs(fromX * cosine[index] - fromZ * sine[index]) - halfWidth[index])
                outZ = max(0.0, abs(fromX * sine[index] + fromZ * cosine[index]) - halfDepth[index])
            } else {
                outX = max(0.0, max(lowX[index] - x, x - highX[index]))
                outZ = max(0.0, max(lowZ[index] - z, z - highZ[index]))
            }
            if (outX * outX + outY * outY + outZ * outZ <= radius * radius) return true
        }
        return false
    }

    /**
     * Moves each folding or rising building on by [millis]. True while any is still on the move. [changed] then lists
     * the buildings whose drawn height changed.
     */
    fun step(millis: Double): Boolean {
        changedCount = 0
        val by = millis / FOLD_MILLIS
        var kept = 0
        for (at in 0 until movingCount) {
            val index = moving[at]
            val target = if (wanted[index]) 1.0 else 0.0
            val now = folded[index]
            val next = when {
                snaps[index] -> target
                target > now -> min(target, now + by)
                else -> max(target, now - by)
            }
            snaps[index] = false
            if (next != now) {
                folded[index] = next
                changed[changedCount++] = index
            }
            if (next == target) isListed[index] = false else moving[kept++] = index
        }
        movingCount = kept
        return movingCount > 0
    }

    companion object {
        /** How tall a folded building stands: a low outline of its footprint. */
        const val PLINTH = 0.6

        /** How long a building takes to fold down, or to rise back. */
        const val FOLD_MILLIS = 250.0

        /** How thick the ray to the character is, around its line, and at the head itself. */
        const val RAY_RADIUS = 1.5
        const val HEAD_RADIUS = 0.5

        /** Over how much of the way before the head the ray narrows, so what stands beside the character stays. */
        const val HEAD_TAPER = 2.5

        /**
         * The near view: how far in front of the camera, how thick around its line at most, and how fast it widens
         * from the eye: 0.9 a unit, about the 42 degrees a tall phone sees above and below the middle of the screen.
         */
        const val NEAR_LENGTH = 6.0
        const val NEAR_RADIUS = 3.0
        const val NEAR_SPREAD = 0.9

        /** How near the eye must be to a building, outside it, for the building to fold at once. */
        const val SNAP_MARGIN = 0.5

        /** How much farther out a folded building must be before it rises again. */
        const val SLACK = 0.6

        /** How finely the way is looked along, and how wide a bucket of buildings is. */
        const val STEP = 0.4
        const val BUCKET = 8.0

        /**
         * How thick the ray is [along] units from the eye, on a way [length] units long to the head: in the near view,
         * widening from [RAY_RADIUS] at [NEAR_SPREAD] a unit up to [NEAR_RADIUS]; [RAY_RADIUS] on the way; narrowing to
         * [HEAD_RADIUS] at the head. The near view never reaches into the part that narrows, so a camera brought in
         * close does not fold what stands round the character; and as it starts from the ray's own thickness at the
         * eye, a camera coming in or going out never folds or raises anything all at once.
         */
        fun radiusAt(along: Double, length: Double): Double {
            val toHead = length - along
            if (toHead < HEAD_TAPER) return HEAD_RADIUS + (RAY_RADIUS - HEAD_RADIUS) * max(0.0, toHead) / HEAD_TAPER
            if (along > NEAR_LENGTH || toHead < HEAD_TAPER + NEAR_RADIUS) return RAY_RADIUS
            return max(RAY_RADIUS, min(NEAR_RADIUS, along * NEAR_SPREAD))
        }
    }
}
