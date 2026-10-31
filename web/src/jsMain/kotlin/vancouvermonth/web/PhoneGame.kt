package vancouvermonth.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.clear
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.Range
import vancouvermonth.core.Action
import vancouvermonth.core.Ending
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Month
import vancouvermonth.core.Reveal
import vancouvermonth.core.TodaysRun
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.save.play
import vancouvermonth.text.Continue
import vancouvermonth.text.actionEffect
import vancouvermonth.text.actionName
import vancouvermonth.text.breakdown
import vancouvermonth.text.cheapestLine
import vancouvermonth.text.continueFrom
import vancouvermonth.text.date
import vancouvermonth.text.endingLine
import vancouvermonth.text.eventLine
import vancouvermonth.text.greeting
import vancouvermonth.text.guessLine
import vancouvermonth.text.keyword
import vancouvermonth.text.money
import vancouvermonth.text.monthStartLines
import vancouvermonth.text.parseAmount
import vancouvermonth.text.Passer
import vancouvermonth.text.passerLine
import vancouvermonth.text.passPrices
import vancouvermonth.text.percent
import vancouvermonth.text.smallTalk
import vancouvermonth.text.stageName
import vancouvermonth.text.todaysRunName
import vancouvermonth.text.todaysRunResult
import vancouvermonth.text.Townsperson
import vancouvermonth.text.utilitiesLabel
import vancouvermonth.text.utilitiesPrices
import vancouvermonth.text.whyNot

/**
 * Draws one run in [root]: the room on September 1st, then one action a day, then the ending.
 * The rules are in core and the wording in text; this only shows the state and passes each tap on.
 * Every tap redraws the whole screen from that state. A run comes from a seed or from today's date, never both.
 * The month is played in a town the player walks in, which [makeWalkableTown] makes on the first day: each day is
 * spent by talking to a place's person. Where it gives none, the month is played with buttons, under a town that
 * is a picture when [makeTown] gives one. Either town is closed at the ending. Given a slot in [saves], the month in
 * progress is kept after every day, and the page opened again for the same run offers to go on with it.
 */
