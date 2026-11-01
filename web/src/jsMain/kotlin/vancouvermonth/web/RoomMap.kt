package vancouvermonth.web

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.Window
import org.w3c.dom.events.Event
import vancouvermonth.core.Room
import vancouvermonth.text.money
import vancouvermonth.text.utilitiesLabel
import vancouvermonth.web.MinimapProjection.Companion.AREA_HEIGHT
import vancouvermonth.web.MinimapProjection.Companion.AREA_WIDTH

/**
 * September 1st's rooms as pins on the whole wide map, north up, as the small map shows it larger: its painted ground
 * and the marks of the ways about town ([Minimap.paintGround], [MinimapProjection] for the whole area), the places as
 * dots with every 회사 and 카페 named by its kind (placed as that map places names, clear of the pins), so the player
 * sees how far each room is from work, and a pin for each room at the door 내 방 has in the room's zone
 * ([Neighbourhood.home], where [HomeSite] puts it), carrying the room's number. The pins are buttons, which a keyboard and a screen reader reach,
 * each heard as its room in full; a tap picks the room ([onPick]). The picked one is drawn pressed, and once [locked]
 * none can be picked. Pins that would lie closer than a finger's width are pushed apart, with a line from each moved
 * pin back to its door. The map is a picture, drawn at its width once it is on the page ([draw]); it is all 2D, so
 * nothing of the town runs under it.
 */
internal class RoomMap(rooms: List<Room>, picked: Int?, locked: Boolean, onPick: (Int) -> Unit) {
    val element = document.createElement("div") as HTMLElement
    private val canvas = document.createElement("canvas") as HTMLCanvasElement
    private val context = canvas.getContext("2d") as? CanvasRenderingContext2D

    /** False where the browser has no 2D canvas to draw the map on: the rooms are then shown as a list. */
    val canDraw: Boolean
        get() = context != null

    /** Each room's 내 방 door, where its pin stands, in the rooms' order. */
    val doors: List<Spot> = rooms.map { Neighbourhood.home(it.zone.number).door }

    /** One pin a room, in the rooms' order, which is also the order a keyboard reaches them in. */
    val pins: List<HTMLButtonElement>

    /** Where each pin's middle was put at the last [draw], in CSS pixels from the map's top left corner. */
    val pinLeft = DoubleArray(rooms.size)
    val pinTop = DoubleArray(rooms.size)

    /** Where each named place's name went at the last [paint]: its box's left, top, right and bottom, in CSS pixels. */
    val names = mutableMapOf<Place, DoubleArray>()

    /** True once the map has been painted ([paint]). For tests. */
    var painted = false
        private set

    /** True once the screen it is on has gone ([close]): a paint still waiting does nothing. */
    private var closed = false

    /**
     * When the map was put on the page ([draw]), on the clock of the page it is in, which a tap's time is told on. A tap
     * made before then, queued while the page was busy (a second, impatient tap on 시작하기, landing where a pin now
     * is), picks nothing: a pin answers only taps made once it was there.
     */
    private var shownAt = window.performance.now()

    /**
     * Paints the map again when its page changes size (a phone turned, a window resized or zoomed) while it is up, so
     * the picture stays sharp and the names stay clear of the pins; the page it listens to, and the width last painted.
     */
    private val onResize: (Event) -> Unit = { if (!closed && element.clientWidth.toDouble() != paintedWidth) paint() }
    private var listeningTo: Window? = null
    private var paintedWidth = 0.0

    init {
        element.className = "room-map"
        element.setAttribute("role", "group")
        element.setAttribute("aria-label", "방 ${rooms.size}곳이 있는 지도")
        // Where it sits and its shape are set here, as for the small map, so it keeps them whatever else is on the
        // page; how it looks is in the page's style.
        element.style.position = "relative"
        element.style.width = "100%"
        element.style.setProperty("aspect-ratio", "$AREA_WIDTH / $AREA_HEIGHT")
        canvas.setAttribute("aria-hidden", "true")
        canvas.style.display = "block"
        canvas.style.width = "100%"
        canvas.style.height = "100%"
        element.appendChild(canvas)
        pins = rooms.mapIndexed { index, room ->
            val pin = document.createElement("button") as HTMLButtonElement
            pin.type = "button"
            pin.className = "room-pin"
            pin.setAttribute("data-room", "$index")
            pin.setAttribute("aria-pressed", (index == picked).toString())
            pin.disabled = locked
            pin.style.position = "absolute"
            pin.style.width = "${PIN}px"
            pin.style.height = "${PIN}px"
            pin.style.margin = "-${PIN / 2}px 0 0 -${PIN / 2}px"
            // Seen, its number; heard, the room in full, as the list of rooms said it.
            pin.appendChild(span("room-pin-number", "${index + 1}번"))
            val zone = room.zone.number
            val heard = " 방, 월세 ${money(room.rent)}, 교통 ${zone}구역, ${areaOf(zone)}, ${utilitiesLabel(room)}"
            pin.appendChild(span("visually-hidden", heard))
            pin.onclick = { event -> if (event.timeStamp.toDouble() >= shownAt) onPick(index) }
            element.appendChild(pin)
            pin
        }
    }

