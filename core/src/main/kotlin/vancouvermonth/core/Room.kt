package vancouvermonth.core

/**
 * A made-up transit zone. The farther out a room is, the cheaper its rent and the dearer its monthly pass.
 * All numbers in this game are invented; they are not real Vancouver prices.
 */
enum class Zone(val number: Int, val monthlyPass: Int) {
    ONE(1, 105),
    TWO(2, 145),
    THREE(3, 195),
}

data class Room(
    val rent: Int,
    val zone: Zone,
    val utilitiesIncluded: Boolean,
    /** What utilities add each month: 0 when they are included in the rent. */
    val utilities: Int,
) {
    val totalMonthlyCost: Int
        get() = rent + utilities + zone.monthlyPass
}
