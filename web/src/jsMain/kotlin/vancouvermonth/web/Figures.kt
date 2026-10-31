package vancouvermonth.web

import vancouvermonth.text.Townsperson

/** The part of a person a box belongs to. A leg swings from the hip and an arm from the shoulder. */
enum class Limb(val pivot: Point) {
    BODY(Point(0.0, 0.0, 0.0)),
    LEFT_LEG(Point(-Figures.LEG_X, Figures.HIP, 0.0)),
    RIGHT_LEG(Point(Figures.LEG_X, Figures.HIP, 0.0)),
    LEFT_ARM(Point(-Figures.ARM_X, Figures.SHOULDER, 0.0)),
    RIGHT_ARM(Point(Figures.ARM_X, Figures.SHOULDER, 0.0)),
}

/** One box of a person, placed from its [limb]'s pivot, so a leg turns about the hip. */
data class Part(val limb: Limb, val block: Block)

/** The colours a person is drawn in; the talk box draws a small face from the same colours. */
data class Look(val skin: Int, val hair: Int, val top: Int, val bottom: Int, val shoes: Int)

/**
 * The player and the townspeople as small, round-headed figures: a big head on a small body, as in a cosy game.
 * Each one stands on the spot (0, 0) facing south. Left and right are as seen from the front.
 */
object Figures {
    const val HIP = 0.36
    const val SHOULDER = 0.72
    const val LEG_X = 0.11
    const val ARM_X = 0.29

    /** Every figure is drawn this much larger than its boxes, so it reads on a phone. */
    const val SCALE = 1.15

    val playerLook = Look(skin = 0xF6D2B0, hair = 0x3A2A20, top = 0xD9534A, bottom = 0x3F5A8A, shoes = 0x5A3E2B)

    /** The player: a red jacket and a backpack. */
    val player: List<Part> = body(playerLook) + Part(Limb.BODY, Block(-0.17, 0.4, -0.27, 0.34, 0.34, 0.13, 0xE0A43A))

    fun lookOf(person: Townsperson): Look = when (person) {
        Townsperson.HANA -> Look(skin = 0xF7D7BC, hair = 0x7A4128, top = 0xF4EDE1, bottom = 0x6D5847, shoes = 0x4A3426)
        Townsperson.LEO -> Look(skin = 0xF3D3B5, hair = 0xC9A66B, top = 0x7B8F5A, bottom = 0x5B4B3A, shoes = 0x3B2E24)
        Townsperson.SUJIN -> Look(skin = 0xF5D5B8, hair = 0x1E1A1A, top = 0x5B7DB1, bottom = 0x2E2E36, shoes = 0xEDEDED)
        Townsperson.MARCUS -> Look(skin = 0xB98258, hair = 0x241911, top = 0x2F3B52, bottom = 0x2F3B52, shoes = 0x1E1E1E)
        Townsperson.GRANDMA -> Look(skin = 0xF4D8C4, hair = 0xDCDCDC, top = 0xA0586B, bottom = 0x6E6259, shoes = 0x4A3B30)
    }

