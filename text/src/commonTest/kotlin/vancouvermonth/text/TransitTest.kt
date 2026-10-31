package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vancouvermonth.core.SpendBlocked
import vancouvermonth.core.Spending

class TransitTest {
    private val every: List<String>
        get() = Transit.STATIONS + Transit.BUS_STOPS + Transit.DOCKS + Transport.entries.flatMap {
            listOf(it.label, Transit.where(it), Transit.riding(it, null), Transit.riding(it, 12))
        } + SpendBlocked.entries.map { Transit.cannotPay(it) } + listOf(Transit.PASS, Transit.CALL_TAXI, Transit.STOP)

    @Test
    fun `the SkyTrain and the bus ride on the pass, and the water bus and the taxi take a fare`() {
        assertTrue(Transport.SKYTRAIN.onPass)
        assertTrue(Transport.BUS.onPass)
        assertNull(Transport.SKYTRAIN.fare)
        assertEquals(Spending.WATER_BUS_RIDE, Transport.WATER_BUS.fare)
        assertEquals(Spending.TAXI_RIDE, Transport.TAXI.fare)
        assertEquals("정기권", Transit.fare(Transport.SKYTRAIN, 0))
        assertEquals("정기권", Transit.fare(Transport.BUS, 0))
        assertEquals("\$4", Transit.fare(Transport.WATER_BUS, 4))
        assertEquals("\$17", Transit.fare(Transport.TAXI, 17))
    }

    @Test
    fun `every station, stop and dock has its own name, and the chooser and the top card say where and what`() {
        assertEquals(4, Transit.STATIONS.size)
        assertEquals(5, Transit.BUS_STOPS.size)
        assertEquals(3, Transit.DOCKS.size)
        for (names in listOf(Transit.STATIONS, Transit.BUS_STOPS, Transit.DOCKS)) assertEquals(names, names.distinct())
        assertEquals("경기장역", Transit.stopName(Transport.SKYTRAIN, 1))
        assertEquals("웨스트엔드", Transit.stopName(Transport.BUS, 4))
        assertEquals("카페 앞", Transit.stopName(Transport.WATER_BUS, 0))
        assertFailsWith<IllegalStateException> { Transit.stopName(Transport.TAXI, 0) }
        assertEquals("스카이트레인 · 경기장역", Transit.title(Transport.SKYTRAIN, "경기장역"))
        assertEquals("택시", Transit.title(Transport.TAXI, null))
        assertEquals("버스 타는 중", Transit.riding(Transport.BUS, null))
        assertEquals("수상버스 타는 중 · -\$4", Transit.riding(Transport.WATER_BUS, 4))
        assertEquals("택시 타는 중 · -\$12", Transit.riding(Transport.TAXI, 12))
        assertTrue("\$4" in Transit.where(Transport.WATER_BUS))
        assertTrue("정기권" in Transit.where(Transport.SKYTRAIN))
        assertEquals("잔고가 모자라요.", Transit.cannotPay(SpendBlocked.NOT_ENOUGH_MONEY))
        assertEquals("타면 9월 생활비가 모자라요.", Transit.cannotPay(SpendBlocked.LIVING_COST))
    }

    @Test
    fun `no line names a real station, transit company or taxi company, and none has an em dash`() {
        val real = listOf(
            "워터프런트", "버라드", "그랜빌", "스타디움", "차이나타운", "메인 스트리트", "사이언스", "커머셜", "엑스포",
            "캐나다 라인", "트랜스링크", "컴퍼스", "아쿠아버스", "페리", "옐로", "캡", "체커", "블랙 탑", "우버", "리프트",
        )
        for (line in every) {
            assertFalse('—' in line, line)
            for (name in real) assertFalse(name in line, "$name in $line")
        }
    }
}
