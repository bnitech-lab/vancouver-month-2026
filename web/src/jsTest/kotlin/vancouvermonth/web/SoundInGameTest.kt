package vancouvermonth.web

import kotlin.js.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.text.Intro
import vancouvermonth.text.Passer
import vancouvermonth.text.Transport
import vancouvermonth.text.Weather

/** A town to walk in that draws nothing, whose nights pass at once. */
internal class QuietTown : WalkableTown {
    override val element = (document.createElement("div") as HTMLElement).also { it.className = "walk-town" }
    override var onPlace: (Place?) -> Unit = {}
    override var onPasserby: (Passer) -> Unit = {}
    override var onShop: () -> Unit = {}
    override var onRent: (Ride) -> Unit = {}
    override var onStop: (Stop) -> Unit = {}
    override var onTravelled: () -> Unit = {}
    override var riding: Ride? = null
    override var travelling: Transport? = null

    /** How far a taxi drives, or a shared one rides, to any place, or null when none goes: the test's to set. */
    var taxiSquares: Double? = 30.0

    /** How far to each place in turn, when a test wants them to differ: null where nothing goes. */
    var squaresTo: ((Spot) -> Double?)? = null

    /** False to make the town refuse to ride a shared one off, as when the way there is gone. */
    var canRideNow = true
    override var onFail: () -> Unit = {}
    override var onMorning: () -> Unit = {}
    override var onArrived: (Place) -> Unit = {}
    override val isHome = true

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

    override fun canRideTo(ride: Ride, to: Spot): Boolean = canRideNow && canRide(ride)

    override fun taxiDistance(to: Spot): Double? = squaresTo.let { if (it == null) taxiSquares else it(to) }

    /** False to make the town refuse to set off, as by night or away from the stop. */
    var canTravelNow = true

    /** As the real town: not while already travelling. The character stands for one at any stop. */
    override fun canTravel(transport: Transport, from: Int, to: Int): Boolean =
        canTravelNow && travelling == null && from != to

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

    override fun endDay() {
        riding = null
        onMorning()
    }

    override fun close() {}
}

/** Leaves the intro for the rooms with its 시작하기, when the page shows an intro with nothing kept to continue. */
internal fun passIntro(root: HTMLElement) {
    if (root.querySelector(".intro") != null) root.button(Intro.START).click()
}

/** Starts seed 1011's September in [root]: past the intro, the cheapest room, an exact guess, and 시작하기. */
internal fun startSeed1011(root: HTMLElement) {
    passIntro(root)
    val game = FirstMinute(1011)
    root.button("${game.cheapestRoomIndex + 1}번 방").click()
    val input = root.querySelector("input") as HTMLInputElement
    input.value = "${game.lowestTotal}"
    input.dispatchEvent(Event("input"))
    root.button("총비용 확인하기").click()
    root.button("시작하기").click()
}

internal fun HTMLElement.button(text: String): HTMLButtonElement =
    querySelectorAll("button").asList().map { it as HTMLButtonElement }
        .firstOrNull { text in it.textContent.orEmpty() } ?: error("No button with \"$text\" in: $textContent")

