package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import vancouvermonth.core.Item
import vancouvermonth.core.Spending

/**
 * What the character can ride: a bike or an e-scooter, with how fast it goes in units a second (walking is 3.6) and a
 * little faster on a double tap, and how the rider sits on it: how high the body is lifted, and the turn of the legs
 * and arms from hanging down (negative is forward, as in [Figures]).
 */
enum class Vehicle(
    val item: Item,
    val speed: Double,
    val dashSpeed: Double,
    val lift: Double,
    val legs: Double,
    val arms: Double,
) {
    BIKE(Item.BIKE, 9.0, 10.5, lift = 0.3, legs = -0.4, arms = -1.25),
    E_SCOOTER(Item.E_SCOOTER, 11.0, 13.0, lift = 0.14, legs = 0.0, arms = -1.8),
}

/** One ride: the player's own [vehicle], or a shared one rented for [fare]. Each is drawn in its [colour]. */
enum class Ride(val vehicle: Vehicle, val fare: Spending?, val colour: Int) {
    BIKE(Vehicle.BIKE, null, Rides.OWN_BIKE),
    E_SCOOTER(Vehicle.E_SCOOTER, null, Rides.OWN_SCOOTER),
    SHARED_BIKE(Vehicle.BIKE, Spending.SHARED_BIKE_RIDE, Rides.SHARED),
    SHARED_E_SCOOTER(Vehicle.E_SCOOTER, Spending.SHARED_E_SCOOTER_RIDE, Rides.SHARED),
    ;

    val shared: Boolean
        get() = fare != null

    companion object {
        /** The ride on the player's own [item]. */
        fun own(item: Item): Ride = if (item == Item.BIKE) BIKE else E_SCOOTER

        /** The ride on a shared [vehicle]. */
        fun shared(vehicle: Vehicle): Ride = if (vehicle == Vehicle.BIKE) SHARED_BIKE else SHARED_E_SCOOTER
    }
}

/** A bike or an e-scooter standing in the town at [spot], turned by [turn] (0 faces south, as in [Trip]). */
data class Parked(val vehicle: Vehicle, val spot: Spot, val turn: Double)

/**
 * The bikes and e-scooters of the town, all made of boxes: the shared bikes two to a dock near the town's 내 방, 도서관,
 * 카페 and the station, and out in Burnaby by 내 방 in zones 2 and 3 and by the two stations of the town centre; the
 * shared e-scooters one to a street corner; and a bike on show beside the bike shop. None of them blocks the way. All
 * colours are invented, none a real company's.
 */
object Rides {
    const val OWN_BIKE = 0xC8463D
    const val OWN_SCOOTER = 0x2F6F73
    const val SHARED = 0x8E6BBF

    /** How near a shared one the character must stand to get on it without walking. */
    const val RENT_REACH = 1.6

    /** Where the docks stand and which way their bikes face. */
    private val docks: List<Pair<Spot, Double>> = listOf(
        Spot(108.2, 100.0) to PI,
        Spot(173.0, 72.6) to PI,
        Spot(187.4, 109.2) to PI,
        Spot(210.0, 64.2) to 0.0,
        Spot(518.4, 25.8) to PI,
        Spot(595.2, 99.8) to PI,
        Spot(550.6, 25.4) to PI,
        Spot(584.4, 18.0) to PI,
    )

    /** The ground the wide map's buildings and trees keep clear round each dock. */
    val keptForDocks: List<Lot>
        get() = docks.map { (spot, _) -> Lot(floor(spot.x).toInt() - 2, floor(spot.z).toInt() - 2, 5, 5) }

    /** How far apart the two bikes of a dock stand. */
    private const val DOCK_GAP = 0.8

    /** Every shared bike and e-scooter, in the order a tap names them. */
    val shared: List<Parked> = docks.flatMap { (spot, turn) ->
        listOf(-1, 1).map { side ->
            val across = side * DOCK_GAP / 2
            Parked(Vehicle.BIKE, Spot(spot.x + across * cos(turn), spot.z - across * sin(turn)), turn)
        }
    } + listOf(
        Parked(Vehicle.E_SCOOTER, Spot(105.6, 97.6), PI / 2),
        Parked(Vehicle.E_SCOOTER, Spot(139.6, 89.4), 0.0),
        Parked(Vehicle.E_SCOOTER, Spot(158.4, 50.4), PI),
        Parked(Vehicle.E_SCOOTER, Spot(193.0, 43.6), PI / 2),
    )

    /** The bike on show beside the shop, against its west wall, facing the street. */
    val onShow = Parked(Vehicle.BIKE, Spot(Neighbourhood.shop.x - 0.45, Neighbourhood.shop.z + 1.5), 0.0)

    /** What stands still with them: each dock's plate, rail and sign, and the bike on show. */
    val scenery: List<Block> = docks.flatMap { (spot, turn) -> placed(dock(), spot, turn) } +
        placed(model(Vehicle.BIKE, OWN_BIKE), onShow.spot, onShow.turn)

    /** Each shared one's boxes where it stands. */
    private val sharedBoxes: List<List<Block>> = shared.map { placed(model(it.vehicle, SHARED), it.spot, it.turn) }

    /** Everything above in one list, to draw at once: [scenery] first, then each shared one's boxes. */
    val boxes: List<Block> = scenery + sharedBoxes.flatten()

