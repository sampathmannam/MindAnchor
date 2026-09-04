# Program 3 (adaptive protocol delivery) physical-device runbook

This runbook is a set of procedures, not a result. Every row below is
unpopulated until a tester actually runs the step on a physical device and
records what happened. **These tests are not complete until every row has an
observed result, an artifact path, a timestamp, a tester, and a Pass/Fail
verdict.** A blank or template row is not evidence of anything, and it must
never be read as a pass.

Screenshots, screen recordings, and logs captured for this runbook must
contain no Journal entry text and no Note text. Crop, redact, or use a
fixture account with no real Journal/Note content before capturing evidence.
If a screen cannot be captured without exposing that content, describe the
observation in the `Observed result` column instead of attaching a capture.

Record deviations as facts. A step that did not go as expected is written
down exactly as observed — never rounded up to a pass, and never rephrased
as a statement about safety, efficacy, or clinical outcome. This runbook
produces operational evidence about what a build does; it does not produce
a clinical judgment.

See `docs/qa/program-3-adaptive-delivery-evidence.md` for whether this
runbook has actually been executed, and see `docs/RELEASING.md` for what
completing it does and does not authorize.

## Row template

Every numbered procedure below is one row (or, where noted, more than one
row) in this shape:

| Field | Value |
| --- | --- |
| Observed result | |
| Artifact path | |
| Timestamp | |
| Tester | |
| Pass/Fail | |

## 1. Session identification

Record, once per test session, before any other step:

- App commit (`git rev-parse HEAD`)
- APK SHA-256 (of the exact APK installed for this session)
- Signing certificate fingerprint
- Device manufacturer/model and Android/API level
- Device time zone
- Tester name
- Test session start time and end time

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 2. Inherited Program 2 evidence

Attach the approved Program 2 whole-review finding and the completed
eight-row Program 2 physical-device artifact this plan's design depends on.
This step does not re-run Program 2's tests; it records that the specific
approved artifacts exist and are attached.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 3. Inherited Program 0 evidence

Attach the completed Program 0 three-restore log and the completed 24-hour
battery/background log.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 4. Ordinary build ships zero delivery surface

Build ordinary debug and ordinary release. With an eligible database fixture
already loaded (a finalized `AVAILABLE_FINAL` + `SUSTAINED_DEVIATION`
decision present), prove there is no advisory card and no Start path
anywhere in the launcher on both builds.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| Ordinary debug | | | | |
| Ordinary release | | | | |

## 5. Personal-research build with only the personal property set

Build with only `PROGRAM3_PERSONAL_RESEARCH=true` (operational evidence
property left unset/false). Prove the operational-evidence gate stays
closed and nothing is delivered.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 6. Explicit owner activation, after external approval only

After external approval is actually recorded (never before), build with
both `PROGRAM3_PERSONAL_RESEARCH=true` and
`PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED=true`, then deliberately enable the
master advisory switch and the delivery switch in Settings.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 7. A finalized eligible decision produces one historical card

Insert or produce a finalized historical `AVAILABLE_FINAL` +
`SUSTAINED_DEVIATION` decision. Record that the one advisory card shows the
source local date and finalization time, and uses no current-state wording
anywhere on the card or its evidence screen.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 8. Ineligible source data shows nothing

Prove each of the following exposes no card and never falls back to an
older, previously-eligible decision:

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| Provisional (not yet final) decision | | | | |
| Final decision with no sustained deviation | | | | |
| Corrupt decision row | | | | |
| Missing-provenance decision | | | | |
| Latest decision ineligible, an earlier decision was eligible | | | | |

## 9. Dismiss is immutable and does not reappear

Dismiss the opportunity once. Verify exactly one immutable `DISMISSED`
event was recorded and the same opportunity never reappears.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 10. Evidence screen content

Open the evidence screen. Verify it shows the protocol's registry target,
exclusions, contraindications, clinical review status, and stop conditions;
verify there is no Q&A/checklist control anywhere; verify exactly one Start
attestation action exists.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 11. Start and the delivery sequence

Start the protocol. Verify the attested and started events were both
recorded, verify the exact 2-second/1-second/6-second visual breathing
sequence, verify the delivery is foreground-only (stops being visible when
backgrounded — see step 12), and verify the five-minute maximum duration
cap.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 12. Every interruption path terminates correctly, never completes

Exercise each of the following separately, from a fresh Start each time.
Verify the exact terminal event named and verify zero completion events are
recorded for that run.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| Stop (`STOPPED_BY_USER`) | | | | |
| Discomfort (`STOPPED_DISCOMFORT_REPORTED`) | | | | |
| Back navigation | | | | |
| Backgrounding the app (`INTERRUPTED_APP_BACKGROUND`) | | | | |
| Process death (`INTERRUPTED_PROCESS_RECOVERY`) | | | | |
| Kill switch flipped mid-episode (`STOPPED_KILL_SWITCH`) | | | | |

## 13. One full completion and its outcome window

Complete one run to the exact registered maximum duration. Verify the
`COMPLETED_MAX_DURATION` event and the `OUTCOME_WINDOW_OPENED` event are
both recorded. Then advance device time (or wait) through the due window
and verify exactly one `OUTCOME_WINDOW_CLOSED_MISSING` event is recorded
with reason `NO_REGISTERED_COMPATIBLE_INSTRUMENT`.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| Completion + outcome window opened | | | | |
| Outcome window closes missing | | | | |

## 14. Cooldown starts at Start, not at presentation

Prove the cooldown period is measured from the `STARTED` event's timestamp,
not from when the opportunity was presented, not from when it was
dismissed, and not from when a prior episode completed.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| | | | | |

## 15. Backup, restore, and idempotency on a replacement phone

Capture/export a continuity snapshot. Restore it on a replacement or test
phone. Compare v4 content hashes and verify the advisory episode chain
still verifies. Restore the same snapshot a second time and verify no
duplicate rows. Verify the person's advisory preferences (master switch,
delivery switch, active-episode id) all return to closed/off/null after
restore, and verify restore never synthesizes a terminal or outcome event
that was not already in the backup.

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| Content hash / chain match | | | | |
| Idempotent second restore | | | | |
| Preferences reset after restore | | | | |

## 16. No activity on system surfaces this feature does not use

Inspect each of the following on the device and record that Program 3
produced no activity on any of them:

| Observed result | Artifact path | Timestamp | Tester | Pass/Fail |
| --- | --- | --- | --- | --- |
| Notification shade | | | | |
| Vibration history | | | | |
| Running services | | | | |
| Overlays / SYSTEM_ALERT_WINDOW | | | | |
| Do Not Disturb / interruption-filter state | | | | |
| Lock task / kiosk mode | | | | |
| Network requests | | | | |
| Health Connect access log | | | | |
| Battery diagnostics | | | | |

## 17. Deviations

Record every deviation from the expected result of any step above, exactly
as observed. A deviation is a fact about what the build did; it is never
converted into an efficacy claim, a safety claim, or a clinical claim
either here or anywhere this runbook is referenced from.

| Step | Observed deviation | Artifact path | Timestamp | Tester |
| --- | --- | --- | --- | --- |
| | | | | |
