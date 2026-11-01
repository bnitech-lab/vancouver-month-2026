@file:JsModule("three")

package vancouvermonth.web

import kotlin.js.Json
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLCanvasElement

// Kotlin declarations for the parts of Three.js (https://threejs.org) the town uses, and nothing more.
// An `external` class is written in JavaScript; Kotlin only learns its name and the members listed here.
// Options go in as a plain JavaScript object, built with json("color" to 0x3a7d44).

/** Anything placed in the 3D world: a box, a group of boxes, a light or the camera. */
external open class Object3D {
    val position: Vector3
    val rotation: Euler
    val scale: Vector3
    var visible: Boolean

    /** Whether the sun's shadow falls from this, and onto this. */
    var castShadow: Boolean
    var receiveShadow: Boolean

    /** Whether it is left out when it seems off screen; false for boxes that move about, whose bounds go stale. */
    var frustumCulled: Boolean

    /** Where it comes among things drawn alike, lowest first; see-through things are drawn after solid ones. */
    var renderOrder: Int

    /** Where it is, how it is turned and how big, all in one; [updateMatrix] works it out from those three. */
    val matrix: Matrix4
    fun updateMatrix()
    fun add(child: Object3D): Object3D
    fun lookAt(x: Double, y: Double, z: Double)
}

external class Vector3 {
    fun set(x: Double, y: Double, z: Double): Vector3
}

external class Vector2 {
    fun set(x: Double, y: Double): Vector2
}

external class Matrix4

/** A turn around each axis, in radians. */
external class Euler {
    var x: Double
    var y: Double
}

/** A colour, set from 0xRRGGBB. */
external class Color(hex: Int) {
    fun setHex(hex: Int): Color
}

/** Haze that fades far things into [color], from [near] to [far] away. */
external class Fog(color: Int, near: Double, far: Double) {
    val color: Color
    var near: Double
    var far: Double
}

external class Scene : Object3D {
    /** The colour behind everything: the sky. */
    var background: Color?
    var fog: Fog?
}

external open class Camera : Object3D

/** A camera without perspective: far things are drawn as big as near ones, as in an isometric game. */
external class OrthographicCamera(
    left: Double,
    right: Double,
    top: Double,
    bottom: Double,
    near: Double,
    far: Double,
) : Camera {
    var left: Double
    var right: Double
    var top: Double
    var bottom: Double
    var near: Double
    var far: Double
    fun updateProjectionMatrix()
}

/** A camera with perspective, as an eye sees: [fov] is how much it sees from top to bottom, in degrees. */
external class PerspectiveCamera(fov: Double, aspect: Double, near: Double, far: Double) : Camera {
    var fov: Double
    var aspect: Double
    fun updateProjectionMatrix()
}

/** Draws a scene into a canvas with WebGL. Takes options such as `canvas` and `context`. */
external class WebGLRenderer(parameters: Json) {
    val shadowMap: ShadowMapSettings
    fun setPixelRatio(value: Double)
    fun setSize(width: Int, height: Int, updateStyle: Boolean)
    fun render(scene: Scene, camera: Camera)

    /** Makes the shaders [scene]'s materials need, ahead of the first frame drawn with them. */
    fun compile(scene: Scene, camera: Camera)

    /** Sends [texture] to the graphics card now, rather than on the first frame that draws it. */
    fun initTexture(texture: CanvasTexture)

    /** Lets go of what the renderer holds on the graphics card. */
    fun dispose()
}

/**
 * Whether the renderer draws shadows, and how soft their edges are. With [autoUpdate] off, the shadows are drawn again
 * only on a frame that sets [needsUpdate].
 */
external interface ShadowMapSettings {
    var enabled: Boolean
    var type: Int
    var autoUpdate: Boolean
    var needsUpdate: Boolean
}

/** Shadows with soft edges. */
external val PCFSoftShadowMap: Int

/** Takes the nearest pixel of a texture instead of blending neighbours, so a few shades stay a few shades. */
external val NearestFilter: Int

/** A texture with one channel, grey levels only. */
external val RedFormat: Int

/** A small picture made from numbers in code. The town's only one is the toon shading's three shades. */
external class DataTexture(data: Uint8Array, width: Int, height: Int, format: Int) {
    var minFilter: Int
    var magFilter: Int
    var needsUpdate: Boolean
    fun dispose()
}

