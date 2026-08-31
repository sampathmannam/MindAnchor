# Support Safety-Plan Save Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SupportScreen's multi-owner safety-plan save handshake with one ViewModel reducer and one transactional Room command that reports success only after exact write/readback and commit.

**Architecture:** `RoomSafetyPlanStore` is the only production safety-plan writer. It sends one immutable command through one `@Transaction` DAO method, assigns a strictly monotonic `updatedAt` inside that transaction, writes and reads back the exact singleton row, and returns only after Room commits. `SupportViewModel` serializes `Viewing`, `Editing`, and `Saving` events on the main dispatcher, arbitrates Room publications by `updatedAt`, treats three seconds as a nonterminal slow acknowledgement, and owns the buffered one-shot `Close` effect; Compose only renders state and dispatches events.

**Tech Stack:** Kotlin 2.0.21, AndroidX Lifecycle 2.8.7, Kotlin coroutines/test 1.7.3, Jetpack Compose 1.7.6/Material 3, Room 2.6.1, JUnit 4, AndroidX instrumentation, AndroidJUnitRunner, SQLite triggers, single-thread test executors.

## Global Constraints

- Work only in `C:\Users\Sampath\Documents\Codex\2026-08-28\ac\MindAnchor-owner\.worktrees\program-0-continuity`.
- This plan covers only the SupportScreen safety-plan save redesign. Do not change crisis-contact entities, DAO methods, UI behavior, clinical copy, continuity formats, restore stages, or unrelated Program 2 work.
- Do not modify, stage, or commit `app/src/main/java/org/mindanchor/llm/LlmPrefs.kt` or root `AGENTS.md`; preserve all other pre-existing changes as user-owned.
- Do not add a Room table, schema version, durable command/outbox, `SavedStateHandle`, optimistic conflict UI, or a second persisted-plan cache.
- One `SafetyPlanStore` command is the only production safety-plan write boundary. Direct DAO insertion remains only for migrations, seeds, and test setup.
- One ViewModel reducer owns `Viewing`, `Editing`, `Saving`, the draft, actual failure, the nonterminal slow flag, queued close, operation correlation, and the one-shot close effect.
- Delete safety-plan `rememberSaveable`, `SafetyPlanDraftState`, `closeAfterSave`, `saveBlocksNavigation`, `SafetyPlanSaveState.Saved`, `consumeSaveSuccess`, CAS admission, `NonCancellable`, and terminal `withTimeout` behavior.
- The production slow threshold is exactly `3_000L` milliseconds. Crossing it means `Saving(isSlow = true)` and “Still saving…”; it never means failure, never cancels SQLite, never enables retry, and never starts a second write.
- A save succeeds only after one Room transaction writes the submitted content, reads back the exact row, and commits. Cancellation is rethrown. Trigger abort, disk/commit failure, ignored insert, or mismatching readback returns `Failed`.
- `operationId` is process-local correlation only and is never persisted. A fresh process restores no draft, in-flight operation, or queued close.
- `updatedAt` remains an epoch-millisecond timestamp and is assigned inside the transaction as `max(clockMillis, Math.addExact(current.updatedAt, 1L))`; overflow fails the command before a write.
- Room Flow remains the durable display authority. The exact committed row may update the presentation immediately, but newer `updatedAt` always wins and an older result may never regress a newer publication.
- Local singleton ordering is last-commit-wins. Backup import must call the same store so direct import and continuity restore participate in the same monotonic ordering.
- Configuration recreation and true process death are distinct: `ActivityScenario.recreate()` proves only same-ViewModel configuration retention; a destroy/fresh-launch test proves only a fresh state owner. The process-death statement is architectural: Room survives, transient memory does not.
- Test persistence with a real, file-backed `AnchorDatabase`, production schema/callback, one dedicated transaction executor, a separate query executor, a transaction executor gate, an after-commit result gate, SQLite write counter/failure triggers, and an executor drain marker. Do not substitute fake suspend persistence or sleeps.
- Acceptance requires 20 consecutive focused runs of the dedicated Support persistence class and 3 full connected runs, with zero retries, skips, or flakes.
- Use serial commands exactly as written (`--no-parallel --max-workers=1`). Stop on the first unexpected result; do not hide a failure by rerunning only the failed method.

## File Structure

- Create `app/src/main/java/org/mindanchor/support/SafetyPlanStore.kt`: command/result contract and Room implementation; no UI state.
- Modify `app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt`: one `@Transaction` safety-plan write/readback method; no schema change.
- Modify `app/src/main/java/org/mindanchor/support/SupportViewModel.kt`: sole reducer, process-local operation IDs, slow timer, Room revision arbitration, contacts passthrough, and close effect.
- Modify `app/src/main/java/org/mindanchor/support/SupportScreen.kt`: render reducer state, dispatch reducer events, and collect only the ViewModel close effect.
- Modify `app/src/main/java/org/mindanchor/support/SupportActivity.kt`: narrow overridable ViewModel-factory seam used by the real-Room activity harness.
- Delete `app/src/main/java/org/mindanchor/support/SafetyPlanDraftState.kt`: its ownership moves into the reducer.
- Modify `app/src/main/java/org/mindanchor/backup/BackupRepository.kt`: inject/use `SafetyPlanStore` and stop import immediately if its plan command fails.
- Modify `app/src/main/res/values/strings.xml`: add only `plan_still_saving`; retain the existing actual-failure wording.
- Modify `gradle/libs.versions.toml` and `app/build.gradle.kts`: add `kotlinx-coroutines-test` 1.7.3 for deterministic reducer time.
- Create `app/src/test/java/org/mindanchor/support/SupportViewModelTest.kt`: reducer/event/timer/revision/effect contract.
- Create `app/src/test/java/org/mindanchor/support/SafetyPlanArchitectureTest.kt`: forbidden-state and sole-writer source boundary.
- Delete `app/src/test/java/org/mindanchor/support/SafetyPlanSaveStateTest.kt`: terminal-delay timeout evidence is invalid for Room.
- Delete `app/src/test/java/org/mindanchor/support/SafetyPlanDraftStateTest.kt`: reducer tests replace it.
- Create `app/src/androidTest/java/org/mindanchor/support/SafetyPlanRoomHarness.kt`: file-backed Room, executors, gates, triggers, drain marker, and test Activity.
- Create `app/src/androidTest/java/org/mindanchor/support/SafetyPlanStoreRoomTest.kt`: exact transaction, monotonic timestamp, mismatch, abort, and overflow evidence.
- Replace `app/src/androidTest/java/org/mindanchor/support/SupportSafetyPlanPersistenceTest.kt`: phase-controlled UI/Room evidence.
- Modify `app/src/androidTest/java/org/mindanchor/support/SupportScreenTest.kt`: keep smoke/accessibility coverage and assert the reducer-owned saving labels only where user-visible.
- Create `app/src/androidTest/java/org/mindanchor/backup/BackupRepositoryImportTest.kt`: prove backup import uses the transactional ordering/failure boundary.
- Create `app/src/androidTest/AndroidManifest.xml`: register the androidTest-only `SupportHarnessActivity`; no production manifest change.
- Verify, but do not modify unless a compile assertion genuinely changed, `app/src/androidTest/java/org/mindanchor/data/db/MigrationTest.kt`, `app/src/androidTest/java/org/mindanchor/continuity/RestoreResumeTest.kt`, `app/src/androidTest/java/org/mindanchor/continuity/ContinuityRoundTripTest.kt`, and `app/src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt`.

---

### Task 1: Add the transactional Room command boundary

**Files:**

- Create: `app/src/main/java/org/mindanchor/support/SafetyPlanStore.kt`
- Modify: `app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt:150-173`
- Create: `app/src/androidTest/java/org/mindanchor/support/SafetyPlanRoomHarness.kt`
- Create: `app/src/androidTest/java/org/mindanchor/support/SafetyPlanStoreRoomTest.kt`

**Interfaces:**

- Consumes: `SafetyDao.plan(): Flow<SafetyPlan?>`, `SafetyDao.planNow(): SafetyPlan?`, `SafetyDao.savePlan(SafetyPlan)`, `AnchorDatabase.withResearchImmutability()`.
- Produces: `SafetyPlanStore`, `SaveSafetyPlan`, `SafetyPlanSaveResult`, `RoomSafetyPlanStore`, and `SafetyDao.savePlanTransaction(draft: SafetyPlan, clockMillis: Long): SafetyPlan` for Tasks 2-5.

- [ ] **Step 1: Capture the protected dirty baseline before any implementation edit**

Run from the repository root:

```powershell
git status --short
git diff -- app/src/main/java/org/mindanchor/llm/LlmPrefs.kt
Get-FileHash -Algorithm SHA256 -LiteralPath .\AGENTS.md
```

