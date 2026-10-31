package vancouvermonth.web

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GridTest {
    /** True when every leg of [route] is a straight walk that keeps clear of blocked squares. */
    private fun Grid.walks(route: List<Spot>): Boolean = route.zipWithNext().all { (a, b) -> isClear(a, b) }

    @Test
    fun `a route goes around a wall, keeping clear of it`() {
        // Column 2 is blocked except on the bottom row.
        val grid = Grid(5, 5, (0..3).map { Cell(2, it) })
        val route = grid.route(Spot(0.5, 0.5), Spot(4.5, 0.5))
        assertEquals(Spot(0.5, 0.5), route.first())
        assertEquals(Spot(4.5, 0.5), route.last())
        assertTrue(grid.walks(route), "$route")
        assertTrue(route.any { it.z > 4.0 }, "the way should pass below the wall: $route")
    }

    @Test
    fun `an open grid gives a straight line with no turns`() {
        val grid = Grid(10, 10, emptyList())
        assertEquals(listOf(Spot(0.5, 0.5), Spot(9.5, 7.5)), grid.route(Spot(0.5, 0.5), Spot(9.5, 7.5)))
    }

    @Test
    fun `a wall with no gap: the walk ends on this side of it, as near as it can get`() {
        val grid = Grid(5, 5, (0..4).map { Cell(2, it) })
        assertEquals(Spot(1.5, 0.5), grid.route(Spot(0.5, 0.5), Spot(4.5, 0.5)).last())
    }

    @Test
    fun `a walk to a blocked square ends on the nearest free one, and one outside the grid at its edge`() {
        val grid = Grid(5, 5, listOf(Cell(2, 2)))
        assertTrue(grid.nearestFree(Cell(2, 2)) in listOf(Cell(2, 1), Cell(1, 2), Cell(3, 2), Cell(2, 3)))
        val end = grid.route(Spot(0.5, 0.5), Spot(2.5, 2.5)).last()
        assertTrue(grid.isFree(grid.cellAt(end)), "$end")
        assertEquals(1.0, hypot(end.x - 2.5, end.z - 2.5), 1e-9)
        assertEquals(Cell(4, 0), grid.nearestFree(Cell(40, -9)))
    }

    @Test
    fun `a walk to a square that is cut off ends at the nearest square the character can reach`() {
        // A ring of blocked squares around (5, 5), which is free but cannot be reached.
        val ring = (4..6).flatMap { x -> (4..6).map { z -> Cell(x, z) } }.filter { it != Cell(5, 5) }
        val grid = Grid(10, 10, ring)
        assertEquals(setOf(Cell(5, 5)), grid.reachableFrom(Cell(5, 5)))
        val route = grid.route(Spot(0.5, 0.5), Spot(5.5, 5.5))
        val end = route.last()
        assertTrue(grid.isFree(grid.cellAt(end)) && grid.cellAt(end) != Cell(5, 5), "$route")
        assertTrue(hypot(end.x - 5.5, end.z - 5.5) <= 2.0, "$end")
        assertTrue(grid.walks(route), "$route")
    }

    @Test
    fun `the open squares come out lowest score first`() {
        val open = OpenSquares()
        val scores = listOf(5.0, 1.0, 4.0, 1.5, 9.0, 0.5, 3.0, 7.0, 2.0)
        for ((index, score) in scores.withIndex()) open.push(Cell(index, 0), score)
        val order = mutableListOf<Double>()
        while (open.isNotEmpty()) order.add(scores[open.pop().x])
        assertEquals(scores.sorted(), order)
    }
}
