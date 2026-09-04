# Program 1 research runbook: provenance, ledger immutability, upgrade, and export

## What this document is

A precise, followable procedure for physically verifying the Program 1
properties that no JVM or single-emulator test can prove: a real upgrade
from an installed v0.71.0 build, a real second physical device, and a real
person reading a real exported file.

**Steps 5, 6, and 7 below have not been physically executed.** They need
an installed prior build, a second phone, a real Google account, and a
recovery key typed by hand. Executing them is the owner's job, not this
task's. Nothing below should be read as a claim that they have been run.

What Program 1 **did** automate and run in this environment, against this
commit, is listed with real counts in `sdd/claude-final-report.md`. The
automated set that bears on this runbook:

- `MigrationTest.aVersion6DatabaseKeepsProgramZeroDataAndGainsTheResearchTables`
  — a real v6 database, seeded with a Journal entry, a context row, a
  morning measure and a continuity change, walked forward through
  `MIGRATION_6_7` on the emulator, with every row asserted to survive.
- `ResearchImmutabilityTest` — the append-only triggers, proven by raw
  `UPDATE`, `DELETE` and `INSERT OR REPLACE` statements all being rejected.
- `ContinuityRoundTripTest` — capture → encrypt → wipe → restore →
  recapture, now carrying the ledger and the study phases, with the
  restored chain verified against the source's anchor.
- `ResearchExportBuilderTest` — the export built from real Room rows,
  self-verifying, on the emulator.

---

## 1. Open Journal and write an entry (phase 0 opens)

| Step | Do | Expect |
| --- | --- | --- |
| 1.1 | Fresh install. Open the launcher, then Journal. | Today shows the writing card, the morning check-in, and "What else about today?". |
| 1.2 | Write and save an entry. | "Context prepared" appears. |
| 1.3 | Settings → Google Drive → Export research JSON. Accept the privacy warning. Open the file. | `studyPhases` has exactly one entry, `ordinal` 0, `reason` `INITIAL`. `ledgerEvents` has a `STUDY_PHASE_STARTED` at sequence 1 and one `PROTOCOL_VERSION_REGISTERED`. |

The first research write is what opens phase 0 — not app start. If phase 0
exists before you have written anything, that is a bug, and it is the one
that would block a replacement-phone restore.

## 2. The morning measure is unchanged

| Step | Do | Expect |
| --- | --- | --- |
| 2.1 | Complete the five-item check-in. | Same five items, same 1–5 chips, same "a personal research measure, not a diagnosis or clinical score" line. |
| 2.2 | Tap Edit, change one value, save. | The row updates in place. Export: exactly one `morningMeasures` entry for the date, `instrumentVersion` `morning-v1`. |
| 2.3 | Read the exported measure. | No total, no score, no threshold, no interpretation anywhere. |

## 3. The research log records and never interprets

| Step | Do | Expect |
| --- | --- | --- |
| 3.1 | Tap each of the seven chips in turn and record one, with and without a note. | Each appears in today's list, newest first. |
| 3.2 | Tap "Medication change". | The dialog says MindAnchor records that something changed and does not give medication advice. |
| 3.3 | Look for a way to edit or delete a recorded row. | There is none. This is deliberate: the table rejects both. |
| 3.4 | Export and read `ledgerEvents`. | Your notes appear exactly as typed. `payloadJson` is `{}` for every self-reported row. No derived field anywhere. |

## 4. The ledger cannot be rewritten

Requires `adb` and a debuggable build.

```bash
adb shell run-as org.mindanchor \
  sqlite3 databases/mindanchor.db "DELETE FROM research_ledger_events;"
adb shell run-as org.mindanchor \
  sqlite3 databases/mindanchor.db "UPDATE study_phases SET reason='x';"
```

Expect both to fail with `Error: ... is append-only`. If either succeeds,
the triggers did not install and the immutability claim is false — check
`AnchorDatabase.researchImmutabilityCallback` is still wired into the
builder and that `ResearchBuilderCallbackTest` is green.