    /** A townsperson, with the one thing that marks them out. */
    fun townsperson(person: Townsperson): List<Part> {
        val look = lookOf(person)
        val extra = when (person) {
            // A green apron and a bun.
            Townsperson.HANA -> listOf(
                Part(Limb.BODY, Block(-0.2, 0.3, 0.15, 0.4, 0.44, 0.03, 0x3E8E6A)),
                Part(Limb.BODY, Block(-0.13, 1.42, -0.2, 0.26, 0.18, 0.26, look.hair)),
            )
            // Glasses, and a red book in one hand.
            Townsperson.LEO -> listOf(
                Part(Limb.BODY, Block(-0.24, 1.1, 0.305, 0.48, 0.03, 0.02, 0x2B2B2B)),
                Part(Limb.RIGHT_ARM, Block(-0.1, -0.46, 0.0, 0.2, 0.26, 0.07, 0x9A3B3B)),
            )
            // A ponytail and a yellow badge on a lanyard.
            Townsperson.SUJIN -> listOf(
                Part(Limb.BODY, Block(-0.1, 0.9, -0.44, 0.2, 0.4, 0.12, look.hair)),
                Part(Limb.BODY, Block(-0.05, 0.42, 0.15, 0.1, 0.13, 0.02, 0xF2C14E)),
            )
            // A white collar and a red tie.
            Townsperson.MARCUS -> listOf(
                Part(Limb.BODY, Block(-0.12, 0.68, 0.15, 0.24, 0.08, 0.02, 0xFFFFFF)),
                Part(Limb.BODY, Block(-0.04, 0.4, 0.15, 0.08, 0.3, 0.025, 0xB33A3A)),
            )
            // A bun, a shawl over the shoulders and a walking stick.
            Townsperson.GRANDMA -> listOf(
                Part(Limb.BODY, Block(-0.13, 1.42, -0.2, 0.26, 0.16, 0.26, look.hair)),
                Part(Limb.BODY, Block(-0.26, 0.6, -0.18, 0.52, 0.14, 0.36, 0xE8D7B0)),
                Part(Limb.RIGHT_ARM, Block(0.0, -0.72, 0.06, 0.05, 0.72, 0.05, 0x7A5233)),
            )
        }
        return body(look) + extra
    }

    /** Every box of [parts] placed on the figure itself, for a figure that never moves its legs or arms. */
    fun standing(parts: List<Part>): List<Block> = parts.map { part ->
        val pivot = part.limb.pivot
        part.block.copy(x = part.block.x + pivot.x, y = part.block.y + pivot.y, z = part.block.z + pivot.z)
    }

    /** The shared body: legs and shoes, a small torso, arms with hands, and a big head with hair, eyes and cheeks. */
    private fun body(look: Look): List<Part> = buildList {
        for (leg in listOf(Limb.LEFT_LEG, Limb.RIGHT_LEG)) {
            add(Part(leg, Block(-0.08, -HIP, -0.09, 0.16, HIP, 0.18, look.bottom)))
            add(Part(leg, Block(-0.09, -HIP, -0.1, 0.18, 0.09, 0.22, look.shoes)))
        }
        for (arm in listOf(Limb.LEFT_ARM, Limb.RIGHT_ARM)) {
            add(Part(arm, Block(-0.06, -0.34, -0.07, 0.12, 0.36, 0.14, look.top)))
            add(Part(arm, Block(-0.06, -0.41, -0.07, 0.12, 0.08, 0.14, look.skin)))
        }
        add(Part(Limb.BODY, Block(-0.22, 0.32, -0.15, 0.44, 0.44, 0.3, look.top)))
        add(Part(Limb.BODY, Block(-0.34, 0.76, -0.3, 0.68, 0.6, 0.6, look.skin)))
        add(Part(Limb.BODY, Block(-0.37, 1.24, -0.33, 0.74, 0.2, 0.66, look.hair)))
        add(Part(Limb.BODY, Block(-0.37, 0.86, -0.33, 0.74, 0.4, 0.14, look.hair)))
        add(Part(Limb.BODY, Block(-0.35, 1.15, 0.22, 0.7, 0.12, 0.1, look.hair)))
        for (side in listOf(-0.37, 0.31)) add(Part(Limb.BODY, Block(side, 0.95, -0.33, 0.06, 0.32, 0.5, look.hair)))
        for (side in listOf(-0.17, 0.08)) add(Part(Limb.BODY, Block(side, 0.97, 0.3, 0.09, 0.14, 0.02, EYES)))
        for (side in listOf(-0.27, 0.17)) add(Part(Limb.BODY, Block(side, 0.9, 0.3, 0.1, 0.05, 0.015, CHEEKS)))
    }

    private const val EYES = 0x2A2220
    private const val CHEEKS = 0xF4A3A0
}
