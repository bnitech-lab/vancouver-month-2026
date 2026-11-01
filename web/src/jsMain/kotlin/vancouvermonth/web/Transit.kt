package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.floor
import kotlin.math.sqrt
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import vancouvermonth.text.Shop
import vancouvermonth.text.Transit
import vancouvermonth.text.Transport

/**
 * A SkyTrain station: how far along the guideway the train's middle stands at it ([along]; below 0 under downtown,
 * past the tunnel's mouth), the lot its entrance takes from the city (null for the station already under the deck by
 * the tunnel's mouth), and where the character stands at its door.
 */
data class Station(val along: Double, val entrance: Lot?, val door: Spot) {
    /** True for the station under downtown, reached by the tunnel: the train is out of sight there. */
    val underground: Boolean
        get() = along < 0
}

/** A bus stop or a water bus dock: where the vehicle stops on its way ([along] its loop), and where the character waits. */
data class Halt(val along: Double, val wait: Spot)

/** A bus line: the [loop] its bus runs round, and its [stops] in the order the bus comes to them. */
class BusLine(val loop: RailLine, val stops: List<Halt>)

/** A station, stop or dock a tap can land on: the [transport] and the stop's place in its list. */
data class Stop(val transport: Transport, val index: Int)

/**
 * The SkyTrain's stations, the bus's loop and stops, the water bus's docks and its way over False Creek, the places a
 * taxi goes, and the vehicles, all made of boxes. None of the stops blocks the way, and all the colours are invented,
 * none a real transit or taxi company's.
 */
object TransitLines {
    /**
     * The stations, west to east: under downtown, by the tunnel's mouth (the station that was always there), and the
     * Expo Line's real stations from the wide map ([WideVancouver.stations]) out to the Burnaby town centre, each with
     * its entrance under the line and its door to the south. The line goes on past the town centre, where the game
     * stops nowhere and places nothing.
     */
    val stations: List<Station> = listOf(
        Station(-29.0, Lot(166, 60, 3, 3), Spot(167.5, 63.5)),
        Station(Neighbourhood.TRAIN_STOP + Neighbourhood.TRAIN_LENGTH / 2, null, Spot(200.8, 64.4)),
    ) + (1..TOWN_CENTRE).map { stationAt(WideVancouver.stations[it].spot) }

    /**
     * The station the wide map has at [spot]: its entrance a lot under the line there, its door just south of it. Where
     * the line bends at the station, it is moved along to the nearest stretch that runs straight for a whole train, as
     * its platform does.
     */
    private fun stationAt(spot: Spot): Station {
        // The guideway's own line, not the town's, so the stations can be laid out before the town is.
        val line = expoLine
        val found = alongOf(line, spot)
        val half = Neighbourhood.TRAIN_LENGTH / 2 + 0.5
        val along = (0..STRAIGHTEN_STEPS).flatMap { listOf(found + it * 0.5, found - it * 0.5) }
            .firstOrNull { line.turnAt(it - half) == line.turnAt(it + half) } ?: found
        val middle = line.spotAt(along)
        val lot = Lot(floor(middle.x).toInt() - 1, floor(middle.z).toInt() - 2, 3, 3)
        return Station(along, lot, Spot(lot.x + 1.5, lot.z + lot.depth + 0.5))
    }

    /** How far along [line] its nearest point to [spot] is. */
    fun alongOf(line: RailLine, spot: Spot): Double {
        var best = Double.MAX_VALUE
        var bestAlong = 0.0
        var gone = 0.0
        for ((a, b) in line.points.zipWithNext()) {
            val dx = b.x - a.x
            val dz = b.z - a.z
            val length = sqrt(dx * dx + dz * dz)
            val share = (((spot.x - a.x) * dx + (spot.z - a.z) * dz) / (length * length)).coerceIn(0.0, 1.0)
            val off = distanceBetween(spot, Spot(a.x + dx * share, a.z + dz * share))
            if (off < best) {
                best = off
                bestAlong = gone + share * length
            }
            gone += length
        }
        return bestAlong
    }

