package vancouvermonth.web

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import vancouvermonth.text.Weather

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

    @Test
    fun `far down the line the train fades out smoothly, and near the town it is all there`() {
        var last: Double? = null
        for (t in times(2 * SkyTrainRun.PERIOD)) {
            val shows = SkyTrainRun.opacityAt(t)
            assertTrue(shows in 0.0..1.0)
            if (abs(SkyTrainRun.shiftAt(t)) <= SkyTrainRun.FADE_FROM) assertEquals(1.0, shows, "at $t")
            last?.let { assertTrue(abs(shows - it) < 0.1, "at $t it went from $it to $shows") }
            last = shows
            assertEquals(shows > 0.0, SkyTrainRun.isShownAt(t))
        }
        // All there while any of it is over the town.
        assertTrue(Neighbourhood.TRAIN_STOP + SkyTrainRun.FADE_FROM > Neighbourhood.COLUMNS + Neighbourhood.TRAIN_LENGTH)
    }

    @Test
    fun `clouds come round only where the haze hides them from anywhere the camera can be`() {
        val nearest = -FollowCamera.FAR
        val farthest = Neighbourhood.COLUMNS + FollowCamera.FAR
        var last = Clouds.at(0.0)
        for (t in times(Clouds.EAST * 2 / Clouds.DRIFT * 1.1, 1_000.0).drop(1)) {
            val now = Clouds.at(t)
            for (cloud in 0 until Clouds.count / 3) {
                val before = last.subList(cloud * 3, cloud * 3 + 3)
                val after = now.subList(cloud * 3, cloud * 3 + 3)
                if (after[0].x < before[0].x) {
                    // It came round: where it left and where it came back are both lost in the haze.
                    val leftFrom = before.minOf { it.x }
                    val cameBackTo = after.maxOf { it.x + it.width }
                    assertTrue(leftFrom - farthest >= Neighbourhood.HAZE_FAR, "left from $leftFrom at $t")
                    assertTrue(nearest - cameBackTo >= Neighbourhood.HAZE_FAR, "came back at $cameBackTo at $t")
                }
            }
            last = now
        }
    }

    @Test
    fun `every streak of rain stays in its box of sky and falls, wherever the box is`() {
        for ((centreX, centreZ) in listOf(0.0 to 0.0, 24.3 to 13.5, -37.2 to 51.9, 47.0 to -5.0)) {
            val west = centreX - Rain.SPAN / 2
            val north = centreZ - Rain.SPAN / 2
            var falling = 0
            var wrapped = 0
            for (t in times(3_000.0, 40.0)) {
                for (index in 0 until Rain.DROPS) {
                    val drop = Rain.dropAt(index, t, centreX, centreZ)
                    assertTrue(drop.x >= west && drop.x < west + Rain.SPAN, "streak $index at $t: x ${drop.x}")
                    assertTrue(drop.z >= north && drop.z < north + Rain.SPAN, "streak $index at $t: z ${drop.z}")
                    assertTrue(drop.y > 0.0 && drop.y <= Rain.TOP, "streak $index at $t: y ${drop.y}")
                    val next = Rain.dropAt(index, t + 20.0, centreX, centreZ)
                    when {
                        next.y < drop.y -> {
                            falling += 1
                            // Down by about the speed of rain, and a little east with the wind, nothing more.
                            val fall = drop.y - next.y
                            assertTrue(fall in 20 * Rain.FALL * 0.85..20 * Rain.FALL * 1.15, "streak $index fell $fall")
                            assertEquals(drop.z, next.z, "streak $index moved north or south")
                        }
                        else -> wrapped += 1
                    }
                }
            }
            // A streak goes back to the top only once a fall, about every second.
            assertTrue(wrapped < falling / 40, "$wrapped went back up, $falling fell")
        }
    }

    @Test
    fun `rain stays put in the town as the box of sky moves with the camera`() {
        // Moving the box by a little leaves every streak still in it exactly where it was.
        for (index in 0 until Rain.DROPS) {
            val here = Rain.dropAt(index, 500.0, 20.0, 13.0)
            val there = Rain.dropAt(index, 500.0, 21.0, 13.5)
            val inBoth = here.x >= 21.0 - Rain.SPAN / 2 && here.z >= 13.5 - Rain.SPAN / 2
            if (!inBoth) continue
            assertEquals(here.x, there.x, 1e-9, "streak $index")
            assertEquals(here.y, there.y, 1e-9, "streak $index")
            assertEquals(here.z, there.z, 1e-9, "streak $index")
        }
    }

    @Test
    fun `the rain written for drawing is the rain worked out, streaks and splashes`() {
        val numbers = Float32Array((Rain.DROPS + Rain.SPLASHES) * 16)
        Rain.write(1_234.0, 24.3, 13.5, numbers)
        for (index in listOf(0, 17, Rain.DROPS - 1)) {
            val drop = Rain.dropAt(index, 1_234.0, 24.3, 13.5)
            val at = index * 16
            // The streak's middle: half a streak under its top, half a lean east of it.
            assertEquals(drop.x + Rain.SLANT * Rain.STREAK / 2, numbers[at + 12].toDouble(), 1e-4)
            assertEquals(drop.y - Rain.STREAK / 2, numbers[at + 13].toDouble(), 1e-4)
            assertEquals(drop.z, numbers[at + 14].toDouble(), 1e-4)
            assertEquals(Rain.STREAK, numbers[at + 5].toDouble(), 1e-6)
        }
        for (index in 0 until Rain.SPLASHES) {
            val splash = Rain.splashAt(index, 1_234.0, 24.3, 13.5)
            val at = (Rain.DROPS + index) * 16
            assertEquals(splash.x + splash.width / 2, numbers[at + 12].toDouble(), 1e-4)
            assertEquals(splash.width, numbers[at].toDouble(), 1e-6)
        }
    }

    @Test
    fun `splashes flash on the ground or the sea for a moment, each time somewhere else`() {
        var shown = 0
        val spots = mutableSetOf<Pair<Int, Int>>()
        for (t in times(Rain.SPLASH_MILLIS * 6, 25.0)) {
            for (index in 0 until Rain.SPLASHES) {
                val splash = Rain.splashAt(index, t, 24.3, 13.5)
                assertTrue(splash.width <= Rain.SPLASH_SIZE * 1.4 + 1e-9, "$splash")
                assertTrue(splash.y == Rain.SPLASH_HEIGHT || splash.z + splash.depth / 2 < 0.0, "a splash in the air: $splash")
                assertTrue(splash.x >= 24.3 - Rain.SPAN / 2 - 1.0 && splash.x <= 24.3 + Rain.SPAN / 2, "$splash")
                if (splash.width > 0.01) {
                    shown += 1
                    spots += splash.x.toInt() to splash.z.toInt()
                }
            }
        }
        val share = shown.toDouble() / (Rain.SPLASHES * times(Rain.SPLASH_MILLIS * 6, 25.0).size)
        assertTrue(share in Rain.SPLASH_SHOWS * 0.7..Rain.SPLASH_SHOWS * 1.3, "shown $share of the time")
        assertTrue(spots.size > Rain.SPLASHES * 3, "splashes keep to the same spots: ${spots.size}")
    }

    @Test
    fun `puddles lie flat on the street, the pavements or the seawall, just over the stone`() {
        for (puddle in Rain.puddles) {
            assertTrue(puddle.height < 0.02 && puddle.y in 0.03..0.07, "$puddle")
            val onStreet = puddle.z >= Neighbourhood.STREET_NORTH && puddle.z + puddle.depth <= Neighbourhood.STREET_SOUTH
            val onSeawall = puddle.z >= 0.0 && puddle.z + puddle.depth <= 2.0
            assertTrue(onStreet || onSeawall, "$puddle")
        }
    }

    @Test
    fun `the clouds grey with the weather`() {
        assertEquals(0xFFFFFF, Clouds.colour(Weather.SUNNY))
        assertTrue(DayLight.lightness(Clouds.colour(Weather.RAINY)) < DayLight.lightness(Clouds.colour(Weather.CLOUDY)))
        assertTrue(DayLight.lightness(Clouds.colour(Weather.CLOUDY)) < DayLight.lightness(Clouds.colour(Weather.SUNNY)))
    }

    @Test
    fun `the train's rumble is louder the nearer it is, and silent while it stands or is away`() {
        // Loudness falls all the way with distance.
        val far = (0..300).map { SkyTrainRun.loudnessAt(it.toDouble()) }
        assertEquals(1.0, far.first())
        assertTrue(far.zipWithNext().all { (a, b) -> b < a })
        assertEquals(0.5, SkyTrainRun.loudnessAt(SkyTrainRun.RUMBLE_NEAR), 1e-9)
        assertTrue(SkyTrainRun.loudnessAt(150.0) < 0.02)

        // An ear above the station's plaza, where the camera often is.
        val ear = Point(27.0, 12.0, 20.0)
        for (t in times(SkyTrainRun.PERIOD, 50.0)) {
            val level = SkyTrainRun.rumbleAt(t, ear)
            assertTrue(level in 0.0..1.0, "at $t: $level")
            if (SkyTrainRun.speedAt(t) == 0.0) assertEquals(0.0, level, "at $t it stands or is away, yet rumbles")
            if (!SkyTrainRun.isShownAt(t)) assertEquals(0.0, level, "at $t it is away, yet rumbles")
        }
        // Once under way, coming in it grows louder as it comes nearer, and pulling out it fades as it goes.
        val full = SkyTrainRun.TOP_SPEED * SkyTrainRun.UNDER_WAY
        val coming = times(SkyTrainRun.PERIOD, 50.0).filter { t ->
            SkyTrainRun.shiftAt(t) < 0 && SkyTrainRun.speedAt(t) >= full
        }
        val going = times(SkyTrainRun.PERIOD, 50.0).filter { t ->
            SkyTrainRun.shiftAt(t) > 0 && SkyTrainRun.speedAt(t) >= full
        }
        assertTrue(coming.size > 20 && going.size > 20)
        for ((a, b) in coming.zipWithNext()) {
            assertTrue(SkyTrainRun.distanceTo(b, ear) < SkyTrainRun.distanceTo(a, ear))
            assertTrue(SkyTrainRun.rumbleAt(b, ear) >= SkyTrainRun.rumbleAt(a, ear), "coming in at $b it got quieter")
        }
        for ((a, b) in going.zipWithNext()) {
            assertTrue(SkyTrainRun.rumbleAt(b, ear) <= SkyTrainRun.rumbleAt(a, ear), "pulling out at $b it got louder")
        }
        // Loudest close by: well heard as it passes the plaza, hardly at all far down the line.
        val loudest = times(SkyTrainRun.PERIOD, 50.0).maxOf { SkyTrainRun.rumbleAt(it, ear) }
        assertTrue(loudest > 0.3, "loudest $loudest")
        val distant = times(SkyTrainRun.PERIOD, 50.0).filter { SkyTrainRun.distanceTo(it, ear) > 120 }
        assertTrue(distant.isNotEmpty() && distant.all { SkyTrainRun.rumbleAt(it, ear) < 0.02 })
    }

    @Test
    fun `the train's speed matches how far it goes`() {
        for (t in times(SkyTrainRun.PERIOD, 37.0)) {
            val moved = abs(SkyTrainRun.shiftAt(t + 1) - SkyTrainRun.shiftAt(t - 1)) / 2 * 1_000
            val boundary = listOf(SkyTrainRun.DWELL, SkyTrainRun.DWELL + SkyTrainRun.LEAVE).any { abs(t - it) < 2 }
            if (!boundary) assertEquals(moved, SkyTrainRun.speedAt(t), 0.05, "at $t")
            assertTrue(SkyTrainRun.speedAt(t) <= SkyTrainRun.TOP_SPEED + 1e-9)
        }
    }

    @Test
    fun `the fire crackles only after dark, and only near it`() {
        assertEquals(1.0, Campfire.nearness(0.0))
        assertEquals(0.0, Campfire.nearness(Campfire.HEARD_WITHIN))
        assertEquals(0.0, Campfire.nearness(30.0))
        val near = (0..100).map { Campfire.nearness(it / 10.0) }
        assertTrue(near.zipWithNext().all { (a, b) -> b <= a })
        // From the bench, at night, it is well heard; by day, or from the street, not at all.
        val bench = distanceBetween(Neighbourhood.benchSeat, Neighbourhood.campfire)
        assertTrue(Campfire.crackle(Campfire.nearness(bench), 1.0) > 0.5)
        assertEquals(0.0, Campfire.crackle(Campfire.nearness(bench), 0.0))
        val street = distanceBetween(Neighbourhood.venue(Place.CAFE).door, Neighbourhood.campfire)
        assertEquals(0.0, Campfire.crackle(Campfire.nearness(street), 1.0))
    }
}
