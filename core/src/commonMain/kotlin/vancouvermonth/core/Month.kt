package vancouvermonth.core

import kotlin.math.min

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

/** What happened on a day, in order, for the screen to show. */
sealed interface Event {
    data class Earned(val amount: Int) : Event
    data class SkillUp(val skill: Skill, val level: Int) : Event
    data class Applied(val interview: Interview?) : Event
    data class Rested(val energy: Int) : Event
    data class InterviewHeld(val stage: InterviewStage, val passed: Boolean, val next: Interview?) : Event
    data class LivingCost(val amount: Int) : Event
}

/**
 * September in Vancouver, one action a day, after the room was picked on September 1st.
 * The goal is a job offer by September 30th. On October 1st the room's total monthly cost is due again.
 * Every day, the offer day included, costs living money; what is left after the first room always covers it,
 * so the money pressure is October's total. All numbers are invented.
 */
class Month(
    val room: Room,
    startingBalance: Int,
    private val dice: Dice,
) {
    var day: Int = FIRST_DAY
        private set
    var balance: Int = startingBalance
        private set
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

    val isOver: Boolean
        get() = ending != null

    val hasInterviewToday: Boolean
        get() = !isOver && interview?.day == day

    val daysLeft: Int
        get() = LAST_DAY - day + 1

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