/** A picture drawn on a 2D [canvas], such as a sign's words. Set [needsUpdate] once it is drawn. */
external class CanvasTexture(canvas: HTMLCanvasElement) {
    var needsUpdate: Boolean

    /** How its colours are written: "srgb" for a picture drawn as a screen shows it. */
    var colorSpace: String
    fun dispose()
}

/** A shape made of triangles, shared by every copy drawn from it. */
external open class BufferGeometry {
    fun dispose()
}

/** The shape of a box. The town draws every box from one 1x1x1 box, stretched with `scale`. */
external class BoxGeometry(width: Double, height: Double, depth: Double) : BufferGeometry

/** A flat square standing up, facing +z: two triangles where a box has twelve. Each streak of rain is one. */
external class PlaneGeometry(width: Double, height: Double) : BufferGeometry

/** For a material's `side`: drawn from both faces, as a flat square seen from behind must be. */
external val DoubleSide: Int

/** How a surface looks. Each kind below takes options such as `color`. */
external open class Material {
    /**
     * True to draw a see-through surface seen from both sides in one pass. Otherwise three.js draws it twice a frame,
     * back faces then front, switching its shader between the two.
     */
    var forceSinglePass: Boolean
    fun dispose()
}

/** A colour that light falls on: sides turned away from the sun are darker. */
external class MeshLambertMaterial(parameters: Json) : Material

/**
 * Cartoon shading: light falls in a few flat shades, picked from `gradientMap`, instead of a smooth fade.
 * The option `emissive` is light a surface gives off itself, as a lit window does, as strong as [emissiveIntensity].
 * With the option `transparent`, [opacity] fades it out, from 1.0 (solid) to 0.0 (gone).
 */
external class MeshToonMaterial(parameters: Json) : Material {
    var emissiveIntensity: Double
    var opacity: Double
}

/**
 * A plain colour that no light changes, as rain is. With the option `transparent`, `opacity` lets the town show
 * through.
 */
external class MeshBasicMaterial(parameters: Json) : Material {
    val color: Color
}

external class Mesh(geometry: BufferGeometry, material: Material) : Object3D

/** Many copies of one shape drawn at once, each with its own place, size and colour: one draw for many boxes. */
external class InstancedMesh(geometry: BufferGeometry, material: Material, count: Int) : Object3D {
    fun setMatrixAt(index: Int, matrix: Matrix4)
    fun setColorAt(index: Int, color: Color)
    fun dispose()

    /** Every copy's place, size and turn, sixteen numbers each, column by column, to change while it moves. */
    val instanceMatrix: InstanceMatrices

    /** Every copy's colour, once [setColorAt] has given one; after a change, its `needsUpdate` sends them again. */
    val instanceColor: InstanceColours?
}

/** The numbers behind [InstancedMesh.instanceColor]: [needsUpdate] sends changed colours to the graphics card. */
external interface InstanceColours {
    var needsUpdate: Boolean
}

/**
 * The numbers behind [InstancedMesh.instanceMatrix]; [needsUpdate] sends changed ones to the graphics card, and
 * [setUsage] with [DynamicDrawUsage] tells it they change often. [addUpdateRange] narrows the next send to [count]
 * numbers from [start]; with none given, all of them go.
 */
external interface InstanceMatrices {
    val array: Float32Array
    var needsUpdate: Boolean
    fun setUsage(usage: Int)
    fun addUpdateRange(start: Int, count: Int)
}

/** For numbers sent to the graphics card again and again, such as boxes that move every frame. */
external val DynamicDrawUsage: Int

/** Several objects that move and turn together, such as the parts of the character. */
external class Group : Object3D

/** Soft light from the whole sky above and the ground below. */
external class HemisphereLight(skyColor: Int, groundColor: Int, intensity: Double) : Object3D {
    var intensity: Double

    /** The colour of the light from the sky. */
    val color: Color
}

/** Light from one point all round, like a fire, fading out by [distance]. */
external class PointLight(color: Int, intensity: Double, distance: Double, decay: Double) : Object3D {
    var intensity: Double
}

/** Light from one direction, like the sun, shining from its position toward its [target]. */
external class DirectionalLight(color: Int, intensity: Double) : Object3D {
    var intensity: Double
    val color: Color
    val target: Object3D
    val shadow: SunShadow
}

/** The sun's shadow: the box around the town it is worked out in, its size in pixels, and small corrections. */
external interface SunShadow {
    val camera: OrthographicCamera
    val mapSize: Vector2
    var bias: Double
    var normalBias: Double
}
