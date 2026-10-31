package vancouvermonth.web

import kotlin.js.json
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.RenderingContext
import org.w3c.dom.events.Event
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent
import vancouvermonth.text.Townsperson

/** What the phone screen needs from the town the player walks in. */
interface WalkableTown {
    /** The whole town, to fill the screen. */
    val element: HTMLElement

    /**
     * Called with the place the player turns to: they tapped its building, its name or its person, or walked up to
     * its door or its person. Called with null when they walk off somewhere else.
     */
    var onPlace: (Place?) -> Unit

    /** Called once if the town stops drawing, so the game can go on with buttons. */
    var onFail: () -> Unit

    /**
     * Called when the change of day is over and the character stands at 내 방's door. From [endDay] until then, the
     * town takes no taps, drags, pinches or names.
     */
    var onMorning: () -> Unit

    /** Marks the name of [place], as on an interview day, or no name when null. */
    fun mark(place: Place?)

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
 * It draws only while something moves: a walk, the camera gliding, or the change of day.
 */
internal class NeighbourhoodScene(private val canvas: HTMLCanvasElement, context: RenderingContext) : WalkableTown {
    override val element = document.createElement("div") as HTMLElement
    override var onPlace: (Place?) -> Unit = {}
    override var onFail: () -> Unit = {}
    override var onMorning: () -> Unit = {}

    private val renderer = WebGLRenderer(json("canvas" to canvas, "context" to context))
    private val scene = Scene()
    private val camera = PerspectiveCamera(60.0, 1.0, FollowCamera.NEAR_PLANE, FollowCamera.FAR_PLANE)
    private val sky = Color(DayLight.MORNING.sky)
    private val haze = Fog(DayLight.MORNING.sky, HAZE_NEAR, HAZE_FAR)
    private val ambient = HemisphereLight(DayLight.MORNING.ambientColour, GROUND_LIGHT, DayLight.MORNING.ambient)
    private val sun = DirectionalLight(DayLight.MORNING.sunColour, DayLight.MORNING.sun)

    // Every box is one shared cube, stretched to size. Boxes are drawn many at once, each in its own colour,
    // with cartoon shading: the windows, lamps and fire in a material that glows after dark.
    private val cube = BoxGeometry(1.0, 1.0, 1.0)
    private val shades = toonShades()
    private val material = MeshToonMaterial(json("gradientMap" to shades))
    private val glowMaterial = MeshToonMaterial(json("gradientMap" to shades, "emissive" to GLOW))
    private val meshes = mutableListOf<InstancedMesh>()

    private val character = Group()
    private val limbs = mutableMapOf<Limb, Group>()
    private val marker = Group()
    private val labels = mutableMapOf<Place, HTMLButtonElement>()

    private val home = Neighbourhood.venue(Place.HOME)
    private val view = FollowCamera(Point(home.door.x, FOCUS_HEIGHT, home.door.z))
    private val taps = TapTimer()
    private val resizeObserver = ResizeObserver { fit() }

    /** Where the character stands, or has got to on a walk, and the way it faces. */
    private var spot = home.door
    private var heading = home.facing
    private var trip: Trip? = null
    private var tripStarted = 0.0

    /** True when the end of the walk should open the place there: a walk the player sent by tapping the ground. */
    private var opensOnArrival = false

    /** When the change of day began, or null outside one. While it lasts, the town takes no input. */
    private var nightFell: Double? = null
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
    private var lastFrame = 0.0
    private var framesDrawn = 0
    private var closed = false

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
    private val onRestored: (Event) -> Unit = { askForFrame() }

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
        // A phone screen may have three pixels per point or more; two look as sharp and cost far less.
        renderer.setPixelRatio(min(window.devicePixelRatio, 2.0))

        addLightAndSky()
        addTown()
        addCharacter()
        addPeople()
        addMarker()
        addLabels()

        resizeObserver.observe(canvas)
        // Each finger is captured on press, so its moves and its lift come to the canvas even off it.
        canvas.addEventListener("pointerdown", onPress)
        canvas.addEventListener("pointermove", onMove)
        canvas.addEventListener("pointerup", onRelease)
        canvas.addEventListener("pointercancel", onCancel)
        canvas.addEventListener("lostpointercapture", onCancel)
        canvas.addEventListener("wheel", onWheel, json("passive" to false))
        // A phone may take WebGL back from a page in the background; Three.js sets it up again, and the town redraws.
        canvas.addEventListener("webglcontextrestored", onRestored)
    }

