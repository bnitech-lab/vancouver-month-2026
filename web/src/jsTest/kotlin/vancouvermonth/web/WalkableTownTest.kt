package vancouvermonth.web

import kotlin.js.Date
import kotlin.js.Promise
import kotlin.math.abs
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
import vancouvermonth.core.Month
import vancouvermonth.text.actionName
import vancouvermonth.text.money
import vancouvermonth.text.Passer
import vancouvermonth.text.Weather
import vancouvermonth.text.weatherOn
import vancouvermonth.text.weatherWord

/** A town to walk in that draws nothing, so a test can make it fail on purpose. Its nights pass at once. */
private class SilentTown : WalkableTown {
    override val element = (document.createElement("div") as HTMLElement).also { it.className = "walk-town" }
    override var onPlace: (Place?) -> Unit = {}
    override var onPasserby: (Passer) -> Unit = {}
    override var onFail: () -> Unit = {}
    override var onMorning: () -> Unit = {}
    override var isCovered = false
    override var weather = Weather.SUNNY
    override var onSounds: (Double, Double, Double) -> Unit = { _, _, _ -> }

    override fun mark(place: Place?) {}

    override fun endChat() {}

    override fun endDay(place: Place) = onMorning()

    override fun close() {}
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

    /**
     * Plays seed 1011 in a real town: the cheapest room, an exact guess, and September starts. [settle] is how long
     * a talk ignores taps after it opens (0 lets a test answer at once) and [night] how long a change of day takes.
     * With [phone], the town is 375 by 812 points, a phone's shape, whatever the test browser's window, so every
     * point a test projects lands where it would on a phone. The town's own life runs by [life], a clock the test
     * holds: by default it stays at 0, so the passersby, the train and the rest stand still where a test can find them.
     * With [weather], each day's weather is the test's own instead of the seed's.
     */
    private fun startInTown(
        settle: Double = 0.0,
        night: Double = DayLight.MILLIS,
        phone: Boolean = true,
        life: () -> Double = { 0.0 },
        weather: ((Int) -> Weather)? = null,
    ): NeighbourhoodScene {
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = {
            val opened = assertNotNull(openWalkableTown(lessMotion = false), "The test browser has no WebGL 2")
            (opened as NeighbourhoodScene).also {
                it.nightMillis = night
                it.lifeClock = life
                town = it
            }
        })
        game.talkSettleMillis = settle
        if (weather != null) game.weatherOf = weather
        game.show()
        startMonth()
        val started = assertNotNull(town)
        if (phone) {
            val element = started.element
            element.style.setProperty("inset", "auto")
            element.style.left = "0px"
            element.style.top = "0px"
            element.style.width = "375px"
            element.style.height = "812px"
        }
        return started
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

    private fun canvasOf(town: NeighbourhoodScene) = town.element.querySelector("canvas") as HTMLCanvasElement

    /** Where [point] is on the town's screen; it must be in front of the camera. */
    private fun onScreen(town: NeighbourhoodScene, point: Point): FramePoint =
        assertNotNull(town.screenPoint(point), "$point is behind the camera")

    /**
     * Taps the town's canvas at [point], 0.0 to 1.0 across and down, as a finger would, at that exact point rather
     * than the nearest whole pixel. The point must be well inside the canvas, below where the top card sits and
     * above where a talk opens.
     */
    private fun tapScreen(town: NeighbourhoodScene, point: FramePoint) {
        val clear = point.left in 0.05..0.95 && point.top in 0.2..0.65
        assertTrue(clear, "$point is near the edge, or under the top card or a talk")
        val canvas = canvasOf(town)
        val frame = canvas.getBoundingClientRect()
        for (type in listOf("pointerdown", "pointerup")) {
            val init: dynamic = js("({})")
            init.pointerId = 1
            init.clientX = frame.left + point.left * frame.width
            init.clientY = frame.top + point.top * frame.height
            init.bubbles = true
            canvas.dispatchEvent(PointerEvent(type, init.unsafeCast<PointerEventInit>()))
        }
    }

    /** One finger event on the canvas: [id] tells the fingers apart, and [first] marks the first finger of a touch. */
    private fun finger(canvas: HTMLCanvasElement, type: String, x: Int, y: Int, id: Int = 1, first: Boolean = false) {
        val init = PointerEventInit(pointerId = id, isPrimary = first, clientX = x, clientY = y, bubbles = true)
        canvas.dispatchEvent(PointerEvent(type, init))
    }