/** The sound switch where the player sees it, and the game's small sounds at the moments they belong to. */
class SoundInGameTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val speaker = NotingSpeaker()
    private val sound = Sound(Memory { FakeStore() }, { speaker })

    @AfterTest
    fun removeScreen() {
        sound.close()
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    private fun switchIn(parent: HTMLElement = root): HTMLButtonElement =
        assertNotNull(parent.querySelector(".sound-switch") as? HTMLButtonElement, "No sound switch: ${parent.innerHTML}")

    @Test
    fun `the switch is on the intro and the day's buttons, and a night's sleep changes the day with a chime`() {
        val game = PhoneGame(root, 1011, playAgain = {})
        game.sound = sound
        game.show()
        assertNotNull(root.querySelector(".title .sound-switch"))
        switchIn().click()
        startSeed1011(root)
        val topbar = assertNotNull(root.querySelector(".topbar") as? HTMLElement)
        assertEquals("소리 끄기", switchIn(topbar).textContent)
        root.button("영어 공부").click()
        assertEquals(listOf("on"), speaker.asked, "an action ends no day")
        root.button("자기").click()
        assertEquals(listOf("on", "chime"), speaker.asked)
    }

    @Test
    fun `in the town the switch is in the top card, a talk opens with a blip and a night's sleep with a chime`() {
        val town = QuietTown()
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { town })
        game.sound = sound
        game.talkSettleMillis = 0.0
        game.show()
        startSeed1011(root)
        val hud = assertNotNull(root.querySelector(".hud") as? HTMLElement)
        switchIn(hud).click()
        assertEquals("소리 끄기", switchIn().textContent)

        // The town tells the game the player turned to the café's person, as a tap on her would.
        town.onPlace(Place.CAFE)
        assertNotNull(root.querySelector(".talk"))
        assertNotNull(root.querySelector(".hud .sound-switch"), "The top card lost its switch when the talk opened")
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        assertEquals(listOf("on", "blip"), speaker.asked.filter { it != "tick" }, "an action ends no day")
        town.onPlace(Place.HOME)
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        assertEquals(listOf("on", "blip", "blip", "chime"), speaker.asked.filter { it != "tick" })
    }

    @Test
    fun `the tune keeps the month's own time while a month is played, and the phone's around it`() {
        val game = PhoneGame(root, 1011, playAgain = {})
        game.sound = sound
        game.show()
        assertNull(sound.gameHour(), "a month's hour on the intro")
        val before = Date().getHours()
        val hour = sound.hour()
        assertTrue(hour == before || hour == Date().getHours(), "not the phone's hour: $hour")
        startSeed1011(root)
        assertEquals(8, sound.hour())
        root.button("영어 공부").click()
        assertEquals(14, sound.hour())
        root.button("카페 알바").click()
        assertEquals(23, sound.hour())
        assertEquals(Mood.NIGHT, moodAt(sound.hour()))
        root.button("자기").click()
        assertEquals(8, sound.hour())
    }

    @Test
    fun `a small talk with a passerby opens with a blip too`() {
        val town = QuietTown()
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { town })
        game.sound = sound
        game.talkSettleMillis = 0.0
        game.show()
        startSeed1011(root)
        switchIn(assertNotNull(root.querySelector(".hud") as? HTMLElement)).click()

        // The town tells the game the player stopped 미나 for a word, as a tap on her would.
        town.onPasserby(Passer.MINA)
        assertNotNull(root.querySelector(".talk"))
        assertEquals(listOf("on", "blip"), speaker.asked.filter { it != "tick" })
    }

    @Test
    fun `a game with no sound has no switch anywhere`() {
        PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { QuietTown() }).show()
        startSeed1011(root)
        assertEquals(null, root.querySelector(".sound-switch"))
    }

    @Test
    fun `after a reload with the sound left on, the first tap still does what it was for`() {
        val store = FakeStore()
        store.setItem("vancouver-month.sound", "on")
        val remembered = Sound(Memory { store }, { speaker })
        try {
            val game = PhoneGame(root, 1011, playAgain = {})
            game.sound = remembered
            game.show()
            assertEquals(emptyList(), speaker.asked)
            root.button(Intro.START).click()
            // The tap woke the sound and still left the intro for the rooms.
            assertEquals(listOf("on"), speaker.asked)
            assertNotNull(root.querySelector(".rooms"))
            root.button("1번 방").click()
            assertEquals("true", root.button("1번 방").getAttribute("aria-pressed"))
        } finally {
            remembered.close()
        }
    }

    @Test
    fun `on a rainy day the top card says so, the town and the sound are told, and people speak of the rain`() {
        val town = QuietTown()
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { town })
        game.sound = sound
        game.talkSettleMillis = 0.0
        // Rain from the second day on; the first day is dry.
        game.weatherOf = { day -> if (day > 2) Weather.RAINY else Weather.SUNNY }
        game.show()
        switchIn().click()
        startSeed1011(root)
        assertEquals(Weather.SUNNY, town.weather)
        assertEquals(false, speaker.rain)
        assertTrue("오늘9월 2일 날씨 맑음" in root.textContent.orEmpty(), root.textContent)

        // The town tells how loud the train and the fire are, and the sound hears it.
        town.onSounds(0.4, 0.2, 0.0)
        assertEquals(listOf(Triple(0.4, 0.2, 0.0)), speaker.nearby)

        // A day of rest at home, after meeting the neighbour, and the next day is rainy.
        town.onPlace(Place.HOME)
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        assertEquals(Weather.RAINY, town.weather)
        assertEquals(true, speaker.rain)
        assertTrue("오늘9월 3일 날씨 비" in root.textContent.orEmpty(), root.textContent)
        val word = assertNotNull(root.querySelector(".hud .weather"))
        assertEquals(" 날씨 비", word.textContent)
        val label = word.querySelector(".visually-hidden")?.textContent
        assertEquals(" 날씨 ", label, "the word has no label for a screen reader")

        town.onPlace(Place.HOME)
        assertEquals("여기 비는 조용히 오지. 금방 익숙해져.", root.querySelector(".talk-line .visually-hidden")?.textContent)
        town.onPasserby(Passer.MINA)
        assertEquals("이것 봐요, 우산 들고 다닌 보람이 있죠?", root.querySelector(".talk-line .visually-hidden")?.textContent)

        // Rested all month, still raining: the ending has no rain.
        while ("다시 하기" !in root.textContent.orEmpty()) {
            town.onPlace(Place.HOME)
            (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        }
        assertEquals(false, speaker.rain, "it still rains at the ending")
    }
}
