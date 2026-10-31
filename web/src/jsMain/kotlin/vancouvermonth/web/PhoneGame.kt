package vancouvermonth.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.clear
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import vancouvermonth.core.Action
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.core.Reveal
import vancouvermonth.core.TodaysRun
import vancouvermonth.text.actionEffect
import vancouvermonth.text.actionName
import vancouvermonth.text.breakdown
import vancouvermonth.text.cheapestLine
import vancouvermonth.text.date
import vancouvermonth.text.endingLine
import vancouvermonth.text.eventLine
import vancouvermonth.text.guessLine
import vancouvermonth.text.money
import vancouvermonth.text.monthStartLines
import vancouvermonth.text.parseAmount
import vancouvermonth.text.passPrices
import vancouvermonth.text.percent
import vancouvermonth.text.stageName
import vancouvermonth.text.todaysRunName
import vancouvermonth.text.todaysRunResult
import vancouvermonth.text.utilitiesLabel
import vancouvermonth.text.utilitiesPrices
import vancouvermonth.text.whyNot

/**
 * Draws one run in [root]: the room on September 1st, then one action a day, then the ending.
 * The rules are in core and the wording in text; this only shows the state and passes each tap on.
 * Every tap redraws the whole screen from that state. [today] is set when this is today's run.
 */