Expected: the status may list user-owned Program 2 changes, modified `LlmPrefs.kt`, and untracked `AGENTS.md`; record the exact output in the implementation task report. The latter two commands establish the protected byte baseline. Do not stage any existing path.

- [ ] **Step 2: Write the real-Room store tests before the interface exists**

Before creating the store test, record the two behavioral RED cases against the untouched `ec5028d` production implementation. Temporarily append these methods to the existing `SupportSafetyPlanPersistenceTest`; they use only the candidate's current helpers and real singleton Room executors:

```kotlin
@Test fun red_slowQueuedSaveDoesNotBecomeTerminalFailureAndMayCommitLate() {
    installWriteCounter()
    val (started, release) = blockTransactions()
    try {
        assertTrue(started.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        enterWarningSigns("late commit")
        invokeDoneAndBackInTheSameMainLoop()

        waitForDisplayedText("Still saving…")
        assertEquals(Lifecycle.State.RESUMED, rule.activityRule.scenario.state)
        assertEquals(0, writeCount())
        assertTrue(rule.onAllNodesWithText("That didn't save", substring = true).fetchSemanticsNodes().isEmpty())

        release.countDown()
        rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) {
            persistedWarningSigns() == "late commit" && writeCount() == 1
        }
        waitForOriginalActivityToClose()
    } finally {
        release.countDown()
    }
}

@Test fun red_newerWriterWinsWhenItCommitsBeforeOlderReadbackCompletes() = runBlocking {
    enterWarningSigns("writer A")
    val queryStarted = CountDownLatch(1)
    val releaseQuery = CountDownLatch(1)
    database.queryExecutor.execute {
        queryStarted.countDown()
        releaseQuery.await(BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }
    assertTrue(queryStarted.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
    try {
        tapDone()
        rule.waitUntil(timeoutMillis = TEST_TIMEOUT_MILLIS) {
            persistedWarningSigns() == "writer A"
        }
        database.safety().savePlan(
            SafetyPlan(warningSigns = "writer B", updatedAt = System.currentTimeMillis() + 1L),
        )
    } finally {
        releaseQuery.countDown()
    }
    waitForDisplayedText("writer B")
    assertTrue(rule.onAllNodesWithText("That didn't save", substring = true).fetchSemanticsNodes().isEmpty())
}
```

Run each temporary method serially:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.support.SupportSafetyPlanPersistenceTest#red_slowQueuedSaveDoesNotBecomeTerminalFailureAndMayCommitLate" --no-parallel --max-workers=1
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.support.SupportSafetyPlanPersistenceTest#red_newerWriterWinsWhenItCommitsBeforeOlderReadbackCompletes" --no-parallel --max-workers=1
```

Expected: both FAIL against `ec5028d`. The first cannot display nonterminal “Still saving…” and the old terminal timeout prevents the required late-success contract. The second reaches the old mismatch/failure path or retains writer A's draft instead of presenting writer B. Save the two complete console outputs as RED evidence outside the repository, then remove exactly these two temporary methods with `apply_patch`; verify `git diff -- app/src/androidTest/java/org/mindanchor/support/SupportSafetyPlanPersistenceTest.kt` is empty before continuing. Do not use `git restore` because the implementation worktree may contain user-owned changes.

Now create the permanent store tests.

Create `SafetyPlanStoreRoomTest.kt` with these five exact tests and no fake persistence:

```kotlin
@RunWith(AndroidJUnit4::class)
class SafetyPlanStoreRoomTest {
    private lateinit var room: SafetyPlanRoomHarness

    @Before fun setUp() {
        room = SafetyPlanRoomHarness(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() = room.close()

    @Test fun saveReturnsTheExactCommittedRowWithAMonotonicStamp() = runBlocking {
        room.dao.savePlan(SafetyPlan(warningSigns = "old", updatedAt = 250L))
        val result = RoomSafetyPlanStore(room.dao) { 100L }.save(
            SaveSafetyPlan(7L, SafetyPlan(warningSigns = "new")),
        )
        val committed = result as SafetyPlanSaveResult.Committed
        assertEquals(7L, committed.operationId)
        assertEquals(251L, committed.stored.updatedAt)
        assertEquals("new", committed.stored.warningSigns)
        assertEquals(committed.stored, room.dao.planNow())
    }

    @Test fun absentRowUsesTheClockMillisStamp() = runBlocking {
        val result = RoomSafetyPlanStore(room.dao) { 500L }.save(
            SaveSafetyPlan(1L, SafetyPlan(copingSteps = "walk")),
        ) as SafetyPlanSaveResult.Committed
        assertEquals(500L, result.stored.updatedAt)
    }

    @Test fun ignoredInsertProducesFailedAndLeavesThePriorRow() = runBlocking {
        val prior = SafetyPlan(warningSigns = "prior", updatedAt = 10L)
        room.dao.savePlan(prior)
        room.installIgnoreInsertTrigger()
        val result = RoomSafetyPlanStore(room.dao) { 20L }.save(
            SaveSafetyPlan(2L, SafetyPlan(warningSigns = "ignored")),
        )
        assertTrue(result is SafetyPlanSaveResult.Failed)
        assertEquals(prior, room.dao.planNow())
    }

    @Test fun abortTriggerProducesFailedAndNoLateWrite() = runBlocking {
        room.installWriteCounter()
        room.installAbortInsertTrigger()
        val result = RoomSafetyPlanStore(room.dao) { 20L }.save(
            SaveSafetyPlan(3L, SafetyPlan(warningSigns = "abort")),
        )
        assertTrue(result is SafetyPlanSaveResult.Failed)
        room.drainTransactions()
        assertEquals(0, room.writeCount())
        assertNull(room.dao.planNow())
    }

    @Test fun timestampOverflowFailsBeforeWriting() = runBlocking {
        room.dao.savePlan(SafetyPlan(warningSigns = "max", updatedAt = Long.MAX_VALUE))
        room.installWriteCounter()
        val result = RoomSafetyPlanStore(room.dao) { Long.MAX_VALUE }.save(
            SaveSafetyPlan(4L, SafetyPlan(warningSigns = "must not replace")),
        )
        assertTrue(result is SafetyPlanSaveResult.Failed)
        assertEquals(0, room.writeCount())
        assertEquals("max", room.dao.planNow()?.warningSigns)
    }
}
```

Create the first version of `SafetyPlanRoomHarness.kt` with the production schema and distinct executors:

```kotlin
internal class SafetyPlanRoomHarness(context: Context) : AutoCloseable {
    private val databaseName = "support-plan-${UUID.randomUUID()}.db"
    private val appContext = context.applicationContext
    internal val transactionExecutor = Executors.newSingleThreadExecutor()
    internal val queryExecutor = Executors.newSingleThreadExecutor()
    internal val database = Room.databaseBuilder(appContext, AnchorDatabase::class.java, databaseName)
        .setTransactionExecutor(transactionExecutor)
        .setQueryExecutor(queryExecutor)
        .withResearchImmutability()
        .build()
    internal val dao = database.safety()

    fun installWriteCounter() {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE support_test_safety_writes (count INTEGER NOT NULL)")
        sql.execSQL("INSERT INTO support_test_safety_writes VALUES (0)")
        sql.execSQL(
            "CREATE TRIGGER support_test_count_safety_plan AFTER INSERT ON safety_plan " +
                "BEGIN UPDATE support_test_safety_writes SET count = count + 1; END",
        )
    }

    fun installAbortInsertTrigger() = database.openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER support_test_abort_safety_plan BEFORE INSERT ON safety_plan " +
            "BEGIN SELECT RAISE(ABORT, 'support test abort'); END",
    )

    fun installIgnoreInsertTrigger() = database.openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER support_test_ignore_safety_plan BEFORE INSERT ON safety_plan " +
            "BEGIN SELECT RAISE(IGNORE); END",
    )

    fun writeCount(): Int = database.openHelper.readableDatabase
        .query("SELECT count FROM support_test_safety_writes")
        .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    fun drainTransactions() {
        val drained = CountDownLatch(1)
        transactionExecutor.execute(drained::countDown)
        check(drained.await(10, TimeUnit.SECONDS)) { "transaction executor did not drain" }
    }

    override fun close() {
        database.close()
        transactionExecutor.shutdownNow()
        queryExecutor.shutdownNow()
        appContext.deleteDatabase(databaseName)
    }
}
```

- [ ] **Step 3: Run the store test to verify RED**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.support.SafetyPlanStoreRoomTest" --no-parallel --max-workers=1
```

Expected: FAIL at `compileDebugAndroidTestKotlin` with unresolved references to `RoomSafetyPlanStore`, `SaveSafetyPlan`, and `SafetyPlanSaveResult`. This is the expected RED against `ec5028d`; no test may be disabled or annotated ignored.

