package vancouvermonth.web

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The map data packed to make the page smaller reads back exactly as the data the game reads now, value for value. */
class PackedMapDataTest {
    @Test
    fun `the town's rows as runs spell out the same squares`() {
        assertEquals(VancouverMapData.rows, VancouverMapData.packedRows.map(::unpackRuns))
    }

    @Test
    fun `the town's shoreline and park read back as the same points`() {
        assertEquals(VancouverMapData.shoreLines.map(::pointsOf), VancouverMapData.packedShoreLines.map(::unpackPoints))
        assertEquals(pointsOf(VancouverMapData.PARK), unpackPoints(VancouverMapData.PACKED_PARK))
    }

    @Test
    fun `the wide map's streets read back as the same points`() {
        assertEquals(WideMapData.streets.map(::pointsOf), WideMapData.packedStreets.map(::unpackPoints))
    }

    @Test
    fun `the wide map's blocks read back as the same numbers and the same lots`() {
        val old = WideMapData.blocks.flatMap { it.split(' ') }.filter { it.isNotEmpty() }.map { block ->
            block.split(',').map(String::toDouble)
        }
        val packed = unpackNumbers(WideMapData.packedBlocks, 5)
        assertEquals(1703, old.size)
        assertEquals(old.size, packed.size)
        for ((before, now) in old.zip(packed)) {
            val (x, z, width, depth, degrees) = now
            assertEquals(before, listOf(x / 10.0, z / 10.0, width / 10.0, depth / 10.0, degrees.toDouble()))
        }
        val lots = packed.map { (x, z, width, depth, degrees) ->
            TurnedLot(Spot(x / 10.0, z / 10.0), width / 10.0, depth / 10.0, degrees * PI / 180)
        }
        assertEquals(WideVancouver.blocks, lots)
        assertTrue(lots.all { it.width > 0 && it.depth > 0 })
    }
}
