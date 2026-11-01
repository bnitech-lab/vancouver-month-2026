package vancouvermonth.web

import kotlin.js.json
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.RenderingContext
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent
import vancouvermonth.text.Passer
import vancouvermonth.text.Shop
import vancouvermonth.text.Transport
import vancouvermonth.text.Weather

/**
 * What a frame of the town costs: draws, triangles, boxes (twelve triangles each) and milliseconds. [draws], [triangles]
 * and [boxes] are the picture's alone; [shadowDraws] and [shadowBoxes] what the sun's shadows took besides, drawn first.
 * For the budget test.
 */
internal data class FrameCost(
    val draws: Int,
    val triangles: Int,
    val boxes: Int,
    val millis: Double,
    val shadowDraws: Int = 0,
    val shadowBoxes: Int = 0,
) {
    /** The picture's and the shadows' together. */
    val allDraws: Int
        get() = draws + shadowDraws
    val allBoxes: Int
        get() = boxes + shadowBoxes
}

/** What the phone screen needs from the town the player walks in. */
interface WalkableTown {
    /** The whole town, to fill the screen. */
    val element: HTMLElement

    /**
     * Called with the place the player turns to: they tapped its building, its name or its person while at it, or the
     * character came to its door or its person on a walk that opens it. Called with null when they walk off somewhere
     * else. A tap on a place from afar only sends the character there ([onSentTo]).
     */
    var onPlace: (Place?) -> Unit

    /**
     * Called with the passerby the player stops for a word: they tapped them. It costs no stamina. The passerby
     * stands and faces the player until [endChat].
     */
    var onPasserby: (Passer) -> Unit

    /**
     * Called when the player turns to the bike shop's keeper: they tapped the shop, its name or the keeper while at it,
     * or the character came to him on a walk that opens it. A tap from afar only sends the character there ([onSentTo]).
     */
    var onShop: () -> Unit

    /**
     * Called when a tap on a far place or on the far bike shop sends the character off, with the [name] of where it
     * goes and how many [squares] the way is: the talk there opens on arrival. Called with null when no such walk is
     * under way any more: it got there, stopped, or was sent somewhere else. A walk of a few squares is not told, as
     * a line about it would only come and go.
     */
    var onSentTo: (name: String?, squares: Double) -> Unit

    /**
     * Called when the character reaches a shared bike or e-scooter the player tapped, with the ride it would be. The
     * game offers where to ride it, each place with its fare by the way there ([taxiDistance]), pays the fare of the
     * place chosen and calls [rideTo]; or says why it cannot.
     */
    var onRent: (Ride) -> Unit

    /** What the character rides now, or null on foot. */
    val riding: Ride?

    /**
     * True when [ride] would get the character on now: not in a night's sleep, nor sitting by the fire, nor already
     * riding; a shared one only when the character has just reached one of its kind. The game pays a fare only when
     * this is true, so the fare and the ride never part.
     */
    fun canRide(ride: Ride): Boolean

    /**
     * Gets on [ride]: one of the player's own, or the shared one the character has just reached (asked for through
     * [onRent]). From here the character goes at the ride's speed, along the same streets. Not in a night's sleep, nor
     * sitting by the fire, nor already riding.
     */
    fun ride(ride: Ride)

    /**
     * True when [ride], the shared one the character has just reached, would take it to [to] now: [canRide], and a way
     * there along the streets. The game pays the fare for [to] only when this is true.
     */
    fun canRideTo(ride: Ride, to: Spot): Boolean = canRide(ride)

    /**
     * Gets on [ride], the shared one the character has just reached, and rides it to [to] along the way its fare was
     * worked out for ([taxiDistance]). There the character gets off, the shared one goes back, and [onTravelled] is
     * called; at one of the places or the bike shop the talk there then opens, as at the end of a walk there. On the way
     * the town takes no taps, and [getOff] gets off where it is.
     */
    fun rideTo(ride: Ride, to: Spot) = ride(ride)

    /**
     * Gets off and goes on foot: a shared bike or e-scooter goes back where it was rented. A walk on its way goes on
     * at walking pace. The game asks for this whenever a talk opens; the town does it itself when the day ends.
     */
    fun getOff()

    /**
     * Called when the character reaches a station, a bus stop or a dock the player tapped, with that stop: the game
     * offers where to go from there.
     */
    var onStop: (Stop) -> Unit

    /**
     * Called when a ride on the SkyTrain, a bus, the water bus or a taxi is over, or a shared one ridden to a place got
     * there: the character is on foot again.
     */
    var onTravelled: () -> Unit

    /**
     * What the character travels on now (the SkyTrain, a bus, the water bus or a taxi), or null. Meanwhile the town
     * takes no taps and no name opens a talk; the camera follows the vehicle.
     */
    val travelling: Transport?

    /**
     * How far a taxi from where the character stands drives to [to], along the streets, and a shared bike or e-scooter
     * rides; null when it cannot get there.
     */
    fun taxiDistance(to: Spot): Double?

    /**
     * True when a ride on [transport] from stop [from] to stop [to] could start now: not in a night's sleep, nor
     * sitting by the fire, nor already travelling; the character at [from]; for a taxi ([from] -1), a way along the
     * streets to the place [to] of [TransitLines.taxiStops]. The game pays a fare only when this is true.
     */
    fun canTravel(transport: Transport, from: Int, to: Int): Boolean

    /**
     * Sets off on [transport] from stop [from] to stop [to] (for a taxi, from where the character stands to the place
     * [to]): off whatever the character rode, into the vehicle, and out at [to]'s stop, where [onTravelled] is called.
     * Out of a taxi at one of the places or the bike shop, the talk there then opens, as at the end of a walk there.
     */
    fun travel(transport: Transport, from: Int, to: Int)

    /** Stops the walk under way where the character is, as for calling a taxi. */
    fun standStill()

    /** Gets out of the taxi where it is now: the character stands there on foot, the taxi drives on, nothing opens. */
    fun leaveTaxi()

    /** Called once if the town stops drawing, so the game can go on with buttons. */
    var onFail: () -> Unit

    /**
     * Called when the change of day is over and the character stands at 내 방's door. From [endDay] until then, the
     * town takes no taps, drags, pinches or names.
     */
    var onMorning: () -> Unit

    /**
     * Called when a walk that opens nothing itself ends at a place, with that place: a talk already open with it may
     * offer more now that the character has come.
     */
    var onArrived: (Place) -> Unit

    /**
     * True when the character is at [place]: within reach of its door or its person, on the few steps between them, or,
     * at 내 방, by its fire at night. What is done at a place is done only there, and a night's sleep too, so nothing is
     * ever done from afar and nothing ever takes the character home.
     */
    fun isAt(place: Place): Boolean

    /**
     * True while a card covers most of the town, as a talk does. The town's own life then moves at a slower beat,
     * to spare the battery.
     */
    var isCovered: Boolean

    /**
     * Today's weather: rain falls on a rainy day, and the sky greys on a cloudy or a rainy one. Set while the day
     * changes, it comes in the dark of the night, so the morning wakes to it.
     */
    var weather: Weather

    /**
     * Called as the town's own life moves, with how loud its sounds are where the player is, each from 0.0 to 1.0:
     * the passing [train] at the camera, how near the campfire the character is ([fire]), and how dark the town's own
     * night is ([night]). What to play is the sound's to decide.
     */
    var onSounds: (train: Double, fire: Double, night: Double) -> Unit

    /**
     * How far down from the town's top edge, in CSS pixels, cards cover it now: the top card and the day's log. A
     * building's name that would reach up under them is hidden, so it never peeks out from under a card. It is asked
     * each time the names are laid out; setting it lays them out again.
     */
    var coveredTop: () -> Double

    /** Marks the name of [place], as on an interview day, or no name when null. */
    fun mark(place: Place?)

    /** Lets the passerby the player stopped walk on: the game closed the small talk. */
    fun endChat()

    /**
     * Ends the day with a night's sleep at 내 방: from the time it is, on through the evening to a night with the lights
     * on, spent on the bench by the campfire, then morning, with the character at 내 방's door and [onMorning] called.
     */
    fun endDay()

    /**
     * Moves the time of day on to [share] of the day (from 0.0 in the morning to 1.0 at night), from the time shown
     * now, over [DayLight.PASS_MILLIS]: the sky, the sun, the lamps and the windows change little by little, and the
     * character stays where it is. A phone that asks for less motion sees the new time at once.
     */
    fun passTime(share: Double)

    /** Shows the time of day [share] at once, as for a month taken up again at that time. */
    fun showTime(share: Double)

    /** Lets go of the drawing for good: the renderer, the shapes and the colours. */
    fun close()

    /** The zone of the room 내 방 stands for: where it stands, and where each morning starts ([HomeSite]). */
    val homeZone: Int
        get() = 1
}

/**
 * The town to walk in, or null when this browser should not draw it: the phone asks for less motion, there is no
 * WebGL 2 (which Three.js needs), or anything fails while setting it up. Then the month is played with buttons.
 * A test can hand in a [canvas] without WebGL.
 */
fun openWalkableTown(
    canvas: HTMLCanvasElement = document.createElement("canvas") as HTMLCanvasElement,
    lessMotion: Boolean = window.matchMedia("(prefers-reduced-motion: reduce)").matches,
    homeZone: Int = 1,
): WalkableTown? {
    if (lessMotion) return null
    return try {
        // Asking for WebGL before Three.js does lets a browser without it fail quietly.
        val context = canvas.getContext("webgl2", json("antialias" to true, "alpha" to false))
        if (context == null) null else NeighbourhoodScene(canvas, context, homeZone)
    } catch (error: Throwable) {
        console.warn("The town could not be drawn:", error)
        null
    }
}

/**
 * Draws [Neighbourhood] with Three.js and lets the player walk in it: a tap on the ground walks there around
 * everything in the way, a double tap dashes, a drag turns the camera and a pinch or the mouse wheel zooms.
 * The town lives on its own too: the townspeople look about and turn to the player, passersby stroll, the SkyTrain
 * comes and goes, the campfire flickers and the water glints. That life is drawn at most [FramePace.AMBIENT_FPS]
 * frames a second; a walk, the camera moving or the change of day are drawn at the screen's full rate; and nothing
 * is drawn while the page is hidden. The city's buildings in the camera's way fold down out of it ([Cutaway]), and a
 * small map in the corner shows where the character is ([Minimap]).
 */
