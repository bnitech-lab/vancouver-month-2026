package vancouvermonth.web

import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.asList

/** The real town, drawn with Three.js. The test browser has WebGL through SwiftShader (web/karma.config.d). */
class TownSceneTest {
    @Test
    fun `the town opens where WebGL works, with the five building names`() {
        val town = assertNotNull(openTown(), "The test browser has no WebGL 2")
        val names = town.element.querySelectorAll(".town-label").asList().map { it.textContent }
        assertEquals(listOf("내 방", "카페", "도서관", "코워킹", "회사"), names)
        assertEquals("three.js r169", town.element.querySelector("canvas")?.getAttribute("data-engine"))
        town.close()
    }

    @Test
    fun `the name of the building where the character is stands out, 내 방 from the first day`() {
        val town = assertNotNull(openTown(), "The test browser has no WebGL 2")
        fun marked() = town.element.querySelectorAll(".town-label.here").asList().map { it.textContent }
        assertEquals(listOf("내 방"), marked())
        town.walkTo(Place.CAFE)
        assertEquals(listOf("카페"), marked())
        town.walkTo(Place.HOME)
        assertEquals(listOf("내 방"), marked())
        town.close()
    }

    @Test
    fun `the town draws a whole walk on the page without failing`(): Promise<Unit> {
        val town = assertNotNull(openTown(), "The test browser has no WebGL 2")
        town.element.style.width = "343px"
        document.body!!.appendChild(town.element)
        town.walkTo(Place.OFFICE)
        // The longest walk takes under a second. A failed frame would hide the town.
        return Promise { resolve, reject ->
            window.setTimeout({
                try {
                    assertFalse(town.element.hidden, "The town hid itself after a failed frame")
                    val canvas = town.element.querySelector("canvas") as HTMLCanvasElement
                    assertEquals(canvas.clientWidth * minOf(window.devicePixelRatio, 2.0), canvas.width.toDouble())
                    resolve(Unit)
                } catch (error: Throwable) {
                    reject(error)
                } finally {
                    town.element.remove()
                    town.close()
                }
            }, 1_200)
        }
    }
}
