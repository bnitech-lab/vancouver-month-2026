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
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.core.Spending
import vancouvermonth.core.TaxiFare
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.save.Spend
import vancouvermonth.save.play
import vancouvermonth.text.Continue
import vancouvermonth.text.Transit
import vancouvermonth.text.Transport
import vancouvermonth.text.date
import vancouvermonth.text.money

/**
 * The SkyTrain, the bus, the water bus and the taxi in the real town, drawn with Three.js on SwiftShader: a stop is
 * tapped where it is drawn, a place to go is chosen, and the ride is waited out by what the town says, not by time.
 */
class TransitInTownTest {
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

    /** Seed 1011's balance on its first day, in its cheapest room. */
    private val firstBalance: Int
        get() = FirstMinute(1011).let { it.startMonth(it.cheapestRoomIndex, it.lowestTotal).balance }

    /** Makes [town] 375 by 812 points, a phone's shape, whatever the test browser's window. */
    private fun pinToPhone(town: NeighbourhoodScene) {
        val element = town.element
        element.style.setProperty("inset", "auto")
        element.style.left = "0px"
        element.style.top = "0px"
        element.style.width = "375px"
        element.style.height = "812px"
    }

    /** A real town for seed 1011, pinned to a phone, its life clock held at 0, talks answering at once. */
    private fun opened(): NeighbourhoodScene {
        val opened = assertNotNull(openWalkableTown(lessMotion = false), "The test browser has no WebGL 2")
        return (opened as NeighbourhoodScene).also {
            it.lifeClock = { 0.0 }
            pinToPhone(it)
            town = it
        }
    }

