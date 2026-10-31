package vancouvermonth.core

import kotlin.random.Random

/** Every piece of luck in a month goes through this, so a test can hand in luck it controls. */
interface Dice {
    /** True with the given [chance], from 0.0 to 1.0. */
    fun succeeds(chance: Double): Boolean

    /** How many days from now something lands, picked from [range]. */
    fun daysLater(range: IntRange): Int
}

class RandomDice(seed: Int) : Dice {
    private val random = Random(seed)

    override fun succeeds(chance: Double): Boolean = random.nextDouble() < chance

    override fun daysLater(range: IntRange): Int = random.nextInt(range.first, range.last + 1)
}
