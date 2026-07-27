package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.data.SyncResult
import com.kaiharimoto.mastertool.core.deck.DeckEdit
import com.kaiharimoto.mastertool.core.deck.DeckEditor
import com.kaiharimoto.mastertool.core.deck.DeckStatistics
import com.kaiharimoto.mastertool.core.deck.DeckValidation
import com.kaiharimoto.mastertool.core.deck.DeckValidator
import com.kaiharimoto.mastertool.core.deck.RejectionReason
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
import kotlinx.serialization.json.JsonObject

/** A transient message shown in the snackbar, optionally with an undo action. */
data class Toast(
    val message: String,
    val undo: (() -> Unit)? = null,
    val id: Long = 0,
)

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

    var inspectedCard by mutableStateOf<Card?>(null)

    var filtersVisible by mutableStateOf(false)

    var toast by mutableStateOf<Toast?>(null)
        private set

    var format by mutableStateOf(Format.TCG)
        private set

    /** The `#ydkx-extended` payload of the loaded deck, preserved untouched. */
    private var extended: JsonObject? = null

    private val undoStack = ArrayDeque<Pair<Deck, String>>()
    private var searchJob: Job? = null
    private var toastCounter = 0L

    val validation: DeckValidation
        get() = DeckValidator.validate(deck, index::byId, format)

    val statistics: DeckStatistics
        get() = DeckStatistics.of(deck, index::byId, DeckSection.MAIN)

    val canUndo: Boolean get() = undoStack.isNotEmpty()

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
        filter = value
        runSearch(immediate = true)
    }

    fun clearFilters() = onFilterChange(CardFilter.NONE)

    private fun runSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        searchJob = scope.launch {
            // Debounced so a fast typist scans the pool once, not once per key.
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            results = index.search(query, filter.copy(format = format), limit = RESULT_LIMIT)
        }
    }

    // ---- editing -----------------------------------------------------------

    fun addCard(card: Card, section: DeckSection = card.requiredSection()) {
        applyEdit(DeckEditor.add(deck, card, section, format), card)
    }

    fun removeOne(card: Card, section: DeckSection) {
        val before = deck
        when (val result = DeckEditor.remove(deck, card.id, section)) {
            is DeckEdit.Applied -> {
                pushUndo(before)
                deck = result.deck
                showToast("Removed ${card.name}.", undo = { undo() })
            }
            is DeckEdit.Rejected -> Unit
        }
    }

    fun moveCard(card: Card, from: DeckSection, to: DeckSection) {
        applyEdit(DeckEditor.move(deck, card, from, to, format), card)
    }

    fun setCount(card: Card, section: DeckSection, count: Int) {
        applyEdit(DeckEditor.setCount(deck, card, section, count, format), card)
    }

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

    private fun pushUndo(previous: Deck) {
        undoStack.addLast(previous to deckName)
        while (undoStack.size > UNDO_DEPTH) undoStack.removeFirst()
    }

    fun undo() {
        val (previousDeck, previousName) = undoStack.removeLastOrNull() ?: return
        deck = previousDeck
        deckName = previousName
    }

    fun setFormat(value: Format) {
        format = value
        runSearch(immediate = true)
    }

    fun rename(value: String) {
        deckName = value
    }

    // ---- persistence -------------------------------------------------------

    fun newDeck() {
        pushUndo(deck)
        deck = Deck.EMPTY
        deckName = "Untitled Deck"
        deckId = null
        extended = null
        showToast("Started a new deck.", undo = { undo() })
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
        }
    }

    fun importFromFile() {
        scope.launch {
            val file = deps.fileAccess.importDeck() ?: return@launch
            val parsed = YdkCodec.parse(file.content)

            pushUndo(deck)
            deck = parsed.document.deck
            deckName = file.name.substringBeforeLast('.').ifBlank { "Imported Deck" }
            deckId = null
            extended = parsed.document.extended

            val warning = parsed.warnings.size
                .takeIf { it > 0 }
                ?.let { " ($it line${if (it == 1) "" else "s"} skipped)" }
                .orEmpty()
            showToast("Imported ${deck.totalCards} cards$warning.", undo = { undo() })
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

    fun copiesInDeck(id: CardId): Int = deck.copiesOf(id)

    fun remaining(card: Card): Int = DeckEditor.remainingCopies(deck, card, format)

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
