package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.BOTTOM
import org.w3c.dom.CENTER
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.TOP
import vancouvermonth.text.minimapLabel
import vancouvermonth.web.MinimapProjection.Companion.AREA_HEIGHT
import vancouvermonth.web.MinimapProjection.Companion.AREA_LEFT
import vancouvermonth.web.MinimapProjection.Companion.AREA_TOP
import vancouvermonth.web.MinimapProjection.Companion.AREA_WIDTH

/**
 * Where a spot of the town lies on a small map [width] by [height] pixels, laid with real north up. The game map is
 * the real city turned [VancouverMapData.TURN_DEGREES] counter-clockwise, so downtown's streets run along x and z; the
 * small map turns it back as much clockwise, so x east runs right and down, z south runs down and left. It shows all
 * the ground the character can stand on, [AREA_WIDTH] by [AREA_HEIGHT] squares, as large as fits, the same scale both
 * ways, in the middle.
 */
class MinimapProjection(val width: Double, val height: Double) {
    /** How many pixels a square of the grid takes on the map, across and down alike. */
    val scale = min(width / AREA_WIDTH, height / AREA_HEIGHT)

    /** Where the area's top left corner is, in pixels: the map's own, or a little in where the map is not its shape. */
    val areaLeft = (width - AREA_WIDTH * scale) / 2
    val areaTop = (height - AREA_HEIGHT * scale) / 2

    /** How far from the map's left edge the spot [x], [z] of the grid lies, in pixels. */
    fun left(x: Double, z: Double): Double = areaLeft + (turnedAcross(x, z) - AREA_LEFT) * scale

    /** How far down from the map's top edge the spot [x], [z] of the grid lies, in pixels. */
    fun top(x: Double, z: Double): Double = areaTop + (turnedDown(x, z) - AREA_TOP) * scale

    companion object {
        /** How far the game map is turned from real north, counter-clockwise, in radians. */
        val TURN = VancouverMapData.TURN_DEGREES * PI / 180
        private val COS = cos(TURN)
        private val SIN = sin(TURN)

        /** How many squares of water or land past the ground the character can stand on the map shows on each side. */
        private const val MARGIN = 6

        /**
         * The area the map shows, in squares of the grid turned north up: every square of the grid that is not water,
         * corner to corner, so the character's arrow is always on the map, with [MARGIN] squares more on each side.
         * Worked out once from the map.
         */
        val AREA_LEFT: Int
        val AREA_TOP: Int
        val AREA_WIDTH: Int
        val AREA_HEIGHT: Int

        init {
            val map = RealVancouver.map
            var left = Double.MAX_VALUE
            var right = -Double.MAX_VALUE
            var top = Double.MAX_VALUE
            var bottom = -Double.MAX_VALUE
            for (z in 0 until map.rows) {
                for (x in 0 until map.columns) {
                    if (map.terrainAt(x, z) == Terrain.WATER) continue
                    for (corner in 0 until 4) {
                        val cornerX = (x + corner % 2).toDouble()
                        val cornerZ = (z + corner / 2).toDouble()
                        val across = turnedAcross(cornerX, cornerZ)
                        val down = turnedDown(cornerX, cornerZ)
                        left = min(left, across)
                        right = max(right, across)
                        top = min(top, down)
                        bottom = max(bottom, down)
                    }
                }
            }
            AREA_LEFT = floor(left).toInt() - MARGIN
            AREA_TOP = floor(top).toInt() - MARGIN
            AREA_WIDTH = ceil(right).toInt() + MARGIN - AREA_LEFT
            AREA_HEIGHT = ceil(bottom).toInt() + MARGIN - AREA_TOP
        }

        /** How far east, in squares, the spot [x], [z] of the grid lies once the grid is turned north up. */
        fun turnedAcross(x: Double, z: Double): Double = x * COS - z * SIN

        /** How far south, in squares, the spot [x], [z] of the grid lies once the grid is turned north up. */
        fun turnedDown(x: Double, z: Double): Double = x * SIN + z * COS

        /** The column of the grid under the spot [across], [down] of the grid turned north up, the turn undone. */
        fun gridX(across: Double, down: Double): Double = across * COS + down * SIN

        /** The row of the grid under the spot [across], [down] of the grid turned north up. */
        fun gridZ(across: Double, down: Double): Double = down * COS - across * SIN

        /**
         * The way a figure turned [heading] (as the town turns figures: 0 faces south, PI / 2 east, on the game map)
         * points on the small map, one unit long: across, then down. Turned as the map is, so a figure facing real
         * north points straight up.
         */
        fun across(heading: Double): Double = sin(heading - TURN)

        fun down(heading: Double): Double = cos(heading - TURN)
    }
}

