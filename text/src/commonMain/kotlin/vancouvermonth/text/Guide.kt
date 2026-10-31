package vancouvermonth.text

/** The sound switch's words: what a tap on it does now. */
fun soundLabel(on: Boolean): String = if (on) "소리 끄기" else "소리 켜기"

/** The card that shows how to play the first time the town opens, and the button that opens it again. */
object Guide {
    const val TITLE = "마을에서 지내는 법"

    /** How to get about and how a day is spent, one short line each. The goal comes after them, from [goalLine]. */
    val LINES = listOf(
        "땅을 누르면 걸어요.",
        "두 번 누르면 빨리 가요.",
        "화면을 끌면 둘러보고, 두 손가락을 오므리고 펴면 멀리·가까이 봐요.",
        "사람이나 건물을 누르면 말을 걸어요. 일 하나를 고르면 하루가 끝나요.",
    )

    const val START = "시작"

    /** The name of the small "?" button that opens the card again. */
    const val HELP = "하는 법 보기"

    /**
     * Where the town's map comes from, in the words the City of Vancouver's open data licence asks for, word for word,
     * wherever its data is shown: the town is traced from that data.
     */
    const val MAP_CREDIT = "Contains information licensed under the Open Government Licence – Vancouver."

    /** The licence's own name, as it reads in [MAP_CREDIT]: on the page it links to [MAP_LICENCE_URL]. */
    const val MAP_LICENCE = "Open Government Licence – Vancouver"

    /** Where the licence can be read, as the licence asks to be linked wherever its statement is shown. */
    const val MAP_LICENCE_URL = "https://opendata.vancouver.ca/pages/licence/"
}
