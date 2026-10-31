package vancouvermonth.text

import vancouvermonth.core.SpendBlocked
import vancouvermonth.core.Spending

/**
 * The ways to get about town besides walking and riding a bike: the SkyTrain and the bus, free with the month's pass
 * (already in the room's total), and the water bus and the taxi, which take a [fare] each ride.
 */
enum class Transport(val label: String, val fare: Spending?) {
    SKYTRAIN("스카이트레인", null),
    BUS("버스", null),
    WATER_BUS("수상버스", Spending.WATER_BUS_RIDE),
    TAXI("택시", Spending.TAXI_RIDE),
    ;

    /** True when the month's pass pays for it, so it costs nothing more. */
    val onPass: Boolean
        get() = fare == null
}

/**
 * The words for the SkyTrain, the bus, the water bus and the taxi. The stations, stops and docks are named for their
 * part of town; none is a real station's name, and no line names a real transit or taxi company.
 */
object Transit {
    /** The SkyTrain's stations, west to east along the line: the first is under downtown, the rest up on the line. */
    val STATIONS = listOf("다운타운역", "경기장역", "크릭 끝역", "동쪽역")

    /** The bus stops, in the order the bus comes to them on its loop. */
    val BUS_STOPS = listOf("공원 입구", "쇼핑 거리", "다운타운", "다운타운 남쪽", "웨스트엔드")

    /** The water bus's docks on False Creek. */
    val DOCKS = listOf("카페 앞", "남쪽 기슭", "크릭 끝")

    /** What a ride on the month's pass costs, beside each place to go. */
    const val PASS = "정기권"

    const val CALL_TAXI = "택시 부르기"
    const val STOP = "그만"

    /** The name of stop [index] of [transport]; a taxi has no stops of its own. */
    fun stopName(transport: Transport, index: Int): String = when (transport) {
        Transport.SKYTRAIN -> STATIONS[index]
        Transport.BUS -> BUS_STOPS[index]
        Transport.WATER_BUS -> DOCKS[index]
        Transport.TAXI -> error("A taxi has no stops")
    }

    /** The chooser's title: where the ride starts, "스카이트레인 · 경기장역", or "택시" alone. */
    fun title(transport: Transport, from: String?): String =
        if (from == null) transport.label else "${transport.label} · $from"

    /** The line under the title: where to, and what it costs. */
    fun where(transport: Transport): String = when (transport) {
        Transport.SKYTRAIN, Transport.BUS -> "어디로 갈까요? 정기권이 있어서 요금은 따로 없어요."
        Transport.WATER_BUS -> "어디로 갈까요? 한 번에 ${money(Spending.WATER_BUS_RIDE.price)}예요."
        Transport.TAXI -> "어디로 갈까요? 기본 ${money(Spending.TAXI_RIDE.price)}에 거리만큼 더 나와요."
    }

    /** What a ride to one place costs, beside its name: "정기권", "$4", "$12". */
    fun fare(transport: Transport, amount: Int): String = if (transport.onPass) PASS else money(amount)

    /** What is ridden now, on the top card: "버스 타는 중", or with what was paid, "택시 타는 중 · -$12". */
    fun riding(transport: Transport, paid: Int?): String {
        val now = "${transport.label} 타는 중"
        return if (paid == null) now else "$now · -${money(paid)}"
    }

    /** Why a fare cannot be paid now, beside the place it would go to. */
    fun cannotPay(blocked: SpendBlocked): String =
        if (blocked == SpendBlocked.LIVING_COST) "타면 9월 생활비가 모자라요." else whyNotSpend(blocked)
}
