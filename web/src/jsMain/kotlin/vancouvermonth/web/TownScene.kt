package vancouvermonth.web

import kotlin.js.json
import kotlin.math.min
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.RenderingContext

/** What the phone screen needs from the town: an element to place on the page, and a way to send the character. */
interface Town {
    val element: HTMLElement

    /** Walks the character from where it is to [place]. */
    fun walkTo(place: Place)

    /** Lets go of the drawing for good: the WebGL renderer, the box shape and the colours. */
    fun close()
}

/**
 * The town in 3D, or null when this browser cannot draw it: no WebGL 2, which Three.js needs, or any error
 * while setting it up. Then the phone screen has no town and plays as it always did.
 * A test can hand in a [canvas] without WebGL.
 */
fun openTown(canvas: HTMLCanvasElement = document.createElement("canvas") as HTMLCanvasElement): Town? =
    try {
        // Asking for WebGL before Three.js does lets a browser without it fail quietly, with nothing in the console.
        val context = canvas.getContext("webgl2", json("antialias" to true, "alpha" to true))
        if (context == null) null else TownScene(canvas, context)
    } catch (error: Throwable) {
        // Anything else is a fault worth seeing, so it goes to the console before the game goes on without a town.
        console.warn("The town could not be drawn:", error)
        null
    }

/**
 * Draws [TownMap] with Three.js and walks the character between buildings. It draws only while something
 * changes: once when the canvas gets its size, then each frame of a walk, then it rests until the next walk.
 */
private class TownScene(private val canvas: HTMLCanvasElement, context: RenderingContext) : Town {
    override val element = document.createElement("div") as HTMLElement

    private val renderer = WebGLRenderer(json("canvas" to canvas, "context" to context))
    private val scene = Scene()
    private val camera = OrthographicCamera(
        -TownCamera.HALF_WIDTH, TownCamera.HALF_WIDTH, TownCamera.HALF_HEIGHT, -TownCamera.HALF_HEIGHT, 1.0, 200.0,
    )
    private val character = Group()
    private val labels = mutableMapOf<Place, HTMLElement>()

    // Every box shares one cube, stretched to size, and every colour one material.
    private val cube = BoxGeometry(1.0, 1.0, 1.0)
    private val materials = mutableMapOf<Int, MeshLambertMaterial>()

    // The town is drawn again when it gets a size: when it first appears, and when the phone turns.
    private val resizeObserver = ResizeObserver { fit() }

    /** The building the character stands at, or is walking to. */
    private var standingAt = Place.HOME
    private var walk: Walk? = null
    private var walkStarted = 0.0
    private var frameAsked = false
    private var fittedWidth = 0
    private var fittedHeight = 0
    private var closed = false

    init {
        element.className = "town"
        // The same shape as the camera's frame, so the names land on their buildings at any width.
        element.style.setProperty("aspect-ratio", "${TownCamera.FRAME_WIDTH} / ${TownCamera.FRAME_HEIGHT}")
        // The day's log already says where the day went, so a screen reader skips the picture.
        element.setAttribute("aria-hidden", "true")
        element.appendChild(canvas)
        // A phone screen may have three pixels per point or more; two look as sharp and cost far less.
        renderer.setPixelRatio(min(window.devicePixelRatio, 2.0))

        addLights()
        addBoxes()
        val eye = TownCamera.position
        val target = TownCamera.target
        camera.position.set(eye.x, eye.y, eye.z)
        camera.lookAt(target.x, target.y, target.z)
        addLabels()
        stand(standingAt)
        highlight(standingAt)

        resizeObserver.observe(canvas)
        // A phone may take WebGL back from a page in the background. The town hides until it is given back.
        canvas.addEventListener("webglcontextlost", { element.hidden = true })
        canvas.addEventListener("webglcontextrestored", {
            element.hidden = false
            askForFrame()
        })
    }

    /** Soft light from the sky, and a sun from the north-west so each box shows a light side and a dark one. */
    private fun addLights() {
        scene.add(HemisphereLight(0xFFFFFF, 0x9AA88A, 2.2))
        val sun = DirectionalLight(0xFFF3E0, 2.0)
        sun.position.set(-5.0, 12.0, 8.0)
        scene.add(sun)
    }

