package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.data.SyncResult
import com.kaiharimoto.mastertool.core.deck.DeckEdit
import com.kaiharimoto.mastertool.core.deck.DeckEditor
import com.kaiharimoto.mastertool.core.deck.DeckSorter
import com.kaiharimoto.mastertool.core.deck.DeckStatistics
import com.kaiharimoto.mastertool.core.deck.DeckValidation
import com.kaiharimoto.mastertool.core.deck.DeckValidator
import com.kaiharimoto.mastertool.core.deck.RejectionReason
import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.core.search.CardFilter
import com.kaiharimoto.mastertool.core.search.CardIndex
import com.kaiharimoto.mastertool.core.ydk.YdkCodec
import com.kaiharimoto.mastertool.ui.AppDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** A transient message shown in the snackbar, optionally with an undo action. */
data class Toast(
    val message: String,
    val undo: (() -> Unit)? = null,
    val id: Long = 0,
)

/**
 * Identifies one edit so its snackbar can only undo *that* edit.
 *
 * A snackbar outlives the action that raised it. Without this, removing a card,
 * adding three more and then pressing the still-visible "Undo" would undo the
 * last add — the stack has no idea which button the user thought they pressed.
 */
data class UndoToken(val serial: Long)

/**
 * State holder for the deck builder.
 *
 * The app is landscape-locked on Android, so there is no rotation recreation to
 * survive and a plain remembered holder is enough — no ViewModel machinery, and
 * one fewer dependency whose API can drift.
 */