/**
 * A small map of the whole town in a corner, so the player always knows roughly where the character is. It lies with
 * real north up, as a paper map of the city does, and never turns ([MinimapProjection]); a small N and arrow at its
 * top say so. The land, water, park, seawall and the city's blocks are painted once from the grid, already turned;
 * over them it draws the five places (내 방 a square in its roof's red, the others dots, the place of an interview day
 * in the colour its name takes), the camera's view as a pale wedge, and the character as an arrow the way it faces.
 * It is drawn again only when asked, as the character or the camera moves, and makes nothing new each time. A tap on
 * it shows it larger, about four fifths of the screen wide, with the places' names; a tap or Escape brings it back. It
 * is a picture: a screen reader hears only which place the character is nearest.
 */
internal class Minimap {
    val element = document.createElement("div") as HTMLElement
    private val canvas = document.createElement("canvas") as HTMLCanvasElement
    private val context = canvas.getContext("2d") as CanvasRenderingContext2D

    /** The area painted once, north up, two pixels a square, and stretched over the map each time it is drawn. */
    private val ground = paintGround()

    private var projection = MinimapProjection(1.0, 1.0)
    private var ratio = 1.0
    private var nearest: Place? = null

    /** Where the character's arrow was last drawn, in CSS pixels from the map's top left corner, for tests. */
    var dotLeft = 0.0
        private set
    var dotTop = 0.0
        private set

    /** True while the map is shown larger, over the names. */
    var isLarge: Boolean
        get() = element.classList.contains("large")
        set(value) {
            element.classList.toggle("large", value)
            element.style.width = if (value) largeWidth() else SMALL_WIDTH
            element.style.zIndex = if (value) "1" else ""
        }

    /**
     * How much room there is under the map's top, in CSS pixels: the larger map is never taller, as on a phone turned
     * on its side. Set as the town lays itself out; the width is worked out again only when it changes.
     */
    var roomBelow = Double.POSITIVE_INFINITY
        set(value) {
            if (value == field) return
            field = value
            if (isLarge) element.style.width = largeWidth()
        }

    private fun largeWidth(): String {
        if (!roomBelow.isFinite()) return LARGE_WIDTH
        val widest = max(SMALLEST_LARGE, roomBelow * AREA_WIDTH / AREA_HEIGHT)
        return "min($LARGE_WIDTH_RULE, ${widest}px)"
    }

    init {
        element.className = "minimap"
        element.setAttribute("role", "img")
        // Where it sits and how big it is are set here, so it keeps them whatever else is on the page; how it looks is
        // in the page's style. Its top is set by the town, under the cards.
        element.style.position = "absolute"
        element.style.top = "8px"
        element.style.right = "max(8px, env(safe-area-inset-right))"
        element.style.width = SMALL_WIDTH
        element.style.setProperty("aspect-ratio", "$AREA_WIDTH / $AREA_HEIGHT")
        element.style.overflowX = "hidden"
        element.style.overflowY = "hidden"
        canvas.setAttribute("aria-hidden", "true")
        canvas.style.display = "block"
        canvas.style.width = "100%"
        canvas.style.height = "100%"
        element.appendChild(canvas)
    }

    /** Takes the map's size on the page again, after the page or the map changed size. */
    fun measure() {
        ratio = min(window.devicePixelRatio, 2.0)
        val width = element.clientWidth.toDouble()
        val height = element.clientHeight.toDouble()
        if (width == 0.0 || height == 0.0) return
        val across = (width * ratio).roundToInt()
        val down = (height * ratio).roundToInt()
        // A canvas given its own size again is wiped and laid out again, so only when the size changed.
        if (canvas.width != across || canvas.height != down) {
            canvas.width = across
            canvas.height = down
        }
        projection = MinimapProjection(width, height)
    }

