package vancouvermonth.web

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One square of the town's grid: column [x] counts east from 0, row [z] counts south from 0. */
data class Cell(val x: Int, val z: Int)

/** How far apart two spots on the ground are. */
fun distanceBetween(a: Spot, b: Spot): Double = hypot(b.x - a.x, b.z - a.z)

/**
 * The ground the character walks on, cut into squares one unit wide. A square is blocked when something stands on
 * it (a building, a person, a tree trunk, a fence) and every square outside the grid is blocked too.
 * [route] finds the way between two spots with A*, the usual shortest-path search in games.
 */
class Grid(val columns: Int, val rows: Int, blocked: Collection<Cell>) {
    private val blockedCells: Set<Cell> = blocked.toSet()

    fun isFree(cell: Cell): Boolean =
        cell.x in 0 until columns && cell.z in 0 until rows && cell !in blockedCells

    /** The square [spot] is on. */
    fun cellAt(spot: Spot): Cell = Cell(floor(spot.x).toInt(), floor(spot.z).toInt())

    fun centreOf(cell: Cell): Spot = Spot(cell.x + 0.5, cell.z + 0.5)

    /**
     * The free square nearest to [cell]. A cell outside the grid is first brought to its edge, so a tap on the
     * water ends on the seawall. Null only when every square is blocked.
     */
    fun nearestFree(cell: Cell): Cell? {
        val inside = Cell(cell.x.coerceIn(0, columns - 1), cell.z.coerceIn(0, rows - 1))
        if (isFree(inside)) return inside
        // Look in growing rings around it, and take the closest free square of the first ring that has one.
        for (ring in 1..max(columns, rows)) {
            val found = ringAround(inside, ring).filter { isFree(it) }.minByOrNull { distance(it, inside) }
            if (found != null) return found
        }
        return null
    }

    /**
     * The way from [from] to [to] around everything blocked: [from] first, then the turns, then where it ends.
     * It ends at [to] itself, or on the nearest free square when [to] is blocked. Empty when no way joins them.
     */
    fun route(from: Spot, to: Spot): List<Spot> {
        val start = nearestFree(cellAt(from)) ?: return emptyList()
        val goal = nearestFree(cellAt(to)) ?: return emptyList()
        val end = if (goal == cellAt(to)) to else centreOf(goal)
        val cells = search(start, goal) ?: return emptyList()
        val turns = cells.drop(1).dropLast(1).map { centreOf(it) }
        return straighten(listOf(from) + turns + end)
    }

    /**
     * True when the character, about [CLEARANCE] wide on each side, can walk straight from [a] to [b] without
     * touching a blocked square.
     */
    fun isClear(a: Spot, b: Spot): Boolean {
        val steps = max(1, ceil(hypot(b.x - a.x, b.z - a.z) / SAMPLE_STEP).toInt())
        for (step in 0..steps) {
            val x = a.x + (b.x - a.x) * step / steps
            val z = a.z + (b.z - a.z) * step / steps
            for (dx in listOf(-CLEARANCE, CLEARANCE)) {
                for (dz in listOf(-CLEARANCE, CLEARANCE)) {
                    if (!isFree(cellAt(Spot(x + dx, z + dz)))) return false
                }
            }
        }
        return true
    }

    /** A*: the squares from [start] to [goal], both included, or null when no way joins them. */
    private fun search(start: Cell, goal: Cell): List<Cell>? {
        // The cheapest known way to each square so far, and the square it came from on that way.
        val cost = mutableMapOf(start to 0.0)
        val cameFrom = mutableMapOf<Cell, Cell>()
        val open = mutableSetOf(start)
        val done = mutableSetOf<Cell>()
        while (open.isNotEmpty()) {
            // The open square that looks best: the way so far plus a guess at the rest that is never too high.
            val current = open.minBy { cost.getValue(it) + guess(it, goal) }
            if (current == goal) return wayBack(goal, cameFrom)
            open.remove(current)
            done.add(current)
            for ((next, step) in neighbours(current)) {
                if (next in done) continue
                val newCost = cost.getValue(current) + step
                if (newCost < (cost[next] ?: Double.MAX_VALUE)) {
                    cost[next] = newCost
                    cameFrom[next] = current
                    open.add(next)
                }
            }
        }
        return null
    }

    private fun wayBack(goal: Cell, cameFrom: Map<Cell, Cell>): List<Cell> {
        val cells = mutableListOf(goal)
        while (true) cells.add(cameFrom[cells.last()] ?: break)
        return cells.reversed()
    }

    /**
     * The free squares next to [cell], with what a step there costs: 1 straight, about 1.41 on a slant. A slanted step
     * needs both squares beside it free, so the character never cuts the corner of a building.
     */
    private fun neighbours(cell: Cell): List<Pair<Cell, Double>> = buildList {
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val next = Cell(cell.x + dx, cell.z + dz)
                if (!isFree(next)) continue
                val slanted = dx != 0 && dz != 0
                if (slanted && !(isFree(Cell(cell.x + dx, cell.z)) && isFree(Cell(cell.x, cell.z + dz)))) continue
                add(next to if (slanted) SLANT else 1.0)
            }
        }
    }

    /** What the rest of the way would cost with nothing in it: slanted steps first, then straight ones. */
    private fun guess(a: Cell, b: Cell): Double {
        val across = abs(a.x - b.x)
        val down = abs(a.z - b.z)
        return max(across, down) + (SLANT - 1) * min(across, down)
    }

    /** Drops each turn the character can skip by walking straight, so it does not zigzag from square to square. */
    private fun straighten(spots: List<Spot>): List<Spot> {
        val kept = mutableListOf(spots.first())
        var last = 0
        while (last < spots.lastIndex) {
            var farthest = spots.lastIndex
            while (farthest > last + 1 && !isClear(spots[last], spots[farthest])) farthest--
            kept.add(spots[farthest])
            last = farthest
        }
        return kept
    }

    private fun ringAround(cell: Cell, ring: Int): List<Cell> = buildList {
        for (dx in -ring..ring) {
            for (dz in -ring..ring) {
                if (max(abs(dx), abs(dz)) == ring) add(Cell(cell.x + dx, cell.z + dz))
            }
        }
    }

    private fun distance(a: Cell, b: Cell): Double = hypot((a.x - b.x).toDouble(), (a.z - b.z).toDouble())

    companion object {
        /** How far the character keeps from a blocked square, a little more than half its width. */
        const val CLEARANCE = 0.3
        const val SAMPLE_STEP = 0.1
        private val SLANT = sqrt(2.0)
    }
}
