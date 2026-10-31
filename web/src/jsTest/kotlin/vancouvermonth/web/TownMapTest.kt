package vancouvermonth.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import vancouvermonth.core.Action

class TownMapTest {
    private val buildings = TownMap.buildings

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
}
