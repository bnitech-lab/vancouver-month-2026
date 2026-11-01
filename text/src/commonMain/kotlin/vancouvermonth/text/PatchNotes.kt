package vancouvermonth.text

import vancouvermonth.core.TodaysRun

/**
 * One update as players read it in the patch notes (업데이트 소식): its version, the day it came out in Vancouver as
 * "2026-10-31", a one-line headline, and what changed in short lines addressed to players. A section with no lines is
 * left out.
 */
class PatchNote(
    val version: String,
    val date: String,
    val headline: String,
    val added: List<String> = emptyList(),
    val improved: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
    val known: List<String> = emptyList(),
) {
    /** The sections that have lines, by name, in the order the notes show them. */
    val sections: List<Pair<String, List<String>>>
        get() = listOf(
            PatchNotes.ADDED to added,
            PatchNotes.IMPROVED to improved,
            PatchNotes.FIXED to fixed,
            PatchNotes.KNOWN to known,
        ).filter { it.second.isNotEmpty() }
}

/**
 * The game's patch notes, the one source for the card in the game and for PATCH_NOTES.md. Every change players can
 * see adds one [PatchNote] at the top of [ALL] and raises [CURRENT] to its version.
 */
object PatchNotes {
    /** The newest version, shown by the title; it is [ALL]'s first entry. */
    const val CURRENT = "0.14"

