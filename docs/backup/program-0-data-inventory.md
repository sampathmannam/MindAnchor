# Program 0 data inventory

This is the continuity review checklist for Task 7. It records the storage
locations present at Program 0's base commit and the capture/restore route.
A protected row must have a destination in the canonical snapshot
(`app/src/main/java/org/mindanchor/continuity/ContinuitySnapshot.kt`); it
must not be silently omitted.

## Protected in Program 0

| Store | Current source path | Logical export and import route |
| --- | --- | --- |
| Safety plan | `app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt` (`SafetyPlan`, `SafetyDao`) | `BackupRepository.export(now)` encodes it as `Backup.plan`; `BackupRepository.import(text, now)` replaces the Room plan. Task 7 carries that legacy JSON verbatim in `ContinuityPayload.legacyBackupJson`. |
| Crisis contacts | `app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt` (`CrisisContact`, `SafetyDao`) | `BackupRepository.export(now)` encodes `Backup.contacts`; `BackupRepository.import(text, now)` adds phone-number-deduplicated contacts. Task 7 carries it in `ContinuityPayload.legacyBackupJson`. |
| WHO-5 pulse history | `app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt` (`PulseResult`, `PulseDao`) | `BackupRepository.export(now)` encodes `Backup.pulses`; `BackupRepository.import(text, now)` adds timestamp-deduplicated pulses. Task 7 carries it in `ContinuityPayload.legacyBackupJson`. |
| Launcher favorites, hidden apps, and renames | `app/src/main/java/org/mindanchor/data/LauncherPrefs.kt` (`launcher` DataStore) | `BackupRepository.export(now)` encodes `Backup.favorites`, `hidden`, and `renames`; `BackupRepository.import(text, now)` calls the three replacement APIs. Task 7 carries it in `ContinuityPayload.legacyBackupJson`. |
| EMA moments | `app/src/main/java/org/mindanchor/model/MomentStore.kt` (`ema` DataStore) | `BackupRepository.export(now)` encodes `Backup.checkIns`; `BackupRepository.import(text, now)` appends entries deduplicated by day and minute. Task 7 carries it in `ContinuityPayload.legacyBackupJson`. |
| App-measured readings | `app/src/main/java/org/mindanchor/vitals/MeasuredStore.kt` (`measured` DataStore) | `BackupRepository.export(now)` encodes `Backup.readings`; `BackupRepository.import(text, now)` restores missing `(day, key)` readings. Task 7 carries it in `ContinuityPayload.legacyBackupJson`. |
| Phone-inferred readings | `app/src/main/java/org/mindanchor/usage/InferredStore.kt` (`inferred` DataStore) | `BackupRepository.export(now)` encodes `Backup.inferred`; `BackupRepository.import(text, now)` restores missing `(day, key)` readings. Task 7 carries it in `ContinuityPayload.legacyBackupJson`. |
| Corpus additions | `app/src/main/java/org/mindanchor/corpus/CorpusStore.kt` (`filesDir/corpus-imported.tsv`) | `BackupRepository.export(now)` reads the imported difference file as `Backup.corpusAdditions`; `BackupRepository.import(text, now)` merges novel passage IDs through `CorpusStore.saveImported`. Task 7 carries it in `ContinuityPayload.legacyBackupJson`. |
| Quick Notes | `app/src/main/java/org/mindanchor/data/NotesPrefs.kt` (`notes` DataStore) | Task 7's `ContinuitySnapshotRepository.capture` reads `NotesPrefs.notes` into `ContinuityPayload.notes` (as `NoteDto`); restore uses the new `NotesPrefs.mergeRestored` extension function, deduplicating by `Note.id` and keeping the record with the larger `updatedAt`. |
| Letters and read-letter dates | `app/src/main/java/org/mindanchor/letters/LetterStore.kt` (`letters` DataStore) | Task 7's `ContinuitySnapshotRepository.capture` reads `LetterStore.letters` and `readDates` into `ContinuityPayload.letters` (as `LetterDto`) and `ContinuityPayload.readLetterDates` (ISO date strings); restore uses the new `LetterStore.mergeRestored` extension function — a local letter for a date wins outright, only genuinely-missing dates are added, and read dates are unioned in. |
| Legacy protective-writing Journal | `app/src/main/java/org/mindanchor/letters/JournalStore.kt` (`journal` DataStore) | Task 4's one-time `JournalLegacyImporter` enumerates the current entries and imports them idempotently into the Program 0 Room Journal. Task 7 then captures the Room Journal rows, not a second copy of this DataStore. |
| Program 0 Journal originals and structural context | `app/src/main/java/org/mindanchor/data/db/JournalEntities.kt` and `app/src/main/java/org/mindanchor/journal/JournalRepository.kt` (Tasks 2–3) | Task 7's `ContinuitySnapshotRepository.capture` reads the Room tables (`JournalDao.entriesNow()` / `allContext()`) into `ContinuityPayload.journalEntries` (`JournalEntryDto`) and `ContinuityPayload.contextRows` (`JournalContextDto`). Task 11's `RestoreCoordinator` applies both idempotently by stable ID via `JournalDao.upsertEntries`/`upsertContext` (`REPLACE`-on-conflict) inside one Room transaction. |
| Morning research measures | `app/src/main/java/org/mindanchor/data/db/JournalEntities.kt` and `app/src/main/java/org/mindanchor/research/MorningMeasureRepository.kt` (Tasks 2 and 5) | Task 7's `ContinuitySnapshotRepository.capture` reads `JournalDao.morningMeasuresNow()` into `ContinuityPayload.morningMeasures` (`MorningMeasureDto`). Task 11's `RestoreCoordinator` applies it idempotently by stable ID via `JournalDao.upsertMorningMeasures` (`REPLACE`-on-conflict) in the same transaction as the Journal rows above. |
| Frictioned apps | `app/src/main/java/org/mindanchor/data/FrictionPrefs.kt` (`friction` DataStore, `flagged_packages`) | Task 7's `ContinuitySnapshotRepository.capture` reads `FrictionPrefs.flaggedApps` into `ContinuityPayload.frictionedApps`; restore uses the new `FrictionPrefs.replaceFlagged` extension function, a full replace after removing blank package names. |
| Always-open apps | `app/src/main/java/org/mindanchor/data/FrictionPrefs.kt` (`friction` DataStore, `always_open`) | Task 7's `ContinuitySnapshotRepository.capture` reads `FrictionPrefs.alwaysOpen` into `ContinuityPayload.alwaysOpenApps`; restore uses the new `FrictionPrefs.replaceAlwaysOpen` extension function, a full replace after removing blank package names. |
| Continuity change ledger | `app/src/main/java/org/mindanchor/data/db/JournalEntities.kt` (`ContinuityChangeEntity`), `app/src/main/java/org/mindanchor/data/db/JournalDao.kt` | Task 7's `ContinuitySnapshotRepository.capture` reads the full ledger (`JournalDao.allChangesNow()`, added this task) into `ContinuityPayload.continuityChanges` (`ContinuityChangeDto`). `acknowledgedSnapshotId` is excluded from the content hash (`ContinuityContentHasher`) since it is bookkeeping about the sync process, not the change itself. |