    /**
     * Draws the map with the character at [x], [z] turned [heading], a camera at [eyeX], [eyeZ] looking toward
     * [focusX], [focusZ] and seeing [across] radians from side to side, and [marked] in the colour of an interview day.
     */
    fun draw(
        x: Double,
        z: Double,
        heading: Double,
        eyeX: Double,
        eyeZ: Double,
        focusX: Double,
        focusZ: Double,
        across: Double,
        marked: Place?,
    ) {
        val map = projection
        val c = context
        val large = isLarge
        c.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)
        c.clearRect(0.0, 0.0, map.width, map.height)
        c.drawImage(ground, map.areaLeft, map.areaTop, AREA_WIDTH * map.scale, AREA_HEIGHT * map.scale)
        // The camera's view: a pale wedge from where it stands, opening the way it looks.
        val eyeLeft = map.left(eyeX, eyeZ)
        val eyeTop = map.top(eyeX, eyeZ)
        val look = atan2(map.top(focusX, focusZ) - eyeTop, map.left(focusX, focusZ) - eyeLeft)
        val reach = hypot(map.left(focusX, focusZ) - eyeLeft, map.top(focusX, focusZ) - eyeTop) + map.scale * WEDGE_PAST
        c.beginPath()
        c.moveTo(eyeLeft, eyeTop)
        c.arc(eyeLeft, eyeTop, reach, look - across / 2, look + across / 2)
        c.closePath()
        c.fillStyle = WEDGE
        c.fill()
        c.lineWidth = 0.8
        c.strokeStyle = WEDGE_EDGE
        c.stroke()
        // The five places: 내 방 a square in its roof's red, the others round.
        val scale = if (large) LARGE_SCALE else 1.0
        val venues = Neighbourhood.venues
        for (at in 0 until venues.size) {
            val venue = venues[at]
            val middleX = venue.x + venue.width / 2.0
            val middleZ = venue.z + venue.depth / 2.0
            val px = map.left(middleX, middleZ)
            val pz = map.top(middleX, middleZ)
            val special = venue.place == Place.HOME || venue.place == marked
            val fill = when {
                venue.place == marked -> MARKED
                venue.place == Place.HOME -> HOME
                else -> PLACE
            }
            dot(px, pz, (if (special) BIG_DOT else DOT) * scale, fill, square = venue.place == Place.HOME)
        }
        // The character, an arrow the way it faces.
        val left = map.left(x, z)
        val top = map.top(x, z)
        dotLeft = left
        dotTop = top
        val ahead = ARROW * scale
        val ax = MinimapProjection.across(heading)
        val ad = MinimapProjection.down(heading)
        c.beginPath()
        c.moveTo(left + ax * ahead, top + ad * ahead)
        c.lineTo(left - ax * ahead * 0.7 + ad * ahead * 0.75, top - ad * ahead * 0.7 - ax * ahead * 0.75)
        c.lineTo(left - ax * ahead * 0.35, top - ad * ahead * 0.35)
        c.lineTo(left - ax * ahead * 0.7 - ad * ahead * 0.75, top - ad * ahead * 0.7 + ax * ahead * 0.75)
        c.closePath()
        c.fillStyle = CHARACTER
        c.fill()
        c.lineWidth = 1.2
        c.strokeStyle = INK
        c.stroke()
        north(map.width - NORTH_INSET * scale, NORTH_TOP * scale, scale, large)
        // The names on the larger map, last, over the wedge and the arrow.
        if (large) {
            for (at in 0 until venues.size) {
                val venue = venues[at]
                val middleX = venue.x + venue.width / 2.0
                val middleZ = venue.z + venue.depth / 2.0
                name(venue.place.label, map.left(middleX, middleZ), map.top(middleX, middleZ) - BIG_DOT * scale - 3)
            }
        }
        // A screen reader hears the place the character is nearest, told again only when that changes.
        val near = nearestPlace(x, z)
        if (near != nearest) {
            nearest = near
            element.setAttribute("aria-label", minimapLabel(near.label))
        }
    }

    private fun dot(x: Double, y: Double, radius: Double, fill: String, square: Boolean) {
        val c = context
        c.beginPath()
        if (square) c.rect(x - radius, y - radius, 2 * radius, 2 * radius) else c.arc(x, y, radius, 0.0, 2 * PI)
        c.fillStyle = fill
        c.fill()
        c.lineWidth = 1.0
        c.strokeStyle = RING
        c.stroke()
    }

    /**
     * The map's north: an arrow pointing up with its tip at [x], [y], and an N under it, [scale] times their small
     * size, with a pale edge so they read over any ground.
     */
    private fun north(x: Double, y: Double, scale: Double, large: Boolean) {
        val c = context
        val wide = NORTH_WIDE * scale
        val tall = NORTH_TALL * scale
        c.beginPath()
        c.moveTo(x, y)
        c.lineTo(x + wide, y + tall)
        c.lineTo(x, y + tall * 0.7)
        c.lineTo(x - wide, y + tall)
        c.closePath()
        c.lineWidth = 2.0
        c.strokeStyle = RING
        c.stroke()
        c.fillStyle = INK
        c.fill()
        c.font = if (large) NORTH_FONT_LARGE else NORTH_FONT
        c.textAlign = CanvasTextAlign.CENTER
        c.textBaseline = CanvasTextBaseline.TOP
        c.lineWidth = 2.5
        c.strokeText("N", x, y + tall + scale)
        c.fillText("N", x, y + tall + scale)
    }

    /** A place's name over its dot, on the larger map only, with a pale edge so it reads over any ground. */
    private fun name(label: String, x: Double, y: Double) {
        val c = context
        c.font = NAME_FONT
        c.textAlign = CanvasTextAlign.CENTER
        c.textBaseline = CanvasTextBaseline.BOTTOM
        c.lineWidth = 3.0
        c.strokeStyle = RING
        c.strokeText(label, x, y)
        c.fillStyle = INK
        c.fillText(label, x, y)
    }

    companion object {
        /** Where each place's door is, worked out once, so finding the nearest makes nothing new. */
        private val DOOR_X = DoubleArray(Neighbourhood.venues.size) { Neighbourhood.venues[it].door.x }
        private val DOOR_Z = DoubleArray(Neighbourhood.venues.size) { Neighbourhood.venues[it].door.z }

        /** The place whose door is nearest the spot [x], [z]. */
        fun nearestPlace(x: Double, z: Double): Place {
            var best = 0
            var bestDistance = Double.MAX_VALUE
            for (at in DOOR_X.indices) {
                val distance = hypot(DOOR_X[at] - x, DOOR_Z[at] - z)
                if (distance < bestDistance) {
                    best = at
                    bestDistance = distance
                }
            }
            return Neighbourhood.venues[best].place
        }

        /**
         * The area the map shows as a picture, north up, [GROUND_DETAIL] pixels a square: water, the city's ground, its
         * blocks, the park and the seawall, and past the grid's edges what the town lays there: land, water, and the
         * north shore's forest across the inlet.
         */
        private fun paintGround(): HTMLCanvasElement {
            val columns = Neighbourhood.COLUMNS
            val rows = Neighbourhood.ROWS
            val across = AREA_WIDTH * GROUND_DETAIL
            val down = AREA_HEIGHT * GROUND_DETAIL
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.width = across
            canvas.height = down
            val context = canvas.getContext("2d") as CanvasRenderingContext2D
            val image = context.createImageData(across.toDouble(), down.toDouble())
            // Written as plain numbers: a byte over 127 would come out negative, which the clamped array takes as 0.
            val pixels = image.data.asDynamic()
            val map = RealVancouver.map
            val built = BooleanArray(columns * rows)
            for (lot in Neighbourhood.cityLots) {
                for (x in lot.x until lot.x + lot.width) {
                    for (z in lot.z until lot.z + lot.depth) built[z * columns + x] = true
                }
            }
            for (j in 0 until down) {
                val south = AREA_TOP + (j + 0.5) / GROUND_DETAIL
                for (i in 0 until across) {
                    val east = AREA_LEFT + (i + 0.5) / GROUND_DETAIL
                    val gridX = MinimapProjection.gridX(east, south)
                    val gridZ = MinimapProjection.gridZ(east, south)
                    val x = floor(gridX).toInt()
                    val z = floor(gridZ).toInt()
                    val colour = if (x in 0 until columns && z in 0 until rows) {
                        when (map.terrainAt(x, z)) {
                            Terrain.WATER -> WATER
                            Terrain.PARK -> PARK
                            Terrain.SHORE -> SHORE
                            Terrain.LAND -> if (built[z * columns + x]) BLOCK else LAND
                        }
                    } else if (Neighbourhood.isNorthShore(gridX, gridZ)) {
                        FOREST
                    } else if (landBeyond(map, x, z)) {
                        LAND
                    } else {
                        WATER
                    }
                    val at = (j * across + i) * 4
                    pixels[at] = colour shr 16 and 0xFF
                    pixels[at + 1] = colour shr 8 and 0xFF
                    pixels[at + 2] = colour and 0xFF
                    pixels[at + 3] = 0xFF
                }
            }
            context.putImageData(image, 0.0, 0.0)
            return canvas
        }

        /**
         * Whether the town lays land on the square [x], [z] past the grid's edge, as [Neighbourhood] does: past the
         * east, south and north edges wherever the land reaches them, and past the two east corners where they are
         * land.
         */
        private fun landBeyond(map: TerrainMap, x: Int, z: Int): Boolean {
            val east = map.columns - 1
            val south = map.rows - 1
            return when {
                x > east -> map.terrainAt(east, z.coerceIn(0, south)) != Terrain.WATER
                x < 0 -> false
                z > south -> map.terrainAt(x, south) != Terrain.WATER
                else -> map.terrainAt(x, 0) != Terrain.WATER
            }
        }

        // The map looks the same in both themes, as the town does.
        private const val WATER = 0x6FB3D9
        private const val LAND = 0xEDE8DC
        private const val BLOCK = 0xD3CCBC
        private const val PARK = 0x8FC165
        private const val SHORE = 0xF6F0E0

        /** The town's dark forest green, on the north shore across the inlet. */
        private const val FOREST = 0x4D7A5E
        private const val PLACE = "#3b2f25"
        private const val HOME = "#c0584a"
        private const val MARKED = "#1d6b57"
        private const val RING = "#fffaee"
        private const val INK = "#3b2f25"
        private const val CHARACTER = "#ffd23f"
        private const val WEDGE = "rgba(255, 214, 90, 0.42)"
        private const val WEDGE_EDGE = "rgba(122, 82, 51, 0.5)"
        private const val NAME_FONT = "700 12px system-ui, sans-serif"
        private const val NORTH_FONT = "700 9px system-ui, sans-serif"
        private const val NORTH_FONT_LARGE = "700 14px system-ui, sans-serif"

        /** How many pixels a square of the grid takes in the picture painted once. */
        private const val GROUND_DETAIL = 2

        /** The dots' and the arrow's sizes in CSS pixels, and how much larger they are on the larger map. */
        private const val DOT = 2.6
        private const val BIG_DOT = 3.6
        private const val ARROW = 5.5
        private const val LARGE_SCALE = 1.6

        /** Where the north arrow's tip is, from the map's right edge and its top, and its half width and height. */
        const val NORTH_INSET = 10.0
        const val NORTH_TOP = 4.0
        private const val NORTH_WIDE = 3.5
        private const val NORTH_TALL = 6.0

        /** How far past the character the wedge of the camera's view reaches, in squares. */
        private const val WEDGE_PAST = 14.0

        /**
         * How wide the map is: small, about 94 points on a 375-point phone and a little taller than wide, as the ground
         * turned north up is, so it takes about as much of the screen as the wider map unturned did; or larger, four
         * fifths of the town.
         */
        private const val SMALL_WIDTH = "clamp(80px, 25vw, 100px)"
        private const val LARGE_WIDTH_RULE = "80%, 480px"
        private const val LARGE_WIDTH = "min($LARGE_WIDTH_RULE)"

        /** The larger map is never narrower than this, however little room there is under it. */
        private const val SMALLEST_LARGE = 160.0
    }
}
