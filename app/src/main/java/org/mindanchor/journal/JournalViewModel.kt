package org.mindanchor.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.research.LedgerEventKind
import org.mindanchor.research.MorningMeasure
import org.mindanchor.research.MorningMeasureRepository
import org.mindanchor.research.ResearchLedgerEvent
import org.mindanchor.research.ResearchLedgerRepository
import org.mindanchor.research.toDomain

/** The three Journal destinations (Task 6 brief): Today, Entries, Patterns. */
enum class JournalDestination { TODAY, ENTRIES, PATTERNS }

/**
 * Owns the Journal screen's UI state (selected destination, the
 * in-progress draft, the entries search query) and exposes the Task 3-5
 * repository calls as functions the composables invoke — the composables
 * themselves stay close to stateless.
 *
 * Deliberately a plain, constructor-injected class rather than an
 * [androidx.lifecycle.ViewModel]: [JournalActivity] builds one fresh in
 * `onCreate` every time, including on recreation, so "the draft survives
 * recreation" is always proven through [JournalDraftStore] — the same
 * mechanism a real process kill depends on — rather than by an incidental
 * retained `ViewModelStore` surviving a configuration change.
 */
@Suppress("LongParameterList")
class JournalViewModel(
    private val journalRepository: JournalRepository,
    private val morningMeasureRepository: MorningMeasureRepository,
    private val ledgerRepository: ResearchLedgerRepository,
    private val draftStore: JournalDraftStore,
    database: AnchorDatabase,
    private val legacyImporter: JournalLegacyImporter? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    today: () -> LocalDate = { LocalDate.now() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    /** Computed once — the whole point of Today is "today", not a clock that drifts under it. */
    val todayDate: LocalDate = today()

    val entries: Flow<List<JournalEntry>> = journalRepository.entries()
    val morningMeasureForToday: Flow<MorningMeasure?> = morningMeasureRepository.forDate(todayDate)

    /** Only what the person recorded today, and only what they recorded themselves. */
    val researchLogForToday: Flow<List<ResearchLedgerEvent>> =
        ledgerRepository.selfReportedOn(todayDate.toString())

    // Task 5 built no aggregate "history" query on MorningMeasureRepository
    // (only forDate); the underlying DAO already exposes one, and reading
    // it directly here — the same way JournalRepositoryTest reads
    // db.journal() directly — avoids widening Task 5's file for a
    // Patterns-only need.
    val morningMeasureHistory: Flow<List<MorningMeasure>> =
        database.journal().morningMeasures().map { rows -> rows.map { it.toDomain() } }

    var destination: JournalDestination by mutableStateOf(JournalDestination.TODAY)
        private set

    var title: String by mutableStateOf("")
        private set

    var body: String by mutableStateOf("")
        private set

    var searchQuery: String by mutableStateOf("")
        private set

    /** Shown once [save] has returned successfully; see the KDoc on [save]. */
    var savedConfirmation: Boolean by mutableStateOf(false)
        private set

    var saveError: Boolean by mutableStateOf(false)
        private set

    init {
        // Best-effort, fire-and-forget: a failure here must never block
        // Today from rendering. JournalLegacyImporter.importIfNeeded()
        // already no-ops after its first successful run.
        legacyImporter?.let { importer ->
            scope.launch { runCatching { importer.importIfNeeded() } }
        }
        scope.launch {
            draftStore.read()?.let { draft ->
                title = draft.title
                body = draft.body
            }
        }
    }

    fun selectDestination(destination: JournalDestination) {
        this.destination = destination
    }

    fun onTitleChange(value: String) {
        title = value
        savedConfirmation = false
        persistDraft()
    }

    fun onBodyChange(value: String) {
        body = value
        savedConfirmation = false
        persistDraft()
    }

    fun onSearchQueryChange(value: String) {
        searchQuery = value
    }

    private fun persistDraft() {
        scope.launch { draftStore.save(title, body, clock()) }
    }

    /**
     * Calls [JournalRepository.create]. On success — the suspend call
     * returned without throwing, which already means the entry is durably
     * saved (Task 3's own two-phase transaction covers that) — the draft
     * is cleared and [savedConfirmation] is shown. "Context prepared" is
     * shown regardless of whether structural-context derivation itself
     * succeeded: Task 3's contract makes that failure invisible and
     * non-blocking to the user, so gating the label on it would be
     * dishonest to that contract, not more honest.
     *
     * On failure the draft is left exactly as it was — nothing the person
     * wrote is cleared or lost.
     */
    fun save() {
        scope.launch {
            saveError = false
            runCatching {
                journalRepository.create(title, body, clock(), todayDate)
            }.onSuccess {
                draftStore.clear()
                title = ""
                body = ""
                savedConfirmation = true
            }.onFailure {
                saveError = true
            }
        }
    }

    /** Set when a research-log record could not be written; cleared on the next attempt. */
    var researchLogError: Boolean by mutableStateOf(false)
        private set

    /**
     * Records one self-reported research-log event.
     *
     * A failure is surfaced rather than swallowed. The whole point of this
     * substrate is that the record exists; quietly losing one and showing
     * nothing would be the wrong default here in a way it would not be for
     * a cosmetic action.
     */
    fun recordResearchEvent(kind: LedgerEventKind, note: String) {
        scope.launch {
            researchLogError = false
            val at = clock()
            runCatching { ledgerRepository.record(kind, occurredAt = at, note = note, now = at) }
                .onFailure { researchLogError = true }
        }
    }

    fun saveMorningMeasure(mood: Int, anxiety: Int, angerUrge: Int, energyFunction: Int, sleepQuality: Int) {
        scope.launch {
            morningMeasureRepository.save(todayDate, clock(), mood, anxiety, angerUrge, energyFunction, sleepQuality)
        }
    }
}
