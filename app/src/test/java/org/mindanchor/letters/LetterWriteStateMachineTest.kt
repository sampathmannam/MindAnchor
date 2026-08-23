package org.mindanchor.letters

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.CheckInPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.llm.LetterError
import org.mindanchor.llm.LlmClient
import org.mindanchor.llm.LlmPrefs
import org.mindanchor.llm.LlmRequest
import org.mindanchor.llm.LlmResponse
import org.robolectric.RobolectricTestRunner

/**
 * The state machine is the contract between the UI and
 * the LLM. The test pins the 4 states and the 4
 * transitions reachable from public actions:
 *   Idle -> Writing -> Reader (success)
 *   Idle -> Writing -> Error (NoApiKey / InvalidApiKey)
 *   Error -> Idle (acknowledgeError)
 *   (Reader -> Writing) -> Reader (regenerate, after
 *   deleting today's existing letter)
 *
 * The fake [LlmClient] is injected via the internal
 * 6-arg [LetterViewModel] constructor so the test never
 * touches the real Groq client or the LlmClientFactory
 * stub. The production 5-arg constructor is what
 * HomeActivity / the letter screen use.
 *
 * Tests call [LetterViewModel.runGeneration] directly
 * inside `runBlocking { ... }` instead of going through
 * [LetterViewModel.generateToday] / [LetterViewModel.regenerate].
 * Those public methods wrap `runGeneration` in
 * `viewModelScope.launch { ... }`, which uses
 * `Dispatchers.Main.immediate` and suspends on DataStore's
 * internal `Dispatchers.IO` — the test scheduler can't
 * wait for that. Calling `runGeneration` directly
 * synchronously from `runBlocking` lets the test observe
 * the terminal state without racing the coroutine.
 */
@RunWith(RobolectricTestRunner::class)
class LetterWriteStateMachineTest {

