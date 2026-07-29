package com.kaiharimoto.mastertool.core.data

import com.kaiharimoto.mastertool.core.db.DeckEntity
import com.kaiharimoto.mastertool.core.db.DeckVersionEntity
import com.kaiharimoto.mastertool.core.db.MasterToolDatabase
import com.kaiharimoto.mastertool.core.deck.DeckHistory
import com.kaiharimoto.mastertool.core.deck.VersionMark
import com.kaiharimoto.mastertool.core.model.Deck
import com.kaiharimoto.mastertool.core.model.DeckEntry
import com.kaiharimoto.mastertool.core.ydk.YdkCodec
import com.kaiharimoto.mastertool.core.ydk.YdkDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A saved deck together with the opaque YDKX payload it was imported with. */
data class StoredDeck(
    val entry: DeckEntry,
    val extended: JsonObject? = null,
) {
    fun toDocument(): YdkDocument = YdkDocument(entry.deck, createdBy = null, extended = extended)
}

/**
 * A deck as it stood before you changed it.
 *
 * Whole, not a diff: it has to be openable on its own, and comparing it against
 * what you have open reads every section anyway.
 */
data class DeckVersion(
    val id: String,
    val deckId: String,
    /** What it was named, if it was named on purpose. Named versions are kept. */
    val name: String?,
    val keptAtEpochMs: Long,
    val deck: Deck,
    val notes: String,
    val extended: JsonObject? = null,
)

/**
 * Persistence for saved decks.
 *
 * Ids are supplied by the caller rather than generated here so the same
 * repository works with a UUID on device and a fixed id in tests.
 */
