package vancouvermonth.web

import kotlin.js.json
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.RenderingContext
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent
import vancouvermonth.text.Passer
import vancouvermonth.text.Weather

/** What the phone screen needs from the town the player walks in. */
interface WalkableTown {
    /** The whole town, to fill the screen. */
    val element: HTMLElement

    /**
     * Called with the place the player turns to: they tapped its building, its name or its person, or walked up to
     * its door or its person. Called with null when they walk off somewhere else.
     */
    var onPlace: (Place?) -> Unit

    /**
     * Called with the passerby the player stops for a word: they tapped them. No day is spent on it. The passerby
     * stands and faces the player until [endChat].
     */
    var onPasserby: (Passer) -> Unit

    /** Called once if the town stops drawing, so the game can go on with buttons. */
    var onFail: () -> Unit

    /**
     * Called when the change of day is over and the character stands at 내 방's door. From [endDay] until then, the
     * town takes no taps, drags, pinches or names.
     */
    var onMorning: () -> Unit

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
     * Ends a day spent at [place]: a golden evening, a night with the lights on, then morning, with the character
     * back at 내 방's door and [onMorning] called. A day of rest is spent on the bench by the campfire.
     */
    fun endDay(place: Place)

    /** Lets go of the drawing for good: the renderer, the shapes and the colours. */
    fun close()
}

/**
 * The town to walk in, or null when this browser should not draw it: the phone asks for less motion, there is no
 * WebGL 2 (which Three.js needs), or anything fails while setting it up. Then the month is played with buttons.
 * A test can hand in a [canvas] without WebGL.
 */