    private fun wheel(town: NeighbourhoodScene) {
        canvasOf(town).dispatchEvent(WheelEvent("wheel", WheelEventInit(deltaY = 200.0, bubbles = true, cancelable = true)))
    }

    private fun drag(town: NeighbourhoodScene) {
        val canvas = canvasOf(town)
        finger(canvas, "pointerdown", 100, 300)
        for (x in listOf(120, 160, 200)) finger(canvas, "pointermove", x, 300)
        finger(canvas, "pointerup", 200, 300)
    }

    /** A step of a test that waits: its name in a failure, when it is ready to run, and what it does. */
    private class Step(val name: String, val ready: () -> Boolean, val run: () -> Unit)

    /** Ready once [millis] have passed since the step before was done. */
    private fun after(millis: Int): () -> Boolean {
        var since: Double? = null
        return {
            val now = Date.now()
            val start = since ?: now.also { since = it }
            now - start >= millis
        }
    }

    /** Runs each step once it is ready, as one test the runner waits for. A failure says which step it was. */
    private fun play(vararg steps: Step): Promise<Unit> = Promise { resolve, reject ->
        fun run(index: Int) {
            if (index == steps.size) {
                resolve(Unit)
                return
            }
            val step = steps[index]
            whenReady(step.ready, { error -> reject(AssertionError("${step.name}: ${error.message}")) }) {
                step.run()
                run(index + 1)
            }
        }
        run(0)
    }

    /** Checks [ready] every 20 ms and then runs [then], or fails after fifteen seconds, which a slow machine needs. */
    private fun whenReady(ready: () -> Boolean, fail: (Throwable) -> Unit, then: () -> Unit) {
        val started = Date.now()
        fun check() {
            try {
                when {
                    ready() -> then()
                    Date.now() - started > 15_000 -> fail(AssertionError("Waited too long: $screen"))
                    else -> window.setTimeout({ check() }, 20)
                }
            } catch (error: Throwable) {
                fail(error)
            }
        }
        check()
    }

    @Test
    fun `the town fills the screen, with the five names and the day's figures over it, and no day buttons`() {
        startInTown(phone = false)
        val town = root.querySelector(".walk-town") as HTMLElement
        val frame = town.getBoundingClientRect()
        assertEquals(window.innerWidth.toDouble(), frame.width)
        assertEquals(window.innerHeight.toDouble(), frame.height)
        val names = town.querySelectorAll(".place-name").asList().map { it.textContent }
        assertEquals(listOf("내 방", "카페", "도서관", "코워킹", "회사"), names)
        assertEquals("three.js r169", town.querySelector("canvas")?.getAttribute("data-engine"))
        assertTrue("오늘9월 2일" in screen && "잔고\$1,835" in screen && "10월 총비용\$1,165" in screen, screen)
        assertTrue("영어 1/10 · 코딩 3/10 · 체력 5/5" in screen, screen)
        assertTrue("땅을 누르면 걷고" in (root.querySelector(".town-hint")?.textContent ?: ""), "the first day's hint")
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
        return play(
            Step("tap the street", { town.isStill }) {
                tapScreen(town, onScreen(town, Point(target.x, 0.0, target.z)))
                val marker = assertNotNull(town.markerSpot, "no marker")
                assertTrue(distanceBetween(marker, target) < 0.01, "the marker is at $marker")
                assertTrue(town.isMarkerShown, "the marker is hidden")
                assertFalse(town.isDashing, "one tap dashes")
            },
            Step("0.7 s later", after(700)) {
                val now = town.characterSpot
                assertTrue(distanceBetween(now, target) < distanceBetween(start, target) - 1.0, "still at $now")
            },
        )
    }

    @Test
    fun `a double tap on the ground dashes`(): Promise<Unit> {
        val town = startInTown()
        return play(
            Step("tap the street twice", { town.isStill }) {
                val point = onScreen(town, Point(town.characterSpot.x + 6.0, 0.0, 13.5))
                tapScreen(town, point)
                tapScreen(town, point)
                assertTrue(town.isDashing, "no dash")
            },
        )
    }