- [ ] **Step 4: Add the one DAO transaction**

Add `androidx.room.Transaction`, change `SafetyDao` from an interface to an abstract DAO class so Room can wrap a concrete transaction body, and retain the contact declarations as abstract methods with identical annotations/signatures. Replace the plan portion with:

```kotlin
@Dao
abstract class SafetyDao {
@Query("SELECT * FROM safety_plan WHERE id = ${SafetyPlan.SINGLETON_ID}")
abstract fun plan(): Flow<SafetyPlan?>

@Query("SELECT * FROM safety_plan WHERE id = ${SafetyPlan.SINGLETON_ID}")
abstract suspend fun planNow(): SafetyPlan?

@Insert(onConflict = OnConflictStrategy.REPLACE)
abstract suspend fun savePlan(plan: SafetyPlan)

@Transaction
open suspend fun savePlanTransaction(draft: SafetyPlan, clockMillis: Long): SafetyPlan {
    val current = planNow()
    val nextUpdatedAt = current?.let {
        maxOf(clockMillis, Math.addExact(it.updatedAt, 1L))
    } ?: clockMillis
    val written = draft.copy(
        id = SafetyPlan.SINGLETON_ID,
        updatedAt = nextUpdatedAt,
    )
    savePlan(written)
    val stored = checkNotNull(planNow()) { "safety plan row missing after insert" }
    check(stored == written) { "safety plan readback did not match the written row" }
    return stored
}
@Query("SELECT * FROM crisis_contacts ORDER BY isProfessional, name")
abstract fun contacts(): Flow<List<CrisisContact>>

@Query("SELECT * FROM crisis_contacts")
abstract suspend fun contactsNow(): List<CrisisContact>

@Insert(onConflict = OnConflictStrategy.REPLACE)
abstract suspend fun addContact(contact: CrisisContact)

@Delete
abstract suspend fun removeContact(contact: CrisisContact)
}
```

The `maxOf` and exact readback execute inside Room's transaction. `Math.addExact` must remain; do not replace it with `+ 1`.

- [ ] **Step 5: Implement the store contract exactly once**

Create `SafetyPlanStore.kt`:

```kotlin
package org.mindanchor.support

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.data.db.SafetyDao
import org.mindanchor.data.db.SafetyPlan

internal interface SafetyPlanStore {
    val plans: Flow<SafetyPlan>
    suspend fun save(command: SaveSafetyPlan): SafetyPlanSaveResult
}

internal data class SaveSafetyPlan(
    val operationId: Long,
    val draft: SafetyPlan,
)

internal sealed interface SafetyPlanSaveResult {
    data class Committed(
        val operationId: Long,
        val stored: SafetyPlan,
    ) : SafetyPlanSaveResult

    data class Failed(
        val operationId: Long,
        val cause: Throwable,
    ) : SafetyPlanSaveResult
}

internal class RoomSafetyPlanStore(
    private val dao: SafetyDao,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : SafetyPlanStore {
    override val plans: Flow<SafetyPlan> = dao.plan().map { it ?: SafetyPlan() }

    override suspend fun save(command: SaveSafetyPlan): SafetyPlanSaveResult = try {
        SafetyPlanSaveResult.Committed(
            operationId = command.operationId,
            stored = dao.savePlanTransaction(command.draft, clockMillis()),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (cause: Throwable) {
        SafetyPlanSaveResult.Failed(command.operationId, cause)
    }
}
```

- [ ] **Step 6: Run the store test to verify GREEN and check the schema stayed fixed**

Run serially:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.support.SafetyPlanStoreRoomTest" --no-parallel --max-workers=1
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.data.db.MigrationTest" --no-parallel --max-workers=1
git diff -- app/schemas
```

Expected: both commands end `BUILD SUCCESSFUL`; all five store tests pass; `MigrationTest` passes; `git diff -- app/schemas` prints nothing because the DAO behavior does not change the Room schema.

- [ ] **Step 7: Commit only the transactional boundary**

```powershell
git add app/src/main/java/org/mindanchor/support/SafetyPlanStore.kt app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt app/src/androidTest/java/org/mindanchor/support/SafetyPlanRoomHarness.kt app/src/androidTest/java/org/mindanchor/support/SafetyPlanStoreRoomTest.kt
git diff --cached --check
git commit -m "feat: add transactional safety plan store"
```

Expected: one commit containing only the four listed files. `LlmPrefs.kt`, root `AGENTS.md`, and unrelated dirty paths remain unstaged.

---

### Task 2: Replace save coordination with one ViewModel reducer

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/org/mindanchor/support/SupportViewModelTest.kt`
- Modify: `app/src/main/java/org/mindanchor/support/SupportViewModel.kt`

**Interfaces:**

- Consumes: `SafetyPlanStore.plans`, `SafetyPlanStore.save(SaveSafetyPlan)`, existing `SafetyDao` contact methods.
- Produces: `SafetyPlanUiState`, `SafetyPlanUiError`, `SupportEvent`, `SupportEffect`, `SupportViewModel.uiState`, `SupportViewModel.effects`, and `SupportViewModel.onEvent(event)` for Task 3.

- [ ] **Step 1: Add deterministic coroutine-test support**

Add to `[versions]` and `[libraries]` in `gradle/libs.versions.toml`:

```toml
coroutines = "1.7.3"
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

Add beside the other `testImplementation` entries in `app/build.gradle.kts`:

```kotlin
testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 2: Write reducer tests before replacing the old ViewModel**

Create `SupportViewModelTest.kt`. Declare `private val dispatcher = StandardTestDispatcher()`, call `Dispatchers.setMain(dispatcher)` in `@Before`, call `Dispatchers.resetMain()` in `@After`, and invoke every coroutine test as `runTest(dispatcher)`. Use a `MutableStateFlow<SafetyPlan>` fake store and a `FakeSafetyDao : SafetyDao()` whose contact methods use a separate `MutableStateFlow<List<CrisisContact>>` and whose plan methods are inert because the injected fake store owns plan tests.

The test file must contain these exact behavioral tests:

```kotlin
@Test fun editAndDraftChangedDoNotWrite() = runTest(dispatcher) {
    val fixture = fixture()
    fixture.start()
    fixture.vm.onEvent(SupportEvent.Edit)
    fixture.vm.onEvent(SupportEvent.DraftChanged(SafetyPlan(warningSigns = "cannot sleep")))
    assertEquals(0, fixture.store.saveCalls)
    assertEquals("cannot sleep", (fixture.vm.uiState.value as SafetyPlanUiState.Editing).draft.warningSigns)
}

@Test fun doneMovesToSavingSynchronouslyAndRejectsEveryDuplicate() = runTest(dispatcher) {
    val fixture = fixture()
    fixture.startEditing("call Maya")
    fixture.vm.onEvent(SupportEvent.Done)
    val saving = fixture.vm.uiState.value as SafetyPlanUiState.Saving
    fixture.vm.onEvent(SupportEvent.Done)
    fixture.vm.onEvent(SupportEvent.Done)
    runCurrent()
    assertEquals(1, fixture.store.saveCalls)
    assertEquals(1L, saving.command.operationId)
}

@Test fun threeSecondsOnlyMarksTheSameOperationSlow() = runTest(dispatcher) {
    val fixture = fixture(slowThresholdMillis = 3_000L)
    fixture.startEditing("walk")
    fixture.vm.onEvent(SupportEvent.Done)
    advanceTimeBy(2_999L)
    assertFalse((fixture.vm.uiState.value as SafetyPlanUiState.Saving).isSlow)
    advanceTimeBy(1L)
    assertTrue((fixture.vm.uiState.value as SafetyPlanUiState.Saving).isSlow)
    assertEquals(1, fixture.store.saveCalls)
}

@Test fun matchingCommitReturnsDirectlyToViewing() = runTest(dispatcher) {
    val fixture = fixture()
    fixture.startEditing("walk")
    fixture.vm.onEvent(SupportEvent.Done)
    runCurrent()
    fixture.store.completeCommitted(updatedAt = 10L)
    advanceUntilIdle()
    assertTrue(fixture.vm.uiState.value is SafetyPlanUiState.Viewing)
    assertEquals("walk", fixture.vm.uiState.value.persisted.warningSigns)
}

@Test fun matchingFailureRetainsDraftCancelsQueuedCloseAndAllowsOneRetry() = runTest(dispatcher) {
    val fixture = fixture()
    fixture.startEditing("stay with Priya")
    fixture.vm.onEvent(SupportEvent.Done)
    fixture.vm.onEvent(SupportEvent.Back)
    runCurrent()
    fixture.store.completeFailed()
    advanceUntilIdle()
    val editing = fixture.vm.uiState.value as SafetyPlanUiState.Editing
    assertEquals("stay with Priya", editing.draft.warningSigns)
    assertEquals(SafetyPlanUiError.SaveFailed, editing.error)
    assertNull(fixture.effects.tryReceive().getOrNull())
    fixture.vm.onEvent(SupportEvent.Done)
    runCurrent()
    assertEquals(2, fixture.store.saveCalls)
}

@Test fun repeatedBackWhileSavingEmitsExactlyOneCloseAfterCommit() = runTest(dispatcher) {
    val fixture = fixture()
    fixture.startEditing("call Maya")
    fixture.vm.onEvent(SupportEvent.Done)
    fixture.vm.onEvent(SupportEvent.Back)
    fixture.vm.onEvent(SupportEvent.Back)
    runCurrent()
    fixture.store.completeCommitted(updatedAt = 20L)
    advanceUntilIdle()
    assertEquals(SupportEffect.Close, fixture.effects.receive())
    assertNull(fixture.effects.tryReceive().getOrNull())
}

@Test fun newerRoomPublicationBeatsAnOlderMatchingResult() = runTest(dispatcher) {
    val fixture = fixture()
    fixture.startEditing("writer A")
    fixture.vm.onEvent(SupportEvent.Done)
    runCurrent()
    fixture.store.publish(SafetyPlan(warningSigns = "writer B", updatedAt = 12L))
    runCurrent()
    fixture.store.completeCommitted(updatedAt = 11L)
    advanceUntilIdle()
    assertEquals("writer B", fixture.vm.uiState.value.persisted.warningSigns)
}

@Test fun staleResultCannotCompleteTheCurrentOperation() = runTest(dispatcher) {
    val fixture = fixture()
    fixture.startEditing("first")
    fixture.vm.onEvent(SupportEvent.Done)
    runCurrent()
    fixture.store.completeFailed()
    advanceUntilIdle()
    fixture.vm.onEvent(SupportEvent.Done)
    val current = fixture.vm.uiState.value as SafetyPlanUiState.Saving
    fixture.vm.acceptSaveResult(
        SafetyPlanSaveResult.Committed(1L, SafetyPlan(warningSigns = "stale", updatedAt = 99L)),
    )
    assertEquals(current, fixture.vm.uiState.value)
}

@Test fun productionSlowThresholdIsExactlyThreeSeconds() {
    assertEquals(3_000L, SupportViewModel.SLOW_THRESHOLD_MILLIS)
}
```

