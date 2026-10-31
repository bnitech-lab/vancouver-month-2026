package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
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
import vancouvermonth.text.minimapLabel

/**
 * Where a spot of the town lies on a small map [width] by [height] pixels of the whole grid, laid as the game map lies
 * and never turned: x east to the right, z south down, so it reads like the map's preview picture.
 */
class MinimapProjection(val width: Double, val height: Double) {
    fun left(x: Double): Double = x * width / Neighbourhood.COLUMNS

    fun top(z: Double): Double = z * height / Neighbourhood.ROWS

    companion object {
        /**
         * The way a figure turned [heading] (as the town turns figures: 0 faces south, PI / 2 east) points on the map,
         * one unit long: across, then down.
         */
        fun across(heading: Double): Double = sin(heading)

        fun down(heading: Double): Double = cos(heading)
    }
}

/**
 * A small map of the whole town in a corner, so the player always knows roughly where the character is. The land,
 * water, park, seawall and the city's blocks are painted once from the grid; over them it draws the five places (내 방
 * a square in its roof's red, the others dots, the place of an interview day in the colour its name takes), the
 * camera's view as a pale wedge, and the character as an arrow the way it faces. It is drawn again only when asked,
 * as the character or the camera moves, and makes nothing new each time. A tap on it shows it larger, about four
 * fifths of the screen wide, with the places' names; a tap or Escape brings it back. It is a picture: a screen reader
 * hears only which place the character is nearest.
 */
internal class Minimap {
    val element = document.createElement("div") as HTMLElement
    private val canvas = document.createElement("canvas") as HTMLCanvasElement
    private val context = canvas.getContext("2d") as CanvasRenderingContext2D

    /** The grid painted once, one pixel a square, and stretched over the map each time it is drawn. */
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
        val widest = max(SMALLEST_LARGE, roomBelow * Neighbourhood.COLUMNS / Neighbourhood.ROWS)
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
        element.style.setProperty("aspect-ratio", "${Neighbourhood.COLUMNS} / ${Neighbourhood.ROWS}")
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
        c.drawImage(ground, 0.0, 0.0, map.width, map.height)
        // The camera's view: a pale wedge from where it stands, opening the way it looks.
        val look = atan2(map.top(focusZ) - map.top(eyeZ), map.left(focusX) - map.left(eyeX))
        val reach = hypot(map.left(focusX) - map.left(eyeX), map.top(focusZ) - map.top(eyeZ)) +
            map.width * WEDGE_PAST / Neighbourhood.COLUMNS
        c.beginPath()
        c.moveTo(map.left(eyeX), map.top(eyeZ))
        c.arc(map.left(eyeX), map.top(eyeZ), reach, look - across / 2, look + across / 2)
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
            val px = map.left(venue.x + venue.width / 2.0)
            val pz = map.top(venue.z + venue.depth / 2.0)
            val special = venue.place == Place.HOME || venue.place == marked
            val fill = when {
                venue.place == marked -> MARKED
                venue.place == Place.HOME -> HOME
                else -> PLACE
            }
            dot(px, pz, (if (special) BIG_DOT else DOT) * scale, fill, square = venue.place == Place.HOME)
        }
        // The character, an arrow the way it faces.
        val left = map.left(x)
        val top = map.top(z)
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
        // The names on the larger map, last, over the wedge and the arrow.
        if (large) {
            for (at in 0 until venues.size) {
                val venue = venues[at]
                val px = map.left(venue.x + venue.width / 2.0)
                val pz = map.top(venue.z + venue.depth / 2.0)
                name(venue.place.label, px, pz - BIG_DOT * scale - 3)
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

        /** The grid as a picture one pixel a square: water, the city's ground, its blocks, the park and the seawall. */
        private fun paintGround(): HTMLCanvasElement {
            val columns = Neighbourhood.COLUMNS
            val rows = Neighbourhood.ROWS
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.width = columns
            canvas.height = rows
            val context = canvas.getContext("2d") as CanvasRenderingContext2D
            val image = context.createImageData(columns.toDouble(), rows.toDouble())
            // Written as plain numbers: a byte over 127 would come out negative, which the clamped array takes as 0.
            val pixels = image.data.asDynamic()
            val map = RealVancouver.map
            val built = BooleanArray(columns * rows)
            for (lot in Neighbourhood.cityLots) {
                for (x in lot.x until lot.x + lot.width) {
                    for (z in lot.z until lot.z + lot.depth) built[z * columns + x] = true
                }
            }
            for (z in 0 until rows) {
                for (x in 0 until columns) {
                    val colour = when (map.terrainAt(x, z)) {
                        Terrain.WATER -> WATER
                        Terrain.PARK -> PARK
                        Terrain.SHORE -> SHORE
                        Terrain.LAND -> if (built[z * columns + x]) BLOCK else LAND
                    }
                    val at = (z * columns + x) * 4
                    pixels[at] = colour shr 16 and 0xFF
                    pixels[at + 1] = colour shr 8 and 0xFF
                    pixels[at + 2] = colour and 0xFF
                    pixels[at + 3] = 0xFF
                }
            }
            context.putImageData(image, 0.0, 0.0)
            return canvas
        }

        // The map looks the same in both themes, as the town does.
        private const val WATER = 0x6FB3D9
        private const val LAND = 0xEDE8DC
        private const val BLOCK = 0xD3CCBC
        private const val PARK = 0x8FC165
        private const val SHORE = 0xF6F0E0
        private const val PLACE = "#3b2f25"
        private const val HOME = "#c0584a"
        private const val MARKED = "#1d6b57"
        private const val RING = "#fffaee"
        private const val INK = "#3b2f25"
        private const val CHARACTER = "#ffd23f"
        private const val WEDGE = "rgba(255, 214, 90, 0.42)"
        private const val WEDGE_EDGE = "rgba(122, 82, 51, 0.5)"
        private const val NAME_FONT = "700 12px system-ui, sans-serif"

        /** The dots' and the arrow's sizes in CSS pixels, and how much larger they are on the larger map. */
        private const val DOT = 2.6
        private const val BIG_DOT = 3.6
        private const val ARROW = 5.5
        private const val LARGE_SCALE = 1.6

        /** How far past the character the wedge of the camera's view reaches, in squares. */
        private const val WEDGE_PAST = 14.0

        /** How wide the map is: small, about 112 points on a 375-point phone, or larger, four fifths of the town. */
        private const val SMALL_WIDTH = "clamp(96px, 30vw, 120px)"
        private const val LARGE_WIDTH_RULE = "80%, 480px"
        private const val LARGE_WIDTH = "min($LARGE_WIDTH_RULE)"

        /** The larger map is never narrower than this, however little room there is under it. */
        private const val SMALLEST_LARGE = 160.0
    }
}
