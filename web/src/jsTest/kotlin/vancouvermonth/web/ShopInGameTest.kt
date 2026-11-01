package vancouvermonth.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import vancouvermonth.core.Action
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Item
import vancouvermonth.core.Month
import vancouvermonth.core.SharedFare
import vancouvermonth.core.Spending
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.save.Move
import vancouvermonth.save.Spend
import vancouvermonth.text.Continue
import vancouvermonth.text.Passer
import vancouvermonth.text.Riding
import vancouvermonth.text.Shop
import vancouvermonth.text.Transit
import vancouvermonth.text.date
import vancouvermonth.text.money

/**
 * The bike shop and riding as the game plays them, in a town that draws nothing: the keeper's talk, buying, getting on
 * and off, renting, the day's log, the kept month, and the button screen.
 */
class ShopInGameTest {
    private val root = (document.createElement("main") as HTMLElement).also { document.body!!.appendChild(it) }
    private val store = FakeStore()
    private val game1011 = FirstMinute(1011)

    /** Seed 1011's balance on its first day, in its cheapest room. */
    private val startBalance = game1011.startMonth(game1011.cheapestRoomIndex, game1011.lowestTotal).balance

    @AfterTest
    fun removeScreen() {
        root.remove()
        for (left in document.querySelectorAll(".walk-town").asList()) (left as HTMLElement).remove()
    }

    /** Opens the page on [store] in [town] (or with buttons for null), as a reload does. */
    private fun open(town: WalkableTown?): PhoneGame {
        root.innerHTML = ""
        val game = PhoneGame(root, 1011, playAgain = {}, makeWalkableTown = { town })
        game.slot = SaveSlot(Memory { store })
        game.talkSettleMillis = 0.0
        game.show()
        return game
    }

    /** Opens the page in [town] with [record] kept, and goes on with it. */
    private fun continued(record: MonthRecord, town: WalkableTown?) {
        store.items[SaveSlot.KEY] = record.encode()
        open(town)
        root.button(Continue.CONTINUE).click()
    }

    private fun text(selector: String): String =
        assertNotNull(root.querySelector(selector), "No $selector in: ${root.textContent}").textContent.orEmpty()

    private fun buyButton(item: Item): HTMLButtonElement = root.querySelectorAll(".talk .buy").asList()
        .map { it as HTMLButtonElement }.single { Shop.buy(item) in it.textContent.orEmpty() }

    private fun hasButton(text: String): Boolean =
        root.querySelectorAll("button").asList().any { text in it.textContent.orEmpty() }

    /** The open card asking where to ride a shared one, or null. */
    private fun rideCard(): HTMLElement? = root.querySelector(".chooser") as? HTMLElement

    /** The button on that card that rides to [place]. */
    private fun rideTo(place: String): HTMLButtonElement =
        assertNotNull(rideCard(), "No card: ${root.textContent}").querySelectorAll(".go").asList()
            .map { it as HTMLButtonElement }
            .firstOrNull { it.querySelector(".action-name")?.textContent == place } ?: error("No $place: ${root.textContent}")

    /** The places on that card, each with its fare as shown. */
    private fun fares(): Map<String, String> =
        assertNotNull(rideCard()).querySelectorAll(".go").asList().associate {
            (it as HTMLElement).querySelector(".action-name")!!.textContent!! to
                it.querySelector(".action-effect")!!.textContent!!
        }

    /** The town reaches a shared [ride], and the player rides it to [place]. */
    private fun rideShared(town: QuietTown, ride: Ride, place: String = "도서관") {
        town.onRent(ride)
        rideTo(place).click()
    }

    /** The places a taxi goes in zone 1, which a shared one goes to too: name and door. */
    private val places = TransitLines.taxiStopsFor(1)

    /** The cheapest room and an exact guess, as [startSeed1011] picks them, for a record to start from. */
    private fun record(vararg moves: Move) =
        MonthRecord(1011, null, game1011.cheapestRoomIndex, game1011.lowestTotal, moves.toList())

