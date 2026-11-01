package vancouvermonth.web

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FollowCameraTest {
    /** In the middle of the big park, where nothing the camera keeps out of stands near. */
    private val start = Point(50.5, 1.0, 60.5)

    /** A camera on a tall phone, settled at [focus], turned to [yaw]. */
    private fun camera(focus: Point = start, yaw: Double = FollowCamera.START_YAW): FollowCamera {
        val camera = FollowCamera(focus)
        camera.aspect = 375.0 / 812
        camera.orbit((camera.yaw - yaw) / FollowCamera.TURN_PER_PIXEL, 0.0)
        camera.jumpTo(focus)
        return camera
    }

    /** The city's buildings as the town folds them for [camera], looking at the character at [focus], once settled. */
    private fun folded(camera: FollowCamera, focus: Point): Cutaway {
        val cutaway = Cutaway(Neighbourhood.foldables.map { it.box })
        cutaway.aim(camera.eye.x, camera.eye.y, camera.eye.z, focus.x, 1.6, focus.z)
        cutaway.step(Cutaway.FOLD_MILLIS)
        return cutaway
    }

    /** How far the eye is from the focus, and the turn it is really at. */
    private fun reachOf(camera: FollowCamera): Double {
        val eye = camera.eye
        val focus = camera.focus
        return sqrt((eye.x - focus.x).pow(2) + (eye.y - focus.y).pow(2) + (eye.z - focus.z).pow(2))
    }

    private fun turnOf(camera: FollowCamera): Double = atan2(camera.eye.x - camera.focus.x, camera.eye.z - camera.focus.z)

    @Test
    fun `dragging turns the camera, and the tilt stays between its limits`() {
        val camera = camera()
        val yaw = camera.yaw
        camera.orbit(100.0, 0.0)
        assertEquals(yaw - 100 * FollowCamera.TURN_PER_PIXEL, camera.yaw, 1e-9)
        camera.orbit(0.0, 10_000.0)
        assertEquals(FollowCamera.MAX_PITCH, camera.pitch)
        camera.orbit(0.0, -10_000.0)
        assertEquals(FollowCamera.MIN_PITCH, camera.pitch)
    }

    @Test
    fun `pinching brings the camera nearer or farther, within its limits`() {
        val camera = camera()
        camera.zoom(0.5)
        assertEquals(FollowCamera.START_DISTANCE * 0.5, camera.distance, 1e-9)
        camera.zoom(0.01)
        assertEquals(FollowCamera.NEAR, camera.distance)
        camera.zoom(100.0)
        assertEquals(FollowCamera.FAR, camera.distance)
    }

    @Test
    fun `the camera glides to where it was asked, then settles`() {
        val camera = camera()
        camera.orbit(50.0, 20.0)
        val subject = Point(start.x + 2.0, 1.0, start.z)
        assertTrue(camera.follow(subject, 16.0), "still moving after one frame")
        var frames = 0
        while (camera.follow(subject, 16.0)) frames++
        assertTrue(frames < 120, "settled in $frames frames")
        assertEquals(subject, camera.subject)
        assertFalse(camera.follow(subject, 16.0))
    }

    @Test
    fun `the focus is in the middle of the screen, and the eye is behind and above it`() {
        val camera = camera()
        val middle = assertNotNull(camera.screenPoint(camera.focus))
        assertEquals(0.5, middle.left, 1e-9)
        assertEquals(0.5, middle.top, 1e-9)
        assertTrue(camera.eye.y > camera.focus.y)
        // A point straight behind the eye is not on the screen at all.
        val behind = Point(2 * camera.eye.x - camera.focus.x, camera.eye.y, 2 * camera.eye.z - camera.focus.z)
        assertNull(camera.screenPoint(behind))
    }

    @Test
    fun `the ground is in view at the lowest and the highest tilt`() {
        for (tilt in listOf(-10_000.0, 10_000.0)) {
            val camera = camera()
            camera.orbit(0.0, tilt)
            camera.jumpTo(start)
            val bottom = camera.pick(0.5, 0.95)
            assertTrue(bottom is Picked.OnGround, "tilt $tilt: $bottom")
        }
    }

    @Test
    fun `a tap on the ground picks that spot`() {
        val camera = camera()
        val spot = Spot(start.x + 3.0, start.z + 0.5)
        val onScreen = assertNotNull(camera.screenPoint(Point(spot.x, 0.0, spot.z)))
        val picked = camera.pick(onScreen.left, onScreen.top) as Picked.OnGround
        assertEquals(spot.x, picked.spot.x, 1e-6)
        assertEquals(spot.z, picked.spot.z, 1e-6)
    }

    @Test
    fun `a tap on a person or a building opens its place`() {
        for (venue in Neighbourhood.venues) {
            // Stand where the player talks, and look at the person from the street, as far off as the camera starts.
            val yaw = if (venue.doorFacesSouth) 0.0 else PI
            val spot = Point(venue.talkSpot.x, 1.0, venue.talkSpot.z)
            val camera = camera(spot, yaw)
            // The city's buildings between the camera and the character fold down, as they do in the town.
            val cutaway = folded(camera, spot)
            val head = assertNotNull(camera.screenPoint(Point(venue.person.x, 1.2, venue.person.z)), "${venue.place}")
            assertEquals(Picked.AtPlace(venue.place), camera.pick(head.left, head.top, cutaway = cutaway), "${venue.place}")
            // Its wall, halfway up, above the person and in line with the camera.
            val front = if (venue.doorFacesSouth) venue.z + venue.depth else venue.z
            val x = venue.person.x.coerceIn(venue.x + 0.6, venue.x + venue.width - 0.6)
            val wall = assertNotNull(camera.screenPoint(Point(x, venue.height / 2, front.toDouble())), "${venue.place}")
            assertTrue(wall.left in 0.0..1.0 && wall.top in 0.0..1.0, "${venue.place}'s wall is at $wall")
            val picked = camera.pick(wall.left, wall.top, cutaway = cutaway)
            assertEquals(Picked.AtPlace(venue.place), picked, "${venue.place}'s wall")
            // Close in, the person is still picked.
            camera.zoom(0.35)
            camera.jumpTo(spot)
            val closer = folded(camera, spot)
            val near = assertNotNull(camera.screenPoint(Point(venue.person.x, 1.2, venue.person.z)), "${venue.place}")
            assertEquals(Picked.AtPlace(venue.place), camera.pick(near.left, near.top, cutaway = closer), "${venue.place}, near")
        }
    }

    @Test
    fun `the sky picks nothing`() {
        // Looking north over the park and the inlet past it, as low as the camera goes.
        val camera = camera(yaw = 0.0)
        camera.orbit(0.0, -10_000.0)
        camera.jumpTo(start)
        assertNull(camera.pick(0.5, 0.0))
    }

    @Test
    fun `nothing stands between the camera and the character, wherever it walks and however the camera turns`() {
        // Buildings, the SkyTrain deck, the station's roof, the awning and the canopy: the camera comes in front of
        // them or rises over them, and it is never inside one.
        val grid = Neighbourhood.grid
        // Every fifth square of the town's corner, as always, and every fiftieth out on the wide map, where only the
        // guideway, its stations and the stops' shelters are walls.
        val (corner, wide) = grid.freeCells().partition { it.x < Neighbourhood.TOWN_COLUMNS }
        val spots = (corner.filterIndexed { index, _ -> index % 5 == 0 } + wide.filterIndexed { index, _ -> index % 50 == 0 })
            .map { grid.centreOf(it) } + Neighbourhood.venues.flatMap { listOf(it.door, it.talkSpot) }
        for (spot in spots) {
            for (yaw in listOf(0.0, PI / 2, PI, -PI / 2)) {
                for (tilt in listOf(-10_000.0, 0.0, 10_000.0)) {
                    val camera = camera(Point(spot.x, 1.0, spot.z), yaw)
                    camera.orbit(0.0, tilt)
                    camera.jumpTo(Point(spot.x, 1.0, spot.z))
                    val from = camera.subject
                    val eye = camera.eye
                    val toEye = Point(eye.x - from.x, eye.y - from.y, eye.z - from.z)
                    val reach = sqrt(toEye.x * toEye.x + toEye.y * toEye.y + toEye.z * toEye.z)
                    if (reach == 0.0) continue
                    val way = Point(toEye.x / reach, toEye.y / reach, toEye.z / reach)
                    // Only a wall within the camera's reach can stand between it and the character, or hold the eye.
                    for (box in FollowCamera.wallsNear(from, reach + 1.0)) {
                        val hit = distanceToBox(from, way, box)
                        assertTrue(hit == null || hit >= reach, "$spot, turn $yaw: $box stands between")
                        val inside = eye.x > box.x && eye.x < box.x + box.width && eye.y > box.y &&
                            eye.y < box.y + box.height && eye.z > box.z && eye.z < box.z + box.depth
                        assertFalse(inside, "$spot, turn $yaw: the eye $eye is inside $box")
                    }
                }
            }
        }
    }

    /** Every free street square with one of the city's buildings within two squares, in the busiest districts. */
    private val downtown: List<Spot> by lazy {
        val grid = Neighbourhood.grid
        grid.freeCells().map { grid.centreOf(it) }.filter { spot ->
            (hypot(spot.x - 157.0, spot.z - 58.0) < 20.0 || hypot(spot.x - 172.0, spot.z - 76.0) < 24.0) &&
                Neighbourhood.cityLots.any { it.isNear(spot.x, spot.z, 2.0) }
        }
    }

    @Test
    fun `downtown the camera keeps its distance and the player's turn, as the city's buildings fold instead`() {
        assertTrue(downtown.size > 1_000, "only ${downtown.size} squares")
        var whole = 0
        for (spot in downtown) {
            val focus = Point(spot.x, 1.0, spot.z)
            val camera = camera(focus)
            val reach = reachOf(camera)
            val tilt = kotlin.math.asin((camera.eye.y - focus.y) / reach)
            // Never pulled in close, never looking down from overhead: the close views downtown are gone.
            assertTrue(reach >= FollowCamera.ALONG_A_STREET, "$spot: only $reach away")
            assertTrue(tilt < 1.2, "$spot: tilted $tilt")
            // Where nothing that matters stands on the way out to where the player put it, the camera is right there,
            // whatever of the city's stands in between.
            val yaw = FollowCamera.START_YAW
            val pitch = FollowCamera.START_PITCH
            val out = Point(cos(pitch) * sin(yaw), sin(pitch), cos(pitch) * cos(yaw))
            val blocked = FollowCamera.wallsNear(focus, FollowCamera.START_DISTANCE + 1.0).any { box ->
                val hit = distanceToBox(focus, out, box)
                hit != null && hit < FollowCamera.START_DISTANCE + 0.5
            }
            if (!blocked) {
                assertEquals(FollowCamera.START_DISTANCE, reach, 1e-9, "$spot")
                assertEquals(yaw, turnOf(camera), 1e-9, "$spot")
                whole++
            }
        }
        assertTrue(whole > downtown.size * 4 / 5, "only $whole of ${downtown.size} squares are clear of the places")
    }

    @Test
    fun `the places, the station and the deck still pull the camera in or lift it, the city's buildings never`() {
        // South of the station, looked at from the north, over it.
        val station = Neighbourhood.guideway.points.first()
        val south = Point(station.x + 4.0, 1.0, station.z + 2.5)
        assertTrue(Neighbourhood.grid.isFree(Neighbourhood.grid.cellAt(Spot(south.x, south.z))), "$south")
        val overStation = camera(south, yaw = PI)
        assertTrue(reachOf(overStation) < FollowCamera.START_DISTANCE - 1.0, "the station did not move the camera")
        // At 회사's door, looked at from the south, over the tower.
        val office = Neighbourhood.venue(Place.OFFICE)
        val door = Point(office.door.x, 1.0, office.door.z)
        val overOffice = camera(door, yaw = 0.0)
        val reach = reachOf(overOffice)
        val tilt = kotlin.math.asin((overOffice.eye.y - door.y) / reach)
        assertTrue(reach < FollowCamera.START_DISTANCE || tilt > FollowCamera.START_PITCH + 0.05, "회사 did not")
        // The walls the camera keeps out of are never the city's.
        val city = Neighbourhood.foldables.map { it.box }.toSet()
        for (spot in downtown.filterIndexed { index, _ -> index % 50 == 0 }) {
            val near = FollowCamera.wallsNear(Point(spot.x, 1.0, spot.z), FollowCamera.FAR)
            assertTrue(near.none { it in city }, "$spot")
        }
    }

    @Test
    fun `a folded building no longer catches a tap meant for the ground behind it, and a standing one still does`() {
        var checked = 0
        var reached = 0
        for (spot in downtown) {
            val focus = Point(spot.x, 1.0, spot.z)
            val camera = camera(focus)
            // A tap on the character's feet, on the ground.
            val feet = Point(spot.x, 0.0, spot.z)
            val onScreen = camera.screenPoint(feet) ?: continue
            val standing = camera.pick(onScreen.left, onScreen.top) as? Picked.OnGround ?: continue
            // Only where one of the city's buildings stands in front of the feet, and the way to them passes over
            // where its plinth will be.
            if (distanceBetween(standing.spot, spot) < 1.0) continue
            val inTheWay = Neighbourhood.foldables.indexOfFirst { building ->
                val box = building.box
                standing.spot.x in box.x - 1e-6..box.x + box.width + 1e-6 &&
                    standing.spot.z in box.z - 1e-6..box.z + box.depth + 1e-6
            }
            if (inTheWay < 0) continue
            val box = Neighbourhood.foldables[inTheWay].box
            val eye = camera.eye
            val length = sqrt((feet.x - eye.x).pow(2) + (feet.y - eye.y).pow(2) + (feet.z - eye.z).pow(2))
            val way = Point((feet.x - eye.x) / length, (feet.y - eye.y) / length, (feet.z - eye.z) / length)
            val plinth = Block(box.x, 0.0, box.z, box.width, Cutaway.PLINTH, box.depth, 0)
            if (distanceToBox(eye, way, plinth) != null) continue
            // Standing, as the town has it before it folds anything, the building takes the tap, as it always did.
            val unfolded = Cutaway(Neighbourhood.foldables.map { it.box })
            assertEquals(standing, camera.pick(onScreen.left, onScreen.top, cutaway = unfolded), "$spot")
            // Folded, the tap goes past it.
            val cutaway = folded(camera, focus)
            assertTrue(cutaway.isFolding(inTheWay), "$spot: $box stands")
            val picked = camera.pick(onScreen.left, onScreen.top, cutaway = cutaway)
            val landed = assertNotNull(picked as? Picked.OnGround, "$spot: $picked")
            val onIt = landed.spot.x in box.x - 1e-6..box.x + box.width + 1e-6 &&
                landed.spot.z in box.z - 1e-6..box.z + box.depth + 1e-6
            assertFalse(onIt, "$spot: the folded $box still took the tap, at ${landed.spot}")
            if (distanceBetween(landed.spot, spot) < 1e-6) reached++
            checked++
            if (checked == 20) break
        }
        assertEquals(20, checked, "too few places downtown where a building stood in front of the character")
        assertTrue(reached >= 10, "only $reached of $checked taps reached the character's feet")
    }

    @Test
    fun `the camera stays under the SkyTrain deck, the awning and the canopy rather than look through them`() {
        // Standing under each, with the camera turned to look along it and tilted as high as it goes: without them
        // among the camera's walls, the camera would rise high above and look down through them.
        val deck = Neighbourhood.guideway.spotAt(22.0)
        val cafe = Neighbourhood.venue(Place.CAFE)
        val office = Neighbourhood.venue(Place.OFFICE)
        val under = listOf(
            Point(deck.x, 1.0, deck.z) to Neighbourhood.DECK_HEIGHT,
            Point(cafe.x + 2.0, 1.0, cafe.z - 0.2) to 2.0,
            Point(office.door.x, 1.0, office.z - 0.2) to 2.2,
        )
        for ((spot, roof) in under) {
            val camera = camera(spot, -PI / 2)
            camera.orbit(0.0, 10_000.0)
            camera.jumpTo(spot)
            assertTrue(camera.eye.y < roof, "under a roof at $roof over $spot, the eye is at ${camera.eye}")
        }
    }

    @Test
    fun `however near the camera comes, the character stays in the picture`() {
        // The camera looks at the character, so however near it comes the character stays.
        val door = Neighbourhood.venue(Place.HOME).door
        for (yaw in listOf(-PI / 2, -PI / 4, 0.0, PI / 2, PI)) {
            for (step in 0..12) {
                val camera = camera(Point(door.x, 1.0, door.z), yaw)
                camera.zoom(0.85.pow(step))
                camera.jumpTo(Point(door.x, 1.0, door.z))
                for (height in listOf(0.0, 1.6)) {
                    val point = camera.screenPoint(Point(door.x, height, door.z))
                    val inside = point != null && point.left in 0.0..1.0 && point.top in 0.0..1.0
                    assertTrue(inside, "turn $yaw, distance ${camera.distance}: the character at $height is at $point")
                }
            }
        }
    }

    @Test
    fun `backed against a building, the camera keeps its distance by rising over it`() {
        for (venue in Neighbourhood.venues) {
            val yaw = if (venue.doorFacesSouth) PI else 0.0
            val camera = camera(Point(venue.door.x, 1.0, venue.door.z), yaw)
            val eye = camera.eye
            val from = camera.subject
            val reach = sqrt((eye.x - from.x).pow(2) + (eye.y - from.y).pow(2) + (eye.z - from.z).pow(2))
            assertTrue(reach > 3.0, "${venue.place}: the camera is only $reach away")
        }
    }

    @Test
    fun `the first view shows at least four buildings' names below the top card on a 375 by 812 phone`() {
        val home = Neighbourhood.venue(Place.HOME).door
        val camera = FollowCamera(Point(home.x, 1.0, home.z))
        camera.aspect = 375.0 / 812
        // The top card is about 95 points tall without the first day's hint, and a name about 32 points tall
        // sits just above its anchor.
        val below = (95.0 + 32.0) / 812
        val shown = Neighbourhood.venues.filter { venue ->
            val point = camera.screenPoint(venue.labelAnchor)
            point != null && point.left in 0.06..0.94 && point.top in below..0.95
        }
        assertTrue(shown.size >= 4, "only ${shown.map { it.place }} show")
        // The character is in the picture too.
        val character = assertNotNull(camera.screenPoint(Point(home.x, 0.8, home.z)))
        assertTrue(character.left in 0.1..0.9 && character.top in below..0.9, "$character")
    }

    @Test
    fun `the camera turns on its own the short way round, within its limits`() {
        val camera = camera()
        camera.orbit(-2 * PI / FollowCamera.TURN_PER_PIXEL, 0.0)
        val turned = camera.yaw
        camera.aim(0.15, 0.5, 9.0)
        assertTrue(abs(camera.yaw - turned) <= PI, "turned from $turned to ${camera.yaw}")
        assertEquals(0.15, (camera.yaw % (2 * PI) + 2 * PI) % (2 * PI), 1e-9)
        assertEquals(0.5, camera.pitch)
        assertEquals(9.0, camera.distance)
        camera.aim(0.0, 9.0, 999.0)
        assertEquals(FollowCamera.MAX_PITCH, camera.pitch)
        assertEquals(FollowCamera.FAR, camera.distance)
    }

    @Test
    fun `a tall phone sees at least 44 degrees across`() {
        for (aspect in listOf(375.0 / 812, 360.0 / 800, 1.0, 16.0 / 9)) {
            val half = FollowCamera.fovFor(aspect) * PI / 360
            val across = 2 * atan(tan(half) * aspect) * 180 / PI
            assertTrue(across >= FollowCamera.MIN_ACROSS - 1e-9, "$aspect sees $across degrees across")
        }
    }

    @Test
    fun `a tall phone sees more from top to bottom, and never too little across`() {
        assertTrue(FollowCamera.fovFor(375.0 / 812) > FollowCamera.fovFor(1.0))
        assertEquals(FollowCamera.MIN_FOV, FollowCamera.fovFor(16.0 / 9))
        val square = FollowCamera.fovFor(1.0)
        assertTrue(square in FollowCamera.MIN_FOV..FollowCamera.MAX_FOV)
    }

    @Test
    fun `a ray finds where it enters a box`() {
        val box = Block(2.0, 0.0, -1.0, 2.0, 2.0, 2.0, 0)
        assertEquals(2.0, distanceToBox(Point(0.0, 1.0, 0.0), Point(1.0, 0.0, 0.0), box))
        assertNull(distanceToBox(Point(0.0, 1.0, 0.0), Point(-1.0, 0.0, 0.0), box))
        assertNull(distanceToBox(Point(0.0, 5.0, 0.0), Point(1.0, 0.0, 0.0), box))
        assertEquals(0.0, distanceToBox(Point(3.0, 1.0, 0.0), Point(1.0, 0.0, 0.0), box))
    }

    /** A camera north of the office, over the inlet, looking south at its front. */
    private fun atTheOffice(): FollowCamera = camera(Point(153.5, 1.0, 50.5), yaw = PI)

    @Test
    fun `a passerby in front of a building is picked`() {
        val camera = atTheOffice()
        val passerby = Spot(153.5, 51.6)
        val head = assertNotNull(camera.screenPoint(Point(passerby.x, 1.3, passerby.z)))
        assertEquals(Picked.AtPlace(Place.OFFICE), camera.pick(head.left, head.top))
        val boxes = listOf(Passerby.boxAround(Spot(-100.0, -100.0)), Passerby.boxAround(passerby))
        assertEquals(Picked.AtPasserby(1), camera.pick(head.left, head.top, boxes))
    }

    @Test
    fun `a building in front of a passerby wins`() {
        val camera = atTheOffice()
        val behind = Spot(153.5, 59.5)
        val head = assertNotNull(camera.screenPoint(Point(behind.x, 1.3, behind.z)))
        val picked = camera.pick(head.left, head.top, listOf(Passerby.boxAround(behind)))
        assertEquals(Picked.AtPlace(Place.OFFICE), picked)
    }

    @Test
    fun `with no passersby, or none in the way, a tap lands where it always did`() {
        val camera = camera()
        val far = listOf(Passerby.boxAround(Spot(-100.0, -100.0)))
        for (left in listOf(0.1, 0.3, 0.5, 0.7, 0.9)) {
            for (top in listOf(0.2, 0.4, 0.6, 0.8)) {
                val before = camera.pick(left, top)
                assertEquals(before, camera.pick(left, top, emptyList()), "$left, $top")
                assertEquals(before, camera.pick(left, top, far), "$left, $top")
            }
        }
    }
}
