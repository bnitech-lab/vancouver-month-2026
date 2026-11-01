package vancouvermonth.web

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
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
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.save.MonthRecord
import vancouvermonth.text.Continue
import vancouvermonth.text.Intro
import vancouvermonth.text.money
import vancouvermonth.text.passPrices
import vancouvermonth.text.utilitiesLabel
import vancouvermonth.text.utilitiesPrices

/**
 * Choosing September's room on the whole map: a pin a room at its zone's 내 방, a tap for its card, the guess as
 * before, and the month starting in that room's zone; reached by a keyboard, laid out for a phone, and replayed on a
 * reload.
 */
class RoomMapTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val frames = mutableListOf<HTMLIFrameElement>()
    private val store = FakeStore()

    /** The zone of each town the game asked for, in order. */
    private val zones = mutableListOf<Int>()

    @AfterTest
    fun removeScreen() {
        root.remove()
        frames.forEach { it.remove() }
    }

    /** Opens seed 1011's page in [into] with the month kept in [store] and no town to walk in, on its intro. */
    private fun open(into: HTMLElement = root): PhoneGame {
        into.innerHTML = ""
        val game = PhoneGame(into, 1011, playAgain = {}, makeWalkableTown = { zone ->
            zones += zone
            null
        })
        game.slot = SaveSlot(Memory { store })
        game.show()
        return game
    }

    private fun pins(into: HTMLElement = root): List<HTMLButtonElement> =
        into.querySelectorAll(".room-map .room-pin").asList().map { it as HTMLButtonElement }

    private fun text(selector: String): String =
        assertNotNull(root.querySelector(selector), "No $selector in: ${root.textContent}").textContent.orEmpty()

    /** Guesses [amount] for the picked room, sees the real total and starts September. */
    private fun guessAndStart(amount: Int, into: HTMLElement = root) {
        val input = into.querySelector("#guess") as HTMLInputElement
        input.value = "$amount"
        input.dispatchEvent(Event("input"))
        into.button("총비용 확인하기").click()
        into.button("9월 2일 시작하기").click()
    }

    @Test
    fun `after 시작하기 the rooms are three pins on the whole map, each at its zone's 내 방 door`() {
        open()
        passIntro(root)
        val rooms = FirstMinute(1011).rooms
        val map = assertNotNull(root.querySelector(".rooms .room-map") as? HTMLElement, "No map of the rooms")
        assertNull(root.querySelector("button.room"), "The list of rooms is still there")
        assertEquals("group", map.getAttribute("role"))
        val pins = pins()
        assertEquals(3, pins.size)
        val box = map.getBoundingClientRect()
        val whole = MinimapProjection(box.width, box.height)
        rooms.forEachIndexed { index, room ->
            val door = HomeSite.of(room.zone.number).home.door
            assertEquals(door, Neighbourhood.home(room.zone.number).door)
            val pin = pins[index].getBoundingClientRect()
            val left = pin.left + pin.width / 2 - box.left
            val top = pin.top + pin.height / 2 - box.top
            assertTrue(abs(left - whole.left(door.x, door.z)) < 1.0, "Pin ${index + 1} across: $left")
            assertTrue(abs(top - whole.top(door.x, door.z)) < 1.0, "Pin ${index + 1} down: $top")
            assertEquals("${index + 1}번", pins[index].querySelector(".room-pin-number")?.textContent)
        }
        // The three rooms are in three zones, so three places on the map.
        assertEquals(setOf(1, 2, 3), rooms.map { it.zone.number }.toSet())
        // The map is painted: 내 방's red under each pin, at its door.
        val canvas = map.querySelector("canvas") as HTMLCanvasElement
        assertEquals("true", canvas.getAttribute("aria-hidden"))
        val ratio = canvas.width / box.width
        val paint = canvas.getContext("2d") as CanvasRenderingContext2D
        for (room in rooms) {
            val door = Neighbourhood.home(room.zone.number).door
            val pixel = paint.getImageData(
                (whole.left(door.x, door.z) * ratio).roundToInt().toDouble(),
                (whole.top(door.x, door.z) * ratio).roundToInt().toDouble(),
                1.0,
                1.0,
            ).data.asDynamic()
            val colour = listOf(pixel[0] as Int, pixel[1] as Int, pixel[2] as Int)
            assertEquals(listOf(0xC0, 0x58, 0x4A), colour, "No 내 방 at zone ${room.zone.number}")
        }
    }

    @Test
    fun `회사 and 카페 are named on the map, clear of every pin and of each other`() {
        for (width in listOf(343, 288)) {
            val holder = (document.createElement("div") as HTMLElement).also { it.style.width = "${width}px" }
            root.appendChild(holder)
            val map = RoomMap(FirstMinute(1011).rooms, null, false) {}
            holder.appendChild(map.element)
            map.draw()
            assertEquals(RoomMap.NAMED.toSet(), map.names.keys, "Not both named at $width")
            for ((place, box) in map.names) {
                for (at in map.pins.indices) {
                    val under = RoomMap.covers(box, map.pinLeft[at], map.pinTop[at], RoomMap.PIN / 2.0)
                    assertFalse(under, "$place under pin ${at + 1} at $width")
                }
            }
            val (office, cafe) = RoomMap.NAMED.map { map.names.getValue(it) }
            val overlap = office[0] < cafe[2] && cafe[0] < office[2] && office[1] < cafe[3] && cafe[1] < office[3]
            assertFalse(overlap, "회사 and 카페 overlap at $width")
            holder.remove()
        }
    }

    @Test
    fun `pins that would overlap on a narrow map are pushed apart and stay on it`() {
        val holder = (document.createElement("div") as HTMLElement).also { it.style.width = "160px" }
        root.appendChild(holder)
        val map = RoomMap(FirstMinute(1011).rooms, null, false) {}
        holder.appendChild(map.element)
        map.draw()
        val box = map.element.getBoundingClientRect()
        val whole = MinimapProjection(box.width, box.height)
        val doors = map.doors.map { whole.left(it.x, it.z) to whole.top(it.x, it.z) }
        // At this size two of the doors are closer than a finger.
        val closest = doors.indices.flatMap { a ->
            (a + 1 until doors.size).map { b ->
                hypot(doors[a].first - doors[b].first, doors[a].second - doors[b].second)
            }
        }.min()
        assertTrue(closest < RoomMap.PIN, "The doors are $closest apart")
        for (a in map.pins.indices) {
            for (b in a + 1 until map.pins.size) {
                val apart = hypot(map.pinLeft[a] - map.pinLeft[b], map.pinTop[a] - map.pinTop[b])
                assertTrue(apart >= RoomMap.PIN, "Pins ${a + 1} and ${b + 1} are $apart apart")
            }
            val pin = map.pins[a].getBoundingClientRect()
            assertTrue(pin.left >= box.left - 0.5 && pin.right <= box.right + 0.5, "Pin ${a + 1} off the map across")
            assertTrue(pin.top >= box.top - 0.5 && pin.bottom <= box.bottom + 0.5, "Pin ${a + 1} off the map down")
        }
        holder.remove()
    }

    @Test
    fun `a pin opens its room's card and stays marked, with the formula and the price board still there`() {
        open()
        passIntro(root)
        assertNull(root.querySelector(".room-card"))
        assertEquals("핀을 누르면 그 방의 월세와 교통 구역이 나와요.", text(".room-map-hint"))
        assertEquals("방을 먼저 고르세요.", text(".guess .hint"))
        val rooms = FirstMinute(1011).rooms
        pins()[1].click()
        val card = text(".rooms .room-card")
        val clues = "교통 ${rooms[1].zone.number}구역 · ${utilitiesLabel(rooms[1])}"
        assertEquals("2번 방월세 ${money(rooms[1].rent)}$clues", card)
        assertEquals(listOf("false", "true", "false"), pins().map { it.getAttribute("aria-pressed") })
        assertEquals("", text(".guess .hint"))
        // Another pin, another card.
        pins()[2].click()
        assertTrue("3번 방" in text(".room-card"))
        assertEquals(listOf("false", "false", "true"), pins().map { it.getAttribute("aria-pressed") })
        assertEquals("한 달 총비용 = 월세 + 공과금 + 교통 정기권", text(".formula"))
        assertTrue(passPrices() in text(".board") && utilitiesPrices() in text(".board"))
        // After the reveal no pin can be picked, and the picked one stays marked, with its card.
        val input = root.querySelector("#guess") as HTMLInputElement
        input.value = "1200"
        input.dispatchEvent(Event("input"))
        root.button("총비용 확인하기").click()
        assertTrue(pins().all { it.disabled })
        assertEquals(listOf("false", "false", "true"), pins().map { it.getAttribute("aria-pressed") })
        assertTrue("3번 방" in text(".room-card"))
        assertNotNull(root.querySelector(".reveal .guess-money"))
    }

    @Test
    fun `the room picked on the map starts the month in its zone, with the guess's money in the balance`() {
        val game = FirstMinute(1011)
        for (zone in 1..3) {
            zones.clear()
            open()
            passIntro(root)
            val index = game.rooms.indexOfFirst { it.zone.number == zone }
            pins()[index].click()
            // The rooms screen is 2D: no town is made until the month starts.
            assertEquals(emptyList<Int>(), zones)
            val total = game.rooms[index].totalMonthlyCost
            guessAndStart(total + 30)
            assertEquals(listOf(zone), zones, "The town for zone $zone")
            val balance = FirstMinute.STARTING_BALANCE - total + 70
            assertTrue("잔고${money(balance)}" in text(".topbar"), text(".topbar"))
            assertEquals(index, MonthRecord.decode(store.items[SaveSlot.KEY])?.room, "The room is not kept")
            store.items.clear()
        }
    }

    @Test
    fun `a keyboard reaches every pin in order, each heard as its room in full, and keeps it after picking`() {
        open()
        passIntro(root)
        val rooms = FirstMinute(1011).rooms
        val pins = pins()
        // Every button on the rooms screen, in the order Tab goes, has the three pins one after another.
        val order = root.querySelectorAll("button, input").asList().map { it as HTMLElement }
            .filter { it.tabIndex >= 0 && !(it.asDynamic().disabled as Boolean) }
        val first = order.indexOf(pins[0])
        assertTrue(first >= 0)
        assertEquals(pins, order.subList(first, first + 3))
        rooms.forEachIndexed { index, room ->
            val pin = pins[index]
            assertEquals("button", pin.type)
            assertNull(pin.getAttribute("aria-hidden"))
            assertEquals(
                "${index + 1}번 방, 월세 ${money(room.rent)}, 교통 ${room.zone.number}구역, ${utilitiesLabel(room)}",
                pin.textContent,
            )
            pin.focus()
            assertSame(pin, document.activeElement)
            val box = pin.getBoundingClientRect()
            assertTrue(box.width >= 44.0 && box.height >= 44.0, "Pin ${index + 1} is ${box.width} x ${box.height}")
        }
        // Picked from the keyboard, the screen is drawn again and the focus stays on the same pin.
        pins[2].click()
        val again = pins()[2]
        assertSame(again, document.activeElement)
        assertEquals("true", again.getAttribute("aria-pressed"))
    }

    /** The rooms screen's parts from the top: each card's heading, then the guess form as "guess". */
    private fun order(into: HTMLElement = root): List<String> =
        into.children.asList().filter { it.tagName != "HEADER" }.map {
            if (it.classList.contains("guess")) "guess" else it.querySelector("h2")?.textContent.orEmpty()
        }

    @Test
    fun `the map comes right after what to do, and the price board right before the guess, map or list`() {
        open()
        passIntro(root)
        assertEquals(listOf("할 일", "어느 방?", "요금표", "guess"), order())
        // The card or the hint sits under the map, inside the rooms.
        assertNotNull(root.querySelector(".rooms .room-map + .room-map-hint"))
        pins()[0].click()
        assertNotNull(root.querySelector(".rooms .room-map + .room-card"))
        assertEquals(listOf("할 일", "어느 방?", "요금표", "guess"), order())
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {})
        game.roomsOnMap = false
        game.show()
        passIntro(root)
        assertEquals(listOf("할 일", "어느 방?", "요금표", "guess"), order())
    }

    @Test
    fun `where the map cannot be drawn the rooms are the list, and it plays the same`() {
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { zone ->
            zones += zone
            null
        })
        game.roomsOnMap = false
        game.show()
        passIntro(root)
        assertNull(root.querySelector(".room-map"))
        assertEquals(3, root.querySelectorAll("button.room").length)
        val first = FirstMinute(1011)
        val index = first.rooms.indexOfFirst { it.zone.number == 3 }
        root.button("${index + 1}번 방").click()
        assertEquals("true", root.button("${index + 1}번 방").getAttribute("aria-pressed"))
        guessAndStart(first.rooms[index].totalMonthlyCost)
        assertEquals(listOf(3), zones)
    }

    @Test
    fun `a month started on the map goes on after a reload in the same room's zone`() {
        open()
        passIntro(root)
        val first = FirstMinute(1011)
        val index = first.rooms.indexOfFirst { it.zone.number == 2 }
        pins()[index].click()
        guessAndStart(first.rooms[index].totalMonthlyCost)
        root.button("영어 공부").click()
        val before = text(".topbar")
        assertEquals(listOf(2), zones)

        open()
        assertNotNull(root.querySelector(".intro .kept"), "No month to continue")
        root.button(Continue.CONTINUE).click()
        assertEquals(listOf(2, 2), zones)
        assertEquals(before, text(".topbar"))
        // And 새로 시작 shows the map again.
        open()
        root.button(Continue.NEW).click()
        assertEquals(3, pins().size)
        assertTrue(pins().none { it.getAttribute("aria-pressed") == "true" })
    }

    /** A phone [width] by [height] points with the game's own style sheet, as published, and its #app. */
    private fun phone(width: Int, height: Int): HTMLElement {
        val frame = document.createElement("iframe") as HTMLIFrameElement
        frame.style.width = "${width}px"
        frame.style.height = "${height}px"
        frame.style.border = "0"
        document.body!!.appendChild(frame)
        frames += frame
        val style = js("window.__karma__.config.pageStyle") as String
        val page = frame.contentDocument!!.asDynamic()
        page.open()
        page.write("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><style>$style</style></head><body>")
        page.close()
        val app = document.createElement("main") as HTMLElement
        app.id = "app"
        frame.contentDocument!!.body!!.appendChild(app)
        return app
    }

    /** True when [button], scrolled into sight, is 44 or more and a finger at its middle lands on it, not over it. */
    private fun takesFinger(button: HTMLButtonElement, width: Int, height: Int): Boolean {
        button.scrollIntoView(js("({ block: 'center' })"))
        val box = button.getBoundingClientRect()
        val inside = box.top >= 0 && box.left >= 0 && box.bottom <= height && box.right <= width
        val hit = button.ownerDocument!!.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2)
        return inside && box.width >= 44 && box.height >= 44 && (hit === button || button.contains(hit))
    }

    @Test
    fun `laid out as published, the whole map is on a phone's first screen and every pin and 시작하기 takes a finger`() {
        for ((width, height) in listOf(375 to 812, 320 to 568)) {
            val app = phone(width, height)
            open(into = app)
            val page = app.ownerDocument!!
            assertTrue(takesFinger(app.button(Intro.START), width, height), "시작하기 is covered at $width")
            passIntro(app)
            page.defaultView!!.scrollTo(0.0, 0.0)
            val map = assertNotNull(app.querySelector(".room-map") as? HTMLElement)
            // The whole map is on a 375 x 812 phone's first screen, right after 시작하기.
            val bottom = map.getBoundingClientRect().bottom
            if (width == 375) assertTrue(bottom <= height, "The map ends at $bottom, below the first screen")
            assertTrue(map.getBoundingClientRect().width <= width - 32 + 0.5, "The map is wider than the column")
            assertTrue(page.documentElement!!.scrollWidth <= width, "The rooms scroll sideways at $width x $height")
            for ((index, pin) in pins(app).withIndex()) {
                assertTrue(takesFinger(pin, width, height), "Pin ${index + 1} is covered or small at $width x $height")
            }
            pins(app)[0].click()
            assertTrue(page.documentElement!!.scrollWidth <= width, "The card scrolls sideways at $width x $height")
            val input = app.querySelector("#guess") as HTMLInputElement
            input.value = "1200"
            input.dispatchEvent(Event("input"))
            app.button("총비용 확인하기").click()
            assertTrue(page.documentElement!!.scrollWidth <= width, "The reveal scrolls sideways at $width x $height")
            assertTrue(takesFinger(app.button("9월 2일 시작하기"), width, height), "9월 2일 시작하기 is covered at $width")
        }
        window.scrollTo(0.0, 0.0)
    }
}