    @Test
    fun `the keeper sells a bike for $350 and an e-scooter for $550, and buying spends no day`() {
        val town = QuietTown()
        open(town)
        startSeed1011(root)
        town.onShop()
        val talk = text(".talk")
        assertTrue(Shop.KEEPER in talk && "${Shop.ROLE} · ${Shop.NAME}" in talk, talk)
        assertTrue(Shop.greeting(firstMeeting = true, owned = emptySet()) in talk, talk)
        assertEquals("자전거 사기\$350", buyButton(Item.BIKE).textContent)
        assertEquals("퀵보드 사기\$550", buyButton(Item.E_SCOOTER).textContent)
        assertTrue(hasButton("이야기") && hasButton("그만"))
        assertSame(root.querySelector(".talk"), document.activeElement)
        assertTrue(town.isCovered)

        buyButton(Item.BIKE).click()
        val hud = text(".hud")
        assertTrue(date(Month.FIRST_DAY) in hud && money(startBalance - 350) in hud, hud)
        assertTrue(Shop.bought(Item.BIKE) in text(".talk"))
        // What is owned shows as owned; the other would leave too little for September's 29 days of living cost.
        assertTrue(buyButton(Item.BIKE).disabled)
        assertTrue("이미 있어요." in buyButton(Item.BIKE).textContent.orEmpty())
        assertTrue(buyButton(Item.E_SCOOTER).disabled)
        assertTrue("이걸 사면 9월 생활비가 모자라요." in buyButton(Item.E_SCOOTER).textContent.orEmpty())
        assertTrue(hasButton(Riding.getOn(Item.BIKE)))
        assertFalse(hasButton(Riding.getOn(Item.E_SCOOTER)))
        assertNull(town.riding)

        root.button("이야기").click()
        assertTrue(Shop.smallTalk(0) in text(".talk"))
        root.button("그만").click()
        assertNull(root.querySelector(".talk"))
        town.onShop()
        assertTrue(Shop.greeting(firstMeeting = false, owned = setOf(Item.BIKE)) in text(".talk"))
    }

    @Test
    fun `자전거 타기 gets on, 내리기 gets off, and any talk that opens gets off first`() {
        val town = QuietTown()
        open(town)
        startSeed1011(root)
        town.onShop()
        buyButton(Item.BIKE).click()
        // Getting on ends the talk with the keeper.
        root.button(Riding.getOn(Item.BIKE)).click()
        assertEquals(Ride.BIKE, town.riding)
        assertNull(root.querySelector(".talk"))
        assertTrue(Riding.now(Item.BIKE, shared = false) in text(".hud"))
        assertSame(root.button(Riding.GET_OFF), document.activeElement)

        root.button(Riding.GET_OFF).click()
        assertNull(town.riding)
        assertTrue(hasButton(Riding.getOn(Item.BIKE)) && !hasButton(Riding.GET_OFF))

        root.button(Riding.getOn(Item.BIKE)).click()
        town.onPlace(Place.LIBRARY)
        assertNull(town.riding, "a place's talk")
        assertNotNull(root.querySelector(".talk"))

        root.button(Riding.getOn(Item.BIKE)).click()
        assertNull(root.querySelector(".talk"))
        town.onPasserby(Passer.MINA)
        assertNull(town.riding, "a passerby's talk")

        root.button(Riding.getOn(Item.BIKE)).click()
        town.onShop()
        assertNull(town.riding, "the keeper's talk")
    }