    /** The last of [WideVancouver.stations] the train stops at: the Burnaby town centre's. */
    const val TOWN_CENTRE = 7

    /** How far a station may be moved along the line, in half squares, to stand on a straight stretch. */
    private const val STRAIGHTEN_STEPS = 24

    /** How tall a station's entrance on the ground is. */
    const val ENTRANCE_HEIGHT = 3.0

    /**
     * The bus's loop: up the West End's street by the park, along downtown's wide street, down a street on its east
     * side and back along the street south of 내 방, the first point again at the end. Every point is on the street.
     */
    val busLoop = RailLine(
        listOf(Spot(105.5, 103.5), Spot(105.5, 70.5), Spot(169.5, 70.5), Spot(169.5, 103.5), Spot(105.5, 103.5)),
    )

    /**
     * The second line's way, out and back along the main streets: from East Vancouver by the station at the city line,
     * past 내 방's corner for a room in zone 2 and the big Burnaby park, down the slope to 내 방's corner for zone 3.
     * Every point is on a main street of the wide map.
     */
    private val burnabyWay = listOf(
        Spot(486.4, 40.0), Spot(489.6, 54.7), Spot(501.9, 53.3), Spot(505.0, 52.3), Spot(507.9, 50.1),
        Spot(520.5, 35.6), Spot(552.9, 68.0), Spot(588.1, 103.4),
    )
    val burnabyLoop = RailLine(burnabyWay + burnabyWay.reversed().drop(1))

    /**
     * The bus lines: downtown's loop, with its stops by the park, two downtown, south of downtown and by the town's
     * 내 방; and the line out to Burnaby, with its stops by the station at the city line, by 내 방 in zone 2, by the big
     * park and by 내 방 in zone 3.
     */
    val busLines: List<BusLine> = listOf(
        BusLine(
            busLoop,
            listOf(
                Halt(18.5, Spot(106.4, 85.5)),
                Halt(57.5, Spot(130.5, 71.6)),
                Halt(87.5, Spot(160.5, 71.6)),
                Halt(149.5, Spot(150.5, 104.4)),
                Halt(184.5, Spot(115.5, 104.4)),
            ),
        ),
        BusLine(
            burnabyLoop,
            listOf(
                Halt(3.0, Spot(488.1, 42.7)),
                Halt(50.5, Spot(517.7, 37.2)),
                Halt(76.4, Spot(537.4, 51.0)),
                Halt(146.3, Spot(586.8, 100.5)),
            ),
        ),
    )

    /** Every bus stop, line by line, each in the order its bus comes to them: a stop's number is its place here. */
    val busStops: List<Halt> = busLines.flatMap { it.stops }

    /** The line bus stop [index] is on. */
    fun lineOf(index: Int): BusLine {
        var left = index
        for (line in busLines) {
            if (left < line.stops.size) return line
            left -= line.stops.size
        }
        error("No bus stop $index")
    }

    /** True when a ride on [transport] can go from stop [from] to stop [to]: a bus only along its own line. */
    fun joins(transport: Transport, from: Int, to: Int): Boolean =
        transport != Transport.BUS || from !in busStops.indices || to !in busStops.indices || lineOf(from) === lineOf(to)

    /**
     * The ground the wide map's buildings and trees keep clear round each bus stop, and round each station's door: room to
     * wait, and the shelter's.
     */
    val keptForStops: List<Lot> by lazy {
        busStops.map { Lot(floor(it.wait.x).toInt() - 2, floor(it.wait.z).toInt() - 2, 5, 5) } +
            stations.map { Lot(floor(it.door.x).toInt() - 1, floor(it.door.z).toInt() - 1, 3, 3) }
    }

    /**
     * The water bus's way over False Creek: from the dock by the café across to the far shore, up the creek to the
     * dock near its head, and back down to the café's, every point on the water.
     */
    val boatLoop = RailLine(
        listOf(
            Spot(200.6, 113.5), Spot(207.8, 112.5), Spot(205.5, 104.0), Spot(205.5, 99.0), Spot(210.5, 93.5),
            Spot(213.0, 86.5), Spot(215.5, 80.0), Spot(214.4, 72.5), Spot(215.5, 80.0), Spot(213.0, 86.5),
            Spot(210.5, 93.5), Spot(204.5, 99.0), Spot(202.5, 105.0), Spot(200.6, 113.5),
        ),
    )

