package vancouvermonth.web

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import vancouvermonth.text.Guide
import vancouvermonth.text.goalLine

/**
 * One card on how to play, over the town the first time it opens and again from the "?" in the town's top card.
 * It is a dialog: it takes the focus, 시작 or Escape closes it, and the focus goes back to the "?". While it is open the
 * town and its top card under it are inert, out of reach of taps, keys and screen readers. Closing it is
 * remembered in [memory], so it does not open by itself again; where the browser remembers nothing, it opens each time.
 */
internal class FirstRunGuide(private val memory: Memory) {
    private var shown: HTMLElement? = null

    /** What was made inert under the card, to give back when it closes. */
    private val stilled = mutableListOf<HTMLElement>()

    val isOpen: Boolean
        get() = shown?.isConnected == true

    /** Called with true when the card opens and false when it closes, so the town under it can slow down. */
    var onOpenChange: (Boolean) -> Unit = {}

    // A page or an extension may send a plain Event named "keydown"; only a real key press is read.
    private val onKey: (Event) -> Unit = { event -> (event as? KeyboardEvent)?.let { key(it) } }

    /** Opens the card over [root], unless it was closed on an earlier visit. */
    fun openFirstTime(root: HTMLElement) {
        if (memory.read(KEY) != SEEN) open(root)
    }

    /** Opens the card over [root] and moves the focus to its button. */
    fun open(root: HTMLElement) {
        if (isOpen) return
        val cover = element("div", "guide")
        val card = cover.add("section", "guide-card")
        card.setAttribute("role", "dialog")
        card.setAttribute("aria-modal", "true")
        card.setAttribute("aria-labelledby", "guide-title")
        // The focus lands on 시작, so a screen reader is pointed to the lines and the goal as the card's description.
        card.setAttribute("aria-describedby", "guide-lines guide-goal")
        card.add("h2", "guide-title", Guide.TITLE).id = "guide-title"
        val lines = card.add("ul", "guide-lines")
        lines.id = "guide-lines"
        for (line in Guide.LINES) lines.add("li", text = line)
        card.add("p", "guide-goal", goalLine()).id = "guide-goal"
        val start = card.add("button", "primary", Guide.START) as HTMLButtonElement
        start.type = "button"
        start.onclick = { close() }
        // The town is traced from the City of Vancouver's open data, whose licence asks for this line where it shows.
        card.add("p", "guide-credit", Guide.MAP_CREDIT)
        for (under in root.children.asList()) {
            if (under is HTMLElement && !under.hasAttribute("inert")) {
                under.setAttribute("inert", "")
                stilled += under
            }
        }
        root.appendChild(cover)
        shown = cover
        onOpenChange(true)
        document.addEventListener("keydown", onKey)
        start.focus()
        // On a short screen the card scrolls; focusing 시작 would scroll past the title, so it opens at its top.
        card.scrollTop = 0.0
    }

    /** Closes the card, remembers that it was seen, and gives the focus back to the "?". */
    fun close() {
        val cover = shown ?: return
        shown = null
        onOpenChange(false)
        document.removeEventListener("keydown", onKey)
        // Before the focus can go back, what is under the card must take it again.
        stilled.forEach { it.removeAttribute("inert") }
        stilled.clear()
        // A card taken off the page with the town was never closed by the player, so it is not counted as seen.
        if (!cover.isConnected) return
        cover.remove()
        memory.write(KEY, SEEN)
        (document.querySelector(".guide-open") as? HTMLElement)?.focus()
    }

    /** Adds the small "?" that opens the card again over [root]. */
    fun helpButton(parent: HTMLElement, root: HTMLElement) {
        val button = parent.add("button", "guide-open", "?") as HTMLButtonElement
        button.type = "button"
        button.setAttribute("aria-label", Guide.HELP)
        button.setAttribute("aria-haspopup", "dialog")
        button.onclick = { open(root) }
    }

    /** Escape closes the card; Tab stays on its one button. A card taken off the page with the town lets go here. */
    private fun key(event: KeyboardEvent) {
        val cover = shown ?: return
        if (!cover.isConnected) {
            close()
            return
        }
        when (event.key) {
            "Escape" -> {
                event.preventDefault()
                close()
            }
            "Tab" -> {
                event.preventDefault()
                (cover.querySelector("button") as? HTMLElement)?.focus()
            }
        }
    }

    private fun element(tag: String, className: String, text: String? = null): HTMLElement {
        val made = document.createElement(tag) as HTMLElement
        if (className.isNotEmpty()) made.className = className
        if (text != null) made.textContent = text
        return made
    }

    private fun HTMLElement.add(tag: String, className: String = "", text: String? = null): HTMLElement =
        element(tag, className, text).also { appendChild(it) }

    private companion object {
        const val KEY = "vancouver-month.guide"
        const val SEEN = "seen"
    }
}
