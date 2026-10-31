package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import vancouvermonth.core.Item
import vancouvermonth.core.Spending

/** The bike shop in the town, and the bikes and e-scooters: where they stand, how fast they go, and how a tap finds them. */
class RidesTest {
    private val town = Neighbourhood.grid
    private val shop = Neighbourhood.shop
    private val home = Neighbourhood.venue(Place.HOME).door

    private fun Grid.walks(route: List<Spot>): Boolean = route.zipWithNext().all { (a, b) -> isClear(a, b) }

    private fun length(route: List<Spot>): Double = route.zipWithNext { a, b -> hypot(b.x - a.x, b.z - a.z) }.sum()

    /** Every door, talk spot and person, the shop's included. */
    private val spotsToKeepClear: List<Spot> = Neighbourhood.venues.flatMap { listOf(it.door, it.talkSpot, it.person) } +
        listOf(shop.door, shop.talkSpot, shop.keeper)

    @Test
    fun `the shop stands on a cleared lot of its own, its keeper's square blocked and its door and talk spot free`() {
        for (x in shop.x until shop.x + shop.width) {
            for (z in shop.z until shop.z + shop.depth) assertFalse(town.isFree(Cell(x, z)), "the shop at $x, $z")
        }
        assertFalse(town.isFree(town.cellAt(shop.keeper)), "the keeper")
        assertTrue(town.isFree(town.cellAt(shop.door)), "the door")
        assertTrue(town.isFree(town.cellAt(shop.talkSpot)), "the talk spot")
        assertEquals(1.0, distanceBetween(shop.keeper, shop.talkSpot), 1e-9)
        assertTrue(distanceBetween(shop.keeper, shop.door) <= 3.5)
        val lot = Lot(shop.x, shop.z, shop.width, shop.depth)
        for (venue in Neighbourhood.venues) {
            assertFalse(lot.overlaps(Lot(venue.x, venue.z, venue.width, venue.depth)), "${venue.place}")
        }
        for (city in Neighbourhood.cityLots) assertFalse(shop.lot.overlaps(city), "a city building on the shop's lot: $city")
        for (building in Neighbourhood.foldables) {
            val box = building.box
            val apart = box.x + box.width <= shop.x || shop.x + shop.width <= box.x ||
                box.z + box.depth <= shop.z || shop.z + shop.depth <= box.z
            assertTrue(apart, "${building.box} folds over the shop")
        }
    }

    @Test
    fun `the shop is easy to reach from 내 방 and from downtown, along the streets`() {
        for (from in listOf(home, Neighbourhood.venue(Place.OFFICE).door, Neighbourhood.venue(Place.LIBRARY).door)) {
            for (target in listOf(shop.door, shop.talkSpot)) {
                val route = town.route(from, target)
                assertEquals(target, route.lastOrNull(), "$from: $route")
                assertTrue(town.walks(route), "$from: $route")
                // On foot in under 20 seconds.
                assertTrue(length(route) < 20 * Trip.WALK_SPEED, "$from: ${length(route)}")
            }
        }
    }

    @Test
    fun `the shop matters as the five places do: it never folds, the camera keeps out of it, and a tap talks to its keeper`() {
        val walls = Neighbourhood.walls.filter { it.shop }
        assertEquals(listOf(shop.box), walls.map { it.box })
        assertFalse(walls.single().folds)
        assertEquals(null, walls.single().place)
        val keeper = Neighbourhood.people.single { it.shop }
        assertTrue(abs(keeper.box.x + keeper.box.width / 2 - shop.keeper.x) < 1e-9)
        assertTrue(Neighbourhood.isAtShop(shop.door) && Neighbourhood.isAtShop(shop.talkSpot))
        assertFalse(Neighbourhood.isAtShop(home))
        for (venue in Neighbourhood.venues) assertEquals(null, Neighbourhood.placeAt(shop.talkSpot), "${venue.place}")
    }

    /** A camera on a phone looking at [focus] from the turn [yaw], settled there. */
    private fun camera(focus: Point, yaw: Double): FollowCamera {
        val camera = FollowCamera(focus)
        camera.aspect = 375.0 / 812
        camera.orbit((camera.yaw - yaw) / FollowCamera.TURN_PER_PIXEL, 0.0)
        camera.jumpTo(focus)
        return camera
    }

