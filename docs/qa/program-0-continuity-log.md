# Program 0 continuity log (template)

## Standing instruction — read before filling in a single field

**Never paste Journal text or the recovery key into this log.** Every
field below is metadata about a restore attempt (a hash, a timestamp, a
device identifier, a pass/fail) — never the content itself. A content hash
is safe to record; the words behind it are not, and the recovery key must
never appear in any document, screenshot, or bug report, ever.

## What this document is

The record of a real, physical-device replacement-phone restore, per
`docs/qa/program-0-continuity-runbook.md` Step 4. **This is a template.**
Every field below is either blank, marked `TBD`, or explicitly marked
"pending physical execution" — none of it is a real observation, and
nothing here should be read as a claim that a physical restore has been
performed. Whoever runs the runbook fills this in with real values as
they go.

---

## Run record

| Field | Value |
|---|---|
| Date / time (start) | pending physical execution |
| Date / time (restore confirmed) | pending physical execution |
| App commit (source device build) | pending physical execution |
| App commit (destination device build) | pending physical execution |
| APK SHA-256 (the exact file installed on both devices) | pending physical execution — never a placeholder hex string; leave blank until observed |
| Signing certificate fingerprint (SHA-256, from `apksigner verify --print-certs`, per `docs/RELEASING.md` §2/§3) | pending physical execution |
| Source device (model, Android version) | TBD |
| Destination / restore device (model, Android version) | TBD |
| Selected backup candidate | TBD — record which object was restored from: `Latest` or a specific versioned snapshot filename (`MindAnchor-Continuity-Snapshot-<timestamp>-<snapshotId>.mab`); if a fallback occurred, note that explicitly |
| Source content hash (`contentSha256`, from the source device's backup-health UI or research export, per `ResearchExportBuilder.truncatedHash`) | pending physical execution |
| Restored content hash (same field, read on the destination device after restore) | pending physical execution |
| Hashes match? | pending physical execution |
| Restore duration (stage-by-stage, if the UI shows per-stage timing; otherwise wall-clock start to `VERIFIED`) | pending physical execution |
| Failures encountered (any `RestoreResult` other than `Verified`; note which one, and whether a retry resolved it) | pending physical execution |
| Repair result (did a retry/resume reach `VERIFIED`? did any duplicate rows/entries appear anywhere?) | pending physical execution |

## Fixture checklist (byte-for-byte confirmation on the destination device)

Do not record the actual fixture *content* here — only whether each item
was confirmed present and unchanged.

- [ ] Journal entry (multiline body) — confirmed present, pending physical execution
- [ ] Journal structural facts (`entry_kind` / `local_date` / `word_count` / `user_title`) — confirmed present, pending physical execution
- [ ] Morning measure (all five values) — confirmed present, pending physical execution
- [ ] Quick Note — confirmed present, pending physical execution
- [ ] Letter (body + read state) — confirmed present, pending physical execution
- [ ] Frictioned app set — confirmed present, pending physical execution
- [ ] Always-open app set — confirmed present, pending physical execution

## Failure-mode drill results (per `program-0-continuity-runbook.md` Step 5)

| Drill | Result | Notes |
|---|---|---|
| 5a. Force-stop right after DataStore-merge | pending physical execution | The one sub-case `RestoreResumeTest.kt` does not already cover on-device (see runbook table) |
| 5b. Corrupted `Latest` file → fallback | pending physical execution | No existing automated coverage at any level (see runbook table) — treat as first-time proof, not re-confirmation |
| 5c. Wrong recovery key | pending physical execution | Already well proven by `RestoreCoordinatorTest` + `RestoreScreenTest`; this is a re-confirmation only |
| 5d. Revoke Google access | pending physical execution | Domain logic (`AUTH` → `NeedsSignIn`) already proven by `BackupHealthTest`; the real revocation cannot be automated |

## Automated-equivalent cross-reference

See `docs/qa/program-0-continuity-runbook.md`'s "What is already proven
automatically, and what genuinely is not" table before treating any drill
above as first-time coverage — several already have a strong automated
equivalent and this log's purpose is the *real-hardware* confirmation,
not the first proof.