    /** The docks: by the café, on the far shore across from it, and near the creek's head. */
    val docks: List<Halt> by lazy {
        listOf(
            Halt(0.0, Spot(196.5, 113.5)),
            Halt(distanceBetween(boatLoop.points[0], boatLoop.points[1]), Spot(211.5, 112.5)),
            Halt(boatLoop.points.take(8).zipWithNext { a, b -> distanceBetween(a, b) }.sum(), Spot(210.5, 72.5)),
        )
    }

    /** Where the character stands at [stop], to get on there and to step out there; a taxi's places are zone 1's. */
    fun waitAt(stop: Stop): Spot = when (stop.transport) {
        Transport.SKYTRAIN -> stations[stop.index].door
        Transport.BUS -> busStops[stop.index].wait
        Transport.WATER_BUS -> docks[stop.index].wait
        Transport.TAXI -> taxiStops[stop.index].second
    }

    /** How many stops [transport] has; for a taxi, the places it goes. */
    fun count(transport: Transport): Int = when (transport) {
        Transport.SKYTRAIN -> stations.size
        Transport.BUS -> busStops.size
        Transport.WATER_BUS -> docks.size
        Transport.TAXI -> taxiStops.size
    }

    /** The name of [stop], or of the place a taxi goes. */
    fun nameOf(stop: Stop): String =
        if (stop.transport == Transport.TAXI) taxiStops[stop.index].first else Transit.stopName(stop.transport, stop.index)

    /** Where a taxi goes in zone 1's town ([taxiStopsFor]). */
    val taxiStops: List<Pair<String, Spot>>
        get() = taxiStopsFor(1)

    /**
     * Where a taxi goes for a room in [zone], by name: the five places (내 방 where that room is), the bike shop and the
     * stations, each at its door. The same names in the same order in every zone.
     */
    fun taxiStopsFor(zone: Int): List<Pair<String, Spot>> = taxiStopsByZone.getOrPut(zone) {
        HomeSite.of(zone).venues.map { it.place.label to it.door } + (Shop.NAME to Neighbourhood.shop.door) +
            stations.mapIndexed { index, station -> Transit.STATIONS[index] to station.door }
    }

    private val taxiStopsByZone = mutableMapOf<Int, List<Pair<String, Spot>>>()

    /** How near its stop the character must stand to get on there. */
    const val STOP_REACH = 1.5

    /** Every stop a tap can land on, in the order [tapBoxes] has them. */
    val tapStops: List<Stop> by lazy {
        stations.indices.map { Stop(Transport.SKYTRAIN, it) } + busStops.indices.map { Stop(Transport.BUS, it) } +
            docks.indices.map { Stop(Transport.WATER_BUS, it) }
    }

    /**
     * A box round each stop, as a tap finds it, a little larger than what is drawn so it is hit before the building it
     * stands in: a station's entrance (the first station's whole building), a bus stop's shelter and sign, a dock's
     * sign and the near end of its pier.
     */
    val tapBoxes: List<Block> by lazy {
        stations.map { station ->
            val lot = station.entrance
            if (lot == null) {
                val start = Neighbourhood.guideway.points.first()
                Block(start.x + 0.22, 0.0, start.z - 1.58, 8.16, Neighbourhood.DECK_HEIGHT + 3.08, 3.16, 0)
            } else {
                Block(lot.x + 0.12, 0.0, lot.z + 0.12, lot.width - 0.24, ENTRANCE_HEIGHT + 0.6, lot.depth - 0.24, 0)
            }
        } + busStops.map { Block(it.wait.x - 0.9, 0.0, it.wait.z - 0.9, 1.8, 2.7, 1.8, 0) } +
            docks.map { Block(it.wait.x - 0.9, -0.4, it.wait.z - 0.9, 1.8, 2.5, 1.8, 0) }
    }