fun openWalkableTown(
    canvas: HTMLCanvasElement = document.createElement("canvas") as HTMLCanvasElement,
    lessMotion: Boolean = window.matchMedia("(prefers-reduced-motion: reduce)").matches,
): WalkableTown? {
    if (lessMotion) return null
    return try {
        // Asking for WebGL before Three.js does lets a browser without it fail quietly.
        val context = canvas.getContext("webgl2", json("antialias" to true, "alpha" to false))
        if (context == null) null else NeighbourhoodScene(canvas, context)
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
internal class NeighbourhoodScene(private val canvas: HTMLCanvasElement, context: RenderingContext) : WalkableTown {
    override val element = document.createElement("div") as HTMLElement
    override var onPlace: (Place?) -> Unit = {}
    override var onPasserby: (Passer) -> Unit = {}
    override var onFail: () -> Unit = {}
    override var onMorning: () -> Unit = {}
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

    /**
     * The still boxes in their tiles, and for each of [Neighbourhood.blocks] which tile it is drawn in and where in it;
     * the glowing windows and glass in one draw. A building that folds is put again where it is drawn, and nowhere
     * else.
     */
    private val tiles = mutableListOf<InstancedMesh>()
    private val tileOf = IntArray(Neighbourhood.blocks.size)
    private val slotOf = IntArray(Neighbourhood.blocks.size)
    private lateinit var glowing: InstancedMesh

    /** The city's buildings that fold down out of the camera's way, and where the camera and character last were. */
    private val cutaway = Cutaway(Neighbourhood.foldables.map { it.box })
    private var aimedEyeX = Double.NaN
    private var aimedEyeY = Double.NaN
    private var aimedEyeZ = Double.NaN
    private var aimedHeadX = Double.NaN
    private var aimedHeadZ = Double.NaN

    /**
     * The small map in the corner, and what it last showed: the character, the way it faced and the camera. It is drawn
     * again only when one of them moved, it was resized, or the day's place to go changed.
     */
    private val minimap = Minimap()
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
    private val greeters = Neighbourhood.venues.mapIndexed { index, venue ->
        Greeter(venue.person, venue.facing, phase = index * 1.3)
    }
    private val greeterParts = Neighbourhood.venues.map { Figures.townsperson(personAt(it.place)) }
    private val greeterFigures = greeterParts.map { FigureBoxes(it) }
    private val passersby = Strolls.all()
    private val passerbyParts = passersby.map { Figures.passer(it.passer) }
    private val passerbyFigures = passerbyParts.map { FigureBoxes(it) }

    /** The passersby dressed for the rain, two with umbrellas open, drawn in their place on a rainy day. */
    private val wetPasserbyParts = passersby.map { Figures.passer(it.passer, Weather.RAINY) }
    private val wetPasserbyFigures = wetPasserbyParts.map { FigureBoxes(it) }

    /** The passersby fade out together in the evening and back in at dawn, so they share a material that can fade. */
    private val passerbyMaterial = MeshToonMaterial(json("gradientMap" to shades, "transparent" to true))
    private lateinit var townsfolk: InstancedMesh
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

    /** The weather drawn now, and the one to come in the dark when it was set while the day changed. */
    private var shownWeather = Weather.SUNNY
    private var comingWeather: Weather? = null

    /** The light as the time of day has it, before the weather greys it. */
    private var lighting = DayLight.MORNING

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
    private val pace = FramePace(openedAt = window.performance.now())

    private val character = Group()
    private val limbs = mutableMapOf<Limb, Group>()
    private val marker = Group()
    private val labels = mutableMapOf<Place, HTMLButtonElement>()

    /** Where the camera was when the names were last laid out, and whether the town was resized since. */
    private var labelsEye: Point? = null
    private var labelsFocus: Point? = null
    private var labelsStale = true

    private val home = Neighbourhood.venue(Place.HOME)
    private val view = FollowCamera(Point(home.door.x, FOCUS_HEIGHT, home.door.z))
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
    private var tripStarted = 0.0

    /** True when the end of the walk should open the place there: a walk the player sent by tapping the ground. */
    private var opensOnArrival = false

    /** True once the player has turned or zoomed the camera since the walk began; then it is left as they set it. */
    private var cameraMovedOnWalk = false

    /** When the change of day began, or null outside one. While it lasts, the town takes no input. */
    private var nightFell: Double? = null
    private var nightProgress = 0.0
    private var sitting = false
    private var wentHome = false

    /** How long the change of day takes. A test may shorten it, to play a month quickly. */
    internal var nightMillis = DayLight.MILLIS

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
        addPeople()
        addMarker()
        addLabels()

        resizeObserver.observe(canvas)
        minimapObserver.observe(minimap.element)
        minimap.element.addEventListener("click", onMinimap)
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
        tiled(Neighbourhood.blocks, material)
        glowing = boxes(Neighbourhood.glowing, glowMaterial)
        scene.add(glowing)
    }

    /**
     * One draw for each tile of [blocks], [TILE] units square by where each box's middle is, noting for each box which
     * tile it is in and where.
     */
    private fun tiled(blocks: List<Block>, material: Material) {
        val groups = LinkedHashMap<Pair<Int, Int>, MutableList<Int>>()
        for ((index, block) in blocks.withIndex()) {
            val column = floor((block.x + block.width / 2) / TILE).toInt()
            val key = column to floor((block.z + block.depth / 2) / TILE).toInt()
            groups.getOrPut(key) { mutableListOf() }.add(index)
        }
        for (members in groups.values) {
            for ((slot, index) in members.withIndex()) {
                tileOf[index] = tiles.size
                slotOf[index] = slot
            }
            tiles.add(boxes(members.map { blocks[it] }, material).also { scene.add(it) })
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
        character.scale.set(Figures.SCALE, Figures.SCALE, Figures.SCALE)
        scene.add(character)
        placeCharacter()
    }

    /**
     * Each place's person by the door, and the passersby on their loops: all the townspeople in one draw and all the
     * passersby in another, every box placed again each frame of the town's life.
     */
    private fun addPeople() {
        townsfolk = figures(greeterParts, material)
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
        val fire = Neighbourhood.campfire
        flames.add(boxes(Neighbourhood.flames, flameMaterial).also { it.castShadow = false })
        flames.position.set(fire.x, Neighbourhood.FLAME_BASE, fire.z)
        scene.add(flames)
        fireLight.position.set(fire.x, FIRE_LIGHT_HEIGHT, fire.z)
        scene.add(fireLight)
        drifting = boxes(Shimmer.at(0.0, 0.0) + Petals.at(0.0) + Clouds.at(0.0), material)
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
        puddles = boxes(Rain.puddles, material)
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

    /** Each building's name, floating over it; a tap on the name talks to the place's person, as a tap on them does. */
    private fun addLabels() {
        for (venue in Neighbourhood.venues) {
            val label = document.createElement("button") as HTMLButtonElement
            label.type = "button"
            label.className = "place-name"
            label.textContent = venue.place.label
            label.style.position = "absolute"
            label.style.left = "0"
            label.style.top = "0"
            label.onclick = {
                touched()
                if (nightFell == null) goTalk(venue.place, dash = false)
            }
            element.appendChild(label)
            labels[venue.place] = label
        }
    }

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

    override fun endDay(place: Place) {
        touched()
        trip = null
        marker.visible = false
        pointers.clear()
        stopChatting()
        sitting = place == Place.HOME
        if (sitting) {
            // The camera comes round to the far side of the fire, near and low, to see the character's face in its
            // light. The player's own view comes back in the morning.
            if (viewBeforeRest == null) viewBeforeRest = Triple(view.yaw, view.pitch, view.distance)
            view.aim(FIRESIDE_TURN, FIRESIDE_TILT, FIRESIDE_DISTANCE)
        }
        wentHome = false
        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            // The phone asks for less motion, so the night passes at once.
            goHome(jump = true)
            morning()
            askForFrame()
            return
        }
        nightFell = window.performance.now()
        askForFrame()
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

    /** Puts the character at [spot] at once, the camera with it, for tests that start somewhere on purpose. */
    internal fun standAt(spot: Spot) {
        trip = null
        marker.visible = false
        this.spot = spot
        view.jumpTo(Point(spot.x, FOCUS_HEIGHT, spot.z))
        askForFrame()
    }

    /** The turn the camera's eye is really at round the point it looks at, as [FollowCamera.yaw] counts. For tests. */
    internal val cameraEyeTurn: Double
        get() = atan2(view.eye.x - view.focus.x, view.eye.z - view.focus.z)

    internal val isDashing: Boolean
        get() = trip?.speed == Trip.DASH_SPEED

    internal val isMarkerShown: Boolean
        get() = marker.visible

    internal val isChangingDay: Boolean
        get() = nightFell != null

    /**
     * True once the town has drawn and nothing of the player's moves: no walk, no change of day, the camera settled
     * and no building still folding or rising. The town's own life may still move. For tests.
     */
    internal val isStill: Boolean
        get() = framesDrawn > 0 && !urgent && !gliding && trip == null && nightFell == null && !cutaway.isMoving

    /** True when the [index]th of [Neighbourhood.foldables] is folded down out of the way, or folding. For tests. */
    internal fun isFolded(index: Int): Boolean = cutaway.isFolding(index)

    /** How tall box [index] of [Neighbourhood.blocks] is drawn now, read from where the drawing keeps it. For tests. */
    internal fun drawnHeight(index: Int): Double =
        tiles[tileOf[index]].instanceMatrix.array[slotOf[index] * 16 + 5].toDouble()

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

    /** How far east of its stop at the station the train was last drawn, for tests. */
    internal val trainShiftDrawn: Double
        get() = trainShift

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
     * A tap at [x], [y] on the page: talk to a place or a passerby, or walk to a spot on the ground. The second tap of
     * a double tap dashes; on a place or a passerby it only dashes there, since the first tap already opened the talk.
     */
    private fun tap(x: Double, y: Double) {
        val frame = canvas.getBoundingClientRect()
        if (frame.width == 0.0 || frame.height == 0.0 || nightFell != null) return
        val dash = taps.isDouble(window.performance.now(), x, y)
        // The passersby can be tapped while they are out.
        val boxes = if (crowd.visible) passersby.map { Passerby.boxAround(it.spot) } else emptyList()
        val picked = view.pick((x - frame.left) / frame.width, (y - frame.top) / frame.height, boxes, cutaway)
        // A tap that lands on something else turns the player away from the passerby; a tap on the sky does not.
        if (picked != null && picked !is Picked.AtPasserby) stopChatting()
        when (picked) {
            is Picked.AtPasserby -> stopFor(passersby[picked.index], dash)
            is Picked.AtPlace -> {
                if (dash) {
                    walkTo(Neighbourhood.venue(picked.place).talkSpot, dash = true, opensOnArrival = false)
                } else {
                    goTalk(picked.place, dash = false)
                }
            }
            is Picked.OnGround -> {
                onPlace(null)
                walkTo(Neighbourhood.landing(spot, picked.spot), dash, opensOnArrival = true)
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

    /** Sends the character to [place]'s person, and opens the place at once. */
    private fun goTalk(place: Place, dash: Boolean) {
        walkTo(Neighbourhood.venue(place).talkSpot, dash, opensOnArrival = false)
        onPlace(place)
    }

    private fun walkTo(target: Spot, dash: Boolean, opensOnArrival: Boolean) {
        if (sitting) return
        val route = Neighbourhood.grid.route(spot, target)
        if (route.size < 2) return
        trip = Trip(route, if (dash) Trip.DASH_SPEED else Trip.WALK_SPEED)
        tripStarted = window.performance.now()
        this.opensOnArrival = opensOnArrival
        cameraMovedOnWalk = false
        val end = route.last()
        marker.position.set(end.x, 0.0, end.z)
        marker.visible = true
        askForFrame()
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
        val moving = urgent || trip != null || nightFell != null || gliding || cutaway.isMoving
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
        val still = lessMotion.matches
        var walking = false
        var night = false
        var folding = false
        try {
            // Inside the try, so that if the walk, the change of day (which brings the new day's weather) or the life
            // ever failed, the town would hand over to the buttons, not freeze.
            walking = stepWalk(now)
            night = stepNight(now)
            placeCharacter()
            val subject = if (sitting) Neighbourhood.benchSeat else spot
            gliding = view.follow(Point(subject.x, FOCUS_HEIGHT, subject.z), millis)
            placeCamera()
            folding = foldBuildings(subject, millis, still)
            if (!still && pace.stepsLife) stepLife()
            // A town whose life stands still has no train passing and no fire to hear.
            if (still) quietSounds()
            renderer.shadowMap.needsUpdate = pace.drawsShadows
            renderer.render(scene, camera)
        } catch (error: Throwable) {
            fail(error)
            return
        }
        // The names move only with the camera, so they are laid out again only when it moved or the town was resized.
        val eye = view.eye
        val focus = view.focus
        if (labelsStale || eye != labelsEye || focus != labelsFocus) {
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
        if (walking || night || gliding || folding || !still) nextFrame()
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
            cutaway.aim(eye.x, eye.y, eye.z, subject.x, HEAD_HEIGHT, subject.z)
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
        val blocks = Neighbourhood.blocks
        for (at in 0 until cutaway.changedCount) {
            val index = cutaway.changed[at]
            val building = Neighbourhood.foldables[index]
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
        val box = Neighbourhood.glowing[index]
        val numbers = glowing.instanceMatrix.array
        numbers[index * 16 + 5] = (box.height * shrink).toFloat()
        numbers[index * 16 + 13] = ((box.y + box.height / 2) * shrink).toFloat()
        glowing.instanceMatrix.needsUpdate = true
    }

    /** Puts box [index] of the still boxes [height] tall from [bottom] up, its footprint as it was. */
    private fun standBox(index: Int, bottom: Double, height: Double) {
        val mesh = tiles[tileOf[index]]
        val at = slotOf[index] * 16
        val numbers = mesh.instanceMatrix.array
        numbers[at + 5] = height.toFloat()
        numbers[at + 13] = (bottom + height / 2).toFloat()
        mesh.instanceMatrix.needsUpdate = true
    }

    /**
     * Draws the small map again when the character or the camera moved since it was last drawn, or it changed size or
     * what it marks.
     */
    private fun drawMinimap() {
        val subject = if (sitting) Neighbourhood.benchSeat else spot
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
        val player = if (sitting) Neighbourhood.benchSeat else spot
        var first = 0
        for (index in greeters.indices) {
            val pose = greeters[index].step(now, millis, player)
            greeterFigures[index].write(pose, townsfolk.instanceMatrix.array, first)
            first += greeterFigures[index].count
        }
        townsfolk.instanceMatrix.needsUpdate = true
        val out = if (nightFell == null) 1.0 else DayLight.outdoors(nightProgress)
        passerbyMaterial.opacity = out
        crowd.visible = out > 0.0
        // A fading figure would leave a solid shadow, so the passersby cast none until they are all there.
        crowd.castShadow = out >= 1.0
        placePassersby(millis, player)
        trainShift = SkyTrainRun.shiftAt(now)
        placeTrain(trainShift)
        val trainShows = SkyTrainRun.opacityAt(now)
        trainMaterial.opacity = trainShows
        trainGlowMaterial.opacity = trainShows
        trainCars.visible = trainShows > 0.0
        trainWindows.visible = trainShows > 0.0
        val flicker = Campfire.flicker(now)
        val spread = 1 + (flicker - 1) * 0.4
        flames.scale.set(spread, flicker, spread)
        flameMaterial.emissiveIntensity = glowLevel * flicker
        fireLight.intensity = FIRE_LIGHT * glowLevel * flicker
        // Grey skies dull the glints on the water, as the night does.
        val dull = max(glowLevel, DayLight.dullness(shownWeather))
        placeBlocks(drifting, Shimmer.at(now, dull) + Petals.at(now) + Clouds.at(now))
        if (rain.visible) placeRain(now)
        // How loud the train and the fire are where the player is: the train at the camera, the fire at the character.
        val night = if (nightFell == null) 0.0 else DayLight.at(nightProgress).glow
        val fire = Campfire.nearness(distanceBetween(player, Neighbourhood.campfire))
        tellSounds(SkyTrainRun.rumbleAt(now, view.eye), fire, night)
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
            val west = Neighbourhood.TRAIN_STOP + shift + car * Neighbourhood.CAR_SPACING
            val middle = west + Neighbourhood.CAR_LENGTH / 2
            val inTunnel = middle + Neighbourhood.CAR_LENGTH / 2 < 0
            val spot = Neighbourhood.guideway.spotAt(middle)
            val turn = Neighbourhood.guideway.turnAt(middle)
            TrainCars.write(Neighbourhood.trainCar, car, spot, turn, inTunnel, trainCars.instanceMatrix.array)
            TrainCars.write(Neighbourhood.trainCarWindows, car, spot, turn, inTunnel, trainWindows.instanceMatrix.array)
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
        val clouds = Shimmer.count + Petals.count
        for (index in clouds until clouds + Clouds.count) {
            drifting.setColorAt(index, colour.setHex(Clouds.colour(weather)))
        }
        drifting.instanceColor?.needsUpdate = true
        crowd.visible = out
        placePassersby(0.0, if (sitting) Neighbourhood.benchSeat else spot)
        if (rainy) placeRain(lifeNow)
        light(lighting)
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
     * At a place's person the character turns to them; at the end of a walk the player sent, the place opens. Unless
     * the player moved the camera on the way, it comes round to the street side of the place's door, looking at its
     * front along the street rather than down between the towers.
     */
    private fun arrive(end: Spot) {
        val place = Neighbourhood.placeAt(end) ?: return
        val venue = Neighbourhood.venue(place)
        if (distanceBetween(end, venue.talkSpot) <= Neighbourhood.REACH) {
            heading = atan2(venue.person.x - end.x, venue.person.z - end.z)
        }
        val streetSide = if (venue.doorFacesSouth) 0.0 else PI
        if (!cameraMovedOnWalk) view.aim(streetSide, FollowCamera.ARRIVAL_TILT, view.distance)
        if (opensOnArrival) onPlace(place)
    }

    /** Moves the light through evening, night and morning. True while the change of day goes on. */
    private fun stepNight(now: Double): Boolean {
        val start = nightFell ?: return false
        val progress = ((now - start) / nightMillis).coerceIn(0.0, 1.0)
        nightProgress = progress
        light(DayLight.at(progress))
        // In the dark the next day's weather comes, and the character goes home, unless it spends the night by the
        // fire.
        val coming = comingWeather
        if (progress >= DayLight.HOME_AT && coming != null) {
            comingWeather = null
            // The same sky as today needs nothing redone.
            if (coming != shownWeather) showWeather(coming)
        }
        if (progress >= DayLight.HOME_AT && !wentHome && !sitting) {
            wentHome = true
            goHome(jump = true)
        }
        if (progress < 1.0) return true
        nightFell = null
        morning()
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
        light(DayLight.MORNING)
        viewBeforeRest?.let { (turn, tilt, distance) -> view.aim(turn, tilt, distance) }
        viewBeforeRest = null
        onMorning()
    }

    /** Lights the town as [lighting] says for the time of day, greyed as today's weather asks. */
    private fun light(lighting: Lighting) {
        this.lighting = lighting
        val lit = DayLight.weathered(lighting, shownWeather)
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

    /** Puts the character where it is: walking with its legs and arms swinging, or sitting on the bench. */
    private fun placeCharacter() {
        val legs = listOf(limbs.getValue(Limb.LEFT_LEG), limbs.getValue(Limb.RIGHT_LEG))
        val arms = listOf(limbs.getValue(Limb.LEFT_ARM), limbs.getValue(Limb.RIGHT_ARM))
        if (sitting) {
            val seat = Neighbourhood.benchSeat
            // A little forward on the seat, toward the way the bench faces.
            character.position.set(seat.x, SITTING_LIFT, seat.z + 0.1 * cos(Neighbourhood.BENCH_FACING))
            character.rotation.y = Neighbourhood.BENCH_FACING
            // Legs straight out in front, hands in the lap.
            for (leg in legs) leg.rotation.x = -PI / 2
            for (arm in arms) arm.rotation.x = -0.5
            return
        }
        val current = trip
        val elapsed = window.performance.now() - tripStarted
        val swing = current?.swingAfter(elapsed) ?: 0.0
        character.position.set(spot.x, current?.liftAfter(elapsed) ?: 0.0, spot.z)
        character.rotation.y = heading
        legs[0].rotation.x = swing
        legs[1].rotation.x = -swing
        arms[0].rotation.x = -swing * 0.8
        arms[1].rotation.x = swing * 0.8
    }

    private fun placeCamera() {
        val eye = view.eye
        val focus = view.focus
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
        val width = canvas.clientWidth.toDouble()
        val height = canvas.clientHeight.toDouble()
        val covered = coveredTop()
        // The small map sits in the top right corner, just under the top card and the day's log.
        val top = covered + MAP_GAP
        if (top != mapTop) {
            mapTop = top
            minimap.element.style.top = "${top}px"
        }
        // The names are all one line, so the first one tells how tall a name is.
        val tall = labels.values.first().offsetHeight.toDouble()
        for ((place, label) in labels) {
            val point = view.screenPoint(Neighbourhood.venue(place).labelAnchor)
            val shown = point != null && point.left in 0.0..1.0 && point.top in 0.0..1.0
            label.classList.toggle("behind", !shown)
            // A name that would reach up under the top card or the day's log is hidden there, and out of the finger's
            // way; it stays for the keyboard and a screen reader, as its place's person is still there to talk to.
            val under = covered > 0.0 && shown && point != null && point.top * height - tall < covered
            label.classList.toggle("under", under)
            label.tabIndex = if (shown) 0 else -1
            if (shown) label.removeAttribute("aria-hidden") else label.setAttribute("aria-hidden", "true")
            if (point != null) {
                val left = point.left * width
                val top = point.top * height
                label.style.transform = "translate(${left}px, ${top}px) translate(-50%, -100%)"
            }
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
