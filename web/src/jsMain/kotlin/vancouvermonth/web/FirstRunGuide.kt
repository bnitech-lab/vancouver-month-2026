package vancouvermonth.web

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import vancouvermonth.text.Guide
import vancouvermonth.text.goalLine

/**
 * One card on how to play, over the town, from the "?" in the town's top card. It never opens by itself: the intro says
 * the same before the month starts. It is a dialog: it takes the focus, 닫기 or Escape closes it, and the focus goes
 * back to the "?". While it is open the town and its top card under it are inert, out of reach of taps, keys and screen
 * readers.
 */
internal class FirstRunGuide {
    private var shown: HTMLElement? = null

    /** What was made inert under the card, to give back when it closes. */
    private val stilled = mutableListOf<HTMLElement>()

    val isOpen: Boolean
        get() = shown?.isConnected == true

    /** Called with true when the card opens and false when it closes, so the town under it can slow down. */
    var onOpenChange: (Boolean) -> Unit = {}

    /** The update news the card's foot links to, read as it opens; null leaves the link out. */
    var notes: () -> PatchNotesCard? = { null }

    // A page or an extension may send a plain Event named "keydown"; only a real key press is read.
    private val onKey: (Event) -> Unit = { event -> (event as? KeyboardEvent)?.let { key(it) } }

    /** Opens the card over [root] and moves the focus to its button. */
    fun open(root: HTMLElement) {
        if (isOpen) return
        val cover = element("div", "guide")
        val card = cover.add("section", "guide-card")
        card.setAttribute("role", "dialog")
        card.setAttribute("aria-modal", "true")
        card.setAttribute("aria-labelledby", "guide-title")
        // The focus lands on 닫기, so a screen reader is pointed to the lines and the goal as the card's description.
        card.setAttribute("aria-describedby", "guide-lines guide-goal")
        card.add("h2", "guide-title", Guide.TITLE).id = "guide-title"
        val lines = card.add("ul", "guide-lines")
        lines.id = "guide-lines"
        for (line in Guide.LINES) lines.add("li", text = line)
        card.add("p", "guide-goal", goalLine()).id = "guide-goal"
        val closeButton = card.add("button", "primary", Guide.CLOSE) as HTMLButtonElement
        closeButton.type = "button"
        closeButton.onclick = { close() }
        // At the foot, next to the map's credit: the update news, which open over this card and come back to it.
        notes()?.let { news -> news.button(card.add("p", "patch-guide-foot"), root, "patch-link") }
        // The town is traced from the City of Vancouver's and the City of Burnaby's open data, whose licences ask for
        // these lines where it shows.
        mapCredit(card, "guide-credit")
        burnabyCredit(card, "guide-credit")
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
        closeButton.focus()
        // On a short screen the card scrolls; focusing 닫기 would scroll past the title, so it opens at its top.
        card.scrollTop = 0.0
    }

    /**
     * Keeps [under], drawn afresh while the card is open, out of reach as the rest under the card is. Should the update
     * news no longer open ([notes]), as with a talk opened under the card on arrival, its link goes.
     */
    fun keepUnder(under: HTMLElement) {
        val cover = shown ?: return
        if (!cover.isConnected) return
        under.setAttribute("inert", "")
        stilled.removeAll { !it.isConnected }
        stilled += under
        if (notes() == null) {
            val foot = cover.querySelector(".patch-guide-foot") ?: return
            if (foot.contains(document.activeElement)) (cover.querySelector("button.primary") as? HTMLElement)?.focus()
            foot.remove()
        }
    }

    /** Closes the card and gives the focus back to the "?". */
    fun close() {
        val cover = shown ?: return
        shown = null
        onOpenChange(false)
        document.removeEventListener("keydown", onKey)
        // Before the focus can go back, what is under the card must take it again.
        stilled.forEach { it.removeAttribute("inert") }
        stilled.clear()
        // A card taken off the page with the town has no "?" to go back to.
        if (!cover.isConnected) return
        cover.remove()
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

    /**
     * Escape closes the card; Tab and Shift+Tab go round its buttons and its link to the licence, and never leave it.
     * A card taken off the page with the town lets go here.
     */
    private fun key(event: KeyboardEvent) {
        val cover = shown ?: return
        if (!cover.isConnected) {
            close()
            return
        }
        // The update news open over the card make it inert, and take the keys until they close.
        if (cover.hasAttribute("inert")) return
        when (event.key) {
            "Escape" -> {
                event.preventDefault()
                close()
            }
            "Tab" -> {
                event.preventDefault()
                val stops = cover.querySelectorAll("button, a[href]").asList().map { it as HTMLElement }
                val at = stops.indexOf(document.activeElement)
                val next = when {
                    at < 0 -> 0
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
}

/**
 * The map's credit, as the City of Vancouver's open data licence asks wherever its data shows, at the end of [parent]:
 * the statement, with the licence's name a link to the licence that opens in a new tab, so the game stays where it is;
 * a screen reader hears that it opens in a new tab.
 */
internal fun mapCredit(parent: HTMLElement, className: String): HTMLElement {
    val credit = document.createElement("p") as HTMLElement
    credit.className = className
    val name = Guide.MAP_CREDIT.indexOf(Guide.MAP_LICENCE)
    credit.appendChild(document.createTextNode(Guide.MAP_CREDIT.substring(0, name)))
    val link = document.createElement("a") as HTMLAnchorElement
    link.textContent = Guide.MAP_LICENCE
    link.setAttribute("aria-label", "${Guide.MAP_LICENCE} ${Guide.NEW_TAB}")
    link.href = Guide.MAP_LICENCE_URL
    link.target = "_blank"
    link.rel = "noopener"
    credit.appendChild(link)
    credit.appendChild(document.createTextNode(Guide.MAP_CREDIT.substring(name + Guide.MAP_LICENCE.length)))
    parent.appendChild(credit)
    return credit
}

/**
 * The Burnaby part of the map's credit, as the City of Burnaby's open data licence asks, at the end of [parent], next to
 * [mapCredit]'s line: the statement, the source, and the licence's name a link to the licence, opening in a new tab.
 */
internal fun burnabyCredit(parent: HTMLElement, className: String): HTMLElement {
    val credit = document.createElement("p") as HTMLElement
    credit.className = "$className burnaby-credit"
    val line = Guide.BURNABY_MAP_LINE
    val name = line.lastIndexOf(Guide.BURNABY_MAP_LICENCE)
    credit.appendChild(document.createTextNode(line.substring(0, name)))
    val link = document.createElement("a") as HTMLAnchorElement
    link.textContent = Guide.BURNABY_MAP_LICENCE
    link.setAttribute("aria-label", "${Guide.BURNABY_MAP_LICENCE} ${Guide.NEW_TAB}")
    link.href = Guide.BURNABY_MAP_LICENCE_URL
    link.target = "_blank"
    link.rel = "noopener"
    credit.appendChild(link)
    credit.appendChild(document.createTextNode(line.substring(name + Guide.BURNABY_MAP_LICENCE.length)))
    parent.appendChild(credit)
    return credit
}