    /** The stops' own boxes that stand still: each bus stop's shelter and sign, and each dock's pier and sign. */
    val scenery: List<Block> by lazy {
        busLines.flatMap { line -> line.stops.flatMap { shelter(line.loop, it) } } + docks.flatMap { pier(it) }
    }

    /**
     * A bus stop's shelter on [loop]: a roof on two posts and a back wall, a bench, and a sign on a post. The street is
     * on the side [Halt.wait] is from the shelter's back. On a street that runs along x or z it stands straight on the
     * grid; on the wide map's slanting streets it is turned along the street.
     */
    private fun shelter(loop: RailLine, halt: Halt): List<Block> {
        val on = loop.spotAt(halt.along)
        val (x, z) = halt.wait.x to halt.wait.z
        // Which way the street lies from the waiting spot, along x or z.
        val dx = on.x - x
        val dz = on.z - z
        val turn = loop.turnAt(halt.along)
        val slanting = abs(sin(2 * turn)) > 0.1
        val alongX = abs(dz) > abs(dx)
        val back = if (slanting) {
            // Away from the street, on the side the waiting spot is from it.
            val side = (x - on.x) * sin(turn) + (z - on.z) * cos(turn)
            0.45 * (if (side > 0) 1 else -1)
        } else if (alongX) {
            -0.45 * (if (dz > 0) 1 else -1)
        } else {
            -0.45 * (if (dx > 0) 1 else -1)
        }
        fun at(along: Double, out: Double, y: Double, length: Double, height: Double, deep: Double, colour: Int): Block =
            if (slanting) {
                // Along the street is the box's own x, out from it its own z, turned as the street runs.
                val middleX = x + along * cos(turn) + out * sin(turn)
                val middleZ = z - along * sin(turn) + out * cos(turn)
                Block(middleX - length / 2, y, middleZ - deep / 2, length, height, deep, colour, turn)
            } else if (alongX) {
                Block(x + along - length / 2, y, z + out - deep / 2, length, height, deep, colour)
            } else {
                Block(x + out - deep / 2, y, z + along - length / 2, deep, height, length, colour)
            }
        return listOf(
            at(0.0, back, 0.0, 1.5, 1.9, 0.06, GLASS),
            at(0.0, back * 0.5, 1.9, 1.7, 0.08, 0.95, SHELTER),
            at(-0.72, back * 0.5, 0.0, 0.07, 1.9, 0.07, POST),
            at(0.72, back * 0.5, 0.0, 0.07, 1.9, 0.07, POST),
            at(0.0, back * 0.8, 0.0, 1.1, 0.42, 0.28, BENCH),
            at(1.1, back * 0.2, 0.0, 0.07, 2.2, 0.07, POST),
            at(1.1, back * 0.2, 1.75, 0.36, 0.42, 0.08, BUS_SIGN),
        )
    }

    /**
     * A dock: a plank pier from the shore out over the water, stopping short of where the water bus draws up, posts
     * under it, and a sign on a post ashore.
     */
    private fun pier(halt: Halt): List<Block> {
        val berth = boatLoop.spotAt(halt.along)
        val (x, z) = halt.wait.x to halt.wait.z
        val out = if (berth.x > x) 1.0 else -1.0
        val near = x + out * 0.2
        val far = berth.x - out * 1.7
        val west = minOf(near, far)
        val east = maxOf(near, far)
        return listOf(
            Block(west, -0.12, z - 0.4, east - west, 0.14, 0.8, PLANK),
            Block(far - 0.06, -0.6, z - 0.42, 0.12, 0.5, 0.12, POST),
            Block(far - 0.06, -0.6, z + 0.3, 0.12, 0.5, 0.12, POST),
            Block(x - 0.04, 0.0, z + 0.55, 0.08, 2.0, 0.08, POST),
            Block(x - 0.25, 1.55, z + 0.52, 0.5, 0.4, 0.08, DOCK_SIGN),
        )
    }

