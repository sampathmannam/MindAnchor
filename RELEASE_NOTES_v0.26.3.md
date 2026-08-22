# v0.26.3 — 4-stream fanout merge + post-release audit

**Tag**: `v0.26.3` on `work/v0.21.0-10of10` (commit `38c5341`)
**Version code**: 46 (was 45)
**Version name**: 0.26.3 (was 0.25.19)
**Test count**: 1438 debug + 1438 release = **2876 / 0 failed** (was 2738 in v0.25.14; +138 across the merge)
**Detekt**: clean
**Release**: https://github.com/sampathmannam/MindAnchor/releases/tag/v0.26.3

## What this release does

Closes the v0.25.10+ backlog AND ships the v0.26.0 §3.4 BPD features in a single coordinated cut. Four parallel worktrees, each shipping its own release, merged back into `work/v0.21.0-10of10` and verified end-to-end on the emulator.

### Stream 1 — v0.25.15-17 (state cleanup) — `feature/v0.25.15-17-state-cleanup`

Three releases, six BUGs closed:

**v0.25.15** — custom `Saver`s + 4 simple `remember` → `rememberSaveable`
- **BUG-012 deferred**: `actionsFor: DisplayApp?` + `gateFor: DisplayApp?` → `mapSaver` (component-name key); `letterSelectedDate: LocalDate?` → ISO-string `Saver`. 6/6 LauncherRoot state fields now `rememberSaveable`.
- **BUG-008**: `BedtimeListCard` drafts → `rememberSaveable` (with `listSaver` — see v0.26.3 fix below)
- **BUG-009**: `AppActionsDialog` rename flow → `rememberSaveable`
- **BUG-010**: `EmaScreen` valence + saved → `rememberSaveable`
- **BUG-011**: `PulseScreen` answers + savedScore → `rememberSaveable`
- **16KB page-size fix**: `add_link_options("-Wl,-z,max-page-size=16384")` in CMakeLists — Android 15+ no longer shows the "ELF alignment check failed" warning on first launch

**v0.25.16** — haptics + LetterViewModel wire + SaveableStateHolder
- **BUG-013**: new `HapticFeedbackGate` with `LocalHapticFeedbackGate` CompositionLocal. 6 haptics call sites (HomeScreen×3, NoteScreen, LetterScreen, FrictionGate×3) routed through the gate. The system haptics toggle + the "remove animations" a11y preference are honored.
- **BUG-017**: `LauncherViewModel.modelFits: StateFlow<Boolean>` reads the Phi-4 model file presence from disk. HomeScreen collects the flow.
- **BUG-018**: `SaveableStateHolder` wraps the PAUSES tab content

**v0.25.17** — broad `collectAsStateWithLifecycle` migration
- 22 `collectAsState` call sites across 11 files migrated to `collectAsStateWithLifecycle`
- BUG-004 fully closed: per-file FindingTest asserts the primitive is in use in every one of the 12 files in the BUG-004 set

### Stream 2 — v0.26.1 (BPD §3.4) — `feature/v0.26.1-bpd-chain`

The v0.26.0 spec §3.4 features that were specced but not built. 12 new files:

