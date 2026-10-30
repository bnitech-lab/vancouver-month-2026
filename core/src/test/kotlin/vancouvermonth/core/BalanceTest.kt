package vancouvermonth.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bots play hundreds of months with the real rules, the way a balance pass is done before people play.
 * The targets: a thoughtful plan lands an offer often but not always, and random clicking does clearly worse.
 */
class BalanceTest {
    private val seeds = 1..2_000

    /** Picks the cheapest room, then rests when tired, works when the rent is at risk, studies, and applies. */
    private fun thoughtful(month: Month): Action {
        if (month.energy <= 1) return Action.REST
        val stillNeeded = month.room.totalMonthlyCost + Month.DAILY_LIVING_COST * month.daysLeft - month.balance
        if (stillNeeded > 0 && month.daysLeft <= stillNeeded / Month.WORK_PAY * 1.6 + 2) return Action.WORK
        if (month.interview == null && (month.english >= 4 || month.day > 14)) return Action.APPLY
        return if (month.english < month.coding) Action.STUDY_ENGLISH else Action.PRACTISE_CODING
    }

    private fun play(seed: Int, choose: (Month, Random) -> Action): Ending {
        val game = FirstMinute(seed)
        val month = game.startMonth(game.cheapestRoomIndex)
        val random = Random(seed)
        while (!month.isOver) {
            if (month.hasInterviewToday) {
                month.attendInterview()
            } else {
                val action = choose(month, random)
                month.take(if (month.canTake(action)) action else Action.REST)
            }
        }
        return month.ending!!
    }

    private fun offerRate(choose: (Month, Random) -> Action): Double =
        seeds.count { play(it, choose) == Ending.OFFER }.toDouble() / seeds.count()

    @Test
    fun `a thoughtful plan lands an offer often but not always`() {
        val rate = offerRate { month, _ -> thoughtful(month) }
        assertTrue(rate in 0.25..0.6, "thoughtful offer rate $rate")
    }

    @Test
    fun `random choices do clearly worse than a thoughtful plan`() {
        val thoughtfulRate = offerRate { month, _ -> thoughtful(month) }
        val randomRate = offerRate { _, random -> Action.entries.random(random) }
        assertTrue(randomRate < thoughtfulRate - 0.1, "random $randomRate, thoughtful $thoughtfulRate")
    }

    @Test
    fun `resting all month never lands an offer`() {
        assertEquals(0.0, offerRate { _, _ -> Action.REST })
    }
}
