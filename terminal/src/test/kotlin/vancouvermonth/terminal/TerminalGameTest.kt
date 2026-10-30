package vancouvermonth.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vancouvermonth.core.FirstMinute

class TerminalGameTest {
    private fun playWith(seed: Int, vararg answers: String): Pair<Boolean, String> {
        val input = answers.iterator()
        val output = StringBuilder()
        // Each answer is echoed after its prompt, the way a terminal shows what the player typed.
        val again = TerminalGame(
            input = { if (input.hasNext()) input.next().also { output.append(it + "\n") } else null },
            output = { output.append(it) },
        ).play(seed)
        return again to output.toString()
    }

    @Test
    fun `a round opens with the title and shows the real total after the guess`() {
        val (again, screen) = playWith(4821, "1", "1200", "n")
        val total = FirstMinute(4821).rooms[0].totalMonthlyCost
        assertFalse(again)
        assertEquals("BNI Tech", screen.lines().first { it.isNotBlank() })
        assertTrue("판 번호 4821" in screen)
        assertTrue("한 달 총비용  ${money(total)}" in screen)
    }

    @Test
    fun `seed 1011 plays exactly as the README shows`() {
        val readme = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "README.md") }
            .first { it.exists() }
            .readText()
        val sample = readme.substringAfter("```\nBNI Tech").substringBefore("```")
        val (_, screen) = playWith(1011, "2", "1150", "n")
        val expectedLines = ("BNI Tech" + sample).lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val shownLines = screen.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        assertEquals(expectedLines, shownLines)
    }

    @Test
    fun `wrong answers are asked again`() {
        val (_, screen) = playWith(4821, "9", "two", "2", "abc", "1,150", "n")
        assertTrue("1부터 3 사이의 번호로 골라 주세요." in screen)
        assertTrue("숫자로 적어 주세요." in screen)
        assertTrue("한 달 총비용  " in screen)
    }

    @Test
    fun `y asks for another round`() {
        val (again, _) = playWith(4821, "3", "1000", "y")
        assertTrue(again)
    }

    @Test
    fun `the game ends quietly when the input ends`() {
        val (again, screen) = playWith(4821)
        assertFalse(again)
        assertTrue("어느 방?" in screen)
    }

    @Test
    fun `room choices count from one`() {
        assertEquals(0, parseRoomChoice("1", 3))
        assertEquals(2, parseRoomChoice(" 3 ", 3))
        assertNull(parseRoomChoice("0", 3))
        assertNull(parseRoomChoice("4", 3))
        assertNull(parseRoomChoice("x", 3))
    }

    @Test
    fun `amounts may carry a dollar sign or commas`() {
        assertEquals(1200, parseAmount("1200"))
        assertEquals(1050, parseAmount("1,050"))
        assertEquals(1200, parseAmount("$1200"))
        assertNull(parseAmount("abc"))
        assertNull(parseAmount("-5"))
    }

    @Test
    fun `money is written with a dollar sign and commas`() {
        assertEquals("$1,245", money(1245))
        assertEquals("$960", money(960))
    }

    @Test
    fun `a seed can be given in either form`() {
        assertEquals(4821, parseSeed(arrayOf("--seed", "4821")))
        assertEquals(4821, parseSeed(arrayOf("--seed=4821")))
        assertNull(parseSeed(arrayOf()))
    }
}
