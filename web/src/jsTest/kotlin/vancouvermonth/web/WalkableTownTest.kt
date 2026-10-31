package vancouvermonth.web

import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.events.WheelEventInit
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit
import vancouvermonth.core.Action
import vancouvermonth.core.FirstMinute

/** A town to walk in that draws nothing, so a test can make it fail on purpose. */
private class SilentTown : WalkableTown {
    override val element = (document.createElement("div") as HTMLElement).also { it.className = "walk-town" }
    override var onPlace: (Place?) -> Unit = {}
    override var onFail: () -> Unit = {}
    var closed = false

    override fun mark(place: Place?) {}

    override fun endDay(place: Place) {}

    override fun close() {
        closed = true
    }
}

/**
 * The town to walk in, drawn for real with Three.js on SwiftShader (web/karma.config.d), and the month played in it
 * by talking to its people, the way a player would.
 */
class WalkableTownTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private var town: NeighbourhoodScene? = null

    @AfterTest
    fun removeScreen() {
        town?.close()
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    private val screen: String
        get() = root.textContent.orEmpty()

    /** A new real town, kept so the test can close it. */
    private fun openScene(): NeighbourhoodScene {
        val opened = assertNotNull(openWalkableTown(lessMotion = false), "The test browser has no WebGL 2")
        return (opened as NeighbourhoodScene).also { town = it }
    }

    /** Plays seed 1011 in the town: the cheapest room, an exact guess, and September starts. */
    private fun startInTown(): NeighbourhoodScene {
        PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { openScene() }).show()
        startMonth()
        return assertNotNull(town)
    }

    private fun startMonth() {
        val game = FirstMinute(1011)
        tap("${game.cheapestRoomIndex + 1}번 방")
        val input = root.querySelector("input") as HTMLInputElement
        input.value = "${game.lowestTotal}"
        input.dispatchEvent(Event("input"))
        tap("총비용 확인하기")
        tap("시작하기")
    }

    private fun buttons(): List<HTMLButtonElement> =
        root.querySelectorAll("button").asList().map { it as HTMLButtonElement }

    private fun button(text: String): HTMLButtonElement =
        buttons().firstOrNull { text in it.textContent.orEmpty() } ?: error("No button with \"$text\" in: $screen")

    private fun tap(text: String) = button(text).click()

    /** Taps the floating name of [place], which talks to its person. */
    private fun talkTo(place: Place) {
        val name = root.querySelectorAll(".place-name").asList().map { it as HTMLButtonElement }
            .first { it.textContent == place.label }
        name.click()
    }

    /** The answer that spends the day, in the open talk. */
    private fun spendButton(): HTMLButtonElement =
        root.querySelector(".talk .spend") as? HTMLButtonElement ?: error("No talk is open: $screen")

    /** Taps the town's canvas at [point], 0.0 to 1.0 across and down, as a finger would. */
    private fun tapScreen(town: NeighbourhoodScene, point: FramePoint) {
        assertTrue(point.left in 0.0..1.0 && point.top in 0.0..1.0, "$point is off the screen")
        val canvas = town.element.querySelector("canvas") as HTMLCanvasElement
        val frame = canvas.getBoundingClientRect()
        val x = (frame.left + point.left * frame.width).toInt()
        val y = (frame.top + point.top * frame.height).toInt()
        for (type in listOf("pointerdown", "pointerup")) finger(canvas, type, x, y)
    }

    private fun finger(canvas: HTMLCanvasElement, type: String, x: Int, y: Int) {
        canvas.dispatchEvent(PointerEvent(type, PointerEventInit(pointerId = 1, clientX = x, clientY = y, bubbles = true)))
    }

    /** Runs each step [millis] after the one before, as one test the runner waits for. */
    private fun steps(vararg steps: Pair<Int, () -> Unit>): Promise<Unit> = Promise { resolve, reject ->
        fun run(index: Int) {
            if (index == steps.size) {
                resolve(Unit)
                return
            }
            val (millis, step) = steps[index]
            window.setTimeout({
                try {
                    step()
                    run(index + 1)
                } catch (error: Throwable) {
                    reject(error)
                }
            }, millis)
        }
        run(0)
    }

    @Test
    fun `the town fills the screen, with the five names and the day's figures over it, and no day buttons`() {
        startInTown()
        val town = root.querySelector(".walk-town") as HTMLElement
        val frame = town.getBoundingClientRect()
        assertEquals(window.innerWidth.toDouble(), frame.width)
        assertEquals(window.innerHeight.toDouble(), frame.height)
        val names = town.querySelectorAll(".place-name").asList().map { it.textContent }
        assertEquals(listOf("내 방", "카페", "도서관", "코워킹", "회사"), names)
        assertEquals("three.js r169", town.querySelector("canvas")?.getAttribute("data-engine"))
        assertTrue("오늘9월 2일" in screen && "잔고\$1,835" in screen && "10월 총비용\$1,165" in screen, screen)
        assertTrue("영어 1/10 · 코딩 3/10 · 체력 5/5" in screen, screen)
        assertNull(root.querySelector(".actions"))
        assertNull(root.querySelector(".talk"))
    }

    @Test
    fun `a phone that asks for less motion, or a browser without WebGL, gets no town to walk in`() {
        assertNull(openWalkableTown(lessMotion = true))
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.asDynamic().getContext = { _: String, _: Any? -> null }
        assertNull(openWalkableTown(canvas, lessMotion = false))
    }

    @Test
    fun `without WebGL the month plays with the buttons, exactly as before`() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.asDynamic().getContext = { _: String, _: Any? -> null }
        PhoneGame(
            root,
            1011,
            playAgain = {},
            makeTown = { openTown(canvas) },
            makeWalkableTown = { openWalkableTown(canvas, lessMotion = false) },
        ).show()
        startMonth()
        assertNull(root.querySelector(".walk-town"))
        assertNull(root.querySelector(".town"))
        assertSame(root.querySelector(".stats"), root.querySelector(".topbar")?.nextElementSibling)
        tap("카페 알바")
        assertTrue("카페에서 일했어요. +\$110" in screen)
    }

    @Test
    fun `when the town stops drawing, the day's buttons take over`() {
        val silent = SilentTown()
        PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { silent }).show()
        startMonth()
        assertSame(silent.element, root.firstElementChild)
        silent.onFail()
        assertNull(root.querySelector(".walk-town"))
        tap("영어 공부")
        assertTrue("영어 실력이 올랐어요. 영어 2/10" in screen)
    }

    @Test
    fun `a tap on the ground walks the character toward it, with a marker where it goes`(): Promise<Unit> {
        val town = startInTown()
        val start = town.characterSpot
        val target = Spot(start.x + 5.0, 13.5)
        return steps(
            300 to {
                tapScreen(town, assertNotNull(town.screenPoint(Point(target.x, 0.0, target.z))))
                val marker = assertNotNull(town.markerSpot, "no marker")
                assertTrue(distanceBetween(marker, target) < 0.3, "the marker is at $marker")
                assertFalse(town.isDashing)
            },
            700 to {
                val now = town.characterSpot
                assertTrue(distanceBetween(now, target) < distanceBetween(start, target) - 1.0, "still at $now")
            },
        )
    }

    @Test
    fun `a double tap dashes`(): Promise<Unit> {
        val town = startInTown()
        return steps(
            300 to {
                val point = assertNotNull(town.screenPoint(Point(town.characterSpot.x + 6.0, 0.0, 13.5)))
                tapScreen(town, point)
                tapScreen(town, point)
                assertTrue(town.isDashing)
            },
        )
    }

    @Test
    fun `a drag turns the camera and the wheel takes it farther`() {
        val town = startInTown()
        val canvas = town.element.querySelector("canvas") as HTMLCanvasElement
        val turn = town.cameraTurn
        finger(canvas, "pointerdown", 100, 300)
        for (x in listOf(120, 160, 200)) finger(canvas, "pointermove", x, 300)
        finger(canvas, "pointerup", 200, 300)
        assertEquals(turn - 100 * FollowCamera.TURN_PER_PIXEL, town.cameraTurn, 1e-9)
        assertNull(town.markerSpot, "a drag is not a tap")
        val distance = town.cameraDistance
        canvas.dispatchEvent(WheelEvent("wheel", WheelEventInit(deltaY = 200.0, bubbles = true, cancelable = true)))
        assertTrue(town.cameraDistance > distance)
    }

    @Test
    fun `a tap on a person or on a building opens that place's talk`(): Promise<Unit> {
        val town = startInTown()
        val home = Neighbourhood.venue(Place.HOME)
        val cafe = Neighbourhood.venue(Place.CAFE)
        return steps(
            300 to {
                tapScreen(town, assertNotNull(town.screenPoint(Point(home.person.x, 1.3, home.person.z))))
                assertTrue("이웃 할머니" in root.querySelector(".talk")?.textContent.orEmpty(), screen)
                tap("그만")
                assertNull(root.querySelector(".talk"))
            },
            600 to {
                tapScreen(town, assertNotNull(town.screenPoint(Point(cafe.x + 1.0, 2.6, cafe.z.toDouble()))))
                val talk = root.querySelector(".talk")?.textContent.orEmpty()
                assertTrue("하나" in talk && "알바하기" in talk && "+\$110, 체력 -1" in talk, talk)
            },
        )
    }

    @Test
    fun `a person's line types out, a tap finishes it, and small talk changes it`() {
        startInTown()
        talkTo(Place.LIBRARY)
        val line = root.querySelector(".talk-line") as HTMLElement
        val whole = "조용히... 영어 책은... 저쪽 창가에 있어요."
        assertEquals(whole, line.querySelector(".visually-hidden")?.textContent)
        val typed = line.firstElementChild as HTMLElement
        assertTrue(typed.textContent.orEmpty().length < whole.length)
        line.click()
        assertEquals(whole, typed.textContent)
        tap("이야기")
        assertNotEquals(whole, root.querySelector(".talk-line .visually-hidden")?.textContent)
        tap("그만")
        assertNull(root.querySelector(".talk"))
        // A second meeting is not a first one.
        talkTo(Place.LIBRARY)
        assertNotEquals(whole, root.querySelector(".talk-line .visually-hidden")?.textContent)
    }

    @Test
    fun `an answer spends the day in core, and the day's log shows over the town`() {
        startInTown()
        talkTo(Place.LIBRARY)
        tap("영어 공부")
        val month = FirstMinute(1011).let { it.startMonth(it.cheapestRoomIndex) }
        month.take(Action.STUDY_ENGLISH)
        assertTrue("오늘9월 3일" in screen, screen)
        assertTrue("잔고${vancouvermonth.text.money(month.balance)}" in screen, screen)
        assertTrue("9월 2일에 있었던 일" in screen && "영어 실력이 올랐어요. 영어 2/10" in screen, screen)
        assertTrue("영어 2/10 · 코딩 3/10 · 체력 4/5" in screen, screen)
        assertNull(root.querySelector(".talk"), "the talk closes when the day is spent")
    }

    @Test
    fun `a day of rest is spent on the bench by the fire, and the morning starts at 내 방`(): Promise<Unit> {
        val town = startInTown()
        talkTo(Place.HOME)
        tap("쉬기")
        assertTrue(town.isSitting)
        return steps(
            (DayLight.MILLIS + 400).toInt() to {
                assertFalse(town.isSitting)
                assertEquals(Neighbourhood.venue(Place.HOME).door, town.characterSpot)
            },
        )
    }

    @Test
    fun `on an interview day only the office opens, and the others say why`() {
        startInTown()
        while ("오늘은 면접 날이에요" !in screen) {
            talkTo(Place.OFFICE)
            if (spendButton().disabled) {
                talkTo(Place.HOME)
                spendButton().click()
            } else {
                spendButton().click()
            }
        }
        for (place in listOf(Place.HOME, Place.CAFE, Place.LIBRARY, Place.COWORKING)) {
            talkTo(place)
            assertTrue(spendButton().disabled, "$place")
            assertTrue("오늘은 면접 날이에요." in spendButton().textContent.orEmpty(), "$place")
        }
        talkTo(Place.OFFICE)
        assertEquals("면접 보기", spendButton().querySelector(".action-name")?.textContent)
        assertFalse(spendButton().disabled)
        assertTrue(root.querySelector(".place-name.here")?.textContent == "회사")
        spendButton().click()
        assertTrue("1차 영어 면접 통과!" in screen || "1차 영어 면접에서 떨어졌어요." in screen, screen)
    }

    @Test
    fun `a whole month played by talking ends exactly as it does with the buttons`() {
        // The same choices each way: a cycle of the five actions, resting when one cannot be done,
        // and the interview on its day.
        val cycle = listOf(Action.APPLY, Action.STUDY_ENGLISH, Action.PRACTISE_CODING, Action.WORK, Action.REST)
        PhoneGame(root, 1011, playAgain = {}).show()
        startMonth()
        var day = 0
        while ("다시 하기" !in screen) {
            if (buttons().any { it.textContent == "면접 보기" }) {
                tap("면접 보기")
            } else {
                val choice = button(vancouvermonth.text.actionName(cycle[day % cycle.size]))
                if (choice.disabled) tap("쉬기") else choice.click()
            }
            day++
        }
        val withButtons = endingText()

        root.innerHTML = ""
        startInTown()
        day = 0
        while ("다시 하기" !in screen) {
            val action = cycle[day % cycle.size]
            if ("오늘은 면접 날이에요" in screen) {
                talkTo(Place.OFFICE)
            } else {
                talkTo(placeFor(action))
                if (spendButton().disabled) talkTo(Place.HOME)
            }
            spendButton().click()
            day++
        }
        assertEquals(withButtons, endingText())
        assertNull(root.querySelector(".walk-town"), "the town closes at the ending")
    }

    /** The ending's own line and figures. */
    private fun endingText(): String =
        listOf(".ending-line", ".figures").joinToString(" | ") { root.querySelector(it)?.textContent.orEmpty() }
}