    @Test
    fun `a drag turns the camera and the wheel takes it farther`() {
        val town = startInTown()
        val turn = town.cameraTurn
        drag(town)
        assertEquals(turn - 100 * FollowCamera.TURN_PER_PIXEL, town.cameraTurn, 1e-9)
        assertNull(town.markerSpot, "a drag is not a tap")
        val distance = town.cameraDistance
        wheel(town)
        assertTrue(town.cameraDistance > distance)
    }

    @Test
    fun `a finger whose lift went missing is forgotten when the next touch starts`() {
        val town = startInTown()
        val canvas = canvasOf(town)
        // A finger goes down and its lift never comes.
        finger(canvas, "pointerdown", 50, 50, id = 7, first = true)
        // The next touch is one finger again, so a drag turns the camera instead of reading as a pinch.
        val turn = town.cameraTurn
        val distance = town.cameraDistance
        finger(canvas, "pointerdown", 100, 300, id = 8, first = true)
        finger(canvas, "pointermove", 200, 300, id = 8, first = true)
        finger(canvas, "pointerup", 200, 300, id = 8, first = true)
        assertEquals(turn - 100 * FollowCamera.TURN_PER_PIXEL, town.cameraTurn, 1e-9)
        assertEquals(distance, town.cameraDistance)
        // A finger the browser takes over is forgotten too.
        finger(canvas, "pointerdown", 100, 300, id = 9)
        finger(canvas, "pointercancel", 100, 300, id = 9)
        drag(town)
        assertEquals(turn - 200 * FollowCamera.TURN_PER_PIXEL, town.cameraTurn, 1e-9)
    }

    @Test
    fun `a tap on a person or on a building opens that place's talk`(): Promise<Unit> {
        val town = startInTown()
        val home = Neighbourhood.venue(Place.HOME)
        val cafe = Neighbourhood.venue(Place.CAFE)
        // The middle of the café's roof, seen from above, rather than its front, which the first view sees edge on.
        val cafeRoof = Point(cafe.x + cafe.width / 2.0, cafe.height + 1.0, cafe.z + cafe.depth / 2.0)
        return play(
            Step("tap the neighbour", { town.isStill }) {
                tapScreen(town, onScreen(town, Point(home.person.x, 1.3, home.person.z)))
                val talk = root.querySelector(".talk")?.textContent.orEmpty()
                assertTrue("이웃 할머니" in talk, "no talk with the neighbour: $screen")
                tap("그만")
                assertNull(root.querySelector(".talk"), "그만 left the talk open")
            },
            Step("tap the café's roof once the character has reached the neighbour", { town.isStill }) {
                tapScreen(town, onScreen(town, cafeRoof))
                val talk = root.querySelector(".talk")?.textContent.orEmpty()
                assertTrue("하나" in talk && "알바하기" in talk && "+\$110, 체력 -1" in talk, "no talk with 하나: $screen")
            },
        )
    }

    @Test
    fun `a double tap on a place only walks there, and the new talk ignores taps for a moment`(): Promise<Unit> {
        val town = startInTown(settle = 400.0)
        val cafe = Neighbourhood.venue(Place.CAFE)
        return play(
            Step("tap the café's roof twice, then its answer and 그만 at once", { town.isStill }) {
                val roof = Point(cafe.x + cafe.width / 2.0, cafe.height + 1.0, cafe.z + cafe.depth / 2.0)
                val onRoof = onScreen(town, roof)
                tapScreen(town, onRoof)
                tapScreen(town, onRoof)
                assertTrue(town.isDashing, "the second tap did not dash")
                // A tap on the answer, as a finger lands where the talk has just come up, does nothing.
                spendButton().click()
                assertTrue("오늘9월 2일" in screen, "the day was spent: $screen")
                assertFalse(town.isChangingDay, "the night began")
                tap("그만")
                assertNotNull(root.querySelector(".talk"), "그만 closed the talk at once")
            },
            Step("answer once the talk has settled", after(450)) {
                spendButton().click()
                assertTrue("오늘9월 3일" in screen, "the day was not spent: $screen")
                assertTrue(town.isChangingDay, "no night")
            },
        )
    }