    /** The sky and its haze, soft light from all round, and a warm sun whose shadows fall around the character. */
    private fun addLightAndSky() {
        scene.background = sky
        scene.fog = haze
        scene.add(ambient)
        renderer.shadowMap.enabled = true
        renderer.shadowMap.type = PCFSoftShadowMap
        sun.castShadow = true
        sun.shadow.mapSize.set(SHADOW_PIXELS, SHADOW_PIXELS)
        val shadowBox = sun.shadow.camera
        shadowBox.left = -SHADOW_REACH
        shadowBox.right = SHADOW_REACH
        shadowBox.top = SHADOW_REACH
        shadowBox.bottom = -SHADOW_REACH
        shadowBox.near = 1.0
        shadowBox.far = 90.0
        shadowBox.updateProjectionMatrix()
        sun.shadow.bias = -0.0005
        sun.shadow.normalBias = 0.03
        scene.add(sun)
        scene.add(sun.target)
        light(DayLight.MORNING)
    }

    private fun addTown() {
        // The ground, the street, the water and the mountains take shadows but cast none, which saves the phone work.
        val ground = boxes(Neighbourhood.ground, material)
        ground.castShadow = false
        scene.add(ground)
        scene.add(boxes(Neighbourhood.blocks, material))
        scene.add(boxes(Neighbourhood.glowing, glowMaterial))
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

    /** Each place's person, standing by the door and facing the street. */
    private fun addPeople() {
        for (venue in Neighbourhood.venues) {
            val person = Group()
            person.add(boxes(Figures.standing(Figures.townsperson(personAt(venue.place))), material))
            person.position.set(venue.person.x, 0.0, venue.person.z)
            person.rotation.y = venue.facing
            person.scale.set(Figures.SCALE, Figures.SCALE, Figures.SCALE)
            scene.add(person)
        }
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
            label.onclick = { if (nightFell == null) goTalk(venue.place, dash = false) }
            element.appendChild(label)
            labels[venue.place] = label
        }
    }