class DeckRepository(
    private val database: MasterToolDatabase,
    private val clock: () -> Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun all(): List<StoredDeck> = withContext(ioDispatcher) {
        database.deckQueries.selectAll().executeAsList().map(::toStored)
    }

    suspend fun byId(id: String): StoredDeck? = withContext(ioDispatcher) {
        database.deckQueries.selectById(id).executeAsOneOrNull()?.let(::toStored)
    }

    suspend fun save(
        id: String,
        name: String,
        deck: Deck,
        extended: JsonObject? = null,
        notes: String = "",
    ): StoredDeck = withContext(ioDispatcher) {
        val now = clock()
        val existing = database.deckQueries.selectById(id).executeAsOneOrNull()
        val createdAt = existing?.createdAtEpochMs ?: now

        val main = CardMapper.joinIds(deck.main)
        val extra = CardMapper.joinIds(deck.extra)
        val side = CardMapper.joinIds(deck.side)
        val extendedJson = extended?.let { json.encodeToString(JsonObject.serializer(), it) }

        val changed = existing == null ||
            existing.main != main ||
            existing.extra != extra ||
            existing.side != side ||
            existing.notes != notes ||
            existing.extendedJson != extendedJson

        // A save that changes nothing is not an edit, so it neither keeps a copy
        // of what is already there nor claims the deck was touched. Letting it
        // move the stamp would also restart the clock below, and the next real
        // edit would go unrecorded.
        val updatedAt = if (changed) now else existing.updatedAtEpochMs

        if (changed && existing != null && DeckHistory.keepsACopy(existing.updatedAtEpochMs, now)) {
            keep(existing)
        }

        database.deckQueries.upsert(
            id = id,
            name = name,
            main = main,
            extra = extra,
            side = side,
            notes = notes,
            extendedJson = extendedJson,
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = updatedAt,
        )

        StoredDeck(
            DeckEntry(id, name, deck, createdAt, updatedAt, notes),
            extended,
        )
    }

    /**
     * Keeps the deck exactly as it stands, without waiting for the clock.
     *
     * The half of this that matters before an event: *this is the list I
     * registered*. The automatic rule keeps a copy of what a deck was when you
     * come back to it, which is the right default and says nothing about which
     * moments were important — only somebody about to play knows that.
     *
     * Also what restoring an older version does first, so that going back can
     * never be the thing that loses the present.
     *
     * Naming a moment that is already kept renames it rather than duplicating
     * it: the id is derived from the moment, so there is only one row for it.
     */
    suspend fun keepNow(deckId: String, name: String? = null): DeckVersion? =
        withContext(ioDispatcher) {
            val row = database.deckQueries.selectById(deckId).executeAsOneOrNull()
                ?: return@withContext null

            keep(row, name?.trim()?.takeIf(String::isNotEmpty))
            database.deckVersionQueries
                .selectById("$deckId@${row.updatedAtEpochMs}")
                .executeAsOneOrNull()
                ?.let(::toVersion)
        }

    /** Every version of a deck, most recent first. */
    suspend fun versions(deckId: String): List<DeckVersion> = withContext(ioDispatcher) {
        database.deckVersionQueries.selectForDeck(deckId).executeAsList().map(::toVersion)
    }

    suspend fun version(id: String): DeckVersion? = withContext(ioDispatcher) {
        database.deckVersionQueries.selectById(id).executeAsOneOrNull()?.let(::toVersion)
    }

    /**
     * Names a version, or takes the name away again.
     *
     * A named one is never thinned out, so this is how somebody says *keep this*
     * about the list they registered — and un-naming it is how they take that
     * back, rather than having to delete the version to stop it being held.
     */
    suspend fun nameVersion(id: String, name: String?) = withContext(ioDispatcher) {
        database.deckVersionQueries.setName(name?.trim()?.takeIf(String::isNotEmpty), id)
    }

    /**
     * Puts a deck back to how it was, and keeps how it is first.
     *
     * There is no way to lose work with this, which is why there is no
     * confirmation on it: going back keeps the present as a version on the way
     * past, so the button that undoes an afternoon is also the button that undoes
     * itself. Going back to what is already there does nothing at all rather than
     * writing a version of the deck as it already stands.
     *
     * Deliberately an ordinary edit of the deck rather than a second kind of open
     * document: the deck keeps its id, its name and its place in the library, and
     * everything downstream — autosave, export, the file on disk — carries on
     * without knowing this happened.
     */
    suspend fun restore(deckId: String, versionId: String): StoredDeck? {
        val version = version(versionId)?.takeIf { it.deckId == deckId } ?: return null
        val current = byId(deckId) ?: return null

        if (current.entry.deck == version.deck &&
            current.entry.notes == version.notes &&
            current.extended == version.extended
        ) {
            return current
        }

        keepNow(deckId)

        return save(
            id = deckId,
            name = current.entry.name,
            deck = version.deck,
            extended = version.extended,
            notes = version.notes,
        )
    }

    /**
     * Keeps the stored deck as a version before it is written over.
     *
     * Stamped with when the deck actually last stood that way rather than with
     * now, which is both more truthful and what makes the id derivable: one deck
     * cannot have stood two ways at one instant, so `deck@stamp` is unique
     * without a generator — and `INSERT OR REPLACE` makes a repeat harmless
     * rather than a crash.
     */
    private fun keep(row: DeckEntity, name: String? = null) {
        val id = "${row.id}@${row.updatedAtEpochMs}"

        // Keeping a moment that is already kept must not quietly un-name it: the
        // id is derived from the moment, so restoring an older version right
        // after naming the current one lands on the same row.
        val already = database.deckVersionQueries.selectById(id).executeAsOneOrNull()?.name

        database.deckVersionQueries.insert(
            id = id,
            deckId = row.id,
            name = name ?: already,
            main = row.main,
            extra = row.extra,
            side = row.side,
            notes = row.notes,
            extendedJson = row.extendedJson,
            keptAtEpochMs = row.updatedAtEpochMs,
        )

        DeckHistory
            .surplus(database.deckVersionQueries.selectForDeck(row.id).executeAsList().map(::toMark))
            .forEach { database.deckVersionQueries.deleteById(it.id) }
    }

    private fun toMark(row: DeckVersionEntity) = VersionMark(row.id, row.keptAtEpochMs, row.name)

    private fun toVersion(row: DeckVersionEntity) = DeckVersion(
        id = row.id,
        deckId = row.deckId,
        name = row.name,
        keptAtEpochMs = row.keptAtEpochMs,
        deck = Deck(
            main = CardMapper.splitIds(row.main),
            extra = CardMapper.splitIds(row.extra),
            side = CardMapper.splitIds(row.side),
        ),
        notes = row.notes,
        extended = row.extendedJson?.let { raw ->
            runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        },
    )

    /** Saves the result of importing a `.ydk` / `.ydkx` file. */
    suspend fun saveImported(id: String, name: String, document: YdkDocument): StoredDeck =
        save(id, name, document.deck, document.extended)

    suspend fun delete(id: String) = withContext(ioDispatcher) {
        // Explicitly, because the table declares no foreign key: SQLite only
        // enforces those with a pragma neither driver factory sets, so one there
        // would look like a guarantee and be decoration.
        database.deckVersionQueries.deleteForDeck(id)
        database.deckQueries.deleteById(id)
    }

    suspend fun rename(id: String, name: String) = withContext(ioDispatcher) {
        database.deckQueries.rename(name, clock(), id)
    }

    /** Serialises a saved deck back out to file text for export or sharing. */
    suspend fun exportText(id: String): String? =
        byId(id)?.let { YdkCodec.write(it.toDocument()) }

    private fun toStored(row: DeckEntity): StoredDeck {
        val extended = row.extendedJson
            ?.let { raw -> runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() }

        return StoredDeck(
            entry = DeckEntry(
                id = row.id,
                name = row.name,
                deck = Deck(
                    main = CardMapper.splitIds(row.main),
                    extra = CardMapper.splitIds(row.extra),
                    side = CardMapper.splitIds(row.side),
                ),
                createdAtEpochMs = row.createdAtEpochMs,
                updatedAtEpochMs = row.updatedAtEpochMs,
                notes = row.notes,
            ),
            extended = extended,
        )
    }
}