    @Test
    fun `a talk is a dialog that takes the focus, and after an answer the focus goes to the day's log`() {
        startInTown()
        talkTo(Place.LIBRARY)
        val talk = root.querySelector(".talk") as HTMLElement
        assertEquals("dialog", talk.getAttribute("role"))
        assertEquals("레오", talk.getAttribute("aria-label"))
        assertSame(talk, document.activeElement)
        tap("영어 공부")
        assertSame(root.querySelector(".over-town .log"), document.activeElement)
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
        assertTrue("잔고${money(month.balance)}" in screen, screen)
        assertTrue("9월 2일에 있었던 일" in screen && "영어 실력이 올랐어요. 영어 2/10" in screen, screen)
        assertTrue("영어 2/10 · 코딩 3/10 · 체력 4/5" in screen, screen)
        assertNull(root.querySelector(".talk"), "the talk closes when the day is spent")
    }

    @Test
    fun `the change of day takes no input, and the morning gives back the player's camera`(): Promise<Unit> {
        val town = startInTown()
        var turn = 0.0
        var tilt = 0.0
        var distance = 0.0
        var fireside = Triple(0.0, 0.0, 0.0)
        return play(
            Step("turn the camera, start a walk, then rest", { town.isStill }) {
                // The player's own view, after a drag, before the night.
                drag(town)
                turn = town.cameraTurn
                tilt = town.cameraTilt
                distance = town.cameraDistance
                // A walk started before the answer leaves no marker behind.
                tapScreen(town, onScreen(town, Point(town.characterSpot.x + 4.0, 0.0, 13.5)))
                assertTrue(town.isMarkerShown, "no marker")
                talkTo(Place.HOME)
                tap("쉬기")
                assertTrue(town.isSitting, "not on the bench")
                assertFalse(town.isMarkerShown, "the marker stayed")
                fireside = Triple(town.cameraTurn, town.cameraTilt, town.cameraDistance)
            },
            Step("in the night", after(300)) {
                // In the night: no talk, no walk, no turn and no zoom.
                talkTo(Place.CAFE)
                assertNull(root.querySelector(".talk"), "a talk opened")
                tapScreen(town, FramePoint(0.5, 0.6))
                assertNull(town.markerSpot, "a walk began")
                drag(town)
                wheel(town)
                val view = Triple(town.cameraTurn, town.cameraTilt, town.cameraDistance)
                assertEquals(fireside, view, "the camera moved")
                assertTrue(town.isSitting, "got up")
            },
            Step("in the morning", { !town.isChangingDay }) {
                assertFalse(town.isSitting, "still on the bench")
                assertEquals(Neighbourhood.venue(Place.HOME).door, town.characterSpot, "not at the door")
                assertFalse(town.isMarkerShown, "a marker is left")
                assertEquals(turn, town.cameraTurn, 1e-9, "the turn")
                assertEquals(tilt, town.cameraTilt, 1e-9, "the tilt")
                assertEquals(distance, town.cameraDistance, 1e-9, "the distance")
                talkTo(Place.CAFE)
                assertNotNull(root.querySelector(".talk"), "the morning takes no input")
            },
        )
    }

    @Test
    fun `on an interview day only the office opens, and the others say why`(): Promise<Unit> {
        val town = startInTown(night = 120.0)
        return Promise { resolve, reject ->
            fun nextDay() {
                if ("오늘은 면접 날이에요" in screen) {
                    for (place in listOf(Place.HOME, Place.CAFE, Place.LIBRARY, Place.COWORKING)) {
                        talkTo(place)
                        assertTrue(spendButton().disabled, "$place")
                        assertTrue("오늘은 면접 날이에요." in spendButton().textContent.orEmpty(), "$place")
                    }
                    talkTo(Place.OFFICE)
                    assertEquals("면접 보기", spendButton().querySelector(".action-name")?.textContent)
                    assertFalse(spendButton().disabled)
                    assertEquals("회사", root.querySelector(".place-name.here")?.textContent)
                    spendButton().click()
                    assertTrue("1차 영어 면접 통과!" in screen || "1차 영어 면접에서 떨어졌어요." in screen, screen)
                    resolve(Unit)
                    return
                }
                talkTo(Place.OFFICE)
                if (spendButton().disabled) talkTo(Place.HOME)
                spendButton().click()
                whenReady({ !town.isChangingDay }, reject) { nextDay() }
            }
            try {
                nextDay()
            } catch (error: Throwable) {
                reject(error)
            }
        }
    }