    /** A bus: a long body, a pale band, dark windows down both sides and the front, a roof and four wheels. */
    val bus: List<Block> = listOf(
        Block(-1.8, 0.28, -0.56, 3.6, 1.1, 1.12, BUS),
        Block(-1.8, 0.28, -0.58, 3.6, 0.22, 1.16, BUS_BAND),
        Block(-1.6, 0.95, -0.59, 3.0, 0.42, 1.18, WINDOW),
        Block(1.72, 0.7, -0.5, 0.1, 0.7, 1.0, WINDOW),
        Block(-1.75, 1.38, -0.52, 3.5, 0.12, 1.04, ROOF),
        Block(-1.3, 0.0, -0.6, 0.5, 0.32, 1.2, TYRE),
        Block(0.85, 0.0, -0.6, 0.5, 0.32, 1.2, TYRE),
    )

    /**
     * A small water bus, its bow ahead (+x): a hull, a deck, a cabin with windows and a roof at the stern, and the open
     * deck ahead of it, where the character stands ([BOAT_STAND]).
     */
    val boat: List<Block> = listOf(
        Block(-1.5, -0.62, -0.58, 3.0, 0.5, 1.16, HULL),
        Block(-1.45, -0.14, -0.54, 2.9, 0.06, 1.08, PLANK),
        Block(-1.4, -0.08, -0.48, 1.1, 0.62, 0.96, CABIN),
        Block(-1.36, 0.2, -0.5, 1.02, 0.24, 1.0, WINDOW),
        Block(-1.5, 0.54, -0.55, 1.3, 0.08, 1.1, ROOF),
        Block(1.2, -0.14, -0.08, 0.38, 0.12, 0.16, HULL),
    )

    /** How far ahead of the water bus's middle the character stands, on the open deck clear of the cabin. */
    const val BOAT_STAND = 0.5

    /** A taxi: a pale body, a cabin with dark windows, a light on its roof, and four wheels. */
    val taxi: List<Block> = listOf(
        Block(-1.0, 0.16, -0.46, 2.0, 0.46, 0.92, TAXI),
        Block(-0.5, 0.62, -0.42, 1.0, 0.36, 0.84, WINDOW),
        Block(-0.45, 0.98, -0.38, 0.9, 0.06, 0.76, TAXI),
        Block(-0.15, 1.04, -0.12, 0.3, 0.14, 0.24, TAXI_LIGHT),
        Block(-0.75, 0.0, -0.48, 0.36, 0.3, 0.96, TYRE),
        Block(0.4, 0.0, -0.48, 0.36, 0.3, 0.96, TYRE),
    )

    /** How high the character stands on the water bus's deck. */
    const val DECK = -0.08

    private const val GLASS = 0xA9C6D3
    private const val SHELTER = 0x3F6E5A
    private const val POST = 0x6E6A64
    private const val BENCH = 0x9B6B45
    private const val BUS_SIGN = 0x3F8E6A
    private const val PLANK = 0xA9825A
    private const val DOCK_SIGN = 0xD9673F
    private const val BUS = 0x4E9A78
    private const val BUS_BAND = 0xF1E9D2
    private const val WINDOW = 0x34495A
    private const val ROOF = 0xE8E4D8
    private const val TYRE = 0x2B2B2B
    private const val HULL = 0xC4553F
    private const val CABIN = 0xF3ECDC
    private const val TAXI = 0xF0E4C4
    private const val TAXI_LIGHT = 0xF29B38
}

/**
 * Moving [length] units at up to [cruise] units a second: speeding up over [ramp] milliseconds, going on at that speed,
 * and slowing down over as long to a stop, or, when the way is too short, speeding up halfway and slowing down again.
 * Asked by the time since it set off.
 */
class Glide(val length: Double, cruise: Double, ramp: Double) {
    /** How fast it speeds up, in units a millisecond, each millisecond. */
    private val rate = cruise / 1_000 / ramp
    private val top: Double
    private val speedUp: Double

    /** How long the whole way takes, in milliseconds. */
    val millis: Double

    init {
        require(length >= 0 && cruise > 0 && ramp > 0) { "A glide needs a way, a speed and a ramp" }
        if (length <= cruise / 1_000 * ramp) {
            speedUp = sqrt(length / rate)
            top = rate * speedUp
            millis = 2 * speedUp
        } else {
            speedUp = ramp
            top = cruise / 1_000
            millis = ramp + length / top
        }
    }

