package vancouvermonth.web

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import vancouvermonth.core.Action

class TownMapTest {
    private val buildings = TownMap.buildings

    /** How tall the character is drawn, a bounce included. */
    private val characterHeight =
        TownMap.character.maxOf { it.y + it.height } * TownMap.CHARACTER_SCALE + Walk.HOP_HEIGHT

    @Test
    fun `each action goes to its own building, and an interview to the office`() {
        assertEquals(Place.CAFE, placeFor(Action.WORK))
        assertEquals(Place.LIBRARY, placeFor(Action.STUDY_ENGLISH))
        assertEquals(Place.COWORKING, placeFor(Action.PRACTISE_CODING))
        assertEquals(Place.OFFICE, placeFor(Action.APPLY))
        assertEquals(Place.HOME, placeFor(Action.REST))
        assertEquals(Place.OFFICE, INTERVIEW_PLACE)
    }

    @Test
    fun `the town has one building for each place, west to east, with its name`() {
        assertEquals(Place.entries, buildings.map { it.place })
        assertEquals(listOf("내 방", "카페", "도서관", "코워킹", "회사"), buildings.map { it.place.label })
    }

    @Test
    fun `the buildings stand apart in one row, inside the town and behind the pavement`() {
        for (building in buildings) {
            assertTrue(building.x >= 0 && building.x + building.width <= TownMap.WIDTH, "${building.place} is outside")
            assertTrue(building.z + building.depth < TownMap.PAVEMENT_Z, "${building.place} stands on the pavement")
        }
        for ((west, east) in buildings.zipWithNext()) {
            assertTrue(west.x + west.width < east.x, "${west.place} touches ${east.place}")
        }
    }

    @Test
    fun `each door spot is in front of its own building, between the wall and the pavement line`() {
        for (building in buildings) {
            val door = building.door
            assertTrue(door.x > building.x && door.x < building.x + building.width, "${building.place}")
            assertTrue(door.z > building.z + building.depth && door.z < TownMap.PAVEMENT_Z, "${building.place}")
        }
    }

    @Test
    fun `the camera looks at the town from up in the south, turned a little east`() {
        val eye = TownCamera.position
        val target = TownCamera.target
        assertTrue(eye.z > target.z && eye.y > target.y && eye.x > target.x)
        assertEquals(FramePoint(0.5, 0.5), TownCamera.framePoint(target))
    }

    @Test
    fun `every building, roof included, is inside the picture`() {
        for (building in buildings) {
            val west = building.x - 0.1
            val east = building.x + building.width + 0.1
            val north = building.z - 0.1
            val south = building.z + building.depth + 0.1
            val top = building.height + TownMap.ROOF
            for (x in listOf(west, east)) for (y in listOf(0.0, top)) for (z in listOf(north, south)) {
                val point = TownCamera.framePoint(Point(x, y, z))
                assertTrue(point.left in 0.0..1.0 && point.top in 0.0..1.0, "${building.place} corner at $point")
            }
        }
    }

    @Test
    fun `the character is in the picture all along a walk from one end of the town to the other`() {
        val walk = Walk(Place.HOME, Place.OFFICE)
        for (spot in walk.path) {
            for (height in listOf(0.0, characterHeight)) {
                val point = TownCamera.framePoint(Point(spot.x, height, spot.z))
                assertTrue(point.left in 0.0..1.0 && point.top in 0.0..1.0, "$spot at $point")
            }
        }
    }

    @Test
    fun `neighbouring names stay apart on the narrowest phone`() {
        // At 320 points wide the town is 288 by 162 points, and a name is up to about 44 points wide, 20 tall.
        val names = buildings.map { TownCamera.framePoint(it.labelAnchor) }
        for ((west, east) in names.zipWithNext()) {
            val apart = abs(east.left - west.left) * 288 >= 44 || abs(east.top - west.top) * 162 >= 20
            assertTrue(apart, "$west and $east")
        }
    }
}
