package com.kaiharimoto.mastertool.ui.deckbuilder

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaiharimoto.mastertool.core.data.SyncResult
import com.kaiharimoto.mastertool.core.deck.DeckEdit
import com.kaiharimoto.mastertool.core.deck.DeckEditor
import com.kaiharimoto.mastertool.core.deck.DeckSorter
import com.kaiharimoto.mastertool.core.deck.DeckTidy
import com.kaiharimoto.mastertool.core.deck.TidyBy
import com.kaiharimoto.mastertool.core.deck.HandSimulator
import com.kaiharimoto.mastertool.core.deck.HandTally
import com.kaiharimoto.mastertool.core.deck.OpeningHand
import com.kaiharimoto.mastertool.core.deck.Selection
import com.kaiharimoto.mastertool.core.deck.Selections
import com.kaiharimoto.mastertool.core.deck.DeckStatistics
import com.kaiharimoto.mastertool.core.deck.DeckValidation
import com.kaiharimoto.mastertool.core.deck.DeckValidator
import com.kaiharimoto.mastertool.core.deck.RejectionReason
import com.kaiharimoto.mastertool.core.deck.SaveStatus
import com.kaiharimoto.mastertool.core.deck.SaveTracking
import com.kaiharimoto.mastertool.core.deck.SavedSnapshot
import com.kaiharimoto.mastertool.core.deck.SortMode
import com.kaiharimoto.mastertool.core.siding.SidingCodec
import com.kaiharimoto.mastertool.core.siding.SidingDiff
import com.kaiharimoto.mastertool.core.siding.SidingEngine
import com.kaiharimoto.mastertool.core.siding.SidingPattern
import com.kaiharimoto.mastertool.core.siding.SidingProblem
import com.kaiharimoto.mastertool.core.siding.SidingSwap
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.model.CardId
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckSection
import com.kaiharimoto.mastertool.core.model.Format
import com.kaiharimoto.mastertool.core.search.CardFilter
import com.kaiharimoto.mastertool.core.layout.GridNavigation
import com.kaiharimoto.mastertool.core.layout.GridStep
import com.kaiharimoto.mastertool.core.search.CardIndex
import com.kaiharimoto.mastertool.core.search.Completions
import com.kaiharimoto.mastertool.core.search.NameCompletion
import com.kaiharimoto.mastertool.core.ydk.YdkCodec
import com.kaiharimoto.mastertool.ui.AppDependencies
import kotlinx.coroutines.CoroutineScope
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Everything that can cover the builder, topmost first.
 *
 * One declared order, rather than two hand-kept lists — "what suppresses the
 * shortcuts" and "what Escape closes" — that nothing made agree. They had been
 * edited separately twice, and a layer missing from the first is a layer whose
 * keys leak through to the builder underneath, while one missing from the second
 * is a layer Escape cannot close.
 *
 * The order is the order things stack: the egg covers everything, so it leaves
 * first.
 */