    /** Every box of the town, and the character: a group of boxes that moves as one. */
    private fun addBoxes() {
        for (block in TownMap.blocks) scene.add(box(block))
        for (block in TownMap.character) character.add(box(block))
        character.scale.set(TownMap.CHARACTER_SCALE, TownMap.CHARACTER_SCALE, TownMap.CHARACTER_SCALE)
        scene.add(character)
    }

    /** One box: the shared cube stretched to the block's size, in the block's colour. */
    private fun box(block: Block): Mesh {
        val material = materials.getOrPut(block.colour) { MeshLambertMaterial(json("color" to block.colour)) }
        val mesh = Mesh(cube, material)
        mesh.scale.set(block.width, block.height, block.depth)
        // A cube is placed by its middle.
        mesh.position.set(block.x + block.width / 2, block.y + block.height / 2, block.z + block.depth / 2)
        return mesh
    }

    /** Each building's name, laid over the picture just above its roof. */
    private fun addLabels() {
        for (building in TownMap.buildings) {
            val spot = TownCamera.framePoint(building.labelAnchor)
            val label = document.createElement("span") as HTMLElement
            label.className = "town-label"
            // The picture has one building of each kind, so each goes by its kind's word.
            label.textContent = building.place.kind.label
            label.style.left = "${spot.left * 100}%"
            label.style.top = "${spot.top * 100}%"
            element.appendChild(label)
            labels[building.place] = label
        }
    }

    override fun walkTo(place: Place) {
        // A walk still going when the next day starts ends at once.
        walk?.let { stand(it.to) }
        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            // The phone asks for less motion, so the character is simply there.
            walk = null
            stand(place)
        } else {
            walk = Walk(standingAt, place)
            walkStarted = window.performance.now()
        }
        standingAt = place
        highlight(place)
        askForFrame()
    }

    /** Marks the name of the building of the kind where the day goes, and only that one. */
    private fun highlight(place: Place) {
        for ((labelPlace, label) in labels) label.classList.toggle("here", labelPlace.kind == place.kind)
    }

    override fun close() {
        closed = true
        resizeObserver.disconnect()
        renderer.dispose()
        cube.dispose()
        for (material in materials.values) material.dispose()
    }

    private fun stand(place: Place) {
        val door = TownMap.building(place).door
        character.position.set(door.x, 0.0, door.z)
        character.rotation.y = 0.0
    }

    private fun fit() {
        val width = canvas.clientWidth
        val height = canvas.clientHeight
        // Off the page the town has no size. It is fitted again when it comes back.
        if (width == 0 || height == 0 || (width == fittedWidth && height == fittedHeight)) return
        fittedWidth = width
        fittedHeight = height
        renderer.setSize(width, height, false)
        askForFrame()
    }

    /** Draws one frame soon, unless one is already on its way. */
    private fun askForFrame() {
        if (frameAsked) return
        frameAsked = true
        window.requestAnimationFrame { now ->
            frameAsked = false
            drawFrame(now)
        }
    }

    private fun drawFrame(now: Double) {
        if (closed) return
        val current = walk
        if (current != null) {
            val progress = current.progressAfter(now - walkStarted)
            val spot = current.spotAt(progress)
            character.position.set(spot.x, current.liftAt(progress), spot.z)
            character.rotation.y = current.headingAt(progress)
            if (progress >= 1.0) {
                stand(current.to)
                walk = null
            }
        }
        try {
            renderer.render(scene, camera)
        } catch (error: Throwable) {
            // If Three.js fails, the town goes and the game goes on without it.
            console.warn("The town stopped drawing:", error)
            element.hidden = true
            walk = null
            return
        }
        if (walk != null) askForFrame()
    }
}

/** The browser's ResizeObserver, which Kotlin's DOM declarations leave out. Both towns use it. */
internal external class ResizeObserver(callback: () -> Unit) {
    fun observe(target: Element)
    fun disconnect()
}
