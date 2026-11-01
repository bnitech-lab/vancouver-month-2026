package vancouvermonth.text

import vancouvermonth.core.Item
import vancouvermonth.core.SharedFare
import vancouvermonth.core.SpendBlocked
import vancouvermonth.core.Spending

/**
 * The bike shop in the town and its keeper. The shop, the keeper and every line are invented: none of them is a real
 * shop, brand or person, and no line names a bike-share or scooter company.
 */
object Shop {
    const val NAME = "자전거 가게"
    const val KEEPER = "벤"
    const val ROLE = "자전거 가게 주인"

    /** The keeper's first word: on meeting the player, before they own anything, and once they do. */
    fun greeting(firstMeeting: Boolean, owned: Set<Item>): String = when {
        firstMeeting -> "어서 와요, 벤이에요. 자전거 한 대면 동네가 훨씬 가까워져요."
        owned.isEmpty() -> "또 왔네요. 오늘은 한 대 장만할래요?"
        else -> "잘 타고 다녀요? 타이어 바람 빠지면 언제든 들러요."
    }

    /** The button that buys [item]. Its price goes beside it. */
    fun buy(item: Item): String = when (item) {
        Item.BIKE -> "자전거 사기"
        Item.E_SCOOTER -> "퀵보드 사기"
    }

    /** What the keeper says as the player buys [item]: where to get on it. */
    fun bought(item: Item): String = when (item) {
        Item.BIKE -> "좋은 자전거예요. 위 카드에서 '${Riding.getOn(item)}'를 누르면 바로 타요."
        Item.E_SCOOTER -> "충전은 꽉 채워 뒀어요. 위 카드에서 '${Riding.getOn(item)}'를 누르면 돼요."
    }

    /** Small talk, [count] from 0: one line after another, and round again. */
    fun smallTalk(count: Int): String = SMALL_TALK[count % SMALL_TALK.size]

    private val SMALL_TALK = listOf(
        "공유 자전거는 거치대에서, 공유 퀵보드는 길모퉁이에서 빌려요. 가는 거리만큼 요금이 나가요.",
        "한 번 사면 9월 내내 공짜로 타요. 걷는 것보다 두세 배는 빨라요.",
        "비 오는 날 바닷가 길은 미끄러워요. 천천히 타요.",
        "누구랑 이야기를 시작하면 바로 내려서 걸어가요. 그게 이 동네 예의예요.",
    )
}

/** The words for riding, on the town's top card. */
object Riding {
    const val GET_OFF = "내리기"

    /** The button that gets on the player's own [item]. */
    fun getOn(item: Item): String = when (item) {
        Item.BIKE -> "자전거 타기"
        Item.E_SCOOTER -> "퀵보드 타기"
    }

    /** What the character is riding now: its own bike or e-scooter, or a [shared] one rented for the ride. */
    fun now(vehicle: Item, shared: Boolean): String = "${name(vehicle, shared)} 타는 중"

    /** A shared one being ridden, with the [fare] it cost: "공유 퀵보드 타는 중 · -$4". */
    fun rented(vehicle: Item, fare: Int): String = "${now(vehicle, shared = true)} · -${money(fare)}"

    /** The title of the card that asks where to ride a shared [vehicle]: "공유 자전거". */
    fun shared(vehicle: Item): String = name(vehicle, shared = true)

    /** The line under it: what a ride costs, "어디로 갈까요? 기본 $1에 거리만큼 더 나와요. 최대 $15.". */
    fun where(fare: SharedFare): String =
        "어디로 갈까요? 기본 ${money(fare.base)}에 거리만큼 더 나와요. 최대 ${money(fare.most)}."

    /** On that card when no place can be ridden to from where the character stands. */
    const val NOWHERE = "지금은 타고 갈 곳이 없어요."

    /** Why a shared bike or e-scooter could not be rented for [kind]'s fare. */
    fun cannotRent(kind: Spending, blocked: SpendBlocked): String {
        val vehicle = if (kind == Spending.SHARED_BIKE_RIDE) Item.BIKE else Item.E_SCOOTER
        val shared = name(vehicle, shared = true)
        if (blocked == SpendBlocked.LIVING_COST) return "${shared}를 빌리면 9월 생활비가 모자라요."
        return "${shared}를 빌리지 못했어요. ${whyNotSpend(blocked)}"
    }

    private fun name(vehicle: Item, shared: Boolean): String {
        val thing = if (vehicle == Item.BIKE) "자전거" else "퀵보드"
        return if (shared) "공유 $thing" else thing
    }
}
