package vancouvermonth.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `no two names cover each other, even at their widest, on the narrowest phones`() {
        // As style.css draws them: bold, 11px with 4px each side under 360 points wide, else 12px with 6px.
        // The town is the screen's width less 16 points each side and its 1-point border.
        for ((screen, font, padding) in listOf(Triple(320, 11.0, 4.0), Triple(360, 12.0, 6.0))) {
            val frameWidth = screen - 2 * 16 - 2.0
            val frameHeight = frameWidth / TownCamera.ASPECT
            // Each name is centred over its anchor and sits on it, as the CSS places it.
            val boxes = buildings.map { building ->
                val anchor = TownCamera.framePoint(building.labelAnchor)
                val width = widestName(building.place.label, font, padding)
                val left = anchor.left * frameWidth - width / 2
                val bottom = anchor.top * frameHeight
                NameBox(building.place.label, left, left + width, bottom - font * LINE_HEIGHT, bottom)
            }
            for (box in boxes) {
                assertTrue(box.left >= 0 && box.right <= frameWidth && box.top >= 0, "$box is cut at $screen points")
            }
            for ((index, first) in boxes.withIndex()) {
                for (second in boxes.drop(index + 1)) {
                    val covers = first.left < second.right && second.left < first.right &&
                        first.top < second.bottom && second.top < first.bottom
                    assertFalse(covers, "$first and $second at $screen points")
                }
            }
        }
    }

    /**
     * The most room [name] can take. The system font draws a Hangul syllable about 0.86em wide, so 1.15em is
     * a safe upper bound for the font a phone may use instead, and a space is under 0.5em.
     */
    private fun widestName(name: String, font: Double, padding: Double): Double =
        2 * padding + name.count { it != ' ' } * font * 1.15 + name.count { it == ' ' } * font * 0.5

    private data class NameBox(val name: String, val left: Double, val right: Double, val top: Double, val bottom: Double)

    private companion object {
        /** style.css gives the names a line height of 1.6. */
        const val LINE_HEIGHT = 1.6
    }
}
