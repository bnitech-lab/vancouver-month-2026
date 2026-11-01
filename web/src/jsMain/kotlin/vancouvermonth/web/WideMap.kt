package vancouvermonth.web

import kotlin.math.PI

/** A row written as runs, as [WideMapData.rows] keeps them ("~3.2s"), spelt out square by square ("~~~..s"). */
fun unpackRuns(text: String): String = buildString {
    var index = 0
    while (index < text.length) {
        val symbol = text[index++]
        var count = 0
        var counted = false
        while (index < text.length && text[index].isDigit()) {
            count = count * 10 + (text[index++] - '0')
            counted = true
        }
        repeat(if (counted) count else 1) { append(symbol) }
    }
}

/** A station on the wide map's rail: where it stands on the line, and how far along the line that is, in squares. */
data class RailStop(val spot: Spot, val along: Double)

/**
 * A real city block of the wide map: its [middle], its size along ([width]) and across ([depth]) its [turn], and the
 * turn in radians as [Block.turn] counts it.
 */
data class TurnedLot(val middle: Spot, val width: Double, val depth: Double, val turn: Double)

/** True when [spot] lies inside the ring of points [edge] (the even-odd rule, the ring closing on its own). */
fun isInside(spot: Spot, edge: List<Spot>): Boolean {
    var inside = false
    var j = edge.lastIndex
    for (i in edge.indices) {
        val a = edge[i]
        val b = edge[j]
        if ((a.z > spot.z) != (b.z > spot.z) && spot.x < (b.x - a.x) * (spot.z - a.z) / (b.z - a.z) + a.x) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * The wide map, traced by `tools/vancouver_map` from City of Vancouver and City of Burnaby open data: the town's map
 * carried on through East Vancouver into south-west Burnaby, with the Expo Line and its stations, the main streets,
 * the city blocks, the three zones a room can be in and a room in each. The town's map ([RealVancouver]) is its
 * top-left corner, square for square, and the town ([Neighbourhood]) stands on it.
 */
object WideVancouver {
    val map: TerrainMap by lazy { TerrainMap.parse(WideMapData.rows.map(::unpackRuns)) }

    /** How many metres of the real city one square stands for, as in the town's map. */
    const val METRES_PER_UNIT = WideMapData.METRES_PER_UNIT

    /** The town's map is columns 0 until this of every row. */
    const val TOWN_COLUMNS = WideMapData.TOWN_COLUMNS

    val rail: List<Spot> by lazy { pointsOf(WideMapData.RAIL) }

    val stations: List<RailStop> by lazy {
        WideMapData.STATIONS.split(' ').filter { it.isNotEmpty() }.map {
            val (x, z, along) = it.split(',').map(String::toDouble)
            RailStop(Spot(x, z), along)
        }
    }

    val streets: List<List<Spot>> by lazy { WideMapData.streets.map(::pointsOf) }

    val blocks: List<TurnedLot> by lazy {
        WideMapData.blocks.flatMap { it.split(' ') }.filter { it.isNotEmpty() }.map {
            val (x, z, width, depth, degrees) = it.split(',').map(String::toDouble)
            TurnedLot(Spot(x, z), width, depth, degrees * PI / 180)
        }
    }

    /** Each zone (1 to 3) as one or more areas, each a ring of points round its edge. */
    val zones: Map<Int, List<List<Spot>>> by lazy {
        WideMapData.zones.mapValues { (_, areas) -> areas.map(::pointsOf) }
    }

    /** The proposed square of 내 방 in each zone. */
    val homes: Map<Int, Cell> get() = WideMapData.homes

    /** The zone [spot] lies in, or null when it lies in none. */
    fun zoneAt(spot: Spot): Int? = zones.entries.firstOrNull { (_, areas) -> areas.any { isInside(spot, it) } }?.key
}
