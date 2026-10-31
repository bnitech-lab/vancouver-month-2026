package vancouvermonth.web

import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import vancouvermonth.core.Spending
import vancouvermonth.core.TaxiFare
import vancouvermonth.text.Shop
import vancouvermonth.text.Transit
import vancouvermonth.text.Transport

/** The SkyTrain's stations, the bus's loop, the water bus's docks and the taxi's ways, on the real town's map. */
class TransitTest {
    private val town = Neighbourhood.grid
    private val map = RealVancouver.map
    private val home = Neighbourhood.venue(Place.HOME).door
    private val guideway = Neighbourhood.guideway

    private fun Grid.walks(route: List<Spot>): Boolean = route.zipWithNext().all { (a, b) -> isClear(a, b) }

    private fun length(route: List<Spot>): Double = route.zipWithNext { a, b -> hypot(b.x - a.x, b.z - a.z) }.sum()

    /** Every door, talk spot and person, the shop's included, and each shared bike and e-scooter. */
    private val spotsToKeepClear: List<Spot> = Neighbourhood.venues.flatMap { listOf(it.door, it.talkSpot, it.person) } +
        listOf(Neighbourhood.shop.door, Neighbourhood.shop.talkSpot, Neighbourhood.shop.keeper) + Rides.shared.map { it.spot }

    /** How far [spot] is from the line between [a] and [b]. */
    private fun distanceToLine(spot: Spot, a: Spot, b: Spot): Double {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val share = (((spot.x - a.x) * dx + (spot.z - a.z) * dz) / (dx * dx + dz * dz)).coerceIn(0.0, 1.0)
        return hypot(spot.x - (a.x + dx * share), spot.z - (a.z + dz * share))
    }

    /** Points every quarter unit along [line] from its start to its end. */
    private fun samples(line: RailLine): List<Spot> =
        (0..(line.length * 4).toInt()).map { line.spotAt(it / 4.0) } + line.points.last()

    /** A place one can walk to from 내 방's door, and walks along. */
    private fun assertWalkable(to: Spot, what: String) {
        assertTrue(town.isFree(town.cellAt(to)), "$what at $to is not open ground")
        val route = town.route(home, to)
        assertEquals(to, route.lastOrNull(), "$what cannot be reached: $route")
        assertTrue(town.walks(route), "$what: $route")
    }

    @Test
    fun `four stations along the line: one under downtown, the old one by the tunnel's mouth, and two up on it`() {
        val stations = TransitLines.stations
        assertEquals(Transit.STATIONS.size, stations.size)
        assertEquals(stations.sortedBy { it.along }, stations)
        assertEquals(1, stations.count { it.underground })
        // The whole train is in the tunnel at the station under downtown, and stands at the old station as it did.
        assertTrue(stations[0].along + Neighbourhood.TRAIN_LENGTH / 2 < 0)
        assertEquals(Neighbourhood.TRAIN_STOP + Neighbourhood.TRAIN_LENGTH / 2, stations[1].along)
        for ((a, b) in stations.zipWithNext()) assertTrue(b.along - a.along > Neighbourhood.TRAIN_LENGTH + 4, "$a, $b")
        for ((index, station) in stations.withIndex()) {
            val name = Transit.STATIONS[index]
            assertWalkable(station.door, name)
            for (clear in spotsToKeepClear) assertTrue(distanceBetween(station.door, clear) >= 1.5, "$name by $clear")
            val lot = station.entrance ?: continue
            for (x in lot.x until lot.x + lot.width) {
                for (z in lot.z until lot.z + lot.depth) assertFalse(town.isFree(Cell(x, z)), "$name's entrance at $x, $z")
            }
            for (city in Neighbourhood.cityLots) assertFalse(lot.overlaps(city), "a city building on $name: $city")
            // Its door is on the street to the south, a step out of its entrance.
            assertEquals(lot.z + lot.depth + 0.5, station.door.z, name)
            assertTrue(station.door.x > lot.x && station.door.x < lot.x + lot.width, name)
            if (station.underground) continue
            // Up on the line: the line runs over the entrance, and straight along the whole platform.
            val middle = guideway.spotAt(station.along)
            assertTrue(lot.isNear(middle.x, middle.z, 0.0), "$name: the line at $middle misses its entrance $lot")
            val half = Neighbourhood.TRAIN_LENGTH / 2
            assertEquals(guideway.turnAt(station.along - half), guideway.turnAt(station.along + half), 1e-9, name)
            assertTrue(middle.x < Neighbourhood.COLUMNS, name)
        }
    }

