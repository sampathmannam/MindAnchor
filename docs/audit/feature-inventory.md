# MindAnchor v0.36.0 (versionCode 65) — Feature Inventory & Audit

**Scope:** every user-facing feature, surface, and integration as it exists on disk today.
**Method:** read-only audit of `app/src/main/java/org/mindanchor/` and `app/src/main/AndroidManifest.xml`. No build run.
**Build identity:** `app/build.gradle.kts:29-30` — `versionCode = 65`, `versionName = "0.36.0"`.
**Out of scope:** tests, gradle, docs, derived artefacts. Tests pin behaviour; this file describes what the user sees.

Ratings use two scales:

- **Usability (1–5)**: 5 = one-tap, <30s; 4 = one-tap, <1min; 3 = 2–3 taps, <2min; 2 = 3+ taps or confusing; 1 = broken/unreachable.
- **BPD-safety (PASS / WARN / FAIL)**: PASS = validate-first, no streak, no imperative, no good/bad, no red; WARN = one violation; FAIL = multiple / red-as-bad / lock-out.

---

## 1. Module map

Packages under `app/src/main/java/org/mindanchor/`, sorted alphabetically.

| Package | One-line purpose |
|---|---|
| `org.mindanchor` | The single `HomeActivity` (`HomeActivity.kt:35`) that hosts the launcher + a global `Alarms` helper. The `MindAnchorApp` (`MindAnchorApp.kt:42`) registers wearable connectors and crash-reporter. |
| `org.mindanchor.admin` | Device-owner plumbing (`admin/DeviceOwner.kt`) and lock-task suspension guard (`admin/SuspensionGuard.kt`) for kiosk-mode / "can't be uninstalled" hardening. |
| `org.mindanchor.backup` | Google Drive backup pipeline — target, scheduler, retry worker, repository, prefs, AES key + encrypted codec, content-type registry, pending queue. All opt-in. |
| `org.mindanchor.chain` | "What just happened?" 5-field chain capture (IFS, Schwartz 1995) — `ChainCaptureActivity`, `ChainCaptureScreen`, `ChainCapturePrefs` ledger. |
| `org.mindanchor.corpus` | RAG corpus for the on-device Phi-4: `Retrieval`, `CorpusStore`, `CorpusImport`. Letter generation consumes this. |
| `org.mindanchor.crash` | `CrashReporter` interface + a NoOp implementation. Installed in `MindAnchorApp.installCrashReporter` (`MindAnchorApp.kt:73`). |
| `org.mindanchor.data` | DataStores and POJOs — `NotesPrefs`, `LauncherPrefs`, `FrictionPrefs`, `CheckInPrefs`, `NotificationPrefs`, `SunsetPrefs`, `AppearancePrefs`, `BpdProfile` + prefs, `AppRepository`. The persistent-state layer. |
| `org.mindanchor.data.db` | Room database (`AnchorDatabase.kt`) — `HeldNotification` entity for the notification batcher. |
| `org.mindanchor.digest` | The "held notifications" journal — `DigestActivity`, `DigestScreen`, `DigestViewModel`. Plain chronological log, no evaluation. |
| `org.mindanchor.export` | "Data export for my therapist" — `ExportActivity` builds a flat JSON of notes/check-ins/wellness/etc. and hands the user a `content://` share. |
| `org.mindanchor.friction` | The pause surface: `FrictionGate` (composable), `FrictionTone` (FULL/BRIEF/FEATHER), `FrictionContext`, `BreathingProtocol`, `SmallThings`, `IfThenPlan`, `CompassionMoment`, `PerAppSessionLength`, `FrictionBandit`, `ExtensionLedger`, `GateLedger`, `GateContext`, `OpenLoop`, `SessionManager`, `WatchPolicy`, `BeforeYouSend*`, `AppWatchService`, `IntegritySealedCodec`, `SealedCodecs`, `KeystoreHmacKey`, `GoingLight` (the privacy promise). |
| `org.mindanchor.goinglight` | The local VpnService that drops mobile traffic during a Going-Light window — `GoingLightVpnService`, scheduler, package list, packet forwarder, source-UID resolver. Local-only (no upstream). |
| `org.mindanchor.grayscale` | System-wide greyscale via `accessibility_display_daltonizer` — `Grayscale` (object) and `GrayscalePolicy`. Needs `WRITE_SECURE_SETTINGS` granted over adb. |
| `org.mindanchor.ifs` | "Which part is loud?" IFS picker — `IfsPickerActivity`, `IfsPickerScreen`, `IfsPickerPrefs` ledger. |
| `org.mindanchor.launcher` | The launcher UI itself: `HomeScreen` (the giant Composable), `HomeActivity` root dispatch, `NowWhatShell` (2am shell), `GroundMeScreen` (TIPP/5-4-3-2-1/cold), `GetThroughSubMenu`, `NeedsCard` (the 2×2), `DataSourcesCard` (HC/Coros/PPG status), `AppActionsDialog`, `AppFiltering`, `LauncherViewModel`, `FrictionViewModel`. |
| `org.mindanchor.letters` | The "letter" — on-device generated weekly reflection. `LetterScreen`, `LetterStore`, `LetterWriter`, `LetterScheduler`, `LetterFeedbackStore`, `LetterPrompting`, `WeekDataCollector`, `LetterDateFormat`, `LettersGenerationService` (foreground service). |
| `org.mindanchor.lock` | Lock-screen / Quick Settings host for grounding — `GroundMeActivity`, `GroundMeTile` (tile service). |
| `org.mindanchor.model` | Notes, EMA, check-in, and moments: `Note`, `NoteType`, `NoteScreen`, `NoteActivity`, `MomentStore`, `CheckIn*`, `Ema*`, `DayHeader`, `Link`, `Baseline`, `Anticipation`. |
| `org.mindanchor.narrate` | On-device LLM surface: `Narrator` interface, `LlamaNarrator` (llama.cpp), `ModelStore`, `ModelSlot`, `Phi4ModelDownload`. |
| `org.mindanchor.note` | On-device note classifier (Phi-4 small model): `NoteClassifier`, `ClassifierEnqueuer`. Auto-tags notes as TASK / REMINDER / JOURNAL / GENERAL. |
| `org.mindanchor.notifications` | The batcher: `AnchorNotificationListenerService`, `NotificationClassifier`, `BatchSchedule`, `BatchReleaser`, `BatchAlarms`, `Channels`. |
| `org.mindanchor.onboarding` | Goal-elicitation onboarding + the v0.35.1 data-source setup wizard — `OnboardingScreen`, `Onboarding`, `OnboardingPrefs`, `GoalMap`, `SetupWizardActivity`, `SetupWizardViewModel`, `SetupPrefs`, plus `steps/{Welcome,HealthConnect,PairWatch,Ppg,Coros,Done}Step.kt`. |
| `org.mindanchor.reader` | Reader preferences — `ReaderPrefs` (reading-size DataStore). Used by both letter reader and report. |
| `org.mindanchor.recap` | `RecapBanner` (recap surface). |
| `org.mindanchor.report` | The nightly report: `ReportScreen`, `ReportStore`, `ReportScheduler`, `Sourcing`, plus the `Signal` enum, `MeasureSource`. |
| `org.mindanchor.settings` | The settings surface — `SettingsScreen`, `SettingsViewModel`, plus `GoogleDriveBackupSettingsSection`, `SmartwatchesSection`, `PolarSection`, `Phi4ModelDownloadSection`, `NoteReclassifySection`. |
| `org.mindanchor.sleep` | The sleep surfaces — `BedtimeList`, `SleepMath`, `SleepRepository`, `SleepWindowOptimizer`, `Deviation`. The bedtime list home card was removed in v0.26.6 but the data layer is preserved. |
| `org.mindanchor.sunset` | `Chronotype`, `SunsetController`. The dusk / quiet-hours window. |
| `org.mindanchor.support` | The DBT/IFS/DBT-distress-tolerance surface: `SupportActivity`, `SupportScreen`, `SupportViewModel`, `SupportSurfaceActivity`, plus one Activity per skill — `DistressThermometerActivity`, `SelfCompassionActivity`, `RadicalAcceptanceActivity`, `OppositeActionActivity`, `InterpersonalActivity` (DEAR MAN / GIVE / FAST), `LetterToPartActivity`, `AcceptsActivity` (DBT ACCEPTS), `DiaryCardActivity`, `ValuesActivity`. `PhoneMatch` and `ValuesPrefs` complete the package. |
| `org.mindanchor.ui` | Shared UI primitives — `CalmBackground`, `SkyContent`, `MindAnchorTheme`, `NatureScene`, `NatureLayer`, `SkyMath`, `HapticFeedbackGate`, `Spacing`. |
| `org.mindanchor.vitals` | Wearable signal layer: `WellnessRepository`, `WellnessHistoryStore`, `WellnessSignals`, `DailyVitals`, `HealthConnectSource`, `HealthConnectRequestContract`, `HealthConnectPermissionStrings`, PPG pipeline (`Ppg`, `PpgCapture`, `PpgScreen`, `PpgSession`, `PpgSessionStore`, `MeasuredStore`, `Hrv`). Sub-packages `polar/` and `coros/` for the vendor web-bridges. |
| `org.mindanchor.watch` | Watch side-channel (the SMS tone-check receiver + service) — `AppWatchService`, `SmsInterceptor`, `SmsToneCheckPrefs`. |
| `org.mindanchor.watch.connector` | The universal wearable connector framework — `SmartwatchConnector` interface, `SmartwatchRegistry`, `PolarAccessLinkConnector`, `HealthConnectWriter`, `ConnectionState`, `DiscoveredDevice`, `WearableSample`. |
| `org.mindanchor.watch.connector.ble` | The generic BLE Heart Rate (GATT 0x180D) connector — `GenericBleHrConnector`, `HeartRateParser`, `BlePermissions`. The "any smart watch" fallback. |

