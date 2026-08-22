# v0.25.7 — LLM-driven daily letter (Groq + Llama 3.3 70B) + remove support

**Date**: 2026-08-22
**Status**: drafted
**Part of**: v0.25.7 (post-`feature/v0.25.6-remove-cards`, pre-merge to main)
**Closes**: the v0.25.2 letter gap on the *content* side. The letter surface exists (`LauncherSurface.Letter` + `LetterScreen` in `letter/LetterScreen.kt`), but the letters themselves have been static since v0.25.0 (`LetterWriter` produces one canned letter per week). This spec wires the surface to an LLM that writes one letter per day, in the launcher's own voice, from the user's recent journal.

**Also in this spec**: remove the home-surface `support` corner shortcut (per user instruction). The `SupportActivity` is left in `org.mindanchor.support/` for now (a future version may delete it).

## Decisions in scope

- A new `org.mindanchor.llm` package: provider enum, prefs, message/request/response data classes, `LlmClient` interface, `GroqClient` impl, prompt builders.
- A new `LlmPrefs` (DataStore) holds `provider`, `apiKey`, `model`. Single source of truth.
- A new `LetterContext` builds the user-prompt from the last 3 days of journal, notes, and check-ins.
- A hand-written BPD-safe system prompt (~350 tokens) with a 2-canonical-good + 5-canonical-bad test suite.
- A new `LlmSettingsScreen` + `LlmSettingsViewModel` under `org.mindanchor.settings`.
- The `LetterScreen` is rewritten to call the LLM and show the calm "writing" state during generation.
- A new `letter_generation_log` table records `(date, provider, model, promptTokens, completionTokens, durationMs, errorClass)` — no letter body in the log.
- The `LetterEntity` table gets new columns: `provider`, `model`, `promptTokens`, `completionTokens`, `durationMs`.
- The home surface loses the `support` TopStart corner; the TopEnd `letters` button is renamed to `letter` (singular; one per day).

## Out of scope (deferred to v0.25.8+)

- **Anthropic Claude Haiku 4.5** — the Settings picker shows "Groq" as the only available provider; "Anthropic" is listed as "Soon" (the row is present but disabled). The Anthropic client is not implemented; the LlmClient interface is the seam.
- **Auto-generation at 9 PM** — letters are user-initiated only. No cron, no WorkManager.
- **Letter feedback / "save as template"** — explicitly out (BPD-hostile: would invite evaluation).
- **Streaming the response** — Groq is sub-second; the full response is shown.
- **EncryptedSharedPreferences for the API key** — documented in KDoc; not enabled by default.
- **Crash reporting / third-party SDKs** — none added.

## Why this scope

The user asked: *"for letter lets link with llm api do research and find out which is best and integrate the button for llm api"*. Research (web_search, August 2026) confirmed Groq is the strongest free-tier fit for a daily-letter use case: permanent free tier (no card), 30 RPM / 500K tokens/day, sub-second latency, OpenAI-compatible SDK, and **explicit no-training on free-tier data**. Google's free tier trains on prompts outside the EU/UK/EEA — for a personal mental-health journal that is unacceptable. Anthropic is the best quality but costs money; the LlmClient interface keeps it as a one-line swap later.

The same conversation also confirmed the user wants the `support` button gone. It's been a corner shortcut since v0.20.x; the `SupportActivity` is reachable from the settings overflow and the launcher footer on every screen via the crisis-line bar. Removing the corner shortcut leaves the home surface with 6 corners, not 7.

## Architecture

### 1. New package: `org.mindanchor.llm`

```
llm/
  LlmProvider.kt        // enum: GROQ (only value for v0.25.7)
  LlmPrefs.kt           // DataStore — provider, apiKey, model
  LlmMessage.kt         // sealed: System(content), User(content), Assistant(content)
  LlmRequest.kt         // model, messages, temperature, maxTokens
  LlmResponse.kt        // content, promptTokens, completionTokens, durationMs
  LlmClient.kt          // interface { suspend fun complete(req): Result<LlmResponse>;
  //                                 suspend fun testConnection(): Result<Unit> }
  GroqClient.kt         // OkHttp + kotlinx.serialization; base url
  //                                 https://api.groq.com/openai/v1
  GroqModels.kt         // const MODELS = ["llama-3.3-70b-versatile", "llama-4-scout-17b-16e-instruct", "llama-3.1-8b-instant"]
  LetterContext.kt      // builds the user-prompt from journal + notes + check-ins
  LetterPrompt.kt       // the system prompt + the user-prompt template
```