    @Test
    fun `the bus loops along the streets, and its five stops stand by it, off every door and talk spot`() {
        val loop = TransitLines.busLoop
        assertEquals(loop.points.first(), loop.points.last())
        for (spot in samples(loop)) {
            val cell = town.cellAt(spot)
            assertTrue(town.isFree(cell), "the bus runs over $cell at $spot")
            assertEquals(Terrain.LAND, map.terrainAt(cell), "the bus leaves the streets at $spot")
            for (city in Neighbourhood.cityLots) assertFalse(city.isNear(spot.x, spot.z, 0.0), "into a building at $spot")
        }
        val stops = TransitLines.busStops
        assertEquals(Transit.BUS_STOPS.size, stops.size)
        assertEquals(stops.sortedBy { it.along }, stops)
        val ways = Strolls.all().flatMap { it.stroll.legs }
        for ((index, stop) in stops.withIndex()) {
            val name = Transit.BUS_STOPS[index]
            assertWalkable(stop.wait, name)
            val lane = loop.spotAt(stop.along)
            assertTrue(distanceBetween(lane, stop.wait) in 0.8..1.3, "$name waits ${distanceBetween(lane, stop.wait)} off")
            for (clear in spotsToKeepClear) assertTrue(distanceBetween(stop.wait, clear) >= 1.5, "$name by $clear")
            for ((a, b) in ways) assertTrue(distanceToLine(stop.wait, a, b) >= 1.5, "$name on a passerby's way")
        }
        // 내 방's stop is a short walk from its door.
        assertTrue(length(town.route(home, stops.last().wait)) < 12.0)
    }

    @Test
    fun `three docks on False Creek, one by the café, and the water bus's way is on the water all along`() {
        val loop = TransitLines.boatLoop
        assertEquals(loop.points.first(), loop.points.last())
        for (spot in samples(loop)) assertEquals(Terrain.WATER, map.terrainAt(town.cellAt(spot)), "the boat on land at $spot")
        val docks = TransitLines.docks
        assertEquals(Transit.DOCKS.size, docks.size)
        for ((index, dock) in docks.withIndex()) {
            val name = Transit.DOCKS[index]
            assertWalkable(dock.wait, name)
            assertTrue(map.terrainAt(town.cellAt(dock.wait)) == Terrain.SHORE, "$name is not on the shore")
            val berth = loop.spotAt(dock.along)
            assertEquals(Terrain.WATER, map.terrainAt(town.cellAt(berth)), name)
            assertTrue(distanceBetween(berth, dock.wait) in 3.0..5.0, "$name's berth is ${distanceBetween(berth, dock.wait)} out")
            for (clear in spotsToKeepClear) assertTrue(distanceBetween(dock.wait, clear) >= 1.5, "$name by $clear")
        }
        // By the café, and across the creek from it.
        val cafe = Neighbourhood.venue(Place.CAFE).door
        assertTrue(length(town.route(cafe, docks[0].wait)) < 25.0)
        assertTrue(docks[1].wait.x > loop.spotAt(docks[0].along).x + 6, "the far shore")
    }

    @Test
    fun `a taxi drives along the streets to every place it goes, for $4 and $1·50 for every ten squares`() {
        val stops = TransitLines.taxiStops
        assertEquals(Neighbourhood.venues.size + 1 + Transit.STATIONS.size, stops.size)
        assertEquals(Place.HOME.label to home, stops[0])
        assertEquals(Shop.NAME, stops[Neighbourhood.venues.size].first)
        var dearest = 0
        for (from in stops.map { it.second }) {
            for ((name, to) in stops) {
                if (distanceBetween(from, to) < 2.0) continue
                val route = town.route(from, to)
                assertEquals(to, route.lastOrNull(), "to $name from $from")
                assertTrue(town.walks(route), "to $name from $from: $route")
                val fare = TaxiFare.forDistance(length(route))
                assertTrue(Spending.TAXI_RIDE.costs(fare))
                dearest = maxOf(dearest, fare)
            }
        }
        // The meter's top is never reached in town.
        assertTrue(dearest < TaxiFare.MOST, "$dearest")
        val office = Neighbourhood.venue(Place.OFFICE).door
        val squares = length(town.route(home, office))
        assertEquals((TaxiFare.BASE + squares * 0.15).roundToInt(), TaxiFare.forDistance(squares))
    }