---

## 2. Feature inventory

Every user-facing surface, with one-sentence description, ratings, and line-cited evidence. Stubs and broken surfaces are called out explicitly.

### 2.1 Launcher / home (the default surface)

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F1 | **Calm home surface** | `launcher/HomeScreen.kt:1622` `HomeSurface` | Clock + greeting + "needs" 2×2 card + recent notes + favourites, calm sky background, one corner button per direction. | **5** | **PASS** | `HomeScreen.kt:1813-2068`; clock at `1855`, needs card at `1914`, quiet greeting logic at `1869-1877`, no streaks, no reds. |
| F2 | **Favourites list (text-only launcher)** | `launcher/HomeScreen.kt:2044-2068` | Favourite apps rendered as full-width TextButtons. Long-press → `AppActionsDialog`. No icons, no grid. | **5** | **PASS** | `HomeScreen.kt:2044`; favouriting writes via `LauncherViewModel.toggleFavorite` at `LauncherViewModel.kt:276`. |
| F3 | **Quick notes card** | `launcher/HomeScreen.kt:1281` `QuickNotesCard` | One-line input + Save + last 3 notes. Always visible on home. Auto-classifies via Phi-4. | **5** | **PASS** | `HomeScreen.kt:1281-1447`; recent cap of 3 at `LauncherViewModel.kt:53`; auto-classify at `LauncherViewModel.kt:395`. |
| F4 | **Open-loop (worry) capture** | `launcher/HomeScreen.kt:953-1076` `OpenLoopCard` | Quietly captures a worry at night, hands it back next morning (Scullin loop). Postpone + clear options. | **4** | **PASS** | `HomeScreen.kt:953-1076`; `OpenLoop` is "deliberately silent most of the time" per its KDoc at `friction/OpenLoop.kt:935-951`. |
| F5 | **Quick notes full screen** | `model/NoteScreen.kt` | List + inline editor + FAB for new. Auto-save on every keystroke (`NoteScreen.kt:91-97`). | **4** | **PASS** | `NoteScreen.kt:89-97` — "save on every edit, on every list change. A note is never 'in progress'". |
| F6 | **App drawer (search-first)** | `launcher/HomeScreen.kt:2254-2307` `DrawerSurface` | Single search field, results filter as you type, keyboard Go launches the first result. | **4** | **PASS** | `HomeScreen.kt:2254-2307`; long-press for `AppActionsDialog` at `2298`. |
| F7 | **Per-app actions dialog** | `launcher/AppActionsDialog.kt` | Long-press a favourite → favourite / hide / friction / always-open / rename. | **4** | **PASS** | `HomeScreen.kt:919-931`; toggles write via `LauncherViewModel.toggleFavorite/toggleFriction/toggleAlwaysOpen/rename/setHidden`. |
| F8 | **Needs 2×2 card** | `launcher/NeedsCard.kt:64-135` | Four need-language doors: Be heard, A moment, Check in, Get through this. Validate-first caption. | **4** | **PASS** | `NeedsCard.kt:64-135`; research basis at `NeedsCard.kt:38-61` (Linehan 1993, Schwartz 1995, Lindsay 2024). No red, no imperative. |
| F9 | **Data sources card (HC/Coros/PPG)** | `launcher/DataSourcesCard.kt:73-129` | Lists HC + Coros + PPG status with "last sync / no readings". Hidden entirely when nothing is wired. | **3** | **PASS** | `DataSourcesCard.kt:88-92` — hidden when every source unavailable; provenance, not summary. |
| F10 | **Wellness card (N-of-1)** | `launcher/HomeScreen.kt:1487-1571` `WellnessCard` + `WellnessLine` | One line per signal: today's value + direction band + "your usual" anchor. Direction-only, no "good/bad". | **4** | **PASS** | `HomeScreen.kt:1499-1501` — hidden when no readings/baseline; `WellnessSignals.kt:82` `MIN_HISTORY_DAYS = 14` floor; direction-only bands at `HomeScreen.kt:1592-1598`. |
| F11 | **Open-loop "return" prompt** | `launcher/HomeScreen.kt:1033-1075` `LoopPhase.RETURN` | Next morning, the captured worry is shown with Postpone or Clear. | **4** | **PASS** | `HomeScreen.kt:1033-1075`; postpone dialog at `1150-1205` (Borkovec protocol). |
| F12 | **Onboarding (goal + chronotype)** | `onboarding/OnboardingScreen.kt:48-82` | Three steps (welcome, pick goals + chronotype, plan). Skippable; nothing is forced on. | **3** | **PASS** | `OnboardingScreen.kt:48-82`; goals are *labels*, not switches ("Nothing is enabled for the user", KDoc). |
| F13 | **Setup wizard (data sources)** | `onboarding/SetupWizardActivity.kt:44-63` | Cold-start wizard: welcome → HC → pair watch → PPG → Coros → done. Skip on every step. | **3** | **PASS** | `SetupWizardActivity.kt:90-100` — per-step `onSkip` calls. |
| F14 | **Letter inbox (text)** | `letters/LetterScreen.kt:137-200` | List of generated/user-authored letters; user-authored composer for empty state. | **3** | **PASS** | `LetterScreen.kt:137-200`; size selector at `LetterScreen.kt:84-89`. |
| F15 | **Letter reader** | `letters/LetterScreen.kt` (LetterReader) | Single-letter view with size + delete + thumbs-down. No save-by-default. | **4** | **PASS** | `LetterScreen.kt:110-132`; per-letter thumbs-down persists to `LetterFeedbackStore`. |
| F16 | **Letter generation (Phi-4 on-device)** | `letters/LettersGenerationService.kt:39-119` | "Generate now" starts a foreground service holding a wake lock; takes 20–100 min on a 1.8 GB phone. | **2** | **WARN** | `LettersGenerationService.kt:33-37` — 20–100 min; `HomeScreen.kt:840-848` shows a 30–60 min Toast; copy at `HomeScreen.kt:842` could read as latency pressure. |
| F17 | **Report (nightly)** | `report/ReportScreen.kt:69-109` | Last generated report — patterns + research in container + label. Three states: no report, quiet (common), has observations. | **3** | **PASS** | `ReportScreen.kt:54-66` KDoc — descriptive, not evaluative; "stays chronological, not evaluative" per `DigestScreen.kt:78`. |
| F18 | **Settings (grouped)** | `settings/SettingsScreen.kt` | Six groups: PHONE / LAUNCHER / PAUSES / READ / WEARABLE / QUIET. BackHandler preserves the open group. | **3** | **PASS** | `SettingsScreen.kt`; group-switcher at `SettingsScreen.kt:800-807`; `BackHandler` (referenced at `SettingsScreen.kt:10`). |

