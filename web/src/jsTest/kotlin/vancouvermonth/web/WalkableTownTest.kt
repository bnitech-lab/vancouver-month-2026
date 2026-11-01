package vancouvermonth.web

import kotlin.js.Date
import kotlin.js.Promise
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
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
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.events.WheelEventInit
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit
import vancouvermonth.core.Action
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.core.SharedFare
import vancouvermonth.core.Spending
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.save.Spend
import vancouvermonth.text.Continue
import vancouvermonth.text.actionName
import vancouvermonth.text.money
import vancouvermonth.text.Passer
import vancouvermonth.text.Shop
import vancouvermonth.text.Transit
import vancouvermonth.text.Transport
import vancouvermonth.text.Weather
import vancouvermonth.text.weatherOn
import vancouvermonth.text.weatherWord

/** A town to walk in that draws nothing, so a test can make it fail on purpose. Its nights pass at once. */
private class SilentTown : WalkableTown {
    override val element = (document.createElement("div") as HTMLElement).also { it.className = "walk-town" }
    override var onPlace: (Place?) -> Unit = {}
    override var onPasserby: (Passer) -> Unit = {}
    override var onShop: () -> Unit = {}
    override var onSentTo: (String?, Double) -> Unit = { _, _ -> }
    override var onRent: (Ride) -> Unit = {}
    override var onStop: (Stop) -> Unit = {}
    override var onTravelled: () -> Unit = {}
    override var riding: Ride? = null
    override var travelling: Transport? = null

    /** How far a taxi drives to any place, or null when none goes: the test's to set. */
    var taxiSquares: Double? = 30.0
    override var onFail: () -> Unit = {}
    override var onMorning: () -> Unit = {}
    override var onArrived: (Place) -> Unit = {}
    override fun isAt(place: Place) = true

    override fun passTime(share: Double) {}

    override fun showTime(share: Double) {}
    override var isCovered = false
    override var weather = Weather.SUNNY
    override var onSounds: (Double, Double, Double) -> Unit = { _, _, _ -> }
    override var coveredTop: () -> Double = { 0.0 }

    override fun mark(place: Place?) {}

    override fun endChat() {}

    /** As the real town: on nothing while already riding. A shared one stands for one the character has reached. */
    override fun canRide(ride: Ride): Boolean = riding == null

    override fun ride(ride: Ride) {
        if (canRide(ride)) riding = ride
    }

    override fun getOff() {
        riding = null
    }

    override fun taxiDistance(to: Spot): Double? = taxiSquares

    /** As the real town: not while already travelling. The character stands for one at any stop. */
    override fun canTravel(transport: Transport, from: Int, to: Int): Boolean = travelling == null && from != to

    override fun travel(transport: Transport, from: Int, to: Int) {
        if (!canTravel(transport, from, to)) return
        riding = null
        travelling = transport
    }

    override fun standStill() {}

    override fun leaveTaxi() {
        if (travelling == Transport.TAXI) arrive()
    }

    /** The ride under way is over: the character is out at its stop. */
    fun arrive() {
        travelling = null
        onTravelled()
    }

    override fun endDay() = onMorning()

    override fun close() {}
}

/**
 * The town to walk in, drawn for real with Three.js on SwiftShader (web/karma.config.d), and the month played in it
 * by talking to its people, the way a player would.
 */
class WalkableTownTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private var town: NeighbourhoodScene? = null

    /**
     * The page's own style sheet, put on the page for a test that checks the layout as it is published, and the rule
     * that pins the cards over the town to a phone's size, as the town itself is pinned.
     */
    private var pageStyle: HTMLElement? = null
    private var phoneStyle: HTMLElement? = null

    @AfterTest
    fun removeScreen() {
        town?.close()
        root.remove()
        pageStyle?.remove()
        phoneStyle?.remove()
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
     * With [weather], each day's weather is the test's own instead of the seed's. [pass] is how long the time an
     * action takes passes on screen.
     */
    private fun startInTown(
        settle: Double = 0.0,
        night: Double = DayLight.MILLIS,
        phone: Boolean = true,
        life: () -> Double = { 0.0 },
        weather: ((Int) -> Weather)? = null,
        pass: Double = DayLight.PASS_MILLIS,
    ): NeighbourhoodScene {
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = {
            val opened = assertNotNull(openWalkableTown(lessMotion = false), "The test browser has no WebGL 2")
            (opened as NeighbourhoodScene).also {
                it.nightMillis = night
                it.passMillis = pass
                it.lifeClock = life
                town = it
            }
        })
        game.talkSettleMillis = settle
        if (weather != null) game.weatherOf = weather
        game.show()
        startMonth()
        val started = assertNotNull(town)
        if (phone) pinToPhone(started)
        return started
    }

    /** Makes [town] 375 by 812 points, a phone's shape, whatever the test browser's window. */
    private fun pinToPhone(town: NeighbourhoodScene) {
        val element = town.element
        element.style.setProperty("inset", "auto")
        element.style.left = "0px"
        element.style.top = "0px"
        element.style.width = "375px"
        element.style.height = "812px"
    }

    /**
     * Opens seed 1011's page on [store], as a reload does, in a real town pinned to a phone once the month starts or
     * goes on. Its life clock stands still and talks answer at once.
     */
    private fun openKept(store: FakeStore): PhoneGame {
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = {
            val opened = assertNotNull(openWalkableTown(lessMotion = false), "The test browser has no WebGL 2")
            (opened as NeighbourhoodScene).also {
                it.lifeClock = { 0.0 }
                pinToPhone(it)
                town = it
            }
        })
        game.slot = SaveSlot(Memory { store })
        game.talkSettleMillis = 0.0
        game.show()
        return game
    }

    private fun hud(): String = root.querySelector(".hud")?.textContent.orEmpty()

    /** The keeper's button that buys what [text] names. */
    private fun buyButton(text: String): HTMLButtonElement = root.querySelectorAll(".talk .buy").asList()
        .map { it as HTMLButtonElement }.firstOrNull { text in it.textContent.orEmpty() } ?: error("No $text: $screen")

    /** Seed 1011's balance on its first day, in its cheapest room. */
    private val firstBalance: Int
        get() = FirstMinute(1011).let { it.startMonth(it.cheapestRoomIndex, it.lowestTotal).balance }

    private fun startMonth() {
        passIntro(root)
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

    /** Taps the floating name of [place]: its talk opens at once at the place, and on arrival from afar. */
    private fun tapName(place: Place) {
        val name = root.querySelectorAll(".place-name").asList().map { it as HTMLButtonElement }
            .first { it.textContent == place.label }
        name.click()
    }

    /**
     * Opens [place]'s talk with the character at its door, where what the talk offers can be done, as a walk or a ride
     * there leaves it: the ways across the map are too long for a test to walk each time. A talk already open is closed
     * first, so the place's own opens again with the character there. What happens from afar is [ArriveFirstTest]'s.
     */
    private fun talkAtDoor(place: Place) {
        if (root.querySelector(".talk") != null) tap("그만")
        assertNotNull(town).standAt(Neighbourhood.venue(place).door)
        tapName(place)
    }

    /** Opens the talk with the bike shop's keeper with the character at its door, as [talkAtDoor] does. */
    private fun talkAtShop(town: NeighbourhoodScene) {
        town.standAt(Neighbourhood.shop.door)
        town.shopName.click()
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
    private fun tapScreen(town: NeighbourhoodScene, point: FramePoint, highest: Double = 0.2) {
        val clear = point.left in 0.05..0.95 && point.top in highest..0.65
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
    fun `the town fills the screen, with the places' names, the shop's, and the day's figures over it, and no day buttons`() {
        startInTown(phone = false)
        val town = root.querySelector(".walk-town") as HTMLElement
        val frame = town.getBoundingClientRect()
        assertEquals(window.innerWidth.toDouble(), frame.width)
        assertEquals(window.innerHeight.toDouble(), frame.height)
        val names = town.querySelectorAll(".place-name").asList().map { it.textContent }
        assertEquals(Place.entries.map { it.label } + "자전거 가게", names)
        assertEquals(listOf("내 방", "물가 카페", "시내 도서관", "벽돌길 코워킹", "항구 회사"), names.take(5))
        assertEquals("three.js r169", town.querySelector("canvas")?.getAttribute("data-engine"))
        assertTrue("오늘9월 2일" in screen && "잔고\$1,935" in screen && "10월 총비용\$1,165" in screen, screen)
        assertTrue("아침 8시 · 체력 5/5 · 영어 1/10 · 코딩 3/10" in screen, screen)
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
        assertTrue("카페에서 일했어요. +\$70" in screen)
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
        assertTrue("영어 공부를 했어요. 레벨업까지 3번" in screen)
    }

    @Test
    fun `a tap on the ground walks the character toward it, with a marker where it goes`(): Promise<Unit> {
        val town = startInTown()
        val start = town.characterSpot
        // Beside 내 방, in front of the first view.
        val target = Spot(start.x + 3.0, start.z + 1.0)
        return play(
            Step("tap the ground", { town.isStill }) {
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
            Step("tap the ground twice", { town.isStill }) {
                val point = onScreen(town, Point(town.characterSpot.x + 3.0, 0.0, town.characterSpot.z + 1.0))
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
    fun `a tap on a building from afar walks there, and a tap on a person or a building at the place opens its talk`():
        Promise<Unit> {
        val town = startInTown()
        val home = Neighbourhood.venue(Place.HOME)
        val office = Neighbourhood.venue(Place.OFFICE)
        // The middle of 내 방's roof, seen from above.
        val homeRoof = Point(home.x + home.width / 2.0, home.height + 1.0, home.z + home.depth / 2.0)
        // 회사's roof, the tallest of the five, over the West End from the first view. It sits high on the screen, but
        // below the top card, which ends near 0.12 of the way down.
        val officeRoof = Point(office.x + office.width / 2.0, office.box.height - 0.05, office.z + office.depth / 2.0)
        return play(
            Step("tap 회사's tower far across the town", { town.isStill }) {
                tapScreen(town, onScreen(town, officeRoof), highest = 0.15)
                assertNull(root.querySelector(".talk"), "a talk at 회사 from afar: $screen")
                assertEquals("항구 회사로 가는 중", root.querySelector(".hud-going")?.textContent)
                assertEquals(office.talkSpot, town.markerSpot)
            },
            // Back at the door, where the neighbour is near.
            Step("tap the neighbour", { true }) {
                town.standAt(home.door)
                assertNull(root.querySelector(".hud-going"), "still on the way to 회사: $screen")
                tapScreen(town, onScreen(town, Point(home.person.x, 1.3, home.person.z)))
                val talk = root.querySelector(".talk")?.textContent.orEmpty()
                assertTrue("이웃 할머니" in talk, "no talk with the neighbour: $screen")
                tap("그만")
                assertNull(root.querySelector(".talk"), "그만 left the talk open")
            },
            Step("tap 내 방's roof once the character has reached the neighbour", { town.isStill }) {
                tapScreen(town, onScreen(town, homeRoof))
                val talk = root.querySelector(".talk")?.textContent.orEmpty()
                assertTrue("이웃 할머니" in talk && "자기" in talk, "no talk at 내 방: $screen")
            },
        )
    }

    @Test
    fun `at a place the camera comes round to the street side of its door, unless the player turned it on the way`():
        Promise<Unit> {
        val town = startInTown()
        val home = Neighbourhood.venue(Place.HOME)
        return play(
            Step("talk to the neighbour, and let the character walk up", { town.isStill }) { tapName(Place.HOME) },
            Step("arrived", { town.isStill }) {
                // 내 방's door faces south: the camera comes round to the south, looking north at its front.
                assertEquals(0.0, town.cameraTurn, 1e-9)
                assertEquals(FollowCamera.ARRIVAL_TILT, town.cameraTilt, 1e-9)
                assertEquals(FollowCamera.START_DISTANCE, town.cameraDistance, 1e-9)
                tap("그만")
                // Back to the door, a few steps east.
                tapScreen(town, onScreen(town, Point(home.door.x, 0.0, home.door.z)))
            },
            Step("at the door, walk up to the neighbour again, turning the camera on the way", { town.isStill }) {
                tapName(Place.HOME)
                drag(town)
            },
            Step("arrived again", { town.isStill }) {
                // The player's own turn stays.
                assertEquals(-100 * FollowCamera.TURN_PER_PIXEL, town.cameraTurn, 1e-9)
                assertEquals(FollowCamera.ARRIVAL_TILT, town.cameraTilt, 1e-9)
            },
        )
    }

    @Test
    fun `a double tap on a place only walks there, and the new talk ignores taps for a moment`(): Promise<Unit> {
        val town = startInTown(settle = 400.0)
        val home = Neighbourhood.venue(Place.HOME)
        return play(
            Step("tap 내 방's roof twice, then its answer and 그만 at once", { town.isStill }) {
                val roof = Point(home.x + home.width / 2.0, home.height + 1.0, home.z + home.depth / 2.0)
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
        talkAtDoor(Place.LIBRARY)
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
        talkAtDoor(Place.LIBRARY)
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
        talkAtDoor(Place.LIBRARY)
        assertNotEquals(whole, root.querySelector(".talk-line .visually-hidden")?.textContent)
    }

    @Test
    fun `an answer takes the action in core, the day goes on, and its log shows over the town`() {
        startInTown()
        talkAtDoor(Place.LIBRARY)
        tap("영어 공부")
        val month = FirstMinute(1011).let { it.startMonth(it.cheapestRoomIndex, it.lowestTotal) }
        month.take(Action.STUDY_ENGLISH)
        assertTrue("오늘9월 2일" in screen, screen)
        assertTrue("잔고${money(month.balance)}" in screen, screen)
        assertTrue("방금 있었던 일" in screen && "영어 공부를 했어요. 레벨업까지 3번" in screen, screen)
        assertTrue("오후 2시 · 체력 3/5 · 영어 1/10 · 코딩 3/10" in screen, screen)
        assertNull(root.querySelector(".talk"), "the talk closes when the action is taken")
    }

    @Test
    fun `after an action the character stays where it is, and the light moves on toward evening little by little`():
        Promise<Unit> {
        val town = startInTown(pass = 800.0)
        var spot = town.characterSpot
        val morning = town.lightShown
        return play(
            Step("walk up to the café", { town.isStill }) {
                assertEquals(0.0, town.timeShown)
                assertEquals(DayLight.MORNING, morning)
                // Downtown is a long walk from 내 방: the test starts by the café's door.
                town.standAt(Neighbourhood.venue(Place.CAFE).door)
                talkAtDoor(Place.CAFE)
            },
            Step("work a shift once there", { town.isStill }) {
                spot = town.characterSpot
                assertTrue(distanceBetween(spot, Neighbourhood.venue(Place.CAFE).talkSpot) < 1.0, "not at the café")
                tap("알바하기")
                assertTrue(town.isPassingTime, "no time passes")
                assertFalse(town.isChangingDay, "the day changed")
                assertTrue("오후 5시" in hud(), hud())
            },
            Step("halfway through the time it took", after(350)) {
                assertTrue(town.timeShown > 0.0 && town.timeShown < 0.6, "${town.timeShown}")
                assertEquals(spot, town.characterSpot, "the character moved")
            },
            Step("once it has passed", { !town.isPassingTime }) {
                assertEquals(0.6, town.timeShown, 1e-9)
                assertEquals(DayLight.during(0.6), town.lightShown)
                assertTrue(town.lightShown.glow > morning.glow, "no lamps toward evening")
                assertTrue(town.lightShown.sun + town.lightShown.ambient < morning.sun + morning.ambient, "no darker")
                // Still at the café: nothing took the character home.
                assertEquals(spot, town.characterSpot, "the character moved")
                assertNotEquals(Neighbourhood.venue(Place.HOME).door, town.characterSpot)
                // The town takes a talk at once, and offers only what the stamina left allows, with the reason.
                talkAtDoor(Place.CAFE)
                assertTrue(spendButton().disabled, "a second shift with two stamina")
                assertTrue("체력이 모자라요." in spendButton().textContent.orEmpty(), spendButton().textContent)
                tap("그만")
                talkAtDoor(Place.LIBRARY)
                assertFalse(spendButton().disabled, "no English with two stamina")
            },
        )
    }

    @Test
    fun `with the stamina spent it is night, a line says to go home, and sleep at 내 방 starts the next morning there`():
        Promise<Unit> {
        val town = startInTown(pass = 300.0, night = 600.0)
        return play(
            Step("work and study", { town.isStill }) {
                talkAtDoor(Place.CAFE)
                tap("알바하기")
                // The library is a long walk from 내 방: the test puts the character by its door.
                town.standAt(Neighbourhood.venue(Place.LIBRARY).door)
                talkAtDoor(Place.LIBRARY)
                tap("영어 공부")
                assertTrue("밤 11시 · 체력 0/5" in hud(), hud())
                assertEquals("밤이에요. 체력을 다 썼어요. 내 방에 가서 자요.", root.querySelector(".hud-bedtime")?.textContent)
                talkAtDoor(Place.CAFE)
                assertTrue("체력이 없어요. 내 방에 가서 자요." in spendButton().textContent.orEmpty(), screen)
                tap("그만")
            },
            Step("the night has come over the town, the lamps lit", { town.isStill }) {
                assertEquals(1.0, town.timeShown, 1e-9)
                assertEquals(DayLight.NIGHT, town.lightShown)
                assertFalse(town.isChangingDay, "the day changed on its own")
                assertNotEquals(Neighbourhood.venue(Place.HOME).door, town.characterSpot, "taken home")
                // The neighbour offers sleep, which a walk home reaches: from 내 방's door here, as the way is long.
                town.standAt(Neighbourhood.venue(Place.HOME).door)
                talkAtDoor(Place.HOME)
                assertEquals("자기", spendButton().querySelector(".action-name")?.textContent)
                assertFalse(spendButton().disabled)
            },
            Step("walk home", { town.isStill }) {
                assertTrue(distanceBetween(town.characterSpot, Neighbourhood.venue(Place.HOME).talkSpot) < 1.0)
                spendButton().click()
                assertTrue(town.isChangingDay, "no night's sleep")
                assertTrue("오늘9월 3일" in screen, screen)
                // Through the night the top card keeps the hour and the stamina the sleep began with.
                assertTrue("밤 11시 · 체력 0/5" in hud(), hud())
            },
            Step("the next morning", { !town.isChangingDay }) {
                assertEquals(Neighbourhood.venue(Place.HOME).door, town.characterSpot, "not at 내 방's door")
                assertEquals(0.0, town.timeShown)
                assertEquals(DayLight.MORNING, town.lightShown)
                assertTrue("아침 8시 · 체력 5/5" in hud(), hud())
                assertNull(root.querySelector(".hud-bedtime"))
                assertTrue("내 방에서 푹 잤어요. 체력 5/5" in screen, screen)
            },
        )
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
                tapScreen(town, onScreen(town, Point(town.characterSpot.x + 3.0, 0.0, town.characterSpot.z + 1.0)))
                assertTrue(town.isMarkerShown, "no marker")
                talkAtDoor(Place.HOME)
                tap("자기")
                assertTrue(town.isSitting, "not on the bench")
                assertFalse(town.isMarkerShown, "the marker stayed")
                fireside = Triple(town.cameraTurn, town.cameraTilt, town.cameraDistance)
            },
            Step("in the night", after(300)) {
                // In the night: no talk, no walk, no turn and no zoom.
                tapName(Place.CAFE)
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
                talkAtDoor(Place.CAFE)
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
                        talkAtDoor(place)
                        assertTrue(spendButton().disabled, "$place")
                        assertTrue("오늘은 면접부터 봐요." in spendButton().textContent.orEmpty(), "$place")
                    }
                    talkAtDoor(Place.OFFICE)
                    assertEquals("면접 보기", spendButton().querySelector(".action-name")?.textContent)
                    assertFalse(spendButton().disabled)
                    assertEquals(Place.OFFICE.label, root.querySelector(".place-name.here")?.textContent)
                    assertTrue("오늘은 면접 날이에요. 항구 회사로 가세요." in screen, screen)
                    spendButton().click()
                    assertTrue("1차 영어 면접 통과!" in screen || "1차 영어 면접에서 떨어졌어요." in screen, screen)
                    resolve(Unit)
                    return
                }
                talkAtDoor(Place.OFFICE)
                if (spendButton().disabled) talkAtDoor(Place.HOME)
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
        // The same choices each way: a cycle of the five actions, sleeping when one cannot be done, and the
        // interview on its day.
        val cycle = listOf(Action.APPLY, Action.STUDY_ENGLISH, Action.PRACTISE_CODING, Action.WORK, Action.REST)
        PhoneGame(root, 1011, playAgain = {}).show()
        startMonth()
        var step = 0
        while ("다시 하기" !in screen) {
            if (buttons().any { it.textContent == "면접 보기" }) {
                tap("면접 보기")
            } else {
                val choice = button(actionName(cycle[step % cycle.size]))
                if (choice.disabled) tap("자기") else choice.click()
            }
            step++
        }
        val withButtons = endingText()

        root.innerHTML = ""
        // Each night and each passing of time is shortened to a tenth of a second or so, so the month plays in a
        // few seconds; every night still goes through the whole change of day, frame by frame, to the town's morning.
        val town = startInTown(night = 120.0, pass = 60.0)
        val door = Neighbourhood.venue(Place.HOME).door
        var view = Triple(0.0, 0.0, 0.0)
        step = 0
        var nights = 0
        return Promise { resolve, reject ->
            fun next() {
                if ("다시 하기" in screen) {
                    assertEquals(withButtons, endingText())
                    assertNull(root.querySelector(".walk-town"), "the town closes at the ending")
                    assertTrue(nights > 10, "only $nights nights")
                    resolve(Unit)
                    return
                }
                val action = cycle[step % cycle.size]
                // Each answer at its place's door, where it can be taken, so nothing goes by how far the character has
                // walked by the clock.
                when {
                    "오늘은 면접 날이에요" in screen -> talkAtDoor(Place.OFFICE)
                    action == Action.REST -> talkAtDoor(Place.HOME)
                    else -> {
                        talkAtDoor(placeFor(action))
                        if (spendButton().disabled) talkAtDoor(Place.HOME)
                    }
                }
                val sleep = "자기" in spendButton().textContent.orEmpty()
                if (sleep) assertFalse(spendButton().disabled, "step $step: 자기 not offered at the door")
                view = Triple(town.cameraTurn, town.cameraTilt, town.cameraDistance)
                spendButton().click()
                step++
                if ("다시 하기" !in screen && sleep) {
                    nights++
                    // In the night no talk opens.
                    tapName(Place.CAFE)
                    assertNull(root.querySelector(".talk"), "a talk opened in the night")
                } else if ("다시 하기" !in screen) {
                    // An action takes nobody home and ends no day: time passes where the character is.
                    assertFalse(town.isChangingDay, "step $step")
                    assertFalse(town.isSitting, "step $step")
                }
                whenReady({ "다시 하기" in screen || (!town.isChangingDay && !town.isPassingTime) }, reject) {
                    if ("다시 하기" !in screen && sleep) {
                        // Each morning: at 내 방's door, standing, no marker, the player's own camera, the morning.
                        assertEquals(door, town.characterSpot, "step $step")
                        assertFalse(town.isSitting, "step $step")
                        assertFalse(town.isMarkerShown, "step $step")
                        assertEquals(view.first, town.cameraTurn, 1e-9, "step $step")
                        assertEquals(view.second, town.cameraTilt, 1e-9, "step $step")
                        assertEquals(view.third, town.cameraDistance, 1e-9, "step $step")
                        assertEquals(0.0, town.timeShown, "step $step")
                    }
                    next()
                }
            }
            try {
                next()
            } catch (error: Throwable) {
                reject(error)
            }
        }
    }

    @Test
    fun `a tap on a passerby opens small talk with only 그만, and the day stays the same`(): Promise<Unit> {
        val town = startInTown()
        return play(
            Step("look along her street", { town.isStill }) { lookAlongMinasStreet(town) },
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
                assertTrue("오늘9월 2일" in screen && "잔고\$1,935" in screen, screen)
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
    fun `sleep starts only at 내 방: a tap on its name from afar walks home, and the talk there offers it`():
        Promise<Unit> {
        val town = startInTown()
        val home = Neighbourhood.venue(Place.HOME)
        return play(
            Step("from the library, tap 내 방's name", { town.isStill }) {
                town.standAt(Neighbourhood.venue(Place.LIBRARY).door)
                tapName(Place.HOME)
                assertFalse(town.isAt(Place.HOME))
                assertNull(root.querySelector(".talk"), "the neighbour talks from afar")
                assertEquals("내 방으로 가는 중", root.querySelector(".hud-going")?.textContent)
                assertFalse(town.isChangingDay, "the night fell far from 내 방")
                assertTrue("오늘9월 2일" in screen, screen)
                // Most of the way home at once, to keep the test short; the last steps are walked.
                town.standAt(Spot(home.door.x + 3.0, home.door.z))
                tapName(Place.HOME)
            },
            Step("arrived at the neighbour", { town.isStill }) {
                assertTrue(town.isAt(Place.HOME))
                assertNull(root.querySelector(".hud-going"), "still on the way: $screen")
                assertNotNull(root.querySelector(".talk"), "no talk on arrival: $screen")
                assertFalse(spendButton().disabled, "no sleep at 내 방")
                spendButton().click()
                assertTrue(town.isChangingDay, "no night's sleep at 내 방")
            },
        )
    }

    @Test
    fun `the passersby go home as the day's time runs out, and are out again in the morning`(): Promise<Unit> {
        val town = startInTown(night = 1_500.0, pass = 300.0)
        return play(
            Step("spend the day's stamina", { town.isStill }) {
                assertTrue(town.arePassersbyOut, "nobody out by day")
                talkAtDoor(Place.LIBRARY)
                tap("영어 공부")
                talkAtDoor(Place.CAFE)
                tap("알바하기")
                assertFalse(town.isChangingDay)
            },
            Step("at night", { !town.isPassingTime && !town.arePassersbyOut }) {
                assertEquals(1.0, town.timeShown, 1e-9)
                talkAtDoor(Place.HOME)
                tap("자기")
                assertTrue(town.isChangingDay)
            },
            Step("in the night's sleep", after(300)) {
                assertFalse(town.arePassersbyOut, "out in the night")
            },
            Step("in the morning", { !town.isChangingDay && town.arePassersbyOut }) {
                assertEquals(0.0, town.timeShown)
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

    @Test
    fun `while an action's time passes, the town draws no more than thirty frames a second`(): Promise<Unit> {
        val town = startInTown(pass = 1_500.0)
        var from = 0
        return play(
            Step("let the time pass", { town.isStill }) {
                from = town.framesDrawn
                town.passTime(0.6)
                assertTrue(town.isPassingTime)
            },
            Step("one second later", after(1_000)) {
                val drawn = town.framesDrawn - from
                assertTrue(town.isPassingTime, "the time passed too soon")
                assertTrue(town.timeShown > 0.0, "the light did not move")
                // The first frame answers the action at once; the rest come on the beat of the town's own life.
                assertTrue(drawn in 2..FramePace.AMBIENT_FPS.toInt() + 2, "$drawn frames in a second")
            },
        )
    }

    /** Turns the camera to look east along the street 미나 walks, from high in the west of the character. */
    private fun lookAlongMinasStreet(town: NeighbourhoodScene) =
        town.aimCamera(-PI / 2, 1.0, FollowCamera.START_DISTANCE)

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
            Step("look along her street", { town.isStill }) { lookAlongMinasStreet(town) },
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
            Step("look along her street", { town.isStill }) { lookAlongMinasStreet(town) },
            Step("tap 미나, then the street", { town.isStill }) {
                tapScreen(town, minaOn(town))
                assertEquals("미나", talkWith())
                // Well away from her, so it is not a double tap.
                val door = Neighbourhood.venue(Place.HOME).door
                tapScreen(town, onScreen(town, Point(door.x + 4.0, 0.0, door.z + 2.0)))
                assertNull(talkWith(), "the small talk stayed open")
                assertNotNull(town.markerSpot, "no walk")
            },
        )
    }

    @Test
    fun `a tap on a passerby replaces a place's talk, and nothing left can spend a day`(): Promise<Unit> {
        val town = startInTown()
        return play(
            Step("talk to the neighbour", { town.isStill }) {
                talkAtDoor(Place.HOME)
                assertEquals("이웃 할머니", talkWith())
            },
            // On arrival the camera comes round to 내 방's front; look along 미나's street again.
            Step("look along 미나's street once there", { town.isStill }) { lookAlongMinasStreet(town) },
            Step("tap 미나 once the character stands by the neighbour", { town.isStill }) {
                assertEquals("이웃 할머니", talkWith())
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
            Step("look along her street", { town.isStill }) { lookAlongMinasStreet(town) },
            Step("go to sleep, then tap 미나 as the evening begins", { town.isStill }) {
                val mina = minaOn(town)
                talkAtDoor(Place.HOME)
                tap("자기")
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
    private fun streakAt(index: Int, millis: Double): Double = Rain.dropAt(index, millis, 0.0, 0.0).y - Rain.STREAK / 2

    /** True when the first streak the town drew is where the rain's rule puts it at [millis]. */
    private fun rainDrawnAt(town: NeighbourhoodScene, millis: Double): Boolean {
        val (index, height) = town.drawnStreak ?: return false
        return abs(height - streakAt(index, millis)) < 1e-4
    }

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
                    assertEquals(" 날씨 ${weatherWord(weatherOn(1011, day))}", word, "day $day")
                    talkAtDoor(Place.HOME)
                    spendButton().click()
                    day++
                    whenReady({ !town.isChangingDay }, reject) { nextDay() }
                    return
                }
                assertTrue(town.isRaining, "no rain on day $day")
                assertTrue(town.isRainOnePassLast, "the rain is drawn twice, or before the passersby")
                assertEquals(Weather.RAINY, town.weatherShown)
                assertEquals(" 날씨 비", word)
                assertTrue("오늘9월 ${day}일" in screen, screen)
                // The rain falls with the town's own time, every streak where the rain's rule puts it.
                assertNotEquals(streakAt(0, 1_000.0), streakAt(0, 1_040.0))
                clock = 1_000.0
                whenReady({ rainDrawnAt(town, 1_000.0) }, reject) {
                    clock = 1_040.0
                    whenReady({ rainDrawnAt(town, 1_040.0) }, reject) {
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
                assertEquals(" 날씨 맑음", root.querySelector(".hud .weather")?.textContent)
                talkAtDoor(Place.HOME)
                spendButton().click()
                assertTrue(town.isChangingDay)
                // The top card already shows the new day, but the evening is still dry.
                assertEquals(" 날씨 비", root.querySelector(".hud .weather")?.textContent)
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
                        talkAtDoor(Place.OFFICE)
                    } else {
                        talkAtDoor(placeFor(cycle[(seen.size - 1) % cycle.size]))
                        if (spendButton().disabled) talkAtDoor(Place.HOME)
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

    /** The building names on screen (not behind the camera), each with whether it hides under the cards. */
    private fun namesOnScreen(): List<Pair<HTMLElement, Boolean>> =
        root.querySelectorAll(".place-name").asList().map { it as HTMLElement }
            .filter { !it.classList.contains("behind") }
            .map { it to it.classList.contains("under") }

    @Test
    fun `a building's name hides where the top card or the day's log would cover it, and nowhere else`():
        Promise<Unit> {
        val town = startInTown()
        return play(
            Step("the names under the game's own cards", { town.isStill }) {
                val hud = assertNotNull(root.querySelector(".over-town .hud") as? HTMLElement)
                val cover = hud.getBoundingClientRect().bottom
                val names = namesOnScreen()
                assertTrue(names.isNotEmpty())
                for ((name, under) in names) {
                    val reaches = name.getBoundingClientRect().top < cover
                    assertEquals(reaches, under, "${name.textContent} reaches ${name.getBoundingClientRect().top}")
                }
                town.coveredTop = { 0.0 }
            },
            // With no card, every name shows, even one that reaches past the top of the screen.
            Step("nothing covered", { town.isStill && namesOnScreen().none { it.second } }) {
                town.coveredTop = { 812.0 }
            },
            Step("all covered", { town.isStill && namesOnScreen().all { it.second } }) {
                // Hidden names stay in the keyboard's reach and a screen reader's.
                for ((name, _) in namesOnScreen()) {
                    assertEquals(0, (name as HTMLButtonElement).tabIndex, "${name.textContent}")
                    assertNull(name.getAttribute("aria-hidden"), "${name.textContent}")
                }
            },
        )
    }

    @Test
    fun `when the phone takes WebGL back, the passing train falls silent, and it rumbles again once WebGL returns`():
        Promise<Unit> {
        var clock = 0.0
        val town = startInTown(life = { clock })
        val heard = mutableListOf<Double>()
        town.onSounds = { train, _, _ -> heard += train }
        val lose: dynamic = canvasOf(town).getContext("webgl2")?.asDynamic()?.getExtension("WEBGL_lose_context")
        assertNotNull(lose, "The test browser cannot lose WebGL on purpose")
        return play(
            Step("the train pulls out", { town.isStill }) {
                // Four seconds after it leaves the station it is under way.
                clock = SkyTrainRun.DWELL + 4_000.0
            },
            Step("it rumbles", { heard.lastOrNull()?.let { it > 0.0 } == true }) {
                lose.loseContext()
            },
            Step("WebGL is gone: silence", { heard.last() == 0.0 }) {
                clock = SkyTrainRun.DWELL + 4_500.0
                lose.restoreContext()
            },
            Step("WebGL is back: it rumbles again", { heard.last() > 0.0 }) {},
        )
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
        // The town's own clock runs, so the rain really falls through the counted second.
        val town = startInTown(life = { window.performance.now() }, weather = { Weather.RAINY })
        var from = 0
        return play(
            Step("let the rain fall", { town.isStill }) {
                assertTrue(town.isRaining)
                from = town.framesDrawn
            },
            Step("one second later", after(1_000)) {
                val drawn = town.framesDrawn - from
                assertTrue(drawn in 1..FramePace.AMBIENT_FPS.toInt() + 1, "$drawn frames in a second")
            },
        )
    }

    @Test
    fun `a small map sits in the corner under the top card, and its arrow moves as the character walks`(): Promise<Unit> {
        val town = startInTown()
        val start = town.characterSpot
        // Beside 내 방, east and a little south, in front of the first view.
        val target = Spot(start.x + 3.0, start.z + 1.0)
        val map = town.minimapElement
        var before = 0.0 to 0.0
        return play(
            Step("the town drew its map", { town.isStill }) {
                assertSame(town.element, map.parentElement)
                assertEquals("img", map.getAttribute("role"))
                assertEquals("지도: 내 방 근처", map.getAttribute("aria-label"))
                // In the town's top right corner, under the top card, never over it, and small.
                val frame = map.getBoundingClientRect()
                val hud = assertNotNull(root.querySelector(".hud")).getBoundingClientRect()
                assertTrue(frame.top >= hud.bottom, "the map's top ${frame.top} is over the card, which ends at ${hud.bottom}")
                assertEquals(375.0 - 8.0, frame.right, 1.0)
                assertTrue(frame.width in 80.0..102.0, "${frame.width} wide")
                // A little taller than wide, as the small map always was.
                assertTrue(frame.height > frame.width, "${frame.width} by ${frame.height}")
                // It shows the ground round the character: the arrow in its middle.
                before = town.minimapDot
                assertEquals(map.clientWidth / 2.0, before.first, 1e-6)
                assertEquals(map.clientHeight / 2.0, before.second, 1e-6)
                tapScreen(town, onScreen(town, Point(target.x, 0.0, target.z)))
            },
            Step("walked there", { town.isStill }) {
                // The arrow is still in the middle: the map moved with the character, east and a little south on the
                // game map, east and south in the real city too, so where it started now lies up and to the left.
                val after = town.minimapDot
                assertEquals(before, after)
                val window = MinimapProjection(map.clientWidth.toDouble(), map.clientHeight.toDouble(), Minimap.WINDOW_ACROSS)
                window.centreOn(town.characterSpot.x, town.characterSpot.z)
                assertTrue(window.left(start.x, start.z) < after.first && window.top(start.x, start.z) < after.second)
            },
        )
    }

    @Test
    fun `a tap on the small map shows it larger, and a tap, Escape or a talk brings it back`(): Promise<Unit> {
        val town = startInTown()
        val map = town.minimapElement
        var small = 0.0
        fun escape() = document.dispatchEvent(KeyboardEvent("keydown", KeyboardEventInit(key = "Escape")))
        return play(
            Step("tap the map", { town.isStill }) {
                small = map.getBoundingClientRect().width
                map.click()
                assertTrue(map.classList.contains("large"))
                // About four fifths of the 375-point town.
                assertEquals(300.0, map.getBoundingClientRect().width, 2.0)
                assertNull(town.markerSpot, "a tap on the map walked the character")
            },
            Step("drawn larger", { town.isStill }) {
                val canvas = map.querySelector("canvas") as HTMLCanvasElement
                assertTrue(canvas.width >= map.clientWidth, "the larger map is drawn at ${canvas.width} pixels")
                map.click()
                assertFalse(map.classList.contains("large"))
                assertEquals(small, map.getBoundingClientRect().width, 0.5)
                map.click()
                escape()
                assertFalse(map.classList.contains("large"), "Escape left it large")
                // A name taking the keyboard's focus brings it back, so it never lies over the name in focus.
                map.click()
                (assertNotNull(root.querySelector(".place-name")) as HTMLElement).focus()
                assertFalse(map.classList.contains("large"), "a name took the focus under the larger map")
                map.click()
                talkAtDoor(Place.HOME)
                assertNotNull(root.querySelector(".talk"))
                assertFalse(map.classList.contains("large"), "a talk opened over the larger map")
                // While the talk covers the town, it does not grow under it.
                map.click()
                assertFalse(map.classList.contains("large"))
            },
        )
    }

    /** Lays the page out with its own style sheet, as it is published (web/karma.config.d/style.js hands it over). */
    private fun usePageStyle() {
        val style = document.createElement("style") as HTMLElement
        style.textContent = js("window.__karma__.config.pageStyle") as String
        document.head!!.appendChild(style)
        pageStyle = style
    }

    /** Pins the town and the cards over it to a phone [width] by [height] points, in the test browser's window. */
    private fun pinPhone(town: NeighbourhoodScene, width: Int, height: Int) {
        town.element.style.width = "${width}px"
        town.element.style.height = "${height}px"
        val style = phoneStyle ?: (document.createElement("style") as HTMLElement).also {
            document.head!!.appendChild(it)
            phoneStyle = it
        }
        style.textContent = ".over-town { inset: auto; left: 0; top: 0; width: ${width}px; height: ${height}px; }"
    }

    private fun rectOf(selector: String) =
        assertNotNull(root.querySelector(selector), "no $selector: $screen").getBoundingClientRect()

    /** A finger's press, lift and click at [x], [y] on whatever the page has there, as the browser would send them. */
    private fun pressAt(x: Double, y: Double): HTMLElement {
        val hit = assertNotNull(document.elementFromPoint(x, y), "nothing at $x, $y") as HTMLElement
        for (type in listOf("pointerdown", "pointerup")) {
            hit.dispatchEvent(PointerEvent(type, PointerEventInit(pointerId = 1, isPrimary = true, clientX = x.toInt(),
                clientY = y.toInt(), bubbles = true)))
        }
        hit.click()
        return hit
    }

    @Test
    fun `laid out as published, the small map sits under the cards and the day's log, above a talk, and takes its taps`():
        Promise<Unit> {
        usePageStyle()
        val town = startInTown(night = 50.0)
        pinPhone(town, 375, 812)
        val map = town.minimapElement
        fun clearOfTheCards(phone: String) {
            val frame = map.getBoundingClientRect()
            val log = rectOf(".over-town .log")
            val talk = rectOf(".talk")
            assertTrue(frame.top >= log.bottom, "$phone: the map's top ${frame.top} is over the log, which ends at ${log.bottom}")
            assertTrue(frame.bottom <= talk.top, "$phone: the map's bottom ${frame.bottom} is over the talk at ${talk.top}")
        }
        return play(
            Step("the first morning", { town.isStill }) {
                val frame = map.getBoundingClientRect()
                assertTrue(frame.top >= rectOf(".hud").bottom, "the map's top ${frame.top} is over the top card")
                // A finger at the map's middle lands on the map, not the town under it: it grows, and nobody walks.
                val hit = pressAt(frame.left + frame.width / 2, frame.top + frame.height / 2)
                assertTrue(map.contains(hit), "a finger on the map lands on ${hit.tagName}.${hit.className}")
                assertTrue(map.classList.contains("large"), "the tap did not show the map larger")
                assertNull(town.markerSpot, "a tap on the map walked the character")
                val larger = map.getBoundingClientRect()
                pressAt(larger.left + larger.width / 2, larger.top + larger.height / 2)
                assertFalse(map.classList.contains("large"))
                assertNull(town.markerSpot, "a tap on the larger map walked the character")
                talkAtDoor(Place.HOME)
                spendButton().click()
            },
            Step("the morning after, with the day's log", { !town.isChangingDay && town.isStill }) {
                val frame = map.getBoundingClientRect()
                val log = rectOf(".over-town .log")
                assertTrue(frame.top >= log.bottom, "the map's top ${frame.top} is over the log, which ends at ${log.bottom}")
                talkAtDoor(Place.HOME)
            },
            Step("with a talk open under the log", { town.isStill }) {
                clearOfTheCards("375 by 812")
                pinPhone(town, 320, 568)
            },
            Step("on a 320 by 568 phone", { town.isStill && map.getBoundingClientRect().top < 300.0 }) {
                assertTrue(rectOf(".talk").bottom <= 568.0, "the talk runs off the small phone")
                clearOfTheCards("320 by 568")
                button("그만").click()
                tap(Transit.CALL_TAXI)
            },
            Step("the taxi's card on it", { town.isStill && chooserSettled() }) {
                chooserFits("the taxi's card", map)
                button("그만").click()
                town.standAt(TransitLines.busStops[0].wait)
                town.onStop(Stop(Transport.BUS, 0))
            },
            Step("the bus's card from 공원 입구 on it", { town.isStill && chooserSettled() }) {
                assertTrue("다운타운 남쪽" in rectText(".chooser"), screen)
                chooserFits("the bus's card", map)
            },
        )
    }

    private fun rectText(selector: String): String = root.querySelector(selector)?.textContent.orEmpty()

    /** True once the chooser has risen into place: its opening animation is over. */
    private fun chooserSettled(): Boolean {
        val card = root.querySelector(".chooser") ?: return false
        return (card.asDynamic().getAnimations().length as Int) == 0
    }

    /**
     * On a 320 by 568 phone, the chooser [what] has each place on one line, its name and fare side by side; its
     * bottom is on the screen; and the small [map] stays above it.
     */
    private fun chooserFits(what: String, map: HTMLElement) {
        val card = rectOf(".chooser")
        assertTrue(card.bottom <= 568.0, "$what runs off the screen: ${card.bottom}")
        val frame = map.getBoundingClientRect()
        assertTrue(frame.bottom <= card.top, "$what at ${card.top} covers the small map, which ends at ${frame.bottom}")
        val choices = root.querySelectorAll(".chooser .go").asList().map { it as HTMLElement }
        assertTrue(choices.isNotEmpty(), screen)
        for (choice in choices) {
            val name = assertNotNull(choice.querySelector(".action-name") as? HTMLElement).getBoundingClientRect()
            val fare = assertNotNull(choice.querySelector(".action-effect") as? HTMLElement).getBoundingClientRect()
            val line = window.getComputedStyle(choice).lineHeight.removeSuffix("px").toDoubleOrNull() ?: 24.0
            assertTrue(name.height < line * 1.5, "$what: ${choice.textContent} breaks over two lines (${name.height})")
            assertTrue(fare.height < line * 1.5, "$what: ${choice.textContent}'s fare breaks (${fare.height})")
            assertTrue(kotlin.math.abs(name.top - fare.top) < 2.0, "$what: ${choice.textContent} on two rows")
            assertTrue(name.right <= fare.left + 1.0, "$what: ${choice.textContent}'s name runs into its fare")
            assertTrue(choice.getBoundingClientRect().right <= 320.0, "$what: ${choice.textContent} off the side")
        }
    }

    @Test
    fun `a fault in the small map hides it, and the town goes on walking`(): Promise<Unit> {
        val town = startInTown()
        val map = town.minimapElement
        val start = town.characterSpot
        val target = Spot(start.x + 3.0, start.z + 1.0)
        return play(
            Step("break the map's drawing, then walk", { town.isStill }) {
                val canvas = map.querySelector("canvas") as HTMLCanvasElement
                canvas.getContext("2d").asDynamic().drawImage = { throw IllegalStateException("broken on purpose") }
                tapScreen(town, onScreen(town, Point(target.x, 0.0, target.z)))
            },
            Step("walked there all the same", { town.isStill }) {
                assertTrue(map.hidden, "the broken map still shows")
                assertTrue(distanceBetween(town.characterSpot, target) < 0.01, "the walk stopped at ${town.characterSpot}")
            },
        )
    }

    /** The five places' own walls, by where they are among the town's still boxes. */
    private fun placeBodies(): List<Pair<Venue, Int>> = Neighbourhood.venues.map { venue ->
        // The town's still boxes as drawn: the shared ones, then 내 방's in zone 1.
        venue to (Neighbourhood.blocks + HomeSite.of(1).blocks).indexOfFirst {
            it.x == venue.x.toDouble() && it.z == venue.z.toDouble() && it.height == venue.height && it.y == 0.0
        }
    }

    /**
     * From [spot], a turn of the camera at the first view's tilt and distance from which one of the city's buildings at
     * least [tall] stands between the camera and the character, though none of them does at the first view's turn:
     * the turn and that building's number. Worked out with the same camera and the same folding rule as the town.
     */
    private fun turnWithBuildingInTheWay(spot: Spot, tall: Double): Pair<Double, Int> {
        val boxes = Neighbourhood.foldables.map { it.box }
        fun inTheWay(yaw: Double): List<Int> {
            val focus = Point(spot.x, 1.0, spot.z)
            val camera = FollowCamera(focus)
            camera.aspect = 375.0 / 812
            camera.aim(yaw, FollowCamera.START_PITCH, FollowCamera.START_DISTANCE)
            camera.jumpTo(focus)
            val eye = camera.eye
            val head = Point(spot.x, 1.6, spot.z)
            val length = sqrt((head.x - eye.x) * (head.x - eye.x) + (head.y - eye.y) * (head.y - eye.y) +
                (head.z - eye.z) * (head.z - eye.z))
            val way = Point((head.x - eye.x) / length, (head.y - eye.y) / length, (head.z - eye.z) / length)
            return boxes.indices.filter { index ->
                val hit = distanceToBox(eye, way, boxes[index])
                hit != null && hit < length && boxes[index].height >= tall
            }
        }
        val cutaway = Cutaway(boxes)
        val start = FollowCamera(Point(spot.x, 1.0, spot.z)).also { it.aspect = 375.0 / 812 }.eye
        cutaway.aim(start.x, start.y, start.z, spot.x, 1.6, spot.z)
        for (step in 0 until 48) {
            val yaw = FollowCamera.START_YAW + step * 2 * PI / 48
            val index = inTheWay(yaw).firstOrNull { !cutaway.isFolding(it) } ?: continue
            return yaw to index
        }
        error("No turn from $spot puts a building $tall tall in the camera's way")
    }

    @Test
    fun `a tall city building between the camera and the character folds down, and stands again once clear`():
        Promise<Unit> {
        val town = startInTown()
        // Downtown, at 도서관's door, among the towers.
        val door = Neighbourhood.venue(Place.LIBRARY).door
        val (yaw, index) = turnWithBuildingInTheWay(door, tall = 8.0)
        val building = Neighbourhood.foldables[index]
        val full = Neighbourhood.blocks[building.body].height
        val places = placeBodies()
        fun placesStand() {
            for ((venue, body) in places) assertEquals(venue.height, town.drawnHeight(body), 1e-4, "${venue.place}")
        }
        return play(
            Step("stand at 도서관's door", { town.isStill }) {
                town.standAt(door)
                town.aimCamera(FollowCamera.START_YAW, FollowCamera.START_PITCH, FollowCamera.START_DISTANCE)
            },
            Step("at the first view's turn, the building stands", { town.isStill }) {
                assertFalse(town.isFolded(index))
                assertEquals(full, town.drawnHeight(building.body), 1e-4)
                placesStand()
                town.aimCamera(yaw, FollowCamera.START_PITCH, FollowCamera.START_DISTANCE)
            },
            Step("turned so it stands in the way, it folds", { town.isStill }) {
                assertTrue(town.isFolded(index), "${building.box} did not fold")
                assertEquals(Cutaway.PLINTH, town.drawnHeight(building.body), 1e-4)
                // The camera keeps the whole distance the player asked for.
                assertEquals(FollowCamera.START_DISTANCE, town.cameraReach, 1e-6)
                placesStand()
                town.aimCamera(FollowCamera.START_YAW, FollowCamera.START_PITCH, FollowCamera.START_DISTANCE)
            },
            Step("turned back, it stands again", { town.isStill }) {
                assertFalse(town.isFolded(index))
                assertEquals(full, town.drawnHeight(building.body), 1e-4)
                placesStand()
            },
        )
    }

    // One test for each place's door: all five turns round every door in one test took about 29 s on a fast laptop,
    // and on a slower machine passed the 30 s Karma waits for a test to report before it gives up on the browser.
    @Test
    fun `the five places never fold, with the camera turned all the way round 내 방's door`() = placesStandRound(Place.HOME)

    @Test
    fun `the five places never fold, with the camera turned all the way round 카페's door`() = placesStandRound(Place.CAFE)

    @Test
    fun `the five places never fold, with the camera turned all the way round 도서관's door`() =
        placesStandRound(Place.LIBRARY)

    @Test
    fun `the five places never fold, with the camera turned all the way round 코워킹's door`() =
        placesStandRound(Place.COWORKING)

    @Test
    fun `the five places never fold, with the camera turned all the way round 회사's door`() =
        placesStandRound(Place.OFFICE)

    /** Stands at [at]'s door and turns the camera all the way round it, checking that none of the five places folds. */
    private fun placesStandRound(at: Place): Promise<Unit> {
        val town = startInTown()
        val places = placeBodies()
        assertTrue(places.all { it.second >= 0 }, "a place's walls are missing: $places")
        val venue = Neighbourhood.venue(at)
        val steps = mutableListOf<Step>()
        steps += Step("stand at ${venue.place}'s door", { town.isStill }) { town.standAt(venue.door) }
        for (turn in 0 until 4) {
            steps += Step("${venue.place}, turn $turn", { town.isStill }) {
                for ((place, body) in places) {
                    assertEquals(place.height, town.drawnHeight(body), 1e-4, "${place.place} at ${venue.place}")
                }
                town.aimCamera(turn * PI / 2, FollowCamera.START_PITCH, FollowCamera.START_DISTANCE)
            }
        }
        steps += Step("last turn", { town.isStill }) {
            for ((place, body) in places) assertEquals(place.height, town.drawnHeight(body), 1e-4, "${place.place}")
        }
        return play(*steps.toTypedArray())
    }

    @Test
    fun `downtown, the camera at the first view's turn keeps its whole distance, the city folding out of its way`():
        Promise<Unit> {
        val town = startInTown()
        val door = Neighbourhood.venue(Place.LIBRARY).door
        return play(
            Step("stand at 도서관's door at the first view's turn", { town.isStill }) {
                town.standAt(door)
                town.aimCamera(FollowCamera.START_YAW, FollowCamera.START_PITCH, FollowCamera.START_DISTANCE)
            },
            Step("settled", { town.isStill }) {
                // Not pulled in, not lifted, not turned to look along a street: just where the player put it.
                assertEquals(FollowCamera.START_DISTANCE, town.cameraReach, 1e-6)
                assertEquals(FollowCamera.START_YAW, town.cameraEyeTurn, 1e-6)
                assertTrue(Neighbourhood.foldables.indices.any { town.isFolded(it) }, "nothing folded downtown")
                assertEquals("지도: ${Place.LIBRARY.label} 근처", town.minimapElement.getAttribute("aria-label"))
            },
        )
    }

    @Test
    fun `the bike shop's keeper sells a bike, and buying it spends no day`(): Promise<Unit> {
        val town = startInTown()
        val shop = Neighbourhood.shop
        return play(
            Step("stand on the street in front of the shop, looking at it", { town.isStill }) {
                town.standAt(Spot(shop.talkSpot.x - 1.5, shop.talkSpot.z + 1.5))
                town.aimCamera(0.0, FollowCamera.START_PITCH, 12.0)
            },
            Step("tap its keeper, a few steps off", { town.isStill }) {
                assertFalse(town.shopName.classList.contains("behind"), "the shop's name is not over it")
                tapScreen(town, onScreen(town, Point(shop.keeper.x, 1.3, shop.keeper.z)))
                assertNull(root.querySelector(".talk"), "he talks before the character has come")
            },
            Step("walked up to him: his talk, then buy a bike", { town.isStill }) {
                val talk = root.querySelector(".talk")?.textContent.orEmpty()
                assertTrue("벤" in talk && "자전거 사기\$350" in talk && "퀵보드 사기\$550" in talk, talk)
                assertTrue("이야기" in talk && "그만" in talk, talk)
                assertTrue(town.isCovered)
                assertTrue(distanceBetween(town.characterSpot, shop.talkSpot) < 0.01, "${town.characterSpot}")
                buyButton("자전거 사기").click()
                assertTrue("9월 2일" in hud() && money(firstBalance - 350) in hud(), hud())
                assertTrue(buyButton("자전거 사기").disabled)
                assertTrue("이미 있어요." in buyButton("자전거 사기").textContent.orEmpty())
                button("자전거 타기")
                assertFalse(town.isChangingDay)
                assertNull(town.riding)
            },
        )
    }

    @Test
    fun `on a bike the character rides the streets it walks, faster, and 내리기 gets off`(): Promise<Unit> {
        val town = startInTown()
        val home = town.characterSpot
        // Beside 내 방, in front of the first view.
        val target = Spot(home.x + 3.0, home.z + 1.0)
        var walked = 0.0
        fun backHome() {
            town.standAt(home)
            town.aimCamera(FollowCamera.START_YAW, FollowCamera.START_PITCH, FollowCamera.START_DISTANCE)
        }
        return play(
            Step("buy a bike, from the shop's name", { town.isStill }) {
                talkAtShop(town)
                buyButton("자전거 사기").click()
                tap("그만")
                backHome()
            },
            Step("walk beside 내 방", { town.isStill }) {
                tapScreen(town, onScreen(town, Point(target.x, 0.0, target.z)))
                walked = assertNotNull(town.tripMillis)
            },
            Step("arrived on foot; back at the door, get on the bike", { town.isStill }) {
                assertTrue(distanceBetween(town.characterSpot, target) < 0.05, "${town.characterSpot}")
                backHome()
                tap("자전거 타기")
                assertEquals(Ride.BIKE, town.riding)
                assertTrue(town.isRideDrawn(Ride.BIKE))
                assertTrue("자전거 타는 중" in hud(), hud())
            },
            Step("ride the same way", { town.isStill }) {
                tapScreen(town, onScreen(town, Point(target.x, 0.0, target.z)))
                val ridden = assertNotNull(town.tripMillis)
                // The same streets, at 9 units a second instead of 3.6.
                assertEquals(walked * Trip.WALK_SPEED / Vehicle.BIKE.speed, ridden, walked * 0.01)
            },
            Step("arrived on the bike; a double tap rides faster still", { town.isStill }) {
                assertTrue(distanceBetween(town.characterSpot, target) < 0.05, "${town.characterSpot}")
                assertEquals(Ride.BIKE, town.riding)
                val point = onScreen(town, Point(target.x - 2.0, 0.0, target.z))
                tapScreen(town, point)
                val single = assertNotNull(town.tripMillis)
                tapScreen(town, point)
                assertTrue(town.isDashing)
                assertEquals(single * Vehicle.BIKE.speed / Vehicle.BIKE.dashSpeed, assertNotNull(town.tripMillis), 1.0)
            },
            Step("내리기", { town.isStill }) {
                tap("내리기")
                assertNull(town.riding)
                assertFalse(town.isRideDrawn(Ride.BIKE))
                button("자전거 타기")
            },
        )
    }

    /** The button on the open card asking where to go that goes to [name]. */
    private fun goButton(name: String): HTMLButtonElement =
        root.querySelectorAll(".chooser .go").asList().map { it as HTMLButtonElement }
            .firstOrNull { it.querySelector(".action-name")?.textContent == name } ?: error("No $name: $screen")

    @Test
    fun `a tap on a shared e-scooter walks up to it and asks where to, and the place chosen is ridden to, paid once`():
        Promise<Unit> {
        val town = startInTown()
        val index = Rides.shared.indexOfFirst { it.vehicle == Vehicle.E_SCOOTER }
        val scooter = Rides.shared[index]
        val shop = TransitLines.taxiStopsFor(1).first { it.first == Shop.NAME }.second
        var fare = 0
        return play(
            Step("stand a few steps east of it, looking west at it", { town.isStill }) {
                town.standAt(Spot(scooter.spot.x + 3.5, scooter.spot.z + 0.4))
                town.aimCamera(PI / 2, 0.6, 10.0)
            },
            Step("tap it", { town.isStill }) {
                assertTrue(town.isParkedShown(index))
                tapScreen(town, onScreen(town, Point(scooter.spot.x, 0.5, scooter.spot.z)))
                assertNotNull(town.markerSpot, "no walk to it")
                assertNull(town.riding)
                assertTrue(money(firstBalance) in hud(), hud())
            },
            Step("reached it: where to?", { root.querySelector(".chooser") != null }) {
                assertNull(town.riding, "on it before a place was chosen")
                assertTrue(distanceBetween(town.characterSpot, scooter.spot) < 0.05, "${town.characterSpot}")
                assertTrue(town.isParkedShown(index))
                assertTrue(money(firstBalance) in hud(), "paid before choosing: ${hud()}")
                // Every place on the card at its way's fare from here.
                for ((name, door) in TransitLines.taxiStopsFor(1)) {
                    val squares = town.taxiDistance(door) ?: continue
                    val shown = goButton(name).querySelector(".action-effect")?.textContent
                    assertEquals(money(SharedFare.E_SCOOTER.forDistance(squares)), shown, name)
                }
                val priced = assertNotNull(town.taxiDistance(shop))
                fare = SharedFare.E_SCOOTER.forDistance(priced)
                goButton(Shop.NAME).click()
                // The way ridden is the way priced, to the square: the ride follows the very way the fare was worked
                // out for.
                assertEquals(priced, assertNotNull(town.tripLength), 1e-9)
                assertEquals(Ride.SHARED_E_SCOOTER, town.riding)
                assertFalse(town.isParkedShown(index), "the rented one still stands on its corner")
                assertTrue(town.isRideDrawn(Ride.SHARED_E_SCOOTER))
                assertTrue(money(firstBalance - fare) in hud(), hud())
                assertEquals("공유 퀵보드 타는 중 · -${money(fare)}", root.querySelector(".hud-riding")?.textContent)
                assertEquals(shop, town.markerSpot)
            },
            Step("on the way, a tap goes nowhere else", {
                town.riding != null && distanceBetween(town.characterSpot, scooter.spot) > 5.0
            }) {
                tapScreen(town, FramePoint(0.5, 0.45))
                assertEquals(shop, town.markerSpot)
                assertEquals(Ride.SHARED_E_SCOOTER, town.riding)
            },
            Step("there: off it, back on its corner, and the keeper's talk open", { town.riding == null && town.isStill }) {
                assertEquals(shop, town.characterSpot)
                // As at the end of a walk to the shop: turned to the keeper, and his talk open.
                assertNotNull(root.querySelector(".shop-talk"), "no talk with the keeper: $screen")
                val keeper = Neighbourhood.shop.keeper
                assertEquals(kotlin.math.atan2(keeper.x - shop.x, keeper.z - shop.z), town.characterHeading, 1e-9)
                assertTrue(town.isParkedShown(index), "it did not go back")
                assertFalse(town.isRideDrawn(Ride.SHARED_E_SCOOTER))
                assertTrue(money(firstBalance - fare) in hud(), "paid more than once: ${hud()}")
                assertNull(root.querySelector(".hud-riding"))
                assertTrue(fare in SharedFare.E_SCOOTER.base..SharedFare.E_SCOOTER.most, "$fare")
            },
        )
    }

    @Test
    fun `내리기 on the way to a place gets off where the shared one is, and nothing more is paid`(): Promise<Unit> {
        val town = startInTown()
        val index = Rides.shared.indexOfFirst { it.vehicle == Vehicle.E_SCOOTER }
        val scooter = Rides.shared[index]
        val cafe = TransitLines.taxiStopsFor(1).first { it.first == Place.CAFE.label }.second
        var fare = 0
        var left: Spot? = null
        return play(
            Step("stand a few steps east of the e-scooter", { town.isStill }) {
                town.standAt(Spot(scooter.spot.x + 3.5, scooter.spot.z + 0.4))
                town.aimCamera(PI / 2, 0.6, 10.0)
            },
            Step("tap it", { town.isStill }) {
                tapScreen(town, onScreen(town, Point(scooter.spot.x, 0.5, scooter.spot.z)))
            },
            Step("reached it: ride to the café", { root.querySelector(".chooser") != null }) {
                fare = SharedFare.E_SCOOTER.forDistance(assertNotNull(town.taxiDistance(cafe)))
                goButton(Place.CAFE.label).click()
                assertEquals(Ride.SHARED_E_SCOOTER, town.riding)
                assertTrue(money(firstBalance - fare) in hud(), hud())
            },
            Step("on the way, 내리기", { distanceBetween(town.characterSpot, scooter.spot) > 4.0 }) {
                tap("내리기")
                left = town.characterSpot
                assertNull(town.riding)
                assertNull(town.markerSpot, "it rode on")
                assertNull(root.querySelector(".talk"), "a talk opened on the way")
                assertTrue(town.isParkedShown(index))
                assertTrue(money(firstBalance - fare) in hud(), hud())
            },
            Step("stands where it got off", after(300)) {
                assertEquals(left, town.characterSpot)
                assertTrue(distanceBetween(town.characterSpot, cafe) > 4.0)
                assertTrue(money(firstBalance - fare) in hud(), "paid again: ${hud()}")
            },
        )
    }

    @Test
    fun `a shared bike to 내 방 arrives as a walk there does: turned to the neighbour, the talk open, a sleep there`():
        Promise<Unit> {
        val town = startInTown()
        val bike = Rides.shared.first { it.vehicle == Vehicle.BIKE }
        val home = TransitLines.taxiStopsFor(1).first { it.first == Place.HOME.label }.second
        val venue = Neighbourhood.venue(Place.HOME)
        return play(
            Step("stand by the dock near 내 방", { town.isStill }) {
                town.standAt(Spot(bike.spot.x, bike.spot.z - 3.0))
                // From the north, looking south past the character at the dock.
                town.aimCamera(PI, 0.6, 10.0)
            },
            Step("tap the bike", { town.isStill }) {
                tapScreen(town, onScreen(town, Point(bike.spot.x, 0.5, bike.spot.z)))
            },
            Step("reached it: ride to 내 방", { root.querySelector(".chooser") != null }) {
                goButton(Place.HOME.label).click()
                assertEquals(Ride.SHARED_BIKE, town.riding)
            },
            Step("there: the neighbour's talk", { town.riding == null && town.isStill }) {
                assertEquals(home, town.characterSpot)
                val talk = assertNotNull(root.querySelector(".talk"), "no talk: $screen")
                assertFalse(talk.classList.contains("chooser"), talk.textContent)
                val toPerson = kotlin.math.atan2(venue.person.x - home.x, venue.person.z - home.z)
                assertEquals(toPerson, town.characterHeading, 1e-9)
                // At 내 방, so the night's sleep is there to take at once.
                assertTrue(town.isAt(Place.HOME))
                val sleep = button(actionName(Action.REST))
                assertFalse(sleep.disabled, sleep.textContent)
                sleep.click()
                assertTrue(town.isChangingDay, "no night after the sleep")
            },
        )
    }

    @Test
    fun `a long shared ride out to Burnaby goes quicker, an e-scooter in under half a minute`(): Promise<Unit> {
        val town = startInTown()
        val index = Rides.shared.indexOfFirst { it.vehicle == Vehicle.E_SCOOTER }
        val scooter = Rides.shared[index]
        val far = TransitLines.taxiStopsFor(1).last()
        return play(
            Step("stand a few steps east of the e-scooter", { town.isStill }) {
                town.standAt(Spot(scooter.spot.x + 3.5, scooter.spot.z + 0.4))
                town.aimCamera(PI / 2, 0.6, 10.0)
            },
            Step("tap it", { town.isStill }) {
                tapScreen(town, onScreen(town, Point(scooter.spot.x, 0.5, scooter.spot.z)))
            },
            Step("reached it: ride to the town centre's station", { root.querySelector(".chooser") != null }) {
                val squares = assertNotNull(town.taxiDistance(far.second))
                goButton(far.first).click()
                assertEquals(Ride.SHARED_E_SCOOTER, town.riding)
                val millis = assertNotNull(town.tripMillis)
                assertEquals(squares / Vehicle.E_SCOOTER.cruise(squares) * 1_000, millis, 1.0)
                assertEquals(Vehicle.E_SCOOTER.top, Vehicle.E_SCOOTER.cruise(squares), "$squares squares")
                // About 525 squares: 26 s at an e-scooter's top, where its everyday 11 would take 48.
                assertTrue(millis < 30_000.0, "$millis ms for $squares squares")
                tap("내리기")
                assertNull(town.riding)
            },
        )
    }

    @Test
    fun `a month continued after buying a bike keeps the bike, ready to ride`() {
        val store = FakeStore()
        openKept(store)
        startMonth()
        val first = assertNotNull(town)
        talkAtShop(first)
        buyButton("자전거 사기").click()
        assertTrue(store.items[SaveSlot.KEY].orEmpty().endsWith("|b350"), store.items[SaveSlot.KEY])
        first.close()

        openKept(store)
        tap(Continue.CONTINUE)
        val again = assertNotNull(town)
        assertTrue(again !== first, "the same town")
        assertTrue(money(firstBalance - 350) in hud(), hud())
        tap("자전거 타기")
        assertEquals(Ride.BIKE, again.riding)
        talkAtShop(again)
        assertNull(again.riding, "the keeper's talk did not get off")
        assertTrue(buyButton("자전거 사기").disabled)
    }

    @Test
    fun `a short balance shows why at the shop`() {
        val store = FakeStore()
        val game = FirstMinute(1011)
        // 28 days of rest to 9월 30일, with $715 left.
        val rests = List(28) { Choice.Take(Action.REST) }
        store.items[SaveSlot.KEY] = MonthRecord(1011, null, game.cheapestRoomIndex, game.lowestTotal, rests).encode()
        openKept(store)
        tap(Continue.CONTINUE)
        val town = assertNotNull(town)
        talkAtShop(town)
        assertTrue(money(firstBalance - 28 * Month.DAILY_LIVING_COST) in hud(), hud())
        buyButton("퀵보드 사기").click()
        val bike = buyButton("자전거 사기")
        assertTrue(bike.disabled)
        assertTrue("잔고가 모자라요." in bike.textContent.orEmpty(), bike.textContent)
        assertTrue("9월 30일" in hud(), hud())
    }

    @Test
    fun `getting on the bike on the way to a shared e-scooter pays no fare, and leaves it on its corner`():
        Promise<Unit> {
        val town = startInTown()
        val index = Rides.shared.indexOfFirst { it.vehicle == Vehicle.E_SCOOTER }
        val scooter = Rides.shared[index]
        return play(
            Step("buy a bike, then stand a few steps east of the e-scooter", { town.isStill }) {
                talkAtShop(town)
                buyButton("자전거 사기").click()
                tap("그만")
                town.standAt(Spot(scooter.spot.x + 3.5, scooter.spot.z + 0.4))
                town.aimCamera(PI / 2, 0.6, 10.0)
            },
            Step("tap the e-scooter, then get on the bike on the way", { town.isStill }) {
                tapScreen(town, onScreen(town, Point(scooter.spot.x, 0.5, scooter.spot.z)))
                assertNotNull(town.markerSpot, "no walk to it")
                tap("자전거 타기")
                assertEquals(Ride.BIKE, town.riding)
            },
            Step("arrived on the bike", { town.isStill }) {
                assertTrue(distanceBetween(town.characterSpot, scooter.spot) < 0.05, "${town.characterSpot}")
                assertEquals(Ride.BIKE, town.riding)
                assertTrue(money(firstBalance - 350) in hud(), "charged for a ride not taken: ${hud()}")
                assertTrue(town.isParkedShown(index), "the e-scooter left its corner")
                assertFalse(town.isRideDrawn(Ride.SHARED_E_SCOOTER))
            },
        )
    }

    @Test
    fun `on a 320 by 568 phone, a bike, its riding row and a busy day's log leave the talk whole and answerable`():
        Promise<Unit> {
        usePageStyle()
        val store = FakeStore()
        val game = FirstMinute(1011)
        // On 9월 2일 a bike and five shared rides, then a day of rest: the log of 9월 2일 opens 9월 3일.
        val rides = List(5) { Spend(Spending.SHARED_BIKE_RIDE, 3) }
        val moves = listOf(Spend(Spending.BIKE, 350)) + rides + Choice.Take(Action.REST)
        store.items[SaveSlot.KEY] = MonthRecord(1011, null, game.cheapestRoomIndex, game.lowestTotal, moves).encode()
        openKept(store)
        tap(Continue.CONTINUE)
        val town = assertNotNull(town)
        pinPhone(town, 320, 568)
        val log = root.querySelectorAll(".log li").asList().map { it.textContent }
        assertEquals(listOf("자전거를 샀어요. -\$350", "그 밖에 5건 -\$15"), log.take(2))
        assertEquals(4, log.size, "$log")
        tap("자전거 타기")
        assertEquals(Ride.BIKE, town.riding)
        talkAtDoor(Place.HOME)
        assertNull(town.riding, "the talk did not get off")
        /** True once the talk has risen into place: its opening animation is over. */
        fun settled(): Boolean {
            val talk = root.querySelector(".talk") ?: return false
            return (talk.asDynamic().getAnimations().length as Int) == 0
        }
        return play(
            Step("the talk in place", { town.isStill && settled() }) {
                val talk = rectOf(".talk")
                val above = maxOf(rectOf(".hud").bottom, rectOf(".over-town .log").bottom)
                assertTrue(talk.top >= above, "the talk at ${talk.top} is under the cards, which end at $above")
                assertTrue(talk.bottom <= 568.0, "the talk runs off the small phone: ${talk.bottom}")
                val answer = rectOf(".talk .spend")
                assertTrue(answer.bottom <= 568.0, "the answer is off the screen: ${answer.bottom}")
                // Wherever the small map and the talk meet, the talk lies over it: the map is in the town's own
                // layer, the talk in the cards' layer above it.
                assertTrue(town.element.contains(town.minimapElement))
                val cards = root.querySelector(".over-town") as HTMLElement
                assertTrue(cards.contains(root.querySelector(".talk")))
                assertEquals("1", window.getComputedStyle(cards).getPropertyValue("z-index"))
            },
        )
    }
}
