package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement

class MinimapTest {
    /** The small map on a 375-point-wide phone: 112 points across, as tall as the grid's shape asks. */
    private val map = MinimapProjection(112.0, 112.0 * Neighbourhood.ROWS / Neighbourhood.COLUMNS)

    private val shown = mutableListOf<HTMLElement>()

    @AfterTest
    fun removeMaps() {
        for (element in shown) element.remove()
    }

    @Test
    fun `the whole grid fills the map, east to the right and south down, never turned`() {
        assertEquals(0.0, map.left(0.0))
        assertEquals(0.0, map.top(0.0))
        assertEquals(map.width, map.left(Neighbourhood.COLUMNS.toDouble()), 1e-9)
        assertEquals(map.height, map.top(Neighbourhood.ROWS.toDouble()), 1e-9)
        val home = Neighbourhood.venue(Place.HOME)
        val coworking = Neighbourhood.venue(Place.COWORKING)
        val office = Neighbourhood.venue(Place.OFFICE)
        val cafe = Neighbourhood.venue(Place.CAFE)
        // 내 방 in the West End lies left of 코워킹 at the east end, and 회사 by the inlet above the café by the creek.
        assertTrue(map.left(home.x.toDouble()) < map.left(coworking.x.toDouble()))
        assertTrue(map.top(office.z.toDouble()) < map.top(cafe.z.toDouble()))
    }

    @Test
    fun `all five places lie inside the map, each dot clear of the others`() {
        val dots = Neighbourhood.venues.map { venue ->
            val left = map.left(venue.x + venue.width / 2.0)
            val top = map.top(venue.z + venue.depth / 2.0)
            assertTrue(left in 4.0..map.width - 4.0 && top in 4.0..map.height - 4.0, "${venue.place} at $left, $top")
            left to top
        }
        for ((index, a) in dots.withIndex()) {
            for (b in dots.drop(index + 1)) assertTrue(hypot(a.first - b.first, a.second - b.second) > 8.0, "$a, $b")
        }
    }

    @Test
    fun `the character's arrow is where the character stands, pointing the way it faces`() {
        assertEquals(map.width / 2, map.left(Neighbourhood.COLUMNS / 2.0), 1e-9)
        assertEquals(map.height / 2, map.top(Neighbourhood.ROWS / 2.0), 1e-9)
        val door = Neighbourhood.venue(Place.HOME).door
        assertEquals(door.x * 112.0 / Neighbourhood.COLUMNS, map.left(door.x), 1e-9)
        // Facing south, as at 내 방's door, it points down the map; east, to the right; north, up; west, to the left.
        val ways = listOf(0.0 to (0.0 to 1.0), PI / 2 to (1.0 to 0.0), PI to (0.0 to -1.0), -PI / 2 to (-1.0 to 0.0))
        for ((heading, way) in ways) {
            assertEquals(way.first, MinimapProjection.across(heading), 1e-9, "$heading")
            assertEquals(way.second, MinimapProjection.down(heading), 1e-9, "$heading")
        }
    }

    @Test
    fun `a screen reader hears the place the character is nearest`() {
        for (venue in Neighbourhood.venues) {
            assertEquals(venue.place, Minimap.nearestPlace(venue.door.x, venue.door.z))
            assertEquals(venue.place, Minimap.nearestPlace(venue.talkSpot.x, venue.talkSpot.z))
        }
        // Deep in the big park, west of everything, 내 방 is nearest.
        assertEquals(Place.HOME, Minimap.nearestPlace(50.5, 60.5))
        val minimap = Minimap()
        document.body!!.appendChild(minimap.element)
        shown += minimap.element
        minimap.measure()
        val cafe = Neighbourhood.venue(Place.CAFE).door
        minimap.draw(cafe.x, cafe.z, 0.0, cafe.x, cafe.z + 20.0, cafe.x, cafe.z, 0.8, marked = null)
        assertEquals("지도: 카페 근처", minimap.element.getAttribute("aria-label"))
        assertEquals("img", minimap.element.getAttribute("role"))
    }

    @Test
    fun `the map is painted from the grid, with the character's arrow and the places marked in their colours`() {
        val minimap = Minimap()
        val element = minimap.element
        // One CSS pixel a square, so each square can be looked at.
        element.style.position = "fixed"
        element.style.width = "${Neighbourhood.COLUMNS}px"
        element.style.height = "${Neighbourhood.ROWS}px"
        element.style.border = "0"
        document.body!!.appendChild(element)
        shown += element
        minimap.measure()
        val home = Neighbourhood.venue(Place.HOME).door
        minimap.draw(home.x, home.z, 0.0, home.x - 10.0, home.z + 10.0, home.x, home.z, 0.8, marked = null)
        val canvas = element.querySelector("canvas") as HTMLCanvasElement
        val context = canvas.getContext("2d") as CanvasRenderingContext2D
        val ratio = canvas.width.toDouble() / Neighbourhood.COLUMNS
        fun colourAt(x: Double, z: Double): Int {
            val pixel = context.getImageData(x * ratio, z * ratio, 1.0, 1.0).data.asDynamic()
            return (pixel[0] as Int shl 16) or (pixel[1] as Int shl 8) or pixel[2] as Int
        }
        // The inlet's open water in the top left corner, and the middle of the big park.
        assertEquals(0x6FB3D9, colourAt(3.5, 3.5))
        assertEquals(0x8FC165, colourAt(50.5, 60.5))
        // The character's arrow, in its own gold, where the character stands.
        assertEquals(home.x, minimap.dotLeft, 1e-9)
        assertEquals(home.z, minimap.dotTop, 1e-9)
        assertEquals(0xFFD23F, colourAt(home.x, home.z + 2.0))
        // 내 방 in its roof's red, the others dark; on an interview day 회사 takes the colour its name does.
        fun middleOf(place: Place): Pair<Double, Double> {
            val venue = Neighbourhood.venue(place)
            return venue.x + venue.width / 2.0 to venue.z + venue.depth / 2.0
        }
        val (officeX, officeZ) = middleOf(Place.OFFICE)
        val (libraryX, libraryZ) = middleOf(Place.LIBRARY)
        val (homeX, homeZ) = middleOf(Place.HOME)
        // The character away in the park, so nothing covers the places.
        minimap.draw(50.5, 60.5, 0.0, 40.5, 70.5, 50.5, 60.5, 0.8, marked = null)
        assertEquals(0xC0584A, colourAt(homeX, homeZ))
        assertEquals(0x3B2F25, colourAt(officeX, officeZ))
        assertEquals(0x3B2F25, colourAt(libraryX, libraryZ))
        minimap.draw(50.5, 60.5, 0.0, 40.5, 70.5, 50.5, 60.5, 0.8, marked = Place.OFFICE)
        assertEquals(0x1D6B57, colourAt(officeX, officeZ))
        assertEquals(0x3B2F25, colourAt(libraryX, libraryZ))
    }
}
