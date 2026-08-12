# v0.25.4 — Google Drive backup (replaces WebDAV)

The v0.23.0 WebDAV bridge is gone; in its
place, the user's own Google account. The
local file picker (the "Save a copy…" /
"Restore from a copy…" buttons in the
Phone group) is unchanged — it remains
the default backup path.

## What's new

### Google Drive backup (Settings → Reading → Google Drive)

A second, opt-in outbound channel. The user
opens Settings → Reading → Google Drive,
taps "Sign in with Google", picks their
account, and the launcher requests the
narrowest "per-file" scope Google offers:
`https://www.googleapis.com/auth/drive.file`.
The launcher can only ever see files it
created — never the rest of the user's
Drive.

After sign-in, the section exposes:

- **Per-type auto-sync toggles** for notes
  and letters (off by default; off means
  the launcher never auto-uploads, the
  toggle is the gate, not the sign-in).
- **"Back up now"** — a one-shot full
  reupload of every existing note + every
  existing letter. The manual escape hatch.
- **"Forget this account"** — clears the
  local credentials and signs out. The
  user's remote copies are untouched; the
  user can re-link the same account any time.

### Per-type file routing (one Drive file per content type)

Every new note / letter is wrapped in
AES-256-GCM (the same wrapper the v0.23.0
WebDAV bridge used) and appended to a
per-type file in the user's Drive root:

- `MindAnchor-Notes.txt` — the home-screen
  quick-capture (and any future freeform
  journal)
- `MindAnchor-Letters.txt` — the v0.25.2
  daily letter

Each file is a sequence of newline-terminated
encrypted entries — the same shape as the
v0.25.2 `BackupCodec` JSON-Lines model. The
file is inspectable in the Drive web UI as
"one entry per line".

### Why one file per type (not per note)

The user spec was explicit: "single document
for each like whole journal though im giving
in multiple it should be stored in google
drive in one document file". `Each` refers
to the type, not the individual note. The
append-only model is also the simplest to
reason about: a journal entry from day 1
and a journal entry from day 100 both go
into the same `MindAnchor-Notes.txt`.

## What this is not

- **Not Google Docs.** The v0.25.4 surface
  is plain text files in Drive, not Google
  Docs. Plain text is readable from any
  device, any text editor, the Drive web
  UI, and the Drive mobile app. A future
  v0.25.5+ could ship a Docs format, but
  that needs the Docs API scope; the v0.25.4
  scope is the narrowest one Google offers.
- **Not auto-backup on by default.** The
  toggles default to `false`. The launcher
  is "off by default; opt-in" — the v0.23.0
  design the v0.25.4 plan explicitly extends.
  Signing in with Google does not, by itself,
  upload anything.
- **Not a full Google Drive client.** The
  launcher can only see files it created,
  not the user's other Drive files. The
  `drive.file` scope is the user-trust
  posture: even if a future bug puts the
  access token somewhere it should not be,
  the launcher cannot read the user's other
  Drive files.

## Wire format

Each call writes the payload bytes verbatim,
followed by a single newline (`\n`, 0x0A).
The per-type file is therefore a sequence of
newline-terminated AES-256-GCM blobs. The
on-the-wire format is unchanged from v0.23.0
(same AES-256-GCM wrapper, same IV+ciphertext+tag
shape); the file shape is the only thing that
changed (per-type, append-only, instead of
whole-file per backup).

## Implementation

- **`GoogleDriveAuth`** — the OAuth entry
  point. Wraps `play-services-auth` 21.2.0
  for the account picker, fetches the access
  token via `GoogleAuthUtil.getToken(account)`
  on demand, stores the access token in
  `EncryptedSharedPreferences` (Keystore-backed
  master key, same as the v0.23.0 WebDAV
  credential store). The refresh token lives
  on Google's side; the launcher never sees
  it.
- **`GoogleDriveBackupTarget`** — the Drive
  REST client. Raw REST over HTTPS, no
  `play-services-drive` AAR (the OAuth token
  + OkHttp is enough; the Drive REST is JSON
  over HTTPS with `Authorization: Bearer`).
  Three endpoints: find-or-create the
  per-type file, download current content,
  upload the new content. Drive has no
  native append, so the model is a 3-round-trip
  per call.
- **`BackupScheduler`** — the per-type
  routing layer. Reads from `NotesPrefs` and
  `LetterStore`, encrypts each entry, dispatches
  to the right `BackupTarget`. The
  "Back up now" button calls `backupAll()`;
  the on-write trigger (`start(scope)`)
  observes the flows and appends on each
  new entry.
- **`ContentType`** — the routing enum:
  `Notes` and `Letters` for v0.25.4. Adding
  a new content type (e.g. CheckIns,
  Patterns) is a clinical-review decision,
  not a refactor.

## What's removed

The v0.23.0 WebDAV bridge is gone. The local
file picker (the "Save a copy…" /
"Restore from a copy…" buttons in the
Phone group) is unchanged. The user who
had a v0.23.0 WebDAV bridge armed needs to
sign in with Google to re-establish the
bridge — the remote WebDAV copies are
untouched.

## Verification

- 1136 unit tests pass (was 1120 in v0.25.3,
  -24 WebDAV tests +10 Drive tests + new
  per-type routing + new file-shape pins).
- Detekt clean.
- Debug + release-unsigned APKs built.
- Manual smoke deferred (emulator AVD boot
  issue on this Windows host — same as
  v0.25.2 / v0.25.3). Install on a physical
  device to verify the OAuth flow +
  the per-type file rendering in the Drive
  web UI.

## Out of scope (v0.25.5+)

- **Two-provider option (Drive + WebDAV).**
  The user explicitly chose replacement, not
  "add". If a power user wants both, that's
  a v0.25.5+ toggle.
- **Google Docs format.** Would need the
  Docs API scope; not in v0.25.4.
- **Auto-sync for the report data, EMA
  entries, PPG sessions.** The `ContentType`
  enum gets new values as those features opt
  in to backup.
- **WorkManager offline → online retry.**
  The v0.25.4 surface makes a best-effort
  attempt; the "Back up now" button is the
  manual retry path. A future v0.25.5+ could
  wire a `CoroutineWorker` with the network
  constraint for automatic retry.
- **Watch connect real root-cause fix
  (v0.25.3-WP-B-real).** Still pending
  `adb logcat -s MindAnchor/HealthConnect:V`
  capture from the failing device.
- **v0.25.1 senior-tester audit deferred
  items.** Worry postponement, DST-safe
  batch reschedule, "did the report help?"
  feedback, 14-day onboarding recap,
  "today's one thing" card, haptic-rich
  captures. Independent of the backup story
  and can land any time.
