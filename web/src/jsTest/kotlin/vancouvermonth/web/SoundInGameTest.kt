package vancouvermonth.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.text.Passer
import vancouvermonth.text.Weather

/** A town to walk in that draws nothing, whose nights pass at once. */
internal class QuietTown : WalkableTown {
    override val element = (document.createElement("div") as HTMLElement).also { it.className = "walk-town" }
    override var onPlace: (Place?) -> Unit = {}
    override var onPasserby: (Passer) -> Unit = {}
    override var onShop: () -> Unit = {}
    override var onRent: (Ride) -> Unit = {}
    override var riding: Ride? = null
    override var onFail: () -> Unit = {}
    override var onMorning: () -> Unit = {}
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

    override fun endDay(place: Place) {
        riding = null
        onMorning()
    }

    override fun close() {}
}

/** Starts seed 1011's September in [root]: the cheapest room, an exact guess, and 시작하기. */
internal fun startSeed1011(root: HTMLElement) {
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
    fun `the switch is on the room screen and the day's buttons, and a day changes with a chime`() {
        val game = PhoneGame(root, 1011, playAgain = {})
        game.sound = sound
        game.show()
        assertNotNull(root.querySelector(".title .sound-switch"))
        switchIn().click()
        startSeed1011(root)
        val topbar = assertNotNull(root.querySelector(".topbar") as? HTMLElement)
        assertEquals("소리 끄기", switchIn(topbar).textContent)
        root.button("영어 공부").click()
        assertEquals(listOf("on", "chime"), speaker.asked)
    }

    @Test
    fun `in the town the switch is in the top card, a talk opens with a blip and a day ends with a chime`() {
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
        assertEquals(listOf("on", "blip", "chime"), speaker.asked.filter { it != "tick" })
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
            root.button("1번 방").click()
            // The tap woke the sound and still picked the room.
            assertEquals(listOf("on"), speaker.asked)
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
