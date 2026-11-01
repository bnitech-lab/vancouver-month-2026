package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContinueTest {
    @Test
    fun `the card says the day and the balance it goes on from`() {
        assertEquals("9월 7일부터 · 잔고 \$1,455", continueFrom(7, 1_455))
        assertEquals("9월 30일부터 · 잔고 \$85", continueFrom(30, 85))
        // Past the morning, the time the day had reached.
        assertEquals("9월 7일 오후 2시부터 · 잔고 \$1,455", continueFrom(7, 1_455, hour = 14))
        assertEquals("9월 7일부터 · 잔고 \$1,455", continueFrom(7, 1_455, hour = 8))
    }

    @Test
    fun `another run's start card names the day of the month it would drop, not a run number`() {
        assertEquals("시작하면 이어하던 9월 14일의 한 달은 지워져요.", replacesKept(14))
        assertTrue(replacesKept(30).length <= 28)
    }

    @Test
    fun `its words are short, and say that starting over drops the kept month`() {
        assertEquals("이어하기", Continue.CONTINUE)
        assertEquals("새로 시작", Continue.NEW)
        assertTrue(Continue.NEW in Continue.NOTE && "지워져요" in Continue.NOTE)
        for (line in listOf(Continue.TITLE, Continue.NOTE)) assertTrue(line.length <= 24, "Too long for a phone: $line")
    }
}
