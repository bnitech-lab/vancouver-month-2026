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
 * [route] finds the way between two spots with A*, the usual shortest-path search in games. Inside, each square is a
 * number, z * [columns] + x, so a search on a big grid makes no new objects for the squares it looks at.
 */
class Grid(val columns: Int, val rows: Int, blocked: Collection<Cell>) {
    private val closed = BooleanArray(columns * rows).also { closed ->
        for (cell in blocked) if (cell.x in 0 until columns && cell.z in 0 until rows) closed[cell.z * columns + cell.x] = true
    }

    fun isFree(cell: Cell): Boolean = isFree(cell.x, cell.z)

    private fun isFree(x: Int, z: Int): Boolean =
        x in 0 until columns && z in 0 until rows && !closed[z * columns + x]

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
     * It ends at [to] itself, or on the nearest free square when [to] is blocked, or on the nearest square the
     * character can reach when [to] is cut off. Empty only when [from] has nowhere to go.
     */
    fun route(from: Spot, to: Spot): List<Spot> {
        val start = nearestFree(cellAt(from)) ?: return emptyList()
        var goal = nearestFree(cellAt(to)) ?: return emptyList()
        var cells = search(start, goal)
        if (cells == null) {
            // Cut off from where the character is: walk as near as it can get instead.
            goal = reachableFrom(start).minBy { distance(it, cellAt(to)) }
            cells = search(start, goal) ?: return emptyList()
        }
        val end = if (goal == cellAt(to)) to else centreOf(goal)
        val turns = cells.drop(1).dropLast(1).map { centreOf(it) }
        return straighten(listOf(from) + turns + end)
    }

    /** Every free square the character can walk to from [start], [start] included. */
    fun reachableFrom(start: Cell): Set<Cell> {
        if (!isFree(start)) return setOf(start)
        val reached = BooleanArray(columns * rows)
        val waiting = IntArray(columns * rows)
        var head = 0
        var tail = 0
        val first = start.z * columns + start.x
        reached[first] = true
        waiting[tail++] = first
        while (head < tail) {
            val square = waiting[head++]
            val x = square % columns
            val z = square / columns
            for (dx in -1..1) {
                for (dz in -1..1) {
                    if (!canStep(x, z, dx, dz)) continue
                    val next = (z + dz) * columns + x + dx
                    if (reached[next]) continue
                    reached[next] = true
                    waiting[tail++] = next
                }
            }
        }
        return (0 until tail).mapTo(LinkedHashSet()) { Cell(waiting[it] % columns, waiting[it] / columns) }
    }

    /** Every free square of the grid. */
    fun freeCells(): List<Cell> {
        val cells = mutableListOf<Cell>()
        for (x in 0 until columns) {
            for (z in 0 until rows) if (isFree(x, z)) cells.add(Cell(x, z))
        }
        return cells
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
            if (!isFree(floor(x - CLEARANCE).toInt(), floor(z - CLEARANCE).toInt())) return false
            if (!isFree(floor(x - CLEARANCE).toInt(), floor(z + CLEARANCE).toInt())) return false
            if (!isFree(floor(x + CLEARANCE).toInt(), floor(z - CLEARANCE).toInt())) return false
            if (!isFree(floor(x + CLEARANCE).toInt(), floor(z + CLEARANCE).toInt())) return false
        }
        return true
    }

    /**
     * True when a step of [dx], [dz] from square [x], [z] lands on a free square. A slanted step needs both squares
     * beside it free, so the character never cuts the corner of a building.
     */
    private fun canStep(x: Int, z: Int, dx: Int, dz: Int): Boolean {
        if (dx == 0 && dz == 0) return false
        if (!isFree(x + dx, z + dz)) return false
        return dx == 0 || dz == 0 || (isFree(x + dx, z) && isFree(x, z + dz))
    }

    /** A*: the squares from [start] to [goal], both included, or null when no way joins them. */
    private fun search(start: Cell, goal: Cell): List<Cell>? {
        val size = columns * rows
        // The cheapest known way to each square so far, and the square it came from on that way.
        val cost = DoubleArray(size) { Double.MAX_VALUE }
        val cameFrom = IntArray(size) { -1 }
        val done = BooleanArray(size)
        val from = start.z * columns + start.x
        val to = goal.z * columns + goal.x
        cost[from] = 0.0
        // Each open square's score is the way so far plus a guess at the rest that is never too high.
        val open = OpenSquares()
        open.push(from, guess(start.x, start.z, goal))
        while (open.isNotEmpty()) {
            val current = open.pop()
            // A square can wait in the heap twice, the second time with a better score; only its first turn counts.
            if (done[current]) continue
            done[current] = true
            if (current == to) return wayBack(to, cameFrom)
            val x = current % columns
            val z = current / columns
            for (dx in -1..1) {
                for (dz in -1..1) {
                    if (!canStep(x, z, dx, dz)) continue
                    val next = (z + dz) * columns + x + dx
                    if (done[next]) continue
                    val newCost = cost[current] + if (dx != 0 && dz != 0) SLANT else 1.0
                    if (newCost < cost[next]) {
                        cost[next] = newCost
                        cameFrom[next] = current
                        open.push(next, newCost + guess(x + dx, z + dz, goal))
                    }
                }
            }
        }
        return null
    }

    private fun wayBack(goal: Int, cameFrom: IntArray): List<Cell> {
        val squares = mutableListOf(goal)
        while (cameFrom[squares.last()] >= 0) squares.add(cameFrom[squares.last()])
        return squares.asReversed().map { Cell(it % columns, it / columns) }
    }

    /** What the rest of the way would cost with nothing in it: slanted steps first, then straight ones. */
    private fun guess(x: Int, z: Int, goal: Cell): Double {
        val across = abs(x - goal.x)
        val down = abs(z - goal.z)
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

/**
 * The squares A* still has to look at, each by its number, the lowest score always first. It is a binary heap: a list
 * where each square scores no more than the two below it (at 2i + 1 and 2i + 2), so adding one or taking the first
 * moves squares along one branch only, instead of searching the whole list. It grows as needed and makes nothing new
 * otherwise.
 */
internal class OpenSquares {
    private var squares = IntArray(256)
    private var scores = DoubleArray(256)
    private var size = 0

    fun isNotEmpty(): Boolean = size > 0

    fun push(square: Int, score: Double) {
        if (size == squares.size) {
            squares = squares.copyOf(size * 2)
            scores = scores.copyOf(size * 2)
        }
        squares[size] = square
        scores[size] = score
        size++
        // Move it up while the one above it scores more.
        var child = size - 1
        while (child > 0) {
            val parent = (child - 1) / 2
            if (scores[parent] <= scores[child]) break
            swap(parent, child)
            child = parent
        }
    }

    /** Takes the square with the lowest score out. */
    fun pop(): Int {
        val first = squares[0]
        swap(0, size - 1)
        size--
        // Move the one now at the top down while one below it scores less.
        var parent = 0
        while (true) {
            val left = 2 * parent + 1
            val right = left + 1
            var lowest = parent
            if (left < size && scores[left] < scores[lowest]) lowest = left
            if (right < size && scores[right] < scores[lowest]) lowest = right
            if (lowest == parent) return first
            swap(parent, lowest)
            parent = lowest
        }
    }

    private fun swap(a: Int, b: Int) {
        val square = squares[a]
        squares[a] = squares[b]
        squares[b] = square
        val score = scores[a]
        scores[a] = scores[b]
        scores[b] = score
    }
}