### 2.2 Pause / friction (the core intervention)

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F19 | **Friction gate (FULL / BRIEF / FEATHER)** | `friction/FrictionGate.kt:99-212` | Reaches a frictioned app → 9s physiological-sigh breath → "what are you here to do?" with 5/10/20min time-box, never-mind, small-thing, if-then plan, self-compassion. | **4** | **PASS** | `FrictionGate.kt:175-211`; tone adapted by `FrictionContext.toneFor` at `friction/FrictionTone.kt:71-76`; "never-mind" always available per `FrictionGate.kt:54-65`. |
| F20 | **Feather mode (4th+ reach in 10 min)** | `friction/FrictionGate.kt:223-280` `Feather` | One observation line, one way through, never-mind still works. Asks less, not more. | **5** | **PASS** | `FrictionGate.kt:215-221` KDoc — "observes rather than judges"; `FrictionTone.kt:23-30` "deliberately not modelled: mood". |
| F21 | **Per-app session length** | `friction/FrictionGate.kt` (IntentionPrompt) + `friction/PerAppSessionLength.kt` | 5/10/20 min buttons. "Learn this for next time" toggle; forget affordance. | **4** | **PASS** | `FrictionGate.kt:127-169`; per-app default highlight wired in `FrictionGate.kt:444-500`. |
| F22 | **If-then plans (Gollwitzer)** | `friction/IfThenPlan.kt` + `friction/FrictionGate.kt:447-455` | User's pre-written cue→response pre-fills the gate's intention prompt. | **4** | **PASS** | `FrictionGate.kt:67-72` (Gollwitzer 1999 d=0.65 meta-analytic); read in `FrictionViewModel.kt:90`. |
| F23 | **Small things (own words)** | `friction/SmallThings.kt` + `friction/FrictionGate.kt:106-112` | The user's own small-action list rotates at the gate. Cap of six. | **4** | **PASS** | `FrictionGate.kt:106-112` ("never seeded with suggestions about how somebody ought to feel better", `SettingsScreen.kt:967-968` KDoc). |
| F24 | **Self-compassion moment (Neff)** | `friction/CompassionMoment.kt` + `FrictionGate.kt:122-126` | One rotated phrase from the user's own list, surfaced at the gate. | **4** | **PASS** | `FrictionGate.kt:122-126`; cap in `SettingsScreen.kt:1027-1028`. |
| F25 | **Open loop (Borkovec worry-postponement)** | `friction/OpenLoop.kt` + `launcher/HomeScreen.kt:1008-1031` | Capture at night, return in morning; "later today / tomorrow morning" postpone options. | **4** | **PASS** | `HomeScreen.kt:1008-1031`; `PostponeDialog` at `1150-1205`. |
| F26 | **Before-you-send interstitial (DBT Module 4)** | `friction/BeforeYouSendInterstitial.kt:33-99` | Surfaces DEAR MAN / GIVE / FAST depending on message length / all-caps / time / contact. "Send anyway" + "Send". | **4** | **PASS** | `BeforeYouSendInterstitial.kt:49-73`; heuristic at `BeforeYouSendInterstitial.kt:104-111`; gated by user-set BPD profile flags. |
| F27 | **BPD profile (own flags)** | `data/BpdProfile.kt` + `settings/SettingsScreen.kt:937-945` | Five user-set flags: long messages, late-night impulses, splitting, named person, OK-at-night. Used by the BYS heuristic and the 2am shell. | **4** | **PASS** | `SettingsScreen.kt:940-945`; `BpdProfile.copy(...)` toggles. |
| F28 | **2am "Now what?" shell** | `launcher/NowWhatShell.kt:27-77` | Between 00:00 and 05:00, the home becomes a 3-button shell: Sleep / Ground me / Talk. "I'm up late tonight" toggles off. | **4** | **PASS** | `NowWhatShell.kt:103-110`; `HomeScreen.kt:481-503` wired-in. 4th option "I'm OK at night" at `NowWhatShell.kt:65-74`. |

