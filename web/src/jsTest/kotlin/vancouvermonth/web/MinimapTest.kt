package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import vancouvermonth.web.MinimapProjection.Companion.AREA_HEIGHT
import vancouvermonth.web.MinimapProjection.Companion.AREA_WIDTH

class MinimapTest {
    /** The whole map, shown larger, on a 375-point-wide phone: four fifths of it across, as tall as the area's shape asks. */
    private val map = MinimapProjection(300.0, 300.0 * AREA_HEIGHT / AREA_WIDTH)

    /** The small map on the same phone: a quarter of it across, the window round the character. */
    private val window = MinimapProjection(93.75, 93.75 * 267 / 239, Minimap.WINDOW_ACROSS)

    /**
     * The ways real north and real east lie on the game map, one square long, worked out as the map's own script turns
     * the city (tools/vancouver_map/build_map.py): counter-clockwise by the turn, with z running south.
     */
    private val turn = VancouverMapData.TURN_DEGREES * PI / 180
    private val northX = -sin(turn)
    private val northZ = -cos(turn)
    private val eastX = cos(turn)
    private val eastZ = -sin(turn)

    /** Every square of the wide map the character could stand on: all but the water. */
    private val standable = Neighbourhood.map.let { ground ->
        (0 until ground.rows).flatMap { z -> (0 until ground.columns).map { x -> Cell(x, z) } }
            .filter { ground.terrainAt(it) != Terrain.WATER }
    }