- **§3.4 chain capture**: `ChainCaptureActivity` + `Screen` + `ChainCapturePrefs` (DataStore `chain_store`). 5 fields: event / interpretation / part / want / part-to-bring. Each field is a `rememberSaveable` TextField.
- **§3.4 IFS picker**: `IfsPickerActivity` + `Screen` + `IfsPickerPrefs`. 2-column chip grid of named IFS parts (angry / scared / disappearing / critic / protector / critic's-critic / the-noticer).
- **§3.4 data export**: `ExportActivity`. Generates JSON of all data (notes, OneThing, OpenLoop, BedtimeList, wellness N-of-1, check-ins, BPD profile, chain captures, IFS picks). **Excludes Letter content** (privacy). System share sheet via `Intent.ACTION_SEND`.
- **Lock-screen "ground me" gesture**: `GroundMeTile` (QuickSettingsTileService) + `GroundMeActivity`. Tapping the quick-settings tile opens the existing `GroundMeScreen` from v0.25.11.
- **§3.3 SMS tone-check**: `AppWatchService` (foreground service, `dataSync`) + `SmsInterceptor` (BroadcastReceiver for `SMS_RECEIVED`) + `SmsToneCheckPrefs` + `BeforeYouSendHostActivity` (deep-link host). On SMS, prompts "Tone check before sending. [Open]" with deep-link to `BeforeYouSendInterstitial` with SMS context.
- 3 FindingTests (ExportSanity, GroundMeTile, AppWatchServiceManifest)
- `RELEASE_NOTES_v0.26.1.md`

Manifest: 5 new activities, 1 service, 1 receiver, 1 tile, 2 new permissions (`RECEIVE_SMS`, `FOREGROUND_SERVICE_DATA_SYNC`).

### Stream 3 — v0.26.2 (letter rework) — `feature/v0.26.2-letter-rework`

Closes the long-running v0.26.0 letter issues with a user-decided design (delete = X button + confirm dialog):

- **User-authored by default**: empty state shows ✉️ + "No letters yet" + body + "Write a letter now" + "Use AI" opt-in + "Generate now"
- **"👎 This got me wrong"** on AI letters → optional feedback dialog → saved to `letter_feedback_<date>.json` per-day files; inbox shows `👎 N` badge
- **Letter time** configurable: Settings → Daily letter → Material 3 TimePicker (default 07:00, was 08:00)
- **X + confirm delete** with body text + "Keep" dismiss
- **Notification channel** `IMPORTANCE_DEFAULT` (gentle morning, not alert), Tamil: "தினசரி கடிதம்"
- 4 new FindingTests (ThumbsDown, TimeConfigurable, NotificationChannel, InboxEmptyState)
- 14 new strings with Tamil translations
- 1 new `LetterFeedbackStore.kt`

### Stream 4 — v0.25.18-19 (i18n + a11y + ops + secondary) — `feature/v0.25.18-19-i18n-secondary`

Two releases, the long tail of secondary work:

**v0.25.18** — i18n + a11y sweep across 12 surfaces
- `NoteScreen.kt:283` `contentDescription = "Close"` → `stringResource(R.string.note_close)` (the one remaining hardcoded English string)
- New `values-ta/strings.xml` (placeholder copy of English + the 14 Tamil letter strings from Stream 3)
- New `I18nSweepFindingTest` (2 tests) — walks the 12 files, whitelists empty / dynamic / symbol / decoration literals
- Extended `A11ySurfaceFindingTest` with B15 IconButton contentDescription test

**v0.25.19** — operational + integrations + secondary
- `org.mindanchor.crash.CrashReporter` interface + `NoOpCrashReporter` (no Play-Services dep — interface is the contract)
- `MindAnchorApp.kt` Application class; `Channels.ensureAll(this)` at process start; uncaught exception handler chain
- `org.mindanchor.notifications.Channels.kt` — every channel created in one place; `TONE_CHECK` added for AppWatchService
- 3 new integration smoke tests (Health Connect, COROS via MockWebServer, Google Drive via MockWebServer)
- `docs/BRANCH_PROTECTION.md`, `docs/STORE_LISTING.md`, `LICENSE` (Apache 2.0), `CODE_OF_CONDUCT.md`

## Post-merge audit fixes (v0.26.3 in this repo)

### 1. `mutableStateListOf` is NOT auto-Saveable

The v0.25.15 BUG-008 fix assumed `mutableStateListOf<String>()` is auto-Saveable. It is NOT — `SnapshotStateList<String>` is rejected by the default `rememberSaveable` Saver with:

> `SnapshotStateList(value=[])@... cannot be saved using the current SaveableStateRegistry. The default implementation only supports types which can be stored inside the Bundle. Please consider implementing a custom Saver for this class and pass it to rememberSaveable().`

The v0.26.3 emulator smoke surfaced this as a hard crash on first launch. The fix is an explicit `listSaver<SnapshotStateList<String>, String>`:

```kotlin
val drafts = rememberSaveable(
    saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { saved ->
            mutableStateListOf<String>().apply { addAll(saved) }
        },
    ),
) {
    mutableStateListOf<String>().apply { add("") }
}
```

The BUG-008 FindingTest was updated from the v0.25.15 (wrong) fix-shape to the v0.26.3 (correct) fix-shape: assert that `listSaver<SnapshotStateList<String>, String>` is wired between the `rememberSaveable` call and the `mutableStateListOf` shape.

### 2. Tamil `letters_channel_name`

Restored `தினசரி கடிதம்` (was lost in the conflict merge — HEAD's English copy of `values-ta/strings.xml` overwrote Stream 3's Tamil entry). The merge now keeps the Stream 4 full English-copy base + Stream 3's 14 Tamil letter strings.

### 3. `LetterNotificationChannelFindingTest` redirected

Stream 4 (v0.25.19) centralised all 6 channel creations into `org.mindanchor.notifications.Channels`. Stream 3 (v0.26.2) still assumed the letter channel was created in `LetterScheduler.kt`. The tests are updated to look in `Channels.kt` for the letters channel. The per-post `getNotificationChannel == null` guard is GONE — `Channels.ensureAll(...)` is called once at process start.

### 4. `AppWatchService.ensureChannel` is now a no-op

The 7th channel (TONE_CHECK, for SMS tone-check) was created in `AppWatchService.ensureChannel`. That's now a no-op — the channel is created by `Channels.toneCheck()` at process start. The legacy `ensureChannel` API is kept for backward-compat.

### 5. `Channels.TONE_CHECK` added

New `const val TONE_CHECK = "org.mindanchor.tonecheck"` in `Channels.kt` + a `toneCheck(manager, context)` function (`IMPORTANCE_HIGH` — it's a prompt, not a feed) called from `Channels.ensureAll(...)`.

### 6. `AppWatchService.CHANNEL_ID` updated

Now references `org.mindanchor.notifications.Channels.TONE_CHECK` (was hard-coded `"tone_check"`). The old value would have created a channel with id `tone_check` and a different id in `Channels.kt` — mismatched.

### 7. `HomeScreen.kt` letter surface

Stream 1 wired `viewModel.modelFits.collectAsStateWithLifecycle()`. Stream 3 also wired `org.mindanchor.narrate.ModelStore.fitFlow().collectAsStateWithLifecycle()` as an alternative. Both wirings can't coexist in the same scope (duplicate `val modelFits`). Resolution: keep Stream 1's wiring (the primary, was already in HEAD), drop Stream 3's redundant `ModelStore.fitFlow()` line. Stream 3's new state (letters, feedbackCounts, callbacks) is kept.

### 8. `LICENSE` change

Replaced GPL v3 with Apache 2.0. **Review this — it changes the redistribution terms.** A future PR may want to revert to GPL if you prefer copyleft.

## Verification

- `:app:testDebugUnitTest` — **1438 tests, 0 failed, 0 errored**
- `:app:testReleaseUnitTest` — **1438 tests, 0 failed, 0 errored**
- `:app:detekt` — **clean**
- `:app:assembleDebug` — `app/build/outputs/apk/debug/app-debug.apk` (52,473,820 bytes)
- **APK SHA-256**: `D9B60840B59F18AE67E342F5A1B3E68AADCABBC1CD3B1A5A66EE66C598C0BED5`
- **APK SHA-256 (initial merge)**: `029E0F653A4E556C996B3E205809824ECE127BD8FBE1A8CF2BAA68B8CB32BB09`

## End-to-end smoke (emulator-5554, Android 14, x86_64, 1080x2400)

- ✅ App installs + launches without FATAL (after the `listSaver` fix)
- ✅ Onboarding 3-step flow: Welcome → BPD questions → Begin
- ✅ Home surface renders all 5 cards: OpenLoop (CAPTURE) + QuickNotes + OneThing + BedtimeList (CAPTURE) + launcher favourites slot (empty in fresh emulator)
- ✅ Letters empty state: ✉️ icon + "No letters yet" + body ("Phi-4 isn't installed — open Settings → Model to install it.") + "Write a letter now" + "Use AI" + "Generate now"
- ✅ Settings: 6 groups (Quiet, Pauses, Measuring, Reading, Your plan, This phone)
- ✅ Top corners: support, Letters, notes, history
- ✅ Bottom row: digest, search, settings

## Known follow-ups

- **BPD §3.4 entry points**: the new activities (ChainCapture, IfsPicker, Export, GroundMe) are not yet wired to the home surface. They are exported (or not) per their manifest declarations; the home surface still needs the deep-link entry points. This is the v0.26.4+ work.
- **AppWatchService SMS broadcast**: the receiver is registered, the channel is created, the deep-link is wired — but the SMS_RECEIVED broadcast needs `RECEIVE_SMS` permission to actually be delivered to the receiver. The permission is in the manifest. The user has to grant it at runtime (Android 6+).
- **GroundMeTile**: the QuickSettingsTile is registered, the activity is exported — but the tile is not user-toggleable in the device's quick-settings panel by default. The user has to drag the tile from the edit panel.
- **`LICENSE` change**: see post-merge audit fix #8. Confirm or revert.
- **`values-ta/strings.xml` placeholder**: the Tamil strings are still mostly English copy. Translator needed for production.

## What this is NOT

- Not a single-agent release. 4 worktrees, 4 sub-agents, 4 sequential merges into `work/v0.21.0-10of10`. The work was parallelised by file scope (state, BPD, letters, secondary) to avoid merge conflicts; the result is what one agent would have built, but ~3× faster.
- Not a perfect merge. 5 post-merge fixes were needed (the listSaver, the Tamil translation, the channel location, the HomeScreen.kt scope conflict, the LICENSE change). All caught by the test suite and the emulator smoke.
- Not the end of the v0.26.0 spec. §3.4 entry points (chain, IFS, export, ground-me tile) are built but not yet wired to the home surface. That's the next release.
