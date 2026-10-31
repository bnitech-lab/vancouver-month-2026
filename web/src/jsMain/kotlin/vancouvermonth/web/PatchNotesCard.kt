package vancouvermonth.web

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import vancouvermonth.text.PatchNotes

/**
 * The patch notes (업데이트 소식) as a card over the page: every update newest first, the newest one open and the older
 * ones folded under their version, date and headline, in a list that scrolls while 닫기 stays in sight. It is a dialog,
 * as the guide is: it takes the focus, 닫기 or Escape closes it, the focus goes back to what opened it, and what lies
 * under it is inert meanwhile. It opens only on the player's tap.
 *
 * The button that opens it carries a small dot while [PatchNotes.CURRENT] is newer than the last version the player
 * opened, kept in [memory]. A first visit has the dot too. Where the browser keeps nothing, the dot goes once the notes
 * are opened on this page and comes back on the next visit.
 */
internal class PatchNotesCard(private val memory: Memory) {
    private var shown: HTMLElement? = null

    /** What opened the card, which takes the focus back when it closes. */
    private var opener: HTMLElement? = null

    /** What was made inert under the card, to give back when it closes. */
    private val stilled = mutableListOf<HTMLElement>()

    /** The buttons on the page that open the card, whose dot goes as soon as it opens. */
    private val buttons = mutableListOf<HTMLButtonElement>()

    private var openedHere = false

    val isOpen: Boolean
        get() = shown?.isConnected == true

    /** True while the newest update was opened neither on this page nor on an earlier visit. */
    val hasNews: Boolean
        get() = !openedHere && PatchNotes.isNewer(PatchNotes.CURRENT, memory.read(KEY))

    // A page or an extension may send a plain Event named "keydown"; only a real key press is read.
    private val onKey: (Event) -> Unit = { event -> (event as? KeyboardEvent)?.let { key(it) } }

    /**
     * Adds the 업데이트 소식 button to [parent], which opens the card over [root]. [className] is "notes-open" for the
     * small pill by the sound switch, or another look for the same button.
     */
    fun button(parent: HTMLElement, root: HTMLElement, className: String = "notes-open") {
        val button = document.createElement("button") as HTMLButtonElement
        button.type = "button"
        button.className = className
        button.setAttribute("aria-haspopup", "dialog")
        showNews(button)
        button.onclick = { open(root, button) }
        parent.appendChild(button)
        buttons.removeAll { !it.isConnected }
        buttons += button
    }

    /** The button's words, and the dot with a word for screen readers while there is news. */
    private fun showNews(button: HTMLButtonElement) {
        button.textContent = PatchNotes.TITLE
        button.classList.toggle("news", hasNews)
        if (hasNews) {
            button.add("span", "visually-hidden", " ${PatchNotes.NEWS}")
        }
    }

    /** Opens the card over [root] with the focus on it, and remembers that the newest update was opened. */
    fun open(root: HTMLElement, from: HTMLElement? = null) {
        if (isOpen) return
        // A card taken off the page with the screen under it lets go first.
        close()
        opener = from
        openedHere = true
        memory.write(KEY, PatchNotes.CURRENT)
        buttons.removeAll { !it.isConnected }
        buttons.forEach(::showNews)

        val cover = element("div", "notes")
        val card = cover.add("section", "notes-card")
        card.setAttribute("role", "dialog")
        card.setAttribute("aria-modal", "true")
        card.setAttribute("aria-labelledby", "notes-title")
        card.setAttribute("tabindex", "-1")
        card.add("h2", "notes-title", PatchNotes.TITLE).id = "notes-title"
        val list = card.add("div", "notes-list")
        PatchNotes.ALL.forEachIndexed { index, note ->
            val entry = list.add("details", "notes-entry")
            if (index == 0) entry.setAttribute("open", "")
            entry.add("summary", "notes-summary").apply {
                add("span", "notes-version", PatchNotes.versionLabel(note.version))
                add("span", "notes-date", " ${PatchNotes.dateLabel(note.date)} ")
                add("span", "notes-headline", note.headline)
            }
            for ((name, lines) in note.sections) {
                entry.add("h3", "notes-section", name)
                val items = entry.add("ul", "notes-lines")
                for (line in lines) items.add("li", text = line)
            }
        }
        val close = card.add("button", "primary notes-close", PatchNotes.CLOSE) as HTMLButtonElement
        close.type = "button"
        close.onclick = { close() }

        for (under in root.children.asList()) {
            if (under is HTMLElement && !under.hasAttribute("inert")) {
                under.setAttribute("inert", "")
                stilled += under
            }
        }
        root.appendChild(cover)
        shown = cover
        document.addEventListener("keydown", onKey)
        card.focus()
        list.scrollTop = 0.0
    }

    /** Closes the card and gives the focus back to what opened it. */
    fun close() {
        val cover = shown ?: return
        shown = null
        document.removeEventListener("keydown", onKey)
        // Before the focus can go back, what is under the card must take it again.
        stilled.forEach { it.removeAttribute("inert") }
        stilled.clear()
        val from = opener
        opener = null
        if (!cover.isConnected) return
        cover.remove()
        if (from != null && from.isConnected) from.focus()
    }

    /** Escape closes the card; Tab and Shift+Tab go round its versions and 닫기, and never leave it. */
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
                val stops = cover.querySelectorAll("summary, button").asList().map { it as HTMLElement }
                val at = stops.indexOf(document.activeElement)
                val next = when {
                    at < 0 -> if (event.shiftKey) stops.size - 1 else 0
                    event.shiftKey -> (at + stops.size - 1) % stops.size
                    else -> (at + 1) % stops.size
                }
                stops[next].focus()
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
        /** The version the player last opened the notes on. */
        const val KEY = "vancouver-month.notes"
    }
}