    @Test
    fun `on the water bus the character stands on the open deck, clear of the cabin, its roof and its windows`() {
        // The character's footprint, a little wider than it is drawn, ahead of the boat's middle.
        val half = 0.3
        val stand = TransitLines.BOAT_STAND
        val above = TransitLines.boat.filter { it.y + it.height > TransitLines.DECK + 0.1 }
        assertTrue(above.size >= 3, "the cabin, its windows and its roof")
        for (box in above) {
            val clear = stand - half >= box.x + box.width || stand + half <= box.x ||
                half <= box.z || -half >= box.z + box.depth
            assertTrue(clear, "the character at $stand stands in $box")
        }
        // On the boat, ahead of its middle and short of the bow.
        val hull = TransitLines.boat.first()
        assertTrue(stand > 0 && stand + half < hull.x + hull.width, "$stand")
    }

    @Test
    fun `a line placed in place is where its spot and turn say, before it, along it and past it`() {
        val out = DoubleArray(3)
        for (line in listOf(guideway, TransitLines.busLoop, TransitLines.boatLoop)) {
            var along = -40.0
            while (along < line.length + 40) {
                line.placeAt(along, out)
                val spot = line.spotAt(along)
                assertEquals(spot.x, out[0], 1e-9, "$along")
                assertEquals(spot.z, out[1], 1e-9, "$along")
                assertEquals(line.turnAt(along), out[2], 1e-9, "$along")
                along += 0.37
            }
        }
    }

    @Test
    fun `a glide speeds up, goes on at its speed and slows to a stop, and a short one never reaches it`() {
        val long = Glide(100.0, 10.0, 1_000.0)
        assertEquals(11_000.0, long.millis, 1e-6)
        assertEquals(0.0, long.at(0.0))
        assertEquals(5.0, long.at(1_000.0), 1e-9)
        assertEquals(100.0, long.at(11_000.0))
        assertEquals(10.0, long.speedAt(5_000.0), 1e-9)
        var last = 0.0
        for (step in 0..1_100) {
            val at = long.at(step * 10.0)
            assertTrue(at >= last - 1e-9 && long.speedAt(step * 10.0) <= 10.0 + 1e-9)
            last = at
        }
        val short = Glide(4.0, 10.0, 1_000.0)
        assertEquals(2.0, short.at(short.millis / 2), 1e-9)
        assertTrue(short.speedAt(short.millis / 2) < 10.0)
        assertEquals(4.0, short.at(short.millis + 1))
    }

    @Test
    fun `a ride goes the shorter way round its loop, and stops where the character gets out`() {
        val loop = TransitLines.busLoop
        val stops = TransitLines.busStops
        // From 내 방's stop to the park's, round the corner, not the long way.
        val (way, distance) = Journey.roundLoop(loop, stops[4].along, stops[0].along, Journey.GOING_ON)
        assertEquals(loop.length - stops[4].along + stops[0].along, distance, 1e-6)
        assertEquals(loop.spotAt(stops[4].along), way.points.first())
        assertTrue(distanceBetween(way.spotAt(distance), loop.spotAt(stops[0].along)) < 1e-6)
        assertEquals(distance + Journey.GOING_ON, way.length, 1e-6)
        // Back the other way.
        val (back, backDistance) = Journey.roundLoop(loop, stops[0].along, stops[4].along, 0.0)
        assertEquals(distance, backDistance, 1e-6)
        assertTrue(distanceBetween(back.points.last(), loop.spotAt(stops[4].along)) < 1e-6)
        for (spot in samples(way)) assertTrue(town.isFree(town.cellAt(spot)), "$spot")

        val ride = Journey(Transport.BUS, way, distance, stops[0].wait, Journey.BUS_SPEED, Journey.BUS_RAMP)
        assertEquals(0.0, ride.travelled(Journey.BOARD_MILLIS))
        assertEquals(distance, ride.travelled(ride.millis - 1), 1e-6)
        assertTrue(ride.isOverAfter(ride.millis))
        assertTrue(ride.goneMillis > ride.millis)
        assertEquals(way.length, ride.travelled(ride.goneMillis), 1e-6)
    }