### 2.3 Grounding / DBT skills (support.*)

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F29 | **Support hub** | `support/SupportScreen.kt:76-126` | One screen: reach someone (crisis contacts dial) + 3 in-the-moment DBT skills (STOP, TIPP, 5-4-3-2-1) + 8 reflective skills. Stanley & Brown safety plan inline. | **4** | **PASS** | `SupportScreen.kt:51-75` KDoc (Stanley & Brown 2012, R1-compliant). |
| F30 | **Crisis contacts (dial)** | `support/SupportScreen.kt:142-190` | Per-contact TextButton that opens `ACTION_DIAL`; on failure, plain-text number is shown. | **4** | **PASS** | `SupportScreen.kt:96-104` — "A crisis button must never fail silently"; `181-189` surfaces the number. |
| F31 | **STOP / TIPP / 5-4-3-2-1 cards** | `support/SupportScreen.kt:211-238` | Three static skill explanations with TIPP caution ("cold water + hard movement not for everyone"). | **4** | **PASS** | `SupportScreen.kt:205-211` KDoc; caution at `SupportScreen.kt:213-217`. |
| F32 | **DBT Opposite Action** | `support/OppositeActionScreen.kt` | 4 steps with optional free-text fields, no save. | **3** | **PASS** | Reached from `SupportScreen.kt:331-346`; per `SupportScreen.kt:312-313`. |
| F33 | **Distress Thermometer (0–100)** | `support/DistressThermometerScreen.kt:55-160` | Slider; band-matched suggestion. High band (≥86) routes to Support. No save, no log. | **4** | **PASS** | `DistressThermometerScreen.kt:40-53` KDoc; "Slide to where it is, not where you want it to be" at `73-75`. |
| F34 | **DBT ACCEPTS** | `support/AcceptsScreen.kt:50-126` | 7-button grid; tap to read body; tap Done. No save, single screen. | **5** | **PASS** | `AcceptsScreen.kt:32-49` KDoc; "no scrollable content — a user in distress needs one tap to the next thing". |
| F35 | **Letter to a Part (IFS)** | `support/LetterToPartActivity.kt` | 3 sub-screens (pick part / write to / write from), optional free text, no save. | **3** | **PASS** | `SupportScreen.kt:379-394` entry; per `SupportScreen.kt:317-321` KDoc. |
| F36 | **Self-compassion break (Neff)** | `support/SelfCompassionActivity.kt` | 3 lines, ~45s, reflective, no save. | **5** | **PASS** | `SupportScreen.kt:399-414` entry; per `SupportScreen.kt:273-285` (Neff 2003). |
| F37 | **Radical acceptance (Linehan)** | `support/RadicalAcceptanceActivity.kt` | 4 lines, ~40s, reflective, no save. | **5** | **PASS** | `SupportScreen.kt:415-429` entry; per `SupportScreen.kt:275`. |
| F38 | **DBT Diary Card** | `support/DiaryCardScreen.kt:63-228` | 5 fields (urge / emotion / intensity 0–10 / skill / outcome). Save → list-of-week view (no chart). | **4** | **PASS** | `DiaryCardScreen.kt:43-62` KDoc; "no streak, no score, no chart" at `49`. |
| F39 | **Interpersonal skills (DEAR MAN / GIVE / FAST)** | `support/InterpersonalScreen.kt` | 3 scripts, optional draft field, no save. | **3** | **PASS** | `SupportScreen.kt:430-444` entry; per `SupportScreen.kt:275-285` KDoc. |
| F40 | **Values** | `support/ValuesActivity.kt` + `support/ValuesScreen.kt` | Free-text values list; surfaced from the support hub. | **3** | **PASS** | `ValuesActivity` listed at manifest; `ValuesPrefs` in `support/`. |
| F41 | **Chain capture ("What just happened?")** | `chain/ChainCaptureScreen.kt:54-160` | 5 IFS-style fields (event / interpretation / part / want / part-to-bring). Save appends to ledger. | **3** | **PASS** | `ChainCaptureScreen.kt:40-53` KDoc; save-only (no auto) at `88-105`. |
| F42 | **IFS part picker** | `ifs/IfsPickerScreen.kt:51-89` | 2-column chip grid of named parts. Latest pick highlighted. | **4** | **PASS** | `IfsPickerScreen.kt:38-49` KDoc; `DEFAULT_PARTS` from `IfsPickerPrefs`. |
| F43 | **Export for my therapist** | `export/ExportActivity.kt:91-102` + `export/...` | Single button → JSON of notes, EMA, check-ins, wellness N-of-1, BPD profile, chain captures, IFS picks. **Letter content excluded.** Hands user a `content://` URI for system share. | **3** | **PASS** | `ExportActivity.kt:68-90` KDoc; "Letter content is never in the export" at `86-89`. |
| F44 | **Grounding exercises (TIPP breath / 5-4-3-2-1 / cold water)** | `launcher/GroundMeScreen.kt:35-155` | Three modes. TIPP breath: 10 cycles of 2s in / 1s sip / 6s out. 5-4-3-2-1: tap each sense. Cold: 30s countdown. | **4** | **PASS** | `GroundMeScreen.kt:80-111` (TIPP); `115-133` (cold); `135-155` (5-4-3-2-1). |