    private fun cornersOf(cell: Cell): List<Pair<Double, Double>> = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)
        .map { (across, down) -> (cell.x + across).toDouble() to (cell.z + down).toDouble() }

    private val shown = mutableListOf<HTMLElement>()

    @AfterTest
    fun removeMaps() {
        for (element in shown) element.remove()
    }

    @Test
    fun `real north is up on the map and real east to the right, at one scale`() {
        for (venue in Neighbourhood.venues) {
            val x = venue.door.x
            val z = venue.door.z
            for (step in listOf(1.0, 10.0, 40.0)) {
                // A spot due north, in the real city, is drawn straight above; one due east, straight to the right.
                val northLeft = map.left(x + northX * step, z + northZ * step)
                val northTop = map.top(x + northX * step, z + northZ * step)
                assertEquals(map.left(x, z), northLeft, 1e-9, "${venue.place} north $step")
                assertEquals(map.top(x, z) - step * map.scale, northTop, 1e-9, "${venue.place} north $step")
                val eastLeft = map.left(x + eastX * step, z + eastZ * step)
                val eastTop = map.top(x + eastX * step, z + eastZ * step)
                assertEquals(map.left(x, z) + step * map.scale, eastLeft, 1e-9, "${venue.place} east $step")
                assertEquals(map.top(x, z), eastTop, 1e-9, "${venue.place} east $step")
            }
        }
        // As on a paper map of the city: 회사 by the inlet lies above 카페 on False Creek, and 내 방 in the West End left
        // of 코워킹 at the east end and of 도서관 in the middle of downtown.
        fun middleOf(place: Place): Pair<Double, Double> {
            val venue = Neighbourhood.venue(place)
            val x = venue.x + venue.width / 2.0
            val z = venue.z + venue.depth / 2.0
            return map.left(x, z) to map.top(x, z)
        }
        assertTrue(middleOf(Place.OFFICE).second < middleOf(Place.CAFE).second)
        assertTrue(middleOf(Place.HOME).first < middleOf(Place.COWORKING).first)
        assertTrue(middleOf(Place.HOME).first < middleOf(Place.LIBRARY).first)
    }

    @Test
    fun `the map shows all the ground the character can stand on, with a little round it`() {
        val projection = MinimapProjection(AREA_WIDTH.toDouble(), AREA_HEIGHT.toDouble())
        assertEquals(1.0, projection.scale, 1e-9)
        var left = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        var top = Double.MAX_VALUE
        var bottom = -Double.MAX_VALUE
        for (cell in standable) {
            for ((x, z) in cornersOf(cell)) {
                left = minOf(left, projection.left(x, z))
                right = maxOf(right, projection.left(x, z))
                top = minOf(top, projection.top(x, z))
                bottom = maxOf(bottom, projection.top(x, z))
            }
        }
        // Six to seven squares of margin on each side, so the map is not mostly empty corners.
        for (margin in listOf(left, projection.width - right, top, projection.height - bottom)) {
            assertTrue(margin in 6.0..7.0, "a margin of $margin squares")
        }
        // About as tall as wide, as the wide map's land turned north up is: downtown to Burnaby runs down and right.
        assertTrue(AREA_HEIGHT.toDouble() / AREA_WIDTH in 0.85..1.15, "$AREA_WIDTH by $AREA_HEIGHT")
    }

    @Test
    fun `the small map shows the ground round the character, north up, the character in its middle`() {
        for (place in listOf(Place.HOME, Place.OFFICE)) {
            val door = Neighbourhood.venue(place).door
            window.centreOn(door.x, door.z)
            assertEquals(window.width / 2, window.left(door.x, door.z), 1e-9)
            assertEquals(window.height / 2, window.top(door.x, door.z), 1e-9)
            // A hundred squares across, the same scale down; real north straight up.
            assertEquals(window.width / Minimap.WINDOW_ACROSS, window.scale, 1e-9)
            assertEquals(window.top(door.x, door.z) - 10 * window.scale, window.top(door.x + northX * 10, door.z + northZ * 10), 1e-9)
            assertEquals(window.left(door.x, door.z), window.left(door.x + northX * 10, door.z + northZ * 10), 1e-9)
        }
        // In Burnaby too: 내 방 for zone 2 in the middle, and the big park's station on the map near it.
        val home = Neighbourhood.home(2).door
        window.centreOn(home.x, home.z)
        val station = TransitLines.stations[7].door
        assertTrue(window.left(station.x, station.z) in 0.0..window.width && window.top(station.x, station.z) in 0.0..window.height)
        // Downtown is far off it.
        val office = Neighbourhood.venue(Place.OFFICE).door
        assertFalse(window.left(office.x, office.z) in 0.0..window.width && window.top(office.x, office.z) in 0.0..window.height)
    }

    @Test
    fun `the whole map on a tap names all five places, none over another, in every zone`() {
        for (zone in 1..3) {
            val minimap = Minimap(HomeSite.of(zone))
            val element = minimap.element
            minimap.isLarge = true
            // As large as on a 375-point phone: four fifths of it.
            element.style.width = "300px"
            document.body!!.appendChild(element)
            shown += element
            minimap.measure()
            val office = Neighbourhood.venue(Place.OFFICE).door
            minimap.draw(office.x, office.z, 0.0, office.x, office.z + 20.0, office.x, office.z, 0.8, marked = null)
            assertEquals(5, minimap.namesDrawn, "zone $zone")
            val boxes = minimap.nameBoxes
            for ((index, a) in boxes.withIndex()) {
                assertTrue(a[0] >= 0.0 && a[1] >= 0.0 && a[2] <= element.clientWidth && a[3] <= element.clientHeight, "zone $zone: $a")
                for (b in boxes.drop(index + 1)) {
                    val apart = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
                    assertTrue(apart, "zone $zone: $a over $b")
                }
            }
        }
    }

    @Test
    fun `off the small map, 내 방 and 회사 show as arrows on its edge`() {
        val minimap = Minimap(HomeSite.of(2))
        document.body!!.appendChild(minimap.element)
        shown += minimap.element
        minimap.measure()
        // At 내 방 in Burnaby, 회사 downtown is off the small map; 내 방 is on it.
        val home = Neighbourhood.home(2).door
        minimap.draw(home.x, home.z, 0.0, home.x, home.z + 20.0, home.x, home.z, 0.8, marked = null)
        assertEquals(setOf(Place.OFFICE), minimap.pointedTo)
        // Downtown, 내 방 in Burnaby is off it, and 회사 on it.
        val office = Neighbourhood.venue(Place.OFFICE).door
        minimap.draw(office.x, office.z, 0.0, office.x, office.z + 20.0, office.x, office.z, 0.8, marked = null)
        assertEquals(setOf(Place.HOME), minimap.pointedTo)
        // The whole map, shown larger, has everything on it and no arrows.
        minimap.isLarge = true
        minimap.measure()
        minimap.draw(office.x, office.z, 0.0, office.x, office.z + 20.0, office.x, office.z, 0.8, marked = null)
        assertEquals(emptySet(), minimap.pointedTo)
        assertEquals("$AREA_WIDTH / $AREA_HEIGHT", minimap.element.style.getPropertyValue("aspect-ratio"))
    }

    @Test
    fun `wherever the character can stand, it is on the whole map`() {
        val narrowest = MinimapProjection(80.0, 80.0 * AREA_HEIGHT / AREA_WIDTH)
        for (projection in listOf(map, narrowest)) {
            for (cell in standable) {
                for ((x, z) in cornersOf(cell)) {
                    val left = projection.left(x, z)
                    val top = projection.top(x, z)
                    assertTrue(left in 0.0..projection.width && top in 0.0..projection.height, "$cell at $left, $top")
                }
            }
        }
    }

    @Test
    fun `the north shore's forest on the map is where the town lays it`() {
        // The town's flat band of dark forest across the inlet, in front of the mountains.
        val band = Neighbourhood.ground.filter { it.y == -0.5 && it.height == 1.2 && it.colour == 0x4D7A5E }
        assertTrue(band.size > 10, "${band.size} stretches")
        for (box in band) {
            val middle = box.x + box.width / 2
            assertTrue(Neighbourhood.isNorthShore(middle, box.z + box.depth / 2), "$box")
            assertTrue(Neighbourhood.isNorthShore(box.x + 0.01, box.z + 0.01), "$box")
            assertFalse(Neighbourhood.isNorthShore(middle, box.z - 0.01), "north of $box")
            assertFalse(Neighbourhood.isNorthShore(middle, box.z + box.depth + 0.01), "south of $box")
        }
        // Nothing on the grid itself, where the town walks.
        assertFalse(RealVancouver.map.cellsOf(Terrain.LAND).any { Neighbourhood.isNorthShore(it.x + 0.5, it.z + 0.5) })
    }

    @Test
    fun `all five places lie inside the map, each dot clear of the others`() {
        val dots = Neighbourhood.venues.map { venue ->
            val x = venue.x + venue.width / 2.0
            val z = venue.z + venue.depth / 2.0
            val left = map.left(x, z)
            val top = map.top(x, z)
            assertTrue(left in 4.0..map.width - 4.0 && top in 4.0..map.height - 4.0, "${venue.place} at $left, $top")
            left to top
        }
        for ((index, a) in dots.withIndex()) {
            for (b in dots.drop(index + 1)) assertTrue(hypot(a.first - b.first, a.second - b.second) > 8.0, "$a, $b")
        }
    }

    @Test
    fun `the character's arrow points the way it faces, turned as the map is`() {
        // The town turns a figure to face the way (x, z) with the heading atan2(x, z): 0 faces south on the game map.
        val north = atan2(northX, northZ)
        val east = atan2(eastX, eastZ)
        val ways = listOf(
            north to (0.0 to -1.0),
            east to (1.0 to 0.0),
            north + PI to (0.0 to 1.0),
            east + PI to (-1.0 to 0.0),
        )
        for ((heading, way) in ways) {
            assertEquals(way.first, MinimapProjection.across(heading), 1e-9, "$heading")
            assertEquals(way.second, MinimapProjection.down(heading), 1e-9, "$heading")
        }
        // Whichever way it faces, the arrow points the way a step that way is drawn.
        val door = Neighbourhood.venue(Place.HOME).door
        for (turns in 0 until 16) {
            val heading = turns * PI / 8
            val left = map.left(door.x + sin(heading), door.z + cos(heading)) - map.left(door.x, door.z)
            val top = map.top(door.x + sin(heading), door.z + cos(heading)) - map.top(door.x, door.z)
            assertEquals(left / map.scale, MinimapProjection.across(heading), 1e-9, "$heading")
            assertEquals(top / map.scale, MinimapProjection.down(heading), 1e-9, "$heading")
        }
    }

    @Test
    fun `the larger map is never taller than the room under it`() {
        val minimap = Minimap()
        document.body!!.appendChild(minimap.element)
        shown += minimap.element
        // As on a phone turned on its side: 100 pixels under the map's top.
        minimap.roomBelow = 100.0
        minimap.isLarge = true
        assertEquals(160.0, minimap.element.getBoundingClientRect().width, 0.5)
        // With room to spare, four fifths of the page, at most 480 pixels.
        minimap.roomBelow = 280.0
        assertEquals(280.0 * AREA_WIDTH / AREA_HEIGHT, minimap.element.getBoundingClientRect().width, 0.5)
        minimap.roomBelow = 2_000.0
        assertEquals(480.0, minimap.element.getBoundingClientRect().width, 0.5)
        minimap.isLarge = false
        assertTrue(minimap.element.getBoundingClientRect().width <= 100.0)
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
    fun `the map is painted north up, with the north shore, the character's arrow, the places and north marked`() {
        val minimap = Minimap()
        val element = minimap.element
        // The whole map, as a tap shows it.
        minimap.isLarge = true
        // One CSS pixel a square, so each square can be looked at.
        element.style.position = "fixed"
        element.style.width = "${AREA_WIDTH}px"
        element.style.height = "${AREA_HEIGHT}px"
        element.style.border = "0"
        document.body!!.appendChild(element)
        shown += element
        minimap.measure()
        val map = MinimapProjection(AREA_WIDTH.toDouble(), AREA_HEIGHT.toDouble())
        // The character stands where its arrow's middle falls in the middle of a pixel, facing real north.
        val standLeft = 60.5
        val standTop = 120.5
        val x = MinimapProjection.gridX(standLeft + MinimapProjection.AREA_LEFT, standTop + MinimapProjection.AREA_TOP)
        val z = MinimapProjection.gridZ(standLeft + MinimapProjection.AREA_LEFT, standTop + MinimapProjection.AREA_TOP)
        val facingNorth = atan2(northX, northZ)
        val home = Neighbourhood.venue(Place.HOME).door
        minimap.draw(x, z, facingNorth, home.x - 10.0, home.z + 10.0, home.x, home.z, 0.8, marked = null)
        val canvas = element.querySelector("canvas") as HTMLCanvasElement
        val context = canvas.getContext("2d") as CanvasRenderingContext2D
        val ratio = canvas.width.toDouble() / AREA_WIDTH
        fun colourAt(left: Double, top: Double): Int {
            val pixel = context.getImageData(left * ratio, top * ratio, 1.0, 1.0).data.asDynamic()
            return (pixel[0] as Int shl 16) or (pixel[1] as Int shl 8) or pixel[2] as Int
        }
        fun colourOf(x: Double, z: Double) = colourAt(map.left(x, z), map.top(x, z))
        // The inlet's open water north of the park, the middle of the big park, and the land the town lays past the
        // grid's south edge, where the land goes on.
        assertEquals(0x6FB3D9, colourOf(40.5, 5.5))
        assertEquals(0x8FC165, colourOf(50.5, 60.5))
        assertEquals(0xEDE8DC, colourOf(130.5, Neighbourhood.ROWS + 5.5))
        // The north shore's forest across the inlet, in the top right, as the town lays it.
        assertTrue(Neighbourhood.isNorthShore(105.5, -33.4))
        assertEquals(0x4D7A5E, colourOf(105.5, -33.4))
        // The character's arrow, in its own gold, where the character stands, pointing straight up.
        assertEquals(standLeft, minimap.dotLeft, 1e-9)
        assertEquals(standTop, minimap.dotTop, 1e-9)
        assertEquals(0xFFD23F, colourAt(standLeft, standTop - 1.0))
        // The north arrow in ink at the top right, as large as the map is.
        val large = 1.6
        assertEquals(0x3B2F25, colourAt(AREA_WIDTH - Minimap.NORTH_INSET * large + 0.5, (Minimap.NORTH_TOP + 3.5) * large))
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
        assertEquals(0xC0584A, colourOf(homeX, homeZ))
        assertEquals(0x3B2F25, colourOf(officeX, officeZ))
        assertEquals(0x3B2F25, colourOf(libraryX, libraryZ))
        minimap.draw(50.5, 60.5, 0.0, 40.5, 70.5, 50.5, 60.5, 0.8, marked = Place.OFFICE)
        assertEquals(0x1D6B57, colourOf(officeX, officeZ))
        assertEquals(0x3B2F25, colourOf(libraryX, libraryZ))
    }
}