class PhoneGame(
    private val root: HTMLElement,
    seed: Int,
    private val playAgain: () -> Unit,
    private val today: TodaysRun? = null,
) {
    init {
        require(today == null || today.seed == seed) { "Today's run is seed ${today?.seed}, not $seed" }
    }

    private val game = FirstMinute(seed)
    private var picked: Int? = null
    private var guess = ""
    private var reveal: Reveal? = null
    private var month: Month? = null

    /** The day the last action was spent on, and what happened, written when it happened. */
    private var lastDay = 0
    private var lastLines: List<String> = emptyList()

    fun show() {
        root.clear()
        val current = month
        when {
            current == null -> showRooms()
            current.isOver -> showEnding(current)
            else -> showDay(current)
        }
    }

    private fun showRooms() {
        showTitle()
        root.child("p", "balance", "잔고 ${money(game.startingBalance)} · 9월 1일")
        root.child("section", "card") {
            child("h2", text = "할 일")
            child("p", "lead", "한 달 총비용이 가장 싼 방을 고르세요.")
            child("p", "formula", "한 달 총비용 = 월세 + 공과금 + 교통 정기권")
        }
        root.child("section", "card") {
            child("h2", text = "요금표")
            child("dl", "board") {
                child("dt", text = "교통 정기권")
                child("dd", text = passPrices())
                child("dt", text = "공과금")
                child("dd", text = utilitiesPrices())
            }
        }
        root.child("section", "rooms") {
            child("h2", text = "어느 방?")
            game.rooms.forEachIndexed { index, room ->
                button("room") {
                    setAttribute("aria-pressed", (index == picked).toString())
                    disabled = reveal != null
                    child("span", "room-number", "${index + 1}번 방")
                    child("span", "room-rent", "월세 ${money(room.rent)}")
                    child("span", "room-clues", "교통 ${room.zone.number}구역 · ${utilitiesLabel(room)}")
                    onclick = {
                        picked = index
                        show()
                    }
                }
            }
        }
        showGuess()
        reveal?.let { showReveal(it) }
    }

    private fun showGuess() {
        val form = root.child("form", "card guess") as HTMLFormElement
        val label = form.child("label", text = "이 방의 한 달 총비용은 얼마일까요?") as HTMLLabelElement
        label.htmlFor = "guess"
        val row = form.child("div", "guess-row")
        row.child("span", "dollar", "$")
        val input = row.child("input") as HTMLInputElement
        input.id = "guess"
        // Text, not number, so "1,300" and "$1,300" reach the same parser the terminal uses in every browser.
        input.type = "text"
        input.setAttribute("inputmode", "numeric")
        input.setAttribute("autocomplete", "off")
        input.placeholder = "예: 1200"
        input.value = guess
        if (reveal != null) {
            input.disabled = true
            return
        }
        val hint = form.child("p", "hint")
        val submit = form.button("primary", text = "총비용 확인하기")
        submit.type = "submit"

        fun refresh() {
            val amount = parseAmount(guess)
            hint.textContent = when {
                picked == null -> "방을 먼저 고르세요."
                guess.isNotBlank() && amount == null -> "숫자로 적어 주세요. 예: 1200"
                else -> ""
            }
            submit.disabled = picked == null || amount == null
        }
        input.oninput = {
            guess = input.value
            refresh()
        }
        form.onsubmit = { event ->
            event.preventDefault()
            val index = picked
            val amount = parseAmount(guess)
            if (index != null && amount != null) {
                reveal = game.reveal(index, amount)
                show()
                root.querySelector(".reveal")?.scrollIntoView()
            }
        }
        refresh()
    }

    private fun showReveal(reveal: Reveal) {
        val room = reveal.room
        root.child("section", "card reveal") {
            child("p", "label", "한 달 총비용")
            child("p", "total", money(room.totalMonthlyCost))
            child("p", "breakdown", breakdown(room))
            child("ul", "points") {
                point(cheapestLine(game, reveal), reveal.cheapestPoints)
                point(guessLine(reveal), reveal.guessPoints)
            }
            child("p", "score", "방 고르기 점수 ${reveal.score} / ${FirstMinute.MAX_SCORE}")
            child("p", "left", "첫 달을 내고 남은 잔고 ${money(reveal.balanceAfterFirstMonth)}")
        }
        root.child("section", "card start") {
            child("h2", text = "9월 한 달이 시작돼요.")
            child("ul", "notes") {
                for (text in monthStartLines(room)) child("li", text = text)
            }
            button("primary", text = "${date(Month.FIRST_DAY)} 시작하기") {
                onclick = {
                    month = game.startMonth(checkNotNull(picked))
                    show()
                    window.scrollTo(0.0, 0.0)
                }
            }
        }
    }

    private fun showDay(month: Month) {
        root.child("header", "topbar") {
            figure("오늘", date(month.day))
            figure("남은 날", "${month.daysLeft}일")
            figure("잔고", money(month.balance))
            figure("10월 1일에 낼 총비용", money(month.room.totalMonthlyCost))
        }
        showLog()
        root.child("section", "card stats") {
            meter("영어", "english", month.english, Month.MAX_SKILL)
            meter("코딩", "coding", month.coding, Month.MAX_SKILL)
            meter("체력", "energy", month.energy, Month.MAX_ENERGY)
        }
        month.interview?.let { interview ->
            root.child("section", "card interview") {
                child("h2", text = "면접")
                child("p") {
                    child("span", text = "${stageName(interview.stage)} ${date(interview.day)} · ")
                    child("strong", "chance", "통과 확률 ${percent(month.passChance(interview.stage))}")
                }
            }
        }
        root.child("section", "actions") {
            if (month.hasInterviewToday) {
                child("h2", text = "오늘은 면접 날이에요.")
                button("primary", text = "면접 보기") {
                    onclick = { spend(month) { attendInterview() } }
                }
            } else {
                child("h2", text = "오늘 할 일 하나")
                for (action in Action.entries) {
                    val blocked = month.blockedBy(action)
                    button("action") {
                        disabled = blocked != null
                        child("span", "action-name", actionName(action))
                        child("span", "action-effect", actionEffect(action, month))
                        if (blocked != null) child("span", "action-why", whyNot(blocked))
                        onclick = { spend(month) { take(action) } }
                    }
                }
            }
        }
    }

    private fun showEnding(month: Month) {
        val ending = checkNotNull(month.ending) { "The month is not over" }
        val reveal = checkNotNull(reveal) { "The room was never revealed" }
        showTitle()
        showLog()
        root.child("section", "card ending ending-${ending.name.lowercase().replace('_', '-')}") {
            child("p", "ending-line", endingLine(ending, month.day, month.room.totalMonthlyCost, month.balance))
            child("div", "figures") {
                figure("방 고르기 점수", "${reveal.score}/${FirstMinute.MAX_SCORE}")
                figure("영어", "${month.english}/${Month.MAX_SKILL}")
                figure("코딩", "${month.coding}/${Month.MAX_SKILL}")
            }
            if (today != null) result(todaysRunResult(today, reveal.score, ending, month.day))
            button("primary", text = "다시 하기") {
                onclick = { playAgain() }
            }
        }
    }

    private fun showTitle() {
        root.child("header", "title") {
            child("p", "brand", "BNI Tech")
            child("h1", text = "밴쿠버 한 달 (가제)")
            child("p", "run", if (today == null) "판 번호 ${game.seed}" else "${todaysRunName(today)} · 판 번호 ${game.seed}")
        }
    }

    private fun showLog() {
        if (lastLines.isEmpty()) return
        root.child("section", "card log") {
            setAttribute("aria-live", "polite")
            child("h2", text = "${date(lastDay)}에 있었던 일")
            child("ul") { lastLines.forEach { child("li", text = it) } }
        }
    }

    /** Spends today, keeps what happened for the next screen, and goes back to the top of the page. */
    private fun spend(month: Month, today: Month.() -> List<Event>) {
        lastDay = month.day
        lastLines = month.today().map { eventLine(it, month) }
        show()
        window.scrollTo(0.0, 0.0)
    }
}