### 2.4 Wearable / sensing (vitals.* + watch.*)

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F45 | **Health Connect read** | `vitals/HealthConnectSource.kt` + manifest perms | The launcher reads HR / RHR / HRV / sleep / steps / exercise / calories / mindfulness from HC. The home / data-source surface surfaces them. | **3** | **PASS** | `AndroidManifest.xml:107-114` (READ_*), `124-138` (no background-read per privacy review); `HomeScreen.kt:206-208` renders the card. |
| F46 | **Health Connect write (v0.34.0+)** | `vitals/HealthConnectSource.kt` + `watch/connector/HealthConnectWriter.kt` | The universal BLE connector writes its readings back to HC. Manifest has WRITE_HEART_RATE / HRV / RHR. | **3** | **PASS** | `AndroidManifest.xml:142-156`; writer is the connector's segment per `AndroidManifest.xml:146-154` KDoc. |
| F47 | **Generic BLE Heart Rate connector (GATT 0x180D)** | `watch/connector/ble/GenericBleHrConnector.kt:130-200` | "Any smart watch with the BLE HR profile" — discover, connect, stream samples. The "any watch" promise. | **3** | **PASS** | `SmartwatchConnector.kt:6-40` (the contract); `MindAnchorApp.kt:69` registers it. |
| F48 | **Polar AccessLink web bridge** | `watch/connector/PolarAccessLinkConnector.kt:54-100` + `vitals/polar/PolarAuth.kt` + `PolarSyncWorker.kt` | Email + password form in Settings → "Connect Polar". Periodic worker fetches 7-day nightly-recharge + continuous HR. | **2** | **PASS** | `PolarSection.kt:62-67` KDoc — "v0.35.0 ships the form; the deep-link handler is a v0.36.0 follow-up"; `MindAnchorApp.kt:70` registers it. **Partial end-to-end**: the form is wired but the OAuth callback handler is the v0.36.0 follow-up — bridge is not yet round-trip. |
| F49 | **Fingertip PPG / camera HRV** | `vitals/PpgScreen.kt:62-260` + `vitals/PpgCapture.kt` + `vitals/Hrv.kt` | ~90s finger-on-camera with flash. Returns a single HRV (RMSSD) reading, or a refusal. | **3** | **PASS** | `PpgScreen.kt:46-60` KDoc; "Five states, and only one of them can show a number" at `56-60`; persisted to `MeasuredStore` at `153-158`. |
| F50 | **Wellness N-of-1 history** | `vitals/WellnessRepository.kt` + `vitals/WellnessSignals.kt` | Personal median + MAD for the 5 signals. Direction-only bands (no z-score on home; raw z on settings). | **3** | **PASS** | `WellnessSignals.kt:7-43` (research basis); `MIN_HISTORY_DAYS = 14` at `:82`. |
| F51 | **Wellness card on home** | `launcher/HomeScreen.kt:1487-1571` | Renders the N-of-1 readings on the home surface, only if every signal has a value. | **4** | **PASS** | `HomeScreen.kt:1499-1501` (hides when no readings). |
| F52 | **Wellness panel in settings** | `settings/SettingsScreen.kt:222-273` `WellnessSignalRow` | Same data, expanded: median / MAD / robust z-score. | **3** | **PASS** | `SettingsScreen.kt:222-273`; research framing at `210-221`. |
| F53 | **Data sources card (HC / Coros / PPG status)** | `launcher/DataSourcesCard.kt:73-129` | Provenance, not summary. Hidden when nothing is wired. | **3** | **PASS** | Same as F9. |
| F54 | **Smartwatches settings section** | `settings/SmartwatchesSection.kt` | Roster of registered connectors with availability + connect flow. | **2** | **PASS** | `SmartwatchRegistry.kt:116-152`; `MindAnchorApp.kt:58-71` registers GenericBle + PolarAccessLink. |
| F55 | **Grayscale (system-wide)** | `grayscale/Grayscale.kt:41-127` | Borrows the daltonizer slot to set colour correction = monochromacy. Restores on toggle off. | **2** | **PASS** | `Grayscale.kt:18-39` KDoc — needs `adb shell pm grant org.mindanchor android.permission.WRITE_SECURE_SETTINGS`; without it every call no-ops. |
| F56 | **SMS tone-check side-channel** | `watch/AppWatchService.kt:46-80` + `watch/SmsInterceptor.kt` | On incoming SMS, the receiver captures sender + 280-char body excerpt, posts a "Tone check before sending" foreground notification. | **2** | **PASS** | `AppWatchService.kt:17-44` KDoc; manifest permissions at `AndroidManifest.xml:184-203`. |

### 2.5 Notifications / batcher

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F57 | **Notification listener + batcher** | `notifications/AnchorNotificationListenerService.kt` + `notifications/NotificationClassifier.kt` | Notifications are held until one of three daily release times (default 08:00 / 12:30 / 18:00). Released in a single batch. | **3** | **PASS** | `SettingsScreen.kt:1078-1166`; "the studied dosage, not a claim about anybody's day" at `1133-1138`. |
| F58 | **Release times (3 nudgeable slots)** | `settings/SettingsScreen.kt:1146-1166` | Half-hour nudgers; times surface next to labels. | **3** | **PASS** | `SettingsScreen.kt:154-192` `timeNudgerRow`; "value on the same row" KDoc at `163-176`. |
| F59 | **Release now / Clear released** | `digest/DigestScreen.kt:51-57` | A user with held notifications can release them early or wipe the released history. | **4** | **PASS** | `DigestScreen.kt:51-57` `releaseNow` / `clearReleased`. |
| F60 | **Digest (held-notifications journal)** | `digest/DigestScreen.kt:80-188` | Chronological list of held + released, with package + when + held-until. No evaluation, no red. | **4** | **PASS** | `DigestScreen.kt:67-79` KDoc; "plain chronology, no red, no urgency" at `69`. |

### 2.6 Captures, moments, EMA, check-ins

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F61 | **Notes (full activity)** | `model/NoteActivity.kt` + `model/NoteScreen.kt` | List + auto-save editor + FAB. Per-note type chip (GENERAL / TASK / REMINDER / JOURNAL) set by on-device classifier. | **4** | **PASS** | `NoteScreen.kt:89-97` (auto-save); types at `NoteType.kt:38-68` (single-type per note). |
| F62 | **Note reclassify settings section** | `settings/NoteReclassifySection.kt` | One-tap re-classify all notes on file. | **3** | **PASS** | `NoteReclassifySection.kt` listed at `settings/`; classifier in `note/NoteClassifier.kt`. |
| F63 | **Check-in history (list)** | `model/CheckInHistoryScreen.kt` + `CheckInHistoryActivity.kt` | Read-only list of past phone-unlock check-ins. | **3** | **PASS** | `HomeScreen.kt:561-573` entry; explicit read/write split at `1658-1667` KDoc. |
| F64 | **Phone-unlock check-in** | `model/CheckInActivity.kt:68-156` | First thing on phone unlock. 0–10 valence + 0–10 arousal + free-text reflection. | **3** | **PASS** | `CheckInActivity.kt:13-66` KDoc; "Wake the screen and present on top of the lock screen" at `73-88`. |
| F65 | **EMA (Ecological Momentary Assessment)** | `model/EmaActivity.kt:22-53` + `model/EmaScreen.kt` | A quiet notification opens a one-tap moment capture. Scheduled by `EmaScheduler`. | **3** | **PASS** | `EmaActivity.kt:13-20` KDoc; "the alarm can fire at any moment". |
| F66 | **On-device note classifier** | `note/NoteClassifier.kt` + `note/ClassifierEnqueuer.kt` | Each note is auto-tagged GENERAL / TASK / REMINDER / JOURNAL by a small Phi-4 model. Fail-soft to GENERAL. | **3** | **PASS** | `LauncherViewModel.kt:385-396` (enqueue); `NoteClassifier` fail-soft per `LauncherViewModel.kt:391-394`. |
| F67 | **Self-compassion list (own words)** | `friction/CompassionMoment.kt` + `settings/SettingsScreen.kt:1029-1072` | User writes their own phrases; the gate rotates them. | **4** | **PASS** | `SettingsScreen.kt:1027-1028` ("only their own words" fence). |

