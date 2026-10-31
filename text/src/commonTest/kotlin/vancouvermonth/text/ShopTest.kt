package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import vancouvermonth.core.Item
import vancouvermonth.core.SpendBlocked
import vancouvermonth.core.Spending

class ShopTest {
    /** Every line the shop and riding can show. */
    private val lines: List<String> = buildList {
        add(Shop.NAME)
        add(Shop.KEEPER)
        add(Shop.ROLE)
        for (first in listOf(true, false)) {
            for (owned in listOf(emptySet(), setOf(Item.BIKE))) add(Shop.greeting(first, owned))
        }
        for (item in Item.entries) {
            add(Shop.buy(item))
            add(Shop.bought(item))
            add(Riding.getOn(item))
            for (shared in listOf(false, true)) add(Riding.now(item, shared))
        }
        repeat(4) { add(Shop.smallTalk(it)) }
        add(Riding.GET_OFF)
        for (kind in listOf(Spending.SHARED_BIKE_RIDE, Spending.SHARED_E_SCOOTER_RIDE)) {
            for (blocked in SpendBlocked.entries) add(Riding.cannotRent(kind, blocked))
        }
    }

    @Test
    fun `the shop is a bike shop kept by 벤, and the buttons say what they buy and ride`() {
        assertEquals("자전거 가게", Shop.NAME)
        assertEquals("벤", Shop.KEEPER)
        assertEquals("자전거 가게 주인", Shop.ROLE)
        assertEquals(listOf("자전거 사기", "퀵보드 사기"), Item.entries.map { Shop.buy(it) })
        assertEquals(listOf("자전거 타기", "퀵보드 타기"), Item.entries.map { Riding.getOn(it) })
        assertEquals("내리기", Riding.GET_OFF)
    }

    @Test
    fun `the keeper greets a newcomer, a visitor and a rider differently`() {
        val greetings = setOf(
            Shop.greeting(firstMeeting = true, owned = emptySet()),
            Shop.greeting(firstMeeting = false, owned = emptySet()),
            Shop.greeting(firstMeeting = false, owned = setOf(Item.E_SCOOTER)),
        )
        assertEquals(3, greetings.size)
        assertTrue(Shop.KEEPER in Shop.greeting(firstMeeting = true, owned = emptySet()))
    }

    @Test
    fun `after a purchase the keeper names the button that gets on it`() {
        for (item in Item.entries) assertTrue(Riding.getOn(item) in Shop.bought(item), "$item")
    }

    @Test
    fun `small talk goes round, and tells where the shared ones are`() {
        val talk = (0 until 4).map { Shop.smallTalk(it) }
        assertEquals(4, talk.toSet().size)
        assertEquals(talk[0], Shop.smallTalk(4))
        assertTrue("거치대" in talk[0] && "길모퉁이" in talk[0], talk[0])
    }

    @Test
    fun `riding says what is ridden, and a refused rental says why`() {
        assertEquals("자전거 타는 중", Riding.now(Item.BIKE, shared = false))
        assertEquals("공유 퀵보드 타는 중", Riding.now(Item.E_SCOOTER, shared = true))
        assertEquals(
            "공유 퀵보드를 빌리지 못했어요. 잔고가 모자라요.",
            Riding.cannotRent(Spending.SHARED_E_SCOOTER_RIDE, SpendBlocked.NOT_ENOUGH_MONEY),
        )
        assertNotEquals(
            Riding.cannotRent(Spending.SHARED_BIKE_RIDE, SpendBlocked.NOT_ENOUGH_MONEY),
            Riding.cannotRent(Spending.SHARED_E_SCOOTER_RIDE, SpendBlocked.NOT_ENOUGH_MONEY),
        )
    }

    @Test
    fun `no line is blank or has an em dash`() {
        for (line in lines) {
            assertTrue(line.isNotBlank())
            assertFalse('\u2014' in line, line)
        }
    }
}
