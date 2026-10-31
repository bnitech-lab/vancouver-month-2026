package vancouvermonth.text

/** Reads amounts such as "1200", "1,200" or "$1200". Anything else, or a negative number, gives null. */
fun parseAmount(text: String): Int? {
    val digits = text.trim().removePrefix("$").replace(",", "")
    val amount = digits.toIntOrNull() ?: return null
    return if (amount >= 0) amount else null
}