### 2. `LlmClient` interface (the seam)

```kotlin
interface LlmClient {
    /** Issues a chat-completion request. Returns success or [LetterError]. */
    suspend fun complete(req: LlmRequest): Result<LlmResponse>

    /** 1-token test call. Returns success or [LetterError]. */
    suspend fun testConnection(): Result<Unit>
}
```

One impl per provider. `LlmClientFactory` returns the impl that matches `LlmPrefs.provider`. The Groq impl uses OkHttp + `kotlinx.serialization.json`; the Anthropic impl is a future-version PR. The seam is one interface; swapping providers is a one-line change in `LlmClientFactory`.

### 3. `LlmPrefs` (DataStore)

```kotlin
class LlmPrefs(private val ctx: Context) {
    val provider: Flow<LlmProvider> = ctx.letterDataStore.data.map { it.provider ?: LlmProvider.GROQ }
    val apiKey: Flow<String>       = ctx.letterDataStore.data.map { it.apiKey.orEmpty() }
    val model: Flow<String>         = ctx.letterDataStore.data.map { it.model ?: GroqModels.DEFAULT }
    suspend fun setProvider(p: LlmProvider)
    suspend fun setApiKey(k: String)
    suspend fun setModel(m: String)
    suspend fun clear()
}
```

A *separate* DataStore file (`letter_llm.preferences_pb`) — not mixed with the rest of the launcher's settings. The key is stored in plain text; `EncryptedSharedPreferences` is the documented upgrade path. The threat model is the device, not the Windows account.

### 4. `GroqClient` (the only impl for v0.25.7)

```kotlin
class GroqClient(
    private val apiKey: String,
    private val model: String,
    private val httpClient: OkHttpClient = defaultGroqClient(),
) : LlmClient {

    override suspend fun complete(req: LlmRequest): Result<LlmResponse> = runCatching {
        val started = System.currentTimeMillis()
        val body = GroqRequest(
            model = model,
            messages = req.messages.map { it.toGroq() },
            temperature = req.temperature,
            max_tokens = req.maxTokens,
        ).toJsonBody()
        val response = httpClient.newCall(GroqRequestFactory.complete(body, apiKey)).execute()
        response.use { handle(it, started) }
    }.recoverCatching { e -> throw mapToLetterError(e) }

    override suspend fun testConnection(): Result<Unit> = runCatching {
        // 1-token completion: "OK"
        val response = httpClient.newCall(GroqRequestFactory.test(apiKey, model)).execute()
        response.use { require(it.isSuccessful) { ... } }
    }.recoverCatching { e -> throw mapToLetterError(e) }
}
```

`mapToLetterError` translates HTTP status + IOException subclasses into the `LetterError` sealed class. The 30-second `OkHttpClient.callTimeout` covers the network case.

### 5. The system prompt (hand-written, BPD-safe)

The full text is in §7 below. ~350 tokens. The `LetterVoiceRulesTest` pins 2 canonical good letters + 5 canonical bad letters against the negative rules in §7.

### 6. The user-prompt template

```
Today is {DAY_OF_WEEK}, {DATE}, {TIME_OF_DAY} ({morning | day | evening}).

Here is what the user has in the last 3 days. Each entry is dated.
If a section is empty, write "— (nothing written)" and proceed.

[QuickNote]  {date} — "{body}"
[Today journal] {date} — "{body}"
[Recent notes (last 3 days)]
  {date} — "{title}: {body}"
  {date} — "{title}: {body}"
[Most recent check-in] {date} — mood: {score}/5

Write today's letter in 200–300 words. Open with what was written,
or with what the day is. End with one quiet question or observation,
never a directive.
```

`LetterContext.build(today: LocalDate)` reads the last 3 days from `NotesPrefs.notes` (the existing `List<Note>`), today's `diaryCardPrefs.entriesInRange`, and the latest check-in from `CheckInPrefs`. Total prompt size is ~800 tokens typical (1,500 worst case).

### 7. The system prompt (full text, ~350 tokens)