    /** The city's buildings as the town folds them for [camera], looking at the character at [focus]. */
    private fun folded(camera: FollowCamera, focus: Point): Cutaway {
        val cutaway = Cutaway(Neighbourhood.foldables.map { it.box })
        cutaway.aim(camera.eye.x, camera.eye.y, camera.eye.z, focus.x, 1.6, focus.z)
        cutaway.step(Cutaway.FOLD_MILLIS)
        return cutaway
    }

    @Test
    fun `a tap on the shop or its keeper finds the shop, and one on a shared bike finds that bike`() {
        // From the street, where the player talks to the keeper, as far off as the camera starts.
        val talk = Point(shop.talkSpot.x, 1.0, shop.talkSpot.z)
        val camera = camera(talk, 0.0)
        val cutaway = folded(camera, talk)
        val head = assertNotNull(camera.screenPoint(Point(shop.keeper.x, 1.3, shop.keeper.z)))
        assertEquals(Picked.AtShop, camera.pick(head.left, head.top, cutaway = cutaway))
        val wall = assertNotNull(camera.screenPoint(Point(shop.x + 1.0, 2.0, shop.z + shop.depth.toDouble())))
        assertEquals(Picked.AtShop, camera.pick(wall.left, wall.top, cutaway = cutaway))

        for ((index, parked) in Rides.shared.withIndex()) {
            val near = Point(parked.spot.x, 1.0, parked.spot.z + 3.0)
            val look = camera(near, 0.0)
            val lookCutaway = folded(look, near)
            val seat = assertNotNull(look.screenPoint(Point(parked.spot.x, 0.6, parked.spot.z)))
            val picked = look.pick(seat.left, seat.top, cutaway = lookCutaway, vehicles = Rides.tapBoxes)
            assertEquals(Picked.AtVehicle(index), picked, "$index at ${parked.spot}")
            // Without them, as while riding, the same tap does not find it.
            assertFalse(look.pick(seat.left, seat.top, cutaway = lookCutaway) is Picked.AtVehicle)
        }
    }

    @Test
    fun `the shared bikes stand two to a dock and the e-scooters one to a corner, all on open ground to walk to`() {
        assertEquals(8, Rides.shared.count { it.vehicle == Vehicle.BIKE })
        assertEquals(4, Rides.shared.count { it.vehicle == Vehicle.E_SCOOTER })
        val loops = Strolls.all().flatMap { it.stroll.legs }
        for ((index, parked) in Rides.shared.withIndex()) {
            val cell = town.cellAt(parked.spot)
            assertTrue(town.isFree(cell), "$index at ${parked.spot} is not open ground")
            val route = town.route(home, parked.spot)
            assertEquals(parked.spot, route.lastOrNull(), "$index: $route")
            for (spot in spotsToKeepClear) {
                assertTrue(distanceBetween(spot, parked.spot) >= 1.5, "$index at ${parked.spot} is by $spot")
            }
            for ((a, b) in loops) {
                assertTrue(distanceToSegment(parked.spot, a, b) >= 1.5, "$index at ${parked.spot} is on a passerby's way")
            }
        }
        // A dock near 내 방, 도서관, 카페 and the station, and an e-scooter near 내 방, the shop, 회사 and 코워킹.
        val docks = Rides.shared.filter { it.vehicle == Vehicle.BIKE }.chunked(2).map { it.first().spot }
        val nearDocks = listOf(Place.HOME, Place.LIBRARY, Place.CAFE).map { Neighbourhood.venue(it).door }
        for ((dock, door) in docks.zip(nearDocks)) assertTrue(distanceBetween(dock, door) < 10.0, "$dock, $door")
        val station = Neighbourhood.guideway.points.first()
        assertTrue(distanceBetween(docks[3], station) < 16.0, "${docks[3]}")
        val scooters = Rides.shared.filter { it.vehicle == Vehicle.E_SCOOTER }.map { it.spot }
        val nearScooters = listOf(home, shop.door, Neighbourhood.venue(Place.OFFICE).door,
            Neighbourhood.venue(Place.COWORKING).door)
        for ((scooter, door) in scooters.zip(nearScooters)) {
            assertTrue(distanceBetween(scooter, door) < 10.0, "$scooter, $door")
        }
    }