private fun HTMLElement.child(
    tag: String,
    className: String? = null,
    text: String? = null,
    build: HTMLElement.() -> Unit = {},
): HTMLElement {
    val element = document.createElement(tag) as HTMLElement
    if (className != null) element.className = className
    if (text != null) element.textContent = text
    element.build()
    appendChild(element)
    return element
}

private fun HTMLElement.button(
    className: String,
    text: String? = null,
    build: HTMLButtonElement.() -> Unit = {},
): HTMLButtonElement {
    val button = child("button", className, text) as HTMLButtonElement
    button.type = "button"
    button.build()
    return button
}

/** A small label above a value, as in the top bar and the ending. */
private fun HTMLElement.figure(label: String, value: String) {
    child("div", "figure") {
        child("span", "figure-label", label)
        child("span", "figure-value", value)
    }
}

/** A labelled bar; the "3/10" next to it is the value a screen reader reads. */
private fun HTMLElement.meter(label: String, kind: String, value: Int, max: Int) {
    child("div", "meter $kind") {
        child("span", "meter-label", label)
        child("span", "meter-bar") {
            setAttribute("aria-hidden", "true")
            child("span", "meter-fill") { style.width = "${value * 100 / max}%" }
        }
        child("span", "meter-value", "$value/$max")
    }
}

/**
 * Today's result as one line, with a button that copies it. Where the browser refuses to copy,
 * the line is selected instead, ready for the phone's own copy.
 */
private fun HTMLElement.result(line: String) {
    child("div", "result") {
        child("h2", text = "결과 한 줄")
        val text = child("p", "result-line", line)
        val copy = button("copy", text = "결과 복사")
        val status = child("p", "result-status") { setAttribute("aria-live", "polite") }

        fun selectInstead() {
            status.textContent = "복사하지 못했어요. 선택된 줄을 직접 복사해 주세요."
            val range = document.createRange()
            range.selectNodeContents(text)
            // Kotlin's DOM declarations have no getSelection, so this call goes to JavaScript directly.
            val selection = window.asDynamic().getSelection()
            selection.removeAllRanges()
            selection.addRange(range)
        }

        copy.onclick = {
            // The copy has to start inside the tap, or the browser refuses it.
            try {
                window.navigator.clipboard.writeText(line)
                    .then { status.textContent = "복사했어요." }
                    .catch { selectInstead() }
            } catch (error: Throwable) {
                // A page that is not on https, localhost or a file has no clipboard at all.
                selectInstead()
            }
        }
    }
}

private fun HTMLElement.point(line: String, points: Int) {
    child("li") {
        child("span", text = line)
        child("span", "plus", "+$points")
    }
}