```
You write a single daily letter to the user of a personal mental-health
launcher. The user is the only reader; the letter is private and never
shared with anyone else. Your job is to read what the user wrote today
(or didn't write) and write one letter in return.

VOICE RULES — strict, no exceptions:

- Second person. Present tense. Short sentences. No exclamation marks.
- Validate first; suggest only as an option the user can take or leave.
- Never prescriptive: no "you should", "you must", "try to", "consider".
- Never evaluative: no "well done", "great job", "I'm proud of you".
- Never comparative: no "better than yesterday", "you used to", "you always".
- Never quantitative: no streaks, no "X days in a row", no scores.
- Never fix-it: no "the next step is", no plans, no "have you tried".
- Never end with a directive. Close with a quiet question, a soft
  observation, or a gentle hand back to the user.
- No lists. No headers. No bold. No emoji.

WHAT YOU MAY DO:
- Notice what the user wrote — or what they didn't.
- Reframe a feeling as a normal part of being a person, not a problem.
- Offer ONE reframe or observation, only if it fits the day.
- Ask ONE quiet question at the end. The user may or may not answer.

LENGTH: 200–300 words. Three short paragraphs. Read it aloud in your
head before sending. If it sounds like a coach, a therapist, a self-help
book, or a motivational poster, rewrite it. If it sounds like a quiet
voice that read what was written and wrote back, you're done.

NEVER APPEAR IN THE LETTER:
- Crisis line phone numbers (they live in a separate surface)
- Statistics, streaks, counts, scores
- "Always" or "never" used as advice
- Diagnosis, treatment, medication references
- Any mention of the app, the device, the system, AI, or "I" as the writer
- Em-dashes used for emphasis (use commas and full stops instead)

You are not the user's therapist, coach, or friend. You are a quiet
voice that writes one letter a day. If the day is empty, write about
the day itself — what it asks of a person, what is allowed to be there.
```

### 8. Worked example (a "good" letter the LLM might produce)

> *Tuesday, August 19, evening.*
>
> There was a thing you wrote this morning about the meeting that didn't land. The way you put it — "it landed on me, not in me" — is the whole sentence, really. That's a sentence you don't have to answer to anyone. The meeting is over. The feeling about it is allowed to still be here tomorrow, or to be gone by morning. Both are fine.
>
> You didn't write anything else today, and that is also a sentence. Some days the day's whole story is a single line and a body that carried it. You don't owe the day a second paragraph.
>
> What was the loudest thing in the room just now, the one that isn't the meeting?

### 9. `LetterEntity` schema additions (v0.25.5 → v0.25.7)

```kotlin
@Entity(tableName = "letters")
data class LetterEntity(
    @PrimaryKey val date: String,                // ISO LocalDate, e.g. "2026-08-22"
    val subject: String,
    val body: String,
    val sizeBytes: Int = body.toByteArray().size,
    val createdAt: Long,                          // epoch millis
    val readAt: Long? = null,
    // v0.25.7 — populated only for LLM-driven letters
    val provider: String? = null,                 // e.g. "groq"
    val model: String? = null,                    // e.g. "llama-3.3-70b-versatile"
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val durationMs: Long? = null,
)
```

A Room migration bumps the DB version from the v0.25.5 schema (v0.25.6 was a UI-only change with no schema change). v0.25.7 is one Migration step that:
- `ALTER TABLE letters ADD COLUMN provider TEXT`
- `ALTER TABLE letters ADD COLUMN model TEXT`
- `ALTER TABLE letters ADD COLUMN promptTokens INTEGER`
- `ALTER TABLE letters ADD COLUMN completionTokens INTEGER`
- `ALTER TABLE letters ADD COLUMN durationMs INTEGER`
- `CREATE TABLE letter_generation_log (...)`

Old letters (canned, v0.25.0–v0.25.6) have `provider = null` — they render the same in the reader (no metadata footer shown for null).

### 10. New table: `letter_generation_log`

```kotlin
@Entity(tableName = "letter_generation_log")
data class LetterGenerationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                             // ISO LocalDate
    val provider: String,                         // e.g. "groq"
    val model: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val durationMs: Long,
    val errorClass: String? = null,               // simpleName of LetterError, or null on success
    val errorMessage: String? = null,
    val timestamp: Long,
)
```