The fake store's `save` must suspend on one `CompletableDeferred` per call, record immutable commands, and expose its effect channel with `produceIn(this)`; do not use real time or `Thread.sleep`.

Use these concrete fakes so the test never falls through to Room or Android storage:

```kotlin
private class FakeSafetyPlanStore : SafetyPlanStore {
    private val published = MutableStateFlow(SafetyPlan())
    private val pending = ArrayDeque<Pair<SaveSafetyPlan, CompletableDeferred<SafetyPlanSaveResult>>>()
    override val plans: Flow<SafetyPlan> = published
    var saveCalls = 0
        private set

    override suspend fun save(command: SaveSafetyPlan): SafetyPlanSaveResult {
        saveCalls += 1
        val result = CompletableDeferred<SafetyPlanSaveResult>()
        pending.addLast(command to result)
        return result.await()
    }

    fun publish(plan: SafetyPlan) { published.value = plan }

    fun completeCommitted(updatedAt: Long) {
        val (command, result) = pending.removeFirst()
        result.complete(
            SafetyPlanSaveResult.Committed(
                command.operationId,
                command.draft.copy(updatedAt = updatedAt),
            ),
        )
    }

    fun completeFailed() {
        val (command, result) = pending.removeFirst()
        result.complete(SafetyPlanSaveResult.Failed(command.operationId, IOException("write failed")))
    }
}

private class FakeSafetyDao : SafetyDao() {
    private val contactRows = MutableStateFlow<List<CrisisContact>>(emptyList())
    override fun plan(): Flow<SafetyPlan?> = flowOf(null)
    override suspend fun planNow(): SafetyPlan? = null
    override suspend fun savePlan(plan: SafetyPlan) = error("plan writes must use FakeSafetyPlanStore")
    override fun contacts(): Flow<List<CrisisContact>> = contactRows
    override suspend fun contactsNow(): List<CrisisContact> = contactRows.value
    override suspend fun addContact(contact: CrisisContact) {
        contactRows.value = contactRows.value + contact
    }
    override suspend fun removeContact(contact: CrisisContact) {
        contactRows.value = contactRows.value - contact
    }
}
```

- [ ] **Step 3: Run the reducer test to verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.support.SupportViewModelTest" --no-parallel --max-workers=1
```

Expected: FAIL at Kotlin compilation because `SafetyPlanUiState`, `SupportEvent`, `SupportEffect`, `uiState`, and `acceptSaveResult` do not exist. The old CAS/`Saved` state must not be adapted to make these tests pass.

- [ ] **Step 4: Define the reducer-owned state, events, and visible-plan rule**

Replace the old `SafetyPlanSaveState`, `canStartSave`, and `saveAndVerifySafetyPlan` declarations with:

```kotlin
internal enum class SafetyPlanUiError { SaveFailed }

internal sealed interface SafetyPlanUiState {
    val persisted: SafetyPlan

    data class Viewing(override val persisted: SafetyPlan) : SafetyPlanUiState

    data class Editing(
        override val persisted: SafetyPlan,
        val draft: SafetyPlan,
        val error: SafetyPlanUiError? = null,
    ) : SafetyPlanUiState

    data class Saving(
        override val persisted: SafetyPlan,
        val command: SaveSafetyPlan,
        val closeRequested: Boolean = false,
        val isSlow: Boolean = false,
    ) : SafetyPlanUiState
}

internal sealed interface SupportEvent {
    data object Edit : SupportEvent
    data class DraftChanged(val draft: SafetyPlan) : SupportEvent
    data object Done : SupportEvent
    data object Back : SupportEvent
}

internal sealed interface SupportEffect {
    data object Close : SupportEffect
}

internal val SafetyPlanUiState.visiblePlan: SafetyPlan
    get() = when (this) {
        is SafetyPlanUiState.Viewing -> persisted
        is SafetyPlanUiState.Editing -> draft
        is SafetyPlanUiState.Saving ->
            if (persisted.updatedAt > command.draft.updatedAt) persisted else command.draft
    }
```

The `Saving` visibility rule keeps the immutable submitted draft on screen before commit, then allows committed/newer Flow rows to become visible while result delivery is gated.

- [ ] **Step 5: Implement synchronous event admission and asynchronous effects**

Give `SupportViewModel` an internal injected constructor plus the existing public Android constructor:

```kotlin
class SupportViewModel internal constructor(
    application: Application,
    private val store: SafetyPlanStore,
    private val dao: SafetyDao,
    private val slowThresholdMillis: Long,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        store = RoomSafetyPlanStore(AnchorDatabase.get(application).safety()),
        dao = AnchorDatabase.get(application).safety(),
        slowThresholdMillis = SLOW_THRESHOLD_MILLIS,
    )

    private val _uiState = MutableStateFlow<SafetyPlanUiState>(
        SafetyPlanUiState.Viewing(SafetyPlan(updatedAt = UNLOADED_UPDATED_AT)),
    )
    internal val uiState = _uiState.asStateFlow()

    private val effectChannel = Channel<SupportEffect>(Channel.BUFFERED)
    internal val effects = effectChannel.receiveAsFlow()

    private var lastOperationId = 0L
    private var slowJob: Job? = null

    val contacts = dao.contacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            store.plans.collect(::acceptPublishedPlan)
        }
    }

    fun onEvent(event: SupportEvent) {
        when (event) {
            SupportEvent.Edit -> startEditing()
            is SupportEvent.DraftChanged -> changeDraft(event.draft)
            SupportEvent.Done -> startSave()
            SupportEvent.Back -> requestClose()
        }
    }

    private fun startEditing() {
        val state = _uiState.value as? SafetyPlanUiState.Viewing ?: return
        val draft = if (state.persisted.updatedAt == UNLOADED_UPDATED_AT) {
            SafetyPlan()
        } else {
            state.persisted
        }
        _uiState.value = SafetyPlanUiState.Editing(state.persisted, draft)
    }

    private fun changeDraft(draft: SafetyPlan) {
        val state = _uiState.value as? SafetyPlanUiState.Editing ?: return
        _uiState.value = state.copy(draft = draft, error = null)
    }

    private fun startSave() {
        val state = _uiState.value as? SafetyPlanUiState.Editing ?: return
        val command = SaveSafetyPlan(Math.addExact(lastOperationId, 1L), state.draft)
        lastOperationId = command.operationId
        _uiState.value = SafetyPlanUiState.Saving(state.persisted, command)
        slowJob?.cancel()
        slowJob = viewModelScope.launch {
            delay(slowThresholdMillis)
            val current = _uiState.value as? SafetyPlanUiState.Saving ?: return@launch
            if (current.command.operationId == command.operationId) {
                _uiState.value = current.copy(isSlow = true)
            }
        }
        viewModelScope.launch {
            acceptSaveResult(store.save(command))
        }
    }

    private fun requestClose() {
        when (val state = _uiState.value) {
            is SafetyPlanUiState.Viewing -> effectChannel.trySend(SupportEffect.Close)
            is SafetyPlanUiState.Editing -> {
                _uiState.value = SafetyPlanUiState.Viewing(state.persisted)
                effectChannel.trySend(SupportEffect.Close)
            }
            is SafetyPlanUiState.Saving -> _uiState.value = state.copy(closeRequested = true)
        }
    }