    /**
     * Places the pins now, at the size the map has on the page, and paints the map just after the screen is first drawn,
     * so the screen answers at once and the picture follows ([paint]).
     */
    fun draw() {
        val page = element.ownerDocument?.defaultView ?: window
        shownAt = page.performance.now()
        if (listeningTo == null) {
            page.addEventListener("resize", onResize)
            listeningTo = page
        }
        place(measured())
        // In the frame the screen first shows, then straight after it is drawn.
        window.requestAnimationFrame { window.setTimeout({ if (!closed) paint() }, 0) }
    }

    /**
     * Paints the map, and places the pins again, at the size it has on the page now. Before the page lays it out,
     * nothing is painted.
     */
    fun paint() {
        val map = measured()
        place(map)
        val c = context ?: return
        if (element.clientWidth == 0 || element.clientHeight == 0) return
        painted = true
        paintedWidth = element.clientWidth.toDouble()
        val width = map.width
        val height = map.height
        val ratio = min(window.devicePixelRatio, 2.0)
        canvas.width = (width * ratio).roundToInt()
        canvas.height = (height * ratio).roundToInt()
        c.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)
        Minimap.paintGround(c, map)
        val scale = Minimap.LARGE_SCALE
        Minimap.drawMarks(c, map, scale)
        // The places the days are spent at, as on the larger map.
        for (venue in Neighbourhood.venues) {
            if (venue.place == Place.HOME) continue
            Minimap.dot(c, middleLeft(map, venue), middleTop(map, venue), Minimap.DOT * scale, Minimap.PLACE, false)
        }
        // Each room's door in 내 방's red, under its pin; from a pin pushed off it, a line back to it.
        for (at in doors.indices) {
            val left = map.left(doors[at].x, doors[at].z)
            val top = map.top(doors[at].x, doors[at].z)
            if (hypot(pinLeft[at] - left, pinTop[at] - top) > 1.0) {
                c.beginPath()
                c.moveTo(left, top)
                c.lineTo(pinLeft[at], pinTop[at])
                c.lineWidth = 2.0
                c.strokeStyle = Minimap.INK
                c.stroke()
            }
            Minimap.dot(c, left, top, Minimap.BIG_DOT * scale, Minimap.HOME, square = true)
        }
        Minimap.north(c, width - Minimap.NORTH_INSET * scale, Minimap.NORTH_TOP * scale, scale, large = true)
        // Every 회사 and 카페 by its kind, placed as the town's whole map places names: over the dot, or else the first
        // spot round it that covers no pin and no other name, with a short line back to the dot.
        names.clear()
        val lift = Minimap.BIG_DOT * scale + 3
        for (place in NAMED) {
            val venue = Neighbourhood.venue(place)
            val dotLeft = middleLeft(map, venue)
            val dotTop = middleTop(map, venue)
            val wide = Minimap.measureName(c, place.kind.label)
            // With no clear spot the name is left out, its dot still drawn, rather than laid over a pin or a name.
            val spot = Minimap.NAME_SPOTS_X.indices.firstOrNull { each ->
                val foot = dotTop + Minimap.NAME_SPOTS_Y[each] - lift
                isNameClear(map, nameBox(dotLeft + Minimap.NAME_SPOTS_X[each], foot, wide))
            } ?: continue
            val left = dotLeft + Minimap.NAME_SPOTS_X[spot]
            val top = dotTop + Minimap.NAME_SPOTS_Y[spot] - lift
            if (spot > 0) Minimap.leader(c, dotLeft, dotTop, left, top)
            names[place] = nameBox(left, top, wide)
            Minimap.name(c, place.kind.label, left, top)
        }
    }

    /** Lets the map go with its screen: a paint still waiting does nothing, and it stops listening to the page. */
    fun close() {
        closed = true
        listeningTo?.removeEventListener("resize", onResize)
        listeningTo = null
    }

    /** The whole map at the size it has on the page; before the page lays it out, a 375-point phone's width. */
    private fun measured(): MinimapProjection {
        val width = element.clientWidth.toDouble()
        val height = element.clientHeight.toDouble()
        return if (width > 0.0 && height > 0.0) {
            MinimapProjection(width, height)
        } else {
            MinimapProjection(NOMINAL_WIDTH, NOMINAL_WIDTH * AREA_HEIGHT / AREA_WIDTH)
        }
    }

    /**
     * Puts each pin's middle on its door on [map], then pushes any two closer than [APART] away from each other, half
     * the way each, keeping every pin wholly on the map.
     */
    private fun place(map: MinimapProjection) {
        for (at in doors.indices) {
            pinLeft[at] = map.left(doors[at].x, doors[at].z)
            pinTop[at] = map.top(doors[at].x, doors[at].z)
        }
        val half = PIN / 2.0
        repeat(SPREAD_ROUNDS) {
            for (a in doors.indices) {
                for (b in a + 1 until doors.size) {
                    val across = pinLeft[b] - pinLeft[a]
                    val down = pinTop[b] - pinTop[a]
                    val apart = hypot(across, down)
                    if (apart >= APART) continue
                    // Two on the very same spot part sideways.
                    val wayAcross = if (apart > 0.0) across / apart else 1.0
                    val wayDown = if (apart > 0.0) down / apart else 0.0
                    val push = (APART - apart) / 2
                    pinLeft[a] -= wayAcross * push
                    pinTop[a] -= wayDown * push
                    pinLeft[b] += wayAcross * push
                    pinTop[b] += wayDown * push
                }
            }
            for (at in doors.indices) {
                pinLeft[at] = pinLeft[at].coerceIn(half, max(half, map.width - half))
                pinTop[at] = pinTop[at].coerceIn(half, max(half, map.height - half))
            }
        }
        // As shares of the map, so the pins stay on their doors when the map is drawn again larger or smaller.
        for (at in pins.indices) {
            pins[at].style.left = "${pinLeft[at] / map.width * 100}%"
            pins[at].style.top = "${pinTop[at] / map.height * 100}%"
        }
    }

    /** The box of a name [wide] pixels wide whose foot's middle is at [left], [top]: left, top, right, bottom. */
    private fun nameBox(left: Double, top: Double, wide: Double): DoubleArray =
        doubleArrayOf(left - wide / 2, top - Minimap.NAME_HEIGHT, left + wide / 2, top)

    /** True when [box] lies on [map] and covers no pin and no name placed before it. */
    private fun isNameClear(map: MinimapProjection, box: DoubleArray): Boolean {
        if (box[0] < 0.0 || box[1] < 0.0 || box[2] > map.width || box[3] > map.height) return false
        val reach = PIN / 2.0 + Minimap.NAME_GAP
        if (pinLeft.indices.any { at -> covers(box, pinLeft[at], pinTop[at], reach) }) return false
        val gap = Minimap.NAME_GAP
        return names.values.none { other ->
            box[0] < other[2] + gap && other[0] < box[2] + gap && box[1] < other[3] + gap && other[1] < box[3] + gap
        }
    }

    private fun middleLeft(map: MinimapProjection, venue: Venue): Double =
        map.left(venue.x + venue.width / 2.0, venue.z + venue.depth / 2.0)

    private fun middleTop(map: MinimapProjection, venue: Venue): Double =
        map.top(venue.x + venue.width / 2.0, venue.z + venue.depth / 2.0)

    companion object {
        /** A pin's size, across and down, in CSS pixels: a finger's, as every button on the page. */
        const val PIN = 44

        /** How far apart two pins' middles must be, in CSS pixels, or they are pushed apart. */
        const val APART = PIN + 2.0

        /** The places named on the map: where the job hunt goes, and where the café shifts are, every one of each. */
        val NAMED = listOf(PlaceKind.OFFICE, PlaceKind.CAFE).flatMap { kind -> Place.entries.filter { it.kind == kind } }

        /** How many times the pins are pushed apart and kept on the map, enough for three to settle. */
        private const val SPREAD_ROUNDS = 6

        /** The map's width before the page lays it out: the column of a 375-point phone. */
        private const val NOMINAL_WIDTH = 343.0

        /**
         * Where a room in [zone] stands, in a word, for a screen reader, which does not see the map: the West End for
         * zone 1, Burnaby for the others, as 내 방 does ([Neighbourhood.homes]).
         */
        fun areaOf(zone: Int): String = if (zone == 1) "웨스트엔드" else "버나비"

        /** True when [box] comes within [reach] of the spot [left], [top]. */
        fun covers(box: DoubleArray, left: Double, top: Double, reach: Double): Boolean =
            hypot(left - left.coerceIn(box[0], box[2]), top - top.coerceIn(box[1], box[3])) < reach

        private fun span(className: String, text: String): HTMLElement =
            (document.createElement("span") as HTMLElement).also {
                it.className = className
                it.textContent = text
            }
    }
}