    @Test
    fun `a shared e-scooter asks where to, and its fare is paid once, for the place ridden to`() {
        val town = QuietTown()
        open(town)
        startSeed1011(root)
        town.onRent(Ride.SHARED_E_SCOOTER)
        // Nothing is paid and nothing ridden while the card asks where to.
        assertNull(town.riding)
        assertTrue(money(startBalance) in text(".hud"))
        val card = assertNotNull(rideCard())
        assertTrue(Riding.shared(Item.E_SCOOTER) in text(".chooser .talk-name"), card.textContent)
        assertEquals(Riding.where(SharedFare.E_SCOOTER), text(".chooser .talk-line"))
        assertSame(card, document.activeElement)
        assertTrue(town.isCovered)
        // 30 squares to every place: $1 and $0.60 for every ten, $2.80, to the dollar.
        assertEquals(places.associate { it.first to "\$3" }, fares())
        rideTo("도서관").click()
        assertEquals(Ride.SHARED_E_SCOOTER, town.riding)
        assertNull(rideCard())
        assertTrue(money(startBalance - 3) in text(".hud"))
        assertEquals(Riding.rented(Item.E_SCOOTER, 3), text(".hud-riding"))
        assertTrue(store.items[SaveSlot.KEY].orEmpty().endsWith("|q3"), store.items[SaveSlot.KEY])
        root.button(Riding.GET_OFF).click()
        assertNull(town.riding)
        assertTrue(money(startBalance - 3) in text(".hud"))
        // Nothing to get on again: a shared one is not the player's.
        assertFalse(hasButton(Riding.GET_OFF) || hasButton("타기"))
    }

    @Test
    fun `the fare on the top card is the one paid for the ride under way, and is gone once off it`() {
        val town = QuietTown()
        val game = open(town)
        startSeed1011(root)
        rideShared(town, Ride.SHARED_E_SCOOTER)
        assertEquals(Riding.rented(Item.E_SCOOTER, 3), text(".hud-riding"))
        root.button(Riding.GET_OFF).click()
        // Were a shared one ever ridden without a fare paid for it, no fare is shown, not the last one nor the base.
        town.ride(Ride.SHARED_BIKE)
        game.show()
        assertEquals(Riding.now(Item.BIKE, shared = true), text(".hud-riding"))
    }

    @Test
    fun `each place's fare goes with its way, from the base up to the most, and the fare shown is the fare paid`() {
        val town = QuietTown()
        val ways = mapOf("카페" to 10.0, "도서관" to 100.0, "코워킹" to 400.0, "회사" to 2_000.0)
        town.squaresTo = { spot -> ways[places.first { it.second == spot }.first] }
        open(town)
        startSeed1011(root)
        town.onRent(Ride.SHARED_BIKE)
        // A bike: $1 and $0.40 for every ten squares, at most $15; a place it cannot reach is left out.
        assertEquals(mapOf("카페" to "\$1", "도서관" to "\$5", "코워킹" to "\$15", "회사" to "\$15"), fares())
        assertEquals("공유 자전거", text(".chooser .talk-name"))
        root.button(Transit.STOP).click()
        town.onRent(Ride.SHARED_E_SCOOTER)
        // An e-scooter: $1 and $0.60 for every ten, at most $20.
        assertEquals(mapOf("카페" to "\$2", "도서관" to "\$7", "코워킹" to "\$20", "회사" to "\$20"), fares())
        for ((place, squares) in ways) {
            assertEquals("\$${SharedFare.E_SCOOTER.forDistance(squares)}", fares()[place], place)
        }
        rideTo("도서관").click()
        assertTrue(money(startBalance - 7) in text(".hud"), text(".hud"))
        assertEquals(Riding.rented(Item.E_SCOOTER, 7), text(".hud-riding"))
        assertTrue(store.items[SaveSlot.KEY].orEmpty().endsWith("|q7"), store.items[SaveSlot.KEY])
    }

    @Test
    fun `no fare is paid for a ride not taken: the card closed, the town refusing, or nowhere to go`() {
        val town = QuietTown()
        open(town)
        startSeed1011(root)
        val kept = store.items[SaveSlot.KEY]
        town.onRent(Ride.SHARED_BIKE)
        root.button(Transit.STOP).click()
        assertNull(rideCard())
        assertNull(town.riding)
        // The town will not set off there after all: the button does nothing, and the card stays.
        town.onRent(Ride.SHARED_BIKE)
        town.canRideNow = false
        rideTo("카페").click()
        assertNull(town.riding)
        assertNotNull(rideCard())
        // A talk that opens closes the card, and nothing is ridden.
        town.onPlace(Place.LIBRARY)
        assertNull(rideCard())
        assertNull(town.riding)
        // From where nothing can be reached, the card says so and has nowhere to choose.
        town.canRideNow = true
        town.taxiSquares = null
        town.onRent(Ride.SHARED_E_SCOOTER)
        assertEquals(Riding.NOWHERE, text(".chooser .chooser-empty"))
        assertEquals(emptyMap(), fares())
        assertTrue(money(startBalance) in text(".hud"), text(".hud"))
        assertEquals(kept, store.items[SaveSlot.KEY], "a fare without a ride was kept")
    }

