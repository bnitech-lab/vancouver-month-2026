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
import org.w3c.dom.asList
import vancouvermonth.core.Action
import vancouvermonth.core.Ending
import vancouvermonth.core.Event
import vancouvermonth.core.FirstMinute
import vancouvermonth.core.Item
import vancouvermonth.core.Month
import vancouvermonth.core.Reveal
import vancouvermonth.core.SharedFare
import vancouvermonth.core.Spending
import vancouvermonth.core.TaxiFare
import vancouvermonth.core.TodaysRun
import vancouvermonth.save.Choice
import vancouvermonth.save.MonthRecord
import vancouvermonth.save.Spend
import vancouvermonth.save.play
import vancouvermonth.text.Continue
import vancouvermonth.text.Guide
import vancouvermonth.text.Intro
import vancouvermonth.text.actionEffect
import vancouvermonth.text.actionName
import vancouvermonth.text.arrivalLines
import vancouvermonth.text.breakdown
import vancouvermonth.text.cheapestLine
import vancouvermonth.text.continueFrom
import vancouvermonth.text.date
import vancouvermonth.text.dayLog
import vancouvermonth.text.endingLine
import vancouvermonth.text.goalLine
import vancouvermonth.text.greeting
import vancouvermonth.text.guessExamplesLine
import vancouvermonth.text.guessLine
import vancouvermonth.text.guessMoneyLabel
import vancouvermonth.text.guessMoneyWhy
import vancouvermonth.text.guessRuleLine
import vancouvermonth.text.keyword
import vancouvermonth.text.money
import vancouvermonth.text.monthCostLine
import vancouvermonth.text.monthStartLines
import vancouvermonth.text.replacesKept
import vancouvermonth.text.signedMoney
import vancouvermonth.text.startBalanceLine
import vancouvermonth.text.parseAmount
import vancouvermonth.text.Passer
import vancouvermonth.text.PatchNotes
import vancouvermonth.text.passerLine
import vancouvermonth.text.passPrices
import vancouvermonth.text.percent
import vancouvermonth.text.Riding
import vancouvermonth.text.Shop
import vancouvermonth.text.smallTalk
import vancouvermonth.text.stageName
import vancouvermonth.text.todaysRunName
import vancouvermonth.text.todaysRunResult
import vancouvermonth.text.Townsperson
import vancouvermonth.text.Transit
import vancouvermonth.text.Transport
import vancouvermonth.text.utilitiesLabel
import vancouvermonth.text.utilitiesPrices
import vancouvermonth.text.Weather
import vancouvermonth.text.weatherOn
import vancouvermonth.text.weatherWord
import vancouvermonth.text.whyNot
import vancouvermonth.text.whyNotSpend
import vancouvermonth.text.whyTotalLines

/**
 * Draws one run in [root]: the intro, the room picked on the map and the guess on September 1st, then one action a day,
 * then the ending.
 * The rules are in core and the wording in text; this only shows the state and passes each tap on.
 * Every tap redraws the whole screen from that state. A run comes from a seed or from today's date, never both.
 * The month is played in a town the player walks in, which [makeWalkableTown] makes on the first day for the room's
 * zone, where 내 방 stands: each day is spent by talking to a place's person. Where it gives none, the month is played with buttons, under a town that
 * is a picture when [makeTown] gives one. Either town is closed at the ending. Given a [slot], the month in
 * progress is kept after every day, and the page opened again for the same run offers to go on with it.
 */
