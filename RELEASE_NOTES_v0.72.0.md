# MindAnchor v0.72.0 — Program 1: a record you could hand to someone

Program 0 made a Journal that survives losing your phone. Program 1 makes
what MindAnchor keeps about you into something a researcher — or you, in
five years — could actually read and check, without taking the app's word
for any of it.

Nothing here senses anything, decides anything, or suggests anything. It
is all record-keeping.

## What's new

### A research log: the things that might explain a day

Under the morning check-in there is now a row of chips — shift or duty,
exercise, illness, caffeine, a medication change, a life event, and
"something felt worse". Tap one, add a note in your own words if you want
to, and it is written down. That is the whole feature.

It records; it never interprets. There is no score, no summary, no "you
have logged this a lot lately". A medication change says plainly, on the
dialog itself, that MindAnchor is writing down that something changed and
does not give medication advice.

You cannot edit or delete an entry once recorded. That is deliberate:
these rows are the record, and a record you can quietly revise is not one.

### A ledger that can be checked, not just trusted

Every research row — what you logged, and every version change MindAnchor
made underneath you — goes into an append-only ledger where each entry's
hash covers the previous entry's. Editing, deleting, or reordering
anything in the middle of that history breaks every entry after it, and
anyone holding an exported file can see that for themselves.

The database refuses `UPDATE` and `DELETE` on those tables outright, so
"append-only" is a property of the storage rather than a promise about the
code.

### Study phases: what software produced which record

Whenever anything that could change how a record is produced or read
changes — the app version, the protocol catalogue, the transformation
that derives structural facts, the missing-data policy, the morning
measure's own version, the data dictionary, or the phone itself — a new
study phase opens. Records are attributed to the phase in effect when
they were written. Old days are never silently reinterpreted by new
software.

Restore onto a replacement phone and the first thing you write there
records, permanently, that the history moved.

### An evidence protocol registry

MindAnchor now holds protocol definitions that cannot exist without a
complete evidence contract: the observable target, who it is and is not
established for, its sources and their strength, the mechanism, the
expected outcome, eligibility and contraindications, fixed steps,
maximum duration, stop rules, cooldown, outcome window, clinical-review
status, and a plain-language explanation. A protocol missing any of those
is refused, and blog, influencer, marketing and AI-generated sources are
refused outright.

Exactly one protocol is registered — the five-minute cyclic-sighing
breathing practice — because that is what this repository's own citation
audit supports. Its clinical-review status says `NOT_REVIEWED`, because
nobody has reviewed it. Three other candidates were considered and left
out, with the reasons recorded in the code.

**Nothing delivers, schedules, or runs a protocol.** That is a later
program. This is a catalogue.

### Missing data is listed, never filled in

Skip a morning measure and the export says so, with a reason, for that
exact date — and it distinguishes a day you skipped from a day before you
had started that measure at all. Nothing is imputed, interpolated, or
carried forward. A series that quietly looks complete is worse than one
with visible holes.

The report says which window it covers rather than promising more than it
can deliver. A row carrying an impossible date — a corrupt restore, or a
clock that was wrong when it was written — is left out of choosing that
window, so one bad row cannot bury your real history under thousands of
invented absences. The row itself is still exported exactly as stored.

### The export explains itself

"Export research JSON" now writes a file that carries its own data
dictionary: every column's type, unit, allowed values, what it means, and
whether you wrote it or MindAnchor did. It also carries the whole ledger
with its anchor, every study phase, the full protocol registry, the
transformation registry, and the missing-data report.

It carries one number that does not come from the file's own contents:
how many ledger entries this phone remembers ever holding. That is what
lets whoever receives the file notice that entries went missing *before*
it was written, rather than having to take the app's word for it.

The file verifies itself. Change one character of a journal body and the
content hash no longer matches. The hash still ignores *when* you
exported, so "did anything change" stays a separate question from "did I
export again".

## Upgrading

Your existing data is untouched. The database gains two new tables and
nothing else; no column is dropped, renamed, or retyped.

Backups written by v0.71.0 still restore. Their content hash was computed
over the older, smaller record shape, and MindAnchor verifies them
against that shape rather than today's — so an older backup restores and
verifies rather than merging correctly and then reporting a mismatch.

Research exports written by v0.71.0 still open and still verify, for the
same reason.

## What this release still does not do

No diagnosis. No prediction. No clinical score or threshold. No efficacy
claim. No medication advice. No reading of what your journal entries
mean. No sensing, no baselines, no automatic anything.

The morning measure is unchanged from v0.71.0 — same five items, same
scale, still not a score.
