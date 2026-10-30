package vancouvermonth.core

import kotlin.random.Random

/**
 * Deals three rooms, one per zone, in a random order. The same seed always deals the same rooms.
 *
 * Rent drops by about 100 a zone while the pass rises by 40 to 50, and utilities may or may not be included,
 * so the room with the lowest rent has the lowest total in only a bit more than half of the deals.
 * Every amount is a multiple of 10.
 */
object RoomDealer {
    const val BASE_RENT = 1_250
    const val RENT_DROP_PER_ZONE = 100
    const val RENT_SPREAD = 80
    const val UTILITIES_INCLUDED_CHANCE = 0.4
    val UTILITIES_RANGE = 120..190

    fun deal(seed: Int): List<Room> {
        val random = Random(seed)
        return Zone.entries.shuffled(random).map { zone -> dealRoom(zone, random) }
    }

    private fun dealRoom(zone: Zone, random: Random): Room {
        val utilitiesIncluded = random.nextDouble() < UTILITIES_INCLUDED_CHANCE
        val middleRent = BASE_RENT - zone.number * RENT_DROP_PER_ZONE
        return Room(
            rent = middleRent + tensBetween(-RENT_SPREAD, RENT_SPREAD, random),
            zone = zone,
            utilitiesIncluded = utilitiesIncluded,
            utilities = if (utilitiesIncluded) 0 else tensBetween(UTILITIES_RANGE.first, UTILITIES_RANGE.last, random),
        )
    }

    /** A random multiple of 10 from [from] to [to], both included. */
    private fun tensBetween(from: Int, to: Int, random: Random): Int = random.nextInt(from / 10, to / 10 + 1) * 10
}