    /** How far along it is [elapsed] milliseconds after setting off: 0 before, [length] once there. */
    fun at(elapsed: Double): Double = when {
        elapsed <= 0 -> 0.0
        elapsed >= millis -> length
        elapsed <= speedUp -> rate * elapsed * elapsed / 2
        elapsed <= millis - speedUp -> rate * speedUp * speedUp / 2 + top * (elapsed - speedUp)
        else -> length - rate * (millis - elapsed) * (millis - elapsed) / 2
    }

    /** How fast it goes [elapsed] milliseconds after setting off, in units a second. */
    fun speedAt(elapsed: Double): Double = when {
        elapsed <= 0 || elapsed >= millis -> 0.0
        elapsed <= speedUp -> rate * elapsed * 1_000
        elapsed <= millis - speedUp -> top * 1_000
        else -> rate * (millis - elapsed) * 1_000
    }
}

/**
 * One ride on [transport]: the vehicle goes along [way] from its start, stops at [stopAt] along it, where the character
 * steps out at [out], and then goes on to the way's end out of the stop and away. The character gets on in the first
 * [boardMillis] and steps out in the last [alightMillis]. For the SkyTrain, the way is the guideway from [railFrom], so
 * how far along the line the train is follows from how far it has gone.
 */
class Journey(
    val transport: Transport,
    val way: RailLine,
    val stopAt: Double,
    val out: Spot,
    cruise: Double,
    ramp: Double,
    val railFrom: Double = 0.0,
    private val railWay: Double = 1.0,
    /** Where the character gets on from, for a ride it is seen on: the dock it waits at for the water bus. */
    val from: Spot? = null,
    /** How long getting on takes, and stepping out: longer on the platform under downtown, which is shown. */
    val boardMillis: Double = BOARD_MILLIS,
    val alightMillis: Double = ALIGHT_MILLIS,
) {
    private val there = Glide(stopAt, cruise, ramp)
    private val away = Glide(way.length - stopAt, cruise, ramp)

    /** When the character steps out, from setting off, in milliseconds. */
    val millis: Double = boardMillis + there.millis + alightMillis

    /** When the vehicle has gone on out of sight, in milliseconds from setting off. */
    val goneMillis: Double = millis + away.millis

    /** How far along [way] the vehicle is [elapsed] milliseconds after the ride began. */
    fun travelled(elapsed: Double): Double =
        if (elapsed < millis) there.at(elapsed - boardMillis) else stopAt + away.at(elapsed - millis)

    /** How fast the vehicle goes then, in units a second. */
    fun speedAt(elapsed: Double): Double =
        if (elapsed < millis) there.speedAt(elapsed - boardMillis) else away.speedAt(elapsed - millis)

    /** How far along the guideway the train's middle is then: for the SkyTrain only. */
    fun alongAt(elapsed: Double): Double = railFrom + railWay * travelled(elapsed)

    /** How far along the guideway the train stops for the character: for the SkyTrain only. */
    val railTo: Double
        get() = railFrom + railWay * stopAt

    fun isOverAfter(elapsed: Double): Boolean = elapsed >= millis

    fun isGoneAfter(elapsed: Double): Boolean = elapsed >= goneMillis

    companion object {
        /** How long getting on takes, and stepping out. */
        const val BOARD_MILLIS = 600.0
        const val ALIGHT_MILLIS = 300.0

        /**
         * A ride on the SkyTrain from [from] to [to], carried on [goingOn] past [to] the same way, quicker on a long ride;
         * getting on or stepping out at the station under downtown takes as long as its platform is shown
         * ([UndergroundRide]).
         */
        fun train(from: Station, to: Station, goingOn: Double = TRAIN_GOING_ON): Journey {
            val start = from.along
            val end = to.along
            val way = wayOn(Neighbourhood.guideway, start, end, goingOn)
            // A long ride goes faster at its quickest, as a train between far stations does.
            val cruise = trainCruise(abs(end - start))
            return Journey(
                Transport.SKYTRAIN, way, abs(end - start), to.door, cruise, trainRamp(cruise), start,
                if (end >= start) 1.0 else -1.0,
                boardMillis = if (from.underground) UndergroundRide.BOARD_MILLIS else BOARD_MILLIS,
                alightMillis = if (to.underground) UndergroundRide.ALIGHT_MILLIS else ALIGHT_MILLIS,
            )
        }

        /** How far the train goes on along the line after a ride, out of the station, before it fades away. */
        const val TRAIN_GOING_ON = 24.0

        /** How fast each goes at its quickest, in units a second (walking is 3.6), and how long it takes to get there. */
        const val TRAIN_SPEED = 12.0
        const val TRAIN_RAMP = 1_500.0
        const val BUS_SPEED = 7.0
        const val BUS_RAMP = 1_000.0
        const val BOAT_SPEED = 5.0
        const val BOAT_RAMP = 1_200.0
        const val TAXI_SPEED = 12.0
        const val TAXI_RAMP = 800.0

        /** How far a bus or a water bus goes on along its loop after the stop, and a taxi back down the street. */
        const val GOING_ON = 14.0

        /**
         * How fast the train goes at its quickest on a ride [length] units long, and how long it takes to get there: as
         * on a short hop between neighbouring stations, or quicker on a long one, up to [TRAIN_TOP], so a ride out to
         * Burnaby takes about [TRAIN_RIDE_SECONDS] at full speed rather than most of a minute.
         */
        fun trainCruise(length: Double): Double = (length / TRAIN_RIDE_SECONDS).coerceIn(TRAIN_SPEED, TRAIN_TOP)

        fun trainRamp(cruise: Double): Double = TRAIN_RAMP * cruise / TRAIN_SPEED

        const val TRAIN_TOP = 30.0
        const val TRAIN_RIDE_SECONDS = 12.0

        /**
         * How fast a taxi goes at its quickest on a ride [length] units long, and how long it takes to get there: as
         * always on a ride downtown, or quicker on a long one, up to [TAXI_TOP], so a ride from Burnaby to downtown takes
         * about [TAXI_RIDE_SECONDS] at full speed rather than most of a minute.
         */
        fun taxiCruise(length: Double): Double = (length / TAXI_RIDE_SECONDS).coerceIn(TAXI_SPEED, TAXI_TOP)

        fun taxiRamp(cruise: Double): Double = TAXI_RAMP * cruise / TAXI_SPEED

        const val TAXI_TOP = 24.0
        const val TAXI_RIDE_SECONDS = 20.0

        /**
         * The way along [line] from along [from] to along [to], carried on [beyond] past [to] the same way: the line's
         * own corners on the way, so it follows the line exactly. Along may be below 0 or past the line's end, where
         * the line carries on straight.
         */
        fun wayOn(line: RailLine, from: Double, to: Double, beyond: Double): RailLine {
            val sign = if (to >= from) 1.0 else -1.0
            return RailLine(alongWay(line, from, sign, abs(to - from) + beyond, cornersOf(line), null))
        }

        /**
         * The way round a closed [loop] from along [from] to along [to], the shorter way round, carried on [beyond]
         * past [to] the same way; and how far along that way [to] is.
         */
        fun roundLoop(loop: RailLine, from: Double, to: Double, beyond: Double): Pair<RailLine, Double> {
            val length = loop.length
            val ahead = wrap(to - from, length)
            val forward = ahead <= length - ahead
            val distance = if (forward) ahead else length - ahead
            val sign = if (forward) 1.0 else -1.0
            val corners = cornersOf(loop).dropLast(1)
            return RailLine(alongWay(loop, from, sign, distance + beyond, corners, length)) to distance
        }

        /** How far along [line] each of its points is. */
        private fun cornersOf(line: RailLine): List<Double> {
            var along = 0.0
            val corners = mutableListOf(0.0)
            for ((a, b) in line.points.zipWithNext()) {
                along += distanceBetween(a, b)
                corners.add(along)
            }
            return corners
        }

        /**
         * The points of a way along [line] from along [from], [sign] up or down it, for [total] units: where it starts,
         * each of [corners] it passes, and where it ends; round and round when the line is a loop [loop] long.
         */
        private fun alongWay(
            line: RailLine,
            from: Double,
            sign: Double,
            total: Double,
            corners: List<Double>,
            loop: Double?,
        ): List<Spot> {
            fun at(along: Double) = line.spotAt(if (loop == null) along else wrap(along, loop))
            val passed = mutableListOf<Double>()
            val laps = if (loop == null) listOf(0.0) else (-2..2).map { it * loop }
            for (lap in laps) {
                for (corner in corners) {
                    val gone = (corner + lap - from) * sign
                    if (gone > 1e-6 && gone < total - 1e-6) passed.add(gone)
                }
            }
            passed.sort()
            val points = listOf(at(from)) + passed.map { at(from + sign * it) } + at(from + sign * total)
            return dedupe(points)
        }

        private fun wrap(value: Double, length: Double): Double = ((value % length) + length) % length

        private fun dedupe(points: List<Spot>): List<Spot> =
            points.filterIndexed { index, spot -> index == 0 || distanceBetween(points[index - 1], spot) > 1e-6 }
    }
}

