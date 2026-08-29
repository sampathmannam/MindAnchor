# MindAnchor v0.71.0 — Program 0: a Journal that survives losing your phone

Program 0 ships the smallest dependable slice of MindAnchor that can hold
a real Journal entry, keep a personal record of your mornings, and get
both back if your phone is lost, broken, or replaced. Everything here is
built to be honest about what it does and does not know about you.

## What's new

### Text Journal — Today, Entries, Patterns

A calm, dependable place to write. **Today** shows the date and a simple
writing card; drafts are saved as you type and recovered automatically
if the app is closed or force-stopped mid-entry. **Entries** lists what
you've written, chronologically, with search — no calendar view, no
media, no AI summaries; Program 0 keeps this surface deliberately plain.
**Patterns** shows transparent counts only: days written, words written,
your morning-measure history, and the *names* of the structural facts
recorded about each entry — never the words themselves, and never an
inference. Patterns says so explicitly: *"No inferences are created in
Program 0."*

### Structural context, kept separate from your words

When you save an entry, MindAnchor separately records four structural
facts about it — what kind of entry it is, the date, the word count, and
your title, if you gave one. That's all. No sentiment analysis, no
emotion detection, no risk labels, no diagnosis. If this structural step
ever fails, your original words are never at risk — they're already
saved before it even runs. You can turn structural-fact extraction off
entirely in Settings without losing the ability to write.

### A morning research measure — not a score

Five quick 1–5 taps each morning — mood, tension, the urge to react,
energy, and how you slept. It's under 30 seconds, and the app calls it
exactly what it is: a personal research measure. There's no "good" or
"bad" total, no color-coded interpretation, no threshold. Just your own
numbers, kept for you.

### Verified encrypted backup, with a key that isn't tied to this phone

Turn on continuity backup and MindAnchor encrypts a snapshot of your
Journal, morning measures, Notes, Letters, and safety-plan data with a
recovery key that's generated *for you*, not derived from this
particular phone's hardware. Every automatic backup is uploaded, then
immediately downloaded again and checked byte-for-byte and by content
hash before it's ever marked "Verified" — an upload alone is never
enough to call something backed up. A nightly snapshot is kept as a
separate, permanent version so you always have a fallback if the latest
one is ever unreadable. Nothing runs on the network while you're
offline; checkpoints and nightly snapshots are ordinary background work
that waits for a connection.

### Restore on a replacement phone

Install MindAnchor on a new phone, sign in to the same Google account,
and enter your recovery key — a phrase you saved somewhere other than
the lost phone. The restore is staged, verified at every step, and safe
to interrupt: if the app is killed mid-restore, it picks back up exactly
where it left off the next time you open it, with no duplicates. A
corrupted latest backup automatically falls back to the newest good
version and tells you which one it used. A wrong key, or a corrupted
file, never touches your data — the restore simply stops and asks you to
try again. If your new phone already has meaningful data on it, Program
0 refuses to restore over it rather than silently merging two histories.

### Everything you already had, still there

Your safety plan, crisis contacts, WHO-5 history, Notes, Letters, app
favorites, and always-open apps (Phone, SMS, WhatsApp, and anything else
you've marked as always-open) all restore alongside the new Journal.
Your existing protective-writing entries — BA, DEAR MAN scripts,
gratitude — are imported into the new Journal automatically, once, the
first time you open it.

## What Program 0 deliberately does not do

- No photos, audio, or attachments in Journal entries — text only, for now.
- No wearable-driven interpretation of anything you write or measure.
- No autonomous intervention, app blocking, or automated action of any kind.
- No diagnosis, treatment claim, or clinical scoring, anywhere.
- No semantic or emotional inference from your Journal text — structural
  facts only, and that's a permanent design choice for this program, not
  a placeholder for something more invasive later.

These are the same boundaries later MindAnchor programs will build past,
deliberately, one verified step at a time — not because the technology
isn't there, but because a wellness tool earns the right to know more
about someone only by first proving it can be trusted with less.

## A note on how this was tested

Every automatable check has been run and passes: the full unit and
instrumentation test suites, migration tests across every historical
database version, lint, coverage, and a reproducible-build check. What
has **not** yet happened is the one test that actually matters most for
this feature: a real restore, on a real second phone, done three times,
with matching content hashes recorded. That is the one gate still open
before this version can be tagged and shipped — see
`docs/qa/program-0-continuity-log.md` and `docs/RELEASING.md` §6.1.
