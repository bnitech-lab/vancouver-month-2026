package vancouvermonth.core

import kotlin.math.min
import kotlin.math.roundToInt

/** What the player can do with one day. */
enum class Action { WORK, STUDY_ENGLISH, PRACTISE_CODING, APPLY, REST }

enum class Skill { ENGLISH, CODING }

/** A job application turns into a phone screen (English) and then a technical interview (coding). */
enum class InterviewStage { PHONE_SCREEN, TECHNICAL }

data class Interview(val stage: InterviewStage, val day: Int)

enum class Ending {
    /** A job offer before the month ran out. */
    OFFER,

    /** The month ended without an offer, and next month's total cannot be paid. */
    CANNOT_PAY_RENT,

    /** The month ended without an offer, but next month's total can be paid. */
    SURVIVED,
}

/** Why an action cannot be taken today. */
enum class Blocked {
    /** The month has ended. */
    MONTH_OVER,

    /** Today belongs to the interview that falls on it. */
    INTERVIEW_TODAY,

    /** Out of energy: only rest is possible. */
    NO_ENERGY,

    /** An application is already in progress. */
    INTERVIEW_BOOKED,

    /** An application now could not get both interviews done by the 30th. */
    APPLICATIONS_CLOSED,
}

/** Something the player can buy once and keep for the rest of the month. */
enum class Item { BIKE, E_SCOOTER }

/**
 * What money can go on in the town without spending a day: something to keep ([buys]), or one ride's fare. Each costs
 * [price]; a taxi's fare depends on the trip, so it may cost anything from its [price] up to [most] ([TaxiFare]). All
 * of them are invented. A ride that is free with the month's pass (the SkyTrain, the bus) needs no spending at all.
 */
enum class Spending(val price: Int, val buys: Item? = null, val most: Int = price) {
    BIKE(350, Item.BIKE),
    E_SCOOTER(550, Item.E_SCOOTER),
    SHARED_BIKE_RIDE(3),
    SHARED_E_SCOOTER_RIDE(4),
    WATER_BUS_RIDE(4),
    TAXI_RIDE(TaxiFare.BASE, most = TaxiFare.MOST),
    ;

    /** True when [amount] is what this costs: its price, or for a taxi any fare its meter can show. */
    fun costs(amount: Int): Boolean = amount in price..most
}

/**
 * A taxi's fare: [BASE] dollars, and [PER_TEN_SQUARES] more for every ten squares of the town's map driven (a square
 * stands for 25 m) up to [LONG_FROM] squares, a long drive's [LONG_PER_TEN_SQUARES] for every ten past that, rounded to
 * the dollar; the meter stops at [MOST]. A drive across downtown costs what it always did, and one out to Burnaby less
 * than twice the short rate would make it. Invented, like every price in the game.
 */
object TaxiFare {
    const val BASE = 4
    const val PER_TEN_SQUARES = 1.5
    const val LONG_FROM = 160.0
    const val LONG_PER_TEN_SQUARES = 0.7
    const val MOST = 60

    /** The fare for a drive of [squares] squares along the streets. */
    fun forDistance(squares: Double): Int {
        val driven = squares.coerceAtLeast(0.0)
        val short = driven.coerceAtMost(LONG_FROM)
        val long = driven - short
        val fare = (BASE + short / 10 * PER_TEN_SQUARES + long / 10 * LONG_PER_TEN_SQUARES).roundToInt()
        return fare.coerceAtMost(MOST)
    }
}

/** Why money cannot be spent now. */
enum class SpendBlocked {
    /** The month has ended. */
    MONTH_OVER,

    /** The player already has what it buys. */
    ALREADY_OWNED,

    /** The balance is less than it costs. */
    NOT_ENOUGH_MONEY,

    /** What would be left could not pay the living cost of every day left in September, today included. */
    LIVING_COST,
}

/** What happened on a day, in order, for the screen to show. */
sealed interface Event {
    data class Earned(val amount: Int) : Event
    data class SkillUp(val skill: Skill, val level: Int) : Event
    data class Applied(val interview: Interview?) : Event
    data class Rested(val energy: Int) : Event
    data class InterviewHeld(val stage: InterviewStage, val passed: Boolean, val next: Interview?) : Event
    data class LivingCost(val amount: Int) : Event

    /** Money spent in the town on [kind], during the day: no day goes by for it. */
    data class Spent(val kind: Spending, val amount: Int) : Event