## Deliberately device-local

| Store | Source path | Reason and route |
| --- | --- | --- |
| Google OAuth credential and signed-in account metadata | `app/src/main/java/org/mindanchor/backup/GoogleDriveAuth.kt` (`TokenStore` encrypted preferences and `google_drive_email` DataStore) | Credentials are device- and keystore-bound. Do not export; authenticate again on the replacement phone. |
| Current device identifier | `app/src/main/java/org/mindanchor/journal/DeviceIdentityStore.kt` (Task 3) | The current phone creates a new ID. Entry-level source-device IDs remain protected data, but the current-device ID is never restored. |
| Android permission grants | Android system permission manager; requests are declared in `app/src/main/AndroidManifest.xml` | Grants belong to the replacement phone and must be requested again. Do not export. |
| Wearable pairing credentials | `app/src/main/java/org/mindanchor/vitals/coros/CorosCredentialStore.kt` | Encrypted, device-local credentials must be re-entered and the wearable re-paired. Do not export. |
| Imported model binaries | `app/src/main/java/org/mindanchor/narrate/ModelStore.kt` (`filesDir/model.gguf`) and `app/src/main/java/org/mindanchor/narrate/WhisperEngine.kt` | Large, device-suitability-dependent binaries are re-imported or re-downloaded. Do not export. |
| Caches | `app/src/main/java/org/mindanchor/corpus/CorpusStore.kt`, `app/src/main/java/org/mindanchor/goinglight/SourceUidResolver.kt`, and `app/src/main/java/org/mindanchor/diagnostics/LogFile.kt` | Derived in-memory/cache-directory content is recreated from protected data or the device. Do not export. |
| Current WorkManager jobs | `app/src/main/java/org/mindanchor/backup/BackupScheduler.kt`, `app/src/main/java/org/mindanchor/friction/BanditResetWorker.kt`, and `app/src/main/java/org/mindanchor/vitals/coros/CorosSyncWorker.kt` | OS-managed work is scheduled anew from restored settings and current device conditions. Do not export queued work or retry state. |

## Deferred with an explicit reason

| Store | Source path | Reason |
| --- | --- | --- |
| Nonessential UI counters | `app/src/main/java/org/mindanchor/data/LauncherPrefs.kt` (`home_launch_count`) | This only controls introductory UI presentation; restoring it is not needed for safety, history, or replacement-phone continuity. |
| Temporary check-in rate-limit state | `app/src/main/java/org/mindanchor/model/CheckInRateLimitHolder.kt` | The holder is intentionally process-local and resets on process death to avoid preserving an over-prompting history. It has no export/import route. |

## Task 15 confirmation

Every protected row above has a real destination in `ContinuityPayload` and a real, idempotent restore-side apply — re-verified at the end of the plan against the final code state (not each task's claim at the time it was written), as part of the Task 15 whole-branch review. No protected row was found silently omitted.
