package vancouvermonth.text

/** The sound switch's words: what a tap on it does now. */
fun soundLabel(on: Boolean): String = if (on) "소리 끄기" else "소리 켜기"

/**
 * What a screen reader hears for the small map in the town's corner, which is a picture: roughly where the character
 * is, by the name of the place or station it is nearest; "근처" when it is close by, and only which way ("쪽") when
 * the nearest is still far off.
 */
fun minimapLabel(nearest: String, near: Boolean = true): String =
    if (near) "지도: $nearest 근처" else "지도: $nearest 쪽"

/**
 * How to play: on the intro, the page's first screen, and on the card the "?" in the town opens, with the button that
 * opens it.
 */
object Guide {
    const val TITLE = "마을에서 지내는 법"

    /**
     * How to get about, how a day is spent, the rides and the small map, one short line each. On the card the goal
     * comes after them, from [goalLine].
     */
    val LINES = listOf(
        "땅을 누르면 걷고, 두 번 누르면 빨리 가요.",
        "화면을 끌면 둘러보고, 두 손가락을 벌리면 가까이, 모으면 멀리 봐요.",
        "사람이나 건물을 누르면 말을 걸어요. 일 하나를 고르면 하루가 끝나요.",
        "스카이트레인 역, 버스 정류장, 수상버스 선착장을 누르면 타요.",
        "자전거는 가게에서 사거나 길에서 빌리고, 택시는 위 카드로 불러요.",
        "오른쪽 위 작은 지도는 내 주변을, 누르면 지도 전체를 보여요.",
    )

    /** The card's button: it opens mid-month from the "?", so it closes the card rather than starts anything. */
    const val CLOSE = "닫기"

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

    /**
     * The wide map's Burnaby part comes from the City of Burnaby's open data, which asks for this statement besides
     * [MAP_CREDIT], word for word: the default statement of the Open Government Licence – Burnaby. It names British
     * Columbia, as the provincial licence it is based on does; that is the licence's own wording, not a slip. The page
     * shows it next to [MAP_CREDIT], as [BURNABY_MAP_LINE].
     */
    const val BURNABY_MAP_CREDIT =
        "Contains information licensed under the Open Government Licence – British Columbia."

    /** The source the Burnaby datasets credit. */
    const val BURNABY_MAP_SOURCE = "City of Burnaby"

    /** The City of Burnaby's name for its licence: on the page it links to [BURNABY_MAP_LICENCE_URL]. */
    const val BURNABY_MAP_LICENCE = "Open Government Licence – Burnaby"

    /** Where that licence can be read, as it asks to be linked where possible. */
    const val BURNABY_MAP_LICENCE_URL = "https://data.burnaby.ca/pages/open-government-licence"

    /**
     * The Burnaby line on the page, next to [MAP_CREDIT]: the statement, then the source and the licence's name, which
     * links to [BURNABY_MAP_LICENCE_URL].
     */
    const val BURNABY_MAP_LINE = "$BURNABY_MAP_CREDIT ($BURNABY_MAP_SOURCE, $BURNABY_MAP_LICENCE)"

    /** What a screen reader hears after a licence link's name: the link opens in a new tab, and the game stays. */
    const val NEW_TAB = "(새 탭에서 열려요)"
}