```

Keep the existing `addContact` and `removeContact` method bodies byte-for-byte below this reducer.

- [ ] **Step 6: Implement revision arbitration and matching-result reduction**

Complete the ViewModel with:

```kotlin
    private fun acceptPublishedPlan(candidate: SafetyPlan) {
        val state = _uiState.value
        if (candidate.updatedAt <= state.persisted.updatedAt) return
        _uiState.value = when (state) {
            is SafetyPlanUiState.Viewing -> state.copy(persisted = candidate)
            is SafetyPlanUiState.Editing -> state.copy(persisted = candidate)
            is SafetyPlanUiState.Saving -> state.copy(persisted = candidate)
        }
    }

    internal fun acceptSaveResult(result: SafetyPlanSaveResult) {
        val saving = _uiState.value as? SafetyPlanUiState.Saving ?: return
        val resultOperationId = when (result) {
            is SafetyPlanSaveResult.Committed -> result.operationId
            is SafetyPlanSaveResult.Failed -> result.operationId
        }
        if (resultOperationId != saving.command.operationId) return

        slowJob?.cancel()
        slowJob = null
        when (result) {
            is SafetyPlanSaveResult.Committed -> {
                val newest = if (result.stored.updatedAt > saving.persisted.updatedAt) {
                    result.stored
                } else {
                    saving.persisted
                }
                _uiState.value = SafetyPlanUiState.Viewing(newest)
                if (saving.closeRequested) effectChannel.trySend(SupportEffect.Close)
            }
            is SafetyPlanSaveResult.Failed -> {
                _uiState.value = SafetyPlanUiState.Editing(
                    persisted = saving.persisted,
                    draft = saving.command.draft,
                    error = SafetyPlanUiError.SaveFailed,
                )
            }
        }
    }

    internal companion object {
        const val SLOW_THRESHOLD_MILLIS = 3_000L
        private const val UNLOADED_UPDATED_AT = Long.MIN_VALUE
    }
}
```

`UNLOADED_UPDATED_AT` is presentation-only and is never written. It guarantees that the store's first emission—including an empty/migrated row stamped `0L`—is accepted by the strict newer-revision rule.

Do not add `CoroutineStart.UNDISPATCHED`, CAS, `withTimeout`, `NonCancellable`, a `Saved` state, or a success-consumption method. The `_uiState.value = Saving(...)` assignment itself is the synchronous admission boundary.

- [ ] **Step 7: Run reducer tests to verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.support.SupportViewModelTest" --no-parallel --max-workers=1
```

Expected: `BUILD SUCCESSFUL`; all nine reducer tests pass with virtual time; no Android device or real delay is used.

- [ ] **Step 8: Commit only the reducer and its test dependency**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/org/mindanchor/support/SupportViewModel.kt app/src/test/java/org/mindanchor/support/SupportViewModelTest.kt
git diff --cached --check
git commit -m "feat: centralize support safety plan state"
```

Expected: one commit with those four files only.

---

### Task 3: Make Compose a pure renderer and the ViewModel the navigation owner

**Files:**

- Create: `app/src/test/java/org/mindanchor/support/SafetyPlanArchitectureTest.kt`
- Modify: `app/src/main/java/org/mindanchor/support/SupportScreen.kt:75-330`
- Modify: `app/src/main/java/org/mindanchor/support/SupportActivity.kt`
- Delete: `app/src/main/java/org/mindanchor/support/SafetyPlanDraftState.kt`
- Delete: `app/src/test/java/org/mindanchor/support/SafetyPlanDraftStateTest.kt`
- Delete: `app/src/test/java/org/mindanchor/support/SafetyPlanSaveStateTest.kt`
- Modify: `app/src/main/res/values/strings.xml:705-706`
- Modify: `app/src/androidTest/java/org/mindanchor/support/SupportScreenTest.kt`

**Interfaces:**

- Consumes: `SupportViewModel.uiState`, `contacts`, `effects`, `onEvent`; `SafetyPlanUiState.visiblePlan`.
- Produces: a route that collects only `SupportEffect.Close`, and a protected `SupportActivity.supportViewModelFactory()` seam used by Task 4.

- [ ] **Step 1: Write the source-boundary test before deleting the old owners**

Create `SafetyPlanArchitectureTest.kt`:

```kotlin
class SafetyPlanArchitectureTest {
    private fun source(path: String) = File(path).readText(Charsets.UTF_8)

    @Test fun composeOwnsNoSafetyPlanSaveOrDraftState() {
        val screen = source("src/main/java/org/mindanchor/support/SupportScreen.kt")
        listOf("rememberSaveable", "closeAfterSave", "SafetyPlanDraftState", "consumeSaveSuccess")
            .forEach { forbidden -> assertFalse("found $forbidden", forbidden in screen) }
    }

    @Test fun viewModelContainsNoTerminalTimeoutOrSavedHandshake() {
        val viewModel = source("src/main/java/org/mindanchor/support/SupportViewModel.kt")
        listOf("withTimeout", "NonCancellable", "compareAndSet", "SafetyPlanSaveState", "Saved")
            .forEach { forbidden -> assertFalse("found $forbidden", forbidden in viewModel) }
    }

}
```

- [ ] **Step 2: Run the boundary test to verify RED against the current screen**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.support.SafetyPlanArchitectureTest" --no-parallel --max-workers=1
```

Expected: FAIL. The two tests report the current `rememberSaveable`, `closeAfterSave`, old draft/save types, CAS, `NonCancellable`, and timeout.

- [ ] **Step 3: Add the nonterminal slow string only**

Keep the current strings and add:

```xml
<string name="plan_saving">Saving…</string>
<string name="plan_still_saving">Still saving…</string>
<string name="plan_save_failed">That didn\'t save. Your plan is still here — try again.</string>
```

The failure string is rendered only for `Editing(error = SaveFailed)`. The slow string must never share the failure live region.

- [ ] **Step 4: Replace Compose-owned plan state with reducer rendering**

Add `androidx.compose.runtime.rememberUpdatedState` and remove the obsolete `rememberSaveable` import. Then, at the start of `SupportScreen`, replace `plan`, `saveState`, `SafetyPlanDraftState`, `closeAfterSave`, `requestClose`, and `LaunchedEffect(saveState)` with:

```kotlin
val context = LocalContext.current
val uiState by viewModel.uiState.collectAsState()
val contacts by viewModel.contacts.collectAsState()
var dialFailure by remember { mutableStateOf<String?>(null) }
val currentOnClose by rememberUpdatedState(onClose)

LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
        if (effect == SupportEffect.Close) currentOnClose()
    }
}

BackHandler { viewModel.onEvent(SupportEvent.Back) }
```

Replace the toolbar Back callback with:

```kotlin
TextButton(onClick = { viewModel.onEvent(SupportEvent.Back) }) {
    Text(stringResource(R.string.action_back))
}
```

Replace the plan action with:

```kotlin
val saving = uiState as? SafetyPlanUiState.Saving
TextButton(
    onClick = {
        viewModel.onEvent(
            if (uiState is SafetyPlanUiState.Viewing) SupportEvent.Edit else SupportEvent.Done,
        )
    },
    enabled = saving == null,
) {
    Text(
        stringResource(
            when {
                saving?.isSlow == true -> R.string.plan_still_saving
                saving != null -> R.string.plan_saving
                uiState is SafetyPlanUiState.Editing -> R.string.action_done
                else -> R.string.action_edit
            },
        ),
    )
}
```

Replace the error and reader/editor selection with:

```kotlin
val editing = uiState as? SafetyPlanUiState.Editing
if (editing?.error == SafetyPlanUiError.SaveFailed) {
    Text(
        text = stringResource(R.string.plan_save_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

when (uiState) {
    is SafetyPlanUiState.Viewing -> SafetyPlanReader(uiState.visiblePlan)
    is SafetyPlanUiState.Editing,
    is SafetyPlanUiState.Saving,
    -> SafetyPlanEditor(
        plan = uiState.visiblePlan,
        onChange = { viewModel.onEvent(SupportEvent.DraftChanged(it)) },
        planFieldsEnabled = uiState is SafetyPlanUiState.Editing,
        contacts = contacts,
        onAddContact = viewModel::addContact,
        onRemoveContact = viewModel::removeContact,
    )
}
```

