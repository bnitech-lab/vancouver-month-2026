package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import vancouvermonth.text.Passer
import vancouvermonth.text.Townsperson

class FiguresTest {
    private val k = Figures.SCALE

    /** Where the middle of a placed box ends up: the last number of each row. */
    private fun middle(matrix: DoubleArray) = Point(matrix[3], matrix[7], matrix[11])

    @Test
    fun `standing still, each box keeps its size and sits where its limb puts it, all drawn larger`() {
        val parts = Figures.townsperson(Townsperson.HANA)
        val placed = Figures.place(parts, Pose(Spot(3.0, 4.0), heading = 0.0))
        assertEquals(parts.size, placed.size)
        for ((part, matrix) in parts.zip(placed)) {
            val block = part.block
            val pivot = part.limb.pivot
            assertEquals(block.width * k, matrix[0], 1e-9)
            assertEquals(block.height * k, matrix[5], 1e-9)
            assertEquals(block.depth * k, matrix[10], 1e-9)
            for (offDiagonal in listOf(1, 2, 4, 6, 8, 9)) assertEquals(0.0, matrix[offDiagonal], 1e-9)
            assertEquals(3.0 + (pivot.x + block.x + block.width / 2) * k, matrix[3], 1e-9)
            assertEquals((pivot.y + block.y + block.height / 2) * k, matrix[7], 1e-9)
            assertEquals(4.0 + (pivot.z + block.z + block.depth / 2) * k, matrix[11], 1e-9)
        }
    }

    @Test
    fun `turned a quarter, a figure's face looks east`() {
        val parts = Figures.townsperson(Townsperson.LEO)
        // The eyes are the boxes on the front of the head.
        val eyes = parts.indexOfFirst { it.block.z > 0.25 && it.block.y > 0.9 && it.block.height < 0.2 }
        val south = middle(Figures.place(parts, Pose(Spot(0.0, 0.0), 0.0))[eyes])
        val east = middle(Figures.place(parts, Pose(Spot(0.0, 0.0), PI / 2))[eyes])
        assertTrue(south.z > 0.3, "$south")
        assertTrue(east.x > 0.3 && abs(east.z) < 0.2, "$east")
    }

    @Test
    fun `a wave lifts the left hand out beside the head, where it shows, and leaves the rest where it was`() {
        val parts = Figures.townsperson(Townsperson.GRANDMA)
        val rest = Figures.place(parts, Pose(Spot(0.0, 0.0), 0.0))
        val head = 0.37 * k
        for (wave in listOf(Greeter.WAVE_RAISE - Greeter.WAVE_SWAY, Greeter.WAVE_RAISE + Greeter.WAVE_SWAY)) {
            val waving = Figures.place(parts, Pose(Spot(0.0, 0.0), 0.0, wave = wave))
            for ((index, part) in parts.withIndex()) {
                if (part.limb != Limb.LEFT_ARM) {
                    assertTrue(rest[index].contentEquals(waving[index]), "${part.limb} moved")
                } else if (part.block.y < -0.4) {
                    val hand = middle(waving[index])
                    assertTrue(hand.x < -head - 0.05, "the hand is hidden by the head: $hand")
                    assertTrue(hand.y > Figures.SHOULDER * k, "the hand is not up: $hand")
                }
            }
        }
    }

    @Test
    fun `a swing moves the legs opposite ways, and a bob lifts the whole figure`() {
        val parts = Figures.passer(Passer.OLIVER)
        val left = parts.indexOfFirst { it.limb == Limb.LEFT_LEG }
        val right = parts.indexOfFirst { it.limb == Limb.RIGHT_LEG }
        val swung = Figures.place(parts, Pose(Spot(0.0, 0.0), 0.0, swing = 0.5))
        assertTrue(middle(swung[left]).z * middle(swung[right]).z < 0, "the legs swing the same way")
        val lifted = Figures.place(parts, Pose(Spot(0.0, 0.0), 0.0, lift = 0.1))
        val still = Figures.place(parts, Pose(Spot(0.0, 0.0), 0.0))
        for ((up, down) in lifted.zip(still)) assertEquals(0.1, middle(up).y - middle(down).y, 1e-9)
    }

    @Test
    fun `each passerby has a whole body, and a look of their own for the talk box`() {
        for (passer in Passer.entries) {
            assertEquals(Limb.entries.toSet(), Figures.passer(passer).map { it.limb }.toSet(), "$passer")
        }
        val looks = Passer.entries.map { Figures.lookOf(it) } + Townsperson.entries.map { Figures.lookOf(it) }
        assertEquals(looks.size, looks.toSet().size)
        // Nothing they carry reaches below the ground.
        for (passer in Passer.entries) {
            for (matrix in Figures.place(Figures.passer(passer), Pose(Spot(0.0, 0.0), 0.0))) {
                assertTrue(middle(matrix).y - matrix[5] / 2 >= -1e-9, "$passer")
            }
        }
    }

    @Test
    fun `the town's fast placing gives the same numbers as the plain one, for any pose`() {
        val figures = Townsperson.entries.map { Figures.townsperson(it) } + Passer.entries.map { Figures.passer(it) }
        val poses = listOf(
            Pose(Spot(0.0, 0.0), 0.0),
            Pose(Spot(12.5, 30.25), 2.0, lift = 0.05, swing = 0.4),
            Pose(Spot(44.0, 1.5), -2.7, swing = -0.45, wave = 2.3),
            Pose(Spot(3.0, 17.0), PI / 2, lift = 0.02, wave = Greeter.WAVE_RAISE),
        )
        for (parts in figures) {
            val fast = FigureBoxes(parts)
            assertEquals(parts.size, fast.count)
            // Two figures' room, written from the second, so the offset is checked too.
            val numbers = Float32Array(fast.count * 2 * 16)
            for (pose in poses) {
                fast.write(pose, numbers, first = fast.count)
                for ((box, matrix) in Figures.place(parts, pose).withIndex()) {
                    val at = (fast.count + box) * 16
                    for (row in 0..2) {
                        for (column in 0..3) {
                            val expected = matrix[row * 4 + column]
                            assertEquals(expected, numbers[at + column * 4 + row].toDouble(), 1e-4, "$pose box $box")
                        }
                    }
                    for (column in 0..2) assertEquals(0f, numbers[at + column * 4 + 3])
                    assertEquals(1f, numbers[at + 15])
                }
            }
        }
    }
}