    /** Every update, newest first. */
    val ALL: List<PatchNote> = listOf(
        PatchNote(
            version = "0.14",
            date = "2026-10-31",
            headline = "인트로와 총비용 짐작 보상",
            added = listOf(
                "첫 화면이 인트로가 됐어요. 이야기와 하는 법을 읽고 '시작하기'를 눌러요.",
                "방의 한 달 총비용을 맞힐수록 최대 $100을 더 받고 9월을 시작해요.",
                "$100보다 더 틀리면 그만큼 잃어요. 잃는 돈은 $300까지예요.",
                "딱 맞히면 +$100, $100 틀리면 $0, $250 틀리면 -$150이에요.",
                "총비용을 확인하면 받거나 잃은 돈과 9월을 시작하는 잔고가 나와요.",
            ),
            improved = listOf(
                "점수는 없어졌어요. 결과와 오늘의 판 한 줄에 받거나 잃은 돈이 나와요.",
                "하던 한 달은 인트로 맨 아래 '이어하기'로 이어서 해요.",
                "마을의 안내 카드는 저절로 뜨지 않아요. '?'를 누르면 열려요.",
                "안내 카드에 스카이트레인, 버스, 수상버스, 택시, 미니맵 설명이 생겼어요.",
                "'다시 하기'를 누르면 인트로 없이 새 판의 방 고르기로 가요.",
            ),
            known = listOf(
                "이번 업데이트 전에 하던 한 달은 이어할 수 없어요. 새로 시작해 주세요.",
            ),
        ),
        PatchNote(
            version = "0.13",
            date = "2026-10-31",
            headline = "스카이트레인·버스·수상버스·택시",
            added = listOf(
                "스카이트레인 역이 네 곳이 됐어요. 다운타운역은 땅 밑에 있어요.",
                "역 건물을 누르면 걸어가서 갈 역을 골라요. 기차가 그 역까지 데려다줘요.",
                "버스가 웨스트엔드와 다운타운을 돌아요. 정류장 다섯 곳에서 타요.",
                "정류장을 누르고 내릴 정류장을 고르면 버스가 길을 따라가요.",
                "스카이트레인과 버스는 정기권으로 타서 요금이 따로 없어요.",
                "폴스 크릭에 수상버스 선착장이 셋 있어요. 한 번에 \$4예요.",
                "위 카드의 '택시 부르기'로 다섯 곳, 자전거 가게, 역 어디든 가요.",
                "택시는 기본 \$4에 거리만큼 더 나와요. 요금을 먼저 보여 줘요.",
                "타고 가는 동안에는 말을 걸 수 없고, 하루도 지나지 않아요.",
                "택시는 가는 길에 '내리기'로 내릴 수 있어요.",
                "미니맵에 역, 버스 정류장, 선착장, 자전거 가게가 작게 보여요.",
            ),
            improved = listOf(
                "지난번에 하던 한 달도 그대로 이어할 수 있어요.",
            ),
        ),
        PatchNote(
            version = "0.12",
            date = "2026-10-31",
            headline = "자전거 가게와 자전거·퀵보드",
            added = listOf(
                "다운타운 가는 길에 자전거 가게가 생겼어요. 주인 벤에게 말을 걸어 보세요.",
                "자전거는 $350, 퀵보드는 $550에 살 수 있어요. 사도 하루가 지나지 않아요.",
                "산 뒤에는 위 카드에서 바로 타고, '내리기'로 내려요.",
                "자전거는 걷기보다 두 배 넘게, 퀵보드는 세 배쯤 빨라요.",
                "내 방, 도서관, 카페, 역 근처에 공유 자전거가 있어요. 한 번에 $3이에요.",
                "길모퉁이에 선 공유 퀵보드는 한 번에 $4예요. 누르면 걸어가서 타요.",
                "말을 걸거나 하루가 끝나면 내려요. 공유한 건 제자리로 돌아가요.",
                "그날 쓴 돈은 다음 날 기록 맨 위에 나와요.",
                "사고 나면 9월 생활비가 모자라게 되는 물건은 살 수 없어요.",
            ),
            known = listOf(
                "이번 업데이트 전에 하던 한 달은 이어할 수 없어요. 새로 시작해 주세요.",
            ),
        ),
        PatchNote(
            version = "0.11",
            date = "2026-10-31",
            headline = "업데이트 소식을 게임 안에서",
            added = listOf(
                "첫 화면에 지금 버전과 '업데이트 소식' 버튼이 생겼어요.",
                "지금까지 바뀐 내용을 버전별로 볼 수 있어요.",
                "새 소식이 있으면 버튼에 작은 점이 떠요. 한 번 열어 보면 사라져요.",
                "마을에서는 '?' 안내 카드 맨 아래에서 열 수 있어요.",
            ),
            improved = listOf(
                "미니맵이 실제 지도처럼 북쪽을 위로 두고, 위쪽에 N 표시가 생겼어요.",
                "방향이 바뀐 미니맵에도 걸어갈 수 있는 곳이 모두 들어 있어요.",
            ),
        ),
        PatchNote(
            version = "0.10",
            date = "2026-10-31",
            headline = "미니맵과 비켜 주는 건물",
            added = listOf(
                "마을 오른쪽 위에 미니맵이 생겼어요.",
                "미니맵에 내 위치와 바라보는 쪽, 다섯 장소가 보여요.",
                "미니맵을 누르면 크게 보이고 장소 이름도 나와요.",
                "면접 날에는 미니맵에서도 회사가 초록색이에요.",
            ),
            improved = listOf(
                "주인공을 가리는 건물은 잠깐 낮게 접혔다가, 지나가면 다시 일어서요.",
            ),
            fixed = listOf(
                "다운타운에서 카메라가 건물에 밀려 갑자기 다가오던 문제를 고쳤어요.",
            ),
            known = listOf(
                "일부 휴대폰에서는 다운타운을 걸을 때 화면이 조금 끊길 수 있어요.",
            ),
        ),
        PatchNote(
            version = "0.9",
            date = "2026-10-31",
            headline = "진짜 밴쿠버 지도 위의 마을",
            added = listOf(
                "마을을 실제 밴쿠버의 모양과 비율대로 새로 지었어요.",
                "다운타운 반도와 끝자락의 큰 공원 숲, 폴스 크릭과 남쪽 기슭이 있어요.",
                "실제 길 방향대로 블록마다 건물이 서요.",
                "업무 지구의 건물은 높고, 웨스트엔드는 낮아요.",
                "바닷가를 따라 방파제 길이 길게 이어져요.",
                "내 방은 웨스트엔드로, 카페는 폴스 크릭 방파제 옆으로 옮겼어요.",
                "도서관은 다운타운 한가운데, 코워킹은 동쪽 벽돌 거리에 있어요.",
                "회사는 바다 옆 업무 지구의 높은 건물에 있어요.",
                "스카이트레인이 실제 노선을 따라 달려요. 터널에서 나와 역으로 들어와요.",
                "마을이 넓어졌지만 가장 먼 곳도 걸어서 30초 남짓이면 가요.",
            ),
            improved = listOf(
                "물을 누르면 가까운 물가까지 걸어가요.",
                "지도 자료의 출처를 '?' 안내 카드 맨 아래에 적었어요.",
            ),
            known = listOf(
                "일부 휴대폰에서는 다운타운을 걸을 때 화면이 조금 끊길 수 있어요.",
            ),
        ),
        PatchNote(
            version = "0.8",
            date = "2026-10-31",
            headline = "어디서나 플레이, 첫 화면 이야기",
            added = listOf(
                "이제 주소 하나로 휴대폰과 컴퓨터 브라우저에서 바로 할 수 있어요.",
                "새 버전이 나오면 같은 주소에서 바로 바뀌어요.",
            ),
            improved = listOf(
                "첫 화면에 도착 이야기와 목표가 나와서, 왜 방부터 고르는지 알 수 있어요.",
                "방을 고르기 전에 총비용을 10월 1일에 또 낸다고 알려 줘요.",
                "방이 쌀수록 알바 대신 취업 준비에 쓸 날이 늘어난다고 미리 알려 줘요.",
                "방을 고른 뒤에는 가장 싼 방보다 한 달에 얼마 더 내는지 보여 줘요.",
            ),
        ),
        PatchNote(
            version = "0.7",
            date = "2026-10-31",
            headline = "이어하기와 비 오는 날",
            added = listOf(
                "창을 닫았다 열어도 하던 한 달을 이어서 할 수 있어요.",
                "'이어하기'는 그날로 바로 가고, '새로 시작'은 처음부터 해요.",
                "날씨가 날마다 맑음, 흐림, 비 가운데 하나로 바뀌어요.",
                "9월의 밴쿠버처럼 열흘에 사흘쯤은 비가 와요.",
                "비 오는 날엔 빗줄기와 웅덩이가 보이고, 우산 쓴 사람도 지나가요.",
                "비 오는 날엔 주민과 행인이 비 이야기를 해요.",
                "소리를 켜면 빗소리, 스카이트레인 소리, 밤 모닥불 소리가 들려요.",
                "날씨는 보고 듣는 것만 바꿔요. 돈과 체력, 확률은 그대로예요.",
            ),
        ),
        PatchNote(
            version = "0.6",
            date = "2026-10-31",
            headline = "살아 있는 마을, 배경음, 첫 안내",
            added = listOf(
                "행인 네 명이 마을을 산책해요. 말을 걸어도 하루가 지나지 않아요.",
                "주민들이 두리번거리다가, 다가가면 돌아보고 손을 흔들어요.",
                "스카이트레인이 32초마다 역을 오가요. 밤에는 창에 불이 켜져요.",
                "모닥불이 일렁이고, 물결이 반짝이고, 꽃잎과 구름이 흘러가요.",
                "저녁이면 행인들이 집으로 가고, 새벽에 다시 나와요.",
                "잔잔한 마을 음악이 생겼어요. 기기 시각에 따라 낮, 저녁, 밤이 달라요.",
                "말을 걸 때, 글자가 나올 때, 하루가 바뀔 때 작은 소리가 나요.",
                "소리는 꺼진 채 시작해요. '소리 켜기'로 켜면 다음에도 기억해요.",
                "마을에 처음 들어가면 안내 카드가 떠요. '?'를 누르면 다시 볼 수 있어요.",
                "한동안 손대지 않거나 대화 중이면 마을을 천천히 그려 배터리를 덜 써요.",
            ),
        ),
        PatchNote(
            version = "0.5",
            date = "2026-10-30",
            headline = "걸어 다니는 마을과 주민 대화",
            added = listOf(
                "9월 한 달을 화면 가득한 3D 마을에서 걸어 다니며 보내요.",
                "땅을 누르면 걷고, 두 번 누르면 빨리 가요.",
                "화면을 끌면 둘러보고, 두 손가락을 오므리거나 펴면 멀리·가까이 봐요.",
                "카페의 하나, 도서관의 레오, 코워킹의 수진, 회사의 마커스를 만나요.",
                "내 방 옆에는 이웃 할머니가 계세요.",
                "주민에게 말을 걸어 그날 할 일을 골라요. '이야기'로 수다도 떨어요.",
                "할 일을 고르면 노을과 밤이 지나고 아침이 와요.",
                "쉬는 날에는 모닥불 옆 벤치에서 밤을 보내요.",
            ),
            improved = listOf(
                "기기에서 '동작 줄이기'를 켜 두면 작은 마을 그림과 버튼으로 해요.",
            ),
        ),
        PatchNote(
            version = "0.4",
            date = "2026-10-30",
            headline = "3D 마을",
            added = listOf(
                "9월 화면에 상자로 지은 작은 3D 마을이 생겼어요.",
                "내 방, 카페, 도서관, 코워킹, 회사와 스카이트레인, 바다 건너 산이 보여요.",
                "할 일을 고르면 주인공이 그 일을 하는 건물로 걸어가요.",
            ),
            improved = listOf(
                "3D를 지원하지 않는 기기에서는 마을 없이 예전처럼 할 수 있어요.",
            ),
        ),
        PatchNote(
            version = "0.3",
            date = "2026-10-30",
            headline = "휴대폰 화면과 오늘의 판",
            added = listOf(
                "휴대폰에 맞춘 브라우저 화면에서 방 고르기부터 결말까지 버튼으로 해요.",
                "위쪽에 날짜, 남은 날, 잔고, 10월 1일에 낼 총비용이 늘 보여요.",
                "영어, 코딩, 체력은 막대로 보여요. 면접이 잡히면 통과 확률도 보여요.",
                "할 수 없는 일에는 그 이유가 적혀요.",
                "'오늘의 판'이 생겼어요. 같은 날짜에는 모두 같은 방과 같은 운으로 해요.",
                "오늘의 판을 마치면 결과 한 줄을 복사해 어디든 붙여 넣을 수 있어요.",
            ),
        ),
        PatchNote(
            version = "0.2",
            date = "2026-10-30",
            headline = "9월 한 달",
            added = listOf(
                "방을 고른 뒤 9월 2일부터 30일까지 하루에 한 가지씩 할 일을 골라요.",
                "카페 알바, 영어 공부, 코딩 연습, 지원서 넣기, 쉬기 가운데 하나예요.",
                "날마다 생활비가 나가고, 쉬기 말고는 모두 체력이 들어요.",
                "지원서를 넣으면 1차 영어 면접이 잡힐 수 있어요.",
                "1차를 통과하면 2차 기술 면접, 둘 다 붙으면 취업 제안이에요.",
                "결말은 취업 제안, 9월 버티기, 10월 총비용 부족 세 가지예요.",
                "지원은 27일까지 할 수 있어서, 두 번의 면접이 늘 9월 안에 끝나요.",
            ),
        ),
        PatchNote(
            version = "0.1",
            date = "2026-10-30",
            headline = "첫 1분",
            added = listOf(
                "처음에는 컴퓨터에서 글자로만 하는 1분짜리 게임이었어요.",
                "방 세 개 가운데 한 달 총비용이 가장 싼 방을 골라요.",
                "방마다 월세와 교통 구역, 공과금 포함 여부가 나와요.",
                "요금표에서 구역별 교통 정기권 값과 공과금 범위를 볼 수 있어요.",
                "고른 방의 총비용을 짐작하면 진짜 총비용과 가장 싼 방을 알려 줘요.",
                "가장 싼 방을 고르면 100점, 짐작이 가까울수록 100점까지 더 받아요.",
                "금액은 1,300처럼 쉼표를 넣어 적어도 돼요.",
            ),
        ),
    )