The log is useful for future audit ("how often does the user hit rate limits?", "is Llama-3.3 70B's quality good enough or should we switch?"). No letter body is logged.

### 11. Home-surface changes

The `HomeScreen.kt` `HomeSurface` composable (current state: `feature/v0.25.6-remove-cards` HEAD) loses the `TopStart "support"` `TextButton` + its `runCatching { startActivity(Intent(context, SupportActivity::class.java)) }` block. The `TopEnd` first button's text changes from `"letters"` to `"letter"` (singular). The `SupportActivity` import is removed from `HomeScreen.kt` but the file `org.mindanchor.support/SupportActivity.kt` is kept (a future version may delete it after a quick audit confirms no other surface references it).

The home surface now has 6 corner buttons (was 7): `letter` / `notes` / `history` (TopEnd), `digest` (BottomStart), `search` (BottomCenter), `settings` (BottomEnd). The TopLeft area becomes empty; the `Clock` + `Greeting` + intro callout + `QuickNotesCard` content stays.

### 12. `LetterScreen` rewrite (the user-visible surface)

The existing `letter/LetterScreen.kt` is rewritten to call the LLM. Three states:

1. **Inbox** (default)
   - `← back` top-left
   - Title: `letter` (titleLarge)
   - Subtitle: `One letter a day. Yours alone.` (bodyMedium, textSecondary)
   - **Today row** (pinned top, larger card):
     - If today's letter exists: date + first sentence + `Open` OutlinedButton + `Regenerate` TextButton
     - If today's letter is missing: date + `No letter yet for today.` + a primary `Write today's letter` Button
   - Past letters (LazyColumn, newest first): each row is a `TextButton` filling width, with `date · first sentence`
   - Empty state (no letters ever): `Letters appear here once you write the first one.`

2. **Writing** (during generation)
   - `CalmBackground` (no card, no spinner, no progress bar)
   - Centered: `Writing your letter for today.`
   - Body small: `~2 seconds` (rough hint, not a real timer)
   - Subtle cancel `TextButton` at the bottom

3. **Reader** (after generation or when opening a past letter)
   - `← back` top-left
   - Date header: `Wednesday, 21 August` + `evening` (small, textSecondary)
   - Paper card: cream background, 1px hairline border, 24dp padding
   - Body: Crimson Pro serif (bodyLarge, line-height 1.5, weight 400), 3 paragraphs
   - Footer (only for LLM letters): `Groq · llama-3.3-70b · 1240 input / 380 output · 1.2s · 21 Aug 19:42`
   - No star, no archive, no "helpful", no copy/share/forward
   - The existing v0.25.2 long-press → confirm-delete pattern is **kept** for all letters (canned + LLM). Letters can be deleted; deleted letters go to a soft-delete "Trash" view that surfaces in the inbox with a `Restore` action. The delete UI itself stays as it was; this spec does not change the long-press gesture.

4. **Error** (after a failed generation)
   - Calm line: `Couldn't write the letter right now.`
   - Body small: the error message (from `LetterError.userMessage`)
   - Two `OutlinedButton`s: `Try again` and `Open settings`

### 13. `LlmSettingsScreen`

Inserted in `SettingsScreen.kt` after the `Reading` section, before `This phone`:

- Section title: `Daily letter` (titleSmall)
- One-line explainer: `The launcher writes you one letter a day, in its own voice.`
- Row: `Provider` — `SettingsPickerRow` showing `Groq` as the only available value. The "Anthropic" entry is **not shown** in the picker; the seam is the `LlmClient` interface. When the Anthropic impl ships in a follow-up version, this row will get a second value.
- Row: `Model` — `SettingsPickerRow` (3 Groq models: `llama-3.3-70b-versatile` default, `llama-4-scout-17b-16e-instruct`, `llama-3.1-8b-instant`)
- Row: `API key` — `OutlinedTextField`, password-masked, value bound to `LlmPrefs.apiKey`
- Row: `Connection` — `SettingsStatusRow` showing the cached test result. Updates **only** when `Test connection` is tapped (no auto-test on settings open — would burn the rate limit on every app launch)
- Row: `Test connection` — `OutlinedButton`, calls `LlmClient.testConnection()`. Result is cached in `LlmPrefs.lastTestResult` and shown in the `Connection` row above