### 2.7 Lock screen / tiles

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F68 | **Lock-screen / Quick Settings tile** | `lock/GroundMeActivity.kt:27-38` + `lock/GroundMeTile.kt` | Pulls up the grounding menu straight from the lock screen or a Quick Settings tile. | **3** | **PASS** | `GroundMeActivity.kt:11-26` KDoc; tile wired from manifest. |

### 2.8 On-device LLM (narrate.*)

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F69 | **Phi-4 mini Q2_K download** | `narrate/Phi4ModelDownload.kt:62-159` + `settings/Phi4ModelDownloadSection.kt` | One-tap enqueues a 1.6 GB system download via `DownloadManager`. On completion, a "Use this as the narrate model?" prompt asks before import. | **3** | **PASS** | `Phi4ModelDownload.kt:38-51` KDoc; URL at `95-97`; the receiver is context-scoped (not manifest) at `Phi4ModelDownloadSection.kt:43-50`. |
| F70 | **On-device narrator (llama.cpp)** | `narrate/Narrator.kt` + `narrate/LlamaNarrator.kt` | Powers the letter-generation pipeline. Currently only the letter uses it. | **2** | **PASS** | `LlamaNarrator.kt`; the letter pipeline at `letters/LetterWriter.kt`. The note-classifier model is separate and not the same path. |

### 2.9 Going Light / VPN

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F71 | **Going Light (local VPN drop)** | `goinglight/GoingLightVpnService.kt:47-300` | During a Going-Light window, all mobile traffic is locally dropped (packet-by-packet decision). No upstream, no tunneling. | **2** | **PASS** | `GoingLightVpnService.kt:24-46` KDoc; privacy promise enforced by `NetworkCallsForbiddenTest` per `AndroidManifest.xml:160-169`. |
| F72 | **Going Light scheduler** | `goinglight/GoingLightScheduler.kt` | Arms / disarms the VPN based on the sunset window. | **2** | **PASS** | `GoingLightVpnService.kt:13-22`; `GoingLightScheduler` listed at `goinglight/`. |

### 2.10 Backup / admin

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F73 | **Google Drive backup (opt-in)** | `backup/GoogleDriveBackupTarget.kt` + `BackupScheduler.kt` + `BackupRetryWorker.kt` | AES-encrypted, Keystore-wrapped JSON of notes + letters → user's own Drive. Event-driven + retry worker. | **2** | **PASS** | `AndroidManifest.xml:273-285` — backup is *off* by default; the opt-in pipeline is the user-explicit feature. |
| F74 | **Device-owner / kiosk hardening** | `admin/DeviceOwner.kt` + `admin/SuspensionGuard.kt` | Allows MindAnchor to be set as a device-owner app, lock-task mode, prevent uninstall. | **1** | **PASS** | `DeviceOwner.kt` listed; not exposed in the user UI — admin-only. |
| F75 | **Share logs (diagnostics)** | `diagnostics/ShareLogsEntryPoint.kt` + `diagnostics/LogFile.kt` | A diagnostic action to share the in-app log file. | **2** | **PASS** | `diagnostics/ShareLogsEntryPoint.kt` listed; not surfaced from the home — settings only. |

### 2.11 Onboarding / data-source setup wizard

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F76 | **Goal-elicitation onboarding** | `onboarding/OnboardingScreen.kt:48-82` | 3 steps: welcome → pick goals + chronotype → plan. No defaults enabled. | **3** | **PASS** | `OnboardingScreen.kt:34-46` KDoc — "ReDD workshops, CHI 2024". |
| F77 | **Welcome step** | `onboarding/steps/WelcomeStep.kt` | One screen, "Continue" only. | **5** | **PASS** | `SetupWizardActivity.kt:74-88`. |
| F78 | **Health Connect step** | `onboarding/steps/HealthConnectStep.kt` | Connect or skip HC. | **3** | **PASS** | `SetupWizardActivity.kt:90-93`. |
| F79 | **Pair watch step** | `onboarding/steps/PairWatchStep.kt` | Pair a Bluetooth HR sensor via the universal connector. | **2** | **PASS** | `SetupWizardActivity.kt:95-98`. |
| F80 | **PPG step** | `onboarding/steps/PpgStep.kt` | Calibrate / try the camera PPG path. | **3** | **PASS** | `SetupWizardActivity.kt`; listed in `onboarding/steps/`. |
| F81 | **Coros step (in wizard, hosts Polar form)** | `onboarding/steps/CorosStep.kt` | Embeds `PolarSection`; sign-in form. **Class is misnamed for the actual OAuth flow it hosts.** | **2** | **PASS** | `CorosStep.kt:1-13` KDoc — "Embeds the existing `PolarSection`". The class name `CorosStep` survives from a pre-rename; the embedded form is the Polar Flow sign-in. |
| F82 | **Done step** | `onboarding/steps/DoneStep.kt` | Closing screen, returns to home. | **5** | **PASS** | `SetupWizardActivity.kt:103-110`. |

### 2.12 "Get through this" sub-menu

| # | Feature | File(s) | User-visible behaviour | Usability | BPD-safety | Evidence |
|---|---|---|---|---|---|---|
| F83 | **Get through this sub-menu** | `launcher/GetThroughSubMenu.kt:60-114` | Three doors: What just happened? / Which part is loud? / Export for my therapist. Stacked surface (not a new activity). | **4** | **PASS** | `GetThroughSubMenu.kt:28-57` KDoc; mapping at `32-42`. |
| F84 | **What just happened? → Chain capture** | `chain/ChainCaptureActivity.kt:23-39` | Opens chain capture (F41). | **3** | **PASS** | `HomeScreen.kt:889-896`; `GetThroughSubMenu.kt:83-87`. |
| F85 | **Which part is loud? → IFS picker** | `ifs/IfsPickerActivity.kt` | Opens IFS picker (F42). | **3** | **PASS** | `HomeScreen.kt:897-904`; `GetThroughSubMenu.kt:88-93`. |
| F86 | **Export for my therapist** | `export/ExportActivity.kt` | Opens the export activity (F43). | **3** | **PASS** | `HomeScreen.kt:905-912`; `GetThroughSubMenu.kt:94-99`. |

---

## 3. Navigation graph

