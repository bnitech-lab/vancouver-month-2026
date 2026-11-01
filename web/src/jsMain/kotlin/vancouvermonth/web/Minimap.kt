package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
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
import vancouvermonth.text.Transit
import vancouvermonth.text.minimapLabel
import vancouvermonth.web.MinimapProjection.Companion.AREA_HEIGHT
import vancouvermonth.web.MinimapProjection.Companion.AREA_LEFT
import vancouvermonth.web.MinimapProjection.Companion.AREA_TOP
import vancouvermonth.web.MinimapProjection.Companion.AREA_WIDTH

/**
 * Where a spot of the town lies on a small map [width] by [height] pixels, laid with real north up. The game map is
 * the real city turned [WideMapData.TURN_DEGREES] counter-clockwise, so downtown's streets run along x and z; the
 * small map turns it back as much clockwise, so x east runs right and down, z south runs down and left. Given no
 * [squaresAcross], it shows all the ground the character can stand on, [AREA_WIDTH] by [AREA_HEIGHT] squares, as large
 * as fits, the same scale both ways, in the middle. Given [squaresAcross], it shows that many squares across, the same
 * scale down, round the spot it is centred on ([centreOn]): a window that follows the character.
 */
class MinimapProjection(val width: Double, val height: Double, squaresAcross: Double? = null) {
    /** How many pixels a square of the grid takes on the map, across and down alike. */
    val scale = if (squaresAcross == null) min(width / AREA_WIDTH, height / AREA_HEIGHT) else width / squaresAcross

    /** Where the turned grid's origin falls on the map, in pixels: moved by [centreOn] for a window. */
    private var originLeft = (width - AREA_WIDTH * scale) / 2 - AREA_LEFT * scale
    private var originTop = (height - AREA_HEIGHT * scale) / 2 - AREA_TOP * scale

    /** Where the area's top left corner is, in pixels: the map's own, or a little in where the map is not its shape. */
    val areaLeft: Double
        get() = originLeft + AREA_LEFT * scale
    val areaTop: Double
        get() = originTop + AREA_TOP * scale

    /** Puts the spot [x], [z] of the grid in the map's middle, for a window round it. It makes nothing new. */
    fun centreOn(x: Double, z: Double) {
        originLeft = width / 2 - turnedAcross(x, z) * scale
        originTop = height / 2 - turnedDown(x, z) * scale
    }

    /** How far from the map's left edge the spot [x], [z] of the grid lies, in pixels. */
    fun left(x: Double, z: Double): Double = originLeft + turnedAcross(x, z) * scale

    /** How far down from the map's top edge the spot [x], [z] of the grid lies, in pixels. */
    fun top(x: Double, z: Double): Double = originTop + turnedDown(x, z) * scale