    /** One draw for many boxes: the shared cube placed, stretched and coloured once for each block. */
    private fun boxes(blocks: List<Block>, material: Material): InstancedMesh {
        val mesh = InstancedMesh(cube, material, blocks.size)
        val placer = Object3D()
        val colour = Color(0)
        for ((index, block) in blocks.withIndex()) {
            // A cube is placed by its middle.
            placer.position.set(block.x + block.width / 2, block.y + block.height / 2, block.z + block.depth / 2)
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

    override fun mark(place: Place?) {
        for ((labelPlace, label) in labels) label.classList.toggle("here", labelPlace == place)
    }

    override fun endDay(place: Place) {
        trip = null
        marker.visible = false
        pointers.clear()
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
        canvas.removeEventListener("pointerdown", onPress)
        canvas.removeEventListener("pointermove", onMove)
        canvas.removeEventListener("pointerup", onRelease)
        canvas.removeEventListener("pointercancel", onCancel)
        canvas.removeEventListener("lostpointercapture", onCancel)
        canvas.removeEventListener("wheel", onWheel)
        canvas.removeEventListener("webglcontextrestored", onRestored)
        for (mesh in meshes) mesh.dispose()
        renderer.dispose()
        cube.dispose()
        material.dispose()
        glowMaterial.dispose()
        shades.dispose()
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

    internal val isDashing: Boolean
        get() = trip?.speed == Trip.DASH_SPEED

    internal val isMarkerShown: Boolean
        get() = marker.visible

    internal val isChangingDay: Boolean
        get() = nightFell != null

    /** True once the town has drawn and nothing moves: no walk, no change of day, the camera settled. For tests. */
    internal val isStill: Boolean
        get() = framesDrawn > 0 && !frameAsked && trip == null && nightFell == null

    /** The camera's turn, tilt and distance as asked, for tests. */
    internal val cameraTurn: Double
        get() = view.yaw

    internal val cameraTilt: Double
        get() = view.pitch

    internal val cameraDistance: Double
        get() = view.distance

    private fun press(event: PointerEvent) {
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
            askForFrame()
            return
        }
        if (!dragged && hypot(event.x - pressX, event.y - pressY) > TAP_SLOP) dragged = true
        if (dragged && !pinched) {
            view.orbit(event.x - last.first, event.y - last.second)
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
        if (nightFell != null) return
        view.zoom(exp(event.deltaY * WHEEL_ZOOM))
        askForFrame()
    }

    /** How far apart the first two fingers are, in pixels. */
    private fun gap(): Double {
        val (a, b) = pointers.values.take(2)
        return hypot(a.first - b.first, a.second - b.second)
    }

    /**
     * A tap at [x], [y] on the page: talk to a place, or walk to a spot on the ground. The second tap of a double tap
     * dashes; on a place it only dashes there, since the first tap already opened the talk.
     */
    private fun tap(x: Double, y: Double) {
        val frame = canvas.getBoundingClientRect()
        if (frame.width == 0.0 || frame.height == 0.0 || nightFell != null) return
        val dash = taps.isDouble(window.performance.now(), x, y)
        when (val picked = view.pick((x - frame.left) / frame.width, (y - frame.top) / frame.height)) {
            is Picked.AtPlace -> {
                if (dash) {
                    walkTo(Neighbourhood.venue(picked.place).talkSpot, dash = true, opensOnArrival = false)
                } else {
                    goTalk(picked.place, dash = false)
                }
            }
            is Picked.OnGround -> {
                onPlace(null)
                walkTo(picked.spot, dash, opensOnArrival = true)
            }
            null -> {}
        }
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
        view.aspect = width.toDouble() / height
        camera.aspect = view.aspect
        camera.fov = view.fov
        camera.updateProjectionMatrix()
        askForFrame()
    }

    /** Draws one frame soon, unless one is already on its way. */
    private fun askForFrame() {
        if (frameAsked || closed) return
        frameAsked = true
        window.requestAnimationFrame { now ->
            frameAsked = false
            drawFrame(now)
        }
    }

    private fun drawFrame(now: Double) {
        if (closed) return
        // After a rest with no frames, the first one counts as a short step, not the whole pause.
        val millis = (now - lastFrame).coerceIn(0.0, MAX_STEP_MILLIS)
        lastFrame = now
        val walking = stepWalk(now)
        val night = stepNight(now)
        placeCharacter()
        val subject = if (sitting) Neighbourhood.benchSeat else spot
        val gliding = view.follow(Point(subject.x, FOCUS_HEIGHT, subject.z), millis)
        placeCamera()
        try {
            renderer.render(scene, camera)
        } catch (error: Throwable) {
            fail(error)
            return
        }
        placeLabels()
        framesDrawn += 1
        if (walking || night || gliding) askForFrame()
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

    /** At a place's person the character turns to them; at the end of a walk the player sent, the place opens. */
    private fun arrive(end: Spot) {
        val place = Neighbourhood.placeAt(end) ?: return
        val venue = Neighbourhood.venue(place)
        if (distanceBetween(end, venue.talkSpot) <= Neighbourhood.REACH) {
            heading = atan2(venue.person.x - end.x, venue.person.z - end.z)
        }
        if (opensOnArrival) onPlace(place)
    }

    /** Moves the light through evening, night and morning. True while the change of day goes on. */
    private fun stepNight(now: Double): Boolean {
        val start = nightFell ?: return false
        val progress = ((now - start) / nightMillis).coerceIn(0.0, 1.0)
        light(DayLight.at(progress))
        // In the dark the character goes home, unless it spends the night by the fire.
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
        light(DayLight.MORNING)
        viewBeforeRest?.let { (turn, tilt, distance) -> view.aim(turn, tilt, distance) }
        viewBeforeRest = null
        onMorning()
    }

    private fun light(lighting: Lighting) {
        sky.setHex(lighting.sky)
        haze.color.setHex(lighting.sky)
        ambient.intensity = lighting.ambient
        ambient.color.setHex(lighting.ambientColour)
        sun.intensity = lighting.sun
        sun.color.setHex(lighting.sunColour)
        glowMaterial.emissiveIntensity = lighting.glow
    }

    /** Puts the character where it is: walking with its legs and arms swinging, or sitting on the bench. */
    private fun placeCharacter() {
        val legs = listOf(limbs.getValue(Limb.LEFT_LEG), limbs.getValue(Limb.RIGHT_LEG))
        val arms = listOf(limbs.getValue(Limb.LEFT_ARM), limbs.getValue(Limb.RIGHT_ARM))
        if (sitting) {
            val seat = Neighbourhood.benchSeat
            character.position.set(seat.x, SITTING_LIFT, seat.z + 0.1)
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
        for ((place, label) in labels) {
            val point = view.screenPoint(Neighbourhood.venue(place).labelAnchor)
            val shown = point != null && point.left in 0.0..1.0 && point.top in 0.0..1.0
            label.classList.toggle("behind", !shown)
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

        /** How far a finger may move and still count as a tap, in pixels. */
        const val TAP_SLOP = 10.0
        const val WHEEL_ZOOM = 0.0015
        const val MAX_STEP_MILLIS = 50.0

        const val HAZE_NEAR = 70.0
        const val HAZE_FAR = 230.0
        const val GROUND_LIGHT = 0x8FAF6A
        const val GLOW = 0xFFC46B
        const val MARKER = 0xFFE08A

        /** Where the sun is from the character: high in the north-east, so shadows fall short to the south-west. */
        val SUN = Point(12.0, 26.0, -8.0)
        const val SHADOW_PIXELS = 1024.0
        const val SHADOW_REACH = 18.0

        /** The camera for a night by the campfire: south of it, near and low, looking north at the bench. */
        const val FIRESIDE_TURN = 0.15
        const val FIRESIDE_TILT = 0.5
        const val FIRESIDE_DISTANCE = 9.0

        /** How high the body sits so the thighs rest on the bench seat. */
        val SITTING_LIFT = Neighbourhood.SEAT_HEIGHT - (Figures.HIP - 0.09) * Figures.SCALE
    }
}