Leave dial failure's ordinary `remember`, the contact form's local `name`/`phone`/`professional` state, crisis content, skills, headings, typography, and contact DAO behavior unchanged.

- [ ] **Step 5: Add the narrow Activity factory seam and no navigation state**

Make `SupportActivity` open and add:

```kotlin
protected open fun supportViewModelFactory(): ViewModelProvider.Factory =
    defaultViewModelProviderFactory

protected open fun closeSupport() = finish()
```

Resolve the ViewModel once inside `setContent` and pass it to the route:

```kotlin
val factory = supportViewModelFactory()
setContent {
    MindAnchorTheme {
        val supportViewModel: SupportViewModel = viewModel(factory = factory)
        SupportScreen(onClose = ::closeSupport, viewModel = supportViewModel)
    }
}
```

Do not collect effects in both Activity and screen. `SupportScreen` is the sole route collector; `SupportActivity` only supplies `finish()`.

- [ ] **Step 6: Delete the obsolete state owners and update smoke assertions**

Delete `SafetyPlanDraftState.kt`, `SafetyPlanDraftStateTest.kt`, and `SafetyPlanSaveStateTest.kt`. Keep `SupportScreenTest`'s existing five tests, but make `aSafetyPlanCanBeWrittenAndReadBack` wait for the persisted reader text rather than only `waitForIdle`; add assertions that “Saving…” disables the action during a gated test in Task 4, not in this smoke class.

- [ ] **Step 7: Run the focused UI and architecture tests**

Run serially:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.support.SupportViewModelTest" --tests "org.mindanchor.support.SafetyPlanArchitectureTest" --no-parallel --max-workers=1
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.support.SupportScreenTest" --no-parallel --max-workers=1
```

Expected: the reducer tests, both architecture tests, and all five SupportScreen tests PASS.

- [ ] **Step 8: Commit the UI ownership change without the known backup writer**

```powershell
git add app/src/main/java/org/mindanchor/support/SupportScreen.kt app/src/main/java/org/mindanchor/support/SupportActivity.kt app/src/main/res/values/strings.xml app/src/androidTest/java/org/mindanchor/support/SupportScreenTest.kt app/src/test/java/org/mindanchor/support/SafetyPlanArchitectureTest.kt
git add -u app/src/main/java/org/mindanchor/support/SafetyPlanDraftState.kt app/src/test/java/org/mindanchor/support/SafetyPlanDraftStateTest.kt app/src/test/java/org/mindanchor/support/SafetyPlanSaveStateTest.kt
git diff --cached --check
git commit -m "refactor: render safety plan from view model state"
```

Expected: one focused commit whose focused tests are green.

---

### Task 4: Prove the lifecycle and race contracts with phase-controlled real Room

**Files:**

- Modify: `app/src/androidTest/java/org/mindanchor/support/SafetyPlanRoomHarness.kt`
- Replace: `app/src/androidTest/java/org/mindanchor/support/SupportSafetyPlanPersistenceTest.kt`
- Create: `app/src/androidTest/AndroidManifest.xml`

**Interfaces:**

- Consumes: `SupportActivity.supportViewModelFactory()`, the injected `SupportViewModel` constructor, `SafetyPlanStore`, `RoomSafetyPlanStore`, and `SafetyPlanRoomHarness` from Tasks 1-3.
- Produces: `TransactionGate`, `AfterCommitResultGate`, `SupportHarnessActivity`, executor drain, captured Compose click helpers, and ten deterministic instrumentation contracts.

- [ ] **Step 1: Extend the harness with explicit phase controls**

Add these concrete helpers to `SafetyPlanRoomHarness.kt`:

```kotlin
internal data class TransactionGate(
    val started: CountDownLatch,
    val release: CountDownLatch,
)

internal fun SafetyPlanRoomHarness.gateTransactionExecutor(): TransactionGate {
    val gate = TransactionGate(CountDownLatch(1), CountDownLatch(1))
    transactionExecutor.execute {
        gate.started.countDown()
        check(gate.release.await(15, TimeUnit.SECONDS)) { "transaction gate timed out" }
    }
    check(gate.started.await(10, TimeUnit.SECONDS)) { "transaction gate never started" }
    return gate
}

internal class AfterCommitResultGate(
    private val delegate: SafetyPlanStore,
) : SafetyPlanStore {
    override val plans = delegate.plans
    val calls = AtomicInteger(0)
    private val armed = AtomicBoolean(false)
    private var committed = CompletableDeferred<SafetyPlanSaveResult.Committed>()
    private var release = CountDownLatch(0)

    fun arm() {
        check(armed.compareAndSet(false, true)) { "result gate already armed" }
        committed = CompletableDeferred()
        release = CountDownLatch(1)
    }

    override suspend fun save(command: SaveSafetyPlan): SafetyPlanSaveResult {
        calls.incrementAndGet()
        val result = delegate.save(command)
        if (result is SafetyPlanSaveResult.Committed && armed.compareAndSet(true, false)) {
            committed.complete(result)
            withContext(Dispatchers.IO) {
                check(release.await(15, TimeUnit.SECONDS)) { "result gate timed out" }
            }
        }
        return result
    }

    suspend fun awaitCommitted(): SafetyPlanSaveResult.Committed = committed.await()
    fun releaseResult() = release.countDown()
}
```

Add this androidTest-only Activity in the same file:

```kotlin
internal class SupportHarnessActivity : SupportActivity() {
    override fun supportViewModelFactory(): ViewModelProvider.Factory =
        checkNotNull(factoryProvider)(application)

    override fun closeSupport() {
        closeCounter?.incrementAndGet()
        super.closeSupport()
    }

    companion object {
        var factoryProvider: ((Application) -> ViewModelProvider.Factory)? = null
        var closeCounter: AtomicInteger? = null
    }
}
```

Create `app/src/androidTest/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name="org.mindanchor.support.SupportHarnessActivity"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 2: Write the two architecture-stop phase tests first**

Replace the current timing tests with a fixture that launches `SupportHarnessActivity` against the harness DB/store and a short injected slow threshold. Add these exact tests before completing the fixture wiring:

```kotlin
@Test fun slowRealRoomSaveQueuedBackAndLateCommitClosesOnce() {
    harness.installWriteCounter()
    val transactionGate = harness.gateTransactionExecutor()
    enterWarningSigns("call Maya")

    invokeDoneAndBackInTheSameMainLoop()
    waitForText("Still saving…")
    assertEquals(Lifecycle.State.RESUMED, scenario.state)
    assertEquals(0, harness.writeCount())
    assertNoText("That didn't save")

    transactionGate.release.countDown()
    waitUntil { persistedWarningSigns() == "call Maya" && harness.writeCount() == 1 }
    waitForOriginalActivityToClose()
    assertEquals(1, closeCount.get())
}

@Test fun newerWriterStaysVisibleWhenOlderCommittedResultIsReleased() = runBlocking {
    harness.installWriteCounter()
    resultGate.arm()
    enterWarningSigns("writer A")
    val capturedDone = captureDoneAction()
    invokeOnMain(capturedDone)
    resultGate.awaitCommitted()

    val writerB = RoomSafetyPlanStore(harness.dao) { 200L }.save(
        SaveSafetyPlan(99L, SafetyPlan(warningSigns = "writer B")),
    ) as SafetyPlanSaveResult.Committed
    waitForText("writer B")
    resultGate.releaseResult()
    waitForText("writer B")
    assertEquals(writerB.stored, harness.dao.planNow())
}
```

- [ ] **Step 3: Run the two final-form tests before completing their harness wiring**

Run each method serially after writing the tests and before adding the missing factory/gate fixture methods:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.support.SupportSafetyPlanPersistenceTest#slowRealRoomSaveQueuedBackAndLateCommitClosesOnce" --no-parallel --max-workers=1
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.support.SupportSafetyPlanPersistenceTest#newerWriterStaysVisibleWhenOlderCommittedResultIsReleased" --no-parallel --max-workers=1
```

Expected: FAIL at `compileDebugAndroidTestKotlin` because the final Activity fixture and armed result-gate wiring are not complete. The required behavioral RED against `ec5028d` was already recorded in Task 1 before any production redesign; keep those outputs with this compile RED in the implementation report.

- [ ] **Step 4: Complete the activity fixture and captured-action helpers**

Initialize the fixture in declaration order so the factory exists before the Activity rule launches:

```kotlin
private val appContext: Context = ApplicationProvider.getApplicationContext()
private val harness = SafetyPlanRoomHarness(appContext)
private val resultGate = AfterCommitResultGate(RoomSafetyPlanStore(harness.dao) { 100L })
private val closeCount = AtomicInteger(0)