    /** Where in [boxes] the shared one [index] starts, and how many boxes it has. */
    fun firstBoxOf(index: Int): Int = scenery.size + sharedBoxes.take(index).sumOf { it.size }

    fun boxCountOf(index: Int): Int = sharedBoxes[index].size

    /** A box round each shared one, as a tap finds it: a little larger than it is drawn. */
    val tapBoxes: List<Block> = shared.map { Block(it.spot.x - 0.65, 0.0, it.spot.z - 0.65, 1.3, 1.3, 1.3, 0) }

    /**
     * [vehicle] as boxes in [colour], standing at (0, 0) facing south, as big as the figures' own boxes: a rider
     * sits or stands at (0, 0). A bike has two wheels, a frame, a saddle, a handlebar and a basket; an e-scooter a
     * deck, two small wheels, a tall stem and a handlebar with a lamp.
     */
    fun model(vehicle: Vehicle, colour: Int): List<Block> = when (vehicle) {
        Vehicle.BIKE -> wheel(-0.3, 0.27, 0.54) + wheel(0.56, 0.27, 0.54) + listOf(
            box(0.0, 0.27, -0.07, 0.05, 0.05, 0.46, colour),
            box(0.0, 0.44, 0.06, 0.06, 0.34, 0.06, colour),
            box(0.0, 0.6, 0.29, 0.06, 0.06, 0.52, colour),
            box(0.0, 0.47, 0.54, 0.06, 0.4, 0.06, colour),
            box(0.0, 0.76, 0.52, 0.05, 0.2, 0.05, METAL),
            box(0.0, 0.86, 0.5, 0.46, 0.05, 0.06, METAL),
            box(0.0, 0.645, 0.0, 0.15, 0.05, 0.26, SADDLE),
            box(-0.13, 0.24, 0.16, 0.08, 0.03, 0.1, TYRE),
            box(0.13, 0.24, 0.16, 0.08, 0.03, 0.1, TYRE),
            box(0.0, 0.74, 0.7, 0.26, 0.14, 0.18, BASKET),
        )
        Vehicle.E_SCOOTER -> wheel(-0.36, 0.09, 0.18) + wheel(0.48, 0.09, 0.18) + listOf(
            box(0.0, 0.11, 0.05, 0.2, 0.06, 0.8, colour),
            box(0.0, 0.55, 0.46, 0.06, 0.86, 0.06, colour),
            box(0.0, 0.98, 0.44, 0.44, 0.05, 0.06, METAL),
            box(-0.2, 0.98, 0.44, 0.08, 0.06, 0.08, TYRE),
            box(0.2, 0.98, 0.44, 0.08, 0.06, 0.08, TYRE),
            box(0.0, 0.8, 0.5, 0.1, 0.06, 0.04, LAMP),
        )
    }

    /** A dock for two bikes: a plate on the ground, a low rail at their front wheels, and a sign on a post. */
    private fun dock(): List<Block> = listOf(
        box(0.0, 0.02, 0.15, 1.7, 0.04, 1.3, PLATE),
        box(0.0, 0.32, 0.62, 1.6, 0.06, 0.06, METAL),
        box(-0.8, 0.17, 0.62, 0.06, 0.3, 0.06, METAL),
        box(0.8, 0.17, 0.62, 0.06, 0.3, 0.06, METAL),
        box(0.9, 0.65, -0.2, 0.06, 1.3, 0.06, METAL),
        box(0.9, 1.15, -0.2, 0.06, 0.32, 0.36, SHARED),
    )

    /** A round wheel in two crossed boxes, its middle [along] the vehicle at [height], [size] across. */
    private fun wheel(along: Double, height: Double, size: Double): List<Block> = listOf(
        box(0.0, height, along, 0.07, size, size * 0.6, TYRE),
        box(0.0, height, along, 0.07, size * 0.6, size, TYRE),
    )

    /** A box by its middle: [x] across, [y] up and [z] along, and its size. */
    private fun box(x: Double, y: Double, z: Double, width: Double, height: Double, depth: Double, colour: Int) =
        Block(x - width / 2, y - height / 2, z - depth / 2, width, height, depth, colour)

    /**
     * [model]'s boxes standing at [spot] turned by [turn], as large as the figures are drawn: each box turned about the
     * spot, so the whole stands together.
     */
    fun placed(model: List<Block>, spot: Spot, turn: Double): List<Block> {
        val k = Figures.SCALE
        val c = cos(turn)
        val s = sin(turn)
        return model.map { box ->
            val x = (box.x + box.width / 2) * k
            val z = (box.z + box.depth / 2) * k
            val width = box.width * k
            val depth = box.depth * k
            // As Three.js turns about the vertical: x' = x cos + z sin, z' = -x sin + z cos.
            Block(
                spot.x + x * c + z * s - width / 2,
                box.y * k,
                spot.z - x * s + z * c - depth / 2,
                width,
                box.height * k,
                depth,
                box.colour,
                turn,
            )
        }
    }

    private const val TYRE = 0x2B2B2B
    private const val METAL = 0x9AA3AA
    private const val SADDLE = 0x3A2A20
    private const val BASKET = 0xC9A56B
    private const val LAMP = 0xF8E7B0
    private const val PLATE = 0xB9B4A8
}