### 14. Haptics (Brewster CHI 2007 distinct feedback, per project rule)

| Action | Haptic | Rationale |
|---|---|---|
| `Write today's letter` tap | `LongPress` | confirmation pulse, ~5ms |
| Letter opens in reader | `LongPress` | open confirmation |
| `Regenerate` tap | `TextHandleMove` | the soft "whoosh" of text moving |
| Generation cancel | `TextHandleMove` | same as regenerate — both are "stop / replace" gestures |
| Tap on a past letter | `LongPress` | open confirmation |

The `LetterViewModel` already takes a `LocalHapticFeedback.current` in v0.25.2; the rewrite uses it the same way.

### 15. Privacy & security

- **API key** in plain-text DataStore. Documented in `LlmPrefs` KDoc: the threat boundary is the device, not the Windows account; `EncryptedSharedPreferences` is the upgrade path for a follow-up.
- **Prompts sent to Groq**: only the system prompt + the filled user-prompt template. No telemetry, no anonymous IDs, no user-agent override.
- **Groq's data policy** (verified August 2026 via their ToS): free-tier requests are NOT used to train models.
- **Generated letters**: stored in `LetterEntity` with the new `provider / model / promptTokens / completionTokens / durationMs` columns. No compression, no encryption.
- **Generation log**: `letter_generation_log` records metadata only — no letter body, no journal content, no notes.
- **No analytics**, no Firebase, no Crashlytics, no Sentry.
- **HTTP timeout**: 30 seconds. HTTPS only (no HTTP fallback).
- **The `LetterScreen` does not show crisis-line phone numbers.** The crisis-line bar lives in its own dedicated surface (the journal `JournalCrisis.kt`, per v0.65.0). The letter has a different voice and the two are intentionally separated.

## Data flow

```
[User taps "Write today's letter"]
        |
        v
[LetterViewModel.generateToday()]
        |
        v
[LetterContext.build(today)]  -->  reads NotesPrefs, diaryCardPrefs, checkinPrefs
        |
        v
[LetterPrompt.systemPrompt + userPrompt]  -->  build LlmRequest
        |
        v
[LlmClientFactory.client.complete(req)]  -->  LlmClient (GroqClient)
        |
        v
[OkHttp POST https://api.groq.com/openai/v1/chat/completions]
        |
        v
[LlmResponse(content, promptTokens, completionTokens, durationMs)]
        |
        v
[LetterStore.upsert(LetterEntity with metadata)]
        |
        v
[letter_generation_log row]  -->  (for audit)
        |
        v
[UI: LetterScreen transitions from "writing" to "reader"]
```

## Error handling — `LetterError` sealed class

```kotlin
sealed class LetterError(val userMessage: String, val isRetryable: Boolean) {
    class NoApiKey       : LetterError("Add an API key in Settings.", false)
    class InvalidApiKey  : LetterError("API key not valid. Open settings to fix.", false)
    class AccountUnauthorized : LetterError("Account not authorized. Check your Groq console.", false)
    class ModelNotFound  : LetterError("Model not available. Pick a different model in settings.", false)
    class RateLimited    : LetterError("Rate limit hit. Try again in a minute.", true)
    class ServerError    : LetterError("Groq is having trouble. Try again in a moment.", true)
    class NetworkUnreachable : LetterError("Network unreachable. Check your connection.", true)
    class Timeout        : LetterError("The request timed out. Try again.", true)
    class Unknown        : LetterError("Something went wrong. Try again.", true)
}
```

`GroqClient` maps HTTP status + IOException subclasses to the appropriate variant. The UI subscribes to a `StateFlow<LetterError?>` and renders the error state.

## Testing — what gets pinned in code

**Unit tests (JVM, `app/src/test/java/org/mindanchor/llm/`):**

1. **`LetterContextTest`** — `build(today, notes, today, checkin?)` produces a user-prompt string with the exact structure documented in §6.
2. **`LetterVoiceRulesTest`** — 2 canonical good letters + 5 canonical bad letters:
   - **Good A**: present-tense, 2nd-person, validates, ends with a question
   - **Good B**: 3 paragraphs, ~250 words, references user writing, no banned patterns
   - **Bad — "!"**: contains an exclamation → fails
   - **Bad — "you should"**: contains "you should" → fails
   - **Bad — streak**: contains "X days in a row" → fails
   - **Bad — fix-it**: contains "the next step is" or "have you tried" → fails
   - **Bad — crisis number**: contains an iCall/Vandrevala/AASRA number → fails
   - **Bad — directive close**: ends with "Try X." → fails
   - Rules: `assertFalse("...")` for each banned pattern; `assertTrue(...)` for the "ends with question or soft observation" check