init {
    SupportHarnessActivity.closeCounter = closeCount
    SupportHarnessActivity.factoryProvider = { application ->
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass == SupportViewModel::class.java)
                return SupportViewModel(application, resultGate, harness.dao, 50L) as T
            }
        }
    }
}

@get:Rule
val rule = createAndroidComposeRule<SupportHarnessActivity>()

private val scenario: ActivityScenario<SupportHarnessActivity>
    get() = rule.activityRule.scenario

@After fun tearDown() {
    resultGate.releaseResult()
    if (scenario.state != Lifecycle.State.DESTROYED) {
        scenario.onActivity { it.finish() }
    }
    rule.waitUntil(10_000L) { scenario.state == Lifecycle.State.DESTROYED }
    SupportHarnessActivity.factoryProvider = null
    SupportHarnessActivity.closeCounter = null
    harness.close()
}
```

Every test that arms a transaction or result gate must also release it in `finally`. Call `harness.drainTransactions()` before every no-late-write assertion. The harness owns trigger/table cleanup because deleting its unique file-backed database removes all test objects.

Capture and invoke the real stale semantics callback exactly as follows:

```kotlin
private fun captureDoneAction(): () -> Boolean {
    val node = rule.onNodeWithText("done").fetchSemanticsNode()
    return checkNotNull(node.config[SemanticsActions.OnClick].action)
}

private fun invokeOnMain(action: () -> Boolean, times: Int = 1) {
    scenario.onActivity {
        repeat(times) { assertTrue(action()) }
    }
}
```

Do not call `performClick()` for the second duplicate because Espresso may wait for recomposition and cease to exercise the stale callback.

- [ ] **Step 5: Add the remaining eight real behavior tests**

The final `SupportSafetyPlanPersistenceTest` contains exactly these ten tests:

1. `doneWritesOnceToRealRoomAndNeverOnKeystrokes` — no row/counter change while typing; one write after Done.
2. `slowRealRoomSaveQueuedBackAndLateCommitClosesOnce` — executor gate, short slow threshold, no failure, late real commit, one close.
3. `realAbortWithQueuedBackRetainsDraftAndHasNoLateCommit` — `RAISE(ABORT)`, same-loop Done/Back, polite failure, unchanged row/counter, drain marker, Activity resumed.
4. `capturedDoneInvokedAgainAfterCommitBeforeResultDeliveryWritesOnce` — after-commit gate, invoke the same captured callback twice, one store call/write, release to direct `Viewing`, no `Saved` intermediary.
5. `newerWriterStaysVisibleWhenOlderCommittedResultIsReleased` — A result gated, B committed through a second real store, B displayed, A released, B remains DB/display winner.
6. `newerWriterPrecedenceAlsoEmitsOneQueuedClose` — repeat test 5 with Back queued; release A, assert exactly one close and B remains in DB.
7. `ignoredInsertReadbackMismatchRetainsDraftAsPoliteFailure` — `RAISE(IGNORE)`, actual mismatch, same draft, no close.
8. `configurationRecreationRetainsDraftAndSlowSaveInTheSameViewModel` — hold transaction executor, recreate after entering draft and again after slow state; release and commit. Test name/comment say configuration recreation only.
9. `destroyAndFreshLaunchShowsRoomOnlyAndDoesNotRestoreDraft_notProcessDeathSimulation` — close an Activity with an unsaved draft, launch a new Activity/ViewModel against the same DB, show only Room; comment explicitly says this is not process-death simulation.
10. `backWhileEditingWritesNothingAndContactsStillUseTheirExistingDaoPath` — Back discards draft with zero writes, then a fresh launch adds/removes a contact through the unchanged DAO behavior.

Every polling wait must observe reducer state, Activity lifecycle, Room row, Flow-rendered text, latch, or executor marker. Do not use `Thread.sleep`, coroutine `delay` as persistence proof, or retries.

- [ ] **Step 6: Run all ten phase tests once to verify GREEN**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.support.SupportSafetyPlanPersistenceTest" --no-parallel --max-workers=1
```

Expected: `BUILD SUCCESSFUL`; all ten methods PASS in one run; no ignored/skipped test; the slow test displays “Still saving…” before release, and failure text appears only in the abort/mismatch tests.

- [ ] **Step 7: Commit the real-Room lifecycle/race evidence**

```powershell
git add app/src/androidTest/AndroidManifest.xml app/src/androidTest/java/org/mindanchor/support/SafetyPlanRoomHarness.kt app/src/androidTest/java/org/mindanchor/support/SupportSafetyPlanPersistenceTest.kt
git diff --cached --check
git commit -m "test: prove safety plan save phases with room"
```

Expected: one test-focused commit with no production state workaround.

---

### Task 5: Route backup import through the store and satisfy release gates

**Files:**

- Create: `app/src/androidTest/java/org/mindanchor/backup/BackupRepositoryImportTest.kt`
- Modify: `app/src/main/java/org/mindanchor/backup/BackupRepository.kt:25-70`
- Verify unchanged: `app/src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt:423-533`
- Verify unchanged: `app/src/androidTest/java/org/mindanchor/continuity/RestoreResumeTest.kt`
- Verify unchanged: `app/src/androidTest/java/org/mindanchor/continuity/ContinuityRoundTripTest.kt`

**Interfaces:**

- Consumes: `SafetyPlanStore.save(SaveSafetyPlan)`, `RoomSafetyPlanStore`, and existing `BackupCodec.toSafetyPlan`.
- Produces: an internal injectable `BackupRepository(context, db, safetyPlanStore)` constructor while retaining the public `BackupRepository(context)` API.

- [ ] **Step 1: Write backup-import ordering and failure tests**

Create `BackupRepositoryImportTest.kt` with a `SafetyPlanRoomHarness` and these exact cases:

```kotlin
@Test fun importUsesStoreMonotonicOrderingWhenItsNowIsOlder() = runBlocking {
    room.dao.savePlan(SafetyPlan(warningSigns = "local", updatedAt = 500L))
    val store = RoomSafetyPlanStore(room.dao) { 100L }
    val repository = BackupRepository(context, room.database, store)
    val backup = BackupCodec.encode(
        BackupCodec.Backup(plan = BackupCodec.Plan(warningSigns = "restored")),
    )

    assertTrue(repository.import(backup, now = 100L))
    val stored = checkNotNull(room.dao.planNow())
    assertEquals("restored", stored.warningSigns)
    assertEquals(501L, stored.updatedAt)
}

@Test fun failedPlanCommandStopsImportBeforeContacts() = runBlocking {
    room.installAbortInsertTrigger()
    val repository = BackupRepository(
        context,
        room.database,
        RoomSafetyPlanStore(room.dao) { 100L },
    )
    val backup = BackupCodec.encode(
        BackupCodec.Backup(
            plan = BackupCodec.Plan(warningSigns = "restored"),
            contacts = listOf(BackupCodec.Contact("Priya", "5551234567")),
        ),
    )

    val thrown = runCatching { repository.import(backup, now = 100L) }.exceptionOrNull()
    assertNotNull(thrown)
    room.drainTransactions()
    assertNull(room.dao.planNow())
    assertEquals(emptyList<CrisisContact>(), room.dao.contactsNow())
}
```

In the already-green `SafetyPlanArchitectureTest.kt`, add the sole-production-writer assertion now so its RED is isolated to this task:

```kotlin
@Test fun everyProductionSafetyPlanWriterUsesTheStore() {
    val productionRoot = File("src/main/java")
    val directWriters = productionRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filterNot { it.invariantSeparatorsPath.endsWith("support/SafetyPlanStore.kt") }
        .filter { ".safety().savePlan(" in it.readText() || "dao.savePlan(" in it.readText() }
        .map { it.invariantSeparatorsPath }
        .toList()
    assertEquals(emptyList<String>(), directWriters)
}
```

- [ ] **Step 2: Run the backup tests and writer boundary to verify RED**

Run serially:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.backup.BackupRepositoryImportTest" --no-parallel --max-workers=1
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.support.SafetyPlanArchitectureTest" --no-parallel --max-workers=1
```

Expected: the instrumentation test fails to compile because the injectable constructor does not exist; the architecture test fails and names `BackupRepository.kt` as the remaining direct production writer.

- [ ] **Step 3: Inject and use the same store without changing the public API**

Change `BackupRepository` to an internal primary constructor plus its existing public convenience constructor:

```kotlin
class BackupRepository internal constructor(
    private val context: Context,
    private val db: AnchorDatabase,
    private val safetyPlanStore: SafetyPlanStore,
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        db = AnchorDatabase.get(context.applicationContext),
        safetyPlanStore = RoomSafetyPlanStore(
            AnchorDatabase.get(context.applicationContext).safety(),
        ),
    )

    private val prefs = LauncherPrefs(context)
