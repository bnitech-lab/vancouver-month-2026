package vancouvermonth.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputTest {
    @Test
    fun `amounts may carry a dollar sign or commas`() {
        assertEquals(1200, parseAmount("1200"))
        assertEquals(1050, parseAmount("1,050"))
        assertEquals(1200, parseAmount("$1200"))
        assertNull(parseAmount("abc"))
        assertNull(parseAmount("-5"))
    }
}