    @Test
    fun `each shared one's boxes are where its index says, and a tap box sits round each`() {
        var first = Rides.scenery.size
        for (index in Rides.shared.indices) {
            assertEquals(first, Rides.firstBoxOf(index))
            val parked = Rides.shared[index]
            val count = Rides.boxCountOf(index)
            assertEquals(Rides.model(parked.vehicle, Rides.SHARED).size, count)
            for (box in Rides.boxes.subList(first, first + count)) {
                val middle = Spot(box.x + box.width / 2, box.z + box.depth / 2)
                assertTrue(distanceBetween(middle, parked.spot) < 1.0, "$index: $box")
            }
            val tap = Rides.tapBoxes[index]
            assertEquals(parked.spot.x, tap.x + tap.width / 2, 1e-9)
            assertEquals(parked.spot.z, tap.z + tap.depth / 2, 1e-9)
            first += count
        }
        assertEquals(Rides.boxes.size, first)
    }

    @Test
    fun `a vehicle placed facing a way has its front that way, and stands on the ground`() {
        val model = Rides.model(Vehicle.BIKE, Rides.OWN_BIKE)
        // The basket is the frontmost box of a bike.
        val basket = model.maxBy { it.z + it.depth / 2 }
        val at = Spot(10.0, 20.0)
        for ((turn, way) in listOf(0.0 to Spot(0.0, 1.0), PI / 2 to Spot(1.0, 0.0), PI to Spot(0.0, -1.0))) {
            val placed = Rides.placed(model, at, turn)
            val front = placed[model.indexOf(basket)]
            val middle = Spot(front.x + front.width / 2, front.z + front.depth / 2)
            val ahead = (middle.x - at.x) * way.x + (middle.z - at.z) * way.z
            assertTrue(ahead > 0.6, "turn $turn: $middle")
            assertEquals(turn, front.turn)
        }
        for (vehicle in Vehicle.entries) {
            val lowest = Rides.model(vehicle, 0).minOf { it.y }
            assertEquals(0.0, lowest, 1e-9, "$vehicle")
        }
    }

    @Test
    fun `a bike goes about 9 units a second and an e-scooter 11, both faster on a double tap and than a walk`() {
        assertEquals(9.0, Vehicle.BIKE.speed)
        assertEquals(11.0, Vehicle.E_SCOOTER.speed)
        for (vehicle in Vehicle.entries) {
            assertTrue(vehicle.speed > Trip.DASH_SPEED, "$vehicle")
            assertTrue(vehicle.dashSpeed > vehicle.speed, "$vehicle")
        }
        assertEquals(listOf(Item.BIKE, Item.E_SCOOTER), Vehicle.entries.map { it.item })
        assertEquals(Ride.BIKE, Ride.own(Item.BIKE))
        assertEquals(Ride.E_SCOOTER, Ride.own(Item.E_SCOOTER))
        assertEquals(Spending.SHARED_BIKE_RIDE, Ride.shared(Vehicle.BIKE).fare)
        assertEquals(Spending.SHARED_E_SCOOTER_RIDE, Ride.shared(Vehicle.E_SCOOTER).fare)
        assertFalse(Ride.BIKE.shared)
    }

    /** At a bike's best speed, in 16 ms frames, the camera keeps within a couple of units of the character. */
    @Test
    fun `the camera keeps up with a ride`() {
        val route = town.route(home, Neighbourhood.venue(Place.OFFICE).talkSpot)
        val trip = Trip(route, Vehicle.E_SCOOTER.dashSpeed)
        val start = route.first()
        val camera = FollowCamera(Point(start.x, 1.0, start.z))
        var elapsed = 0.0
        var farthest = 0.0
        while (!trip.isOverAfter(elapsed)) {
            elapsed += 16.0
            val spot = trip.spotAfter(elapsed)
            camera.follow(Point(spot.x, 1.0, spot.z), 16.0)
            farthest = maxOf(farthest, hypot(camera.focus.x - spot.x, camera.focus.z - spot.z))
        }
        assertTrue(farthest < 2.0, "the camera fell $farthest behind")
    }

    private fun distanceToSegment(spot: Spot, a: Spot, b: Spot): Double {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val length = dx * dx + dz * dz
        val share = if (length == 0.0) 0.0 else (((spot.x - a.x) * dx + (spot.z - a.z) * dz) / length).coerceIn(0.0, 1.0)
        return hypot(spot.x - (a.x + dx * share), spot.z - (a.z + dz * share))
    }
}