internal class NeighbourhoodScene(
    private val canvas: HTMLCanvasElement,
    context: RenderingContext,
    override val homeZone: Int = 1,
) : WalkableTown {
    /** The town as lived in for the room's zone: 내 방 where the room is, and the ground to walk on with it. */
    private val site = HomeSite.of(homeZone)

    /**
     * Where a taxi goes from this zone, and its way from where the character stands to each place, worked out once
     * while it stands there: all of them in one search over the ground, as the wide map's ways are long. Set before
     * the town is built, so nothing that builds it can find them unset.
     */
    private val taxiStops = TransitLines.taxiStopsFor(homeZone)
    private var taxiFrom: Spot? = null
    private val taxiWays = mutableMapOf<Spot, List<Spot>?>()

    /** Every box that stands still: the town's, then 내 방's; and every glowing one, likewise. */
    private val stillBlocks = Neighbourhood.blocks + site.blocks
    private val glowingBlocks = Neighbourhood.glowing + site.glowing

    /** The light glinting on the sea downtown, off this zone's places. */
    private val shimmer = Shimmer.of(homeZone)

    override val element = document.createElement("div") as HTMLElement
    override var onPlace: (Place?) -> Unit = {}
    override var onPasserby: (Passer) -> Unit = {}
    override var onShop: () -> Unit = {}
    override var onSentTo: (String?, Double) -> Unit = { _, _ -> }
    override var onRent: (Ride) -> Unit = {}
    override var onStop: (Stop) -> Unit = {}
    override var onTravelled: () -> Unit = {}
    override var onFail: () -> Unit = {}
    override var onMorning: () -> Unit = {}
    override var onArrived: (Place) -> Unit = {}

    override fun isAt(place: Place): Boolean {
        if (place == Place.HOME && sitting) return true
        if (site.placeAt(spot) == place) return true
        // Stepping up from its door to its person, the character has not left the place.
        val steps = trip?.route ?: return false
        return site.placeAt(steps.first()) == place && site.placeAt(steps.last()) == place
    }

    override var onSounds: (Double, Double, Double) -> Unit = { _, _, _ -> }

    private val renderer = WebGLRenderer(json("canvas" to canvas, "context" to context))
    private val scene = Scene()
    private val camera = PerspectiveCamera(60.0, 1.0, FollowCamera.NEAR_PLANE, FollowCamera.FAR_PLANE)
    private val sky = Color(DayLight.MORNING.sky)
    private val haze = Fog(DayLight.MORNING.sky, Neighbourhood.HAZE_NEAR, Neighbourhood.HAZE_FAR)
    private val ambient = HemisphereLight(DayLight.MORNING.ambientColour, GROUND_LIGHT, DayLight.MORNING.ambient)
    private val sun = DirectionalLight(DayLight.MORNING.sunColour, DayLight.MORNING.sun)

    // Every box is one shared cube, stretched to size. Boxes are drawn many at once, each in its own colour,
    // with cartoon shading: the windows, lamps and fire in a material that glows after dark.
    private val cube = BoxGeometry(1.0, 1.0, 1.0)
    private val shades = toonShades()
    private val material = MeshToonMaterial(json("gradientMap" to shades))
    private val glowMaterial = MeshToonMaterial(json("gradientMap" to shades, "emissive" to GLOW))
    private val meshes = mutableListOf<InstancedMesh>()

    /** The platform under downtown and its tunnel, drawn instead of the town while a ride is down there. */
    private val underground = UndergroundScene(cube, shades)

    /** True once the underground's shaders and sign are ready, readied while the town idles after its first frame. */
    private var undergroundReady = false

    /**
     * The still boxes in their tiles, and for each of [Neighbourhood.blocks] which tile it is drawn in and where in it;
     * the glowing windows and glass in two draws, the town's corner's and the wide map's, and for each glowing box which
     * of them and where in it. A building that folds is put again where it is drawn, and nowhere else.
     */
    private val tiles = mutableListOf<InstancedMesh>()
    private val tileOf = IntArray(stillBlocks.size) { NOT_TILED }
    private val slotOf = IntArray(stillBlocks.size)
    private val glowDraws = mutableListOf<InstancedMesh>()
    private val glowDrawOf = IntArray(glowingBlocks.size)
    private val glowSlotOf = IntArray(glowingBlocks.size)

    /**
     * Each draw of the town that may be left out when far from the camera: where its middle is, how far its boxes reach
     * from it, and whether it holds anything tall. The haze is linear, so a low box still shows some of its own colour
     * until the haze's far end: a low draw is left out only once its nearest box is [LOW_FOG] of the way into the haze
     * as it is now (about 214 squares on a clear day, nearer in the rain), where it shows a tenth of its colour at
     * most; a tall one, which shows over the haze longer, only at the far plane. A reach below 0 marks a low draw.
     */
    private val farMeshes = mutableListOf<InstancedMesh>()
    private var farX = DoubleArray(0)
    private var farZ = DoubleArray(0)
    private var farRadius = DoubleArray(0)
    private var farReach = DoubleArray(0)

    /** The city's buildings that fold down out of the camera's way, and where the camera and character last were. */
    private val cutaway =
        Cutaway(Neighbourhood.foldables.map { it.box }, Neighbourhood.foldables.map { Neighbourhood.blocks[it.body] })

    /**
     * The suburbs' houses near the camera, walls, roofs and doors, with their lawns and yard trees ([HouseDetail]), in
     * two draws written again whenever the camera moves or a house folds: those round the character, which cast the
     * sun's shadows, and the rest, which cast none; and where the camera was when they were last written. The houses'
     * own walls among the still boxes are not drawn there ([isHouse]); a block whose houses are drawn near the camera
     * has its rows' low boxes put away while they are.
     */
    private lateinit var casterDetails: InstancedMesh
    private lateinit var details: InstancedMesh
    private val isHouse = BooleanArray(stillBlocks.size).also { house ->
        for (each in Neighbourhood.houses) house[each.body] = true
    }
    private lateinit var houseDetail: HouseDetail
    private val foldOf: (Int) -> Double = { cutaway.foldOf(it) }
    private val detailColour = Color(0)
    private var detailEyeX = Double.NaN
    private var detailEyeY = Double.NaN
    private var detailEyeZ = Double.NaN
    private var detailFocusX = Double.NaN
    private var detailFocusY = Double.NaN
    private var detailFocusZ = Double.NaN
    private var detailAcross = Double.NaN
    private var detailStale = true
    private var aimedEyeX = Double.NaN
    private var aimedEyeY = Double.NaN
    private var aimedEyeZ = Double.NaN
    private var aimedHeadX = Double.NaN
    private var aimedHeadZ = Double.NaN

    /**
     * The small map in the corner, and what it last showed: the character, the way it faced and the camera. It is drawn
     * again only when one of them moved, it was resized, or the day's place to go changed.
     */
    private val minimap = Minimap(site)
    private var mapX = Double.NaN
    private var mapZ = Double.NaN
    private var mapHeading = Double.NaN
    private var mapEyeX = Double.NaN
    private var mapEyeZ = Double.NaN
    private var mapStale = true
    private var mapResized = true
    private var mapTop = Double.NaN

    /** True once drawing the map has failed: it is hidden, and the town goes on without it. */
    private var mapBroken = false
    private var marked: Place? = null

    /** How much the camera sees from side to side, in radians. */
    private var across = 1.0

    // The town's own life. Everything in it moves by the town's own time, [lifeClock], which a test may set.
    internal var lifeClock: () -> Double = { window.performance.now() }
    private var lifeNow = lifeClock()
    private val greeters = site.venues.mapIndexed { index, venue ->
        Greeter(venue.person, venue.facing, phase = index * 1.3)
    }
    private val greeterParts = site.venues.map { Figures.townsperson(personAt(it.place)) }
    private val greeterFigures = greeterParts.map { FigureBoxes(it) }

    /**
     * Which draw of [folkDraws] each place's person is in: 0, the town's own, for downtown's places and 내 방; 1 for the
     * places out in East Vancouver and 2 for those in Burnaby, each left out from far off as the city round them is.
     */
    private val folkDrawOf = IntArray(site.venues.size) { index ->
        val venue = site.venues[index]
        when {
            venue.place == Place.HOME || venue.x < Neighbourhood.TOWN_COLUMNS -> 0
            venue.x < CITY_LINE -> 1
            else -> 2
        }
    }

    /** Where the next person goes in each of [folkDraws] as they are placed: worked out again each step of life. */
    private val folkFirst = IntArray(FOLK_DRAWS)

    /** The lots of the places out past downtown, whose own boxes are drawn apart from the city round them. */
    private val placeLots = site.venues.filter { it.x >= Neighbourhood.TOWN_COLUMNS && it.place != Place.HOME }
        .map { Neighbourhood.lotFor(it) }

    /** The bike shop's keeper, who looks about and greets the player as the places' people do. */
    private val keeper = Greeter(Neighbourhood.shop.keeper, Neighbourhood.shop.facing, phase = greeters.size * 1.3)
    private val keeperFigure = FigureBoxes(Figures.keeper)
    private val passersby = Strolls.all()
    private val passerbyParts = passersby.map { Figures.passer(it.passer) }
    private val passerbyFigures = passerbyParts.map { FigureBoxes(it) }

    /** The passersby dressed for the rain, two with umbrellas open, drawn in their place on a rainy day. */
    private val wetPasserbyParts = passersby.map { Figures.passer(it.passer, Weather.RAINY) }
    private val wetPasserbyFigures = wetPasserbyParts.map { FigureBoxes(it) }

    /** The passersby fade out together in the evening and back in at dawn, so they share a material that can fade. */
    private val passerbyMaterial = MeshToonMaterial(json("gradientMap" to shades, "transparent" to true))
    private lateinit var townsfolk: InstancedMesh
    private val folkDraws = mutableListOf<InstancedMesh>()
    private lateinit var passerbyBoxes: InstancedMesh
    private lateinit var wetPasserbyBoxes: InstancedMesh

    /** The passerby the player has stopped for a word, who stands and faces them for a while. */
    private var chatting: Passerby? = null

    /** The train's three cars, their bodies in one draw and their windows in another, placed along the guideway. */
    private lateinit var trainCars: InstancedMesh
    private lateinit var trainWindows: InstancedMesh

    /** The train fades out far down the line, so it has materials of its own that can fade. */
    private val trainMaterial = MeshToonMaterial(json("gradientMap" to shades, "transparent" to true))
    private val trainGlowMaterial =
        MeshToonMaterial(json("gradientMap" to shades, "emissive" to GLOW, "transparent" to true))
    private val flames = Group()
    private val flameMaterial = MeshToonMaterial(json("gradientMap" to shades, "emissive" to GLOW))
    private val fireLight = PointLight(FIRE_COLOUR, 0.0, FIRE_REACH, 2.0)
    private lateinit var drifting: InstancedMesh

    /** How brightly the windows and lamps glow now, from 0.0 by day to 1.0 at night; the fire burns as bright. */
    private var glowLevel = 0.0

    /** How far the train is from its stop at the station, as last drawn. */
    private var trainShift = 0.0

    /**
     * Where along the guideway the line's own train stands at the station it runs at now: the one nearest 내 방 at
     * first (by the tunnel's mouth, from the West End), and then the one nearest where the player looks
     * ([SkyTrainRun.nearestStand]).
     */
    private var runStand = SkyTrainRun.nearestStand(site.home.door.x, site.home.door.z)

    /** The weather drawn now, and the one to come in the dark when it was set while the day changed. */
    private var shownWeather = Weather.SUNNY
    private var comingWeather: Weather? = null

    /**
     * The light as the time of day has it, before the weather greys it, and as it is drawn, greyed: written over on
     * every frame of a change of light, never made anew.
     */
    private val shownLight = TownLight().set(DayLight.MORNING)
    private val drawnLight = TownLight()

    /** The sea, one plane under the whole town, which darkens in the rain. */
    private lateinit var sea: InstancedMesh

    /** The rain's streaks and splashes in one draw, in a plain pale colour no light changes; the puddles in another. */
    private val rainMaterial = MeshBasicMaterial(
        json(
            "color" to 0xFFFFFF,
            "transparent" to true,
            "opacity" to RAIN_OPACITY,
            "depthWrite" to false,
            "side" to DoubleSide,
        ),
    ).also {
        // Both faces in one pass: one plain colour gains nothing from three.js drawing the back faces first.
        it.forceSinglePass = true
    }

    /** The flat square each streak of rain and each splash is drawn from. */
    private val drop = PlaneGeometry(1.0, 1.0)
    private lateinit var rain: InstancedMesh
    private lateinit var puddles: InstancedMesh

    /** True when the page is not on screen. A test may set it. */
    internal var pageHidden: () -> Boolean = { document.asDynamic().hidden == true }
    private val lessMotion = window.matchMedia("(prefers-reduced-motion: reduce)")

    /**
     * True when the phone asks for less motion now: the one switch for the town's own life, the time passing, the night
     * and the underground. A test may set it.
     */
    internal var asksLessMotion: () -> Boolean = { lessMotion.matches }

    private val pace = FramePace(openedAt = window.performance.now())

    private val character = Group()
    private val limbs = mutableMapOf<Limb, Group>()
    private lateinit var leftLeg: Group
    private lateinit var rightLeg: Group
    private lateinit var leftArm: Group
    private lateinit var rightArm: Group
    private val marker = Group()
    private val labels = mutableMapOf<Place, HTMLButtonElement>()
    private val shopLabel = document.createElement("button") as HTMLButtonElement

    /**
     * The bikes and e-scooters: those standing in the town in one draw, with each box's place kept so a rented one can
     * be put back; and under the character the one it rides, one draw for each [Ride], only the ridden one shown.
     */
    private lateinit var parked: InstancedMesh
    private lateinit var parkedMatrices: Float32Array
    private val mount = Group()
    private val rideMeshes = mutableListOf<InstancedMesh>()

    /** What the character rides, and which of [Rides.shared] it was rented from (-1 for none, or its own). */
    private var ride: Ride? = null
    private var rentedFrom = -1

    /**
     * The shared one the character is on its way to, to rent on arrival; and the one it has reached and stands by,
     * while the game offers where to ride it. -1 for none.
     */
    private var rentAt = -1
    private var renting = -1

    /** Where the shared one ridden now goes, the place its fare was paid for; null for none. */
    private var rideTo: Spot? = null

    /** The station, stop or dock the character is on its way to, by its place in [TransitLines.tapStops]; -1 for none. */
    private var stopAt = -1

    /**
     * The buses, the water buses and the taxi, in one draw: the bus and the water bus that run their loops on their own,
     * and the one of each the character rides, with the taxi, shown only for a ride.
     */
    private lateinit var vehicles: InstancedMesh
    private val busRun = LoopRun(
        TransitLines.busLoop,
        TransitLines.busLines[0].stops.map { it.along }.toDoubleArray(),
        Journey.BUS_SPEED,
        Journey.BUS_RAMP,
        BUS_DWELL,
    )

    /** The bus of the line out to Burnaby, on its own way out and back. */
    private val burnabyRun = LoopRun(
        TransitLines.burnabyLoop,
        TransitLines.busLines[1].stops.map { it.along }.toDoubleArray(),
        Journey.BUS_SPEED,
        Journey.BUS_RAMP,
        BUS_DWELL,
    )
    private val boatRun = LoopRun(
        TransitLines.boatLoop,
        DoubleArray(TransitLines.docks.size) { TransitLines.docks[it].along },
        Journey.BOAT_SPEED,
        Journey.BOAT_RAMP,
        BOAT_DWELL,
    )

    /**
     * The ride under way and when it began; and the vehicle going on after the character got out, and when its ride
     * began. For the SkyTrain, the train itself is the ride's until it has gone on out of the way, and then the line's
     * own train comes back, faded in from when [trainBackAt].
     */
    private var journey: Journey? = null
    private var journeyStarted = 0.0
    private var leaving: Journey? = null
    private var leavingStarted = 0.0
    private var trainRide: Journey? = null
    private var trainRideStarted = 0.0
    private var trainFadesIn = true
    private var trainBackAt = Double.NEGATIVE_INFINITY

    /** Which ride's vehicle is drawn, so it is hidden once, when it is gone. */
    private var vehicleShown: Transport? = null

    /** How high the camera looks: at the character, or up at the train on its guideway. */
    private var focusHeight = FOCUS_HEIGHT

    /**
     * True while the ride's train is under downtown: the platform there and the tunnel are drawn instead of the town
     * ([UndergroundScene]), and the character stands for the train on the small map.
     */
    private var focusUnderground = false

    /**
     * Where a ride's vehicle, and the loop's bus or boat, are worked out in place, as x, z and the turn: nothing new is
     * made for them each frame.
     */
    private val ridePlace = DoubleArray(3)
    private val loopPlace = DoubleArray(3)
    private val trainPlace = DoubleArray(3)

    /** Where the character stands on the water bus, worked out each frame of the ride. */
    private var aboardX = 0.0
    private var aboardY = 0.0
    private var aboardZ = 0.0

    /** Where the camera was when the names were last laid out, and whether the town was resized since. */
    private var labelsEye: Point? = null
    private var labelsFocus: Point? = null
    private var labelsStale = true

    private val home = site.home
    private val view = FollowCamera(Point(home.door.x, FOCUS_HEIGHT, home.door.z), site.walls, site.people).also {
        // The first view, turned as 내 방's site asks.
        it.aim(site.startYaw, FollowCamera.START_PITCH, FollowCamera.START_DISTANCE)
        it.jumpTo(Point(home.door.x, FOCUS_HEIGHT, home.door.z))
    }
    private val taps = TapTimer()
    private val resizeObserver = ResizeObserver { fit() }

    /** The small map is measured again only when it changed size, not each time the town is fitted. */
    private val minimapObserver = ResizeObserver {
        mapResized = true
        askForFrame()
    }

    /** Where the character stands, or has got to on a walk, and the way it faces. */
    private var spot = home.door
    private var heading = home.facing
    private var trip: Trip? = null
        set(value) {
            field = value
            if (value == null) sentTo = null
        }
    private var tripStarted = 0.0

    /**
     * The name of where a tap from afar sent the walk under way, whose talk opens on arrival; null for any other walk.
     * The game hears each change ([onSentTo]).
     */
    private var sentTo: String? = null
        set(value) {
            if (field == value) return
            field = value
            val squares = trip?.length ?: 0.0
            onSentTo(value?.takeIf { squares > TOLD_WALK_SQUARES }, squares)
        }

    /** True when the trip under way is a dash, after a double tap. */
    private var tripDash = false

    /** True when the end of the walk should open the place there: a walk the player sent by tapping the ground. */
    private var opensOnArrival = false

    /** True once the player has turned or zoomed the camera since the walk began; then it is left as they set it. */
    private var cameraMovedOnWalk = false

    /** When the change of day began, or null outside one. While it lasts, the town takes no input. */
    private var nightFell: Double? = null
    private var nightProgress = 0.0
    private var sitting = false

    /** How much of the day had gone when the night's sleep began: the light carries on from there. */
    private var nightFrom = 0.0

    /** How long the change of day takes. A test may shorten it, to play a month quickly. */
    internal var nightMillis = DayLight.MILLIS

    /**
     * How much of the day has gone as the town shows it, from 0.0 in the morning to 1.0 at night; and, while the time
     * an action took passes, when that began and the time it goes from and to.
     */
    private var shownShare = 0.0
    private var timePassed: Double? = null
    private var timeFrom = 0.0
    private var timeTo = 0.0

    /** How long the time an action takes passes on screen. A test may shorten it. */
    internal var passMillis = DayLight.PASS_MILLIS

    /**
     * The player's own turn, tilt and distance of the camera, kept while it looks at the campfire for a night of
     * rest, to give back in the morning. Null on any other night.
     */
    private var viewBeforeRest: Triple<Double, Double, Double>? = null

    private var frameAsked = false

    /** True when the next frame should come at full rate: the player did something, or something of theirs moves. */
    private var urgent = false
    private var gliding = false
    private var lastFrame = 0.0
    private var closed = false

    /** How many frames the town has drawn, for tests. */
    internal var framesDrawn = 0
        private set

    // The fingers (or the mouse button) on the town and where each one is now, to tell a tap from a drag or a pinch.
    private val pointers = mutableMapOf<Int, Pair<Double, Double>>()
    private var pressX = 0.0
    private var pressY = 0.0
    private var dragged = false
    private var pinched = false
    private var pinchGap = 0.0

    private val onPress: (Event) -> Unit = { press(it as PointerEvent) }
    private val onMove: (Event) -> Unit = { move(it as PointerEvent) }
    private val onRelease: (Event) -> Unit = { release(it as PointerEvent, cancelled = false) }
    private val onCancel: (Event) -> Unit = { release(it as PointerEvent, cancelled = true) }
    private val onWheel: (Event) -> Unit = { wheel(it as WheelEvent) }
    private val onLost: (Event) -> Unit = {
        contextLost = true
        // Nothing moves until WebGL comes back, so nothing rumbles or crackles either.
        quietSounds()
    }
    private val onRestored: (Event) -> Unit = {
        contextLost = false
        askForFrame()
    }

    /** A phone that stops asking for less motion gets the town's life back at once, not at the next touch. */
    private val onMotionPreference: (Event) -> Unit = { askForFrame() }

    /** A tap on the small map shows it larger or smaller; Escape brings the larger one back. */
    private val onMinimap: (Event) -> Unit = { showLargeMinimap(!minimap.isLarge) }
    private val onMinimapKey: (Event) -> Unit = { event ->
        if ((event as? KeyboardEvent)?.key == "Escape") showLargeMinimap(false)
    }

    /** A name taking the keyboard's focus brings the larger map back, so it never lies over the name in focus. */
    private val onFocusIn: (Event) -> Unit = { showLargeMinimap(false) }

    /** True while the phone has taken WebGL back from the page: the town waits, drawing nothing, until it returns. */
    private var contextLost = false

    /** True once the sound has been told all is quiet, so it is told only once while the town's life stands still. */
    private var soundsQuiet = true

    /** Back on screen, the town draws again at once; while hidden, it asks for no frames at all. */
    private val onVisibility: (Event) -> Unit = {
        if (!pageHidden()) {
            pace.reset()
            askForFrame()
        }
    }

    init {
        element.className = "walk-town"
        // Fixed to the whole screen, so the town fills it whatever else is on the page. A finger on the town moves
        // the camera or the character, never the page.
        element.style.position = "fixed"
        element.style.setProperty("inset", "0")
        element.style.overflowX = "hidden"
        element.style.overflowY = "hidden"
        element.style.setProperty("touch-action", "none")
        canvas.style.display = "block"
        canvas.style.width = "100%"
        canvas.style.height = "100%"
        canvas.setAttribute("aria-hidden", "true")
        element.appendChild(canvas)
        // Before the names, so a name that floats over the small map is drawn over it and can still be tapped.
        element.appendChild(minimap.element)
        // A phone screen may have three pixels per point or more; two look as sharp and cost far less.
        renderer.setPixelRatio(min(window.devicePixelRatio, 2.0))

        addLightAndSky()
        addTown()
        addLife()
        addWeather()
        addCharacter()
        addRides()
        addVehicles()
        addPeople()
        addMarker()
        addLabels()

        resizeObserver.observe(canvas)
        minimapObserver.observe(minimap.element)
        minimap.element.addEventListener("click", onMinimap)
        element.addEventListener("focusin", onFocusIn)
        // Each finger is captured on press, so its moves and its lift come to the canvas even off it.
        canvas.addEventListener("pointerdown", onPress)
        canvas.addEventListener("pointermove", onMove)
        canvas.addEventListener("pointerup", onRelease)
        canvas.addEventListener("pointercancel", onCancel)
        canvas.addEventListener("lostpointercapture", onCancel)
        canvas.addEventListener("wheel", onWheel, json("passive" to false))
        // A phone may take WebGL back from a page in the background; Three.js sets it up again, and the town redraws.
        canvas.addEventListener("webglcontextlost", onLost)
        canvas.addEventListener("webglcontextrestored", onRestored)
        document.addEventListener("visibilitychange", onVisibility)
        lessMotion.addEventListener("change", onMotionPreference)
    }

    /** The sky and its haze, soft light from all round, and a warm sun whose shadows fall around the character. */
    private fun addLightAndSky() {
        scene.background = sky
        scene.fog = haze
        scene.add(ambient)
        renderer.shadowMap.enabled = true
        renderer.shadowMap.type = PCFSoftShadowMap
        // The shadows are drawn again only when the frame pace asks, not on every frame.
        renderer.shadowMap.autoUpdate = false
        sun.castShadow = true
        sun.shadow.mapSize.set(SHADOW_PIXELS, SHADOW_PIXELS)
        val shadowBox = sun.shadow.camera
        shadowBox.left = -SHADOW_REACH
        shadowBox.right = SHADOW_REACH
        shadowBox.top = SHADOW_REACH
        shadowBox.bottom = -SHADOW_REACH
        shadowBox.near = 1.0
        // The sun is 30 units from the character; past the ground below the tallest tower, nothing more to shadow.
        shadowBox.far = SHADOW_DEPTH
        shadowBox.updateProjectionMatrix()
        sun.shadow.bias = -0.0005
        sun.shadow.normalBias = 0.03
        scene.add(sun)
        scene.add(sun.target)
        light(DayLight.MORNING)
    }

    /**
     * The town's boxes that stand still. The buildings, trees and the rest that cast shadows are drawn in one draw for
     * each tile of the town, so the tiles out of the camera's sight, and out of the sun's shadow box, are not drawn at
     * all. The ground, the water and the mountains take shadows but cast none, which saves the phone work; being flat
     * or few, they and the glowing windows are one draw each.
     */
    private fun addTown() {
        val (water, ground) = Neighbourhood.ground.partition { it.colour == Neighbourhood.WATER }
        sea = boxes(water, material)
        sea.castShadow = false
        scene.add(sea)
        scene.add(boxes(ground, material).also { it.castShadow = false })
        scene.add(boxes(Neighbourhood.corner, material).also { it.castShadow = false })
        for (tile in Neighbourhood.groundTiles) {
            val mesh = boxes(tile, material)
            mesh.castShadow = false
            scene.add(mesh)
            far(mesh, tile)
        }
        tiled(stillBlocks, material)
        addHouseDetail()
        // The glass of the wide map's towers and 내 방 out there in a draw of their own, left out from far off; and each
        // place out past downtown, its windows and lamps, in one of its own, so it is drawn only near it.
        val (corner, wide) = glowingBlocks.indices.partition { glowingBlocks[it].x < Neighbourhood.TOWN_COLUMNS }
        val byPlace = wide.groupBy { index -> placeLotOf(glowingBlocks[index]) }
        val ofPlaces = placeLots.indices.mapNotNull { byPlace[it] }
        val draws = listOf(corner) + listOfNotNull(byPlace[-1]) + ofPlaces
        for ((draw, members) in draws.withIndex()) {
            if (members.isEmpty()) continue
            for ((slot, index) in members.withIndex()) {
                glowDrawOf[index] = glowDraws.size
                glowSlotOf[index] = slot
            }
            val panes = members.map { glowingBlocks[it] }
            val mesh = boxes(panes, glowMaterial)
            scene.add(mesh)
            glowDraws.add(mesh)
            // A place's windows are small: they go with the low city round it, however tall the place.
            if (draw > 0) far(mesh, panes, low = draw >= draws.size - ofPlaces.size)
        }
    }

    /** Which of [placeLots] [block]'s middle stands in, or -1 for none. */
    private fun placeLotOf(block: Block): Int {
        val x = block.x + block.width / 2
        val z = block.z + block.depth / 2
        return placeLots.indexOfFirst { it.isNear(x, z, 0.0) }
    }

    /**
     * The two draws of the suburbs' houses near the camera, their lawns and yard trees: each as many boxes as it may
     * ever hold, each with a colour from the start, none drawn until the camera is placed ([placeHouseDetail]). Only
     * the one round the character casts the sun's shadows, as the shadows are drawn only there.
     */
    private fun addHouseDetail() {
        casterDetails = nearDraw(HouseDetail.CASTER_CAPACITY, castsShadows = true)
        details = nearDraw(HouseDetail.CAPACITY, castsShadows = false)
        houseDetail = HouseDetail(
            Neighbourhood.yards,
            casterDetails.instanceMatrix.array, casterDetails.instanceColor!!.array,
            details.instanceMatrix.array, details.instanceColor!!.array,
        )
    }

    private fun nearDraw(capacity: Int, castsShadows: Boolean): InstancedMesh {
        val mesh = InstancedMesh(cube, material, capacity)
        mesh.castShadow = castsShadows
        mesh.receiveShadow = true
        mesh.frustumCulled = false
        mesh.instanceMatrix.setUsage(DynamicDrawUsage)
        for (index in 0 until capacity) mesh.setColorAt(index, detailColour)
        mesh.instanceColor?.setUsage(DynamicDrawUsage)
        mesh.count = 0
        meshes.add(mesh)
        scene.add(mesh)
        return mesh
    }

    /**
     * Writes the suburbs' houses, lawns and yard trees for the camera as it is now, when it moved or a house folded
     * since they were last written: once a frame, after the camera is placed and the buildings folded.
     */
    private fun placeHouseDetail() {
        val eye = view.eye
        val focus = view.focus
        if (!detailStale && eye.x == detailEyeX && eye.y == detailEyeY && eye.z == detailEyeZ && focus.x == detailFocusX &&
            focus.y == detailFocusY && focus.z == detailFocusZ && across == detailAcross
        ) return
        detailStale = false
        houseDetailWrites += 1
        detailEyeX = eye.x
        detailEyeY = eye.y
        detailEyeZ = eye.z
        detailFocusX = focus.x
        detailFocusY = focus.y
        detailFocusZ = focus.z
        detailAcross = across
        houseDetail.fill(
            eye.x, eye.y, eye.z, focus.x, focus.y, focus.z, tan(across / 2), tan(view.fov * PI / 360), foldOf,
        )
        // The rows' low boxes of each block that came near, or went off, put away or back.
        for (at in 0 until houseDetail.changedCount) {
            val index = houseDetail.changed[at]
            val yard = Neighbourhood.yards[index]
            for (row in yard.firstRow until yard.firstRow + yard.rows) showStill(row, !houseDetail.shown[index])
        }
        sendNear(casterDetails, houseDetail.casters)
        sendNear(details, houseDetail.others)
    }

    /**
     * Draws the boxes [draw] holds with [mesh], sending only those to the graphics card again, and of their colours only
     * those written again.
     */
    private fun sendNear(mesh: InstancedMesh, draw: NearDraw) {
        val count = draw.count
        mesh.count = count
        if (count == 0) return
        mesh.instanceMatrix.addUpdateRange(0, count * 16)
        mesh.instanceMatrix.needsUpdate = true
        val from = draw.coloursFrom
        val colours = mesh.instanceColor ?: return
        if (from >= count) return
        colours.addUpdateRange(from * 3, (count - from) * 3)
        colours.needsUpdate = true
    }

    /** Draws still box [index] where it stands, turned as it is, or [shown] false, puts it away at no size at all. */
    private fun showStill(index: Int, shown: Boolean) {
        requireTiled(index)
        val block = stillBlocks[index]
        val mesh = tiles[tileOf[index]]
        val at = slotOf[index] * 16
        val numbers = mesh.instanceMatrix.array
        val size = if (shown) 1.0 else 0.0
        val cosine = cos(block.turn) * size
        val sine = sin(block.turn) * size
        numbers[at] = (cosine * block.width).toFloat()
        numbers[at + 2] = (-sine * block.width).toFloat()
        numbers[at + 5] = (block.height * size).toFloat()
        numbers[at + 8] = (sine * block.depth).toFloat()
        numbers[at + 10] = (cosine * block.depth).toFloat()
        mesh.instanceMatrix.addUpdateRange(at, 11)
        mesh.instanceMatrix.needsUpdate = true
    }

    /**
     * Notes [mesh], drawing [blocks], as one to leave out when it is far from the camera ([leaveOutFar]): as a low draw,
     * however tall its boxes, when [low].
     */
    private fun far(mesh: InstancedMesh, blocks: List<Block>, low: Boolean = false) {
        var west = Double.MAX_VALUE
        var east = -Double.MAX_VALUE
        var north = Double.MAX_VALUE
        var south = -Double.MAX_VALUE
        var tallest = 0.0
        for (block in blocks) {
            // A turned box reaches past its own sides by up to half its diagonal.
            val out = if (block.turn == 0.0) 0.0 else hypot(block.width, block.depth) / 2
            val middleX = block.x + block.width / 2
            val middleZ = block.z + block.depth / 2
            west = min(west, min(block.x, middleX - out))
            east = max(east, max(block.x + block.width, middleX + out))
            north = min(north, min(block.z, middleZ - out))
            south = max(south, max(block.z + block.depth, middleZ + out))
            tallest = max(tallest, block.y + block.height)
        }
        val index = farMeshes.size
        farMeshes.add(mesh)
        farX = farX.copyOf(index + 1)
        farZ = farZ.copyOf(index + 1)
        farRadius = farRadius.copyOf(index + 1)
        farReach = farReach.copyOf(index + 1)
        farX[index] = (west + east) / 2
        farZ[index] = (north + south) / 2
        farRadius[index] = hypot(east - west, south - north) / 2
        farReach[index] = if (tallest > TALL && !low) FollowCamera.FAR_PLANE else -1.0
    }

    /**
     * Leaves out each draw of [farMeshes] whose boxes all lie farther from the camera's eye at [eyeX], [eyeZ] than it
     * can be seen, and draws the rest. It makes nothing new, as it runs every frame.
     */
    private fun leaveOutFar(eyeX: Double, eyeZ: Double) {
        val lowReach = haze.near + LOW_FOG * (haze.far - haze.near)
        for (index in farMeshes.indices) {
            val away = hypot(farX[index] - eyeX, farZ[index] - eyeZ) - farRadius[index]
            val reach = if (farReach[index] < 0) lowReach else farReach[index]
            farMeshes[index].visible = away < reach
        }
    }

    /**
     * One draw for each tile of [blocks], [TILE] units square by where each box's middle is, noting for each box which
     * tile it is in and where.
     */
    private fun tiled(blocks: List<Block>, material: Material) {
        val groups = LinkedHashMap<Pair<Int, Int>, MutableList<Int>>()
        for ((index, block) in blocks.withIndex()) {
            // A suburban house is drawn only near the camera, with its roof and door ([details]).
            if (isHouse[index]) continue
            val column = floor((block.x + block.width / 2) / TILE).toInt()
            val key = column to floor((block.z + block.depth / 2) / TILE).toInt()
            groups.getOrPut(key) { mutableListOf() }.add(index)
        }
        for (members in groups.values) {
            for ((slot, index) in members.withIndex()) {
                tileOf[index] = tiles.size
                slotOf[index] = slot
            }
            val tile = members.map { blocks[it] }
            val mesh = boxes(tile, material).also { scene.add(it) }
            tiles.add(mesh)
            // A place out past downtown, however tall, keeps its tile in view no farther than the city round it does.
            val city = tile.filter { placeLotOf(it) < 0 }
            far(mesh, tile, low = city.isNotEmpty() && city.none { it.y + it.height > TALL })
        }
    }

    /** The player: a body that turns as one, with legs and arms that swing from the hip and the shoulder. */
    private fun addCharacter() {
        for (limb in Limb.entries) {
            val group = Group()
            group.position.set(limb.pivot.x, limb.pivot.y, limb.pivot.z)
            group.add(boxes(Figures.player.filter { it.limb == limb }.map { it.block }, material))
            character.add(group)
            limbs[limb] = group
        }
        leftLeg = limbs.getValue(Limb.LEFT_LEG)
        rightLeg = limbs.getValue(Limb.RIGHT_LEG)
        leftArm = limbs.getValue(Limb.LEFT_ARM)
        rightArm = limbs.getValue(Limb.RIGHT_ARM)
        character.scale.set(Figures.SCALE, Figures.SCALE, Figures.SCALE)
        scene.add(character)
        placeCharacter()
    }

    /**
     * The shared bikes in their docks, the e-scooters on their corners and the bike on show at the shop, in one draw;
     * and each ride, drawn under the character only while it rides it, turning with it.
     */
    private fun addRides() {
        parked = boxes(Rides.boxes, material)
        parkedMatrices = Float32Array(parked.instanceMatrix.array)
        scene.add(parked)
        for (each in Ride.entries) {
            val mesh = boxes(Rides.model(each.vehicle, each.colour), material)
            mesh.visible = false
            mount.add(mesh)
            rideMeshes.add(mesh)
        }
        mount.scale.set(Figures.SCALE, Figures.SCALE, Figures.SCALE)
        scene.add(mount)
    }

    /** The buses, water buses and the taxi in one draw, placed again as they move; the ones ridden hidden until a ride. */
    private fun addVehicles() {
        val models = listOf(
            TransitLines.bus, TransitLines.bus, TransitLines.boat, TransitLines.boat, TransitLines.taxi, TransitLines.bus,
        )
        vehicles = movingBoxes(models.flatten(), material)
        for (slot in listOf(RIDE_BUS, RIDE_BOAT, TAXI)) hideVehicle(slot)
        placeLoopVehicles(lifeNow)
    }

    /**
     * Each place's person by the door, and the passersby on their loops: all the townspeople in one draw and all the
     * passersby in another, every box placed again each frame of the town's life.
     */
    private fun addPeople() {
        townsfolk = figures(greeterParts.filterIndexed { index, _ -> folkDrawOf[index] == 0 } + listOf(Figures.keeper), material)
        folkDraws.add(townsfolk)
        for (draw in 1 until FOLK_DRAWS) {
            val members = site.venues.indices.filter { folkDrawOf[it] == draw }
            val mesh = figures(members.map { greeterParts[it] }, material)
            folkDraws.add(mesh)
            // Left out from far off by where its people stand, a little round each.
            far(mesh, members.map { site.venues[it].person }.map { Block(it.x - 1.0, 0.0, it.z - 1.0, 2.0, 2.0, 2.0, 0) })
        }
        passerbyBoxes = figures(passerbyParts, passerbyMaterial)
        wetPasserbyBoxes = figures(wetPasserbyParts, passerbyMaterial)
        wetPasserbyBoxes.visible = false
        stepLife()
    }

    /** One draw for the boxes of many figures, each box placed every frame by its [FigureBoxes]. */
    private fun figures(parts: List<List<Part>>, material: Material): InstancedMesh {
        val mesh = boxes(parts.flatten().map { it.block }, material)
        // The figures move about, so the bounds worked out from where they first stood would soon be wrong.
        mesh.frustumCulled = false
        mesh.instanceMatrix.setUsage(DynamicDrawUsage)
        scene.add(mesh)
        return mesh
    }

    /**
     * The SkyTrain, which runs along the guideway; the campfire's flames and its light; and glints on the water,
     * blossom petals and clouds, which cast no shadow.
     */
    private fun addLife() {
        val cars = Neighbourhood.CARS
        trainCars = movingBoxes(List(cars) { Neighbourhood.trainCar }.flatten(), trainMaterial)
        trainWindows = movingBoxes(List(cars) { Neighbourhood.trainCarWindows }.flatten(), trainGlowMaterial)
        val fire = site.campfire
        flames.add(boxes(Neighbourhood.flames, flameMaterial).also { it.castShadow = false })
        flames.position.set(fire.x, Neighbourhood.FLAME_BASE, fire.z)
        scene.add(flames)
        fireLight.position.set(fire.x, FIRE_LIGHT_HEIGHT, fire.z)
        scene.add(fireLight)
        drifting = boxes(shimmer.at(0.0, 0.0) + Petals.at(0.0) + Clouds.at(0.0), material)
        drifting.castShadow = false
        drifting.receiveShadow = false
        drifting.frustumCulled = false
        drifting.instanceMatrix.setUsage(DynamicDrawUsage)
        scene.add(drifting)
    }

    /** The rain, falling only on a rainy day, and the puddles it leaves; neither casts a shadow. */
    private fun addWeather() {
        rain = InstancedMesh(drop, rainMaterial, Rain.DROPS + Rain.SPLASHES)
        rain.castShadow = false
        rain.receiveShadow = false
        rain.frustumCulled = false
        rain.instanceMatrix.setUsage(DynamicDrawUsage)
        rain.visible = false
        // After the see-through passersby and train, so a streak in front of them is not covered by them.
        rain.renderOrder = 1
        meshes.add(rain)
        scene.add(rain)
        puddles = boxes(Rain.puddlesAt(site), material)
        puddles.castShadow = false
        puddles.visible = false
        scene.add(puddles)
    }

    /** A small square on the ground where the character is walking to, shown only while it walks. */
    private fun addMarker() {
        val half = 0.45
        val bar = 0.09
        val ring = listOf(
            Block(-half, 0.02, -half, 2 * half, 0.03, bar, MARKER),
            Block(-half, 0.02, half - bar, 2 * half, 0.03, bar, MARKER),
            Block(-half, 0.02, -half, bar, 0.03, 2 * half, MARKER),
            Block(half - bar, 0.02, -half, bar, 0.03, 2 * half, MARKER),
            Block(-0.08, 0.02, -0.08, 0.16, 0.05, 0.16, MARKER),
        )
        val mesh = boxes(ring, material)
        mesh.castShadow = false
        marker.add(mesh)
        marker.visible = false
        scene.add(marker)
    }

    /** Each building's name, floating over it; a tap on the name turns to the place's person, as a tap on them does. */
    private fun addLabels() {
        for (venue in site.venues) {
            val label = document.createElement("button") as HTMLButtonElement
            label.type = "button"
            label.className = "place-name"
            label.textContent = venue.place.label
            label.style.position = "absolute"
            label.style.left = "0"
            label.style.top = "0"
            label.onclick = { event ->
                touched()
                val dash = isDoubleTap(event)
                if (nightFell == null && journey == null && rideTo == null) goTalk(venue.place, dash)
            }
            element.appendChild(label)
            labels[venue.place] = label
        }
        shopLabel.type = "button"
        shopLabel.className = "place-name shop-name"
        shopLabel.textContent = Shop.NAME
        shopLabel.style.position = "absolute"
        shopLabel.style.left = "0"
        shopLabel.style.top = "0"
        shopLabel.onclick = { event ->
            touched()
            val dash = isDoubleTap(event)
            if (nightFell == null && journey == null && rideTo == null) goToShop(dash)
        }
        element.appendChild(shopLabel)
    }

    /**
     * True when the click on a name is the second tap of a double tap, as a tap on the canvas may be ([tap]). A click from
     * a key or a script (its detail is 0) has no finger behind it, so it is never one.
     */
    private fun isDoubleTap(click: MouseEvent): Boolean =
        click.detail != 0 && taps.isDouble(window.performance.now(), click.x, click.y)

    /** One draw for boxes that move, placed again on every beat of the town's life, as the train's are. */
    private fun movingBoxes(blocks: List<Block>, material: Material): InstancedMesh {
        val mesh = boxes(blocks, material)
        mesh.frustumCulled = false
        mesh.instanceMatrix.setUsage(DynamicDrawUsage)
        scene.add(mesh)
        return mesh
    }

    /** One draw for many boxes: the shared cube placed, stretched, turned and coloured once for each block. */
    private fun boxes(blocks: List<Block>, material: Material): InstancedMesh {
        val mesh = InstancedMesh(cube, material, blocks.size)
        val placer = Object3D()
        val colour = Color(0)
        for ((index, block) in blocks.withIndex()) {
            // A cube is placed, and turned, by its middle.
            placer.position.set(block.x + block.width / 2, block.y + block.height / 2, block.z + block.depth / 2)
            placer.rotation.y = block.turn
            placer.scale.set(block.width, block.height, block.depth)
            placer.updateMatrix()
            mesh.setMatrixAt(index, placer.matrix)
            mesh.setColorAt(index, colour.setHex(block.colour))
        }
        mesh.castShadow = true
        mesh.receiveShadow = true
        meshes.add(mesh)
        return mesh
    }

    override var coveredTop: () -> Double = { 0.0 }
        set(value) {
            field = value
            labelsStale = true
            askForFrame()
        }

    override var isCovered: Boolean
        get() = pace.isCovered
        set(value) {
            if (value == pace.isCovered) return
            pace.isCovered = value
            // A talk or the guide takes the player's eyes, and the larger map would lie under it.
            if (value) showLargeMinimap(false)
            touched()
        }

    override fun endChat() {
        touched()
        stopChatting()
    }

    override var weather: Weather
        get() = comingWeather ?: shownWeather
        set(value) {
            if (nightFell != null) {
                // The new day's sky comes in the dark of the night.
                comingWeather = value
                return
            }
            comingWeather = null
            if (value != shownWeather) showWeather(value)
        }

    override fun mark(place: Place?) {
        for ((labelPlace, label) in labels) label.classList.toggle("here", labelPlace == place)
        if (place != marked) {
            marked = place
            mapStale = true
            askForFrame()
        }
    }

    override fun endDay() {
        touched()
        // The ride ends with the day: a shared one goes back, and the next morning starts on foot.
        getOff()
        endJourney()
        rentAt = -1
        renting = -1
        stopAt = -1
        trip = null
        marker.visible = false
        pointers.clear()
        stopChatting()
        // The night is spent by the campfire at 내 방, where the sleep is.
        sitting = true
        // The night falls from the time it is, even halfway through the time an action took.
        timePassed = null
        nightFrom = shownShare
        // The camera comes round to the far side of the fire, near and low, to see the character's face in its light.
        // The player's own view comes back in the morning.
        if (viewBeforeRest == null) viewBeforeRest = Triple(view.yaw, view.pitch, view.distance)
        view.aim(FIRESIDE_TURN, FIRESIDE_TILT, FIRESIDE_DISTANCE)
        if (asksLessMotion()) {
            // The phone asks for less motion, so the night passes at once.
            goHome(jump = true)
            morning()
            askForFrame()
            return
        }
        nightFell = window.performance.now()
        askForFrame()
    }

    override fun passTime(share: Double) {
        if (nightFell != null) return
        if (asksLessMotion()) {
            showTime(share)
            return
        }
        timeFrom = shownShare
        timeTo = share.coerceIn(0.0, 1.0)
        timePassed = window.performance.now()
        // The player just did something: the light moves on the quicker beat, even after a long talk.
        touched()
        askForFrame()
    }

    override fun showTime(share: Double) {
        if (nightFell != null) return
        timePassed = null
        shownShare = share.coerceIn(0.0, 1.0)
        light(DayLight.during(shownShare, shownLight))
        askForFrame()
    }

    override val riding: Ride?
        get() = ride

    override fun canRide(ride: Ride): Boolean {
        if (sitting || nightFell != null || this.ride != null || journey != null) return false
        // A shared one only while the character stands by the one it reached, and of its kind.
        return !ride.shared || (renting >= 0 && Rides.shared[renting].vehicle == ride.vehicle)
    }

    override fun canRideTo(ride: Ride, to: Spot): Boolean = canRide(ride) && (!ride.shared || taxiWay(to) != null)

    override fun rideTo(ride: Ride, to: Spot) {
        if (!ride.shared || !canRideTo(ride, to)) return
        val way = taxiWay(to) ?: return
        ride(ride)
        // Along the very way the fare was worked out for, so the way paid for is the way ridden; quicker on a long one.
        rideTo = to
        tripDash = false
        trip = Trip(way, ride.vehicle.cruise(way.zipWithNext { a, b -> distanceBetween(a, b) }.sum()))
        tripStarted = window.performance.now()
        opensOnArrival = false
        cameraMovedOnWalk = false
        marker.position.set(to.x, 0.0, to.z)
        marker.visible = true
        askForFrame()
    }

    override fun ride(ride: Ride) {
        if (!canRide(ride)) return
        // A shared one the character was on its way to rent is not rented once it rides anything.
        rentAt = -1
        if (ride.shared) {
            val at = renting
            renting = -1
            rentedFrom = at
            showParked(at, false)
            heading = Rides.shared[at].turn
        }
        this.ride = ride
        for (index in rideMeshes.indices) rideMeshes[index].visible = index == ride.ordinal
        goOnFromHere()
        touched()
        askForFrame()
    }

    override fun getOff() {
        if (ride == null) return
        ride = null
        for (mesh in rideMeshes) mesh.visible = false
        if (rentedFrom >= 0) showParked(rentedFrom, true)
        rentedFrom = -1
        if (rideTo != null) {
            // Off a shared one on its way to a place: the character stands where it got off.
            rideTo = null
            trip = null
            marker.visible = false
        } else {
            goOnFromHere()
        }
        touched()
        askForFrame()
    }

    override val travelling: Transport?
        get() = journey?.transport

    /** The way along the streets from where the character stands to [to], or null when it cannot get there. */
    private fun taxiWay(to: Spot): List<Spot>? {
        if (taxiFrom != spot) {
            taxiFrom = spot
            taxiWays.clear()
            val places = taxiStops.map { it.second }
            val routes = site.grid.routes(spot, places)
            for ((index, place) in places.withIndex()) taxiWays[place] = usable(routes[index], place)
        }
        return taxiWays.getOrPut(to) { usable(site.grid.route(spot, to), to) }
    }

    /** [route] as a taxi's way to [to]: null when it does not get there, or [to] is too near to need one. */
    private fun usable(route: List<Spot>, to: Spot): List<Spot>? =
        route.takeIf { it.size >= 2 && it.last() == to && distanceBetween(spot, to) > TAXI_NOT_NEEDED }

    override fun taxiDistance(to: Spot): Double? =
        taxiWay(to)?.zipWithNext { a, b -> distanceBetween(a, b) }?.sum()

    override fun canTravel(transport: Transport, from: Int, to: Int): Boolean {
        if (sitting || nightFell != null || journey != null) return false
        if (to !in 0 until TransitLines.count(transport) || to == from) return false
        if (transport == Transport.TAXI) return from == -1 && taxiWay(taxiStops[to].second) != null
        if (from !in 0 until TransitLines.count(transport) || !TransitLines.joins(transport, from, to)) return false
        return distanceBetween(spot, TransitLines.waitAt(Stop(transport, from))) <= TransitLines.STOP_REACH
    }

    override fun travel(transport: Transport, from: Int, to: Int) {
        if (!canTravel(transport, from, to)) return
        val made = journeyFor(transport, from, to) ?: return
        // Off whatever the character rode, a shared one back to its dock, and no walk, rental or talk on the way.
        getOff()
        stopChatting()
        trip = null
        marker.visible = false
        rentAt = -1
        renting = -1
        stopAt = -1
        // A vehicle still going on from the last ride gives way to this one, rather than stand still in the street.
        if (leaving != null) {
            leaving = null
            hideRideVehicle()
        }
        val now = window.performance.now()
        journey = made
        journeyStarted = now
        cameraMovedOnWalk = false
        if (transport == Transport.SKYTRAIN) {
            val scheduled = SkyTrainRun.shiftAt(lifeNow) + runStand
            // The line's own train already standing at the station is the one boarded; elsewhere it comes in faded.
            trainFadesIn = abs(scheduled - made.railFrom) > 0.5 || !SkyTrainRun.isShownAt(lifeNow) ||
                trainBackAt + TRAIN_FADE > now
            trainRide = made
            trainRideStarted = now
        }
        touched()
        askForFrame()
    }

    /** The ride on [transport] from stop [from] to stop [to], or for a taxi from here to place [to]. */
    private fun journeyFor(transport: Transport, from: Int, to: Int): Journey? = when (transport) {
        Transport.SKYTRAIN -> Journey.train(TransitLines.stations[from], TransitLines.stations[to])
        Transport.BUS -> {
            val stops = TransitLines.busStops
            val loop = TransitLines.lineOf(from).loop
            val (way, distance) = Journey.roundLoop(loop, stops[from].along, stops[to].along, Journey.GOING_ON)
            Journey(transport, way, distance, stops[to].wait, Journey.BUS_SPEED, Journey.BUS_RAMP)
        }
        Transport.WATER_BUS -> {
            val docks = TransitLines.docks
            val (way, distance) = Journey.roundLoop(TransitLines.boatLoop, docks[from].along, docks[to].along, Journey.GOING_ON)
            Journey(
                transport, way, distance, docks[to].wait, Journey.BOAT_SPEED, Journey.BOAT_RAMP,
                from = docks[from].wait,
            )
        }
        Transport.TAXI -> taxiWay(taxiStops[to].second)?.let { route ->
            val there = route.zipWithNext { a, b -> distanceBetween(a, b) }.sum()
            // After dropping the character, the taxi turns back down the street it came along.
            val back = alongFor(route.reversed(), Journey.GOING_ON)
            // A long ride goes faster at its quickest, as the train does.
            val cruise = Journey.taxiCruise(there)
            Journey(transport, RailLine(route + back.drop(1)), there, route.last(), cruise, Journey.taxiRamp(cruise))
        }
    }

    /** The first [length] units of [points]: its points up to there, and where it reaches. */
    private fun alongFor(points: List<Spot>, length: Double): List<Spot> {
        val kept = mutableListOf(points.first())
        var left = length
        for ((a, b) in points.zipWithNext()) {
            val step = distanceBetween(a, b)
            if (step >= left) {
                kept.add(Spot(a.x + (b.x - a.x) * left / step, a.z + (b.z - a.z) * left / step))
                return kept
            }
            left -= step
            kept.add(b)
        }
        return kept
    }

    override fun standStill() {
        trip = null
        marker.visible = false
        rentAt = -1
        renting = -1
        stopAt = -1
        askForFrame()
    }

    override fun leaveTaxi() {
        val going = journey ?: return
        if (going.transport != Transport.TAXI) return
        // Out where the taxi is, on the street; it drives on to where it was going, and away.
        journey = null
        leaving = going
        leavingStarted = journeyStarted
        stepOut(going.way.spotAt(going.travelled(window.performance.now() - journeyStarted)))
    }

    /** The character stands at [at] on foot, the camera at ground level again, and the game is told. */
    private fun stepOut(at: Spot) {
        spot = at
        focusHeight = FOCUS_HEIGHT
        if (focusUnderground) {
            view.jumpTo(Point(at.x, FOCUS_HEIGHT, at.z))
            labelsStale = true
        }
        focusUnderground = false
        character.visible = true
        touched()
        askForFrame()
        onTravelled()
    }

    /** Ends any ride at once, as the day does: the character is on foot, and every vehicle of a ride is gone. */
    private fun endJourney() {
        if (journey == null && leaving == null && trainRide == null) return
        journey = null
        leaving = null
        if (trainRide != null) trainBackAt = window.performance.now()
        trainRide = null
        focusHeight = FOCUS_HEIGHT
        focusUnderground = false
        character.visible = true
        hideRideVehicle()
    }

    /**
     * Moves the ride under way on to [now]: the character with the vehicle, out at its stop at the end; and the vehicle
     * of the last ride on out of the way. True while either moves.
     */
    private fun stepJourney(now: Double): Boolean {
        val going = journey
        if (going != null) {
            val elapsed = now - journeyStarted
            if (going.isOverAfter(elapsed)) {
                journey = null
                leaving = going
                leavingStarted = journeyStarted
                heading = (going.way.turnAt(going.stopAt) + PI / 2)
                stepOut(going.out)
                // A taxi drops the character at the door it was paid to: the talk there opens, as off a shared ride.
                if (going.transport == Transport.TAXI) {
                    opensOnArrival = true
                    reach(going.out, rode = true)
                }
            } else {
                followRide(going, elapsed)
            }
        }
        val drawn = journey ?: leaving
        if (drawn != null) {
            val since = now - if (drawn === journey) journeyStarted else leavingStarted
            if (drawn === leaving && drawn.isGoneAfter(since)) {
                leaving = null
                hideRideVehicle()
            } else if (drawn.transport != Transport.SKYTRAIN) {
                placeRideVehicle(drawn, since)
            }
        }
        stepTrainRide(now)
        return journey != null || leaving != null || trainRide != null
    }

    /** The character, and the camera's subject, where the vehicle is [elapsed] into [going]. */
    private fun followRide(going: Journey, elapsed: Double) {
        val place = ridePlace
        if (going.transport == Transport.SKYTRAIN) {
            // Under downtown too, the character goes with the train, so the small map shows it under the streets.
            Neighbourhood.guideway.placeAt(going.alongAt(elapsed), place)
            spot = Spot(place[0], place[1])
            heading = place[2] + PI / 2
            val underground = UndergroundRide.isUnder(going, elapsed)
            focusHeight = if (underground) FOCUS_HEIGHT else Neighbourhood.DECK_HEIGHT + TRAIN_FOCUS
            if (underground != focusUnderground) {
                focusUnderground = underground
                labelsStale = true
                // Out of the tunnel the camera cuts to the train at its mouth, as a ride underground does.
                if (elapsed > going.boardMillis) view.jumpTo(Point(spot.x, focusHeight, spot.z))
            }
            return
        }
        going.way.placeAt(going.travelled(elapsed), place)
        spot = Spot(place[0], place[1])
        heading = place[2] + PI / 2
        focusHeight = FOCUS_HEIGHT
        if (going.transport == Transport.WATER_BUS) standAboard(going, elapsed, place[0], place[1], place[2])
    }

    /**
     * Where the character stands on the water bus at [x], [z] turned [turn]: on the open deck ahead of the cabin; while
     * getting on, along the pier from the dock to there, and while stepping out, from there to the dock it gets out at.
     */
    private fun standAboard(going: Journey, elapsed: Double, x: Double, z: Double, turn: Double) {
        val deckX = x + TransitLines.BOAT_STAND * cos(turn)
        val deckZ = z - TransitLines.BOAT_STAND * sin(turn)
        val from = going.from
        val off = elapsed - (going.millis - Journey.ALIGHT_MILLIS)
        val share: Double
        val startX: Double
        val startZ: Double
        val endX: Double
        val endZ: Double
        if (elapsed < Journey.BOARD_MILLIS && from != null) {
            share = elapsed / Journey.BOARD_MILLIS
            startX = from.x
            startZ = from.z
            endX = deckX
            endZ = deckZ
        } else if (off > 0) {
            share = off / Journey.ALIGHT_MILLIS
            startX = deckX
            startZ = deckZ
            endX = going.out.x
            endZ = going.out.z
        } else {
            aboardX = deckX
            aboardY = TransitLines.DECK
            aboardZ = deckZ
            return
        }
        val eased = share.coerceIn(0.0, 1.0)
        aboardX = startX + (endX - startX) * eased
        aboardZ = startZ + (endZ - startZ) * eased
        // Down from the pier onto the deck, and back up.
        val onDeck = if (off > 0) 1 - eased else eased
        aboardY = TransitLines.DECK * onDeck
    }

    /** Draws the vehicle of [going] where it is [elapsed] into the ride. */
    private fun placeRideVehicle(going: Journey, elapsed: Double) {
        val place = ridePlace
        going.way.placeAt(going.travelled(elapsed), place)
        val slot = when (going.transport) {
            Transport.BUS -> RIDE_BUS
            Transport.WATER_BUS -> RIDE_BOAT
            else -> TAXI
        }
        val model = when (going.transport) {
            Transport.BUS -> TransitLines.bus
            Transport.WATER_BUS -> TransitLines.boat
            else -> TransitLines.taxi
        }
        if (vehicleShown != null && vehicleShown != going.transport) hideRideVehicle()
        vehicleShown = going.transport
        VehicleBoxes.write(model, slot, place[0], 0.0, place[1], place[2], false, vehicles.instanceMatrix.array)
        vehicles.instanceMatrix.needsUpdate = true
    }

    /** Hides the vehicle of the last ride, if one is drawn. */
    private fun hideRideVehicle() {
        val shown = vehicleShown ?: return
        vehicleShown = null
        hideVehicle(
            when (shown) {
                Transport.BUS -> RIDE_BUS
                Transport.WATER_BUS -> RIDE_BOAT
                else -> TAXI
            },
        )
    }

    /** Shrinks the vehicle whose boxes start at [slot] to nothing. */
    private fun hideVehicle(slot: Int) {
        val model = when (slot) {
            RIDE_BUS, BUS_ON_LOOP, BURNABY_BUS -> TransitLines.bus
            RIDE_BOAT, BOAT_ON_LOOP -> TransitLines.boat
            else -> TransitLines.taxi
        }
        VehicleBoxes.write(model, slot, 0.0, 0.0, 0.0, 0.0, true, vehicles.instanceMatrix.array)
        vehicles.instanceMatrix.needsUpdate = true
    }

    /**
     * Moves the train of a SkyTrain ride on to [now]: in at the station it is boarded at, along the line, and on out of
     * the way after the character got out, faded out at the end; then the line's own train comes back.
     */
    private fun stepTrainRide(now: Double) {
        val ride = trainRide ?: return
        val elapsed = now - trainRideStarted
        if (ride.isGoneAfter(elapsed)) {
            trainRide = null
            trainBackAt = now
            return
        }
        // Getting on under downtown, the train is still coming in to the platform.
        trainShift = UndergroundRide.trainAlong(ride, elapsed) - runStand
        placeTrain(trainShift)
        val fadeIn = if (trainFadesIn) (elapsed / Journey.BOARD_MILLIS).coerceIn(0.0, 1.0) else 1.0
        val fadeOut = ((ride.goneMillis - elapsed) / TRAIN_FADE).coerceIn(0.0, 1.0)
        val shown = minOf(fadeIn, fadeOut)
        trainMaterial.opacity = shown
        trainGlowMaterial.opacity = shown
        trainCars.visible = shown > 0.0
        trainWindows.visible = shown > 0.0
        trainRideSpeed = UndergroundRide.trainSpeed(ride, elapsed)
    }

    /** How fast the ridden train goes now, for its rumble. */
    private var trainRideSpeed = 0.0

    /** The bus and the water bus on their loops, where the town's clock at [millis] has them. It makes nothing new. */
    private fun placeLoopVehicles(millis: Double) {
        val array = vehicles.instanceMatrix.array
        val place = loopPlace
        TransitLines.busLoop.placeAt(busRun.alongAt(millis), place)
        VehicleBoxes.write(TransitLines.bus, BUS_ON_LOOP, place[0], 0.0, place[1], place[2], false, array)
        TransitLines.burnabyLoop.placeAt(burnabyRun.alongAt(millis), place)
        VehicleBoxes.write(TransitLines.bus, BURNABY_BUS, place[0], 0.0, place[1], place[2], false, array)
        TransitLines.boatLoop.placeAt(boatRun.alongAt(millis), place)
        VehicleBoxes.write(TransitLines.boat, BOAT_ON_LOOP, place[0], 0.0, place[1], place[2], false, array)
        vehicles.instanceMatrix.needsUpdate = true
    }

    /** A trip on its way goes on from where the character is, at the pace it now goes. */
    private fun goOnFromHere() {
        val current = trip ?: return
        val route = site.grid.route(spot, current.route.last())
        if (route.size < 2) return
        trip = Trip(route, paceFor(tripDash))
        tripStarted = window.performance.now()
    }

    /** How fast the character goes now, in units a second: on foot or on what it rides, a little faster on a [dash]. */
    private fun paceFor(dash: Boolean): Double {
        val vehicle = ride?.vehicle ?: return if (dash) Trip.DASH_SPEED else Trip.WALK_SPEED
        return if (dash) vehicle.dashSpeed else vehicle.speed
    }

    /** Shows the shared one [index] where it stands, or hides it while it is ridden: only its own boxes are sent. */
    private fun showParked(index: Int, shown: Boolean) {
        val numbers = parked.instanceMatrix.array
        val first = Rides.firstBoxOf(index) * 16
        val count = Rides.boxCountOf(index) * 16
        for (at in first until first + count) numbers[at] = if (shown) parkedMatrices[at] else 0f
        parked.instanceMatrix.addUpdateRange(first, count)
        parked.instanceMatrix.needsUpdate = true
    }

    override fun close() {
        if (closed) return
        closed = true
        resizeObserver.disconnect()
        minimapObserver.disconnect()
        canvas.removeEventListener("pointerdown", onPress)
        canvas.removeEventListener("pointermove", onMove)
        canvas.removeEventListener("pointerup", onRelease)
        canvas.removeEventListener("pointercancel", onCancel)
        canvas.removeEventListener("lostpointercapture", onCancel)
        canvas.removeEventListener("wheel", onWheel)
        canvas.removeEventListener("webglcontextlost", onLost)
        canvas.removeEventListener("webglcontextrestored", onRestored)
        document.removeEventListener("visibilitychange", onVisibility)
        lessMotion.removeEventListener("change", onMotionPreference)
        minimap.element.removeEventListener("click", onMinimap)
        element.removeEventListener("focusin", onFocusIn)
        document.removeEventListener("keydown", onMinimapKey)
        for (mesh in meshes) mesh.dispose()
        renderer.dispose()
        cube.dispose()
        drop.dispose()
        material.dispose()
        glowMaterial.dispose()
        trainMaterial.dispose()
        trainGlowMaterial.dispose()
        passerbyMaterial.dispose()
        flameMaterial.dispose()
        rainMaterial.dispose()
        underground.dispose()
        shades.dispose()
        // The town's sounds go quiet with it, last, so nothing the sound does can stop the town letting go.
        tellSounds(0.0, 0.0, 0.0)
    }

    /**
     * Tells the sound how loud the train and the fire are, and how dark the night is. A sound that fails is the
     * sound's trouble: it is noted and the town goes on, rather than handing a working town over to the buttons.
     */
    private fun tellSounds(train: Double, fire: Double, night: Double) {
        soundsQuiet = train == 0.0 && fire == 0.0
        try {
            onSounds(train, fire, night)
        } catch (error: Throwable) {
            console.warn("The town's sounds failed:", error)
        }
    }

    /** Tells the sound that the train and the fire are silent, unless it already knows. */
    private fun quietSounds() {
        if (!soundsQuiet) tellSounds(0.0, 0.0, 0.0)
    }

    /** Where the character is now, for tests. */
    internal val characterSpot: Spot
        get() = spot

    /** Which way the character faces, as [Trip] counts it (0 faces south). For tests. */
    internal val characterHeading: Double
        get() = heading

    /** Where the walk is going, while the marker shows it, for tests. */
    internal val markerSpot: Spot?
        get() = trip?.route?.last()

    /** Where [point] is on the screen, from 0.0 to 1.0 across and down, for tests. */
    internal fun screenPoint(point: Point): FramePoint? = view.screenPoint(point)

    internal val isSitting: Boolean
        get() = sitting

    /** Turns the camera as a drag would, to [yaw], [pitch] and [distance], for tests that look somewhere on purpose. */
    internal fun aimCamera(yaw: Double, pitch: Double, distance: Double) {
        view.aim(yaw, pitch, distance)
        askForFrame()
    }

    /** Sends the character walking to [target], as a tap on the ground there would. For tests. */
    internal fun walkFor(target: Spot) {
        walkTo(target, dash = false, opensOnArrival = false)
    }

    /** Puts the character at [spot] at once, the camera with it, for tests that start somewhere on purpose. */
    internal fun standAt(spot: Spot) {
        trip = null
        marker.visible = false
        renting = -1
        this.spot = spot
        view.jumpTo(Point(spot.x, FOCUS_HEIGHT, spot.z))
        askForFrame()
    }

    /** The turn the camera's eye is really at round the point it looks at, as [FollowCamera.yaw] counts. For tests. */
    internal val cameraEyeTurn: Double
        get() = atan2(view.eye.x - view.focus.x, view.eye.z - view.focus.z)

    internal val isDashing: Boolean
        get() = trip != null && tripDash

    /** How long the way of the trip under way is, in units, or null standing. For tests. */
    internal val tripLength: Double?
        get() = trip?.length

    /** How long the trip under way takes from where it started, in milliseconds, or null standing. For tests. */
    internal val tripMillis: Double?
        get() = trip?.millis

    /** True when the shared one [index] stands in its dock or on its corner, drawn; false while it is ridden. */
    internal fun isParkedShown(index: Int): Boolean =
        parked.instanceMatrix.array[Rides.firstBoxOf(index) * 16 + 15] != 0f

    /** True when the ride [each] is drawn under the character. For tests. */
    internal fun isRideDrawn(each: Ride): Boolean = rideMeshes[each.ordinal].visible

    /** True while the character is drawn: not while it is in the train, a bus or a taxi. For tests. */
    internal val isCharacterShown: Boolean
        get() = character.visible

    /** Where the vehicle of the ride on [transport] is drawn, or null when none is. For tests and screenshots. */
    internal fun vehicleAt(transport: Transport): Spot? {
        if (vehicleShown != transport) return null
        val slot = when (transport) {
            Transport.BUS -> RIDE_BUS
            Transport.WATER_BUS -> RIDE_BOAT
            else -> TAXI
        }
        val numbers = vehicles.instanceMatrix.array
        return Spot(numbers[slot * 16 + 12].toDouble(), numbers[slot * 16 + 14].toDouble())
    }

    /**
     * What a frame of the town costs where the camera is now, for the budget test: [frames] frames drawn at once, each
     * with the sun's shadows, how many draws, triangles and boxes the last one took for the picture and for the shadows
     * (a box is twelve triangles), and the milliseconds each took on average with the graphics finished. When
     * [turning], the camera turns a little before each frame, so each is a moving frame: the camera placed, the
     * buildings folded and the near houses written again, all timed.
     */
    internal fun measureFrames(frames: Int, turning: Boolean = false): FrameCost {
        val subject = if (sitting) site.benchSeat else spot
        val focus = Point(subject.x, focusHeight, subject.z)
        view.follow(focus, 0.0)
        placeCamera()
        // Under downtown, the platform and the tunnel as they are drawn there, and no town to write houses for.
        val going = journey
        val drawn = if (focusUnderground && going != null) underground.scene else scene
        if (drawn !== scene && going != null) {
            underground.place(going, window.performance.now() - journeyStarted, false, drawnLight.sky, camera)
        }
        if (drawn === scene) placeHouseDetail()
        val info = renderer.asDynamic().info
        val gl = renderer.asDynamic().getContext()
        // Reading a pixel back waits for the frame to be drawn, which a finish alone does not in every browser.
        val pixel = Uint8Array(4)
        // Three.js counts afresh after drawing the shadows, so the shadows are counted here by hand: reset before each.
        info.autoReset = false
        val started = window.performance.now()
        for (frame in 0 until frames) {
            if (turning) {
                view.orbit(TURN_PIXELS, 0.0)
                view.jumpTo(focus)
                placeCamera()
                if (drawn === scene) {
                    foldBuildings(subject, FRAME_MILLIS, still = false)
                    placeHouseDetail()
                }
            }
            info.reset()
            renderer.shadowMap.needsUpdate = true
            renderer.render(drawn, camera)
            gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixel)
        }
        val millis = (window.performance.now() - started) / frames
        val allDraws = (info.render.calls as Number).toInt()
        val allTriangles = (info.render.triangles as Number).toInt()
        // The picture alone: one more frame, untimed, without the shadows.
        info.autoReset = true
        renderer.shadowMap.needsUpdate = false
        renderer.render(drawn, camera)
        val draws = (info.render.calls as Number).toInt()
        val triangles = (info.render.triangles as Number).toInt()
        return FrameCost(draws, triangles, triangles / 12, millis, allDraws - draws, (allTriangles - triangles) / 12)
    }

    /** How many of the suburbs' houses, roofs, doors, lawns and yard trees are drawn near the camera now. For tests. */
    internal val houseDetailBoxes: Int
        get() = casterDetails.count + details.count

    /** How many times the near houses have been written. For tests. */
    internal var houseDetailWrites = 0
        private set

    /** True when block [yard] of [Neighbourhood.yards] has its houses drawn one by one now. For tests. */
    internal fun isHouseDrawn(yard: Int): Boolean = houseDetail.shown[yard]

    /** True while the train is a ride's, not the line's own. For tests. */
    internal val isTrainRidden: Boolean
        get() = trainRide != null

    /** How many times the names have been laid out. For tests. */
    internal var labelLayouts = 0
        private set

    /** True once the underground's shaders and sign were readied ahead, while the town idled. For tests. */
    internal val isUndergroundReady: Boolean
        get() = undergroundReady

    /** True while a ride is shown under downtown, the platform and the tunnel drawn instead of the town. For tests. */
    internal val isUnderground: Boolean
        get() = focusUnderground && journey != null

    /** The platform and the tunnel as last drawn: the camera's eye there, the character, the doors. For tests. */
    internal val undergroundShown: UndergroundScene
        get() = underground

    /** The bike shop's name, floating over it. For tests. */
    internal val shopName: HTMLButtonElement
        get() = shopLabel

    internal val isMarkerShown: Boolean
        get() = marker.visible

    internal val isChangingDay: Boolean
        get() = nightFell != null

    /** True while the time an action took passes on screen. For tests. */
    internal val isPassingTime: Boolean
        get() = timePassed != null

    /** How much of the day has gone as the town shows it now, and the light it shows. For tests. */
    internal val timeShown: Double
        get() = shownShare
    internal val lightShown: Lighting
        get() = shownLight.toLighting()

    /**
     * True once the town has drawn and nothing of the player's moves: no walk, no change of day, the camera settled
     * and no building still folding or rising. The town's own life may still move. For tests.
     */
    internal val isStill: Boolean
        get() = framesDrawn > 0 && !urgent && !gliding && trip == null && nightFell == null && !cutaway.isMoving &&
            journey == null && timePassed == null

    /** True when the [index]th of [Neighbourhood.foldables] is folded down out of the way, or folding. For tests. */
    internal fun isFolded(index: Int): Boolean = cutaway.isFolding(index)

    /** How tall box [index] of [Neighbourhood.blocks] is drawn now, read from where the drawing keeps it. For tests. */
    internal fun drawnHeight(index: Int): Double {
        requireTiled(index)
        return tiles[tileOf[index]].instanceMatrix.array[slotOf[index] * 16 + 5].toDouble()
    }

    /** Fails loudly for a box the still tiles do not draw, a suburban house's walls, rather than read another's. */
    private fun requireTiled(index: Int) {
        require(tileOf[index] != NOT_TILED) { "Box $index is drawn near the camera, not in the still tiles" }
    }

    /** How far the camera's eye is from the point it looks at, for tests. */
    internal val cameraReach: Double
        get() {
            val eye = view.eye
            val focus = view.focus
            val x = eye.x - focus.x
            val y = eye.y - focus.y
            val z = eye.z - focus.z
            return sqrt(x * x + y * y + z * z)
        }

    /** The small map, and where it last drew the character's arrow, in CSS pixels from its top left. For tests. */
    internal val minimapElement: HTMLElement
        get() = minimap.element

    internal val minimapDot: Pair<Double, Double>
        get() = minimap.dotLeft to minimap.dotTop

    /** The places the small map last pointed to with arrows on its edge, out of its sight. For tests. */
    internal val minimapPointsTo: Set<Place>
        get() = minimap.pointedTo

    /** How far east of its stop at the station the train was last drawn, for tests. */
    internal val trainShiftDrawn: Double
        get() = trainShift

    /** Where along the guideway the line's own train stands at its station now, for tests. */
    internal val trainStand: Double
        get() = runStand

    /** Each passerby and where they are, for tests. */
    internal val passerbySpots: List<Pair<Passer, Spot>>
        get() = passersby.map { it.passer to it.spot }

    /** True while the passersby are out, by day; false at night. For tests. */
    internal val arePassersbyOut: Boolean
        get() = crowd.visible

    /** The weather drawn now, and whether rain is falling, for tests. */
    internal val weatherShown: Weather
        get() = shownWeather

    internal val isRaining: Boolean
        get() = rain.visible

    /** True when the rain is drawn in one pass, after the other see-through things, for tests. */
    internal val isRainOnePassLast: Boolean
        get() = rainMaterial.forceSinglePass && rain.renderOrder > 0

    /**
     * The first streak of rain drawn (one right by the camera is left out) and how high it was last drawn, for tests:
     * it falls as the town's time goes on.
     */
    internal val drawnStreak: Pair<Int, Double>?
        get() {
            val numbers = rain.instanceMatrix.array
            for (index in 0 until Rain.DROPS) {
                if (numbers[index * 16 + 5] != 0f) return index to numbers[index * 16 + 13].toDouble()
            }
            return null
        }

    /** The passersby as drawn today: in their rain clothes on a rainy day. */
    private val crowd: InstancedMesh
        get() = if (shownWeather == Weather.RAINY) wetPasserbyBoxes else passerbyBoxes

    /** The camera's turn, tilt and distance as asked, for tests. */
    internal val cameraTurn: Double
        get() = view.yaw

    internal val cameraTilt: Double
        get() = view.pitch

    internal val cameraDistance: Double
        get() = view.distance

    private fun press(event: PointerEvent) {
        touched()
        if (event.button.toInt() != 0 || nightFell != null) return
        // The first finger of a new touch: any finger still on record lost its lift somewhere, so it is forgotten.
        if (event.isPrimary) pointers.clear()
        try {
            canvas.setPointerCapture(event.pointerId)
        } catch (error: Throwable) {
            // A pointer that is no longer down cannot be captured; its moves still come while it is over the canvas.
        }
        pointers[event.pointerId] = event.x to event.y
        if (pointers.size == 1) {
            pressX = event.x
            pressY = event.y
            dragged = false
            pinched = false
        } else {
            pinched = true
            pinchGap = gap()
        }
    }

    private fun move(event: PointerEvent) {
        val last = pointers[event.pointerId] ?: return
        pointers[event.pointerId] = event.x to event.y
        if (nightFell != null) return
        if (pointers.size >= 2) {
            // Fingers spreading apart bring the camera nearer; pinching them together takes it farther.
            val gap = gap()
            if (pinchGap > 0 && gap > 0) view.zoom(pinchGap / gap)
            pinchGap = gap
            cameraMovedOnWalk = true
            askForFrame()
            return
        }
        if (!dragged && hypot(event.x - pressX, event.y - pressY) > TAP_SLOP) dragged = true
        if (dragged && !pinched) {
            view.orbit(event.x - last.first, event.y - last.second)
            cameraMovedOnWalk = true
            askForFrame()
        }
    }

    private fun release(event: PointerEvent, cancelled: Boolean) {
        if (pointers.remove(event.pointerId) == null) return
        pinchGap = 0.0
        if (!cancelled && pointers.isEmpty() && !dragged && !pinched) tap(event.x, event.y)
    }

    private fun wheel(event: WheelEvent) {
        event.preventDefault()
        touched()
        if (nightFell != null) return
        view.zoom(exp(event.deltaY * WHEEL_ZOOM))
        cameraMovedOnWalk = true
        askForFrame()
    }

    /** How far apart the first two fingers are, in pixels. */
    private fun gap(): Double {
        val (a, b) = pointers.values.take(2)
        return hypot(a.first - b.first, a.second - b.second)
    }

    /**
     * A tap at [x], [y] on the page: turn to a place or a passerby, or walk to a spot on the ground. The second tap of
     * a double tap dashes; on a place or a passerby it only dashes there, as the first tap saw to the talk.
     */
    private fun tap(x: Double, y: Double) {
        val frame = canvas.getBoundingClientRect()
        if (frame.width == 0.0 || frame.height == 0.0 || nightFell != null || journey != null || rideTo != null) return
        val dash = taps.isDouble(window.performance.now(), x, y)
        // The passersby can be tapped while they are out.
        val boxes = if (crowd.visible) passersby.map { Passerby.boxAround(it.spot) } else emptyList()
        // The shared bikes and e-scooters can be tapped to rent one, on foot.
        val vehicles = if (ride == null && !sitting) Rides.tapBoxes else emptyList()
        val left = (x - frame.left) / frame.width
        val stops = if (sitting) emptyList() else TransitLines.tapBoxes
        val picked = view.pick(left, (y - frame.top) / frame.height, boxes, cutaway, vehicles, stops)
        // A tap that lands on something else turns the player away from the passerby; a tap on the sky does not.
        if (picked != null && picked !is Picked.AtPasserby) stopChatting()
        when (picked) {
            is Picked.AtPasserby -> stopFor(passersby[picked.index], dash)
            is Picked.AtVehicle -> goRent(picked.index, dash)
            is Picked.AtStop -> goToStop(picked.index, dash)
            Picked.AtShop -> goToShop(dash)
            is Picked.AtPlace -> goTalk(picked.place, dash)
            is Picked.OnGround -> {
                onPlace(null)
                walkTo(site.landing(spot, picked.spot), dash, opensOnArrival = true)
            }
            null -> {}
        }
    }

    /**
     * Stops [passerby] for a word: they stand and face the player, the character walks up to them unless already
     * near, and the talk opens at once. The second tap of a double tap only hurries the character there, unless the
     * first tap missed them: then it opens the talk too.
     */
    private fun stopFor(passerby: Passerby, dash: Boolean) {
        if (sitting) return
        val already = chatting === passerby
        chatting?.takeIf { it !== passerby }?.release()
        chatting = passerby
        passerby.hold()
        val there = passerby.spot
        val away = distanceBetween(spot, there)
        if (away > CHAT_DISTANCE) {
            // Up to them, a step short, on the player's side.
            val share = (away - 1.0) / away
            val near = Spot(spot.x + (there.x - spot.x) * share, spot.z + (there.z - spot.z) * share)
            walkTo(near, dash, opensOnArrival = false)
        } else {
            trip = null
            marker.visible = false
            heading = atan2(there.x - spot.x, there.z - spot.z)
            askForFrame()
        }
        if (!dash || !already) onPasserby(passerby.passer)
    }

    /** Lets the passerby the player stopped walk on, as when the player turns to something else. */
    private fun stopChatting() {
        chatting?.release()
        chatting = null
    }

    /** Turns to [place]'s person, as [goUpTo] does. */
    private fun goTalk(place: Place, dash: Boolean) =
        goUpTo(place.label, site.venue(place).talkSpot, isAt(place), dash) { onPlace(place) }

    /** Turns to the bike shop's keeper, as [goUpTo] does. */
    private fun goToShop(dash: Boolean) =
        goUpTo(Shop.NAME, Neighbourhood.shop.talkSpot, Neighbourhood.isAtShop(spot), dash) { onShop() }

    /**
     * Sends the character to [talkSpot], where the person of [name] is talked to. Already [there], it steps up to them
     * and [opens] the talk at once, unless this is a double tap's second, which only hurries it. From afar it sets off,
     * away from any talk, and the talk opens on arrival: nobody is talked to, and nothing done, from afar.
     */
    private fun goUpTo(name: String, talkSpot: Spot, there: Boolean, dash: Boolean, opens: () -> Unit) {
        // A walk already sent here keeps its talk for its end, even once within reach of the door.
        if (there && sentTo != name) {
            walkTo(talkSpot, dash, opensOnArrival = false)
            if (!dash) opens()
        } else {
            onPlace(null)
            // Another tap on where it is going keeps a dash already under way.
            walkTo(talkSpot, dash || (sentTo == name && tripDash), opensOnArrival = true, sentTo = name)
        }
    }

    /**
     * Sends the character to the shared bike or e-scooter [index], to rent it on arrival, or rents it at once when the
     * character stands by it. Like a tap on the ground, it turns the player away from a talk.
     */
    private fun goRent(index: Int, dash: Boolean) {
        onPlace(null)
        val there = Rides.shared[index].spot
        if (distanceBetween(spot, there) <= Rides.RENT_REACH || !walkTo(there, dash, opensOnArrival = false)) {
            trip = null
            marker.visible = false
            askToRent(index)
            return
        }
        rentAt = index
    }

    /**
     * Sends the character to the station, stop or dock [index] of [TransitLines.tapStops], where the game offers where
     * to go, or offers it at once when the character stands there. Like a tap on the ground, it turns the player away
     * from a talk.
     */
    private fun goToStop(index: Int, dash: Boolean) {
        onPlace(null)
        val stop = TransitLines.tapStops[index]
        val there = TransitLines.waitAt(stop)
        if (distanceBetween(spot, there) <= TransitLines.STOP_REACH || !walkTo(there, dash, opensOnArrival = false)) {
            trip = null
            marker.visible = false
            stopAt = -1
            onStop(stop)
            return
        }
        stopAt = index
    }

    /**
     * At the shared one [index]: the character stands by it, and the game is asked where to ride it, which gets the
     * character on it.
     */
    private fun askToRent(index: Int) {
        rentAt = -1
        renting = index
        onRent(Ride.shared(Rides.shared[index].vehicle))
    }

    /**
     * Starts a trip to [target] at the pace the character goes now, unless it is there, sent there from afar by a tap on
     * [sentTo] if any. True when it set off.
     */
    private fun walkTo(target: Spot, dash: Boolean, opensOnArrival: Boolean, sentTo: String? = null): Boolean {
        if (sitting) return false
        rentAt = -1
        renting = -1
        stopAt = -1
        val route = site.grid.route(spot, target)
        if (route.size < 2) return false
        tripDash = dash
        trip = Trip(route, paceFor(dash))
        tripStarted = window.performance.now()
        this.opensOnArrival = opensOnArrival
        cameraMovedOnWalk = false
        val end = route.last()
        marker.position.set(end.x, 0.0, end.z)
        marker.visible = true
        this.sentTo = sentTo
        askForFrame()
        return true
    }

    private fun fit() {
        val width = canvas.clientWidth
        val height = canvas.clientHeight
        // Off the page the town has no size. It is fitted again when it comes back.
        if (width == 0 || height == 0) return
        renderer.setSize(width, height, false)
        labelsStale = true
        view.aspect = width.toDouble() / height
        camera.aspect = view.aspect
        camera.fov = view.fov
        camera.updateProjectionMatrix()
        across = 2 * atan(tan(view.fov * PI / 360) * view.aspect)
        // The camera's wedge on the map is as wide as it sees, which a turned phone changes.
        mapStale = true
        askForFrame()
    }

    /** Shows the small map larger, or back as it was. Not while a card covers the town, as it would lie under it. */
    private fun showLargeMinimap(large: Boolean) {
        if (large == minimap.isLarge || (large && pace.isCovered)) return
        minimap.isLarge = large
        if (large) {
            document.addEventListener("keydown", onMinimapKey)
        } else {
            document.removeEventListener("keydown", onMinimapKey)
        }
        mapResized = true
        touched()
        askForFrame()
    }

    /** Notes that the player touched the town, or the game turned to it: the town's life goes back to its quicker beat. */
    private fun touched() {
        pace.noteInput(window.performance.now())
    }

    /** Draws the next frame at the screen's full rate: the player did something, or the picture must change. */
    private fun askForFrame() {
        urgent = true
        nextFrame()
    }

    /** Asks the screen for its next frame, unless one is already on its way. */
    private fun nextFrame() {
        if (frameAsked || closed) return
        frameAsked = true
        window.requestAnimationFrame { now ->
            frameAsked = false
            drawFrame(now)
        }
    }

    private fun drawFrame(now: Double) {
        if (closed) return
        val hidden = pageHidden() || contextLost
        // The time an action took passes on the beat of the town's own life, not on every frame the screen offers: only
        // the light changes, so the sun's shadows need not be drawn again for it.
        val moving = urgent || trip != null || nightFell != null || gliding || cutaway.isMoving || journey != null ||
            leaving != null || trainRide != null
        if (!pace.shouldDraw(now, moving, hidden)) {
            // Hidden or without WebGL, nothing more is asked for until that ends; otherwise the life waits its turn.
            if (!hidden) nextFrame()
            return
        }
        urgent = false
        // After a rest with no frames, the first one counts as a short step, not the whole pause.
        val millis = (now - lastFrame).coerceIn(0.0, MAX_STEP_MILLIS)
        lastFrame = now
        // A phone that asks for less motion keeps the town's own life still, and draws only for the player. The life
        // moves on the beat only, even on frames drawn at the screen's full rate for the player.
        val still = asksLessMotion()
        var walking = false
        var travelled = false
        var night = false
        var passing = false
        var folding = false
        try {
            // Inside the try, so that if the walk, the change of day (which brings the new day's weather) or the life
            // ever failed, the town would hand over to the buttons, not freeze.
            walking = stepWalk(now)
            travelled = stepJourney(now)
            night = stepNight(now)
            passing = stepTime(now)
            placeCharacter()
            val subject = if (sitting) site.benchSeat else spot
            gliding = view.follow(Point(subject.x, focusHeight, subject.z), millis)
            placeCamera()
            // Under the ground the town is not drawn, so no building folds for it, and no near house is written.
            folding = !focusUnderground && foldBuildings(subject, millis, still)
            // Once a frame, after the folds, so a house folding sinks with its roof in the same frame.
            if (!focusUnderground) placeHouseDetail()
            if (!still && pace.stepsLife) stepLife()
            // A town whose life stands still has no train passing and no fire to hear.
            if (still) quietSounds()
            // Under the ground no shadow is drawn; the town's are drawn afresh on its first frame back.
            renderer.shadowMap.needsUpdate = pace.drawsShadows || focusUnderground
            val going = journey
            if (focusUnderground && going != null) {
                underground.place(going, now - journeyStarted, still, drawnLight.sky, camera)
                renderer.render(underground.scene, camera)
            } else {
                renderer.render(scene, camera)
            }
        } catch (error: Throwable) {
            fail(error)
            return
        }
        // The names move only with the camera, so they are laid out again only when it moved or the town was resized.
        val eye = view.eye
        val focus = view.focus
        // Under the ground they are hidden once, as it is entered, and left so.
        if (labelsStale || (!focusUnderground && (eye != labelsEye || focus != labelsFocus))) {
            placeLabels()
            labelsEye = eye
            labelsFocus = focus
            labelsStale = false
        }
        // On its own: a fault in the map hides the map, and never stops the town.
        if (!mapBroken) {
            try {
                drawMinimap()
            } catch (error: Throwable) {
                console.warn("The small map stopped:", error)
                mapBroken = true
                minimap.element.hidden = true
            }
        }
        framesDrawn += 1
        if (framesDrawn == 1) afterFirstFrame()
        if (walking || travelled || night || passing || gliding || folding || !still) nextFrame()
    }

    /**
     * Folds down the city's buildings that stand between the camera and the character at [subject], or fill the view
     * just in front of the camera, and raises those that no longer do, moving each on by [millis]; a phone that asks
     * for less motion sees them fold at once. They are worked out again only when the camera or the character moved.
     * True while any is still folding or rising.
     */
    private fun foldBuildings(subject: Spot, millis: Double, still: Boolean): Boolean {
        val eye = view.eye
        if (eye.x != aimedEyeX || eye.y != aimedEyeY || eye.z != aimedEyeZ || subject.x != aimedHeadX ||
            subject.z != aimedHeadZ
        ) {
            aimedEyeX = eye.x
            aimedEyeY = eye.y
            aimedEyeZ = eye.z
            aimedHeadX = subject.x
            aimedHeadZ = subject.z
            cutaway.aim(eye.x, eye.y, eye.z, subject.x, focusHeight + HEAD_HEIGHT - FOCUS_HEIGHT, subject.z)
        }
        val moving = cutaway.step(if (still) Cutaway.FOLD_MILLIS else millis)
        if (cutaway.changedCount > 0) placeFolds()
        return moving
    }

    /**
     * Draws each building whose fold changed as tall as it now stands: its walls down toward their plinth, the plant
     * room on its roof sinking with them, and its glass shrinking to fit, each box's footprint and colour kept. Only
     * those boxes are put again, in the draws that already hold them.
     */
    private fun placeFolds() {
        val blocks = stillBlocks
        for (at in 0 until cutaway.changedCount) {
            val index = cutaway.changed[at]
            val building = Neighbourhood.foldables[index]
            // A suburban house folds where it is drawn, near the camera: its walls, roof and door are written again.
            if (isHouse[building.body]) {
                detailStale = true
                continue
            }
            val fold = cutaway.foldOf(index)
            val body = blocks[building.body]
            val full = body.height
            val height = if (full > Cutaway.PLINTH) full - (full - Cutaway.PLINTH) * fold else full
            standBox(building.body, body.y, height)
            if (building.roof >= 0) standBox(building.roof, body.y + height, blocks[building.roof].height * (1 - fold))
            val shrink = height / full
            if (building.glassNorth >= 0) shrinkPane(building.glassNorth, shrink)
            if (building.glassSouth >= 0) shrinkPane(building.glassSouth, shrink)
        }
    }

    /** Draws pane [index] of the glowing glass [shrink] times as tall and as high up, to fit its folding building. */
    private fun shrinkPane(index: Int, shrink: Double) {
        val box = glowingBlocks[index]
        val mesh = glowDraws[glowDrawOf[index]]
        val at = glowSlotOf[index] * 16
        val numbers = mesh.instanceMatrix.array
        numbers[at + 5] = (box.height * shrink).toFloat()
        numbers[at + 13] = ((box.y + box.height / 2) * shrink).toFloat()
        // Only this box's numbers go to the graphics card again, not all the glass.
        mesh.instanceMatrix.addUpdateRange(at, 16)
        mesh.instanceMatrix.needsUpdate = true
    }

    /** Puts box [index] of the still boxes [height] tall from [bottom] up, its footprint as it was. */
    private fun standBox(index: Int, bottom: Double, height: Double) {
        requireTiled(index)
        val mesh = tiles[tileOf[index]]
        val at = slotOf[index] * 16
        val numbers = mesh.instanceMatrix.array
        numbers[at + 5] = height.toFloat()
        numbers[at + 13] = (bottom + height / 2).toFloat()
        mesh.instanceMatrix.addUpdateRange(at, 16)
        mesh.instanceMatrix.needsUpdate = true
    }

    /**
     * Draws the small map again when the character or the camera moved since it was last drawn, or it changed size or
     * what it marks.
     */
    private fun drawMinimap() {
        val subject = if (sitting) site.benchSeat else spot
        val facing = if (sitting) Neighbourhood.BENCH_FACING else heading
        val eye = view.eye
        val moved = subject.x != mapX || subject.z != mapZ || facing != mapHeading || eye.x != mapEyeX ||
            eye.z != mapEyeZ
        if (!moved && !mapStale && !mapResized) return
        if (mapResized) {
            mapResized = false
            minimap.measure()
        }
        mapStale = false
        mapX = subject.x
        mapZ = subject.z
        mapHeading = facing
        mapEyeX = eye.x
        mapEyeZ = eye.z
        val focus = view.focus
        minimap.draw(subject.x, subject.z, facing, eye.x, eye.z, focus.x, focus.z, across, marked)
    }

    /**
     * Moves the town's own life on to the time [lifeClock] tells: the townspeople and the passersby (gone home in the
     * night), the train, the fire, and the glints, petals and clouds.
     */
    private fun stepLife() {
        val now = lifeClock()
        val millis = (now - lifeNow).coerceIn(0.0, MAX_LIFE_STEP_MILLIS)
        lifeNow = now
        val player = if (sitting) site.benchSeat else spot
        folkFirst.fill(0)
        for (index in greeters.indices) {
            val pose = greeters[index].step(now, millis, player)
            val draw = folkDrawOf[index]
            // A draw left out far off is not written: its people stand where they were until it is near again.
            val mesh = folkDraws[draw]
            if (mesh.visible) greeterFigures[index].write(pose, mesh.instanceMatrix.array, folkFirst[draw])
            folkFirst[draw] += greeterFigures[index].count
        }
        keeperFigure.write(keeper.step(now, millis, player), townsfolk.instanceMatrix.array, folkFirst[0])
        for (draw in folkDraws) if (draw.visible) draw.instanceMatrix.needsUpdate = true
        val out = if (nightFell == null) {
            DayLight.outdoorsAt(shownShare)
        } else {
            DayLight.outdoors(nightProgress, nightFrom)
        }
        passerbyMaterial.opacity = out
        crowd.visible = out > 0.0
        // A fading figure would leave a solid shadow, so the passersby cast none until they are all there.
        crowd.castShadow = out >= 1.0
        placePassersby(millis, player)
        // The train of a ride is placed with the ride, every frame; otherwise the line's own runs, faded back in after
        // a ride.
        if (trainRide == null) {
            // Away down the line, the train comes round next at the station nearest where the player looks.
            if (!SkyTrainRun.isShownAt(now)) runStand = SkyTrainRun.nearestStand(view.focus.x, view.focus.z)
            trainShift = SkyTrainRun.shiftAt(now)
            placeTrain(trainShift)
            val back = ((window.performance.now() - trainBackAt) / TRAIN_FADE).coerceIn(0.0, 1.0)
            val trainShows = SkyTrainRun.opacityAt(now) * back
            trainMaterial.opacity = trainShows
            trainGlowMaterial.opacity = trainShows
            trainCars.visible = trainShows > 0.0
            trainWindows.visible = trainShows > 0.0
        }
        placeLoopVehicles(now)
        val flicker = Campfire.flicker(now)
        val spread = 1 + (flicker - 1) * 0.4
        flames.scale.set(spread, flicker, spread)
        flameMaterial.emissiveIntensity = glowLevel * flicker
        fireLight.intensity = FIRE_LIGHT * glowLevel * flicker
        // Grey skies dull the glints on the water, as the night does.
        val dull = max(glowLevel, DayLight.dullness(shownWeather))
        placeBlocks(drifting, shimmer.at(now, dull) + Petals.at(now) + Clouds.at(now))
        if (rain.visible) placeRain(now)
        // How loud the train and the fire are where the player is: the train at the camera, the fire at the character.
        val night = shownLight.glow
        val fire = Campfire.nearness(distanceBetween(player, site.campfire))
        val rumble = if (trainRide == null) {
            SkyTrainRun.rumbleAround(runStand + trainShift, SkyTrainRun.speedAt(now), SkyTrainRun.opacityAt(now), view.eye)
        } else {
            // Under the ground, heard where the camera rides with the train.
            val ear = if (focusUnderground) underground.eye else view.eye
            SkyTrainRun.rumbleAround(runStand + trainShift, trainRideSpeed, trainMaterial.opacity, ear)
        }
        tellSounds(rumble, fire, night)
    }

    /**
     * Moves the passersby on by [millis] with the player at [player], and draws them as they are dressed today: the
     * hand holding an umbrella raised a little to its shaft.
     */
    private fun placePassersby(millis: Double, player: Spot) {
        val wet = shownWeather == Weather.RAINY
        val mesh = crowd
        val dressed = if (wet) wetPasserbyFigures else passerbyFigures
        var first = 0
        for (index in passersby.indices) {
            val passerby = passersby[index]
            val pose = passerby.step(millis, player)
            val umbrella = Figures.holdsUmbrella(passerby.passer, shownWeather)
            val held = if (umbrella) pose.copy(wave = Figures.UMBRELLA_ARM) else pose
            dressed[index].write(held, mesh.instanceMatrix.array, first)
            first += dressed[index].count
        }
        mesh.instanceMatrix.needsUpdate = true
    }

    /**
     * Puts each of the train's cars along the guideway, [shift] from its stop at the station, turned along the line
     * where it bends. A car all the way into the tunnel is left out.
     */
    private fun placeTrain(shift: Double) {
        for (car in 0 until Neighbourhood.CARS) {
            val west = runStand - Neighbourhood.TRAIN_LENGTH / 2 + shift + car * Neighbourhood.CAR_SPACING
            val middle = west + Neighbourhood.CAR_LENGTH / 2
            val inTunnel = middle + Neighbourhood.CAR_LENGTH / 2 < 0
            val place = trainPlace
            Neighbourhood.guideway.placeAt(middle, place)
            val x = place[0]
            val z = place[1]
            val turn = place[2]
            TrainCars.write(Neighbourhood.trainCar, car, x, z, turn, inTunnel, trainCars.instanceMatrix.array)
            TrainCars.write(Neighbourhood.trainCarWindows, car, x, z, turn, inTunnel, trainWindows.instanceMatrix.array)
        }
        trainCars.instanceMatrix.needsUpdate = true
        trainWindows.instanceMatrix.needsUpdate = true
    }

    /** The rain at [millis], falling in a box of sky between where the camera looks and the camera itself. */
    private fun placeRain(millis: Double) {
        val eye = view.eye
        val focus = view.focus
        val x = focus.x + (eye.x - focus.x) * RAIN_TOWARD_EYE
        val z = focus.z + (eye.z - focus.z) * RAIN_TOWARD_EYE
        Rain.write(millis, x, z, rain.instanceMatrix.array, eye, view.yaw)
        rain.instanceMatrix.needsUpdate = true
    }

    /**
     * Dresses the town for [weather]: rain and puddles or none, the sky, the light and the haze, the sea and the
     * clouds, and the passersby, who change clothes where they stand.
     */
    private fun showWeather(weather: Weather) {
        val out = crowd.visible
        crowd.visible = false
        shownWeather = weather
        val rainy = weather == Weather.RAINY
        rain.visible = rainy
        puddles.visible = rainy
        val (near, far) = DayLight.haze(weather)
        haze.near = near
        haze.far = far
        val colour = Color(0)
        sea.setColorAt(0, colour.setHex(DayLight.sea(weather)))
        sea.instanceColor?.needsUpdate = true
        val clouds = shimmer.count + Petals.count
        for (index in clouds until clouds + Clouds.count) {
            drifting.setColorAt(index, colour.setHex(Clouds.colour(weather)))
        }
        drifting.instanceColor?.needsUpdate = true
        crowd.visible = out
        placePassersby(0.0, if (sitting) site.benchSeat else spot)
        if (rainy) placeRain(lifeNow)
        light(shownLight)
        touched()
        askForFrame()
    }

    /** Puts each box of a mesh that moves where [blocks] are now: stretched to size, with no turn. */
    private fun placeBlocks(mesh: InstancedMesh, blocks: List<Block>) {
        val numbers = mesh.instanceMatrix.array
        for ((index, block) in blocks.withIndex()) {
            val at = index * 16
            for (offset in 0 until 16) numbers[at + offset] = 0f
            numbers[at] = block.width.toFloat()
            numbers[at + 5] = block.height.toFloat()
            numbers[at + 10] = block.depth.toFloat()
            numbers[at + 12] = (block.x + block.width / 2).toFloat()
            numbers[at + 13] = (block.y + block.height / 2).toFloat()
            numbers[at + 14] = (block.z + block.depth / 2).toFloat()
            numbers[at + 15] = 1f
        }
        mesh.instanceMatrix.needsUpdate = true
    }

    /** Moves the character along its walk. True while it is still walking. */
    private fun stepWalk(now: Double): Boolean {
        val current = trip ?: return false
        val elapsed = now - tripStarted
        spot = current.spotAfter(elapsed)
        heading = current.headingAfter(elapsed)
        if (!current.isOverAfter(elapsed)) {
            marker.rotation.y = now / 700
            return true
        }
        trip = null
        marker.visible = false
        arrive(current.route.last())
        return false
    }

    /**
     * The end of a walk or a shared ride at [end]: a rental or a stop the player was on the way to is offered; otherwise
     * the character has reached whatever is there ([reach]).
     */
    private fun arrive(end: Spot) {
        val rode = rideTo != null
        if (rode) {
            // At the place the shared ride was paid for: off it, and the game is told. At one of the places or the
            // shop the character then turns to its person and the talk opens, as at the end of a walk there.
            getOff()
            onTravelled()
            opensOnArrival = true
        } else if (rentAt >= 0 && ride == null) {
            askToRent(rentAt)
            return
        } else if (stopAt >= 0) {
            val stop = TransitLines.tapStops[stopAt]
            stopAt = -1
            onStop(stop)
            return
        }
        reach(end, rode)
    }

    /**
     * At a place's person the character turns to them, and so it does at the door of a place it [rode] to; at the end of
     * a walk the player sent, or a ride, the place opens. Unless the player moved the camera on the way, it comes round
     * to the street side of the place's door, looking at its front along the street rather than down between the towers.
     * Nothing happens where there is no place: at a station, or on an open street.
     */
    private fun reach(end: Spot, rode: Boolean) {
        if (Neighbourhood.isAtShop(end)) {
            val shop = Neighbourhood.shop
            if (rode || distanceBetween(end, shop.talkSpot) <= Neighbourhood.REACH) {
                heading = atan2(shop.keeper.x - end.x, shop.keeper.z - end.z)
            }
            // Its door faces south: the camera comes round to the south, looking at its front.
            if (!cameraMovedOnWalk) view.aim(0.0, FollowCamera.ARRIVAL_TILT, view.distance)
            if (opensOnArrival) onShop()
            return
        }
        val place = site.placeAt(end) ?: return
        val venue = site.venue(place)
        if (rode || distanceBetween(end, venue.talkSpot) <= Neighbourhood.REACH) {
            heading = atan2(venue.person.x - end.x, venue.person.z - end.z)
        }
        val streetSide = if (venue.doorFacesSouth) 0.0 else PI
        if (!cameraMovedOnWalk) view.aim(streetSide, FollowCamera.ARRIVAL_TILT, view.distance)
        if (opensOnArrival) onPlace(place) else onArrived(place)
    }

    /**
     * Once the town has drawn its first frame, readies the underground when the browser is idle, or within
     * [READY_LATEST] milliseconds at the latest: a page that keeps drawing may never be idle.
     */
    private fun afterFirstFrame() {
        if (window.asDynamic().requestIdleCallback == undefined) {
            window.setTimeout({ readyUnderground() }, READY_LATEST)
        } else {
            window.asDynamic().requestIdleCallback({ readyUnderground() }, json("timeout" to READY_LATEST))
        }
    }

    /**
     * Makes the underground's shaders and sends its sign's picture to the graphics card while the town idles, so the
     * graphics driver has linked them by the time the platform's first frame uses them. Not three's compileAsync: it
     * goes on checking the shaders on a timer, which throws in the page once the town has let go of them. If anything
     * fails here, they are made on that frame instead, and the town goes on either way.
     */
    private fun readyUnderground() {
        if (closed || undergroundReady) return
        try {
            underground.signPicture?.let { renderer.initTexture(it) }
            renderer.compile(underground.scene, camera)
            undergroundReady = true
        } catch (error: Throwable) {
            console.warn("The underground's shaders were not made ahead:", error)
        }
    }

    /** Moves the light through evening, night and morning. True while the change of day goes on. */
    private fun stepNight(now: Double): Boolean {
        val start = nightFell ?: return false
        val progress = ((now - start) / nightMillis).coerceIn(0.0, 1.0)
        nightProgress = progress
        light(DayLight.at(progress, nightFrom, shownLight))
        // In the dark the next day's weather comes.
        val coming = comingWeather
        if (progress >= DayLight.DARK_AT && coming != null) {
            comingWeather = null
            // The same sky as today needs nothing redone.
            if (coming != shownWeather) showWeather(coming)
        }
        if (progress < 1.0) return true
        nightFell = null
        morning()
        return false
    }

    /**
     * Moves the light on through the time an action took, eased in and out, from the time it was to the time it is.
     * True while it goes on.
     */
    private fun stepTime(now: Double): Boolean {
        val start = timePassed ?: return false
        val progress = ((now - start) / passMillis).coerceIn(0.0, 1.0)
        val eased = progress * progress * (3 - 2 * progress)
        shownShare = timeFrom + (timeTo - timeFrom) * eased
        light(DayLight.during(shownShare, shownLight))
        if (progress < 1.0) return true
        timePassed = null
        return false
    }

    /** The character stands at 내 방's door, facing the street; [jump] moves the camera there at once. */
    private fun goHome(jump: Boolean) {
        sitting = false
        trip = null
        marker.visible = false
        spot = home.door
        heading = home.facing
        if (jump) view.jumpTo(Point(spot.x, FOCUS_HEIGHT, spot.z))
    }

    /**
     * The morning: the character is up at 내 방's door with no walk and no marker, the light is the morning's, the
     * camera is the player's own again after a night by the fire, and the town takes input again.
     */
    private fun morning() {
        if (sitting) goHome(jump = false)
        marker.visible = false
        val coming = comingWeather
        comingWeather = null
        if (coming != null && coming != shownWeather) showWeather(coming)
        shownShare = 0.0
        light(DayLight.MORNING)
        viewBeforeRest?.let { (turn, tilt, distance) -> view.aim(turn, tilt, distance) }
        viewBeforeRest = null
        onMorning()
    }

    /** Lights the town as [lighting] says for the time of day, greyed as today's weather asks. */
    private fun light(lighting: Lighting) = light(shownLight.set(lighting))

    /**
     * Lights the town as [light] says for the time of day, greyed as today's weather asks; [light] becomes the light
     * shown. It makes nothing new, as it runs on every frame of a change of light.
     */
    private fun light(light: TownLight) {
        if (light !== shownLight) shownLight.set(light)
        val lit = DayLight.underSky(drawnLight.set(shownLight), shownWeather)
        sky.setHex(lit.sky)
        haze.color.setHex(lit.sky)
        ambient.intensity = lit.ambient
        ambient.color.setHex(lit.ambientColour)
        sun.intensity = lit.sun
        sun.color.setHex(lit.sunColour)
        glowMaterial.emissiveIntensity = lit.glow
        trainGlowMaterial.emissiveIntensity = lit.glow
        glowLevel = lit.glow
        // The rain is a paler shade of the sky, so it shows against the town by day and does not glare at night.
        rainMaterial.color.setHex(DayLight.mix(lit.sky, 0xFFFFFF, RAIN_PALE))
    }

    /**
     * Puts the character where it is: walking with its legs and arms swinging, riding with its hands on the handlebar,
     * or sitting on the bench. It makes nothing new, as it runs every frame.
     */
    private fun placeCharacter() {
        val going = journey
        if (going != null) {
            // In the train, the bus or the taxi, out of sight; on the water bus, standing on its deck.
            val aboard = going.transport == Transport.WATER_BUS
            character.visible = aboard
            mount.visible = false
            if (aboard) {
                character.position.set(aboardX, aboardY, aboardZ)
                character.rotation.y = heading
                leftLeg.rotation.x = 0.0
                rightLeg.rotation.x = 0.0
                leftArm.rotation.x = 0.0
                rightArm.rotation.x = 0.0
            }
            return
        }
        character.visible = true
        mount.visible = true
        if (sitting) {
            val seat = site.benchSeat
            // A little forward on the seat, toward the way the bench faces.
            character.position.set(seat.x, SITTING_LIFT, seat.z + 0.1 * cos(Neighbourhood.BENCH_FACING))
            character.rotation.y = Neighbourhood.BENCH_FACING
            // Legs straight out in front, hands in the lap.
            leftLeg.rotation.x = -PI / 2
            rightLeg.rotation.x = -PI / 2
            leftArm.rotation.x = -0.5
            rightArm.rotation.x = -0.5
            return
        }
        val current = trip
        val elapsed = window.performance.now() - tripStarted
        val riding = ride
        if (riding != null) {
            val vehicle = riding.vehicle
            // Up on the saddle or the deck, the ride under the character and turned with it.
            character.position.set(spot.x, vehicle.lift * Figures.SCALE, spot.z)
            character.rotation.y = heading
            mount.position.set(spot.x, 0.0, spot.z)
            mount.rotation.y = heading
            // On a bike the feet go round with the pedals as it moves; on an e-scooter one foot stands ahead.
            val step = if (vehicle == Vehicle.BIKE) {
                if (current == null) 0.0 else sin(current.travelled(elapsed) / PEDAL_LENGTH * 2 * PI) * PEDAL_SWING
            } else {
                SCOOTER_STANCE
            }
            leftLeg.rotation.x = vehicle.legs - step
            rightLeg.rotation.x = vehicle.legs + step
            leftArm.rotation.x = vehicle.arms
            rightArm.rotation.x = vehicle.arms
            return
        }
        val swing = current?.swingAfter(elapsed) ?: 0.0
        character.position.set(spot.x, current?.liftAfter(elapsed) ?: 0.0, spot.z)
        character.rotation.y = heading
        leftLeg.rotation.x = swing
        rightLeg.rotation.x = -swing
        leftArm.rotation.x = -swing * 0.8
        rightArm.rotation.x = swing * 0.8
    }

    private fun placeCamera() {
        val eye = view.eye
        val focus = view.focus
        leaveOutFar(eye.x, eye.z)
        camera.position.set(eye.x, eye.y, eye.z)
        camera.lookAt(focus.x, focus.y, focus.z)
        // The sun's shadows are worked out around the character, where they matter, so they stay sharp.
        sun.position.set(focus.x + SUN.x, SUN.y, focus.z + SUN.z)
        sun.target.position.set(focus.x, 0.0, focus.z)
    }

    /**
     * Lays each building's name over the picture, just above its roof. A name behind the camera or off the screen
     * hides, and cannot be reached with the Tab key or by a screen reader until it shows again.
     */
    private fun placeLabels() {
        labelLayouts += 1
        val width = canvas.clientWidth.toDouble()
        val height = canvas.clientHeight.toDouble()
        val covered = coveredTop()
        // The small map sits in the top right corner, just under the top card and the day's log.
        val top = covered + MAP_GAP
        if (top != mapTop) {
            mapTop = top
            minimap.element.style.top = "${top}px"
        }
        // The larger map is never taller than the room under it, as on a phone turned on its side.
        minimap.roomBelow = height - top - MAP_GAP
        // The names are all one line, so the first one tells how tall a name is.
        val tall = labels.values.first().offsetHeight.toDouble()
        for ((place, label) in labels) {
            placeLabel(label, site.venue(place).labelAnchor, width, height, tall, covered)
        }
        placeLabel(shopLabel, Neighbourhood.shop.labelAnchor, width, height, tall, covered)
    }

    /**
     * Lays [label] over [anchor] on a town [width] by [height] pixels, a name [tall] pixels tall, with cards over the
     * town's top [covered] pixels.
     */
    private fun placeLabel(
        label: HTMLButtonElement,
        anchor: Point,
        width: Double,
        height: Double,
        tall: Double,
        covered: Double,
    ) {
        val point = view.screenPoint(anchor)
        // A place far off, as downtown's are from Burnaby, is lost in the haze, and so is its name.
        val eye = view.eye
        val near = hypot(anchor.x - eye.x, anchor.z - eye.z) < NAMES_REACH
        // Under the ground no name floats over the platform.
        val shown = !focusUnderground && near && point != null && point.left in 0.0..1.0 && point.top in 0.0..1.0
        label.classList.toggle("behind", !shown)
        // A name that would reach up under the top card or the day's log is hidden there, and out of the finger's
        // way; it stays for the keyboard and a screen reader, as its person is still there to talk to.
        val under = covered > 0.0 && shown && point != null && point.top * height - tall < covered
        label.classList.toggle("under", under)
        label.tabIndex = if (shown) 0 else -1
        if (shown) label.removeAttribute("aria-hidden") else label.setAttribute("aria-hidden", "true")
        // A hidden name is not moved: it is placed again when it shows.
        if (shown && point != null) {
            val left = point.left * width
            val top = point.top * height
            label.style.transform = "translate(${left}px, ${top}px) translate(-50%, -100%)"
        }
    }

    /** If Three.js fails, the town goes, and the game goes on with buttons. */
    private fun fail(error: Throwable) {
        console.warn("The town stopped drawing:", error)
        close()
        onFail()
    }

    /** The three shades of the cartoon shading, darkest first: one row of three grey pixels. */
    private fun toonShades(): DataTexture {
        val levels = Uint8Array(arrayOf<Byte>(115.toByte(), 190.toByte(), 255.toByte()))
        val texture = DataTexture(levels, 3, 1, RedFormat)
        texture.minFilter = NearestFilter
        texture.magFilter = NearestFilter
        texture.needsUpdate = true
        return texture
    }

    private val PointerEvent.x: Double
        get() = clientX.toDouble()

    private val PointerEvent.y: Double
        get() = clientY.toDouble()

    private companion object {
        const val FOCUS_HEIGHT = 1.0

        /** How high the character's head is: the end of the way the city's buildings fold out of. */
        const val HEAD_HEIGHT = 1.6

        /** How far under the top card and the day's log the small map sits, in CSS pixels. */
        const val MAP_GAP = 8.0

        /** How far a finger may move and still count as a tap, in pixels. */
        const val TAP_SLOP = 10.0
        const val WHEEL_ZOOM = 0.0015
        const val MAX_STEP_MILLIS = 50.0

        const val GROUND_LIGHT = 0x8FAF6A
        const val GLOW = 0xFFC46B
        const val MARKER = 0xFFE08A

        /** Where the sun is from the character: high in the north-east, so shadows fall short to the south-west. */
        val SUN = Point(12.0, 26.0, -8.0)
        const val SHADOW_PIXELS = 1024.0
        const val SHADOW_REACH = 15.0
        const val SHADOW_DEPTH = 55.0

        /** How wide a tile of the town's still boxes is: each tile is one draw, left out when out of sight. */
        const val TILE = 40.0

        /** The draws of the places' people: the town's own, East Vancouver's and Burnaby's. */
        private const val FOLK_DRAWS = 3

        /** About where Burnaby begins on the wide map, at the city line: the people east of it share a draw. */
        private const val CITY_LINE = 490.0

        /** The tile of a still box no tile draws: a suburban house's walls, drawn near the camera instead. */
        const val NOT_TILED = -1

        /** The camera for a night by the campfire: north of it, near and low, looking south at the bench. */
        const val FIRESIDE_TURN = PI + 0.15
        const val FIRESIDE_TILT = 0.5
        const val FIRESIDE_DISTANCE = 9.0

        /** How high the body sits so the thighs rest on the bench seat. */
        val SITTING_LIFT = Neighbourhood.SEAT_HEIGHT - (Figures.HIP - 0.09) * Figures.SCALE

        /** The longest step the town's own life takes in one frame, so after a pause it goes on rather than jumps. */
        const val MAX_LIFE_STEP_MILLIS = 100.0

        /** Within this distance of a passerby the character turns to them where it stands, instead of walking up. */
        const val CHAT_DISTANCE = 1.6

        /** How long the bus and the water bus wait at each stop on their own loops, in milliseconds. */
        const val BUS_DWELL = 2_500.0
        const val BOAT_DWELL = 3_000.0

        /** How long the train of a ride fades out at the end, and the line's own train back in after it. */
        const val TRAIN_FADE = 700.0

        /** How far above the guideway's deck the camera looks while it follows the train. */
        const val TRAIN_FOCUS = 3.2

        /** A place this near needs no taxi. */
        const val TAXI_NOT_NEEDED = 2.0

        /** How long after the town's first frame, at the latest, the underground is readied, idle or not. */
        const val READY_LATEST = 2_000

        /** Where each vehicle's boxes start in the one draw of them all. */
        val BUS_ON_LOOP = 0
        val RIDE_BUS = TransitLines.bus.size
        val BOAT_ON_LOOP = 2 * TransitLines.bus.size
        val RIDE_BOAT = BOAT_ON_LOOP + TransitLines.boat.size
        val TAXI = RIDE_BOAT + TransitLines.boat.size
        val BURNABY_BUS = TAXI + TransitLines.taxi.size

        /** How far the budget test turns the camera before each moving frame it measures, and how long a frame lasts. */
        const val TURN_PIXELS = 6.0
        const val FRAME_MILLIS = 1000.0 / 60

        /** A walk sent by a tap from afar is told to the game over this many squares, about a second on foot. */
        const val TOLD_WALK_SQUARES = 4.0

        /** A place's name farther than this from the camera's eye is hidden, its place deep in the haze. */
        const val NAMES_REACH = 170.0

        /** A draw with nothing taller than this is low: it is left out [LOW_FOG] of the way into the haze. */
        const val TALL = 6.5
        const val LOW_FOG = 0.9

        /** How far a bike goes for one turn of its pedals, and how far the legs swing with them, in radians. */
        const val PEDAL_LENGTH = 1.8
        const val PEDAL_SWING = 0.35

        /** How far ahead the front foot stands on an e-scooter, and the back foot behind, in radians. */
        const val SCOOTER_STANCE = 0.12

        /** The campfire's warm light at night, at full flicker, how far it reaches, and how high it hangs. */
        const val FIRE_COLOUR = 0xFFA040
        const val FIRE_LIGHT = 4.0
        const val FIRE_REACH = 7.0
        const val FIRE_LIGHT_HEIGHT = 0.8

        /**
         * The rain: how solid a streak is, how much paler than the sky, and how far toward the camera its box of sky
         * sits from where the camera looks.
         */
        const val RAIN_OPACITY = 0.55
        const val RAIN_PALE = 0.45
        const val RAIN_TOWARD_EYE = 0.4
    }
}