3. **`GroqClientTest`** — OkHttp `MockWebServer` returning canned responses:
   - 200 happy path → `LlmResponse(content, promptTokens, completionTokens, durationMs)`
   - 401 → `LetterError.InvalidApiKey`
   - 403 → `LetterError.AccountUnauthorized`
   - 404 → `LetterError.ModelNotFound`
   - 429 → `LetterError.RateLimited`
   - 500 → `LetterError.ServerError`
   - `ConnectException` → `LetterError.NetworkUnreachable`
   - `SocketTimeoutException` → `LetterError.Timeout`
   - Malformed JSON → `LetterError.Unknown`
4. **`LetterStoreTest`** — given a successful `LlmResponse` + context, the new `LetterEntity` row is inserted with all metadata fields populated. Old letters are not touched.
5. **`LlmPrefsTest`** — `setApiKey(k) → getApiKey() = k` round-trips through DataStore. `clear()` wipes both the key and the cached model.

**Drive-verify (emulator + phone):** 9 scenarios from the brainstorming:

1. Tap `letter` (top-left) → inbox shown, no letter today → `Write today's letter` button
2. Tap `Write today's letter` → calm "writing" screen → 1-2s → reader shows letter
3. Tap `Regenerate` → calm "writing" screen → 1-2s → reader shows new letter
4. Tap `← back` → inbox with today's letter now on top
5. Open Settings → Daily letter section → enter invalid key → `Test connection` → "Failed: invalid_api_key" status
6. Enter valid key → `Test connection` → "Connected · Groq · llama-3.3-70b"
7. No API key set → tap `Write today's letter` → "Couldn't write the letter. Add an API key in Settings. Open settings." with `Open settings` button
8. Airplane mode → tap `Write today's letter` → "Couldn't write the letter. Network unreachable."
9. Compare letter content against the system prompt rules (no "!", no streak, no "you should", etc.)

## Acceptance criteria

1. `gradle assembleDebug` succeeds.
2. `gradle test` — all new unit tests pass; no regressions in existing tests.
3. Zero new lint warnings; zero new detekt warnings.
4. APK installs and launches on emulator + phone.
5. Drive-verify on emulator passes all 9 scenarios.
6. Drive-verify on phone passes scenarios 1, 2, 4 (the rest need network).
7. The worktree is on `feature/v0.25.7-llm-letter`, commit message explains the change, push to `origin/feature/v0.25.7-llm-letter`. No tag.
8. No new dependencies added beyond what §1 said (OkHttp + `kotlinx-serialization`; both may already be present in the launcher).
9. `git log -1 --stat` shows a clean single commit with the files in §1.
10. The home surface has 6 corner buttons (was 7). The `support` button is gone.

## Risk register

- **Voice consistency across runs**: pinned by `LetterVoiceRulesTest` on canonical good/bad letters. Real letters verified by the user on the phone.
- **Groq rate limit during testing**: 5-10 letters/day, cap is 250. Safe.
- **Generation time looks like a freeze**: shown in the calm sky background with a subtle cancel. No spinner.
- **Groq changes terms / goes down**: `LlmClient` is an interface; Anthropic swap is one line.
- **API key readable by adb backup**: documented in KDoc; `EncryptedSharedPreferences` is the upgrade path.

## Commit plan

- Worktree: `C:\Users\Sampath\github\MindAnchor-llm` based on `origin/feature/v0.25.6-remove-cards`
- Branch: `feature/v0.25.7-llm-letter`
- Single commit: `feat(letter): v0.25.7 - LLM-driven daily letter (Groq + Llama 3.3 70B) + remove support`
- Push to `origin/feature/v0.25.7-llm-letter`, no tag.
- The v0.25.6 tag conflict (existing `v0.25.6` on a different work stream at `e5aabe0`) is not affected by v0.25.7 — v0.25.7 lives on its own branch and is never tagged with `v0.25.6`.
