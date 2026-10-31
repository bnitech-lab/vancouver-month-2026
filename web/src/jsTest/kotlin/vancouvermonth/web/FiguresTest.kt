package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
}
