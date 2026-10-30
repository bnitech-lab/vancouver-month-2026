package vancouvermonth.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomDealerTest {
    private val seeds = 1..1_000

    @Test
    fun `the same seed deals the same rooms`() {
        assertEquals(RoomDealer.deal(4821), RoomDealer.deal(4821))
    }

    @Test
    fun `every deal has one room in each zone`() {
        for (seed in seeds) {
            val zones = RoomDealer.deal(seed).map { it.zone }.toSet()
            assertEquals(Zone.entries.toSet(), zones, "seed $seed")
        }
    }

    @Test
    fun `rent stays inside its zone's range and ends in zero`() {
        for (seed in seeds) {
            for (room in RoomDealer.deal(seed)) {
                val middle = RoomDealer.BASE_RENT - room.zone.number * RoomDealer.RENT_DROP_PER_ZONE
                val allowed = (middle - RoomDealer.RENT_SPREAD - 5)..(middle + RoomDealer.RENT_SPREAD + 5)
                assertTrue(room.rent in allowed, "seed $seed: rent ${room.rent} for zone ${room.zone.number}")
                assertEquals(0, room.rent % 10, "seed $seed")
            }
        }
    }

    @Test
    fun `included utilities cost nothing and separate ones stay in range`() {
        for (seed in seeds) {
            for (room in RoomDealer.deal(seed)) {
                if (room.utilitiesIncluded) {
                    assertEquals(0, room.utilities, "seed $seed")
                } else {
                    assertTrue(room.utilities in 120..190, "seed $seed: utilities ${room.utilities}")
                }
            }
        }
    }

    @Test
    fun `the lowest rent is often but not always the lowest total`() {
        val surprises = seeds.count { seed ->
            val rooms = RoomDealer.deal(seed)
            rooms.minBy { it.rent } != rooms.minBy { it.totalMonthlyCost }
        }
        val share = surprises.toDouble() / seeds.count()
        assertTrue(share in 0.25..0.6, "share of deals where the cheapest rent is not the cheapest total: $share")
    }
}
