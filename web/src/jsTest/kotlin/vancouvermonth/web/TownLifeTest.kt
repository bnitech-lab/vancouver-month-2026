package vancouvermonth.web

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TownLifeTest {
    /** The town's time every [step] milliseconds over [millis]. */
    private fun times(millis: Double, step: Double = 16.0): List<Double> = (0..(millis / step).toInt()).map { it * step }

    @Test
    fun `a train passes every twenty to forty seconds, and stands at the station for a while each time`() {
        assertTrue(SkyTrainRun.PERIOD in 20_000.0..40_000.0)
        for (round in 0..2) {
            val start = round * SkyTrainRun.PERIOD
            assertEquals(0.0, SkyTrainRun.shiftAt(start))
            assertEquals(0.0, SkyTrainRun.shiftAt(start + SkyTrainRun.DWELL - 1))
            assertTrue(SkyTrainRun.shiftAt(start + SkyTrainRun.DWELL + 3_000) > 0, "it leaves east")
            assertTrue(SkyTrainRun.shiftAt(start + SkyTrainRun.PERIOD - 3_000) < 0, "it comes in from the west")
        }
    }

    @Test
    fun `the train always stays on the guideway, and never jumps while it can be seen`() {
        var last: Double? = null
        for (t in times(2 * SkyTrainRun.PERIOD)) {
            val shift = SkyTrainRun.shiftAt(t)
            if (!SkyTrainRun.isShownAt(t)) {
                last = null
                continue
            }
            val west = Neighbourhood.TRAIN_STOP + shift
            assertTrue(west >= Neighbourhood.GUIDEWAY_WEST, "at $t the train is off the west end: $west")
            assertTrue(west + Neighbourhood.TRAIN_LENGTH <= Neighbourhood.GUIDEWAY_EAST, "at $t off the east end")
            last?.let { assertTrue(abs(shift - it) < 0.6, "at $t it jumped from $it to $shift") }
            last = shift
        }
        // Its boxes sit on the deck, under the station's roof, within the deck's width.
        for (block in Neighbourhood.train + Neighbourhood.trainWindows) {
            assertTrue(block.y >= Neighbourhood.DECK_HEIGHT + 0.4 - 1e-9, "$block")
            assertTrue(block.y + block.height <= Neighbourhood.DECK_HEIGHT + 2.4, "$block")
            assertTrue(block.z >= Neighbourhood.DECK_NORTH && block.z + block.depth <= Neighbourhood.DECK_SOUTH, "$block")
            assertTrue(block.x >= 0.0 && block.x + block.width <= Neighbourhood.TRAIN_LENGTH, "$block")
        }
    }

    @Test
    fun `the train slows all the way into the station, and is away only while far down the line`() {
        val arrive = SkyTrainRun.PERIOD - SkyTrainRun.ARRIVE
        val speeds = (0 until 20).map { step ->
            val t = arrive + step * SkyTrainRun.ARRIVE / 20
            (SkyTrainRun.shiftAt(t + 16) - SkyTrainRun.shiftAt(t)) / 16 * 1_000
        }
        assertTrue(speeds.zipWithNext().all { (a, b) -> b < a }, "$speeds")
        assertTrue(speeds.last() < 2.0, "comes in at ${speeds.last()} units a second")
        for (t in times(SkyTrainRun.PERIOD, 50.0)) {
            if (!SkyTrainRun.isShownAt(t)) assertTrue(abs(SkyTrainRun.shiftAt(t)) >= SkyTrainRun.REACH - 1e-9, "at $t")
        }
    }

    @Test
    fun `the campfire flickers, always between three quarters and a quarter more of steady`() {
        val flickers = times(10_000.0).map { Campfire.flicker(it) }
        assertTrue(flickers.all { it in 0.75..1.25 }, "${flickers.min()} to ${flickers.max()}")
        assertTrue(flickers.max() - flickers.min() > 0.25, "hardly flickers")
        assertNotEquals(Campfire.flicker(1_000.0), Campfire.flicker(1_050.0))
        assertEquals(Campfire.flicker(1_234.0), Campfire.flicker(1_234.0))
    }

    @Test
    fun `glints stay on the water, come and go, and fade in the night`() {
        for (t in times(20_000.0, 250.0)) {
            val glints = Shimmer.at(t, night = 0.0)
            assertEquals(Shimmer.count, glints.size)
            for (glint in glints) {
                val middle = Spot(glint.x + glint.width / 2, glint.z + glint.depth / 2)
                val onSea = middle.z < -0.4 && glint.y < 0.0
                val onPond = middle.x in 34.0..38.0 && middle.z in 24.6..27.4
                assertTrue(onSea || onPond, "$glint at $t")
                assertTrue(glint.width <= Shimmer.MAX_WIDTH, "$glint")
                if (onPond) assertTrue(glint.x >= 34.0 && glint.x + glint.width <= 38.0, "$glint spills off the pond")
            }
        }
        val one = Shimmer.at(1_000.0, 0.0).map { it.width }
        val later = Shimmer.at(1_700.0, 0.0).map { it.width }
        assertNotEquals(one, later)
        val night = Shimmer.at(1_000.0, 1.0).map { it.width }
        assertTrue(night.zip(one).all { (dark, day) -> dark <= day * 0.11 + 0.002 }, "still bright at night")
    }

    @Test
    fun `petals drift down from each blossom tree and stay near it`() {
        assertTrue(Neighbourhood.blossoms.size >= 4)
        for (t in times(12_000.0, 100.0)) {
            val petals = Petals.at(t)
            assertEquals(Petals.count, petals.size)
            for ((index, petal) in petals.withIndex()) {
                val tree = Neighbourhood.blossoms[index / (Petals.count / Neighbourhood.blossoms.size)]
                assertTrue(distanceBetween(Spot(petal.x, petal.z), tree) < 2.5, "$petal far from $tree")
                assertTrue(petal.y in 0.0..Petals.START_HEIGHT + 0.1, "$petal")
            }
        }
        val falling = Petals.at(1_000.0).zip(Petals.at(1_200.0)).count { (a, b) -> b.y < a.y }
        assertTrue(falling > Petals.count / 2, "only $falling petals fall")
    }

    @Test
    fun `clouds drift east high over the water and come round again`() {
        for (t in times(600_000.0, 5_000.0)) {
            for (box in Clouds.at(t)) {
                assertTrue(box.y > 25.0 && box.z < -20.0, "$box")
                assertTrue(box.x >= Clouds.WEST - 15 && box.x + box.width <= Clouds.EAST + 15, "$box at $t")
            }
        }
        assertEquals(Clouds.count, Clouds.at(0.0).size)
        val first = Clouds.at(0.0).first().x
        assertEquals(first + 1_000 * Clouds.DRIFT, Clouds.at(1_000.0).first().x, 1e-9)
    }
}
