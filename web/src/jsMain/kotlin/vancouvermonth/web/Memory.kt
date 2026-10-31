package vancouvermonth.web

import kotlinx.browser.window

/** The two calls of the browser's localStorage the page uses, so a test can hand in a storage of its own. */
internal external interface KeyValueStore {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
}

/**
 * What the page remembers between visits: whether the sound was on, and whether the guide was seen. Where the browser
 * refuses its storage (a private window, or blocked site data), reading or writing throws; then nothing is remembered
 * and the page goes on as on a first visit.
 */
internal class Memory(private val store: () -> KeyValueStore?) {
    fun read(key: String): String? = try {
        store()?.getItem(key)
    } catch (error: Throwable) {
        null
    }

    fun write(key: String, value: String) {
        try {
            store()?.setItem(key, value)
        } catch (error: Throwable) {
            // Not remembered, then: the next visit starts as the first did.
        }
    }
}

/** The page's own memory, in the browser's localStorage. */
internal fun pageMemory(): Memory = Memory { window.localStorage.unsafeCast<KeyValueStore>() }