enum class Overlay {
    EGG,
    SHOWCASE,
    INSPECTOR,
    HELP,
    FILTERS,
    STATS,
    SIDING,
    TEST_HAND,
    SHOOTOUT,
    NOTES,
    ISSUES,
}

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

    /**
     * Whether the results are cards that *say* the query rather than are called
     * it, which happens only when nothing is named that.
     */
    var matchedText by mutableStateOf(false)
        private set

    private var currentDeck by mutableStateOf(Deck.EMPTY)

    /**
     * The decklist.
     *
     * Assigning it clears any selection, because a selection is a set of
     * positions into this list and every edit moves them. Enforced here rather
     * than at the eight places that write a deck: one of them forgetting would
     * leave a selection quietly pointing at different cards than the ones
     * highlighted, and the next thing done to it moves real cards.
     *
     * It also starts the autosave clock, for the same reason and with more at
     * stake: an edit that did not schedule a save is an edit that can be lost,
     * and there is no way to notice that has happened until it matters.
     */
    var deck: Deck
        get() = currentDeck
        private set(value) {
            if (value == currentDeck) return
            currentDeck = value
            selection = Selection.EMPTY
            scheduleAutosave()
        }

    /**
     * Which cards are picked up, if any.
     *
     * Empty almost always. A non-empty selection puts the pane holding it into
     * selection mode, where tapping a card adds or removes it from the group
     * rather than removing a copy from the deck.
     */
    var selection by mutableStateOf(Selection.EMPTY)
        private set

    var deckName by mutableStateOf("Untitled Deck")
        private set

    /**
     * Whatever the player wrote about this deck.
     *
     * Stored, loaded and shown in the library since the repository was written,
     * and until now unwritable — which was worse than a missing feature. `save`
     * defaults the column to the empty string, so every save from the builder
     * was quietly clearing whatever was in it.
     */
    var deckNotes by mutableStateOf("")
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

    var showcaseVisible by mutableStateOf(false)

    var helpVisible by mutableStateOf(false)

    var eggVisible by mutableStateOf(false)

    /**
     * Whether any text field in the builder has the caret.
     *
     * A count rather than a flag because focus moving from one field to another
     * reports the two changes in whichever order it likes, and a flag would end
     * up clear when focus had simply moved next door.
     */
    private var focusedTextFields by mutableStateOf(0)

    val textInputFocused: Boolean get() = focusedTextFields > 0

    fun onTextFieldFocusChanged(focused: Boolean) {
        focusedTextFields = (focusedTextFields + if (focused) 1 else -1).coerceAtLeast(0)
    }

    fun isOpen(overlay: Overlay): Boolean = when (overlay) {
        Overlay.EGG -> eggVisible
        Overlay.SHOWCASE -> showcaseVisible
        Overlay.INSPECTOR -> inspection != null
        Overlay.HELP -> helpVisible
        Overlay.FILTERS -> filtersVisible
        Overlay.STATS -> statsVisible
        Overlay.SIDING -> sidingVisible
        Overlay.TEST_HAND -> testHandVisible
        Overlay.SHOOTOUT -> shootoutVisible
        Overlay.NOTES -> notesVisible
        Overlay.ISSUES -> issuesVisible
    }

    /**
     * Both of these are exhaustive `when`s over [Overlay], which since Kotlin
     * 1.7 is enforced rather than merely warned about — so a new layer added to
     * the enum will not compile until it says how it opens and closes. That is
     * the point of the enum existing at all.
     */
    fun close(overlay: Overlay) {
        when (overlay) {
            Overlay.EGG -> eggVisible = false
            Overlay.SHOWCASE -> showcaseVisible = false
            Overlay.INSPECTOR -> inspection = null
            Overlay.HELP -> helpVisible = false
            Overlay.FILTERS -> filtersVisible = false
            Overlay.STATS -> statsVisible = false
            Overlay.SIDING -> sidingVisible = false
            Overlay.TEST_HAND -> testHandVisible = false
            Overlay.SHOOTOUT -> shootoutVisible = false
            Overlay.NOTES -> notesVisible = false
            Overlay.ISSUES -> issuesVisible = false
        }
    }

    /** Whether anything is covering the builder, which is what silences its keys. */
    val anyOverlayOpen: Boolean get() = Overlay.entries.any(::isOpen)

    /** Closes the topmost open layer. False when there was nothing to close. */
    fun dismissTopOverlay(): Boolean {
        val top = Overlay.entries.firstOrNull(::isOpen) ?: return false
        close(top)
        return true
    }

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

    /**
     * The `#ydkx-extended` payload of the loaded deck, preserved untouched.
     *
     * Snapshot state because the siding panel is read off it: loading a `.ydkx`
     * has to make its patterns appear without anything else being told.
     */
    private var extended by mutableStateOf<JsonObject?>(null)

    /** Whether the siding panel is up. */
    var sidingVisible by mutableStateOf(false)

    var notesVisible by mutableStateOf(false)

    var testHandVisible by mutableStateOf(false)

    var shootoutVisible by mutableStateOf(false)

    /**
     * The deck across the table.
     *
     * Loaded from a file and kept entirely separate from the one being built —
     * the whole point is to look at both at once, and an opponent's list that
     * could be edited by accident would be a very bad surprise.
     */
    var opponentDeck by mutableStateOf(Deck.EMPTY)
        private set

    var opponentName by mutableStateOf("")
        private set

    var yourOpening by mutableStateOf<OpeningHand?>(null)
        private set

    var theirOpening by mutableStateOf<OpeningHand?>(null)
        private set

    var youGoFirst by mutableStateOf(true)
        private set

    /** The hand currently on the table, if one has been dealt. */
    var testHand by mutableStateOf<OpeningHand?>(null)
        private set

    var testHandGoingFirst by mutableStateOf(true)
        private set

    /**
     * How the deck has been opening.
     *
     * Kept across reshuffles and cleared by hand, because the number only means
     * something over a run of hands -- one brick is not information.
     */
    var handTally by mutableStateOf(HandTally())
        private set

    /**
     * The decklist as last opened or saved — the one you registered.
     *
     * Everything siding means is relative to this. Without it "what have I
     * changed" has no answer, because the deck on screen is the only deck there
     * is.
     */
    var registeredDeck by mutableStateOf(Deck.EMPTY)
        private set

    /**
     * What has been swapped since then, as a plan.
     *
     * Nobody writes a siding plan and then performs it; they move cards between
     * the Main and Side decks until the matchup feels right, and that *is* the
     * plan. This is what makes recording one a button rather than a form.
     */
    val pendingSwap: SidingSwap by derivedStateOf {
        SidingDiff.between(registeredDeck, deck)
    }

    /**
     * Bumped when a whole decklist arrives, and never by an edit.
     *
     * The panes deal their cards in when it changes. Adding one card is not a
     * deal — re-dealing forty because a forty-first arrived would be a party
     * trick rather than a response to anything.
     */
    var dealSerial by mutableStateOf(0L)
        private set

    /**
     * The matchup plans carried by the loaded file, keyed as it keys them.
     *
     * Read straight out of the payload rather than kept alongside it, so there
     * is one copy and it cannot drift from what would be written back.
     */
    val sidingPatterns: Map<String, SidingPattern> by derivedStateOf {
        SidingCodec.read(extended)
    }

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
            matchedText = outcome.matchedText
        }
    }

    // ---- test hands --------------------------------------------------------

    /**
     * Shuffles the Main deck and deals.
     *
     * Uses the shared random rather than a seed, because the point is not to
     * reproduce a hand -- it is to keep seeing new ones until a pattern shows up.
     */
    fun dealTestHand(goingFirst: Boolean) {
        testHandGoingFirst = goingFirst
        testHand = HandSimulator.deal(deck.main, goingFirst, Random.Default)
    }

    /** Records a verdict and immediately deals the next hand, which is the loop. */
    fun judgeTestHand(playable: Boolean) {
        if (testHand == null) return
        handTally = handTally.judged(playable)
        dealTestHand(testHandGoingFirst)
    }

    /** One more card, for a hand that hinges on the draw. */
    fun drawOneMore() {
        testHand = testHand?.let(HandSimulator::drawOne)
    }

    // ---- the deck across the table -----------------------------------------

    /** Reads an opponent's `.ydk`, and deals immediately so the panel is never empty. */
    fun loadOpponent() {
        scope.launch {
            val file = deps.fileAccess.importDeck() ?: return@launch
            val parsed = YdkCodec.parse(file.content)
            opponentDeck = parsed.document.deck
            opponentName = file.name.substringBeforeLast('.').ifBlank { "Opponent" }
            dealShootout(youGoFirst)
        }
    }

    /**
     * Deals both openings at once.
     *
     * The choice is who goes first, not how many cards each side draws — one of
     * them going first means the other is going second, and dealing two
     * five-card hands would be a matchup that cannot happen.
     */
    fun dealShootout(goingFirst: Boolean) {
        youGoFirst = goingFirst
        yourOpening = HandSimulator.deal(deck.main, goingFirst, Random.Default)
        theirOpening = HandSimulator.deal(opponentDeck.main, !goingFirst, Random.Default)
    }

    fun resetHandTally() {
        handTally = HandTally()
    }

    // ---- siding ------------------------------------------------------------

    /**
     * Records what has been swapped so far as a plan for [opponent].
     *
     * Written into the payload the file already carries, so it goes back to the
     * desktop with everything else the desktop put there. Saving under a name
     * that exists replaces it — the second attempt at a matchup is the one you
     * meant.
     */
    fun recordSiding(opponent: String) {
        val name = opponent.trim()
        if (name.isEmpty()) return

        val swap = pendingSwap
        if (swap.isEmpty) {
            showToast("Nothing has been swapped yet.")
            return
        }

        val existing = sidingPatterns[name]
        val updated = (existing ?: SidingPattern(deckName = name)).copy(
            deckName = name,
            // Whichever half is being recorded keeps the other one, so the two
            // can be captured in either order and in separate sittings.
            goingFirst = if (recordingGoingFirst) swap else existing?.goingFirst ?: SidingSwap(),
            goingSecond = if (recordingGoingFirst) existing?.goingSecond ?: SidingSwap() else swap,
            // Three cards to recognise the matchup by, taken from what came in.
            thumbnails = swap.cardsIn.distinct().take(3),
        )

        extended = SidingCodec.write(extended, sidingPatterns + (name to updated))
        sidingVisible = false
        showToast(
            "Recorded ${swap.cardsIn.size} in / ${swap.cardsOut.size} out for $name, " +
                if (recordingGoingFirst) "going first." else "going second.",
        )
    }

    /** Which half of a matchup the next recording describes. */
    var recordingGoingFirst by mutableStateOf(true)

    /** Puts the deck back to the list you registered, abandoning a swap. */
    fun resetToRegistered() {
        if (deck == registeredDeck) return
        val token = pushUndo(deck)
        deck = registeredDeck
        showToast("Back to the registered decklist.", undo = { undoIfCurrent(token) })
    }

    /**
     * Swaps the deck over for a matchup.
     *
     * One edit and one undo, because that is what it is: between games two and
     * three you make a decision, and taking it back is one decision too.
     *
     * A plan that no longer fits the deck still applies as far as it can and
     * says what it could not do — there is a clock running, and refusing to side
     * because one card had already been moved would be the wrong trade.
     */
    fun applySiding(pattern: SidingPattern, goingFirst: Boolean) {
        val result = SidingEngine.apply(deck, pattern, goingFirst)
        if (result.deck == deck) {
            showToast("Nothing to swap for ${pattern.deckName}.")
            return
        }

        val token = pushUndo(deck)
        deck = result.deck
        sidingVisible = false

        val going = if (goingFirst) "going first" else "going second"
        showToast(
            if (result.isClean) {
                "Sided for ${pattern.deckName}, $going."
            } else {
                "Sided for ${pattern.deckName}, $going — ${describe(result.problems)}"
            },
            undo = { undoIfCurrent(token) },
        )
    }

    /**
     * What went wrong, in words that name the card.
     *
     * "You have two of these, not three" can be acted on at a table; "siding
     * failed" cannot.
     */
    private fun describe(problems: List<SidingProblem>): String {
        val first = problems.first()
        val rest = problems.size - 1
        val text = when (first) {
            is SidingProblem.NotInMain ->
                "${nameOf(first.card)} isn't in the Main deck ${first.wanted} times " +
                    "(found ${first.found})"
            is SidingProblem.NotInSide ->
                "${nameOf(first.card)} isn't in the Side deck ${first.wanted} times " +
                    "(found ${first.found})"
            is SidingProblem.Unbalanced ->
                "it swaps ${first.cardsIn} in for ${first.cardsOut} out"
        }
        return if (rest > 0) "$text, and $rest more." else "$text."
    }

    private fun nameOf(id: CardId): String = index.byId(id)?.name ?: id.value.toString()

    // ---- selection ---------------------------------------------------------
    //
    // Reached from the long-press menu rather than from a modifier key, so the
    // whole feature works on a tablet with no keyboard. Once a selection exists,
    // the pane holding it is in selection mode and a plain tap toggles.

    /** Starts a selection, or moves it to a different card. */
    fun select(section: DeckSection, index: Int) {
        selection = Selections.only(section, index)
    }

    fun toggleSelected(section: DeckSection, index: Int) {
        selection = Selections.toggle(selection, section, index)
    }

    /** Everything from the last card touched to this one, in reading order. */
    fun selectThrough(section: DeckSection, index: Int) {
        selection = Selections.extendTo(selection, section, index)
    }

    /** The rectangle between the last card touched and this one. */
    fun selectBlockThrough(section: DeckSection, index: Int, columns: Int) {
        selection = Selections.extendBlockTo(selection, section, index, columns, deck[section].size)
    }

    fun clearSelection() {
        selection = Selection.EMPTY
    }

    /** Whether a drag starting on this card should carry the whole group. */
    fun dragCarriesSelection(section: DeckSection, index: Int): Boolean =
        selection.size > 1 && selection.contains(section, index)

    /**
     * Moves everything selected so it lands before [insertBefore].
     *
     * Only within one section: a selection cannot span two, and a group arriving
     * in a different section would need the copy limit and the section rules
     * checked per card, which is the stepper's job and not a drag's.
     */
    fun moveSelectionTo(section: DeckSection, insertBefore: Int) {
        val moving = selection.takeIf { it.section == section }?.ordered() ?: return
        if (moving.isEmpty()) return

        val result = DeckEditor.reorderManyTo(deck, section, moving, insertBefore)
        if (result !is DeckEdit.Applied || result.deck == deck) return

        val token = pushUndo(deck)
        deck = result.deck

        // Assigning the deck cleared the selection, which is right in general and
        // wrong here: this is the one edit that knows exactly where the cards
        // went. Keeping them selected is what lets a group be nudged twice.
        val landed = DeckEditor.landingIndex(moving, insertBefore)
        selection = Selection(
            section = section,
            indices = (landed until landed + moving.size).toSet(),
            anchor = landed,
        )

        showToast(
            "Moved ${moving.size} cards.",
            undo = { undoIfCurrent(token) },
        )
    }

    /**
     * A card the panes should scroll to.
     *
     * Carries a serial because the same card can need revealing twice — arrow
     * into it, scroll away with the wheel, arrow again — and without one the
     * second request looks identical to the first and nothing happens.
     */
    data class Reveal(val section: DeckSection, val index: Int, val serial: Long)

    var reveal by mutableStateOf<Reveal?>(null)
        private set

    private var revealSerial = 0L

    private fun revealCursor(section: DeckSection, index: Int) {
        reveal = Reveal(section, index, ++revealSerial)
    }

    /**
     * Moves the cursor by arrow key, or grows the selection with it.
     *
     * There is no separate cursor: the selection *is* the cursor, which is what
     * makes the keyboard and the mouse the same feature rather than two that
     * have to be kept looking alike. An arrow pressed with nothing selected puts
     * the cursor down rather than moving one — the alternative is a first press
     * that appears to do nothing.
     */
    fun moveCursor(step: GridStep, columns: Int, extend: Boolean) {
        val section = selection.section ?: DeckSection.MAIN
        val count = deck[section].size
        if (count == 0) return

        if (selection.isEmpty) {
            selection = Selections.only(section, 0)
            revealCursor(section, 0)
            return
        }

        val to = GridNavigation.step(Selections.focusOf(selection), count, columns, step) ?: return
        selection = if (extend) {
            Selections.extendTo(selection, section, to)
        } else {
            Selections.only(section, to)
        }
        revealCursor(section, to)
    }

    /**
     * Carries the held cards one place, or one row, that way.
     *
     * No toast. Everything else that edits the deck announces itself, and it
     * should — but this fires on every repeat of a held key, and a stack of
     * toasts saying "moved 1 card" would bury the thing being arranged. The
     * cards visibly moving is the confirmation, which is the same argument that
     * keeps `addTopMatch` quiet.
     */
    fun carrySelection(step: GridStep, columns: Int) {
        val section = selection.section ?: return
        val moving = selection.ordered()
        if (moving.isEmpty()) return

        val delta = when (step) {
            GridStep.LEFT -> -1
            GridStep.RIGHT -> 1
            GridStep.UP -> -columns
            GridStep.DOWN -> columns
        }

        val result = DeckEditor.carry(deck, section, moving, delta)
        if (result !is DeckEdit.Applied || result.deck == deck) return

        pushUndo(deck)
        deck = result.deck

        // Assigning the deck cleared the selection, which is right in general
        // and wrong here: this edit knows exactly where the cards went, and
        // dropping them after one press would make the second press impossible.
        val landed = DeckEditor.carryLanding(moving, delta, count = deck[section].size)
        selection = Selection(
            section = section,
            indices = (landed until landed + moving.size).toSet(),
            anchor = landed,
        )
        revealCursor(section, landed)
    }

    // ---- editing -----------------------------------------------------------

    fun addCard(card: Card, section: DeckSection = card.requiredSection()) {
        // A card added to a pane that is already full lands out of sight, and
        // the only sign it worked is a number in the header changing. Which is
        // the same amount of feedback as nothing having happened.
        if (applyEdit(DeckEditor.add(deck, card, section, format), card)) {
            revealNewest(section, card.id)
        }
    }

    /**
     * Adds the best current match and leaves the search box exactly as it was.
     *
     * The tool this replaces cached its autocomplete matches so that a keypress
     * could add the top one instantly, and it is the fastest way to build a deck
     * that either program has: type three letters, press enter three times, and
     * there are three copies in the deck without a hand leaving the keyboard.
     *
     * Keeping the query is the whole point -- clearing it would make the second
     * copy cost as much as the first. There is no toast either: the card
     * appearing in the pane is the confirmation, and three of them in a row
     * would bury the screen.
     */
    fun addTopMatch() {
        val card = results.firstOrNull() ?: return
        addCard(card)
    }

    /**
     * The rest of the best typeable name, drawn as grey text after the cursor.
     *
     * Derived from `results`, which is debounced, so for a moment after each
     * keystroke this is computed against the previous query's names. That is
     * safe by construction rather than by timing: a completion is only offered
     * when the name continues what is in the box, so stale names offer nothing
     * rather than something wrong.
     */
    val completion: NameCompletion? by derivedStateOf {
        Completions.firstIn(query, results.map { it.name })
    }

    /**
     * Finishes the word, and nothing else.
     *
     * Notably it does not add the card. Completing a name and adding a copy are
     * two different intentions -- often the name is being finished in order to
     * read it, or to narrow the pool before choosing between three printings --
     * and binding them to one key would make Tab unusable for the first.
     */
    fun acceptCompletion(): String? {
        val name = completion?.name ?: return null
        query = name
        runSearch(immediate = true)
        return name
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

    // ---- drops -------------------------------------------------------------
    //
    // Every one of these goes through the same `applyEdit` as tapping does, so a
    // drop is undoable and a rejected drop explains itself in the same words.

    fun addCardAt(card: Card, section: DeckSection, index: Int) {
        applyEdit(DeckEditor.addAt(deck, card, section, index, format), card)
    }

    fun moveCardTo(
        card: Card,
        from: DeckSection,
        fromIndex: Int,
        to: DeckSection,
        insertBefore: Int,
    ) {
        applyEdit(DeckEditor.moveAt(deck, card, from, fromIndex, to, insertBefore, format), card)
    }

    /** Drag-out: the copy at [index] leaves the deck. */
    fun removeAt(card: Card, section: DeckSection, index: Int) {
        when (val result = DeckEditor.removeAt(deck, section, index)) {
            is DeckEdit.Applied -> {
                val token = pushUndo(deck)
                deck = result.deck
                showToast("Removed ${card.name}.", undo = { undoIfCurrent(token) })
            }
            is DeckEdit.Rejected -> Unit
        }
    }

    /**
     * Whether a drop would be accepted, for live feedback during a drag.
     *
     * [from] is null when the card is being dragged out of the search pane.
     */
    fun canDrop(card: Card, from: DeckSection?, to: DeckSection): Boolean {
        if (!DeckEditor.sectionAccepts(card, to)) return false
        // A move does not change how many copies the deck holds, so only a card
        // arriving from the pool can be stopped by the banlist.
        if (from == null && DeckEditor.remainingCopies(deck, card, format) <= 0) return false
        // Reordering within a section needs no room; arriving in a new one does.
        if (from != to && deck[to].size >= to.maxSize) return false
        return true
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

    /**
     * Tidies a section without deciding its order.
     *
     * Deliberately does not touch the section's stored sort mode the way
     * [sortSection] does. After a tidy the arrangement is still a manual one --
     * cards moved next to each other, nothing else disturbed -- and recording a
     * mode would claim the pane is now maintained by the program.
     *
     * Routed through `DeckEditor.rearrange` rather than assigning the list
     * directly, so a grouping function that somehow lost a card is refused
     * instead of quietly shortening the deck.
     */
    fun tidySection(section: DeckSection, mode: TidyBy) {
        val current = deck[section]
        val tidied = DeckTidy.apply(current, mode, index::byId)
        if (tidied == current) {
            showToast("${section.displayName} Deck is already tidy.")
            return
        }

        when (val edit = DeckEditor.rearrange(deck, section, tidied)) {
            is DeckEdit.Applied -> {
                val token = pushUndo(deck)
                deck = edit.deck
                showToast(
                    "${mode.label} in the ${section.displayName} Deck.",
                    undo = { undoIfCurrent(token) },
                )
            }
            is DeckEdit.Rejected -> showToast("That tidy would have changed the deck, so nothing moved.")
        }
    }

    /** Every section currently holding [id], for controls that act on a card. */
    fun sectionsHolding(id: CardId): List<DeckSection> =
        DeckSection.entries.filter { section -> deck[section].any { it == id } }

    fun copiesIn(id: CardId, section: DeckSection): Int = deck[section].count { it == id }

    /** Returns whether the deck actually changed, which not every caller cares about. */
    private fun applyEdit(edit: DeckEdit, card: Card): Boolean {
        when (edit) {
            is DeckEdit.Applied -> {
                if (edit.deck == deck) return false
                pushUndo(deck)
                deck = edit.deck
                return true
            }
            is DeckEdit.Rejected -> {
                showToast(explain(edit.reason, card))
                return false
            }
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
    fun updateNotes(value: String) {
        deckNotes = value
        scheduleAutosave()
    }

    fun rename(value: String) {
        deckName = value
        // The name is written into the same row as the cards, and a deck whose
        // title says one thing while its file says another is exactly the kind
        // of drift nobody notices until the file is the only copy left.
        scheduleAutosave()
    }

    // ---- persistence -------------------------------------------------------

    /**
     * Everything that swaps the open deck for a different one has to let go of
     * the old deck's id *first*.
     *
     * Assigning `deck` schedules an autosave, and an autosave that fires with
     * the previous deck's id writes the new contents into the old deck's row.
     * That is a saved deck being silently replaced by a different one, which is
     * the worst thing this program could do, so it is done in one place and the
     * three callers say why they are calling it.
     */
    private fun releaseOpenDeck() {
        autosaveJob?.cancel()
        deckId = null
        savedSnapshot = null
    }

    fun newDeck() {
        releaseOpenDeck()
        registeredDeck = Deck.EMPTY
        val token = pushUndo(deck)
        deck = Deck.EMPTY
        deckName = "Untitled Deck"
        deckNotes = ""
        extended = null
        showToast("Started a new deck.", undo = { undoIfCurrent(token) })
    }

    fun save(onSaved: (String) -> Unit = {}) {
        autosaveJob?.cancel()
        scope.launch {
            val id = deckId ?: deps.newDeckId().also { deckId = it }
            writeToDisk(id)
            // Saving is the other way a list becomes the one you registered,
            // which is what stops a saved swap reading as a pending one forever.
            registeredDeck = deck
            showToast("Saved \"$deckName\".")
            onSaved(id)
        }
    }

    /**
     * How long an edit sits before it is written down.
     *
     * Long enough that arranging a pane by hand is one write rather than forty,
     * short enough that nobody gets up and walks away inside it.
     */
    private val autosaveDelayMs = 1_500L

    private var autosaveJob: Job? = null

    /** What is on disk, or null if nothing is. */
    private var savedSnapshot by mutableStateOf<SavedSnapshot?>(null)

    /**
     * Whether the work in front of you exists anywhere else.
     *
     * Shown in the toolbar rather than kept internal, because the whole design
     * rests on the first save being manual: if the program will not write a
     * never-saved deck on its own, it owes the person a standing answer to
     * "would I lose this".
     */
    val saveStatus: SaveStatus by derivedStateOf {
        SaveTracking.status(deck, deckName, deckNotes, savedSnapshot)
    }

    private fun scheduleAutosave() {
        // Read now rather than after the delay: the point is to decide on the
        // state that prompted the edit, and `deckId` cannot become null while a
        // save is pending anyway -- `newDeck` cancels first.
        if (!SaveTracking.shouldAutosave(deckId, saveStatus)) return

        autosaveJob?.cancel()
        autosaveJob = scope.launch {
            delay(autosaveDelayMs)
            deckId?.let { writeToDisk(it) }
        }
    }

    /**
     * The one place a deck reaches the database.
     *
     * Both callers have to leave the snapshot matching what was written, and a
     * snapshot that drifted from disk would report unsaved changes forever or,
     * worse, report none while there were some.
     */
    private suspend fun writeToDisk(id: String) {
        val name = deckName.ifBlank { "Untitled Deck" }
        val written = deck
        val notes = deckNotes
        deps.deckRepository.save(id, name, written, extended, notes)
        savedSnapshot = SavedSnapshot(written, name, notes)
    }

    fun load(id: String) {
        scope.launch {
            val stored = deps.deckRepository.byId(id) ?: return@launch
            releaseOpenDeck()
            deck = stored.entry.deck
            registeredDeck = stored.entry.deck
            dealSerial++
            deckName = stored.entry.name
            deckNotes = stored.entry.notes
            deckId = stored.entry.id
            savedSnapshot = SavedSnapshot(stored.entry.deck, stored.entry.name, stored.entry.notes)
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

            // An imported file is a new deck, not an edit to the open one.
            releaseOpenDeck()
            val token = pushUndo(deck)
            deck = parsed.document.deck
            registeredDeck = parsed.document.deck
            dealSerial++
            deckName = file.name.substringBeforeLast('.').ifBlank { "Imported Deck" }
            deckNotes = ""
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

    /** Steps the inspector through the list it was opened on. */
    fun pageInspection(delta: Int) {
        val current = inspection ?: return
        val next = (current.index + delta).coerceIn(0, current.cards.lastIndex)
        if (next != current.index) inspection = current.copy(index = next)
    }

    /** Keeps the stored page in step when the inspector is swiped rather than keyed. */
    fun onInspectionPageChanged(index: Int) {
        val current = inspection ?: return
        if (current.index != index) inspection = current.copy(index = index)
    }

    fun copiesInDeck(id: CardId): Int = deck.copiesOf(id)

    fun remaining(card: Card): Int = DeckEditor.remainingCopies(deck, card, format)

    /** Asks the owning pane to scroll [id] into view and flash it. */
    fun reveal(section: DeckSection, id: CardId) {
        revealAt(section, deck[section].indexOfFirst { it == id }, id, flash = true)
    }

    /**
     * Shows the copy that was just added, which is the *last* one.
     *
     * Adding a third Ash Blossom and scrolling to the first is an answer to a
     * question nobody asked.
     */
    private fun revealNewest(section: DeckSection, id: CardId) {
        revealAt(section, deck[section].indexOfLast { it == id }, id, flash = false)
    }

    private fun revealAt(section: DeckSection, position: Int, id: CardId, flash: Boolean) {
        if (position < 0) return
        revealRequest = RevealRequest(section, position, id, ++toastCounter, flash)
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
    /**
     * Whether the card should also be highlighted when it arrives.
     *
     * On for a card somebody went looking for — the deck check naming a
     * problem — and off for one they just put there themselves, where the card
     * appearing is the confirmation and a flash on every add would be a strobe.
     */
    val flash: Boolean = true,
)