    const val TITLE = "업데이트 소식"
    const val CLOSE = "닫기"

    /** What a screen reader hears after [TITLE] on its button while there is news the player has not opened. */
    const val NEWS = "새 소식"

    const val ADDED = "새로운 기능"
    const val IMPROVED = "개선"
    const val FIXED = "수정"
    const val KNOWN = "알려진 문제"

    /** "v0.11". */
    fun versionLabel(version: String): String = "v$version"

    /** "2026년 10월 31일", from "2026-10-31". */
    fun dateLabel(date: String): String {
        val day = requireNotNull(TodaysRun.parse(date)) { "Not a date: $date" }
        return "${day.year}년 ${day.month}월 ${day.day}일"
    }

    /**
     * True when [version] is newer than [than], the version the player last opened: numbers compared part by part, so
     * 0.10 is newer than 0.9. Nothing opened yet, or a version that does not read, counts as older.
     */
    fun isNewer(version: String, than: String?): Boolean {
        val newer = parts(version) ?: return false
        val older = than?.let { parts(it) } ?: return true
        for (index in 0 until maxOf(newer.size, older.size)) {
            val a = newer.getOrElse(index) { 0 }
            val b = older.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** "0.11" as [0, 11], or null when it is not numbers joined by dots. */
    internal fun parts(version: String): List<Int>? =
        version.split('.').map { part -> part.toIntOrNull()?.takeIf { it >= 0 && part == "$it" } ?: return null }

    /** The notes as PATCH_NOTES.md at the repository's root reads them. */
    fun markdown(): String = buildString {
        appendLine("<!-- Made from text/src/commonMain/kotlin/vancouvermonth/text/PatchNotes.kt; the README says how. -->")
        appendLine()
        appendLine("# 밴쿠버 한 달 (가제) $TITLE")
        appendLine()
        appendLine("새 버전부터 적어요. 게임 첫 화면의 '$TITLE'에서도 볼 수 있어요.")
        for (note in ALL) {
            appendLine()
            appendLine("## ${versionLabel(note.version)} · ${dateLabel(note.date)}")
            appendLine()
            appendLine("**${note.headline}**")
            for ((name, lines) in note.sections) {
                appendLine()
                appendLine("### $name")
                appendLine()
                for (line in lines) appendLine("- $line")
            }
        }
    }
}
