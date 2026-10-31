@file:JsModule("three")

package vancouvermonth.web

import kotlin.js.Json
import org.khronos.webgl.Uint8Array

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

    /** Lets go of what the renderer holds on the graphics card. */
    fun dispose()
}

/** Whether the renderer draws shadows, and how soft their edges are. */
external interface ShadowMapSettings {
    var enabled: Boolean
    var type: Int
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

/** The shape of a box. The town draws every box from one 1x1x1 box, stretched with `scale`. */
external class BoxGeometry(width: Double, height: Double, depth: Double) {
    fun dispose()
}

/** How a surface looks. Each kind below takes options such as `color`. */
external open class Material {
    fun dispose()
}

/** A colour that light falls on: sides turned away from the sun are darker. */
external class MeshLambertMaterial(parameters: Json) : Material

/**
 * Cartoon shading: light falls in a few flat shades, picked from `gradientMap`, instead of a smooth fade.
 * [emissive] is light a surface gives off itself, as a lit window does, as strong as [emissiveIntensity].
 */
external class MeshToonMaterial(parameters: Json) : Material {
    val emissive: Color
    var emissiveIntensity: Double
}

external class Mesh(geometry: BoxGeometry, material: Material) : Object3D

/** Many copies of one shape drawn at once, each with its own place, size and colour: one draw for many boxes. */
external class InstancedMesh(geometry: BoxGeometry, material: Material, count: Int) : Object3D {
    fun setMatrixAt(index: Int, matrix: Matrix4)
    fun setColorAt(index: Int, color: Color)
    fun dispose()
}

/** Several objects that move and turn together, such as the parts of the character. */
external class Group : Object3D

/** Soft light from the whole sky above and the ground below. */
external class HemisphereLight(skyColor: Int, groundColor: Int, intensity: Double) : Object3D {
    var intensity: Double

    /** The colour of the light from the sky. */
    val color: Color
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