class PhoneGame private constructor(
    private val root: HTMLElement,
    seed: Int,
    private val today: TodaysRun?,
    private val playAgain: () -> Unit,
    private val makeTown: () -> Town?,
    private val makeWalkableTown: (zone: Int) -> WalkableTown?,
) {
    /** A run from a seed, as in `?seed=1011`. */
    constructor(
        root: HTMLElement,
        seed: Int,
        playAgain: () -> Unit,
        makeTown: () -> Town? = { null },
        makeWalkableTown: (zone: Int) -> WalkableTown? = { null },
    ) : this(root, seed, null, playAgain, makeTown, makeWalkableTown)

    /** Today's run: its date picks the seed, and its ending shows the result line to copy. */
    constructor(
        root: HTMLElement,
        today: TodaysRun,
        playAgain: () -> Unit,
        makeTown: () -> Town? = { null },
        makeWalkableTown: (zone: Int) -> WalkableTown? = { null },
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

    /** True while the player talks to the bike shop's keeper; whether they have met him; how much small talk so far. */
    private var atShop = false
    private var metKeeper = false
    private var shopTalks = 0

    /**
     * The money spent today so far, which goes at the top of today's log when the day is spent; and, on the top card
     * until the next ride or the day's end, why a shared one could not be rented.
     */
    private var spentToday: List<Event> = emptyList()
    private var rideNote: String? = null

    /**
     * Where to go, open over the town: on [choosing] from stop [choosingFrom] (-1 for a taxi from where the character
     * stands), with each taxi fare worked out as it opened (null where no taxi goes). Null when it is closed.
     */
    private var choosing: Transport? = null
    private var choosingFrom = -1
    private var taxiFares: List<Int?> = emptyList()

    /** What the ride under way cost, for the top card; null on the month's pass. */
    private var travelPaid: Int? = null

    /**
     * The shared bike or e-scooter the character stands by, while the card asking where to ride it is open, with the
     * fare to each place a taxi goes ([TransitLines.taxiStopsFor]) worked out as it opened (null where it cannot go);
     * and what the shared ride under way cost, for the top card.
     */
    private var renting: Ride? = null
    private var rideFares: List<Int?> = emptyList()
    private var ridePaid: Int? = null

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

    /** The card on how to play, opened from the "?" in the town's top card; null for none. */
    internal var guide: FirstRunGuide? = null

    /** The update news, from a button by the title and at the button screen's foot; null for none. */
    internal var notes: PatchNotesCard? = null

    /** Each day's weather, from the run's seed: only for how the town looks and sounds. A test may set its own. */
    internal var weatherOf: (Int) -> Weather = { day -> weatherOn(game.seed, day) }

    /**
     * Where the month in progress is kept between visits; null for a game that keeps nothing. The kept month is read
     * on the first [show] after this is set, so setting it again reads it again.
     */
    internal var slot: SaveSlot? = null
        set(value) {
            field = value
            keptRead = false
        }

    /** The month being played as the slot keeps it, and the month kept from an earlier visit, read once on opening. */
    private var record: MonthRecord? = null
    private var kept: KeptMonth? = null
    private var keptRead = false

    /**
     * True when the page took the kept month's run because its address named none: 새로 시작 then plays a new run, as
     * such an address did before there was anything to continue. An address that names a run starts it again instead.
     */
    internal var startOverWithNewRun = false

    /** The day another run's kept month goes on from, read with [kept]: starting this run replaces that month. */
    private var replacing: Int? = null

    /**
     * True once the intro is left for the rooms, by 시작하기 or by 새로 시작 over a kept month. The page sets it from the
     * start when its address asks to go straight to the rooms, as 다시 하기 and a new run from 새로 시작 do. A month kept
     * for this run is still offered on the intro first.
     */
    internal var introPassed = false

    /**
     * True to show the rooms as pins on the whole map ([RoomMap]), as the page does where it can draw one; false for
     * the list of rooms, which a test may ask for.
     */
    internal var roomsOnMap = true

    /** The map of the rooms on the screen, if it is; let go when the screen is drawn again. */
    private var roomMap: RoomMap? = null

    /** True once the map's picture has been set to be painted while the intro is read. */
    private var mapWarming = false

    fun show() {
        stopTyping()
        roomMap?.close()
        roomMap = null
        val current = month
        // Rain is heard on a rainy day of the month, and nowhere else.
        sound?.weather(if (current != null && !current.isOver) weatherOf(current.day) else Weather.SUNNY)
        val walking = walkable
        if (current != null && !current.isOver && walking != null) {
            showTownDay(current, walking)
            return
        }
        root.clear()
        val offered = if (current == null) keptMonth() else null
        when {
            current == null && (offered != null || !introPassed) -> showIntro(offered)
            current == null -> showRooms()
            current.isOver -> showEnding(current)
            else -> showDay(current)
        }
    }

    /**
     * The page's first screen: the title with the update news and the sound switch, the arrival story, how to play, how
     * the guess pays, and the map's credit; at its foot, kept in sight however long the rest, 시작하기 to the rooms. Over
     * a month kept from an earlier visit for this run, the intro opens with that month's day and balance, and the foot
     * offers 이어하기, which goes on with it, and 새로 시작, which drops it.
     */
    private fun showIntro(offer: KeptMonth?) {
        showTitle()
        root.child("div", "intro") {
            offer?.let { kept ->
                val month = kept.replayed.month
                child("section", "card kept") {
                    setAttribute("aria-labelledby", "kept-title")
                    child("h2", text = Continue.TITLE).id = "kept-title"
                    child("p", "kept-from", continueFrom(month.day, month.balance)).id = "kept-from"
                    child("p", "kept-note", Continue.NOTE).id = "kept-note"
                }
            }
            // Who the player is and what the month is for. The story says the money and the date, so no balance line
            // repeats them.
            child("section", "card story") {
                for (text in arrivalLines(game.startingBalance)) {
                    child("p", if (text == goalLine()) "goal" else null, text)
                }
            }
            child("section", "card how-to") {
                child("h2", text = Guide.TITLE)
                child("ul", "how-to-lines") { for (line in Guide.LINES) child("li", text = line) }
            }
            child("section", "card guess-pays") {
                child("h2", text = Intro.GUESS)
                child("p", text = Intro.GUESS_WHAT)
                child("p", "guess-rule-line", guessRuleLine())
                child("p", "guess-examples", guessExamplesLine())
            }
            child("div", if (offer == null) "intro-start" else "intro-start kept-buttons") {
                if (offer == null) {
                    button("primary", text = Intro.START) { onclick = { toRooms() } }
                } else {
                    button("primary", text = Continue.CONTINUE) {
                        setAttribute("aria-describedby", "kept-from")
                        onclick = { goOn(offer) }
                    }
                    button("secondary", text = Continue.NEW) {
                        setAttribute("aria-describedby", "kept-note")
                        onclick = { startOver() }
                    }
                }
            }
            // The town is traced from the two cities' open data, whose licences ask for their statements where it shows.
            mapCredit(this, "intro-credit")
            burnabyCredit(this, "intro-credit")
        }
        warmUpMap()
    }

    /**
     * Paints the whole map's ground, and lays out the city it is painted from, while the intro is read: once, after the
     * intro shows, when the browser has nothing else to do. So the rooms open at once after 시작하기. A failure only
     * leaves the work to the rooms.
     */
    private fun warmUpMap() {
        if (mapWarming || !roomsOnMap) return
        mapWarming = true
        window.requestAnimationFrame {
            whenIdle {
                try {
                    Minimap.warmUp()
                } catch (error: Throwable) {
                    // The rooms paint it themselves, or show the list.
                }
            }
        }
    }

    /** Leaves the intro for the rooms, from the top of the page the game is on. */
    private fun toRooms() {
        introPassed = true
        show()
        (root.ownerDocument?.defaultView ?: window).scrollTo(0.0, 0.0)
    }

    private fun showRooms() {
        showTitle()
        root.child("section", "card") {
            child("h2", text = "할 일")
            child("p", "lead", "한 달 총비용이 가장 싼 방을 고르세요.")
            child("p", "formula", "한 달 총비용 = 월세 + 공과금 + 교통 정기권")
            for (text in whyTotalLines()) child("p", "why", text)
        }
        // The rooms as pins on the whole map, where the browser draws one; as a list where it does not. The map comes
        // right after what to do, so a phone shows all of it on the first screen.
        val map = if (roomsOnMap) RoomMap(game.rooms, picked, reveal != null, ::pick).takeIf { it.canDraw } else null
        roomMap = map
        root.child("section", "rooms") {
            child("h2", text = "어느 방?")
            if (map == null) {
                roomList()
            } else {
                appendChild(map.element)
                val index = picked
                if (index == null) {
                    child("p", "room-map-hint", "핀을 누르면 그 방의 월세, 교통 구역, 공과금이 나와요.")
                } else {
                    roomCard(index)
                }
            }
        }
        // The prices right before the guess, where the player adds rent, utilities and the pass.
        root.child("section", "card") {
            child("h2", text = "요금표")
            child("dl", "board") {
                child("dt", text = "교통 정기권")
                child("dd", text = passPrices())
                child("dt", text = "공과금")
                child("dd", text = utilitiesPrices())
            }
        }
        showGuess()
        reveal?.let { showReveal(it) }
        // Placed at the width it takes on the page, measured only once the whole screen is there: measured any earlier,
        // the page would be shorter than where the player has scrolled to, and the browser would pull it back up. The
        // picture is painted just after the screen is first drawn, so the screen shows at once.
        map?.draw()
    }

    /** Picks room [index] and draws the screen again, the focus kept on its pin, or its button in the list. */
    private fun pick(index: Int) {
        picked = index
        show()
        val again = root.querySelector(".room-pin[data-room=\"$index\"]")
            ?: root.querySelectorAll("button.room").item(index)
        (again as? HTMLElement)?.focus()
    }

    /** The rooms as a list of buttons, for a browser that cannot draw the map. */
    private fun HTMLElement.roomList() {
        game.rooms.forEachIndexed { index, room ->
            button("room") {
                setAttribute("aria-pressed", (index == picked).toString())
                disabled = reveal != null
                child("span", "room-number", "${index + 1}번 방")
                child("span", "room-rent", "월세 ${money(room.rent)}")
                child("span", "room-clues", "교통 ${room.zone.number}구역 · ${utilitiesLabel(room)}")
                onclick = { pick(index) }
            }
        }
    }

    /** The picked room's card under the map: its number, rent, zone and utilities, as the list showed them. */
    private fun HTMLElement.roomCard(index: Int) {
        val room = game.rooms[index]
        child("div", "card room-card") {
            child("span", "room-number", "${index + 1}번 방")
            child("span", "room-rent", "월세 ${money(room.rent)}")
            child("span", "room-clues", "교통 ${room.zone.number}구역 · ${utilitiesLabel(room)}")
        }
    }

    private fun showGuess() {
        val form = root.child("form", "card guess") as HTMLFormElement
        val label = form.child("label", text = "이 방의 한 달 총비용은 얼마일까요?") as HTMLLabelElement
        label.htmlFor = "guess"
        // How the guess pays, said again where it is typed.
        form.child("p", "guess-rule", guessRuleLine()).id = "guess-rule"
        val row = form.child("div", "guess-row")
        row.child("span", "dollar", "$")
        val input = row.child("input") as HTMLInputElement
        input.id = "guess"
        input.setAttribute("aria-describedby", "guess-rule")
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
                child("li", text = cheapestLine(game, reveal))
                child("li", text = guessLine(reveal))
            }
            // What the room itself costs the month against the cheapest, before the guess's money.
            child("p", "month-cost", monthCostLine(game, reveal))
            // What the guess wins or loses as the month starts, why, and the balance September starts with.
            val money = reveal.guessMoney
            child("p", "guess-money ${if (money < 0) "guess-money-penalty" else "guess-money-reward"}") {
                child("span", "guess-money-label", guessMoneyLabel(money))
                child("span", "guess-money-amount", signedMoney(money))
            }
            child("p", "guess-why", guessMoneyWhy(reveal.guessGap))
            child("p", "left", startBalanceLine(reveal.balanceAtStart))
        }
        root.child("section", "card start") {
            child("h2", text = "9월 한 달이 시작돼요.")
            child("ul", "notes") {
                for (text in monthStartLines(room)) child("li", text = text)
            }
            replacing?.let { child("p", "replaces", replacesKept(it)).id = "replaces" }
            button("primary", text = "${date(Month.FIRST_DAY)} 시작하기") {
                if (replacing != null) setAttribute("aria-describedby", "replaces")
                onclick = {
                    val room = checkNotNull(picked)
                    beginMonth(game.startMonth(room, reveal.guess), MonthRecord(game.seed, today, room, reveal.guess))
                }
            }
        }
    }

    /** Opens [started] for play, in a town to walk in where there is one, and keeps it in the slot as [keeping]. */
    private fun beginMonth(started: Month, keeping: MonthRecord) {
        month = started
        record = keeping
        slot?.keep(keeping)
        // 내 방 stands where the room is: the room's zone decides where it is and where each morning starts.
        walkable = makeWalkableTown(started.room.zone.number)?.also { listenTo(it) }
        if (walkable == null) town = makeTown()
        guide?.onOpenChange = { walkable?.isCovered = isTownCovered() }
        // In the town the update news open from the guide's foot, never over a talk or the change of day.
        guide?.notes = { notes.takeIf { talkingAt == null && chattingWith == null && !dayChanging } }
        show()
        window.scrollTo(0.0, 0.0)
    }

    /** The month kept from an earlier visit for this run, read once, when the page first shows. */
    private fun keptMonth(): KeptMonth? {
        if (!keptRead) {
            keptRead = true
            kept = slot?.keptFor(game.seed, today)
            replacing = slot?.otherRunDay(game.seed, today)
        }
        return kept
    }

    /** Drops the kept month and shows the run's rooms, or plays a new run when the address named none. */
    private fun startOver() {
        slot?.clear()
        if (startOverWithNewRun) {
            playAgain()
            return
        }
        kept = null
        toRooms()
    }

    /** Goes on with [offer] on the day it reached, with that day's log, as if the page had never closed. */
    private fun goOn(offer: KeptMonth) {
        kept = null
        val record = offer.record
        val month = offer.replayed.month
        picked = record.room
        guess = "${record.guess}"
        reveal = offer.replayed.reveal
        if (record.days.isNotEmpty()) {
            lastDay = month.day - 1
            lastEvents = offer.replayed.lastEvents
            lastLines = dayLog(lastEvents, month)
        }
        // What was bought is the month's own; what was spent today goes into today's log, as if never closed.
        spentToday = offer.replayed.spentToday
        // Who was met is not kept, but the people of the places where days were spent know the player by now.
        for (choice in record.days) {
            met += personAt(if (choice is Choice.Take) placeFor(choice.action) else INTERVIEW_PLACE)
        }
        // In the town, the focus lands where a day ends: on the day before's log, or on the top card on the first day.
        focusNext = if (record.days.isEmpty()) ".hud" else ".over-town .log"
        beginMonth(month, record)
    }

    private fun showDay(month: Month) {
        root.child("header", "topbar") {
            dayFigure(month.day, weatherOf(month.day))
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
        // The same update news as by the title, at the foot, out of the way of the day's figures.
        notes?.let { news -> root.child("footer", "patch-foot") { news.button(this, root) } }
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
                figure(guessMoneyLabel(reveal.guessMoney), signedMoney(reveal.guessMoney))
                figure("영어", "${month.english}/${Month.MAX_SKILL}")
                figure("코딩", "${month.coding}/${Month.MAX_SKILL}")
            }
            if (today != null) result(todaysRunResult(today, reveal.guessMoney, ending, month.day))
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
            // The update news and the sound switch share the brand's line, so the title has the width to itself.
            child("div", "title-bar") {
                child("p", "brand", "BNI Tech")
                if (notes != null || sound != null) {
                    child("div", "title-controls") {
                        notes?.button(this, root)
                        sound?.button(this)
                    }
                }
            }
            child("div", "title-name") {
                child("h1", text = "밴쿠버 한 달 (가제)")
                child("p", "patch-current", PatchNotes.versionLabel(PatchNotes.CURRENT))
            }
            child("p", "run", if (today == null) "판 번호 ${game.seed}" else "${todaysRunName(today)} · 판 번호 ${game.seed}")
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
        // No day ends while the character travels: it gets out at its stop first.
        if (walkable?.travelling != null) return
        lastDay = month.day
        lastEvents = spentToday + month.play(choice)
        lastLines = dayLog(lastEvents, month)
        spentToday = emptyList()
        rideNote = null
        talkingAt = null
        atShop = false
        choosing = null
        renting = null
        record = record?.after(choice)
        if (month.isOver) {
            slot?.clear()
            closeTown()
        } else {
            record?.let { slot?.keep(it) }
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
        town.onPlace = { place ->
            val open = place != talkingAt || chattingWith != null || atShop || choosing != null || renting != null
            if (!dayChanging && town.travelling == null && open) talkTo(place)
        }
        town.onPasserby = { passer -> if (!dayChanging && town.travelling == null) chatWith(passer) }
        town.onShop = { if (!dayChanging && town.travelling == null) openShop() }
        town.onRent = { ride -> if (!dayChanging && town.travelling == null) rent(ride) }
        town.onStop = { stop -> if (!dayChanging && town.travelling == null) choose(stop.transport, stop.index) }
        town.onTravelled = {
            travelPaid = null
            // Not out from under the guide, if it was opened on the way.
            if (guide?.isOpen != true) focusNext = ".hud"
            show()
        }
        town.onMorning = { dayChanging = false }
        town.onSounds = { train, fire, night -> sound?.nearby(train, fire, night) }
        town.onFail = {
            walkable = null
            talkingAt = null
            atShop = false
            choosing = null
            renting = null
            travelPaid = null
            dayChanging = false
            show()
        }
    }

    /** Opens the talk with [place]'s person, who greets the player as the day suggests, or closes it for null. */
    private fun talkTo(place: Place?) {
        val current = month ?: return
        talkingAt = place
        atShop = false
        choosing = null
        renting = null
        if (chattingWith != null) walkable?.endChat()
        chattingWith = null
        smallTalks = 0
        if (place != null) {
            // A talk opens on foot: the character gets off whatever it rides.
            walkable?.getOff()
            val person = personAt(place)
            talkLine = greeting(person, current, person !in met, lastEvents, weatherOf(current.day))
            met.add(person)
            talkOpened = window.performance.now()
            focusNext = ".talk"
            sound?.blip()
        }
        show()
    }

    /** True while a card lies over most of the town: a talk, a small talk or the guide on how to play. */
    private fun isTownCovered(): Boolean =
        talkingAt != null || chattingWith != null || atShop || choosing != null || renting != null ||
            guide?.isOpen == true

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
        town.weather = weatherOf(month.day)
        root.child("div", "over-town") {
            child("header", "hud") {
                setAttribute("tabindex", "-1")
                child("div", "hud-figures") {
                    dayFigure(month.day, weatherOf(month.day))
                    figure("남은 날", "${month.daysLeft}일")
                    figure("잔고", money(month.balance))
                    figure("10월 총비용", money(month.room.totalMonthlyCost))
                }
                child("div", "hud-line") {
                    child("p", "hud-stats") {
                        // On a narrow phone the line breaks between two skills, never inside one.
                        listOf(
                            "영어 ${month.english}/${Month.MAX_SKILL}",
                            "코딩 ${month.coding}/${Month.MAX_SKILL}",
                            "체력 ${month.energy}/${Month.MAX_ENERGY}",
                        ).forEachIndexed { index, skill ->
                            if (index > 0) appendChild(document.createTextNode(" · "))
                            child("span", text = skill)
                        }
                    }
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
                rides(month, town)
            }
            showLog(this)
            querySelector(".log")?.setAttribute("tabindex", "-1")
            val talking = talkingAt
            val chatting = chattingWith
            val where = choosing
            val rentable = renting
            if (where != null) {
                chooser(month, town, where)
            } else if (rentable != null) {
                rideChooser(month, town, rentable)
            } else if (atShop) {
                shopTalk(month)
            } else if (talking != null) {
                talk(month, talking)
            } else if (chatting != null) {
                chat(chatting)
            } else if (lastLines.isEmpty()) {
                // On the first day, at the bottom where the talks open, how to get about.
                child("p", "town-hint", "땅을 누르면 걷고, 두 번 누르면 빨리 가요. 사람이나 건물을 누르면 말을 걸어요.")
            }
        }
        // The names of the buildings hide where the top card and the day's log cover the town.
        town.coveredTop = { coverOf(town) }
        // A keyboard or a screen reader follows what happens: into a talk that opens, to the log after an answer.
        focusNext?.let { (root.querySelector(it) as? HTMLElement)?.focus() }
        focusNext = null
    }

    /** How far down from the top of [town] the top card and the day's log reach, in CSS pixels. */
    private fun coverOf(town: WalkableTown): Double {
        val top = town.element.getBoundingClientRect().top
        return root.querySelectorAll(".over-town .hud, .over-town .log").asList()
            .maxOfOrNull { (it as HTMLElement).getBoundingClientRect().bottom - top } ?: 0.0
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
        atShop = false
        choosing = null
        renting = null
        walkable?.getOff()
        if (current.day != chatsDay) {
            chats.clear()
            chatsDay = current.day
        }
        val count = chats[passer] ?: 0
        chats[passer] = count + 1
        chattingWith = passer
        talkLine = passerLine(passer, current.day, count, weatherOf(current.day))
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

    /**
     * One row on the town's top card: a button to get on each thing the player owns, and 택시 부르기 while nothing lies
     * over the town; or, while riding, what is ridden (with the fare for a shared one) and 내리기; or, while travelling,
     * what on and what it cost, with 내리기 for a taxi; and why a shared one could not be rented. Taps while the day
     * changes do nothing, as the town's do.
     */
    private fun HTMLElement.rides(month: Month, town: WalkableTown) {
        val riding = town.riding
        // What a shared ride cost is forgotten once off it, however the ride ended.
        if (riding == null) ridePaid = null
        val note = rideNote
        // A taxi is called from the open town: not over a talk, which keeps the top card short on a small phone.
        val offerTaxi = riding == null && !isTownCovered()
        if (town.travelling == null && riding == null && month.owned.isEmpty() && note == null && !offerTaxi) return
        child("div", "hud-ride") {
            val travelling = town.travelling
            if (travelling != null) {
                // On the way: what is ridden, and for a taxi a way out; no talk and no other ride until it is over.
                child("span", "hud-riding", Transit.riding(travelling, travelPaid))
                if (travelling == Transport.TAXI) {
                    button("hud-ride-button", text = Riding.GET_OFF) {
                        onclick = {
                            if (!dayChanging) {
                                town.leaveTaxi()
                                travelPaid = null
                                focusNext = ".hud"
                                show()
                            }
                        }
                    }
                }
                return@child
            }
            note?.let { child("span", "hud-ride-note", it).setAttribute("aria-live", "polite") }
            if (riding != null) {
                val paid = ridePaid
                val now = if (riding.fare != null && paid != null) {
                    Riding.rented(riding.vehicle.item, paid)
                } else {
                    Riding.now(riding.vehicle.item, shared = riding.fare != null)
                }
                child("span", "hud-riding", now)
                button("hud-ride-button", text = Riding.GET_OFF) {
                    onclick = {
                        if (!dayChanging) {
                            town.getOff()
                            focusNext = ".hud-ride-button"
                            show()
                        }
                    }
                }
                return@child
            }
            for (item in Item.entries) {
                if (!month.owns(item)) continue
                button("hud-ride-button", text = Riding.getOn(item)) {
                    onclick = {
                        if (!dayChanging) {
                            // Getting on ends a talk, as a talk ends a ride.
                            closeTalks()
                            rideNote = null
                            town.ride(Ride.own(item))
                            focusNext = ".hud-ride-button"
                            show()
                        }
                    }
                }
            }
            if (offerTaxi) button("hud-ride-button hud-taxi", text = Transit.CALL_TAXI) {
                onclick = {
                    if (!dayChanging) {
                        town.standStill()
                        choose(Transport.TAXI, -1)
                    }
                }
            }
        }
    }

    /**
     * Opens where to go on [transport] from stop [from] (for a taxi, -1: from where the character stands), over the
     * town, as a talk opens. A taxi's fare to each place is worked out now, for where the character stands.
     */
    private fun choose(transport: Transport, from: Int) {
        val town = walkable ?: return
        closeTalks()
        rideNote = null
        choosing = transport
        choosingFrom = from
        taxiFares = if (transport == Transport.TAXI) {
            TransitLines.taxiStopsFor(town.homeZone).map { (_, spot) ->
                town.taxiDistance(spot)?.let { TaxiFare.forDistance(it) }
            }
        } else {
            emptyList()
        }
        talkOpened = window.performance.now()
        focusNext = ".talk"
        sound?.blip()
        show()
    }

    /**
     * Where to go from here on [transport]: each stop (or each place a taxi goes) with its name and its fare, or why it
     * cannot be paid now, and 그만. A place the taxi cannot reach, or the stop the character is at, is left out.
     */
    private fun HTMLElement.chooser(month: Month, town: WalkableTown, transport: Transport) {
        val from = choosingFrom
        val fromName = if (from >= 0) TransitLines.nameOf(Stop(transport, from)) else null
        val empty = if (transport == Transport.TAXI && taxiFares.all { it == null }) Transit.NOWHERE else null
        val stop = { choosing = null }
        whereTo(Transit.title(transport, fromName), Transit.where(transport), empty, stop) {
            for (index in 0 until TransitLines.count(transport)) {
                // A bus goes only along its own line.
                if (index == from || !TransitLines.joins(transport, from, index)) continue
                val fare = if (transport == Transport.TAXI) taxiFares.getOrNull(index) ?: continue else 0
                val kind = transport.fare
                val amount = if (kind == null) 0 else if (transport == Transport.TAXI) fare else kind.price
                val blocked = kind?.let { month.spendBlockedBy(it, amount) }
                button("keyword go") {
                    disabled = blocked != null
                    child("span", "action-name", TransitLines.nameOf(Stop(transport, index)))
                    child("span", "action-effect", Transit.fare(transport, amount))
                    if (blocked != null) child("span", "action-why", Transit.cannotPay(blocked))
                    onclick = { go(month, town, transport, from, index, amount) }
                }
            }
        }
    }

    /**
     * Where to ride the shared [ride] from here: each place a taxi goes with its fare by the way there, or why it cannot
     * be paid now, and 그만. A place it cannot reach, or one right here, is left out.
     */
    private fun HTMLElement.rideChooser(month: Month, town: WalkableTown, ride: Ride) {
        val kind = ride.fare ?: return
        val fare = SharedFare.of(kind) ?: return
        val empty = if (rideFares.all { it == null }) Riding.NOWHERE else null
        val stop = { renting = null }
        whereTo(Riding.shared(ride.vehicle.item), Riding.where(fare), empty, stop) {
            for ((index, place) in TransitLines.taxiStopsFor(town.homeZone).withIndex()) {
                val amount = rideFares.getOrNull(index) ?: continue
                val blocked = month.spendBlockedBy(kind, amount)
                button("keyword go") {
                    disabled = blocked != null
                    child("span", "action-name", place.first)
                    child("span", "action-effect", money(amount))
                    if (blocked != null) child("span", "action-why", Transit.cannotPay(blocked))
                    onclick = { rideOff(month, town, ride, place.second, amount) }
                }
            }
        }
    }

    /**
     * A card over the town that asks where to go, as a talk opens: [title], the [line] under it, [empty] when there is
     * nowhere to go, the places [rows] adds, and 그만, which closes it after [stop].
     */
    private fun HTMLElement.whereTo(
        title: String,
        line: String,
        empty: String?,
        stop: () -> Unit,
        rows: HTMLElement.() -> Unit,
    ) {
        child("section", "talk chooser") {
            setAttribute("role", "dialog")
            setAttribute("aria-label", title)
            setAttribute("tabindex", "-1")
            // As in the talks, the tap that opened it does nothing on it for a moment.
            addEventListener("click", { event ->
                if (window.performance.now() - talkOpened < talkSettleMillis) {
                    event.stopPropagation()
                    event.preventDefault()
                }
            }, true)
            child("p", "talk-name") {
                child("strong", text = title)
            }
            child("p", "talk-line", line)
            if (empty != null) child("p", "chooser-empty", empty)
            child("div", "keywords chooser-list") { rows() }
            child("div", "keywords") {
                button("keyword", text = Transit.STOP) {
                    onclick = {
                        stop()
                        focusNext = ".hud"
                        show()
                    }
                }
            }
        }
    }

    /**
     * Rides [transport] from [from] to [to]: the fare [amount] paid first through the month's rules when it takes one,
     * and only when the town can set off, so a fare is never paid for a ride not taken. No day goes by.
     */
    private fun go(month: Month, town: WalkableTown, transport: Transport, from: Int, to: Int, amount: Int) {
        if (dayChanging || choosing != transport || !town.canTravel(transport, from, to)) return
        val kind = transport.fare
        if (kind != null && !pay(month, kind, amount)) return
        travelPaid = if (kind != null) amount else null
        choosing = null
        town.travel(transport, from, to)
        focusNext = ".hud"
        show()
    }

    /** Closes whatever talk is open: a place's, a passerby's or the shop keeper's, or a card asking where to go. */
    private fun closeTalks() {
        talkingAt = null
        atShop = false
        choosing = null
        renting = null
        if (chattingWith != null) walkable?.endChat()
        chattingWith = null
    }

    /** Opens the talk with the bike shop's keeper, on foot. No day is spent on it. */
    private fun openShop() {
        val current = month ?: return
        closeTalks()
        walkable?.getOff()
        atShop = true
        shopTalks = 0
        talkLine = Shop.greeting(!metKeeper, current.owned)
        metKeeper = true
        talkOpened = window.performance.now()
        focusNext = ".talk"
        sound?.blip()
        show()
    }

    /** The talk with the keeper: a button to buy each, with its price or why not, small talk, and 그만. */
    private fun HTMLElement.shopTalk(month: Month) {
        child("section", "talk shop-talk") {
            setAttribute("role", "dialog")
            setAttribute("aria-label", Shop.KEEPER)
            setAttribute("tabindex", "-1")
            // As in the other talks, the tap that opened it does nothing on it for a moment.
            addEventListener("click", { event ->
                if (window.performance.now() - talkOpened < talkSettleMillis) {
                    event.stopPropagation()
                    event.preventDefault()
                }
            }, true)
            child("div", "talk-head") {
                face(Figures.keeperLook)
                child("p", "talk-name") {
                    child("strong", text = Shop.KEEPER)
                    child("span", "talk-role", "${Shop.ROLE} · ${Shop.NAME}")
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
                for (item in Item.entries) {
                    val kind = Spending.entries.first { it.buys == item }
                    val blocked = month.spendBlockedBy(kind)
                    button("keyword buy") {
                        disabled = blocked != null
                        child("span", "action-name", Shop.buy(item))
                        child("span", "action-effect", money(kind.price))
                        if (blocked != null) child("span", "action-why", whyNotSpend(blocked))
                        onclick = { buy(month, kind) }
                    }
                }
                button("keyword", text = "이야기") {
                    onclick = {
                        talkLine = Shop.smallTalk(shopTalks)
                        shopTalks += 1
                        focusNext = ".talk"
                        show()
                    }
                }
                button("keyword", text = "그만") {
                    onclick = {
                        atShop = false
                        focusNext = ".hud"
                        show()
                    }
                }
            }
        }
    }

    /** Buys [kind] from the keeper, who says how to get on it. */
    private fun buy(month: Month, kind: Spending) {
        val item = kind.buys ?: return
        if (!pay(month, kind)) return
        talkLine = Shop.bought(item)
        focusNext = ".talk"
        show()
    }

    /**
     * At a shared bike or e-scooter: opens where to ride it, as a talk opens, each place with its fare by the way there
     * from where the character stands; or says why it cannot be rented, when even the cheapest place's fare cannot be
     * paid. Nothing is paid until a place is chosen ([rideOff]).
     */
    private fun rent(ride: Ride) {
        val current = month ?: return
        val town = walkable ?: return
        val kind = ride.fare ?: return
        val fare = SharedFare.of(kind) ?: return
        if (!town.canRide(ride)) return
        val fares = TransitLines.taxiStopsFor(town.homeZone).map { (_, spot) ->
            town.taxiDistance(spot)?.let { fare.forDistance(it) }
        }
        val blocked = current.spendBlockedBy(kind, fares.filterNotNull().minOrNull() ?: fare.base)
        if (blocked != null) {
            rideNote = Riding.cannotRent(kind, blocked)
            show()
            return
        }
        closeTalks()
        rideNote = null
        renting = ride
        rideFares = fares
        talkOpened = window.performance.now()
        focusNext = ".talk"
        sound?.blip()
        show()
    }

    /**
     * Rides the shared [ride] to [to] for [fare], the fare its card showed: paid first through the month's rules, and
     * only when the town can set off there, so a fare is never paid for a ride not taken. No day goes by.
     */
    private fun rideOff(month: Month, town: WalkableTown, ride: Ride, to: Spot, fare: Int) {
        val kind = ride.fare ?: return
        if (dayChanging || renting != ride || !town.canRideTo(ride, to)) return
        if (!pay(month, kind, fare)) return
        renting = null
        ridePaid = fare
        town.rideTo(ride, to)
        focusNext = ".hud-ride-button"
        show()
    }

    /**
     * Spends [amount] (by default [kind]'s price) on [kind] through the month's rules, without spending the day: kept for
     * today's log and in the slot.
     * False when the month does not allow it.
     */
    private fun pay(month: Month, kind: Spending, amount: Int = kind.price): Boolean {
        if (!month.canSpend(kind, amount)) return false
        val spent = month.spend(kind, amount)
        spentToday = spentToday + spent
        record = record?.after(Spend(kind, spent.amount))
        record?.let { slot?.keep(it) }
        sound?.blip()
        return true
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

/**
 * Today's date with its weather in a word after it, which a screen reader hears as "9월 3일 날씨 비" and so on. The
 * hidden label starts with a space, so the date and "날씨" are never read as one word.
 */
private fun HTMLElement.dayFigure(day: Int, weather: Weather) {
    child("div", "figure") {
        child("span", "figure-label", "오늘")
        child("span", "figure-value") {
            appendChild(document.createTextNode(date(day)))
            child("span", "weather") {
                child("span", "visually-hidden", " 날씨 ")
                appendChild(document.createTextNode(weatherWord(weather)))
            }
        }
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

/** Runs [work] when the browser has nothing else to do, or straight after this task where it cannot tell when. */
private fun whenIdle(work: () -> Unit) {
    if (window.asDynamic().requestIdleCallback == undefined) {
        window.setTimeout({ work() }, 0)
    } else {
        window.asDynamic().requestIdleCallback({ work() })
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