```

Replace only the safety-plan line in `import` with:

```kotlin
val planResult = safetyPlanStore.save(
    SaveSafetyPlan(
        operationId = BACKUP_IMPORT_OPERATION_ID,
        draft = BackupCodec.toSafetyPlan(backup.plan, now),
    ),
)
if (planResult is SafetyPlanSaveResult.Failed) throw planResult.cause
```

Add this private constant inside the file's existing `companion object` (alongside `fileName`, `read`, `write`, and `writeTo`); do not create a second companion object:

```kotlin
private const val BACKUP_IMPORT_OPERATION_ID = 0L
```

`0L` is a process-local non-UI correlation value; it is never stored. Do not add a durable import ID or alter the backup JSON. Keep all contact/pulse/preferences/corpus import behavior after this successful boundary unchanged.

- [ ] **Step 4: Verify backup import and continuity restore convergence**

Run serially:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.backup.BackupRepositoryImportTest" --no-parallel --max-workers=1
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.RestoreResumeTest" --no-parallel --max-workers=1
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.ContinuityRoundTripTest" --no-parallel --max-workers=1
.\gradlew.bat :app:testDebugUnitTest --tests "org.mindanchor.support.SafetyPlanArchitectureTest" --no-parallel --max-workers=1
```

Expected: all four commands PASS. The backup tests prove imported `updatedAt` is monotonic and a real Room failure propagates before contacts. Propagation preserves continuity's existing resumability: `RestoreCoordinator` cannot persist `DATASTORES_MERGED` after a failed legacy import and retries from `ROOM_MERGED`. Existing continuity tests pass unchanged because `RestoreCoordinator.mergeDataStores` already delegates `legacyBackupJson` to `BackupRepository.import`; no continuity format/stage change is needed. All three architecture assertions now pass.

- [ ] **Step 5: Commit only backup writer convergence**

```powershell
git add app/src/main/java/org/mindanchor/backup/BackupRepository.kt app/src/androidTest/java/org/mindanchor/backup/BackupRepositoryImportTest.kt app/src/test/java/org/mindanchor/support/SafetyPlanArchitectureTest.kt
git diff --cached --check
git commit -m "fix: route safety plan imports through store"
```

Expected: one three-file commit. No continuity source/test file is staged.

- [ ] **Step 6: Run the static removal and sole-writer checks**

Run:

```powershell
rg -n "rememberSaveable|SafetyPlanDraftState|closeAfterSave|saveBlocksNavigation|SafetyPlanSaveState|consumeSaveSuccess|compareAndSet|NonCancellable|withTimeout" app/src/main/java/org/mindanchor/support
rg -n "\.safety\(\)\.savePlan\(|dao\.savePlan\(" app/src/main/java
rg -n "SavedStateHandle" app/src/main/java/org/mindanchor/support
rg -n "plan_still_saving|SLOW_THRESHOLD_MILLIS = 3_000L" app/src/main
```

Expected: commands 1, 2, and 3 print nothing. The store calls `savePlanTransaction`, and the direct `savePlan(written)` call exists only inside that DAO transaction, so neither direct-writer pattern is present outside the DAO boundary. Command 4 prints the new string use/definition and the exact `3_000L` constant.

- [ ] **Step 7: Run the complete JVM and static gates serially**

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-parallel --max-workers=1
.\gradlew.bat detekt --no-parallel --max-workers=1
.\gradlew.bat :app:lintDebug --no-parallel --max-workers=1
git diff --check
```

Expected: every Gradle command ends `BUILD SUCCESSFUL`; `git diff --check` prints nothing. If the pre-existing protected `LlmPrefs.kt` causes a known unrelated gate failure, report its unchanged baseline diff separately; do not edit it and do not describe the redesign gate as green until the branch owner resolves or explicitly isolates that unrelated failure.

- [ ] **Step 8: Prove the dedicated persistence class 20 consecutive times**

Install the current debug app and test APK once:

```powershell
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest --no-parallel --max-workers=1
```

Then run the exact class 20 times, stopping at the first nonzero exit:

```powershell
1..20 | ForEach-Object {
    Write-Host "Support persistence run $_/20"
    adb shell am instrument -w -r -e class org.mindanchor.support.SupportSafetyPlanPersistenceTest org.mindanchor.test/androidx.test.runner.AndroidJUnitRunner
    if ($LASTEXITCODE -ne 0) { throw "Support persistence run $_ failed" }
}
```

Expected: every run ends `OK`, all ten methods execute, and no output contains `FAILURES!!!`, `INSTRUMENTATION_FAILED`, ignored, skipped, retry, or flaky-test handling. One failure resets the evidence count to zero after the defect is fixed.

- [ ] **Step 9: Run the complete connected suite three consecutive times**

Run:

```powershell
1..3 | ForEach-Object {
    Write-Host "Full connected run $_/3"
    .\gradlew.bat :app:connectedDebugAndroidTest --rerun-tasks --no-parallel --max-workers=1
    if ($LASTEXITCODE -ne 0) { throw "Full connected run $_ failed" }
}
```

Expected: all three runs end `BUILD SUCCESSFUL` with zero failed, ignored, or skipped instrumentation tests. They include `SafetyPlanStoreRoomTest`, all ten `SupportSafetyPlanPersistenceTest` methods, `BackupRepositoryImportTest`, unchanged contact behavior, `SupportScreenTest`, semantics, large-font, screenshot, migration, and continuity restore/round-trip coverage. Any unrelated failure is isolated and documented, never silently excluded from the count.

- [ ] **Step 10: Verify protected files, commit scope, and request independent review**

Run:

```powershell
git status --short
git diff -- app/src/main/java/org/mindanchor/llm/LlmPrefs.kt
Get-FileHash -Algorithm SHA256 -LiteralPath .\AGENTS.md
git log --oneline -5
```

Expected: the protected diff/hash match Step 1 exactly; neither protected path appears in any of the five redesign commits. The five narrow commit subjects are:

```text
feat: add transactional safety plan store
feat: centralize support safety plan state
refactor: render safety plan from view model state
test: prove safety plan save phases with room
fix: route safety plan imports through store
```

Give an independent reviewer the complete range from the parent of the first commit through the fifth commit and ask for Critical/Important findings specifically on: transaction commit/readback truth, cancellation, monotonic overflow, stale-result arbitration, same-loop Done/Back, close exactly once, authentic captured-callback duplicates, after-commit writer ordering, configuration-vs-fresh-owner wording, backup/continuity writer convergence, and test harness phase control. Acceptance requires “no Critical or Important findings.” Fix any finding with a new failing focused test, minimal code, the same focused/full gates, and a separate narrow commit.

## Self-Review

- **Spec coverage:** Task 1 creates the one transactional Room command with checked monotonic `updatedAt`, exact in-transaction readback, post-commit result, cancellation rethrow, and real SQLite failure/mismatch evidence. Task 2 gives the ViewModel sole ownership of `Viewing`/`Editing`/`Saving`, operation IDs, slow threshold, stale-result rejection, revision arbitration, draft/error retention, and Close. Task 3 removes every Compose/save handshake owner and distinguishes slow from actual failure. Task 4 supplies every required real-Room phase control and lifecycle/race scenario, including truthful configuration/fresh-owner language. Task 5 routes direct and continuity backup import through the store and executes 20 focused plus three full connected runs.
- **Scope discipline:** Contacts remain on `SafetyDao` and are tested unchanged. No schema, migration, continuity payload/stage, clinical copy, launcher behavior, `LlmPrefs.kt`, or root `AGENTS.md` change is planned. The only new production file is the narrow store.
- **Placeholder scan:** The plan contains no deferred implementation markers. Every code-producing task names exact files, signatures, test cases, serial commands, expected RED/GREEN outcomes, and explicit staging commands.
- **Type consistency:** `SaveSafetyPlan(operationId, draft)`, `SafetyPlanSaveResult.Committed/Failed`, `SafetyPlanUiState.Viewing/Editing/Saving`, `SupportEvent`, `SupportEffect.Close`, `RoomSafetyPlanStore`, and `savePlanTransaction(draft, clockMillis)` are spelled and typed consistently from DAO/store through reducer, Compose, harness, and backup import.
- **Risk check:** The plan does not claim a timeout can cancel SQLite. The executor drain, write counter, abort/ignore triggers, after-commit result gate, separate writer, and strict `updatedAt` arbitration directly cover the previously unprovable late-commit and result/publication races. Process death is deliberately not simulated or overclaimed.
