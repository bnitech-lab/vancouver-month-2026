package vancouvermonth.web

import kotlin.js.Date
import kotlin.js.Promise
import kotlin.math.PI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit
import vancouvermonth.core.Action
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Spending
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.save.Spend
import vancouvermonth.save.play
import vancouvermonth.text.Continue
import vancouvermonth.text.DO_ON_ARRIVAL
import vancouvermonth.text.FAR_WALK_HINT
import vancouvermonth.text.Riding
import vancouvermonth.text.SLEEP_ON_ARRIVAL
import vancouvermonth.text.Shop
import vancouvermonth.text.Transit
import vancouvermonth.text.Transport
import vancouvermonth.text.keyword

/**
 * A job is done at its place, not from afar: the town drawn for real on SwiftShader at a phone's size, played by tapping
 * names, as a player does. A tap from afar starts the walk and opens nothing; the talk opens on arrival, on foot or at
 * the end of a shared ride, and only there does its answer work.
 */
class ArriveFirstTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private var town: NeighbourhoodScene? = null
    private var guide: FirstRunGuide? = null

    @AfterTest
    fun removeScreen() {
        guide?.close()
        town?.close()
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    /**
     * Opens seed 1011's game with 내 방 in [zone], in a real town pinned to a phone's 375 by 812 points once it is made;
     * its life clock stands still, and talks answer at once. With [kept], the page goes on with that month.
     */
    private fun open(zone: Int, kept: MonthRecord? = null, withGuide: Boolean = false): NeighbourhoodScene {
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = {
            val opened = assertNotNull(openWalkableTown(lessMotion = false, homeZone = zone), "No WebGL 2")
            (opened as NeighbourhoodScene).also {
                it.lifeClock = { 0.0 }
                it.passMillis = 60.0
                val element = it.element
                element.style.setProperty("inset", "auto")
                element.style.left = "0px"
                element.style.top = "0px"
                element.style.width = "375px"
                element.style.height = "812px"
                town = it
            }
        })
        game.talkSettleMillis = 0.0
        if (withGuide) game.guide = FirstRunGuide().also { guide = it }
        if (kept != null) {
            val store = FakeStore()
            store.items[SaveSlot.KEY] = kept.encode()
            game.slot = SaveSlot(Memory { store })
        }
        game.show()
        if (kept != null) root.button(Continue.CONTINUE).click() else startSeed1011(root)
        return assertNotNull(town)
    }

    private val screen: String
        get() = root.textContent.orEmpty()

    private fun hud(): String = root.querySelector(".hud")?.textContent.orEmpty()

    /** The day, the days left and the money on the top card. */
    private fun figures(): String = root.querySelector(".hud-figures")?.textContent.orEmpty()

    /** The line on the top card that says where the character is on its way to, or null. */
    private fun going(): String? = root.querySelector(".hud-going")?.textContent

    private fun talk(): HTMLElement? = root.querySelector(".talk") as? HTMLElement

    /** The answer in the open talk that takes the place's action or the interview. */
    private fun answer(): HTMLButtonElement =
        root.querySelector(".talk .spend") as? HTMLButtonElement ?: error("No talk is open: $screen")

    private fun smallTalk(): HTMLButtonElement = assertNotNull(talk(), "No talk is open: $screen").button("이야기")

    /** Taps the floating name of [place]. */
    private fun tapName(place: Place) {
        val name = root.querySelectorAll(".place-name").asList().map { it as HTMLButtonElement }
            .first { it.textContent == place.label }
        name.click()
    }

    /** A place a card asks whether to go to, by its name. */
    private fun goButton(name: String): HTMLButtonElement =
        assertNotNull(root.querySelector(".chooser"), "No chooser: $screen").querySelectorAll(".go").asList()
            .map { it as HTMLButtonElement }
            .firstOrNull { it.querySelector(".action-name")?.textContent == name } ?: error("No $name: $screen")

    /** Taps the town's canvas at [point], 0.0 to 1.0 across and down, as a finger would. */
    private fun tapScreen(town: NeighbourhoodScene, point: FramePoint) {
        assertTrue(point.left in 0.05..0.95 && point.top in 0.2..0.65, "$point is near the edge or under a card")
        val canvas = town.element.querySelector("canvas") as HTMLCanvasElement
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

    private fun onScreen(town: NeighbourhoodScene, point: Point): FramePoint =
        assertNotNull(town.screenPoint(point), "$point is behind the camera")

    /** A step of a test that waits: its name in a failure, when it is ready to run, and what it does. */
    private class Step(val name: String, val ready: () -> Boolean, val run: () -> Unit)

    /** Ready once [millis] have passed since the step before was done. */
    private fun after(millis: Double): () -> Boolean {
        var since: Double? = null
        return {
            val now = Date.now()
            val start = since ?: now.also { since = it }
            now - start >= millis
        }
    }

    /** Runs each step once it is ready, checking every 20 ms, and fails a step that waits more than half a minute. */
    private fun play(vararg steps: Step): Promise<Unit> = Promise { resolve, reject ->
        fun run(index: Int) {
            if (index == steps.size) {
                resolve(Unit)
                return
            }
            val step = steps[index]
            val started = Date.now()
            fun check() {
                try {
                    when {
                        step.ready() -> {
                            step.run()
                            run(index + 1)
                        }
                        Date.now() - started > 30_000 -> reject(AssertionError("${step.name}: waited too long: $screen"))
                        else -> window.setTimeout({ check() }, 20)
                    }
                } catch (error: Throwable) {
                    reject(AssertionError("${step.name}: ${error.message}"))
                }
            }
            check()
        }
        run(0)
    }

    /** Seed 1011's cheapest room kept up to the morning of its first interview, applied for at 항구 회사. */
    private fun keptToInterviewDay(): MonthRecord {
        val first = FirstMinute(1011)
        val room = first.cheapestRoomIndex
        val month = first.startMonth(room, first.lowestTotal)
        var record = MonthRecord(1011, null, room, first.lowestTotal)
        fun take(choice: Choice) {
            month.play(choice)
            record = record.after(choice)
        }
        while (!month.hasInterviewToday) {
            if (month.interview == null) take(Choice.Take(Action.APPLY, Place.OFFICE.number))
            if (!month.hasInterviewToday) take(Choice.Take(Action.REST))
        }
        return record
    }

    @Test
    fun `from Burnaby a tap on a downtown name walks toward it, opens no talk and spends nothing`(): Promise<Unit> {
        val town = open(zone = 3)
        val first = "아침 8시 · 체력 5/5"
        val office = Neighbourhood.venue(Place.OFFICE)
        val library = Neighbourhood.venue(Place.LIBRARY)
        val start = HomeSite.of(3).venue(Place.HOME).door
        var figures = ""
        return play(
            Step("tap 항구 회사's name, across the map", { town.isStill }) {
                figures = figures()
                assertFalse(town.isAt(Place.OFFICE))
                tapName(Place.OFFICE)
                assertNull(talk(), "a talk from 12 km away: $screen")
                assertEquals("항구 회사로 가는 중", going())
                assertEquals(office.talkSpot, town.markerSpot)
                assertTrue(assertNotNull(town.tripLength) > 300.0, "${town.tripLength}")
                // The first long walk says once what would be quicker, and the taxi is right there.
                assertTrue(FAR_WALK_HINT in hud(), hud())
                root.button(Transit.CALL_TAXI)
            },
            Step("half a second on, nothing is open and nothing spent", after(500.0)) {
                assertNull(talk(), "a talk on the way: $screen")
                assertTrue(first in hud(), hud())
                assertEquals(figures, figures())
                assertFalse(town.isAt(Place.OFFICE))
                assertTrue(distanceBetween(town.characterSpot, start) < 200.0, "${town.characterSpot}")
            },
            Step("another far name changes the walk, and the hint is not said again", { true }) {
                tapName(Place.LIBRARY)
                assertNull(talk())
                assertEquals("시내 도서관으로 가는 중", going())
                assertEquals(library.talkSpot, town.markerSpot)
                assertFalse(FAR_WALK_HINT in hud(), hud())
                // From here on the month never says it again, however far the walk.
                town.standAt(start)
                assertNull(going(), "still on the way: $screen")
                tapName(Place.OFFICE)
                assertEquals("항구 회사로 가는 중", going())
                assertFalse(FAR_WALK_HINT in hud(), hud())
            },
            Step("the taxi button stops the walk, and asks where to", { true }) {
                root.button(Transit.CALL_TAXI).click()
                assertNull(going(), "still on the way: $screen")
                assertNull(town.markerSpot, "still walking")
                goButton(Place.OFFICE.label)
            },
        )
    }

    @Test
    fun `a tap on the ground or a far name turns away from the walk and the talk, and the shop waits too`(): Promise<Unit> {
        val town = open(zone = 1)
        val home = Neighbourhood.venue(Place.HOME)
        return play(
            Step("at 내 방's door its name opens the talk at once, and a far name closes it", { town.isStill }) {
                tapName(Place.HOME)
                assertNotNull(talk(), "no talk at the place: $screen")
                assertNull(going(), "on the way, at the place: $screen")
                tapName(Place.CAFE)
                assertNull(talk(), "the talk stayed open on the way: $screen")
                assertEquals("물가 카페로 가는 중", going())
            },
            Step("a tap on the ground ends the walk's line", { true }) {
                town.standAt(home.door)
                tapName(Place.CAFE)
                assertNotNull(going())
                tapScreen(town, onScreen(town, Point(home.door.x + 3.0, 0.0, home.door.z + 1.0)))
                assertNull(going(), "still on the way: $screen")
            },
            Step("the bike shop from afar: the walk, no talk, nothing to buy", { town.isStill }) {
                town.standAt(home.door)
                town.shopName.click()
                assertNull(talk(), "the keeper talks from afar: $screen")
                assertEquals("자전거 가게로 가는 중", going())
                assertEquals(Neighbourhood.shop.talkSpot, town.markerSpot)
            },
            Step("a few steps from him, the name again", { true }) {
                val shop = Neighbourhood.shop
                town.standAt(Spot(shop.talkSpot.x - 2.5, shop.talkSpot.z + 1.5))
                town.shopName.click()
                assertNull(talk(), "the keeper talks before the character has come: $screen")
                assertEquals("${Shop.NAME}로 가는 중", going())
            },
            Step("arrived: his talk", { town.isStill }) {
                assertNull(going())
                assertNotNull(root.querySelector(".shop-talk"), "no talk with the keeper: $screen")
            },
        )
    }

    @Test
    fun `a talk open away from its place holds back its answer and small talk, with the reason, until the character comes`():
        Promise<Unit> {
        val town = open(zone = 1)
        val cafe = Neighbourhood.venue(Place.CAFE)
        val day = "아침 8시 · 체력 5/5"
        return play(
            Step("from 내 방's door, each place's talk", { town.isStill }) {
                val jobs = mapOf(
                    Place.CAFE to Action.WORK,
                    Place.LIBRARY to Action.STUDY_ENGLISH,
                    Place.COWORKING to Action.PRACTISE_CODING,
                    Place.OFFICE to Action.APPLY,
                )
                for ((place, action) in jobs) {
                    town.onPlace(place)
                    val answer = answer()
                    assertTrue(answer.disabled, "$place from afar")
                    assertTrue(keyword(action) in answer.textContent.orEmpty(), answer.textContent)
                    assertTrue(DO_ON_ARRIVAL in answer.textContent.orEmpty(), answer.textContent)
                    assertTrue(smallTalk().disabled, "small talk with $place from afar")
                    answer.click()
                    smallTalk().click()
                    assertNotNull(talk(), "the talk closed")
                    assertTrue(day in hud(), "$place: ${hud()}")
                    root.button("그만").click()
                }
            },
            Step("sleep from afar says its own line", { true }) {
                town.standAt(cafe.door)
                town.onPlace(Place.HOME)
                assertTrue(answer().disabled, "sleep from afar")
                assertTrue(SLEEP_ON_ARRIVAL in answer().textContent.orEmpty(), answer().textContent)
                answer().click()
                assertFalse(town.isChangingDay, "the night fell far from 내 방")
                root.button("그만").click()
            },
            Step("the café's talk, a few steps off, walks up", { true }) {
                town.standAt(Spot(cafe.door.x - 3.0, cafe.door.z))
                town.onPlace(Place.CAFE)
                assertTrue(answer().disabled, "the shift before the character has come")
                town.walkFor(cafe.talkSpot)
            },
            Step("come: what the talk offers can start, the focus still on it", { town.isStill }) {
                assertTrue(town.isAt(Place.CAFE))
                assertFalse(answer().disabled, "no shift at the café: ${answer().textContent}")
                assertFalse(DO_ON_ARRIVAL in answer().textContent.orEmpty(), answer().textContent)
                assertFalse(smallTalk().disabled, "no small talk at the café")
                assertSame(talk(), document.activeElement)
                answer().click()
                assertTrue("체력 2/5" in hud(), hud())
                assertNull(talk(), "the talk stayed open")
            },
        )
    }

    @Test
    fun `from zone 3 a tap on the near café's name walks there, the talk opens on arrival, and the shift can be worked`():
        Promise<Unit> {
        val town = open(zone = 3)
        val cafe = HomeSite.of(3).venue(Place.SLOPE_CAFE)
        return play(
            Step("the first morning, tap 비탈 카페's name", { town.isStill }) {
                assertNull(going())
                tapName(Place.SLOPE_CAFE)
                assertNull(talk(), "a talk before the walk: $screen")
                assertEquals("비탈 카페로 가는 중", going())
                // A short walk: no word about taxis.
                assertTrue(assertNotNull(town.tripLength) < 60.0, "${town.tripLength}")
                assertFalse(FAR_WALK_HINT in hud(), hud())
            },
            Step("on arrival, 마테오 talks, and his shift can be worked", { town.isStill }) {
                assertTrue(town.isAt(Place.SLOPE_CAFE))
                assertEquals(cafe.talkSpot, town.characterSpot)
                assertNull(going(), "still on the way: $screen")
                assertNotNull(talk(), "no talk on arrival: $screen")
                assertEquals("마테오", talk()?.getAttribute("aria-label"))
                assertFalse(answer().disabled, answer().textContent)
                assertFalse(smallTalk().disabled)
                smallTalk().click()
                answer().click()
                assertTrue("체력 2/5" in hud(), hud())
                assertTrue("방금 있었던 일" in screen, screen)
            },
            Step("at the place, the name opens the talk at once", { town.isStill }) {
                tapName(Place.SLOPE_CAFE)
                assertNotNull(talk(), "no talk at the place: $screen")
                assertNull(going(), "on the way, at the place: $screen")
            },
        )
    }

    @Test
    fun `a double tap on a place from afar dashes there, and the talk still opens on arrival`(): Promise<Unit> {
        val town = open(zone = 1)
        val home = Neighbourhood.venue(Place.HOME)
        return play(
            Step("stand a few steps east of 내 방, looking at it", { town.isStill }) {
                town.standAt(Spot(home.door.x + 4.0, home.door.z + 1.0))
                town.aimCamera(FollowCamera.START_YAW, FollowCamera.START_PITCH, FollowCamera.START_DISTANCE)
            },
            Step("tap its roof twice", { town.isStill }) {
                val roof = onScreen(town, Point(home.x + home.width / 2.0, home.height + 1.0, home.z + home.depth / 2.0))
                tapScreen(town, roof)
                assertEquals("내 방으로 가는 중", going())
                tapScreen(town, roof)
                assertTrue(town.isDashing, "the second tap did not dash")
                assertNull(talk(), "a talk before the character has come: $screen")
                assertEquals("내 방으로 가는 중", going())
            },
            Step("arrived", { town.isStill }) {
                assertNotNull(talk(), "no talk on arrival: $screen")
                assertFalse(answer().disabled, answer().textContent)
            },
        )
    }

    @Test
    fun `a taxi to the place stands the character at its door, and then the name opens the talk and the action works`():
        Promise<Unit> {
        val town = open(zone = 3)
        val cafe = HomeSite.of(3).venue(Place.SLOPE_CAFE)
        return play(
            Step("tap a far name, then shorten the walk with a taxi to the near café", { town.isStill }) {
                tapName(Place.TOWN_CENTRE_OFFICE)
                assertNotNull(going())
                root.button(Transit.CALL_TAXI).click()
                goButton(Place.SLOPE_CAFE.label).click()
                assertEquals(Transport.TAXI, town.travelling)
                assertNull(going(), "still on the way on foot: $screen")
            },
            Step("out at its door: no talk yet, as ever", { town.travelling == null && town.isStill }) {
                assertEquals(cafe.door, town.characterSpot)
                assertTrue(town.isAt(Place.SLOPE_CAFE))
                assertNull(talk(), "a talk from the taxi: $screen")
                tapName(Place.SLOPE_CAFE)
                assertFalse(answer().disabled, answer().textContent)
                answer().click()
                assertTrue("체력 2/5" in hud(), hud())
            },
        )
    }

    @Test
    fun `a shared ride to the place opens the talk as it gets off, and the action works`(): Promise<Unit> {
        val town = open(zone = 1)
        val scooter = Rides.shared.first { it.vehicle == Vehicle.E_SCOOTER }
        val cafe = Neighbourhood.venue(Place.CAFE)
        return play(
            Step("stand a few steps east of a shared e-scooter, looking west at it", { town.isStill }) {
                town.standAt(Spot(scooter.spot.x + 3.5, scooter.spot.z + 0.4))
                town.aimCamera(PI / 2, 0.6, 10.0)
            },
            Step("tap it", { town.isStill }) {
                tapScreen(town, onScreen(town, Point(scooter.spot.x, 0.5, scooter.spot.z)))
            },
            Step("reached it: ride to the café", { root.querySelector(".chooser") != null }) {
                goButton(Place.CAFE.label).click()
                assertEquals(Ride.SHARED_E_SCOOTER, town.riding)
            },
            Step("there: off it, with the talk open and the shift ready", { town.riding == null && town.isStill }) {
                assertEquals(cafe.door, town.characterSpot)
                assertNotNull(talk(), "no talk on arrival: $screen")
                assertFalse(answer().disabled, answer().textContent)
                answer().click()
                assertTrue("체력 2/5" in hud(), hud())
            },
        )
    }

    @Test
    fun `on the player's own bike the character rides to the far name, and the talk there gets it off`(): Promise<Unit> {
        val first = FirstMinute(1011)
        val kept = MonthRecord(1011, null, first.cheapestRoomIndex, first.lowestTotal, listOf(Spend(Spending.BIKE, 350)))
        val town = open(zone = 1, kept = kept)
        val cafe = Neighbourhood.venue(Place.CAFE)
        return play(
            Step("on the bike, a few squares off, the café's name", { town.isStill }) {
                root.button("자전거 타기").click()
                town.standAt(Spot(cafe.door.x - 6.0, cafe.door.z - 2.0))
                tapName(Place.CAFE)
                assertNull(talk())
                assertEquals(Ride.BIKE, town.riding, "off the bike on the way")
                assertEquals("물가 카페로 가는 중", going())
                root.button(Riding.GET_OFF)
            },
            Step("arrived: off the bike, with the talk open", { town.isStill }) {
                assertNull(town.riding, "the talk did not get off")
                assertNotNull(talk(), "no talk on arrival: $screen")
                assertFalse(answer().disabled, answer().textContent)
            },
        )
    }

    @Test
    fun `the interview is held only once the character has come to the office`(): Promise<Unit> {
        val town = open(zone = 1, kept = keptToInterviewDay())
        val office = Neighbourhood.venue(Place.OFFICE)
        return play(
            Step("the interview's morning at 내 방's door: the name only sets off", { town.isStill }) {
                assertTrue("오늘은 면접 날이에요. 항구 회사로 가세요." in hud(), hud())
                tapName(Place.OFFICE)
                assertNull(talk(), "the desk talks from afar: $screen")
                assertEquals("항구 회사로 가는 중", going())
                assertTrue("오늘은 면접 날이에요" in hud(), hud())
            },
            Step("a talk open from afar holds the interview back, with the reason", { true }) {
                town.standAt(Neighbourhood.venue(Place.HOME).door)
                town.onPlace(Place.OFFICE)
                assertEquals("면접 보기", answer().querySelector(".action-name")?.textContent)
                assertTrue(answer().disabled, "an interview from afar")
                assertTrue(DO_ON_ARRIVAL in answer().textContent.orEmpty(), answer().textContent)
                answer().click()
                assertTrue("오늘은 면접 날이에요" in hud(), "the interview was held from afar: ${hud()}")
                root.button("그만").click()
            },
            Step("a few steps from the office, the name again", { true }) {
                town.standAt(Spot(office.door.x - 4.0, office.door.z))
                tapName(Place.OFFICE)
                assertNull(talk(), "the desk talks before the character has come: $screen")
            },
            Step("arrived: the interview", { town.isStill }) {
                assertFalse(answer().disabled, answer().textContent)
                assertFalse(DO_ON_ARRIVAL in answer().textContent.orEmpty(), answer().textContent)
                answer().click()
                assertTrue("1차 영어 면접 통과!" in screen || "1차 영어 면접에서 떨어졌어요." in screen, screen)
            },
        )
    }

    @Test
    fun `arriving with the guide open opens the talk under it, and leaves the focus on the guide`(): Promise<Unit> {
        val town = open(zone = 3, withGuide = true)
        return play(
            Step("set off for the near café, and open the guide on the way", { town.isStill }) {
                tapName(Place.SLOPE_CAFE)
                (assertNotNull(root.querySelector(".guide-open")) as HTMLElement).click()
                assertNotNull(root.querySelector(".guide-card"))
            },
            Step("arrived", { town.isStill && talk() != null }) {
                assertNotNull(root.querySelector(".guide-card"), "the arrival closed the guide")
                assertTrue(assertNotNull(root.querySelector(".guide-card")).contains(document.activeElement))
                root.button("닫기").click()
                assertFalse(answer().disabled, answer().textContent)
            },
        )
    }
}