    @Test
    fun `the train of a ride runs along the guideway from one station to the next, and on out of the way`() {
        val stations = TransitLines.stations
        val from = stations[1].along
        val to = stations[2].along
        val way = Journey.wayOn(guideway, from, to, 20.0)
        assertEquals(to - from + 20.0, way.length, 1e-6)
        for (step in 0..(way.length * 2).toInt()) {
            val along = from + step / 2.0
            assertTrue(distanceBetween(way.spotAt(step / 2.0), guideway.spotAt(along)) < 1e-6, "$along")
        }
        val ride = Journey(Transport.SKYTRAIN, way, to - from, stations[2].door, Journey.TRAIN_SPEED, Journey.TRAIN_RAMP, from)
        assertEquals(from, ride.alongAt(0.0))
        assertEquals(to, ride.alongAt(ride.millis - 1), 1e-6)
        // Westward into the tunnel, under downtown.
        val west = Journey.wayOn(guideway, from, stations[0].along, 0.0)
        assertEquals(from - stations[0].along, west.length, 1e-6)
        assertTrue(west.points.last().x < guideway.points.first().x)
    }

    @Test
    fun `the bus and the water bus run their loops on their own, waiting at each stop`() {
        val bus = LoopRun(
            TransitLines.busLoop,
            DoubleArray(5) { TransitLines.busStops[it].along },
            Journey.BUS_SPEED,
            Journey.BUS_RAMP,
            2_500.0,
        )
        assertEquals(TransitLines.busStops[0].along, bus.alongAt(0.0))
        assertEquals(TransitLines.busStops[0].along, bus.alongAt(2_000.0))
        assertTrue(bus.alongAt(3_000.0) > TransitLines.busStops[0].along)
        for (step in 0..400) {
            val along = bus.alongAt(step * bus.period / 400)
            assertTrue(along >= 0 && along < TransitLines.busLoop.length, "$along")
        }
        assertEquals(bus.alongAt(1_234.0), bus.alongAt(1_234.0 + bus.period), 1e-6)
    }

    @Test
    fun `the small map marks every station, bus stop and dock, and the bike shop`() {
        assertEquals(TransitLines.stations.size, Minimap.marksOf(0))
        assertEquals(TransitLines.busStops.size, Minimap.marksOf(1))
        assertEquals(TransitLines.docks.size, Minimap.marksOf(2))
        assertEquals(1, Minimap.marksOf(3))
    }

    @Test
    fun `a tap on each station, bus stop and dock finds it`() {
        assertEquals(TransitLines.tapStops.size, TransitLines.tapBoxes.size)
        assertEquals(TransitLines.stations.size + TransitLines.busStops.size + TransitLines.docks.size, TransitLines.tapStops.size)
        for ((index, stop) in TransitLines.tapStops.withIndex()) {
            val box = TransitLines.tapBoxes[index]
            // The character waits at the stop, the camera behind it on the open side: the street, or the water.
            val there = TransitLines.waitAt(stop)
            val open = when (stop.transport) {
                Transport.BUS -> TransitLines.busLoop.spotAt(TransitLines.busStops[stop.index].along)
                Transport.WATER_BUS -> TransitLines.boatLoop.spotAt(TransitLines.docks[stop.index].along)
                else -> Spot(there.x, there.z + 1.0)
            }
            val focus = Point(there.x, 1.0, there.z)
            val camera = FollowCamera(focus)
            camera.aspect = 375.0 / 812
            val yaw = kotlin.math.atan2(open.x - there.x, open.z - there.z)
            camera.orbit((camera.yaw - yaw) / FollowCamera.TURN_PER_PIXEL, 0.0)
            camera.jumpTo(focus)
            val cutaway = Cutaway(Neighbourhood.foldables.map { it.box })
            cutaway.aim(camera.eye.x, camera.eye.y, camera.eye.z, focus.x, 1.6, focus.z)
            cutaway.step(Cutaway.FOLD_MILLIS)
            val middle = Point(box.x + box.width / 2, box.y + minOf(box.height / 2, 1.2), box.z + box.depth / 2)
            val seen = assertNotNull(camera.screenPoint(middle), "$stop")
            val picked = camera.pick(seen.left, seen.top, cutaway = cutaway, stops = TransitLines.tapBoxes)
            assertEquals(Picked.AtStop(index), picked, "$stop at $middle")
            assertFalse(camera.pick(seen.left, seen.top, cutaway = cutaway) is Picked.AtStop)
        }
    }
}
