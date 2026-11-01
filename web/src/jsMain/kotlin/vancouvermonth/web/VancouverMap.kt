package vancouvermonth.web

/** What one square of a [TerrainMap] is, and the character that stands for it in [VancouverMapData.rows]. */
enum class Terrain(val symbol: Char) {
    WATER('~'),
    LAND('.'),
    PARK('p'),

    /** Land beside the water, where the seawall path runs. */
    SHORE('s'),
}

/**
 * Ground cut into squares, one [Terrain] each, read from rows of characters with the north row first. Water blocks
 * the way and every other square is walkable, so [grid] is ground for the same A* the town walks on now.
 */
class TerrainMap(val columns: Int, val rows: Int, private val squares: List<Terrain>) {
    /** What [cell] is. Every square outside the map is water. */
    fun terrainAt(cell: Cell): Terrain = terrainAt(cell.x, cell.z)

    /** What the square in column [x] and row [z] is, with nothing new made. Every square outside the map is water. */
    fun terrainAt(x: Int, z: Int): Terrain =
        if (x in 0 until columns && z in 0 until rows) squares[z * columns + x] else Terrain.WATER

    /** Every square of [terrain], row by row from the north. */
    fun cellsOf(terrain: Terrain): List<Cell> = squares.indices
        .filter { squares[it] == terrain }
        .map { Cell(it % columns, it / columns) }

    /** The ground to walk on: every square but the water. */
    val grid: Grid by lazy { Grid(columns, rows, cellsOf(Terrain.WATER)) }

    companion object {
        /** The map [lines] draw, one line per row and one character per square, as in [VancouverMapData.rows]. */
        fun parse(lines: List<String>): TerrainMap {
            require(lines.isNotEmpty()) { "A map needs at least one row" }
            val columns = lines.first().length
            val squares = lines.flatMapIndexed { z, line ->
                require(line.length == columns) { "Row $z is ${line.length} squares long, not $columns" }
                line.map { symbol ->
                    Terrain.entries.firstOrNull { it.symbol == symbol }
                        ?: throw IllegalArgumentException("Row $z has an unknown square '$symbol'")
                }
            }
            return TerrainMap(columns, lines.size, squares)
        }
    }
}

/** The points of a line written as "x,z x,z ...", as [VancouverMapData] keeps its lines. */
fun pointsOf(text: String): List<Spot> = text.split(' ').filter { it.isNotEmpty() }.map { pair ->
    val (x, z) = pair.split(',')
    Spot(x.toDouble(), z.toDouble())
}

/** The 64 digits of a base-64 VLQ, in order of their value. */
private const val VLQ_DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/**
 * Whole numbers packed by `tools/vancouver_map`, [fields] to an item: each number written as a base-64 VLQ, as source
 * maps write them (five bits to a character, lowest first, the sixth set while more follow, the sign in the lowest bit),
 * and each the change from the same number of the item before (the first item's from 0).
 */
fun unpackNumbers(text: String, fields: Int): List<IntArray> {
    val numbers = mutableListOf<Int>()
    var value = 0
    var shift = 0
    for (char in text) {
        val digit = VLQ_DIGITS.indexOf(char)
        require(digit >= 0) { "Not a VLQ digit: '$char'" }
        value += (digit and 31) shl shift
        if (digit and 32 != 0) {
            shift += 5
        } else {
            numbers.add(if (value and 1 == 1) -(value shr 1) else value shr 1)
            value = 0
            shift = 0
        }
    }
    require(shift == 0 && numbers.size % fields == 0) { "${numbers.size} numbers do not make items of $fields" }
    val sums = IntArray(fields)
    return numbers.chunked(fields) { item -> IntArray(fields) { sums[it] += item[it]; sums[it] } }
}

/** The points of a line packed as [unpackNumbers] reads them, x and z in tenths of a square. */
fun unpackPoints(text: String): List<Spot> = unpackNumbers(text, 2).map { (x, z) -> Spot(x / 10.0, z / 10.0) }

/**
 * Real Vancouver, traced from the City of Vancouver's open data by `tools/vancouver_map`: the ground, the shoreline,
 * the big park, the rail on its guideway, the line the mountains stand behind, and a square for each of the five
 * places. [Neighbourhood] builds the town on it.
 */
object RealVancouver {
    val map: TerrainMap by lazy { TerrainMap.parse(VancouverMapData.rows) }

    /** How many metres of the real city one square stands for. */
    const val METRES_PER_UNIT = VancouverMapData.METRES_PER_UNIT

    val shoreLines: List<List<Spot>> by lazy { VancouverMapData.shoreLines.map(::pointsOf) }
    val park: List<Spot> by lazy { pointsOf(VancouverMapData.PARK) }
    val rail: List<Spot> by lazy { pointsOf(VancouverMapData.RAIL) }
    val mountains: List<Spot> by lazy { pointsOf(VancouverMapData.MOUNTAINS) }
    val spots: Map<Place, Cell> get() = VancouverMapData.spots
}
