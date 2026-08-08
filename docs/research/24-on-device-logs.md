# 24 — Structured on-device log path with share entry point

## Why this brief

The senior-architect review noted that the project
has no on-device log path. A user reporting a bug has
no way to attach diagnostic context; a developer
debugging has no structured way to ask for it. The
project's on-device-only constraint makes this *harder*,
not easier — there is no server to log to, no crash
reporter, no analytics SDK — but a 1-day job in pure
Android.

## Primary research

- Android FileProvider documentation:
  https://developer.android.com/training/secure-file-sharing/share-file
  - FileProvider is the standard pattern for sharing
    files with other apps.
  - "We recommend that you avoid using Uri.fromFile()...
    Instead, use URI permissions to grant other apps
    access to specific URIs."
- Android Sharing files in Compose:
  https://developer.android.com/develop/ui/compose/sharing/send
  - `Intent.createChooser(intent)` displays the
    Android Sharesheet.
  - `EXTRA_STREAM` carries the content URI; the
    receiving app needs per-URI permission.
- Android FileProvider reference:
  https://developer.android.com/reference/androidx/core/content/FileProvider

## What this PR ships

1. `LogFile.kt` — a small wrapper that appends a
   structured log line to a file in the app's
   `cacheDir/logs/` directory. The format is one
   line per record: `timestamp<TAB>level<TAB>tag<TAB>message`.
   The file is rotated at 1 MB (keep the last 5
   files in `cacheDir/logs/`).

2. `ShareLogsEntryPoint.kt` — the settings entry
   point. Tap "Share logs" → `Intent.ACTION_SEND`
   with the log file as `EXTRA_STREAM`, wrapped in
   `Intent.createChooser`. The intent uses a
   `FileProvider` to grant per-URI read access to
   the receiving app.

3. `AndroidManifest.xml` updates:
   - The FileProvider declaration with the right
     authority (`org.mindanchor.fileprovider`).
   - The `file_paths.xml` resource declaring
     `cache-path` for the logs directory.
   - The `@wording-reviewed` sentinel is already
     on the manifest (item A) so the clinical-
     review gate will flag this change for review.

4. Tests:
   - `LogFileTest` pins the format (one line per
     record, the four fields, the rotation policy).
   - `ShareLogsEntryPointTest` pins the intent
     shape (ACTION_SEND, EXTRA_STREAM, chooser
     wrapper).

## What this PR does NOT ship

- The settings UI button. The data layer and the
  intent are in; the Composable is a follow-up.
- Crash reporting. The MASTG-BEST-0066 threat
  model does not include a remote crash reporter
  (the project's no-cloud promise). The local log
  is the only diagnostic surface.
- Performance metrics. The brief is a 1-day
  feature, not a metrics platform.

## Risk

- The logs may include the user's own words
  (small-things, if-then plans, compassion
  moments). Sharing the log means sharing those
  words. The share-intent is explicit — the user
  has to pick a recipient — but the user may not
  realize what is in the log file. The settings
  UI must include a clear "what's in this file"
  note before the share button (the clinical-
  review gate will block the wording).
- Log files persist in `cacheDir/logs/` until the
  user clears the cache. A 1 MB × 5 file ceiling
  is ~5 MB on disk; this is bounded but real for
  a no-cloud app.

## Verification

- 5 new test cases in `LogFileTest`:
  1. One-line-per-record format
  2. The four fields in order
  3. Rotation at 1 MB
  4. The 5-file ceiling
  5. UTF-8 message content (including
     multi-byte characters)
- 3 new test cases in `ShareLogsEntryPointTest`:
  1. The intent action is ACTION_SEND
  2. The intent carries EXTRA_STREAM with the log
     file's content URI
  3. The intent is wrapped in `Intent.createChooser`
- 12/12 assertions Python-mirror-verified.

## Primary sources

- Android FileProvider documentation,
  https://developer.android.com/training/secure-file-sharing/share-file
- Android Compose Sharing,
  https://developer.android.com/develop/ui/compose/sharing/send
- Android FileProvider reference,
  https://developer.android.com/reference/androidx/core/content/FileProvider