class DeckBuilderState(
    private val deps: AppDependencies,
    private val scope: CoroutineScope,
) {
    var index by mutableStateOf(CardIndex.EMPTY)
        private set

    var query by mutableStateOf("")
        private set

    var filter by mutableStateOf(CardFilter.NONE)
        private set

    var results by mutableStateOf<List<Card>>(emptyList())
        private set

    /** How many cards matched in total, which is usually more than were drawn. */
    var matchCount by mutableStateOf(0)
        private set

    var deck by mutableStateOf(Deck.EMPTY)
        private set

    var deckName by mutableStateOf("Untitled Deck")
        private set

    var deckId by mutableStateOf<String?>(null)
        private set

    var isSyncing by mutableStateOf(false)
        private set

    var syncMessage by mutableStateOf<String?>(null)
        private set

    /**
     * The inspector opens onto a list, not a card.
     *
     * Opening a card from the search results and then flipping through the rest
     * of them is how you compare candidates for a slot; opening one card and
     * having to close it to see the next is how you stop bothering.
     */
    var inspection by mutableStateOf<Inspection?>(null)

    var filtersVisible by mutableStateOf(false)

    var statsVisible by mutableStateOf(false)

    var issuesVisible by mutableStateOf(false)

    /** Which section the statistics panel is reporting on. */
    var statsSection by mutableStateOf(DeckSection.MAIN)

    var toast by mutableStateOf<Toast?>(null)
        private set

    var format by mutableStateOf(Format.TCG)
        private set

    /**
     * Set when an issue or a search result should be brought into view.
     *
     * Cleared by the pane once it has scrolled, so the same card can be
     * requested again later.
     */
    var revealRequest by mutableStateOf<RevealRequest?>(null)

    /** The `#ydkx-extended` payload of the loaded deck, preserved untouched. */
    private var extended: JsonObject? = null

    private val undoStack = ArrayDeque<Deck>()
    private val redoStack = ArrayDeque<Deck>()
    private var searchJob: Job? = null
    private var toastCounter = 0L
    private var editSerial = 0L

    /**
     * Recomputed only when the deck, pool or format actually changes.
     *
     * As a plain `get()` this ran on every recomposition of anything that read
     * it — including every keystroke in the deck-name field, since that shares a
     * recomposition scope with the legality readout — and validation walks every
     * distinct card scanning all three sections.
     */
    val validation: DeckValidation by derivedStateOf {
        DeckValidator.validate(deck, index::byId, format)
    }

    val statistics: DeckStatistics by derivedStateOf {
        DeckStatistics.of(deck, index::byId, statsSection)
    }

    /** Main-deck statistics, which is what opening-hand odds are drawn from. */
    val mainStatistics: DeckStatistics by derivedStateOf {
        DeckStatistics.of(deck, index::byId, DeckSection.MAIN)
    }

    /**
     * Backed by observable state rather than read from the deques directly: a
     * plain `ArrayDeque` is invisible to snapshot observation, so the undo button
     * never noticed it had become enabled.
     */
    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    // ---- lifecycle ---------------------------------------------------------

    fun start() {
        scope.launch {
            index = deps.cardRepository.loadFromCache()
            runSearch(immediate = true)

            val status = deps.cardRepository.status()
            if (status.isEmpty) {
                refreshCardPool(force = true, silentWhenFresh = false)
            } else {
                refreshCardPool(force = false, silentWhenFresh = true)
            }
        }
    }

    fun refreshCardPool(force: Boolean = true, silentWhenFresh: Boolean = false) {
        if (isSyncing) return
        scope.launch {
            isSyncing = true
            syncMessage = if (index.size == 0) "Downloading the card database…" else "Refreshing…"

            when (val result = deps.cardRepository.sync(force = force)) {
                is SyncResult.Updated -> {
                    index = deps.cardRepository.index.value
                    runSearch(immediate = true)
                    showToast("Card database updated — ${result.cardCount} cards.")
                }
                is SyncResult.UpToDate -> {
                    if (!silentWhenFresh) showToast("Card database is up to date.")
                }
                is SyncResult.Failed -> {
                    val suffix = if (result.cachedCardCount > 0) {
                        " Using the ${result.cachedCardCount} cards already on this device."
                    } else {
                        " No cards are cached yet — connect to the internet and try again."
                    }
                    showToast("Couldn't reach the card database.$suffix")
                }
            }

            isSyncing = false
            syncMessage = null
        }
    }

    // ---- search ------------------------------------------------------------

    fun onQueryChange(value: String) {
        query = value
        runSearch()
    }

    fun onFilterChange(value: CardFilter) {
        // The format is owned by the toolbar, not the filter sheet, so it is
        // stamped on here rather than left for whoever consumes the filter.
        filter = value.copy(format = format)
        runSearch(immediate = true)
    }

    fun clearFilters() = onFilterChange(CardFilter.NONE)

    private fun runSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        val activeQuery = query
        val activeFilter = filter
        searchJob = scope.launch {
            // Debounced so a fast typist scans the pool once, not once per key.
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            // Scoring 13,000 names with a bounded Levenshtein is far too much
            // work for the frame thread, and `scope` is the composition's.
            val outcome = withContext(deps.computeDispatcher) {
                index.search(activeQuery, activeFilter, limit = RESULT_LIMIT)
            }
            results = outcome.cards
            matchCount = outcome.matchCount
        }
    }

    // ---- editing -----------------------------------------------------------

    fun addCard(card: Card, section: DeckSection = card.requiredSection()) {
        applyEdit(DeckEditor.add(deck, card, section, format), card)
    }

    fun removeOne(card: Card, section: DeckSection) {
        when (val result = DeckEditor.remove(deck, card.id, section)) {
            is DeckEdit.Applied -> {
                val token = pushUndo(deck)
                deck = result.deck
                showToast("Removed ${card.name}.", undo = { undoIfCurrent(token) })
            }
            is DeckEdit.Rejected -> showToast(explain(result.reason, card))
        }
    }

    fun moveCard(card: Card, from: DeckSection, to: DeckSection) {
        applyEdit(DeckEditor.move(deck, card, from, to, format), card)
    }

    fun setCount(card: Card, section: DeckSection, count: Int) {
        applyEdit(DeckEditor.setCount(deck, card, section, count, format), card)
    }

    fun removeAllCopies(card: Card, section: DeckSection) {
        val remaining = deck[section].filterNot { it == card.id }
        if (remaining.size == deck[section].size) return

        val token = pushUndo(deck)
        deck = deck.with(section, remaining)
        showToast(
            "Removed every copy of ${card.name} from ${section.displayName}.",
            undo = { undoIfCurrent(token) },
        )
    }

    /**
     * Reorders a section.
     *
     * An edit, not a view setting: the stored order is what gets written back to
     * `.ydk`, so sorting only the display would leave the file disagreeing with
     * the deck. Being an edit also means one press of undo puts it back.
     */
    fun sortSection(section: DeckSection, mode: SortMode) {
        val current = deck[section]
        val sorted = DeckSorter.sort(current, index::byId, mode, format)
        if (sorted == current) return

        val token = pushUndo(deck)
        deck = deck.with(section, sorted)
        showToast(
            "Sorted ${section.displayName} Deck by ${mode.displayName.lowercase()}.",
            undo = { undoIfCurrent(token) },
        )
    }

    /** Every section currently holding [id], for controls that act on a card. */
    fun sectionsHolding(id: CardId): List<DeckSection> =
        DeckSection.entries.filter { section -> deck[section].any { it == id } }

    fun copiesIn(id: CardId, section: DeckSection): Int = deck[section].count { it == id }

    private fun applyEdit(edit: DeckEdit, card: Card) {
        when (edit) {
            is DeckEdit.Applied -> {
                if (edit.deck != deck) {
                    pushUndo(deck)
                    deck = edit.deck
                }
            }
            is DeckEdit.Rejected -> showToast(explain(edit.reason, card))
        }
    }

    private fun explain(reason: RejectionReason, card: Card): String = when (reason) {
        RejectionReason.SECTION_FULL -> "That section is full."
        RejectionReason.COPY_LIMIT -> {
            val limit = DeckEditor.copyLimit(card, format)
            if (limit == 0) {
                "${card.name} is Forbidden in ${format.name}."
            } else {
                "${card.name} is limited to $limit ${if (limit == 1) "copy" else "copies"}."
            }
        }
        RejectionReason.WRONG_SECTION ->
            if (card.isExtraDeck) {
                "${card.name} belongs in the Extra Deck."
            } else {
                "${card.name} can't go in the Extra Deck."
            }
        RejectionReason.NOT_PLAYABLE -> "${card.name} can't be put in a deck."
        RejectionReason.NOT_PRESENT -> "That card isn't in this section."
    }

    // ---- history -----------------------------------------------------------

    private fun pushUndo(previous: Deck): UndoToken {
        undoStack.addLast(previous)
        while (undoStack.size > UNDO_DEPTH) undoStack.removeFirst()
        // A new edit invalidates anything that was undone to reach this point.
        redoStack.clear()
        return stamp()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(deck)
        deck = previous
        stamp()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(deck)
        deck = next
        stamp()
    }

    /** Undoes an edit only while it is still the most recent one. */
    private fun undoIfCurrent(token: UndoToken) {
        if (editSerial == token.serial) undo()
    }

    private fun stamp(): UndoToken {
        editSerial++
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        return UndoToken(editSerial)
    }

    fun onFormatChange(value: Format) {
        format = value
        filter = filter.copy(format = value)
        runSearch(immediate = true)
    }

    /**
     * Renaming is not a deck edit and deliberately does not touch the undo
     * stack: it used to be restored alongside the deck, so undoing a card add
     * silently reverted a rename made after it.
     */
    fun rename(value: String) {
        deckName = value
    }

    // ---- persistence -------------------------------------------------------

    fun newDeck() {
        val token = pushUndo(deck)
        deck = Deck.EMPTY
        deckName = "Untitled Deck"
        deckId = null
        extended = null
        showToast("Started a new deck.", undo = { undoIfCurrent(token) })
    }

    fun save(onSaved: (String) -> Unit = {}) {
        scope.launch {
            val id = deckId ?: deps.newDeckId().also { deckId = it }
            deps.deckRepository.save(id, deckName.ifBlank { "Untitled Deck" }, deck, extended)
            showToast("Saved \"$deckName\".")
            onSaved(id)
        }
    }

    fun load(id: String) {
        scope.launch {
            val stored = deps.deckRepository.byId(id) ?: return@launch
            deck = stored.entry.deck
            deckName = stored.entry.name
            deckId = stored.entry.id
            extended = stored.extended
            undoStack.clear()
            redoStack.clear()
            stamp()
        }
    }

    fun importFromFile() {
        scope.launch {
            val file = deps.fileAccess.importDeck() ?: return@launch
            val parsed = YdkCodec.parse(file.content)

            val token = pushUndo(deck)
            deck = parsed.document.deck
            deckName = file.name.substringBeforeLast('.').ifBlank { "Imported Deck" }
            deckId = null
            extended = parsed.document.extended

            val warning = parsed.warnings.size
                .takeIf { it > 0 }
                ?.let { " ($it line${if (it == 1) "" else "s"} skipped)" }
                .orEmpty()
            showToast("Imported ${deck.totalCards} cards$warning.", undo = { undoIfCurrent(token) })
        }
    }

    fun exportToFile() {
        scope.launch {
            val text = YdkCodec.write(deck, createdBy = "kai's master tool", extended = extended)
            val extension = if (extended != null) "ydkx" else "ydk"
            val name = "${deckName.ifBlank { "deck" }}.$extension"
            if (deps.fileAccess.exportDeck(name, text)) {
                showToast("Exported $name.")
            }
        }
    }

    fun shareDeck() {
        scope.launch {
            val text = YdkCodec.write(deck, createdBy = "kai's master tool", extended = extended)
            val extension = if (extended != null) "ydkx" else "ydk"
            deps.fileAccess.shareDeck("${deckName.ifBlank { "deck" }}.$extension", text)
        }
    }

    // ---- helpers -----------------------------------------------------------

    /** Opens the inspector on [index] of [cards], which it can then page through. */
    fun inspect(cards: List<Card>, index: Int) {
        if (cards.isEmpty()) return
        inspection = Inspection(cards, index.coerceIn(0, cards.lastIndex))
    }

    fun inspect(card: Card) = inspect(listOf(card), 0)

    fun copiesInDeck(id: CardId): Int = deck.copiesOf(id)

    fun remaining(card: Card): Int = DeckEditor.remainingCopies(deck, card, format)

    /** Asks the owning pane to scroll [id] into view and flash it. */
    fun reveal(section: DeckSection, id: CardId) {
        val position = deck[section].indexOfFirst { it == id }
        if (position < 0) return
        revealRequest = RevealRequest(section, position, id, ++toastCounter)
    }

    fun showToast(message: String, undo: (() -> Unit)? = null) {
        toast = Toast(message, undo, ++toastCounter)
    }

    fun consumeToast() {
        toast = null
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 130L
        const val RESULT_LIMIT = 150
        const val UNDO_DEPTH = 50
    }
}

/** What the inspector is showing, and what it can page to from there. */
data class Inspection(val cards: List<Card>, val index: Int)

/** A request to scroll a card into view, raised by the issues panel. */
data class RevealRequest(
    val section: DeckSection,
    val position: Int,
    val cardId: CardId,
    val id: Long,
)