/**
 * A vehicle that runs round a closed [loop] on its own, as the SkyTrain runs its line: from stop to stop along [stops]
 * (how far along the loop each is, in order), gliding up to [cruise] and down again, waiting [dwell] milliseconds at
 * each. Asked by the town's own clock.
 */
class LoopRun(private val loop: RailLine, private val stops: DoubleArray, cruise: Double, ramp: Double, dwell: Double) {
    private val legs = Array(stops.size) { index ->
        val from = stops[index]
        val to = if (index + 1 < stops.size) stops[index + 1] else stops[0] + loop.length
        Glide(to - from, cruise, ramp)
    }
    private val dwell = dwell

    /** How long a whole round takes, in milliseconds. */
    val period: Double = legs.sumOf { it.millis + dwell }

    /** How far along the loop it is at [millis] of the town's clock. */
    fun alongAt(millis: Double): Double {
        var left = ((millis % period) + period) % period
        for (index in legs.indices) {
            if (left < dwell) return stops[index]
            left -= dwell
            val leg = legs[index]
            if (left < leg.millis) return (stops[index] + leg.at(left)) % loop.length
            left -= leg.millis
        }
        return stops[0]
    }
}

/** Writes a vehicle's boxes into a draw's matrices. */
object VehicleBoxes {
    /**
     * Writes [boxes] (long along x, standing on the ground at 0, 0) turned [turn] about the vertical (as [RailLine.turnAt]
     * counts it: 0 runs east) and set down at [x], [y], [z], into [into] from box number [first] on. [hidden] shrinks them
     * to nothing. It makes nothing new.
     */
    fun write(boxes: List<Block>, first: Int, x: Double, y: Double, z: Double, turn: Double, hidden: Boolean, into: Float32Array) {
        val across = cos(turn)
        val toward = sin(turn)
        for (index in boxes.indices) {
            val box = boxes[index]
            val at = (first + index) * 16
            for (offset in 0 until 15) into[at + offset] = 0f
            into[at + 15] = 1f
            if (hidden) continue
            val middleX = box.x + box.width / 2
            val middleZ = box.z + box.depth / 2
            into[at] = (across * box.width).toFloat()
            into[at + 2] = (-toward * box.width).toFloat()
            into[at + 5] = box.height.toFloat()
            into[at + 8] = (toward * box.depth).toFloat()
            into[at + 10] = (across * box.depth).toFloat()
            into[at + 12] = (x + middleX * across + middleZ * toward).toFloat()
            into[at + 13] = (y + box.y + box.height / 2).toFloat()
            into[at + 14] = (z - middleX * toward + middleZ * across).toFloat()
        }
    }

    /** The turn [RailLine.turnAt] counts (0 runs east) for a figure's heading as [Trip] counts it (0 faces south). */
    fun turnOf(heading: Double): Double = heading - PI / 2
}
