@file:JsModule("three")

package vancouvermonth.web

import kotlin.js.Json

// Kotlin declarations for the parts of Three.js (https://threejs.org) the town uses, and nothing more.
// An `external` class is written in JavaScript; Kotlin only learns its name and the members listed here.
// Options go in as a plain JavaScript object, built with json("color" to 0x3a7d44).

/** Anything placed in the 3D world: a box, a group of boxes, a light or the camera. */
external open class Object3D {
    val position: Vector3
    val rotation: Euler
    val scale: Vector3
    fun add(child: Object3D): Object3D
    fun lookAt(x: Double, y: Double, z: Double)
}

external class Vector3 {
    fun set(x: Double, y: Double, z: Double): Vector3
}

/** A turn around each axis, in radians. */
external class Euler {
    var y: Double
}

external class Scene : Object3D

/** A camera without perspective: far things are drawn as big as near ones, as in an isometric game. */
external class OrthographicCamera(
    left: Double,
    right: Double,
    top: Double,
    bottom: Double,
    near: Double,
    far: Double,
) : Object3D

/** Draws a scene into a canvas with WebGL. Takes options such as `canvas` and `context`. */
external class WebGLRenderer(parameters: Json) {
    fun setPixelRatio(value: Double)
    fun setSize(width: Int, height: Int, updateStyle: Boolean)
    fun render(scene: Scene, camera: OrthographicCamera)

    /** Lets go of what the renderer holds on the graphics card. */
    fun dispose()
}

/** The shape of a box. The town draws every box from one 1x1x1 box, stretched with `scale`. */
external class BoxGeometry(width: Double, height: Double, depth: Double) {
    fun dispose()
}

/** A colour that light falls on: sides turned away from the sun are darker. Takes the option `color`. */
external class MeshLambertMaterial(parameters: Json) {
    fun dispose()
}

external class Mesh(geometry: BoxGeometry, material: MeshLambertMaterial) : Object3D

/** Several objects that move and turn together, such as the parts of the character. */
external class Group : Object3D

/** Soft light from the whole sky above and the ground below. */
external class HemisphereLight(skyColor: Int, groundColor: Int, intensity: Double) : Object3D

/** Light from one direction, like the sun. */
external class DirectionalLight(color: Int, intensity: Double) : Object3D