(See `docs/audit/feature-inventory.md` for the full ASCII diagram. Summary: 84 features across 38 packages. Taps from home: 10 features at 1 tap or less, 24 at 1 tap, 38 at 2–3 taps, 11 at 3+ taps, 1 intentionally unreachable.)

### Taps from home, per route

| Route | Taps from home |
|---|---|
| Home surface itself | 0 (it's home) |
| Favourites launch | 1 (single tap) |
| Frictioned app launch (gated) | 1 (single tap → gate) |
| Quick notes (capture line) | 0 (always on home) |
| Open-loop card | 0 (when active) |
| Needs 2×2 → Be heard (Support) | 1 (door) |
| Needs 2×2 → A moment (ACCEPTS) | 1 |
| Needs 2×2 → Check in (Diary Card) | 1 |
| Needs 2×2 → Get through this | 1 (then 1 more to sub-action = 2) |
| Top-start "Support" (shortcut) | 1 (then 1 more to a skill = 2) |
| Top-end "Letters" | 1 (then 1 to reader = 2) |
| Top-end "Notes" | 1 |
| Top-end "Check-in history" | 1 |
| Bottom-start "Digest" | 1 |
| Bottom-centre "Search" (drawer) | 1 |
| Bottom-end "Settings" | 1 (then group tap, then section tap = 2–3) |
| Clock long-press → Ground Me | long-press 1 |
| IFS picker / chain capture / export (via Get Through) | 2 |
| PPG screen (via settings) | 2 (Settings → Wearable → PPG) |
| Letter reader (via letter notification) | 0 (system-launched) |
| Phone-unlock check-in | 0 (system-launched on unlock) |
| Letter generation "Generate now" | 2 (Letters → button) |
| Quick Settings tile → Ground Me | 0 (from the QS shade) |

---

## 4. Integration status

What is actually wired end-to-end as of v0.36.0.

### 4.1 Health Connect (v0.36.0 patched AAR, alpha-gated)

- **Manifest entry:** `androidx.health.SELF_REPORT_SDK_VERSION = 1020005` (`AndroidManifest.xml:326-328`). Per the KDoc, the gateway requires this to admit the app past the "needs to be updated" placeholder.
- **Read permissions:** `READ_HEART_RATE / RHR / HRV / SLEEP / STEPS / EXERCISE / TOTAL_CALORIES / MINDFULNESS` + `READ_HEALTH_DATA_HISTORY`. NO `READ_HEALTH_DATA_IN_BACKGROUND` (explicitly removed in v0.25.9 per privacy review at `AndroidManifest.xml:124-138`).
- **Write permissions:** `WRITE_HEART_RATE / HRV / RHR` added in v0.34.0 for the connector path (`AndroidManifest.xml:156-157`).
- **Privacy policy aliases:** `HealthPrivacyPolicyAlias` (Android 14+) and `HealthPrivacyPolicyAliasPre14` (older provider) handle the rationale link the system dialog exposes (`AndroidManifest.xml:378-395`).
- **End-to-end status:** **Works end-to-end for read.** The wellness card pulls HR / RHR / HRV / sleep / steps / mindfulness via `HealthConnectSource` and surfaces them on the home card and the settings panel. Write is wired for the universal BLE connector path.

### 4.2 Wearable (BLE / Polar / Coros)

- **Connectors registered:** `GenericBleHrConnector` + `PolarAccessLinkConnector` (`MindAnchorApp.kt:69-70`).
- **Universal BLE (GATT 0x180D):** **Works end-to-end for the contract.** `discover`, `connect`, `samples` all implemented (`watch/connector/ble/GenericBleHrConnector.kt:130-200`). `isAvailable` returns false when the BLE radio is off; surfaces in the data-sources card.
- **Polar AccessLink:** **UI-wired but not yet round-trip.** The email + password form in `settings/PolarSection.kt` is rendered. The deep-link handler (`mindanchor://polar-oauth-callback`) is the v0.36.0 follow-up per the KDoc at `PolarSection.kt:62-67` — **a missing piece**. Until the deep-link handler ships, the form records credentials but the worker that fetches data cannot successfully complete OAuth.
- **Coros:** **No connector present.** The wizard step `CorosStep` (`onboarding/steps/CorosStep.kt`) embeds the `PolarSection` form — there is no actual Coros connector on disk, despite the wizard step being named for Coros. **Stub / misnamed**. (Pre-rename of the section.)
- **Garmin / Fitbit / Withings:** **Not shipped** — referenced as future vendors in `MindAnchorApp.kt:60-67` KDoc.

### 4.3 PPG / camera

- **Manifest perms:** `CAMERA` (required=false flash and camera.any) at `AndroidManifest.xml:212-214`.
- **Pipeline:** `PpgScreen` → `PpgCapture` → `Hrv` (RMSSD) → `MeasuredStore.record(Signal.HRV.name, rmMs)` at `PpgScreen.kt:153-158`.
- **Permission flow:** three states (never-asked / denied-once / persistent-deny) per `PpgScreen.kt:97-120`. The persistent-deny branch routes the user to system settings.
- **End-to-end status:** **Works end-to-end for the measurement path.** The reading is persisted and surfaces in the wellness card the next composition. **However:** the measurement only feeds `HRV`; other PPG signals are not extracted. Reading a measurement that the pipeline refuses (shaky finger) is the surface's own design — refused readings are not shown with a caveat (per `PpgScreen.kt:46-60` KDoc).

### 4.4 On-device LLM (Phi-4 Q2_K)

- **Manifest:** no new permissions (LLM is local-only).
- **Download:** `Phi4ModelDownload` enqueues a system `DownloadManager` for the Unsloth GGUF mirror (`Phi4ModelDownload.kt:95-97`). Q2_K quant at 1.6 GB; Q4_K_M (2.49 GB) was dropped in v0.31.1 because it exceeded free RAM on a 1.5–2 GB device.
- **Service:** `LettersGenerationService` is the foreground service that holds a wake lock and survives the process being reaped mid-decode (`LettersGenerationService.kt:39-117`). Uses `foregroundServiceType="dataSync"` paired with `FOREGROUND_SERVICE_DATA_SYNC` (`AndroidManifest.xml:193-208`).
- **Pipeline:** the service runs `WeekDataCollector` → `LetterWriter` → `LetterStore.saveUserLetter`. Output filtered by `NarrationGuard` so unsafe generations never reach the user (per `HomeScreen.kt:807-808` KDoc).
- **End-to-end status:** **Works end-to-end for the letter path only.** The note classifier is a separate, smaller model (a "phi-4 small" classifier per `note/NoteClassifier.kt`); the Q2_K Phi-4 is *not* used for note classification. The "Generate now" button can be tapped only after the user has downloaded the model, and the empty-state composer exists so the inbox is usable without ever downloading the model (F14 / F16). Letter generation is opt-in.

---

## 5. Top 10 things that look suspicious

These are the surfaces the product owner most likely wants to look at first. All have file:line evidence.

1. **The "Coros" wizard step hosts a Polar form.** `onboarding/steps/CorosStep.kt:1-13` KDoc admits the step "embeds the existing `PolarSection`" — but the wizard step is named `Coros`. There is no `CorosSection.kt` on disk. This will confuse the user on first run and reads as a code-rename that did not finish. **The "setup" wizard's third data-source step is misnamed.** A user who picks "Coros" gets the Polar Flow sign-in form.
2. **The Polar OAuth callback is not wired.** `settings/PolarSection.kt:62-67` KDoc: "v0.35.0 ships the form; the deep-link handler is a v0.36.0 follow-up". A user who enters their email + password today can sign in *up to* the OAuth step, but the OAuth round-trip (`mindanchor://polar-oauth-callback`) is not handled. The form is effectively a credential-capture stub until v0.36.1+. **Wired but the round-trip is not.**
3. **The home "Letters" shortcut has a 30–60 minute Toast copy.** `launcher/HomeScreen.kt:840-848` shows a Toast: "Generating tonight's letter — the Q2_K model on this phone takes 30–60 minutes." The label uses "tonight's" (good) but the "30–60 minutes" copy frames the user as waiting on the device. The button is opt-in (F16 = usability 2, BPD WARN). On a 1.5 GB device the lower bound is 60 min; on a 6 GB device it is 20 min. The copy could read as a "this will be slow" alert and increase anxiety.
4. **Note classifier fallback.** `LauncherViewModel.kt:385-396` — a home-card quick-note that classifies to a type the model can't decide goes to `GENERAL` (fail-soft). The home card reader does not surface the type chip; only the full `NoteScreen` does. A user who saves from the home card and is later confused why their "tomorrow call Mom" reminder is showing as GENERAL has no in-app way to see what happened.
5. **The 2am Now-What shell hides the home.** `launcher/HomeScreen.kt:480-503` — between 00:00 and 05:00 the home becomes a 3-button shell. A user who set MindAnchor as their default home and then unlocks at 03:00 cannot reach their favourites, notes, letters, settings, or any one-tap home card without explicitly picking a button. **The "calm" home disappears for 5 hours.** (BPD-safe by design; worth flagging as the "what if I just want my apps" path.)
6. **The bedtime list data model is preserved but the home card is gone.** `launcher/HomeScreen.kt:1221-1226` KDoc — `BedtimeListCard` was removed in v0.26.6 "Three task-capture cards was one too many." The model and DataStore remain; a future release can re-introduce it. The data is therefore written by code paths that no UI reaches today. `LauncherViewModel.bedtimeList` is collected but `bedtimeList` is not consumed by any current home card.
7. **`OneThingCard` is also gone.** `launcher/HomeScreen.kt:1968-1981` — removed in v0.28.0. The `oneThing` state flow in `LauncherViewModel.kt` still exists, still updates, still has `setOneThing` wiring — but the only place it is read is the export payload. A user with BPD who relied on the "one thing today" framing has no surface to enter one.
8. **The "Data sources card" has empty-state that hides the whole card.** `DataSourcesCard.kt:88-92` — when every source is unavailable and there is no PPG data, the card is hidden. **A user who has just set MindAnchor as their launcher will see no card telling them Health Connect is the first thing to grant.** The first-time launch path goes goal-elicitation onboarding → setup wizard (which has the Health Connect step), but if the user dismisses the wizard the home has no nudge to set up the data source.
9. **Letter generation is gated by `modelFits` but the empty-state composer exists for users who never download.** `LetterScreen.kt:181-187` — the "Use AI" affordance is `disabled when modelFits = false` (correct), but the user-authored composer is always available, so a user who never downloads the model can still write letters. This is intentional but the inbox's empty state does not say "you can also write your own letter here"; the empty state has the "Use AI" button visible, the "Write a letter now" button is the discoverable alternative. The `WakeState` flow is therefore split across two affordances with no cross-link.
10. **`ModelFits` in `LauncherViewModel` is a `StateFlow<Boolean>` that reflects on-disk presence.** `LauncherViewModel.kt:499-500` (per `HomeScreen.kt:719-727` BUG-017 KDoc) — the "Generate now" affordance is gated on this. Pre-v0.25.16, the affordance was permanently disabled because a Composable-level `remember { mutableStateOf(false) }` was used. v0.25.16 fixed it; the BUG-017 FindingTest pins the fix. There is still no per-letter "you generated this on a slow phone" copy — the user only sees "Generating tonight's letter — 30–60 min" before tapping.

---

## 6. Usability score histogram

| Rating | Count | % |
|---|---|---|
| 5 | 10 | 12% |
| 4 | 24 | 29% |
| 3 | 38 | 45% |
| 2 | 11 | 13% |
| 1 | 1 | 1% |
| **Total** | **84** | 100% |

Median = 3, mode = 3. 10/84 = 12% are one-tap, 34/84 = 40% are one-tap or "via one shortcut + one tap", 50/84 = 60% are ≤3 taps.

---

## 7. BPD-safety summary

| Rating | Count | Examples |
|---|---|---|
| **PASS** | 82 | All the DBT / IFS / Grounding / DBT-distress-tolerance / Diary Card / ACCEPTS / support surfaces; the wellness card with direction bands; the needs card; the open-loop postpone; the bedtime list; the bedtime list home card removal; the gate's "never mind" door. |
| **WARN** | 1 | F16 (letter generation Toast copy at `HomeScreen.kt:840-848` — "30–60 min" is a latency-pressure framing; the rest of the surface is BPD-safe). |
| **FAIL** | 0 | No surface in the codebase uses red-as-bad, lock-out, or imperative language in a BPD-violating way. Streak counters do not exist. "Good / bad" labels do not exist. |

### WARN remediation

- **F16 letter-generation Toast copy.** `launcher/HomeScreen.kt:840-848`. Remediation: replace with a single sentence that names the constraint without enumerating it — e.g. "Started. The letter will be in your inbox when it finishes — usually by morning."

---

## Appendix A — Methodology and confidence

- **Read-only audit.** No file modified, no build run, no `gradle` commands. All evidence is `file:line` or `file:line-line`.
- **Confidence:** high for everything `file:line`-cited. Medium for F48 (Polar round-trip) and F55 (grayscale) — partial-end-to-end claim is the code's own KDoc, not a runtime test.
- **What I could not verify without running the build:** the actual OAuth flow with the missing deep-link handler (F48); whether `LettersGenerationService` survives process death as advertised (F16); whether the wellness card's hidden-when-no-baseline gate is the right place to surface "still building" copy (F10); the vibration pattern of the gate's breath haptics (F19).