    @Test
    fun `a whole month played by talking, through every change of day, ends exactly as it does with the buttons`():
        Promise<Unit> {
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
                val choice = button(actionName(cycle[day % cycle.size]))
                if (choice.disabled) tap("쉬기") else choice.click()
            }
            day++
        }
        val withButtons = endingText()

        root.innerHTML = ""
        // Each night is shortened to a tenth of a second, so the month plays in a few seconds; every day still goes
        // through the whole change of day, frame by frame, to the town's morning.
        val town = startInTown(night = 120.0)
        val turn = town.cameraTurn
        val tilt = town.cameraTilt
        val distance = town.cameraDistance
        val door = Neighbourhood.venue(Place.HOME).door
        day = 0
        return Promise { resolve, reject ->
            fun nextDay() {
                if ("다시 하기" in screen) {
                    assertEquals(withButtons, endingText())
                    assertNull(root.querySelector(".walk-town"), "the town closes at the ending")
                    resolve(Unit)
                    return
                }
                val action = cycle[day % cycle.size]
                if ("오늘은 면접 날이에요" in screen) {
                    talkTo(Place.OFFICE)
                } else {
                    talkTo(placeFor(action))
                    if (spendButton().disabled) talkTo(Place.HOME)
                }
                spendButton().click()
                day++
                if ("다시 하기" !in screen) {
                    // In the night no talk opens.
                    talkTo(Place.CAFE)
                    assertNull(root.querySelector(".talk"), "a talk opened in the night")
                }
                whenReady({ "다시 하기" in screen || !town.isChangingDay }, reject) {
                    if ("다시 하기" !in screen) {
                        // Each morning: at 내 방's door, standing, no marker, the player's own camera.
                        assertEquals(door, town.characterSpot, "day $day")
                        assertFalse(town.isSitting, "day $day")
                        assertFalse(town.isMarkerShown, "day $day")
                        assertEquals(turn, town.cameraTurn, 1e-9, "day $day")
                        assertEquals(tilt, town.cameraTilt, 1e-9, "day $day")
                        assertEquals(distance, town.cameraDistance, 1e-9, "day $day")
                    }
                    nextDay()
                }
            }
            try {
                nextDay()
            } catch (error: Throwable) {
                reject(error)
            }
        }
    }

    @Test
    fun `a tap on a passerby opens small talk with only 그만, and the day stays the same`(): Promise<Unit> {
        val town = startInTown()
        return play(
            Step("tap 미나 on the pavement", { town.isStill }) {
                val mina = town.passerbySpots.first { it.first == Passer.MINA }.second
                tapScreen(town, onScreen(town, Point(mina.x, 1.3, mina.z)))
                val talk = root.querySelector(".talk") as? HTMLElement ?: error("no talk: $screen")
                assertEquals("미나", talk.getAttribute("aria-label"))
                assertTrue("우산 든 학생" in talk.textContent.orEmpty(), talk.textContent)
                val answers = talk.querySelectorAll("button").asList().map { it.textContent }
                assertEquals(listOf("그만"), answers)
                assertNull(root.querySelector(".talk .spend"), "a talk with a passerby spends a day")
                assertSame(talk, document.activeElement)
                assertTrue("오늘9월 2일" in screen && "잔고\$1,835" in screen, screen)
                assertFalse(town.isChangingDay)
            },
            Step("그만, then the street", { true }) {
                tap("그만")
                assertNull(root.querySelector(".talk"), "그만 left the talk open")
                assertTrue("오늘9월 2일" in screen, screen)
            },
        )
    }

    @Test
    fun `the town's own clock moves the train out of the station and back, and the passersby along their loops`():
        Promise<Unit> {
        var clock = 0.0
        val town = startInTown(life = { clock })
        var drawn = 0
        var spots = emptyList<Pair<Passer, Spot>>()
        fun drawnSince(): () -> Boolean = { town.framesDrawn >= drawn + 2 }
        return play(
            Step("at the station", { town.isStill }) {
                assertEquals(0.0, town.trainShiftDrawn, "the train left before its time")
                spots = town.passerbySpots
                clock = SkyTrainRun.DWELL + SkyTrainRun.LEAVE / 2
                drawn = town.framesDrawn
            },
            Step("halfway out", drawnSince()) {
                assertTrue(town.trainShiftDrawn > 20.0, "the train is at ${town.trainShiftDrawn}")
                for ((before, after) in spots.zip(town.passerbySpots)) {
                    assertTrue(distanceBetween(before.second, after.second) > 0.05, "${before.first} stood still")
                }
                clock = SkyTrainRun.PERIOD - SkyTrainRun.ARRIVE / 2
                drawn = town.framesDrawn
            },
            Step("coming in from the west", drawnSince()) {
                assertTrue(town.trainShiftDrawn < -5.0, "the train is at ${town.trainShiftDrawn}")
                clock = SkyTrainRun.PERIOD + 1_000
                drawn = town.framesDrawn
            },
            Step("back at the station", drawnSince()) {
                assertEquals(0.0, town.trainShiftDrawn)
            },
        )
    }

    @Test
    fun `the passersby are gone in the night and out again in the morning`(): Promise<Unit> {
        val town = startInTown(night = 3_000.0)
        return play(
            Step("spend the day", { town.isStill }) {
                assertTrue(town.arePassersbyOut, "nobody out by day")
                talkTo(Place.LIBRARY)
                tap("영어 공부")
                assertTrue(town.isChangingDay)
            },
            Step("in the night", { !town.arePassersbyOut || !town.isChangingDay }) {
                assertTrue(town.isChangingDay, "the passersby were never gone before morning")
            },
            Step("in the morning", { !town.isChangingDay }) {
                assertTrue(town.arePassersbyOut, "not back in the morning")
            },
        )
    }

    @Test
    fun `nothing is drawn while the page is hidden, and the town draws again when it shows`(): Promise<Unit> {
        val town = startInTown()
        var hiddenAt = 0
        return play(
            Step("hide the page", { town.isStill }) {
                town.pageHidden = { true }
                document.dispatchEvent(Event("visibilitychange"))
                // A frame already asked for checks first, so nothing more is drawn from here.
                hiddenAt = town.framesDrawn
                // A drag still turns the camera, but draws nothing while hidden.
                drag(town)
            },
            Step("half a second hidden", after(500)) {
                assertEquals(hiddenAt, town.framesDrawn, "drew while hidden")
                town.pageHidden = { false }
                document.dispatchEvent(Event("visibilitychange"))
            },
            Step("shown again", { town.framesDrawn > hiddenAt }) {},
        )
    }

    @Test
    fun `with only the town's own life moving, it draws no more than thirty frames a second`(): Promise<Unit> {
        var clock = 0.0
        val town = startInTown(life = { clock })
        var from = 0
        return play(
            Step("let the town's life run", { town.isStill }) {
                clock = 1.0
                from = town.framesDrawn
            },
            Step("one second later", after(1_000)) {
                val drawn = town.framesDrawn - from
                assertTrue(drawn in 1..FramePace.AMBIENT_FPS.toInt() + 1, "$drawn frames in a second")
            },
        )
    }

    /** Where 미나 is on the town's screen, at the height of her head. */
    private fun minaOn(town: NeighbourhoodScene): FramePoint {
        val mina = town.passerbySpots.first { it.first == Passer.MINA }.second
        return onScreen(town, Point(mina.x, 1.3, mina.z))
    }

    /** Who the open talk is with, or null when none is open. */
    private fun talkWith(): String? = root.querySelector(".talk")?.getAttribute("aria-label")

    @Test
    fun `a double tap on a passerby hurries the character there, and the small talk stays open with nothing to spend`():
        Promise<Unit> {
        val town = startInTown()
        return play(
            Step("tap 미나 twice", { town.isStill }) {
                val mina = minaOn(town)
                tapScreen(town, mina)
                tapScreen(town, mina)
                assertTrue(town.isDashing, "the second tap did not dash")
                assertEquals("미나", talkWith())
                assertNull(root.querySelector(".talk .spend"), "a small talk can spend a day")
                assertTrue("오늘9월 2일" in screen, screen)
            },
        )
    }

    @Test
    fun `with a small talk open, a tap on the ground closes it`(): Promise<Unit> {
        val town = startInTown()
        return play(
            Step("tap 미나, then the street", { town.isStill }) {
                tapScreen(town, minaOn(town))
                assertEquals("미나", talkWith())
                // Well away from her, so it is not a double tap.
                val street = Neighbourhood.venue(Place.HOME).door
                tapScreen(town, onScreen(town, Point(street.x + 4.0, 0.0, Neighbourhood.STREET_MIDDLE)))
                assertNull(talkWith(), "the small talk stayed open")
                assertNotNull(town.markerSpot, "no walk")
            },
        )
    }

    @Test
    fun `a tap on a passerby replaces a place's talk, and nothing left can spend a day`(): Promise<Unit> {
        val town = startInTown()
        return play(
            Step("talk to 레오", { town.isStill }) {
                talkTo(Place.LIBRARY)
                assertEquals("레오", talkWith())
            },
            Step("tap 미나 once the character stands by 레오", { town.isStill }) {
                assertEquals("레오", talkWith())
                tapScreen(town, minaOn(town))
                assertEquals("미나", talkWith())
                assertNull(root.querySelector(".talk .spend"), "the place's answer is still there")
                assertEquals(1, root.querySelectorAll(".talk").length)
                assertTrue("오늘9월 2일" in screen, screen)
            },
        )
    }

    @Test
    fun `while the day changes, a tap on a passerby opens nothing`(): Promise<Unit> {
        val town = startInTown()
        return play(
            Step("spend the day, then tap 미나 as the evening begins", { town.isStill }) {
                val mina = minaOn(town)
                talkTo(Place.LIBRARY)
                tap("영어 공부")
                assertTrue(town.isChangingDay)
                assertTrue(town.arePassersbyOut, "already gone")
                tapScreen(town, mina)
                assertNull(talkWith(), "a talk opened during the change of day")
            },
        )
    }

    /** The ending's own line and figures. */
    private fun endingText(): String =
        listOf(".ending-line", ".figures").joinToString(" | ") { root.querySelector(it)?.textContent.orEmpty() }

    /** The day's figures in the top card and the day's log, without the weather word. */
    private fun dayFigures(): String {
        val hud = root.querySelector(".hud")?.textContent.orEmpty()
        val weather = root.querySelector(".hud .weather")?.textContent.orEmpty()
        return hud.replace(weather, "") + " | " + root.querySelector(".log")?.textContent.orEmpty()
    }

    /** How high the first streak of rain is drawn when the town's time is [millis]: its top less half a streak. */
    private fun streakAt(millis: Double): Double = Rain.dropAt(0, millis, 0.0, 0.0).y - Rain.STREAK / 2

    @Test
    fun `on seed 1011's sunny days no rain falls, and on its first rainy day rain falls and the top card says 비`():
        Promise<Unit> {
        val days = Month.FIRST_DAY..Month.LAST_DAY
        val rainyDay = days.first { weatherOn(1011, it) == Weather.RAINY }
        assertEquals(Weather.SUNNY, weatherOn(1011, Month.FIRST_DAY))
        var clock = 0.0
        val town = startInTown(night = 120.0, life = { clock })
        var day = Month.FIRST_DAY
        return Promise { resolve, reject ->
            fun nextDay() {
                val word = root.querySelector(".hud .weather")?.textContent
                if (day < rainyDay) {
                    assertFalse(town.isRaining, "rain on day $day")
                    assertEquals("날씨 ${weatherWord(weatherOn(1011, day))}", word, "day $day")
                    talkTo(Place.HOME)
                    spendButton().click()
                    day++
                    whenReady({ !town.isChangingDay }, reject) { nextDay() }
                    return
                }
                assertTrue(town.isRaining, "no rain on day $day")
                assertTrue(town.isRainOnePassLast, "the rain is drawn twice, or before the passersby")
                assertEquals(Weather.RAINY, town.weatherShown)
                assertEquals("날씨 비", word)
                assertTrue("오늘9월 ${day}일" in screen, screen)
                // The rain falls with the town's own time, every streak where the rain's rule puts it.
                assertNotEquals(streakAt(1_000.0), streakAt(1_040.0))
                clock = 1_000.0
                whenReady({ abs(town.firstStreakHeight - streakAt(1_000.0)) < 1e-4 }, reject) {
                    clock = 1_040.0
                    whenReady({ abs(town.firstStreakHeight - streakAt(1_040.0)) < 1e-4 }, reject) {
                        assertTrue(town.arePassersbyOut, "the passersby stayed in")
                        resolve(Unit)
                    }
                }
            }
            try {
                nextDay()
            } catch (error: Throwable) {
                reject(error)
            }
        }
    }

    @Test
    fun `a rainy morning's rain comes in the dark of the night, not as the day is spent`(): Promise<Unit> {
        val town = startInTown(weather = { day -> if (day > Month.FIRST_DAY) Weather.RAINY else Weather.SUNNY })
        return play(
            Step("spend a dry day", { town.isStill }) {
                assertFalse(town.isRaining)
                assertEquals("날씨 맑음", root.querySelector(".hud .weather")?.textContent)
                talkTo(Place.HOME)
                spendButton().click()
                assertTrue(town.isChangingDay)
                // The top card already shows the new day, but the evening is still dry.
                assertEquals("날씨 비", root.querySelector(".hud .weather")?.textContent)
                assertEquals(Weather.RAINY, town.weather)
                assertEquals(Weather.SUNNY, town.weatherShown)
                assertFalse(town.isRaining, "the rain came as the day was spent")
            },
            Step("the rain comes", { town.weatherShown == Weather.RAINY }) {
                assertTrue(town.isChangingDay, "the rain came only in the morning")
                assertTrue(town.isRaining)
            },
            Step("in the morning", { !town.isChangingDay }) {
                assertTrue(town.isRaining)
                assertTrue(town.arePassersbyOut)
            },
        )
    }

    @Test
    fun `the rain changes none of the day's figures: the same choices end each day the same on a sunny run`():
        Promise<Unit> {
        val cycle = listOf(Action.APPLY, Action.STUDY_ENGLISH, Action.PRACTISE_CODING, Action.WORK, Action.REST)
        val days = 8

        /** Plays [days] days in a town where every day is [weather], and gives each morning's figures. */
        fun playIn(weather: Weather): Promise<List<String>> {
            val town = startInTown(night = 120.0, weather = { weather })
            val seen = mutableListOf(dayFigures())
            return Promise { resolve, reject ->
                fun nextDay() {
                    assertEquals(weather == Weather.RAINY, town.isRaining)
                    if (seen.size > days) {
                        resolve(seen)
                        return
                    }
                    if ("오늘은 면접 날이에요" in screen) {
                        talkTo(Place.OFFICE)
                    } else {
                        talkTo(placeFor(cycle[(seen.size - 1) % cycle.size]))
                        if (spendButton().disabled) talkTo(Place.HOME)
                    }
                    spendButton().click()
                    whenReady({ !town.isChangingDay }, reject) {
                        seen += dayFigures()
                        nextDay()
                    }
                }
                try {
                    nextDay()
                } catch (error: Throwable) {
                    reject(error)
                }
            }
        }

        return playIn(Weather.RAINY).then { rainy ->
            town?.close()
            town = null
            root.innerHTML = ""
            for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
            playIn(Weather.SUNNY).then { sunny ->
                assertEquals(days + 1, rainy.size)
                assertEquals(sunny, rainy)
            }
        }.then { }
    }

    @Test
    fun `a sound that fails never fails the town, and the town still lets go when it closes`(): Promise<Unit> {
        var clock = 0.0
        val town = startInTown(life = { clock })
        var failed = false
        val handOver = town.onFail
        town.onFail = {
            failed = true
            handOver()
        }
        town.onSounds = { _, _, _ -> throw IllegalStateException("No sound here") }
        var from = 0
        var closedAt = 0
        return play(
            Step("let the town's life run with a sound that throws", { town.isStill }) {
                from = town.framesDrawn
                clock = 1_000.0
            },
            Step("it goes on drawing", { town.framesDrawn > from + 2 }) {
                clock = 2_000.0
                assertFalse(failed, "the town handed over to the buttons")
                assertNull(root.querySelector(".actions"), "the day's buttons came")
                assertNotNull(root.querySelector(".walk-town"))
            },
            Step("it closes, the sound told last", { town.framesDrawn > from + 4 }) {
                // The quiet sent on closing throws too; the town still lets go of everything.
                town.close()
                closedAt = town.framesDrawn
                clock = 3_000.0
                drag(town)
            },
            Step("and draws nothing after", after(300)) {
                assertEquals(closedAt, town.framesDrawn, "it drew after closing")
                assertFalse(failed)
            },
        )
    }

    @Test
    fun `with rain falling and only the town's own life moving, it still draws no more than thirty frames a second`():
        Promise<Unit> {
        var clock = 0.0
        val town = startInTown(life = { clock }, weather = { Weather.RAINY })
        var from = 0
        return play(
            Step("let the rain fall", { town.isStill }) {
                assertTrue(town.isRaining)
                clock = 1.0
                from = town.framesDrawn
            },
            Step("one second later", after(1_000)) {
                val drawn = town.framesDrawn - from
                assertTrue(drawn in 1..FramePace.AMBIENT_FPS.toInt() + 1, "$drawn frames in a second")
            },
        )
    }
}