    /** Plays seed 1011 in a real town: the cheapest room, an exact guess, and September starts. */
    private fun startInTown(): NeighbourhoodScene {
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { opened() })
        game.talkSettleMillis = 0.0
        game.show()
        startSeed1011(root)
        return assertNotNull(town)
    }

    /** Opens seed 1011's page with [record] kept, and goes on with it in a real town. */
    private fun continued(record: MonthRecord): NeighbourhoodScene {
        val store = FakeStore()
        store.items[SaveSlot.KEY] = record.encode()
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { opened() })
        game.slot = SaveSlot(Memory { store })
        game.talkSettleMillis = 0.0
        game.show()
        root.button(Continue.CONTINUE).click()
        return assertNotNull(town)
    }

    private fun hud(): String = root.querySelector(".hud")?.textContent.orEmpty()

    private fun chooser(): HTMLElement? = root.querySelector(".chooser") as? HTMLElement

    /** The button in the open chooser that goes to [name]. */
    private fun goButton(name: String): HTMLButtonElement =
        assertNotNull(chooser(), "No chooser: $screen").querySelectorAll(".go").asList().map { it as HTMLButtonElement }
            .firstOrNull { it.querySelector(".action-name")?.textContent == name } ?: error("No $name: $screen")

    /** Where [point] is on the town's screen; it must be in front of the camera. */
    private fun onScreen(town: NeighbourhoodScene, point: Point): FramePoint =
        assertNotNull(town.screenPoint(point), "$point is behind the camera")

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

    /** Stands the character at [stop] and turns the camera to look at it from its open side, near. */
    private fun standAt(town: NeighbourhoodScene, stop: Stop, yaw: Double) {
        town.standAt(TransitLines.waitAt(stop))
        town.aimCamera(yaw, 0.55, 9.0)
    }

    /** Taps the middle of [stop]'s tap box, low down, where it is drawn. */
    private fun tapStop(town: NeighbourhoodScene, stop: Stop) {
        val box = TransitLines.tapBoxes[TransitLines.tapStops.indexOf(stop)]
        tapScreen(town, onScreen(town, Point(box.x + box.width / 2, box.y + 1.2, box.z + box.depth / 2)))
    }

    /** A step of a test that waits: its name in a failure, when it is ready to run, and what it does. */
    private class Step(val name: String, val ready: () -> Boolean, val run: () -> Unit)

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

    /** Checks [ready] every 20 ms and then runs [then], or fails after fifteen seconds. */
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

    /** Ready once [millis] have passed since the step before was done. */
    private fun after(millis: Double): () -> Boolean {
        var since: Double? = null
        return {
            val now = Date.now()
            val start = since ?: now.also { since = it }
            now - start >= millis
        }
    }

    /** True once the ride is over and nothing of the player's moves. */
    private fun arrived(town: NeighbourhoodScene): () -> Boolean = { town.travelling == null && town.isStill }

    /** The camera's eye under the ground each time it is looked at there, with when, to find how long it stood still. */
    private class Underground(val town: NeighbourhoodScene) {
        private val eyes = mutableListOf<Pair<Double, Point>>()

        /** Notes where the eye is now, when under the ground; true, so it can sit in any step's readiness. */
        fun look(): Boolean {
            if (town.isUnderground) eyes += Date.now() to town.undergroundShown.eye
            return true
        }

        /** How many times the eye was looked at under the ground. */
        val looks: Int
            get() = eyes.size

        /** The longest time, in milliseconds, the eye was found in the same place. */
        val longestStill: Double
            get() {
                var longest = 0.0
                var since = eyes.firstOrNull()?.first ?: return 0.0
                for ((before, after) in eyes.zipWithNext()) {
                    if (before.second != after.second) since = after.first else longest = maxOf(longest, after.first - since)
                }
                return longest
            }
    }

    /** No place's name floats over the town's picture: under the ground, none is shown. */
    private fun namesShown(town: NeighbourhoodScene): Int =
        town.element.querySelectorAll(".place-name").asList().count { !(it as HTMLElement).classList.contains("behind") }

    @Test
    fun `the SkyTrain from the station by the tunnel to the one past the creek's head, on the pass, no day gone`():
        Promise<Unit> {
        val town = startInTown()
        val from = Stop(Transport.SKYTRAIN, 1)
        val to = TransitLines.stations[2]
        var aboard = false
        return play(
            Step("stand at the station", { town.isStill }) { standAt(town, from, 0.0) },
            Step("tap its building", { town.isStill }) {
                tapStop(town, from)
                assertNotNull(chooser(), "the chooser opens at once at the station: $screen")
                assertTrue("스카이트레인 · 터널 입구역" in chooser()!!.textContent.orEmpty())
                assertTrue(Transit.PASS in goButton("크릭 끝역").textContent.orEmpty())
                assertNull(chooser()!!.querySelector(".go:disabled"))
                goButton("크릭 끝역").click()
                assertEquals(Transport.SKYTRAIN, town.travelling)
                assertTrue(town.isTrainRidden)
                assertTrue(Transit.riding(Transport.SKYTRAIN, null) in hud(), hud())
                assertNull(chooser())
            },
            Step("on the line, the camera with the train", { town.travelling != null && town.characterSpot.x > 212.0 }) {
                aboard = true
                assertFalse(town.isCharacterShown, "the character rides inside")
                val nearest = (0..120).minOf { distanceBetween(town.characterSpot, Neighbourhood.guideway.spotAt(it * 0.5)) }
                assertTrue(nearest < 0.3, "off the line: ${town.characterSpot}")
                // Taps go nowhere while riding.
                tapScreen(town, FramePoint(0.5, 0.5))
                assertFalse(town.isMarkerShown)
            },
            Step("out at the station past the creek's head", arrived(town)) {
                assertTrue(aboard)
                assertEquals(to.door, town.characterSpot)
                assertTrue(town.isCharacterShown)
                assertTrue(date(Month.FIRST_DAY) in hud(), hud())
                assertTrue(money(firstBalance) in hud(), "the pass pays: ${hud()}")
                assertFalse(Transit.riding(Transport.SKYTRAIN, null) in hud())
                assertTrue(Transit.CALL_TAXI in hud())
            },
        )
    }

    @Test
    fun `from the station under downtown the platform shows, the train comes in and takes the character, then the mouth`():
        Promise<Unit> {
        val town = startInTown()
        val from = Stop(Transport.SKYTRAIN, 0)
        val under = Underground(town)
        return play(
            Step("stand at the entrance under downtown", { town.isStill }) { standAt(town, from, 0.0) },
            Step("tap it and ride east", { town.isStill }) {
                tapStop(town, from)
                assertTrue("스카이트레인 · 다운타운역" in chooser()?.textContent.orEmpty(), screen)
                goButton("터널 입구역").click()
                assertEquals(Transport.SKYTRAIN, town.travelling)
            },
            Step("on the platform, the character waiting", { town.isUnderground && town.undergroundShown.isRiderShown }) {
                under.look()
                val eye = town.undergroundShown.eye
                assertTrue(eye.x in UndergroundRide.HALL_WEST..UndergroundRide.HALL_EAST, "$eye")
                assertFalse(town.isCharacterShown, "the town's own character is drawn too")
                assertEquals(0, namesShown(town), "a place's name over the platform")
                // On the small map the character goes with the train, under the streets.
                assertEquals(UndergroundRide.LINE_Z, town.characterSpot.z, 0.01)
            },
            Step("the doors open", { under.look() && town.undergroundShown.doorsDrawn > 0.99 }) {
                assertTrue(town.undergroundShown.isRiderShown || !town.isUnderground)
            },
            Step("in the tunnel behind the train, its lamps going by", {
                under.look() && town.isUnderground && town.undergroundShown.eye.x > UndergroundRide.HALL_EAST
            }) {
                assertFalse(town.undergroundShown.isRiderShown)
                assertTrue(town.undergroundShown.areLampsShown)
                assertEquals(0.0, town.undergroundShown.doorsDrawn)
            },
            Step("out of the tunnel, on the line", {
                under.look() && !town.isUnderground && town.characterSpot.x > UndergroundRide.MOUTH.x
            }) {
                assertFalse(town.isUnderground)
                assertTrue(town.cameraReach < 40.0)
                assertTrue(under.looks > 10, "${under.looks} looks")
                assertTrue(under.longestStill < STILL_MILLIS, "the camera stood still ${under.longestStill} ms")
            },
            Step("out at the station by the tunnel", arrived(town)) {
                assertEquals(TransitLines.stations[1].door, town.characterSpot)
                assertTrue(money(firstBalance) in hud(), hud())
            },
        )
    }

    @Test
    fun `from the station by the tunnel west into it, then the platform under downtown, where the character steps out`():
        Promise<Unit> {
        val town = startInTown()
        val from = Stop(Transport.SKYTRAIN, 1)
        val downtown = TransitLines.stations[0].door
        val under = Underground(town)
        return play(
            Step("stand at the station by the tunnel", { town.isStill }) { standAt(town, from, 0.0) },
            Step("ride west", { town.isStill }) {
                tapStop(town, from)
                goButton("다운타운역").click()
                assertEquals(Transport.SKYTRAIN, town.travelling)
            },
            Step("into the tunnel: the camera at its mouth, after the train", { town.isUnderground }) {
                under.look()
                assertFalse(town.isCharacterShown)
                val eye = town.undergroundShown.eye
                assertTrue(UndergroundRide.MOUTH.x - eye.x < 6.0, "$eye")
                assertFalse(town.undergroundShown.isRiderShown)
            },
            Step("at the platform the character steps out", {
                under.look() && town.isUnderground && town.undergroundShown.isRiderShown
            }) {
                val eye = town.undergroundShown.eye
                assertTrue(eye.x in UndergroundRide.HALL_WEST..UndergroundRide.HALL_EAST, "$eye")
                assertTrue(town.undergroundShown.doorsDrawn > 0.0)
            },
            Step("out under downtown, the camera at the door", { under.look() && arrived(town)() }) {
                assertTrue(under.longestStill < STILL_MILLIS, "the camera stood still ${under.longestStill} ms")
                assertFalse(town.isUnderground)
                assertEquals(downtown, town.characterSpot)
                assertTrue(town.isCharacterShown)
                val door = onScreen(town, Point(downtown.x, 1.0, downtown.z))
                assertTrue(kotlin.math.abs(door.left - 0.5) < 0.05 && kotlin.math.abs(door.top - 0.5) < 0.05, "$door")
                assertTrue(money(firstBalance) in hud(), hud())
            },
        )
    }

    @Test
    fun `under the ground the train still rumbles, heard where the camera rides with it`(): Promise<Unit> {
        val town = opened()
        var loudest = 0.0
        var drawnUnder = 0
        town.onSounds = { train, _, _ -> if (town.isUnderground) loudest = maxOf(loudest, train) }
        return play(
            Step("stand at the entrance under downtown", { town.isStill }) {
                town.standAt(TransitLines.stations[0].door)
            },
            Step("ride east", { town.isStill }) {
                town.travel(Transport.SKYTRAIN, 0, 1)
                drawnUnder = town.framesDrawn
            },
            Step("out of the tunnel", { town.travelling != null && !town.isUnderground && town.framesDrawn > drawnUnder }) {
                assertTrue(loudest > 0.05, "the train under the ground was silent: $loudest")
            },
        )
    }

    @Test
    fun `with less motion the platform and the tunnel are calm: no lamps going by, and no drift while waiting`():
        Promise<Unit> {
        val town = opened()
        town.asksLessMotion = { true }
        var waiting: Point? = null
        var frames = 0
        return play(
            Step("stand at the entrance under downtown", { town.isStill }) {
                town.standAt(TransitLines.stations[0].door)
            },
            Step("ride east", { town.isStill }) { town.travel(Transport.SKYTRAIN, 0, 1) },
            Step("waiting on the platform", { town.isUnderground && town.undergroundShown.isRiderShown }) {
                waiting = town.undergroundShown.eye
                frames = town.framesDrawn
                assertFalse(town.undergroundShown.areLampsShown)
            },
            Step("still waiting a moment later", after(300.0)) {
                assertTrue(town.framesDrawn > frames, "nothing was drawn")
                if (town.undergroundShown.isRiderShown) assertEquals(waiting, town.undergroundShown.eye)
            },
            Step("the train takes it on through the tunnel", {
                town.isUnderground && town.undergroundShown.eye.x > UndergroundRide.HALL_EAST
            }) {
                assertFalse(town.undergroundShown.areLampsShown)
            },
        )
    }

    @Test
    fun `내리기 lets the character out of the taxi on the street, where a tap walks it on`(): Promise<Unit> {
        val town = startInTown()
        val route = Neighbourhood.grid.route(Neighbourhood.venue(Place.HOME).door, Neighbourhood.shop.door)
        val paid = TaxiFare.forDistance(route.zipWithNext { a, b -> distanceBetween(a, b) }.sum())
        return play(
            Step("a taxi toward the bike shop", { town.isStill }) {
                root.button(Transit.CALL_TAXI).click()
                goButton("자전거 가게").click()
                assertEquals(Transport.TAXI, town.travelling)
                assertTrue(money(firstBalance - paid) in hud(), hud())
            },
            Step("on the way, get out", {
                town.travelling != null && town.vehicleAt(Transport.TAXI) != null && town.characterSpot.x > 121.0
            }) {
                root.button("내리기").click()
                assertNull(town.travelling)
                assertTrue(town.isCharacterShown)
                val cell = Neighbourhood.grid.cellAt(town.characterSpot)
                assertTrue(Neighbourhood.grid.isFree(cell), "out at ${town.characterSpot}")
                assertFalse("타는 중" in hud(), hud())
                assertTrue(money(firstBalance - paid) in hud(), "nothing more paid: ${hud()}")
            },
            Step("a tap walks on", { town.isStill }) {
                val here = town.characterSpot
                tapScreen(town, onScreen(town, Point(here.x, 0.0, here.z - 2.0)))
                assertNotNull(town.markerSpot, "no walk after getting out")
            },
        )
    }

    @Test
    fun `a ride while the page is hidden draws nothing, and is over at the first frame back`(): Promise<Unit> {
        val town = startInTown()
        val from = Stop(Transport.WATER_BUS, 0)
        val docks = TransitLines.docks
        val (way, distance) = Journey.roundLoop(TransitLines.boatLoop, docks[0].along, docks[1].along, Journey.GOING_ON)
        val ride = Journey(Transport.WATER_BUS, way, distance, docks[1].wait, Journey.BOAT_SPEED, Journey.BOAT_RAMP)
        var drawn = 0
        return play(
            Step("stand at the café's dock", { town.isStill }) { standAt(town, from, PI / 2) },
            Step("set off, and the page is hidden", { town.isStill }) {
                tapStop(town, from)
                goButton("남쪽 기슭").click()
                town.pageHidden = { true }
                drawn = town.framesDrawn
            },
            Step("hidden past the whole ride", after(ride.millis + 300.0)) {
                assertTrue(town.framesDrawn <= drawn + 1, "drew ${town.framesDrawn - drawn} frames while hidden")
                assertEquals(Transport.WATER_BUS, town.travelling)
                town.pageHidden = { false }
                document.dispatchEvent(Event("visibilitychange"))
            },
            Step("back on screen: out on the far shore", { town.travelling == null }) {
                assertEquals(docks[1].wait, town.characterSpot)
                assertFalse("타는 중" in hud(), hud())
                assertTrue(money(firstBalance - 4) in hud(), hud())
            },
        )
    }

    @Test
    fun `the bus from 웨스트엔드 round the corner to the park, along the streets, on the pass`(): Promise<Unit> {
        val town = startInTown()
        val from = Stop(Transport.BUS, 4)
        val to = TransitLines.busStops[0]
        var seen = false
        return play(
            Step("stand at the stop by 내 방", { town.isStill }) { standAt(town, from, 0.0) },
            Step("tap its shelter", { town.isStill }) {
                tapStop(town, from)
                assertTrue("버스 · 웨스트엔드" in chooser()?.textContent.orEmpty(), screen)
                assertEquals(4, chooser()!!.querySelectorAll(".go").length)
                goButton("공원 입구").click()
                assertEquals(Transport.BUS, town.travelling)
            },
            Step("on the bus", { town.travelling != null && town.vehicleAt(Transport.BUS) != null && town.characterSpot.x < 106.0 }) {
                seen = true
                assertFalse(town.isCharacterShown)
                val bus = assertNotNull(town.vehicleAt(Transport.BUS))
                assertTrue(distanceBetween(bus, town.characterSpot) < 0.01, "the camera follows the bus")
                assertTrue(Neighbourhood.grid.isFree(Neighbourhood.grid.cellAt(bus)))
            },
            Step("out at the park's stop", arrived(town)) {
                assertTrue(seen)
                assertEquals(to.wait, town.characterSpot)
                assertTrue(date(Month.FIRST_DAY) in hud(), hud())
                assertTrue(money(firstBalance) in hud(), hud())
                assertFalse(Transit.riding(Transport.BUS, null) in hud(), hud())
            },
        )
    }

    @Test
    fun `the water bus across False Creek takes $4 once, and the log and the record keep it`(): Promise<Unit> {
        val store = FakeStore()
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { opened() })
        game.slot = SaveSlot(Memory { store })
        game.talkSettleMillis = 0.0
        game.show()
        startSeed1011(root)
        val town = assertNotNull(this.town)
        val from = Stop(Transport.WATER_BUS, 0)
        return play(
            Step("stand at the café's dock", { town.isStill }) { standAt(town, from, PI / 2) },
            Step("tap the dock", { town.isStill }) {
                tapStop(town, from)
                assertTrue("\$4" in goButton("남쪽 기슭").textContent.orEmpty(), screen)
                goButton("남쪽 기슭").click()
                assertEquals(Transport.WATER_BUS, town.travelling)
                assertTrue(money(firstBalance - 4) in hud(), hud())
                assertTrue(Transit.riding(Transport.WATER_BUS, 4) in hud(), hud())
                assertTrue(store.items[SaveSlot.KEY].orEmpty().endsWith("|w4"), store.items[SaveSlot.KEY])
            },
            Step("on the water", {
                town.travelling != null && town.vehicleAt(Transport.WATER_BUS) != null && town.characterSpot.x > 202.0
            }) {
                assertTrue(town.isCharacterShown, "standing on the deck")
                val boat = assertNotNull(town.vehicleAt(Transport.WATER_BUS))
                assertEquals(Terrain.WATER, RealVancouver.map.terrainAt(Neighbourhood.grid.cellAt(boat)))
            },
            Step("out on the far shore", arrived(town)) {
                assertEquals(TransitLines.docks[1].wait, town.characterSpot)
                assertTrue(money(firstBalance - 4) in hud(), "paid once: ${hud()}")
                assertTrue(store.items[SaveSlot.KEY].orEmpty().endsWith("|w4"), store.items[SaveSlot.KEY])
            },
        )
    }

    @Test
    fun `a taxi shows its fare before it is paid, takes it once, and drives to the place`(): Promise<Unit> {
        val town = startInTown()
        val home = Neighbourhood.venue(Place.HOME).door
        val shop = Neighbourhood.shop.door
        val route = Neighbourhood.grid.route(home, shop)
        val fare = TaxiFare.forDistance(route.zipWithNext { a, b -> distanceBetween(a, b) }.sum())
        return play(
            Step("call a taxi at 내 방's door", { town.isStill }) {
                assertEquals(home, town.characterSpot)
                root.button(Transit.CALL_TAXI).click()
                assertTrue(Transit.where(Transport.TAXI) in chooser()?.textContent.orEmpty(), screen)
                // Not to where the character already is.
                assertTrue(chooser()!!.querySelectorAll(".go").asList().none { it.textContent.orEmpty().startsWith("내 방") })
                assertTrue(money(fare) in goButton("자전거 가게").textContent.orEmpty(), "the fare first: $screen")
                assertTrue(money(firstBalance) in hud(), "nothing paid yet")
                goButton("자전거 가게").click()
                assertEquals(Transport.TAXI, town.travelling)
                assertTrue(money(firstBalance - fare) in hud(), hud())
                assertTrue(Transit.riding(Transport.TAXI, fare) in hud(), hud())
            },
            Step("in the taxi", { town.travelling != null && town.vehicleAt(Transport.TAXI) != null && town.characterSpot.x > 125.0 }) {
                assertFalse(town.isCharacterShown)
                assertTrue(Neighbourhood.grid.isFree(Neighbourhood.grid.cellAt(town.characterSpot)))
            },
            Step("out at the shop", arrived(town)) {
                assertEquals(shop, town.characterSpot)
                assertTrue(money(firstBalance - fare) in hud(), "paid once: ${hud()}")
                assertTrue(date(Month.FIRST_DAY) in hud())
            },
        )
    }

    @Test
    fun `with too little left for September, the water bus and the taxi say why and take nothing`(): Promise<Unit> {
        // Rest to the 30th, then a taxi or two down to $42: a $4 fare would leave less than the day's living cost.
        val game = FirstMinute(1011)
        val moves = mutableListOf<vancouvermonth.save.Move>()
        val month = game.startMonth(game.cheapestRoomIndex, game.lowestTotal)
        while (month.day < Month.LAST_DAY) {
            month.play(Choice.Take(vancouvermonth.core.Action.REST))
            moves += Choice.Take(vancouvermonth.core.Action.REST)
        }
        while (month.balance > 42) {
            val amount = minOf(TaxiFare.MOST, month.balance - 42).coerceAtLeast(TaxiFare.BASE)
            if (!month.canSpend(Spending.TAXI_RIDE, amount)) break
            month.spend(Spending.TAXI_RIDE, amount)
            moves += Spend(Spending.TAXI_RIDE, amount)
        }
        assertEquals(42, month.balance)
        val town = continued(MonthRecord(1011, null, game.cheapestRoomIndex, game.lowestTotal, moves))
        val from = Stop(Transport.WATER_BUS, 0)
        return play(
            Step("stand at the café's dock", { town.isStill }) { standAt(town, from, PI / 2) },
            Step("tap the dock", { town.isStill }) {
                tapStop(town, from)
                val button = goButton("남쪽 기슭")
                assertTrue(button.disabled)
                assertTrue(Transit.cannotPay(vancouvermonth.core.SpendBlocked.LIVING_COST) in button.textContent.orEmpty())
                button.click()
                assertNull(town.travelling)
                assertTrue(money(42) in hud(), hud())
                root.button(Transit.STOP).click()
                root.button(Transit.CALL_TAXI).click()
                val taxis = chooser()!!.querySelectorAll(".go").asList().map { it as HTMLButtonElement }
                assertTrue(taxis.isNotEmpty() && taxis.all { it.disabled }, screen)
                assertTrue(money(42) in hud(), hud())
            },
        )
    }

    @Test
    fun `while the bus runs no talk opens and no day can end, and once out a day is spent as ever`(): Promise<Unit> {
        val town = startInTown()
        val from = Stop(Transport.BUS, 4)
        return play(
            Step("stand at the stop by 내 방", { town.isStill }) { standAt(town, from, 0.0) },
            Step("ride toward the park", { town.isStill }) {
                tapStop(town, from)
                goButton("공원 입구").click()
                assertEquals(Transport.BUS, town.travelling)
            },
            Step("on the way, try every way to a talk", { town.travelling != null && town.characterSpot.x < 110.0 }) {
                val name = root.querySelectorAll(".place-name").asList().map { it as HTMLButtonElement }
                    .first { it.textContent == Place.HOME.label }
                name.click()
                town.shopName.click()
                tapScreen(town, FramePoint(0.5, 0.45))
                assertNull(root.querySelector(".talk"), "a talk opened mid-ride: $screen")
                assertFalse(town.isChangingDay)
                assertTrue(date(Month.FIRST_DAY) in hud())
                assertTrue(town.travelling == Transport.BUS)
            },
            Step("out at the park's stop, then home's talk spends the day", arrived(town)) {
                assertFalse(town.isChangingDay)
                val name = root.querySelectorAll(".place-name").asList().map { it as HTMLButtonElement }
                    .first { it.textContent == Place.HOME.label }
                name.click()
                val answer = assertNotNull(root.querySelector(".talk .spend") as? HTMLButtonElement, screen)
                answer.click()
                assertTrue(town.isChangingDay)
            },
        )
    }

    private companion object {
        /**
         * The longest the camera may be found in one place under the ground: a moment. The test browser draws on the
         * computer's processor, a few frames a second at worst, so it is generous; the exact rule is UndergroundRideTest's.
         */
        const val STILL_MILLIS = 600.0
    }
}
