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
import vancouvermonth.core.Spending
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.save.Move
import vancouvermonth.save.Spend
import vancouvermonth.text.Continue
import vancouvermonth.text.Passer
import vancouvermonth.text.Riding
import vancouvermonth.text.Shop
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
    private val startBalance = game1011.startMonth(game1011.cheapestRoomIndex).balance

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
        assertEquals("자전거를 샀어요. -\$350", text(".hud-spent"))
        assertTrue(Shop.bought(Item.BIKE) in text(".talk"))
        // What is owned shows as owned; the other can still be bought.
        assertTrue(buyButton(Item.BIKE).disabled)
        assertTrue("이미 있어요." in buyButton(Item.BIKE).textContent.orEmpty())
        assertFalse(buyButton(Item.E_SCOOTER).disabled)
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
    fun `a shared e-scooter's fare is paid once as the character gets on it`() {
        val town = QuietTown()
        open(town)
        startSeed1011(root)
        town.onRent(Ride.SHARED_E_SCOOTER)
        assertEquals(Ride.SHARED_E_SCOOTER, town.riding)
        assertTrue(money(startBalance - 4) in text(".hud"))
        assertEquals("공유 퀵보드를 빌렸어요. -\$4", text(".hud-spent"))
        assertTrue(Riding.now(Item.E_SCOOTER, shared = true) in text(".hud"))
        root.button(Riding.GET_OFF).click()
        assertNull(town.riding)
        assertTrue(money(startBalance - 4) in text(".hud"))
        // Nothing to get on again: a shared one is not the player's.
        assertFalse(hasButton(Riding.GET_OFF) || hasButton("타기"))
    }

    @Test
    fun `a short balance says why at the shop, and why a shared one cannot be rented`() {
        // 28 days of rest bring 9월 30일; a bike and 91 shared e-scooter rides leave a dollar.
        val rest = Choice.Take(Action.REST)
        val bike = Spend(Spending.BIKE, 350)
        val ride = Spend(Spending.SHARED_E_SCOOTER_RIDE, 4)
        val kept = record(*Array(28) { rest }, bike, *Array(91) { ride })
        val month = assertNotNull(kept.replay()).month
        assertEquals(Month.LAST_DAY, month.day)
        assertEquals(1, month.balance)
        val town = QuietTown()
        continued(kept, town)
        town.onShop()
        assertTrue(buyButton(Item.BIKE).disabled && "이미 있어요." in buyButton(Item.BIKE).textContent.orEmpty())
        assertTrue(buyButton(Item.E_SCOOTER).disabled)
        assertTrue("잔고가 모자라요." in buyButton(Item.E_SCOOTER).textContent.orEmpty())
        root.button("그만").click()

        town.onRent(Ride.SHARED_E_SCOOTER)
        assertNull(town.riding)
        assertEquals("공유 퀵보드를 빌리지 못했어요. 잔고가 모자라요.", text(".hud-spent"))
        assertTrue(money(1) in text(".hud"))
        assertEquals(kept.encode(), store.items[SaveSlot.KEY], "a refused rental was kept")
    }

    @Test
    fun `the money spent during a day heads that day's log, and the kept month keeps it`() {
        val town = QuietTown()
        open(town)
        startSeed1011(root)
        town.onShop()
        buyButton(Item.BIKE).click()
        root.button("그만").click()
        town.onRent(Ride.SHARED_E_SCOOTER)
        assertTrue(store.items[SaveSlot.KEY].orEmpty().endsWith("|b350q4"), store.items[SaveSlot.KEY])
        town.onPlace(Place.LIBRARY)
        (root.querySelector(".talk .spend") as HTMLButtonElement).click()
        val log = root.querySelectorAll(".log li").asList().map { it.textContent }
        assertEquals("자전거를 샀어요. -\$350", log[0])
        assertEquals("공유 퀵보드를 빌렸어요. -\$4", log[1])
        assertTrue(log[2].orEmpty().startsWith("영어 실력이 올랐어요."), "$log")
        assertTrue(money(startBalance - 350 - 4 - Month.DAILY_LIVING_COST) in log[3].orEmpty(), "$log")
        assertNull(root.querySelector(".hud-spent"), "the last spend outlived its day")
        assertNull(town.riding, "the night ended the ride")
        val kept = record(
            Spend(Spending.BIKE, 350),
            Spend(Spending.SHARED_E_SCOOTER_RIDE, 4),
            Choice.Take(Action.STUDY_ENGLISH),
        )
        assertEquals(kept.encode(), store.items[SaveSlot.KEY])
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
