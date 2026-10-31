package vancouvermonth.text

/** The card that offers, on opening the page, the month kept from an earlier visit. */
object Continue {
    const val TITLE = "하던 한 달이 있어요."
    const val CONTINUE = "이어하기"
    const val NEW = "새로 시작"
    const val NOTE = "새로 시작하면 하던 한 달은 지워져요."
}

/** The day the kept month goes on from, and its balance: "9월 7일부터 · 잔고 $1,455". */
fun continueFrom(day: Int, balance: Int): String = "${date(day)}부터 · 잔고 ${money(balance)}"

/** On another run's start card, that starting it drops the month kept on [day]: "시작하면 이어하던 9월 14일의 한 달은 지워져요." */
fun replacesKept(day: Int): String = "시작하면 이어하던 ${date(day)}의 한 달은 지워져요."
