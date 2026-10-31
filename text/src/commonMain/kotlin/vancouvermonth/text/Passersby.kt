package vancouvermonth.text

/**
 * People who walk through the town and stop for a word, apart from the five who keep a place. A talk with one of them
 * is small talk only: it spends no day. Every name and every line is invented; none of them is a real person, and no
 * line names a real shop or brand.
 */
enum class Passer(val title: String, val role: String) {
    OLIVER("올리버", "조깅하는 사람"),
    MINA("미나", "우산 든 학생"),
    PRIYA("프리야", "공원 산책 중"),
    THEO("테오", "출근길 회사원"),
}

/**
 * What [passer] says when the player stops them: [count] is how many times today, from 0, so the line changes. On a
 * rainy day their first word is about the rain, and the next ones go on as on any day.
 */
fun passerLine(passer: Passer, day: Int, count: Int, weather: Weather = Weather.SUNNY): String {
    if (weather == Weather.RAINY) {
        if (count == 0) return rainLines.getValue(passer)
        return passerLine(passer, day, count - 1)
    }
    val lines = passerLines.getValue(passer)
    return lines[(day + count) % lines.size]
}

/** A few lines each, about everyday life in the town: rain, coffee, the bus, the seawall and the mountains. */
private val passerLines: Map<Passer, List<String>> = mapOf(
    Passer.OLIVER to listOf(
        "바닷가 길은 아침이 제일 좋아요. 산이 다 보여요.",
        "비 와도 뛰어요. 여기선 비가 핑계가 안 돼요.",
        "물개 봤어요? 가끔 물 위로 고개를 내밀어요.",
    ),
    Passer.MINA to listOf(
        "버스가 또 늦어요. 그냥 걸어갈까 봐요.",
        "우산 챙겼어요? 오후에 또 온대요.",
        "내릴 때 기사님한테 고맙다고 해요. 여기 습관이에요.",
    ),
    Passer.PRIYA to listOf(
        "벚꽃은 봄에 또 펴요. 그때도 여기 있어요?",
        "연못 오리들은 사람을 안 무서워해요.",
        "구름 낀 날엔 산이 숨어요. 맑은 날 다시 봐요.",
    ),
    Passer.THEO to listOf(
        "커피 없이는 아침이 안 와요.",
        "한 정거장이라 그냥 걸어요. 산 보면서요.",
        "여기 사람들은 비 와도 모자만 써요. 우산은 여행객이래요.",
    ),
)

/** What each one says first on a rainy day. */
private val rainLines: Map<Passer, String> = mapOf(
    Passer.OLIVER to "비 오는 날엔 바닷가 길이 텅 비어요. 저만 뛰어요.",
    Passer.MINA to "이것 봐요, 우산 들고 다닌 보람이 있죠?",
    Passer.PRIYA to "비가 오면 연못 오리들이 더 신나 해요.",
    Passer.THEO to "이 정도는 비도 아니에요. 모자면 충분해요.",
)