    private lateinit var llmPrefs: LlmPrefs
    private lateinit var notesPrefs: NotesPrefs
    private lateinit var checkInPrefs: CheckInPrefs
    private lateinit var letterStore: LetterStore
    private lateinit var letterLog: LetterGenerationLog
    private lateinit var fakeClient: FakeLlmClient

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        llmPrefs = LlmPrefs(ctx)
        notesPrefs = NotesPrefs(ctx)
        checkInPrefs = CheckInPrefs(ctx)
        letterStore = LetterStore(ctx)
        letterLog = LetterGenerationLog(ctx)
        fakeClient = FakeLlmClient()
        runBlocking {
            llmPrefs.reset()
            letterStore.reset()
            letterLog.reset()
        }
    }

    @After
    fun tearDown() {
        // No Dispatchers.setMain to clean up — tests no longer
        // touch the Main dispatcher.
    }

    @Test
    fun `initial state is Idle`() {
        val vm = newVm()
        assertEquals(LetterWriteState.Idle, vm.state.value)
    }

    @Test
    fun `generateToday with no api key transitions to Error with NoApiKey`() {
        val vm = newVm()
        runBlocking { vm.runGeneration(LocalDate.now(), isRegenerate = false) }
        assertTrue(vm.state.value is LetterWriteState.Error)
        assertEquals(
            LetterError.NoApiKey()::class,
            (vm.state.value as LetterWriteState.Error).error::class,
        )
    }

    @Test
    fun `generateToday with api key and a successful response transitions to Reader`() {
        runBlocking { llmPrefs.setApiKey("gsk_test") }
        fakeClient.nextResult = Result.success(
            LlmResponse(
                content = "It was a quiet Tuesday.\n\nThe note sat there.\n\nWhat was the loudest thing?",
                promptTokens = 100,
                completionTokens = 50,
                durationMs = 800L,
            ),
        )
        val vm = newVm()
        runBlocking { vm.runGeneration(LocalDate.now(), isRegenerate = false) }
        val state = vm.state.value
        assertTrue("expected Reader, got $state", state is LetterWriteState.Reader)
        val reader = state as LetterWriteState.Reader
        assertEquals(
            "It was a quiet Tuesday.\n\nThe note sat there.\n\nWhat was the loudest thing?",
            reader.letter.body,
        )
        assertEquals("groq", reader.letter.provider)
        assertEquals(100, reader.letter.promptTokens)
        assertEquals(50, reader.letter.completionTokens)
    }

    @Test
    fun `generateToday with an InvalidApiKey error transitions to Error and logs the failure`() {
        runBlocking { llmPrefs.setApiKey("bad") }
        fakeClient.nextResult = Result.failure(LetterError.InvalidApiKey())
        val vm = newVm()
        runBlocking { vm.runGeneration(LocalDate.now(), isRegenerate = false) }
        assertTrue(vm.state.value is LetterWriteState.Error)
        val entries = runBlocking { letterLog.entries.first() }
        assertEquals(1, entries.size)
        assertEquals("InvalidApiKey", entries[0].errorClass)
    }

    @Test
    fun `acknowledgeError returns to Idle`() {
        runBlocking { llmPrefs.setApiKey("bad") }
        fakeClient.nextResult = Result.failure(LetterError.InvalidApiKey())
        val vm = newVm()
        runBlocking { vm.runGeneration(LocalDate.now(), isRegenerate = false) }
        assertTrue(vm.state.value is LetterWriteState.Error)
        vm.acknowledgeError()
        assertEquals(LetterWriteState.Idle, vm.state.value)
    }

    @Test
    fun `regenerate deletes today's letter first, then runs generation`() {
        runBlocking {
            llmPrefs.setApiKey("gsk_test")
            letterStore.save(
                Letter(
                    date = LocalDate.now(),
                    body = "old letter",
                    provider = "groq",
                    model = "llama-3.3-70b-versatile",
                    promptTokens = 1,
                    completionTokens = 1,
                    durationMs = 1L,
                ),
            )
            // Delete first (mimics regenerate's behavior)
            letterStore.delete(LocalDate.now())
        }
        fakeClient.nextResult = Result.success(
            LlmResponse("new body", 100, 50, 800L),
        )
        val vm = newVm()
        runBlocking { vm.runGeneration(LocalDate.now(), isRegenerate = true) }
        val state = vm.state.value
        assertTrue(state is LetterWriteState.Reader)
        assertEquals("new body", (state as LetterWriteState.Reader).letter.body)
        val allLetters = runBlocking { letterStore.letters.first() }
        assertEquals(1, allLetters.size)
        assertEquals("new body", allLetters[0].body)
    }

    /**
     * Construct the view model with the fake [LlmClient]
     * wired in via the internal 6-arg secondary
     * constructor. The public 5-arg constructor would call
     * [org.mindanchor.llm.LlmClientFactory.create] (which
     * currently returns a NotImplementedLlmClient stub that
     * always fails with [LetterError.Unknown]) and could
     * not be steered by [fakeClient.nextResult]. The
     * internal constructor is `internal` (same module) so
     * the test can call it without exporting it to the
     * rest of the app.
     */
    private fun newVm() = LetterViewModel(
        llmPrefs = llmPrefs,
        notesPrefs = notesPrefs,
        checkInPrefs = checkInPrefs,
        letterStore = letterStore,
        letterLog = letterLog,
        client = fakeClient,
    )
}

/**
 * Test seam: a fake [LlmClient] whose result is set per
 * test via [nextResult]. [testConnection] mirrors the
 * success / failure of the last [complete] call so the
 * "Test connection" button (a different code path) can be
 * tested by the same fake if a future test needs it.
 */
private class FakeLlmClient : LlmClient {
    var nextResult: Result<LlmResponse> = Result.failure(LetterError.Unknown())
    override suspend fun complete(req: LlmRequest): Result<LlmResponse> = nextResult
    override suspend fun testConnection(): Result<Unit> =
        if (nextResult.isSuccess) Result.success(Unit)
        else Result.failure(nextResult.exceptionOrNull()!!)
}