    /**
     * As the month started: the guess at the room's total, [gap] dollars off, won [amount] (or lost it, when it is
     * below zero, or neither at zero). See [FirstMinute.guessMoney].
     */
    data class Guessed(val gap: Int, val amount: Int) : Event
}

/**
 * September in Vancouver, one action a day, after the room was picked on September 1st.
 * The goal is a job offer by September 30th. On October 1st the room's total monthly cost is due again.
 * Every day, the offer day included, costs living money; what is left after the first room, even after the most a
 * guess can lose, always covers it, and money spent in the town never leaves less than the living cost of the days
 * left, so the balance never goes below zero in September and the money pressure is October's total. All numbers are
 * invented.
 */
class Month(
    val room: Room,
    startingBalance: Int,
    private val dice: Dice,
    /** The guess's money, added to [startingBalance] once as the month starts; null for a month with no guess. */
    guessed: Event.Guessed? = null,
) {
    var day: Int = FIRST_DAY
        private set
    var balance: Int = startingBalance + (guessed?.amount ?: 0)
        private set

    /** What happened as the month started, before its first day: the guess's money, when there was a guess. */
    val opening: List<Event> = listOfNotNull(guessed)
    var english: Int = START_ENGLISH
        private set
    var coding: Int = START_CODING
        private set
    var energy: Int = MAX_ENERGY
        private set
    var interview: Interview? = null
        private set
    var ending: Ending? = null
        private set

    /** What the player has bought this month and keeps until its end. */
    var owned: Set<Item> = emptySet()
        private set

    val isOver: Boolean
        get() = ending != null

    val hasInterviewToday: Boolean
        get() = !isOver && interview?.day == day

    val daysLeft: Int
        get() = LAST_DAY - day + 1

    /**
     * What the balance would be on October 1st, before October's total is paid, if every day left, today included,
     * cost only living money: no more pay and no offer.
     */
    val balanceForecast: Int
        get() = balance - daysLeft * DAILY_LIVING_COST

    /** True when that forecast falls short of October's total, so the money is tight. */
    val moneyTight: Boolean
        get() = balanceForecast < room.totalMonthlyCost

    /** Chance that an application turns into a phone screen. A better coding level makes a better résumé. */
    val applyChance: Double
        get() = min(MAX_CHANCE, APPLY_BASE + APPLY_PER_CODING * coding)

    fun passChance(stage: InterviewStage): Double = when (stage) {
        InterviewStage.PHONE_SCREEN -> min(MAX_CHANCE, PHONE_SCREEN_BASE + PHONE_SCREEN_PER_ENGLISH * english)
        InterviewStage.TECHNICAL -> min(MAX_CHANCE, TECHNICAL_BASE + TECHNICAL_PER_CODING * coding)
    }

    /** False from the day an application could no longer get both interviews done by the 30th. */
    val canStillApplyThisMonth: Boolean
        get() = day + PHONE_SCREEN_DELAY.first <= LAST_PHONE_SCREEN_DAY

    /** Why [action] cannot be taken today, or null when it can. */
    fun blockedBy(action: Action): Blocked? = when {
        isOver -> Blocked.MONTH_OVER
        hasInterviewToday -> Blocked.INTERVIEW_TODAY
        action == Action.REST -> null
        energy == 0 -> Blocked.NO_ENERGY
        action == Action.APPLY && interview != null -> Blocked.INTERVIEW_BOOKED
        action == Action.APPLY && !canStillApplyThisMonth -> Blocked.APPLICATIONS_CLOSED
        else -> null
    }

    fun canTake(action: Action): Boolean = blockedBy(action) == null

    /** Spends today on [action]. */
    fun take(action: Action): List<Event> {
        require(canTake(action)) { "Cannot take $action on day $day" }
        val event = when (action) {
            Action.WORK -> {
                balance += WORK_PAY
                energy -= 1
                Event.Earned(WORK_PAY)
            }
            Action.STUDY_ENGLISH -> {
                english = min(MAX_SKILL, english + 1)
                energy -= 1
                Event.SkillUp(Skill.ENGLISH, english)
            }
            Action.PRACTISE_CODING -> {
                coding = min(MAX_SKILL, coding + 1)
                energy -= 1
                Event.SkillUp(Skill.CODING, coding)
            }
            Action.APPLY -> {
                energy -= 1
                if (dice.succeeds(applyChance)) {
                    val phoneScreenDay = min(LAST_PHONE_SCREEN_DAY, day + dice.daysLater(PHONE_SCREEN_DELAY))
                    interview = Interview(InterviewStage.PHONE_SCREEN, phoneScreenDay)
                }
                Event.Applied(interview)
            }
            Action.REST -> {
                energy = min(MAX_ENERGY, energy + REST_GAIN)
                Event.Rested(energy)
            }
        }
        return listOf(event) + endDay()
    }

    fun owns(item: Item): Boolean = item in owned

    /**
     * Why [amount] cannot be spent on [kind] now, or null when it can: the month is over, it is already owned, the
     * balance is short of it, or what is left would not pay the living cost of every day left, today included. An
     * interview day does not stop it.
     */
    fun spendBlockedBy(kind: Spending, amount: Int = kind.price): SpendBlocked? = when {
        isOver -> SpendBlocked.MONTH_OVER
        kind.buys != null && owns(kind.buys) -> SpendBlocked.ALREADY_OWNED
        balance < amount -> SpendBlocked.NOT_ENOUGH_MONEY
        balance - amount < daysLeft * DAILY_LIVING_COST -> SpendBlocked.LIVING_COST
        else -> null
    }

    /** True when [amount] is what [kind] costs and nothing stops spending it now. */
    fun canSpend(kind: Spending, amount: Int = kind.price): Boolean =
        kind.costs(amount) && spendBlockedBy(kind, amount) == null

    /**
     * Spends [amount] on [kind]: it comes off the balance, and what it buys is kept for the rest of the month. No day
     * goes by, nothing else changes and no luck is used, so the days before and after play out as they would have,
     * with less money: October's total may then be out of reach, but September's living cost never is.
     */
    fun spend(kind: Spending, amount: Int = kind.price): Event.Spent {
        require(kind.costs(amount)) { "$kind does not cost $amount" }
        val blocked = spendBlockedBy(kind, amount)
        require(blocked == null) { "Cannot spend $amount on $kind on day $day: $blocked" }
        balance -= amount
        kind.buys?.let { owned = owned + it }
        return Event.Spent(kind, amount)
    }

    /** Spends today on the interview that falls on it. */
    fun attendInterview(): List<Event> {
        val today = interview
        check(today != null && hasInterviewToday) { "No interview on day $day" }
        val passed = dice.succeeds(passChance(today.stage))
        val next = if (passed && today.stage == InterviewStage.PHONE_SCREEN) {
            // A phone screen is never later than the 29th, so the technical interview still fits in September.
            Interview(InterviewStage.TECHNICAL, min(LAST_DAY, day + dice.daysLater(TECHNICAL_DELAY)))
        } else {
            null
        }
        interview = next
        val held = Event.InterviewHeld(today.stage, passed, next)
        val offer = passed && today.stage == InterviewStage.TECHNICAL
        return listOf(held) + endDay(offer)
    }

    private fun endDay(offer: Boolean = false): List<Event> {
        balance -= DAILY_LIVING_COST
        when {
            offer -> ending = Ending.OFFER
            day == LAST_DAY -> ending = if (balance >= room.totalMonthlyCost) Ending.SURVIVED else Ending.CANNOT_PAY_RENT
            else -> day += 1
        }
        return listOf(Event.LivingCost(DAILY_LIVING_COST))
    }

    companion object {
        /** September 1st goes to picking a room, so the month starts on the 2nd. */
        const val FIRST_DAY = 2
        const val LAST_DAY = 30

        /** The latest phone screen, leaving the 30th for the technical interview. */
        const val LAST_PHONE_SCREEN_DAY = LAST_DAY - 1

        const val DAILY_LIVING_COST = 40
        const val WORK_PAY = 110

        const val MAX_SKILL = 10
        const val START_ENGLISH = 1
        const val START_CODING = 3
        const val MAX_ENERGY = 5
        const val REST_GAIN = 2

        const val MAX_CHANCE = 0.9
        const val APPLY_BASE = 0.15
        const val APPLY_PER_CODING = 0.04
        const val PHONE_SCREEN_BASE = 0.05
        const val PHONE_SCREEN_PER_ENGLISH = 0.09
        const val TECHNICAL_BASE = 0.05
        const val TECHNICAL_PER_CODING = 0.08

        val PHONE_SCREEN_DELAY = 2..4
        val TECHNICAL_DELAY = 2..3
    }
}