    @Test
    fun `a short balance says why at the shop, and why a shared one cannot be rented`() {
        // 28 days of rest bring 9월 30일; a bike and 106 shared e-scooter rides leave $41, a day's living cost and $1.
        val rest = Choice.Take(Action.REST)
        val bike = Spend(Spending.BIKE, 350)
        val ride = Spend(Spending.SHARED_E_SCOOTER_RIDE, 4)
        val kept = record(*Array(28) { rest }, bike, *Array(106) { ride })
        val month = assertNotNull(kept.replay()).month
        assertEquals(Month.LAST_DAY, month.day)
        assertEquals(41, month.balance)
        // One more ride would have left less than today's living cost, so the record could not hold it.
        assertNull(record(*Array(28) { rest }, bike, *Array(107) { ride }).replay())
        val town = QuietTown()
        continued(kept, town)
        town.onShop()
        assertTrue(buyButton(Item.BIKE).disabled && "이미 있어요." in buyButton(Item.BIKE).textContent.orEmpty())
        assertTrue(buyButton(Item.E_SCOOTER).disabled)
        assertTrue("잔고가 모자라요." in buyButton(Item.E_SCOOTER).textContent.orEmpty())
        root.button("그만").click()

        town.onRent(Ride.SHARED_E_SCOOTER)
        assertNull(town.riding)
        assertNull(rideCard(), "the card opened though no place could be paid for")
        assertEquals("공유 퀵보드를 빌리면 9월 생활비가 모자라요.", text(".hud-ride-note"))
        assertTrue(money(41) in text(".hud"))
        assertEquals(kept.encode(), store.items[SaveSlot.KEY], "a refused rental was kept")
        // Getting on the bike clears the note.
        root.button(Riding.getOn(Item.BIKE)).click()
        assertNull(root.querySelector(".hud-ride-note"))
    }

    @Test
    fun `a place whose fare would leave too little for September's living cost is greyed out, and says why`() {
        // As above, $41 on 9월 30일: one day's living cost and $1 to spend.
        val rest = Choice.Take(Action.REST)
        val ride = Spend(Spending.SHARED_E_SCOOTER_RIDE, 4)
        val kept = record(*Array(28) { rest }, Spend(Spending.BIKE, 350), *Array(106) { ride })
        val town = QuietTown()
        town.squaresTo = { spot -> if (spot == places.first { it.first == "카페" }.second) 10.0 else 100.0 }
        continued(kept, town)
        town.onRent(Ride.SHARED_BIKE)
        // A bike to the café is $1 and can be paid; a $5 ride to the library cannot.
        assertFalse(rideTo("카페").disabled)
        val library = rideTo("도서관")
        assertTrue(library.disabled)
        assertTrue("타면 9월 생활비가 모자라요." in library.textContent.orEmpty(), library.textContent)
        library.click()
        assertNull(town.riding)
        rideTo("카페").click()
        assertEquals(Ride.SHARED_BIKE, town.riding)
        assertTrue(money(40) in text(".hud"), text(".hud"))
    }