class PhoneGame private constructor(
    private val root: HTMLElement,
    seed: Int,
    private val today: TodaysRun?,
    private val playAgain: () -> Unit,
    private val makeTown: () -> Town?,
    private val makeWalkableTown: () -> WalkableTown?,
) {
    /** A run from a seed, as in `?seed=1011`. */
    constructor(
        root: HTMLElement,
        seed: Int,
        playAgain: () -> Unit,
        makeTown: () -> Town? = { null },
        makeWalkableTown: () -> WalkableTown? = { null },
    ) : this(root, seed, null, playAgain, makeTown, makeWalkableTown)

    /** Today's run: its date picks the seed, and its ending shows the result line to copy. */
    constructor(
        root: HTMLElement,
        today: TodaysRun,
        playAgain: () -> Unit,
        makeTown: () -> Town? = { null },
        makeWalkableTown: () -> WalkableTown? = { null },
    ) : this(root, today.seed, today, playAgain, makeTown, makeWalkableTown)

    private val game = FirstMinute(seed)
    private var picked: Int? = null
    private var guess = ""
    private var reveal: Reveal? = null
    private var month: Month? = null
    private var town: Town? = null
    private var walkable: WalkableTown? = null
    private var playedInTown = false

    /** The day the last action was spent on, and what happened, as the month told it and as written. */
    private var lastDay = 0
    private var lastEvents: List<Event> = emptyList()
    private var lastLines: List<String> = emptyList()

    /** The place whose person the player is talking to in the town, or null; what they say; who has been met. */
    private var talkingAt: Place? = null
    private var talkLine = ""
    private var smallTalks = 0
    private val met = mutableSetOf<Townsperson>()

    /** The passerby the player has stopped for a word, or null. No day is spent on them. */
    private var chattingWith: Passer? = null

    /** How many words each passerby has had on [chatsDay], so the same person says something new each time. */
    private val chats = mutableMapOf<Passer, Int>()
    private var chatsDay = 0

    /** The timer typing the person's line, while it types. */
    private var typing: Int? = null

    /** When the open talk opened: for [talkSettleMillis] after that, taps on it do nothing. A test may change it. */
    private var talkOpened = 0.0
    internal var talkSettleMillis = TALK_SETTLE_MILLIS

    /** True from an answer until the town's morning: meanwhile no talk opens. */
    private var dayChanging = false

    /** What takes the focus once the screen is drawn again: a talk that opened, or the day's log after an answer. */
    private var focusNext: String? = null

    /** The sound switch, and the sounds of a talk and of the change of day; null for a game with no sound. */
    internal var sound: Sound? = null

    /** The card on how to play, shown the first time the town opens; null for none. */
    internal var guide: FirstRunGuide? = null

    /** Where the month in progress is kept between visits; null for a game that keeps nothing. */
    internal var saves: SaveSlot? = null

    /** The month being played as the slot keeps it, and the month kept from an earlier visit, read once on opening. */
    private var record: MonthRecord? = null
    private var kept: KeptMonth? = null
    private var keptRead = false

    fun show() {
        stopTyping()
        val current = month
        val walking = walkable
        if (current != null && !current.isOver && walking != null) {
            showTownDay(current, walking)
            return
        }
        root.clear()
        val offered = if (current == null) keptMonth() else null
        when {
            offered != null -> showKept(offered)
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
                    val room = checkNotNull(picked)
                    beginMonth(game.startMonth(room), MonthRecord(game.seed, today, room, reveal.guess))
                }
            }
        }
    }

    /** Opens [started] for play, in a town to walk in where there is one, and keeps it in the slot as [kept]. */
    private fun beginMonth(started: Month, kept: MonthRecord) {
        month = started
        record = kept
        saves?.keep(kept)
        walkable = makeWalkableTown()?.also { listenTo(it) }
        if (walkable == null) town = makeTown()
        guide?.onOpenChange = { walkable?.isCovered = isTownCovered() }
        show()
        window.scrollTo(0.0, 0.0)
        if (walkable != null) guide?.openFirstTime(root)
    }

    /** The month kept from an earlier visit for this run, read once, when the page first shows. */
    private fun keptMonth(): KeptMonth? {
        if (!keptRead) {
            keptRead = true
            kept = saves?.keptFor(game.seed, today)
        }
        return kept
    }

    /** Before the rooms, a card that offers the kept month: 이어하기 goes on with it, 새로 시작 drops it. */
    private fun showKept(kept: KeptMonth) {
        showTitle()
        val month = kept.replayed.month
        root.child("section", "card kept") {
            setAttribute("aria-labelledby", "kept-title")
            child("h2", text = Continue.TITLE).id = "kept-title"
            child("p", "kept-from", continueFrom(month.day, month.balance)).id = "kept-from"
            child("p", "kept-note", Continue.NOTE).id = "kept-note"
            child("div", "kept-buttons") {
                button("primary", text = Continue.CONTINUE) {
                    setAttribute("aria-describedby", "kept-from")
                    onclick = { goOn(kept) }
                }
                button("secondary", text = Continue.NEW) {
                    setAttribute("aria-describedby", "kept-note")
                    onclick = {
                        this@PhoneGame.kept = null
                        saves?.clear()
                        show()
                    }
                }
            }
        }
    }

    /** Goes on with [kept] on the day it reached, with that day's log, as if the page had never closed. */
    private fun goOn(kept: KeptMonth) {
        this.kept = null
        val record = kept.record
        val month = kept.replayed.month
        picked = record.room
        guess = "${record.guess}"
        reveal = kept.replayed.reveal
        if (record.days.isNotEmpty()) {
            lastDay = month.day - 1
            lastEvents = kept.replayed.lastEvents
            lastLines = lastEvents.map { eventLine(it, month) }
        }
        // Who was met is not kept, but the people of the places where days were spent know the player by now.
        for (choice in record.days) {
            met += personAt(if (choice is Choice.Take) placeFor(choice.action) else INTERVIEW_PLACE)
        }
        beginMonth(month, record)
    }

    private fun showDay(month: Month) {
        root.child("header", "topbar") {
            figure("오늘", date(month.day))
            figure("남은 날", "${month.daysLeft}일")
            figure("잔고", money(month.balance))
            figure("10월 1일에 낼 총비용", money(month.room.totalMonthlyCost))
            sound?.button(this)
        }
        val shownTown = town
        if (shownTown == null) {
            showLog()
            showStats(month)
            showInterview(month)
            showActions(month)
        } else {
            // The same town every day, so the character stays where the last day left it. The town takes room,
            // so the day's buttons come right under it, where a small phone still shows them without scrolling.
            root.appendChild(shownTown.element)
            showActions(month)
            showLog()
            showStats(month)
            showInterview(month)
        }
    }

    private fun showStats(month: Month) {
        root.child("section", "card stats") {
            meter("영어", "english", month.english, Month.MAX_SKILL)
            meter("코딩", "coding", month.coding, Month.MAX_SKILL)
            meter("체력", "energy", month.energy, Month.MAX_ENERGY)
        }
    }

    private fun showInterview(month: Month) {
        val interview = month.interview ?: return
        root.child("section", "card interview") {
            child("h2", text = "면접")
            child("p") {
                child("span", text = "${stageName(interview.stage)} ${date(interview.day)} · ")
                child("strong", "chance", "통과 확률 ${percent(month.passChance(interview.stage))}")
            }
        }
    }

    private fun showActions(month: Month) {
        root.child("section", "actions") {
            if (month.hasInterviewToday) {
                child("h2", text = "오늘은 면접 날이에요.")
                button("primary", text = "면접 보기") {
                    onclick = { spend(month, INTERVIEW_PLACE, Choice.AttendInterview) }
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
                        onclick = { spend(month, placeFor(action), Choice.Take(action)) }
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
            // After a month in the town, the neighbour has a word on the day of an offer.
            if (playedInTown && ending == Ending.OFFER) {
                val word = greeting(Townsperson.GRANDMA, month, firstMeeting = false, yesterday = lastEvents)
                child("p", "ending-word", "${Townsperson.GRANDMA.title}: $word")
            }
            child("div", "figures") {
                figure("방 고르기 점수", "${reveal.score}/${FirstMinute.MAX_SCORE}")
                figure("영어", "${month.english}/${Month.MAX_SKILL}")
                figure("코딩", "${month.coding}/${Month.MAX_SKILL}")
            }
            if (today != null) result(todaysRunResult(today, reveal.score, ending, month.day))
            child("div", "ending-buttons") {
                button("primary", text = "다시 하기") {
                    onclick = { playAgain() }
                }
                // A link, since it only opens the page again; the page then reads the browser's date.
                child("a", "secondary", "오늘의 판").setAttribute("href", "?today")
            }
        }
    }

    private fun showTitle() {
        root.child("header", "title") {
            child("p", "brand", "BNI Tech")
            child("h1", text = "밴쿠버 한 달 (가제)")
            child("p", "run", if (today == null) "판 번호 ${game.seed}" else "${todaysRunName(today)} · 판 번호 ${game.seed}")
            sound?.button(this)
        }
    }

    private fun showLog(parent: HTMLElement = root) {
        if (lastLines.isEmpty()) return
        parent.child("section", "card log") {
            setAttribute("aria-live", "polite")
            child("h2", text = "${date(lastDay)}에 있었던 일")
            child("ul") { lastLines.forEach { child("li", text = it) } }
        }
    }

    /**
     * Spends today at [place] on [choice], keeps what happened for the next screen, and goes back to the top of the
     * page. The character sets off for [place] as the day's log appears. The slot keeps the month, or lets it go at
     * the ending.
     */
    private fun spend(month: Month, place: Place, choice: Choice) {
        lastDay = month.day
        lastEvents = month.play(choice)
        lastLines = lastEvents.map { eventLine(it, month) }
        talkingAt = null
        record = record?.after(choice)
        if (month.isOver) {
            saves?.clear()
            closeTown()
        } else {
            record?.let { saves?.keep(it) }
            sound?.chime()
            town?.walkTo(place)
            val walking = walkable
            if (walking != null) {
                // Until the morning, the town takes no input and no talk opens. The day's log takes the focus.
                dayChanging = true
                focusNext = ".over-town .log"
                walking.endDay(place)
            }
        }
        show()
        window.scrollTo(0.0, 0.0)
    }

    /** The ending has no town, so the town lets go of its drawing. */
    private fun closeTown() {
        town?.close()
        town = null
        walkable?.close()
        walkable = null
    }

    /** The town tells the game where the player turns, and when it can no longer draw: then buttons take over. */
    private fun listenTo(town: WalkableTown) {
        playedInTown = true
        town.onPlace = { place -> if (!dayChanging && (place != talkingAt || chattingWith != null)) talkTo(place) }
        town.onPasserby = { passer -> if (!dayChanging) chatWith(passer) }
        town.onMorning = { dayChanging = false }
        town.onFail = {
            walkable = null
            talkingAt = null
            dayChanging = false
            show()
        }
    }

    /** Opens the talk with [place]'s person, who greets the player as the day suggests, or closes it for null. */
    private fun talkTo(place: Place?) {
        val current = month ?: return
        talkingAt = place
        if (chattingWith != null) walkable?.endChat()
        chattingWith = null
        smallTalks = 0
        if (place != null) {
            val person = personAt(place)
            talkLine = greeting(person, current, firstMeeting = person !in met, yesterday = lastEvents)
            met.add(person)
            talkOpened = window.performance.now()
            focusNext = ".talk"
            sound?.blip()
        }
        show()
    }

    /** True while a card lies over most of the town: a talk, a small talk or the guide on how to play. */
    private fun isTownCovered(): Boolean = talkingAt != null || chattingWith != null || guide?.isOpen == true

    /**
     * A day in the town: the town fills the screen, the day's figures sit over its top, and a talk with a place's
     * person opens over its bottom. The town stays on the page all month, so its picture never blinks; only what
     * lies over it is drawn again.
     */
    private fun showTownDay(month: Month, town: WalkableTown) {
        if (town.element.parentElement != root) {
            root.clear()
            root.appendChild(town.element)
        }
        root.querySelector(".over-town")?.remove()
        town.mark(if (month.hasInterviewToday) INTERVIEW_PLACE else null)
        town.isCovered = isTownCovered()
        root.child("div", "over-town") {
            child("header", "hud") {
                setAttribute("tabindex", "-1")
                child("div", "hud-figures") {
                    figure("오늘", date(month.day))
                    figure("남은 날", "${month.daysLeft}일")
                    figure("잔고", money(month.balance))
                    figure("10월 총비용", money(month.room.totalMonthlyCost))
                }
                child("div", "hud-line") {
                    child(
                        "p",
                        "hud-stats",
                        "영어 ${month.english}/${Month.MAX_SKILL} · 코딩 ${month.coding}/${Month.MAX_SKILL} · " +
                            "체력 ${month.energy}/${Month.MAX_ENERGY}",
                    )
                    townControls()
                }
                val interview = month.interview
                when {
                    month.hasInterviewToday -> child("p", "hud-note", "오늘은 면접 날이에요. 회사로 가세요.")
                    interview != null -> child(
                        "p",
                        "hud-note",
                        "${stageName(interview.stage)} ${date(interview.day)} · " +
                            "통과 확률 ${percent(month.passChance(interview.stage))}",
                    )
                }
            }
            showLog(this)
            querySelector(".log")?.setAttribute("tabindex", "-1")
            val talking = talkingAt
            val chatting = chattingWith
            if (talking != null) {
                talk(month, talking)
            } else if (chatting != null) {
                chat(chatting)
            } else if (lastLines.isEmpty()) {
                // On the first day, at the bottom where the talks open, how to get about.
                child("p", "town-hint", "땅을 누르면 걷고, 두 번 누르면 빨리 가요. 사람이나 건물을 누르면 말을 걸어요.")
            }
        }
        // A keyboard or a screen reader follows what happens: into a talk that opens, to the log after an answer.
        focusNext?.let { (root.querySelector(it) as? HTMLElement)?.focus() }
        focusNext = null
    }

    /** The sound switch and the "?" that shows how to play again, at the end of the town's top card. */
    private fun HTMLElement.townControls() {
        val sound = sound
        val guide = guide
        if (sound == null && guide == null) return
        child("div", "hud-controls") {
            sound?.button(this)
            guide?.helpButton(this, root)
        }
    }

    /** The talk with [place]'s person: who they are, their line typed out, and what the player can answer. */
    private fun HTMLElement.talk(month: Month, place: Place) {
        val person = personAt(place)
        child("section", "talk") {
            setAttribute("role", "dialog")
            setAttribute("aria-label", person.title)
            setAttribute("tabindex", "-1")
            // The tap that opened the talk can land on it a moment later, as the second tap of a double tap or a
            // finger lifted late. For a short while after the talk opens, taps on it do nothing.
            addEventListener("click", { event ->
                if (window.performance.now() - talkOpened < talkSettleMillis) {
                    event.stopPropagation()
                    event.preventDefault()
                }
            }, true)
            child("div", "talk-head") {
                face(Figures.lookOf(person))
                child("p", "talk-name") {
                    child("strong", text = person.title)
                    child("span", "talk-role", "${person.role} · ${place.label}")
                }
            }
            val line = child("p", "talk-line")
            // The letters come one at a time for the eyes; a screen reader gets the whole line at once.
            val typed = line.child("span") { setAttribute("aria-hidden", "true") }
            line.child("span", "visually-hidden", talkLine)
            typeOut(typed, talkLine)
            // A tap on the line shows the rest of it at once.
            line.onclick = {
                stopTyping()
                typed.textContent = talkLine
            }
            child("div", "keywords") {
                answer(month, place)
                button("keyword", text = "이야기") {
                    onclick = {
                        talkLine = smallTalk(person, month, smallTalks)
                        smallTalks += 1
                        focusNext = ".talk"
                        show()
                    }
                }
                button("keyword", text = "그만") {
                    onclick = {
                        talkingAt = null
                        focusNext = ".hud"
                        show()
                    }
                }
            }
        }
    }

    /**
     * The answer that spends the day: the place's action with what it does, or why it cannot be done today. On an
     * interview day the office offers the interview instead.
     */
    private fun HTMLElement.answer(month: Month, place: Place) {
        val interview = month.interview
        if (month.hasInterviewToday && place == INTERVIEW_PLACE && interview != null) {
            button("keyword spend") {
                child("span", "action-name", "면접 보기")
                val chance = percent(month.passChance(interview.stage))
                child("span", "action-effect", "${stageName(interview.stage)} · 통과 확률 $chance")
                onclick = { spend(month, INTERVIEW_PLACE, Choice.AttendInterview) }
            }
            return
        }
        val action = actionAt(place)
        val blocked = month.blockedBy(action)
        button("keyword spend") {
            disabled = blocked != null
            child("span", "action-name", keyword(action))
            child("span", "action-effect", actionEffect(action, month))
            if (blocked != null) child("span", "action-why", whyNot(blocked))
            onclick = { spend(month, place, Choice.Take(action)) }
        }
    }

    /** Stops [passer] for a word: a line of small talk, typed out, and nothing to answer but 그만. */
    private fun chatWith(passer: Passer) {
        val current = month ?: return
        talkingAt = null
        if (current.day != chatsDay) {
            chats.clear()
            chatsDay = current.day
        }
        val count = chats[passer] ?: 0
        chats[passer] = count + 1
        chattingWith = passer
        talkLine = passerLine(passer, current.day, count)
        talkOpened = window.performance.now()
        focusNext = ".talk"
        sound?.blip()
        show()
    }

    /** The talk with a passerby: who they are, their line typed out, and 그만. No answer spends the day. */
    private fun HTMLElement.chat(passer: Passer) {
        child("section", "talk") {
            setAttribute("role", "dialog")
            setAttribute("aria-label", passer.title)
            setAttribute("tabindex", "-1")
            // As in a talk with a place's person, the tap that opened it does nothing on it for a moment.
            addEventListener("click", { event ->
                if (window.performance.now() - talkOpened < talkSettleMillis) {
                    event.stopPropagation()
                    event.preventDefault()
                }
            }, true)
            child("div", "talk-head") {
                face(Figures.lookOf(passer))
                child("p", "talk-name") {
                    child("strong", text = passer.title)
                    child("span", "talk-role", passer.role)
                }
            }
            val line = child("p", "talk-line")
            val typed = line.child("span") { setAttribute("aria-hidden", "true") }
            line.child("span", "visually-hidden", talkLine)
            typeOut(typed, talkLine)
            line.onclick = {
                stopTyping()
                typed.textContent = talkLine
            }
            child("div", "keywords") {
                button("keyword", text = "그만") {
                    onclick = {
                        chattingWith = null
                        walkable?.endChat()
                        focusNext = ".hud"
                        show()
                    }
                }
            }
        }
    }

    /** Types [line] into [element] one letter at a time. */
    private fun typeOut(element: HTMLElement, line: String) {
        var shown = 0
        typing = window.setInterval({
            shown += 1
            element.textContent = line.take(shown)
            if (shown >= line.length) stopTyping()
            if (line.getOrNull(shown - 1)?.isWhitespace() == false) sound?.tick()
        }, TYPE_MILLIS)
    }

    private fun stopTyping() {
        typing?.let { window.clearInterval(it) }
        typing = null
    }

    private companion object {
        /** How long each letter of a person's line takes to appear. */
        const val TYPE_MILLIS = 35

        /** How long a talk ignores taps after it opens. */
        const val TALK_SETTLE_MILLIS = 400.0
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
        val copy = button("secondary", text = "결과 복사")
        val status = child("p", "result-status") { setAttribute("aria-live", "polite") }

        fun selectInstead() {
            status.textContent = if (selectAll(text)) {
                "복사하지 못했어요. 선택된 줄을 직접 복사해 주세요."
            } else {
                "복사하지 못했어요. 줄을 눌러 선택한 뒤 복사해 주세요."
            }
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

/** Selects all of [element]'s text, as a long press would. False when the page has no selection to use. */
internal fun selectAll(element: HTMLElement): Boolean {
    val selection = getSelection() ?: return false
    val range = document.createRange()
    range.selectNodeContents(element)
    selection.removeAllRanges()
    selection.addRange(range)
    return true
}

/** The page's text selection. Kotlin's DOM declarations leave it out, so these are the parts this page uses. */
internal external interface Selection {
    fun removeAllRanges()
    fun addRange(range: Range)
}

/** The browser's own getSelection(), the same function as window.getSelection(). */
internal external fun getSelection(): Selection?

/** A small face drawn from a person's colours: hair over a face with two eyes, on the colour of their clothes. */
private fun HTMLElement.face(look: Look) {
    child("span", "face") {
        setAttribute("aria-hidden", "true")
        style.background = css(look.top)
        child("span", "face-skin") {
            style.background = css(look.skin)
            child("span", "face-hair") { style.background = css(look.hair) }
            child("span", "face-eyes")
        }
    }
}

/** 0xRRGGBB as CSS writes it: "#rrggbb". */
private fun css(colour: Int): String = "#" + colour.toString(16).padStart(6, '0')

private fun HTMLElement.point(line: String, points: Int) {
    child("li") {
        child("span", text = line)
        child("span", "plus", "+$points")
    }
}