    companion object {
        /** How far the game map is turned from real north, counter-clockwise, in radians. */
        val TURN = WideMapData.TURN_DEGREES * PI / 180
        private val COS = cos(TURN)
        private val SIN = sin(TURN)

        /** How many squares of water or land past the ground the character can stand on the map shows on each side. */
        private const val MARGIN = 6

        /**
         * The area the whole map shows, in squares of the grid turned north up: every square of the wide map that is
         * not water, corner to corner, so the character's arrow is always on the map, with [MARGIN] squares more on each
         * side. Worked out once from the map.
         */
        val AREA_LEFT: Int
        val AREA_TOP: Int
        val AREA_WIDTH: Int
        val AREA_HEIGHT: Int

        init {
            val map = Neighbourhood.map
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
 * A small map in a corner, so the player always knows roughly where the character is. It lies with real north up, as
 * a paper map of the city does, and never turns ([MinimapProjection]); a small N and arrow at its top say so. Small, it
 * shows the ground round the character, [WINDOW_ACROSS] squares across, the character in its middle, and follows it;
 * a tap shows the whole wide map larger, about four fifths of the screen wide, with the places' names, and a tap or
 * Escape brings the small one back. The land, water, park, seawall and the city's blocks are painted once from the
 * grid, already turned; over them it draws the SkyTrain's stations, the bus stops, the water bus's docks and the bike
 * shop as small marks, the places of [site] (내 방 a square in its roof's red, the others dots, the place of an
 * interview day in the colour its name takes), the camera's view as a pale wedge, and the character as an arrow the
 * way it faces. The larger map names each place by its kind (카페, 회사), and an interview day's office by its own name.
 * Off the small map, 내 방, the 회사 nearest the character and an interview day's office show as arrows on its edge,
 * pointing the way to them. It is drawn again only when asked, as the character or the camera moves, and makes nothing
 * new each time. It is a picture: a screen reader hears only which place the character is nearest.
 */
internal class Minimap(private val site: HomeSite = HomeSite.of(1)) {
    val element = document.createElement("div") as HTMLElement
    private val canvas = document.createElement("canvas") as HTMLCanvasElement
    private val context = canvas.getContext("2d") as CanvasRenderingContext2D

    private var projection = MinimapProjection(1.0, 1.0)
    private var ratio = 1.0

    /** The places of [site], worked out once: each one's middle, and its door, so nothing new is made to draw them. */
    private val venues = site.venues
    private val middleX = DoubleArray(venues.size) { venues[it].x + venues[it].width / 2.0 }
    private val middleZ = DoubleArray(venues.size) { venues[it].z + venues[it].depth / 2.0 }
    private val doorX = DoubleArray(venues.size) { venues[it].door.x }
    private val doorZ = DoubleArray(venues.size) { venues[it].door.z }

    /**
     * What a screen reader can hear the character is near: the places of [site], then the line's stations, each
     * one's door and name, worked out once; and which one it heard last, and whether close by.
     */
    private val landmarks = venues.size + TransitLines.stations.size
    private val landmarkX = DoubleArray(landmarks) {
        if (it < venues.size) doorX[it] else TransitLines.stations[it - venues.size].door.x
    }
    private val landmarkZ = DoubleArray(landmarks) {
        if (it < venues.size) doorZ[it] else TransitLines.stations[it - venues.size].door.z
    }
    private val landmarkName = Array(landmarks) {
        if (it < venues.size) venues[it].place.label else Transit.STATIONS[it - venues.size]
    }
    private var heard = -1
    private var heardNear = false

    /** Where the character's arrow was last drawn, in CSS pixels from the map's top left corner, for tests. */
    var dotLeft = 0.0
        private set
    var dotTop = 0.0
        private set

    /**
     * The order the larger map names the places in, 내 방, an interview day's office and the offices first, worked out
     * again only when the office marked changes; each name's width, its kind's word and its own name, measured once; and
     * where each name went on the last drawing (its bottom middle) and how wide it was, to keep them apart. For tests,
     * [namesDrawn] is how many were.
     */
    private var nameOrder = orderOfNames(null)
    private var nameOrderFor: Place? = null
    private val kindWidth = DoubleArray(venues.size) { -1.0 }
    private val ownWidth = DoubleArray(venues.size) { -1.0 }
    private val nameLeft = DoubleArray(venues.size)
    private val nameTop = DoubleArray(venues.size)
    private val nameWide = DoubleArray(venues.size)
    private val nameOf = IntArray(venues.size)
    var namesDrawn = 0
        private set

    /** Where each name was drawn on the larger map, as its box's left, top, right and bottom. For tests. */
    val nameBoxes: List<List<Double>>
        get() = (0 until namesDrawn).map { at ->
            val half = nameWide[at] / 2
            listOf(nameLeft[at] - half, nameTop[at] - NAME_HEIGHT, nameLeft[at] + half, nameTop[at])
        }

    /** The names drawn on the larger map, in the order they were placed. For tests. */
    val namesShown: List<String>
        get() = (0 until namesDrawn).map { nameText(nameOf[it], nameOrderFor) }

    /** Which of the places were last drawn as arrows on the small map's edge, out of its sight, by their number. */
    private val pointed = BooleanArray(venues.size)

    /** The places last drawn as arrows on the small map's edge. For tests. */
    val pointedTo: Set<Place>
        get() = venues.indices.filter { pointed[it] }.map { venues[it].place }.toSet()

    /** True while the map is shown larger, the whole wide map, over the names. */
    var isLarge: Boolean
        get() = element.classList.contains("large")
        set(value) {
            element.classList.toggle("large", value)
            element.style.width = if (value) largeWidth() else SMALL_WIDTH
            element.style.setProperty("aspect-ratio", if (value) "$AREA_WIDTH / $AREA_HEIGHT" else WINDOW_SHAPE)
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
        element.style.setProperty("aspect-ratio", WINDOW_SHAPE)
        element.style.overflowX = "hidden"
        element.style.overflowY = "hidden"
        canvas.setAttribute("aria-hidden", "true")
        canvas.style.display = "block"
        canvas.style.width = "100%"
        canvas.style.height = "100%"
        element.appendChild(canvas)
    }

    /** Takes the map's size on the page again, after the page or the map changed size, or it went larger or smaller. */
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
        projection = if (isLarge) MinimapProjection(width, height) else MinimapProjection(width, height, WINDOW_ACROSS)
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
        if (!large) map.centreOn(x, z)
        c.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)
        paintGround(c, map)
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
        val scale = if (large) LARGE_SCALE else 1.0
        drawMarks(c, map, scale)
        // The places: 내 방 a square in its roof's red, the others round; off the small map, 내 방, the nearest 회사 and
        // an interview day's an arrow on its edge, the way to go.
        var nearestOffice = -1
        var officeAway = Double.MAX_VALUE
        for (at in venues.indices) {
            if (venues[at].place.kind != PlaceKind.OFFICE) continue
            val away = hypot(middleX[at] - x, middleZ[at] - z)
            if (away < officeAway) {
                nearestOffice = at
                officeAway = away
            }
        }
        for (at in venues.indices) {
            pointed[at] = false
            val place = venues[at].place
            val px = map.left(middleX[at], middleZ[at])
            val pz = map.top(middleX[at], middleZ[at])
            val special = place == Place.HOME || place == marked
            val fill = when {
                place == marked -> MARKED
                place == Place.HOME -> HOME
                else -> PLACE
            }
            if (isOn(map, px, pz, 0.0)) {
                dot(c, px, pz, (if (special) BIG_DOT else DOT) * scale, fill, square = place == Place.HOME)
            } else if (!large && (place == Place.HOME || at == nearestOffice || place == marked)) {
                edgeArrow(map, px, pz, fill)
                pointed[at] = true
            }
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
        north(c, map.width - NORTH_INSET * scale, NORTH_TOP * scale, scale, large)
        // The names on the larger map, last, over the wedge and the arrow: 내 방's first, then an interview day's office.
        // The places downtown lie close together on the whole map, so a name that would lie over another goes to the
        // first clear spot round its dot, with a short line back to the dot.
        namesDrawn = 0
        if (large) {
            c.font = NAME_FONT
            if (marked != nameOrderFor) {
                nameOrder = orderOfNames(marked)
                nameOrderFor = marked
            }
            for (at in nameOrder) {
                val own = venues[at].place == marked
                val widths = if (own) ownWidth else kindWidth
                if (widths[at] < 0) widths[at] = c.measureText(nameText(at, marked)).width
                val wide = widths[at]
                val dotLeft = map.left(middleX[at], middleZ[at])
                val dotTop = map.top(middleX[at], middleZ[at])
                val lift = BIG_DOT * scale + 3
                var spot = -1
                for (each in NAME_SPOTS_X.indices) {
                    val left = dotLeft + NAME_SPOTS_X[each]
                    val top = dotTop + NAME_SPOTS_Y[each] - lift
                    if (isNameClear(wide, left, top, map)) {
                        spot = each
                        break
                    }
                }
                if (spot < 0) spot = 0
                val left = dotLeft + NAME_SPOTS_X[spot]
                val top = dotTop + NAME_SPOTS_Y[spot] - lift
                if (spot > 0) leader(c, dotLeft, dotTop, left, top)
                nameLeft[namesDrawn] = left
                nameTop[namesDrawn] = top
                nameWide[namesDrawn] = wide
                nameOf[namesDrawn] = at
                namesDrawn += 1
                name(c, nameText(at, marked), left, top)
            }
        }
        // A screen reader hears the place or station the character is nearest, "근처" only within NEAR_REACH squares,
        // told again only when that changes.
        var best = 0
        var bestDistance = Double.MAX_VALUE
        for (at in 0 until landmarks) {
            val distance = hypot(landmarkX[at] - x, landmarkZ[at] - z)
            if (distance < bestDistance) {
                best = at
                bestDistance = distance
            }
        }
        val near = bestDistance <= NEAR_REACH
        if (best != heard || near != heardNear) {
            heard = best
            heardNear = near
            element.setAttribute("aria-label", minimapLabel(landmarkName[best], near))
        }
    }

    /**
     * The name the larger map gives place [at]: its kind's word, 내 방 for 내 방, and its own name when it is the [marked]
     * office of an interview day.
     */
    private fun nameText(at: Int, marked: Place?): String {
        val place = venues[at].place
        return if (place == marked) place.label else place.kind.label
    }

    /** The order the larger map names the places in: 내 방, then [marked], then the offices, then the rest. */
    private fun orderOfNames(marked: Place?): IntArray = venues.indices.sortedBy {
        val place = venues[it].place
        val rank = when {
            place == Place.HOME -> 0
            place == marked -> 1
            place.kind == PlaceKind.OFFICE -> 2
            else -> 3
        }
        rank * venues.size + it
    }.toIntArray()

    /** True when a name [wide] pixels wide, its bottom middle at [left], [top], lies on [map] and over no name drawn yet. */
    private fun isNameClear(wide: Double, left: Double, top: Double, map: MinimapProjection): Boolean {
        val half = wide / 2
        if (left - half < 0 || left + half > map.width || top - NAME_HEIGHT < 0 || top > map.height) return false
        for (other in 0 until namesDrawn) {
            val otherHalf = nameWide[other] / 2
            if (abs(nameLeft[other] - left) < half + otherHalf + NAME_GAP && abs(nameTop[other] - top) < NAME_HEIGHT + NAME_GAP) {
                return false
            }
        }
        return true
    }

    /**
     * An arrow on the small map's edge where the way from its middle to [left], [top] (off the map) leaves it, pointing
     * out that way, in [fill]: the way to a place out of sight.
     */
    private fun edgeArrow(map: MinimapProjection, left: Double, top: Double, fill: String) {
        val c = context
        val middleLeft = map.width / 2
        val middleTop = map.height / 2
        val dx = left - middleLeft
        val dy = top - middleTop
        // How far toward it the edge is, EDGE_INSET in from it.
        val reach = min(
            if (dx == 0.0) Double.MAX_VALUE else (middleLeft - EDGE_INSET) / abs(dx),
            if (dy == 0.0) Double.MAX_VALUE else (middleTop - EDGE_INSET) / abs(dy),
        )
        val tipLeft = middleLeft + dx * reach
        val tipTop = middleTop + dy * reach
        val length = hypot(dx, dy)
        val ux = dx / length
        val uy = dy / length
        c.beginPath()
        c.moveTo(tipLeft + ux * EDGE_ARROW, tipTop + uy * EDGE_ARROW)
        c.lineTo(tipLeft - ux * EDGE_ARROW + uy * EDGE_ARROW, tipTop - uy * EDGE_ARROW - ux * EDGE_ARROW)
        c.lineTo(tipLeft - ux * EDGE_ARROW - uy * EDGE_ARROW, tipTop - uy * EDGE_ARROW + ux * EDGE_ARROW)
        c.closePath()
        c.fillStyle = fill
        c.fill()
        c.lineWidth = 1.0
        c.strokeStyle = RING
        c.stroke()
    }

    companion object {
        /**
         * The small marks, worked out once: each station's door (kind 0), bus stop (1) and dock (2), and the bike shop's
         * door (3), with the colour of each kind.
         */
        private val MARKS: List<Pair<Spot, Int>> = TransitLines.stations.map { it.door to 0 } +
            TransitLines.busStops.map { it.wait to 1 } + TransitLines.docks.map { it.wait to 2 } +
            (Neighbourhood.shop.door to 3)
        private val MARK_X = DoubleArray(MARKS.size) { MARKS[it].first.x }
        private val MARK_Z = DoubleArray(MARKS.size) { MARKS[it].first.z }
        private val MARK_KIND = IntArray(MARKS.size) { MARKS[it].second }
        private val MARK_FILLS = arrayOf("#3c6cb4", "#3f8e6a", "#d9673f", "#e0a43a")

        /** How many marks of each kind the map draws, for tests. */
        fun marksOf(kind: Int): Int = MARK_KIND.count { it == kind }

        /**
         * Paints the ground's picture now, with the city's layout it is painted from, if that is not done yet, so the
         * first map drawn need not wait for it. Its width in pixels.
         */
        internal fun warmUp(): Int = groundPicture.width

        /**
         * The water over the whole of [map], then the painted ground, the part of it on the map only, so a window of
         * the wide map costs no more than the whole.
         */
        internal fun paintGround(c: CanvasRenderingContext2D, map: MinimapProjection) {
            c.fillStyle = WATER_CSS
            c.fillRect(0.0, 0.0, map.width, map.height)
            val ground = groundPicture
            val detail = GROUND_DETAIL.toDouble()
            // The picture's top left corner on the map: the area's, [GROUND_PAD] squares further out.
            val pictureLeft = map.areaLeft - GROUND_PAD * map.scale
            val pictureTop = map.areaTop - GROUND_PAD * map.scale
            // The window's corners in the picture's pixels, kept inside the picture.
            val fromX = max(0.0, (0.0 - pictureLeft) / map.scale * detail)
            val fromY = max(0.0, (0.0 - pictureTop) / map.scale * detail)
            val toX = min(ground.width.toDouble(), (map.width - pictureLeft) / map.scale * detail)
            val toY = min(ground.height.toDouble(), (map.height - pictureTop) / map.scale * detail)
            if (toX <= fromX || toY <= fromY) return
            c.drawImage(
                ground, fromX, fromY, toX - fromX, toY - fromY,
                pictureLeft + fromX / detail * map.scale, pictureTop + fromY / detail * map.scale,
                (toX - fromX) / detail * map.scale, (toY - fromY) / detail * map.scale,
            )
        }

        /**
         * The ways about town and the bike shop, [scale] times their small size, under the places: stations square,
         * the rest round. Only those on [map] are drawn.
         */
        internal fun drawMarks(c: CanvasRenderingContext2D, map: MinimapProjection, scale: Double) {
            for (at in MARK_X.indices) {
                val left = map.left(MARK_X[at], MARK_Z[at])
                val top = map.top(MARK_X[at], MARK_Z[at])
                if (!isOn(map, left, top, 0.0)) continue
                dot(c, left, top, MARK * scale, MARK_FILLS[MARK_KIND[at]], MARK_KIND[at] == 0)
            }
        }

        /** True when [left], [top] is on the map, [inset] pixels in from its edges. */
        private fun isOn(map: MinimapProjection, left: Double, top: Double, inset: Double): Boolean =
            left >= inset && left <= map.width - inset && top >= inset && top <= map.height - inset

        /** A dot of [radius] at [x], [y] in [fill], a square when [square], with a pale ring. */
        internal fun dot(
            c: CanvasRenderingContext2D,
            x: Double,
            y: Double,
            radius: Double,
            fill: String,
            square: Boolean,
        ) {
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
        internal fun north(c: CanvasRenderingContext2D, x: Double, y: Double, scale: Double, large: Boolean) {
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

        /** A place's name centred on [x], its foot at [y], with a pale edge so it reads over any ground. */
        internal fun name(c: CanvasRenderingContext2D, label: String, x: Double, y: Double) {
            c.font = NAME_FONT
            c.textAlign = CanvasTextAlign.CENTER
            c.textBaseline = CanvasTextBaseline.BOTTOM
            c.lineWidth = 3.0
            c.strokeStyle = RING
            c.strokeText(label, x, y)
            c.fillStyle = INK
            c.fillText(label, x, y)
        }

        /** How wide [label] is as a name on the map, in pixels. */
        internal fun measureName(c: CanvasRenderingContext2D, label: String): Double {
            c.font = NAME_FONT
            return c.measureText(label).width
        }

        /**
         * A short line from a place's dot at [dotLeft], [dotTop] to its name moved off it ([NAME_SPOTS_X]), the name's
         * foot at [left], [top].
         */
        internal fun leader(c: CanvasRenderingContext2D, dotLeft: Double, dotTop: Double, left: Double, top: Double) {
            val nameMiddle = top - NAME_HEIGHT / 2
            // To the name's nearer edge, top or bottom, so the line does not cross it.
            val end = if (nameMiddle < dotTop) top else top - NAME_HEIGHT
            c.beginPath()
            c.moveTo(dotLeft, dotTop)
            c.lineTo(left, end)
            c.lineWidth = 1.2
            c.strokeStyle = INK
            c.stroke()
        }

        /** The place of zone 1's town whose door is nearest the spot [x], [z]. */
        fun nearestPlace(x: Double, z: Double): Place =
            HomeSite.of(1).venues.minBy { hypot(it.door.x - x, it.door.z - z) }.place

        /**
         * The area the map shows, and [GROUND_PAD] squares more on each side, as a picture, north up, [GROUND_DETAIL]
         * pixels a square: water, the city's ground, its
         * blocks, the park and the seawall, and past the grid's edges what the town lays there: land, water, and the
         * north shore's forest across the inlet.
         */
        /** The ground's picture, painted once for every map the page makes. */
        private val groundPicture: HTMLCanvasElement by lazy { makeGroundPicture() }

        private fun makeGroundPicture(): HTMLCanvasElement {
            val columns = Neighbourhood.COLUMNS
            val rows = Neighbourhood.ROWS
            val across = (AREA_WIDTH + 2 * GROUND_PAD) * GROUND_DETAIL
            val down = (AREA_HEIGHT + 2 * GROUND_PAD) * GROUND_DETAIL
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.width = across
            canvas.height = down
            val context = canvas.getContext("2d") as CanvasRenderingContext2D
            val image = context.createImageData(across.toDouble(), down.toDouble())
            // Written as plain numbers: a byte over 127 would come out negative, which the clamped array takes as 0.
            val pixels = image.data.asDynamic()
            val map = Neighbourhood.map
            val built = Neighbourhood.built
            for (j in 0 until down) {
                val south = AREA_TOP - GROUND_PAD + (j + 0.5) / GROUND_DETAIL
                for (i in 0 until across) {
                    val east = AREA_LEFT - GROUND_PAD + (i + 0.5) / GROUND_DETAIL
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
        private const val WATER_CSS = "#6fb3d9"
        private const val LAND = 0xEDE8DC
        private const val BLOCK = 0xD3CCBC
        private const val PARK = 0x8FC165
        private const val SHORE = 0xF6F0E0

        /** The town's dark forest green, on the north shore across the inlet. */
        private const val FOREST = 0x4D7A5E
        internal const val PLACE = "#3b2f25"
        internal const val HOME = "#c0584a"
        private const val MARKED = "#1d6b57"
        private const val RING = "#fffaee"
        internal const val INK = "#3b2f25"
        private const val CHARACTER = "#ffd23f"
        private const val WEDGE = "rgba(255, 214, 90, 0.42)"
        private const val WEDGE_EDGE = "rgba(122, 82, 51, 0.5)"
        private const val NAME_FONT = "700 12px system-ui, sans-serif"
        private const val NORTH_FONT = "700 9px system-ui, sans-serif"
        private const val NORTH_FONT_LARGE = "700 14px system-ui, sans-serif"

        /** How many pixels a square of the grid takes in the picture painted once. */
        private const val GROUND_DETAIL = 2

        /** The dots' and the arrow's sizes in CSS pixels, and how much larger they are on the larger map. */
        internal const val DOT = 2.6
        internal const val BIG_DOT = 3.6
        private const val ARROW = 5.5

        /** The small marks' size, under the places' dots. */
        private const val MARK = 1.7
        internal const val LARGE_SCALE = 1.6

        /** Where the north arrow's tip is, from the map's right edge and its top, and its half width and height. */
        const val NORTH_INSET = 10.0
        const val NORTH_TOP = 4.0
        private const val NORTH_WIDE = 3.5
        private const val NORTH_TALL = 6.0

        /** How far past the character the wedge of the camera's view reaches, in squares. */
        private const val WEDGE_PAST = 14.0

        /**
         * The small map's window: this many squares across, about 2.5 km, and a little taller than wide, the shape the
         * small map had when it showed the town's own corner, so it takes the same room on the screen.
         */
        const val WINDOW_ACROSS = 100.0
        private const val WINDOW_SHAPE = "239 / 267"

        /** How many squares the small map's window shows from top to bottom, from its shape. */
        const val WINDOW_DOWN = WINDOW_ACROSS * 267 / 239

        /**
         * How many squares the ground's picture reaches past the whole map's area on each side: half the small window's
         * height, rounded up, so the window, centred on the character wherever it stands, never runs off the picture
         * into a bare sea where the town draws land.
         */
        const val GROUND_PAD = 56

        /** How tall a name on the larger map is, and the room kept between two names, in pixels. */
        internal const val NAME_HEIGHT = 14.0
        internal const val NAME_GAP = 2.0

        /** How far, in squares, from a place's or station's door a screen reader still hears "근처". */
        const val NEAR_REACH = 40.0

        /**
         * Where a name may go round its dot on the larger map and on the map of the rooms ([RoomMap]), the first clear
         * one taken: right over it, then under it, then out to the sides and farther up and down, and last a ring
         * farther out, for a dot crowded by others or by the rooms' pins. Offsets of the name's bottom middle, in pixels;
         * a name moved off its dot gets a [leader].
         */
        internal val NAME_SPOTS_X = doubleArrayOf(
            0.0, 0.0, -34.0, 34.0, -34.0, 34.0, 0.0, 0.0, -56.0, 56.0, -48.0, 48.0,
            0.0, -74.0, 74.0, -70.0, 70.0, 0.0, -62.0, 62.0,
        )
        internal val NAME_SPOTS_Y = doubleArrayOf(
            0.0, 26.0, -6.0, -6.0, 22.0, 22.0, -20.0, 44.0, 8.0, 8.0, -24.0, -24.0,
            -42.0, -10.0, -10.0, 34.0, 34.0, 64.0, -44.0, -44.0,
        )

        /** How far in from the small map's edge an arrow to a place out of sight sits, and how big it is, in pixels. */
        private const val EDGE_INSET = 6.0
        private const val EDGE_ARROW = 4.0

        /**
         * How wide the map is: small, about 94 points on a 375-point phone and a little taller than wide; or larger, the
         * whole wide map, four fifths of the town.
         */
        private const val SMALL_WIDTH = "clamp(80px, 25vw, 100px)"
        private const val LARGE_WIDTH_RULE = "80%, 480px"
        private const val LARGE_WIDTH = "min($LARGE_WIDTH_RULE)"

        /** The larger map is never narrower than this, however little room there is under it. */
        private const val SMALLEST_LARGE = 160.0
    }
}