    @Test
    fun `on the way to a shared one, getting on the bike means no fare is paid, then or on arrival`() {
        val town = QuietTown()
        open(town)
        startSeed1011(root)
        town.onShop()
        buyButton(Item.BIKE).click()
        root.button(Riding.getOn(Item.BIKE)).click()
        assertEquals(Ride.BIKE, town.riding)
        val kept = store.items[SaveSlot.KEY]
        // The town reaches a shared e-scooter it was sent to before the bike: the game pays nothing.
        town.onRent(Ride.SHARED_E_SCOOTER)
        assertEquals(Ride.BIKE, town.riding)
        assertTrue(money(startBalance - 350) in text(".hud"), text(".hud"))
        assertEquals(kept, store.items[SaveSlot.KEY], "a fare without a ride was kept")
        assertNull(root.querySelector(".hud-ride-note"))
    }

    @Test
    fun `the money spent during a day heads that day's log, and the kept month keeps it`() {
        val town = QuietTown()
        open(town)
        startSeed1011(root)
        town.onShop()
        buyButton(Item.BIKE).click()
        root.button("그만").click()
        rideShared(town, Ride.SHARED_E_SCOOTER)
        assertTrue(store.items[SaveSlot.KEY].orEmpty().endsWith("|b350q3"), store.items[SaveSlot.KEY])
        town.onPlace(Place.LIBRARY)
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        val log = root.querySelectorAll(".log li").asList().map { it.textContent }
        assertEquals("자전거를 샀어요. -\$350", log[0])
        assertEquals("공유 퀵보드를 빌렸어요. -\$3", log[1])
        assertTrue(log[2].orEmpty().startsWith("영어 실력이 올랐어요."), "$log")
        assertTrue(money(startBalance - 350 - 3 - Month.DAILY_LIVING_COST) in log[3].orEmpty(), "$log")
        assertNull(root.querySelector(".hud-riding"), "the ride outlived its day")
        assertNull(town.riding, "the night ended the ride")
        val kept = record(
            Spend(Spending.BIKE, 350),
            Spend(Spending.SHARED_E_SCOOTER_RIDE, 3),
            Choice.Take(Action.STUDY_ENGLISH),
        )
        assertEquals(kept.encode(), store.items[SaveSlot.KEY])
    }

    @Test
    fun `a busy day's spends take two lines of the next day's log`() {
        val town = QuietTown()
        open(town)
        startSeed1011(root)
        for (ride in listOf(Ride.SHARED_BIKE, Ride.SHARED_E_SCOOTER, Ride.SHARED_BIKE, Ride.SHARED_BIKE)) {
            rideShared(town, ride)
            root.button(Riding.GET_OFF).click()
        }
        town.onPlace(Place.HOME)
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        val log = root.querySelectorAll(".log li").asList().map { it.textContent }
        // 30 squares each: $2 a bike, $3 an e-scooter.
        assertEquals(listOf("공유 자전거를 빌렸어요. -\$2", "그 밖에 3건 -\$7"), log.take(2))
        assertEquals(4, log.size, "$log")
    }

    @Test
    fun `continued after a purchase, the bike is still the player's, and today's spends still head today's log`() {
        open(QuietTown())
        startSeed1011(root)
        val first = QuietTown()
        open(first)
        // The month kept at its start is offered; go on with it in a fresh town, and buy there.
        root.button(Continue.CONTINUE).click()
        first.onShop()
        buyButton(Item.BIKE).click()

        val again = QuietTown()
        open(again)
        root.button(Continue.CONTINUE).click()
        assertTrue(money(startBalance - 350) in text(".hud"))
        root.button(Riding.getOn(Item.BIKE)).click()
        assertEquals(Ride.BIKE, again.riding)
        again.onShop()
        assertTrue(buyButton(Item.BIKE).disabled)
        root.button("그만").click()
        again.onPlace(Place.CAFE)
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        assertEquals("자전거를 샀어요. -\$350", root.querySelector(".log li")?.textContent)
    }

    @Test
    fun `on the button screen there is no shop and no riding, even with a bike bought`() {
        continued(record(Spend(Spending.BIKE, 350)), town = null)
        assertNotNull(root.querySelector(".actions"))
        assertTrue(money(startBalance - 350) in text(".topbar"))
        assertFalse(Shop.NAME in root.textContent.orEmpty())
        assertFalse(hasButton(Riding.getOn(Item.BIKE)))
    }
}