## 5. Upgrade from an installed v0.71.0 build

**Not executed.** Needs the previous release installed with real data.

| Step | Do | Expect |
| --- | --- | --- |
| 5.1 | On a phone running v0.71.0 with at least one Journal entry, one context row and one morning measure, note the exported content hash. | Recorded. |
| 5.2 | Install this build over it. Do not clear data. | The app opens. No crash, no "Room cannot verify the data integrity". |
| 5.3 | Open Journal and read the old entry. | Body identical, character for character. |
| 5.4 | Export again. | The old rows are all present. `dataDictionaryVersion` is now `mindanchor-research-v2`. |

## 6. Restore a Program 0 checkpoint onto this build

**Not executed.** Needs a `.mab` file written by v0.71.0.

| Step | Do | Expect |
| --- | --- | --- |
| 6.1 | On a clean install of this build, sign in, enter the recovery key, and restore from a checkpoint written by v0.71.0. | The restore reaches VERIFIED. |
| 6.2 | If it reports a hash mismatch instead. | This is the failure the version-1 hash projection exists to prevent. Capture the staged file and the logs; do not clear data. |
| 6.3 | Write one Journal entry afterwards. | Export shows a second study phase with `reason` `DEVICE_CHANGE`, and a `DEVICE_CHANGE` ledger event chained onto the restored head. |

## 7. Replacement phone, Program 1 data

**Not executed.** Needs two physical devices.

| Step | Do | Expect |
| --- | --- | --- |
| 7.1 | On phone A: entry, measure, several research-log events. Export; record `contentSha256`, `ledgerHeadHash`, `ledgerEventCount`. | Recorded. |
| 7.2 | Wait for a verified checkpoint (Settings shows the last verified time). | Verified. |
| 7.3 | On phone B: install, sign in to the same account, enter the same recovery key, restore. | VERIFIED. |
| 7.4 | Export on phone B. | Same `contentSha256`, same `ledgerHeadHash`, same `ledgerEventCount` as 7.1. |
| 7.5 | Re-run `LedgerChain.verify` mentally: every `previousEventHash` equals the prior event's `eventHash`, sequences run 1..n. | Intact. |
| 7.6 | Write one entry on phone B. | A new phase with `reason` `DEVICE_CHANGE` appears, chained onto the restored head — the moment the history changed device is now permanently in the record. |

## 8. Read the export as a stranger would

| Step | Do | Expect |
| --- | --- | --- |
| 8.1 | Open the exported JSON with no other file to hand. | `dataDictionary` describes every column: type, unit, allowed values, provenance, missing-data policy. |
| 8.2 | Find a day you skipped the measure. | It is in `missingData` with a reason. No value was invented for it. |
| 8.3 | Read `protocolRegistry`. | One protocol, `cyclic-sighing` v1, with its full evidence contract and `clinicalReviewStatus` `NOT_REVIEWED`. |
| 8.4 | Check `ledgerIntegrity`. | `VERIFIED`. Treat it as a convenience, not the authority — the anchor is there so you can re-derive it yourself. |

## What a failure here means

| Symptom | What it means |
| --- | --- |
| Phase 0 exists on a fresh install before any write | The lazy-open rule broke. A replacement-phone restore will be blocked by the preflight. |
| A Program 0 checkpoint reports `VerifyMismatch` | The version-1 hash projection no longer reproduces Program 0's bytes. Do not "fix" it by re-pinning a constant. |
| `research_ledger_events` accepts a DELETE | The immutability triggers are not installed on that database. Every immutability claim in the design is void until they are. |
| An export does not self-verify | Either the seal and the verify disagree, or the file was edited. Check which before assuming either. |
| A day with a measure appears in `missingData` | A stored `localDate` is malformed, or the export's date joins are wrong. The report is inventing a hole. |
