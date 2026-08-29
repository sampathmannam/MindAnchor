# MindAnchor Privacy Promise

**Last updated:** 2026-08-29

This page is the privacy promise for MindAnchor. It is short because
the app's stance is short. If anything below is wrong, the bug is in
the app, not in the wording.

## What the app does

MindAnchor is a launcher. It is a small piece of software that stands
in for the home screen of your phone. It is private by design, and
its build configuration enforces that:

- `allowBackup="false"` — your data is never copied off the device
  by the system.
- `dataExtractionRules` excludes both cloud backup and device
  transfer.
- `app/proguard-rules.pro` keeps no analytics SDK, no crash reporter,
  no telemetry library in the dependency graph. A static
  `NetworkCallsForbiddenTest` checks that no production source
  outside the opt-in bridges (`org.mindanchor.vitals.coros/**`,
  `org.mindanchor.backup/**`, and `org.mindanchor.llm/**`)
  imports any network API.

## What the app holds on this phone

- **Your notes**, encrypted with an Android Keystore-derived HMAC.
- **Your check-in history** (the times of day you answered "how are
  you?" and the answers you gave).
- **Your safety plan** (the people you would call first), held
  locally.
- **Your wearable data** if you have connected a watch via
  Health Connect. The launcher reads it; it never writes back to
  the watch and never transmits it.
- **If you have opted in:** your COROS account email + password
  hash, your signed-in Google account email plus a short-lived
  Google Drive access token (Google's side holds the refresh
  token; this phone re-requests a fresh access token from Google
  each time one is needed rather than storing one that outlives
  a session), and your API key for whichever LLM provider(s)
  you've set up for the Daily letter feature (Google AI Studio,
  OpenRouter, or Groq — each provider's key is stored in its own
  slot, so adding one doesn't overwrite another), all held in
  `EncryptedSharedPreferences` (Tink-backed). Each of these can
  be cleared from Settings.
- **If you turn on Google Drive backup:** a second copy of your
  notes, letters, check-ins, and wellness readings in your own
  Google Drive, in four plain text files this app created and is
  the only app that can see. Your safety plan and crisis contacts
  are never included — see the section above.

## What the app does NOT do

- No analytics. No advertising SDK. No fingerprinting.
- No crash reporting to any server. The "Share logs" button in
  Settings writes a file to local storage and hands it to the
  system Share sheet — you choose who, if anyone, sees it.
- No background telemetry. The launcher makes network calls in
  exactly two situations: the three opt-in bridges above (only
  once you've turned one on) and the automatic update check
  described below.
- No advertising ID. No usage stats.

## What the app does to the network

Two kinds of network calls happen. The first is the three opt-in
bridges described above — the COROS Training Hub sync, Google
Drive backup, and the Daily letter (LLM) feature — each silent
until you turn it on. The second is one automatic check that runs
regardless of whether any bridge is on:

**The auto-update check.** When you open the app, it makes **one**
GET request to
`api.github.com/repos/sampathmannam/MindAnchor/releases/latest`
to find out whether a newer version of MindAnchor has been
published. The request sends no user data, no device id, and
no app id beyond the standard HTTP `User-Agent`. The result is
cached locally for 24 hours. The check is best-effort: on
network failure it returns silently, and the launcher does not
block on it.

**The Daily letter (LLM) feature**, in more detail: there is no
on-device model or LLM of any kind running on the phone. Once you
add an API key in Settings → Reading for a provider (Google AI
Studio, OpenRouter, or Groq), the launcher sends your recent notes
and most recent check-in to that provider to write one daily
reflection. No key, no outbound call — ever.

The auto-update check can be turned off entirely by denying the
app network access at the system level (Settings → Apps →
MindAnchor → Mobile data & Wi-Fi → disable). With that permission
denied and none of the three opt-in bridges turned on, the
launcher makes zero outbound calls.

## What to do with your data

- **To see what the app holds:** Settings → This phone → A copy
  of your data.
- **To delete everything:** uninstall the app. Because no
  data is on any server, uninstalling is the deletion.

## Regulatory framing

- **DPDPA (India, 2023):** MindAnchor is a personal data
  processor. The only data it processes is yours, on your
  device, with no cross-border transfer. Consent under
  Section 6 is implicit in the install action; the
  "no data leaves the device" stance means the data
  principal rights under Sections 11–14 are not engaged
  by the launcher's normal operation.
- **GDPR (EU):** MindAnchor has no data controller in the
  EU. The personal data the launcher processes is held
  on the data subject's own device and never leaves it,
  so the controller / processor relationship under
  Article 4 does not arise. Right-to-erasure is satisfied
  by uninstalling the app.
- **CCPA (California):** MindAnchor does not sell personal
  information. There is no "do not sell my personal
  information" toggle because there is nothing to sell
  and no buyer to sell it to.
- **HIPAA (US):** MindAnchor is not a covered entity or
  a business associate. The app explicitly does not
  diagnose, treat, or bill for any health condition. The
  Wellness check-in is a 2-minute self-report kept on
  the user's own device. The launcher makes no claim to
  clinical validity.

## Crisis resources

If you are in crisis right now, the app is not the right place.
The About screen in the app lists crisis line numbers for the
US (988), India (Tele-MANAS 14416), the UK (Samaritans 116 123),
and the EU (112). Outside the app: the iCall helpline (India)
is at 9152987821, Vandrevala Foundation at 1860-2662-362, and
AASRA at 9820466726.

## Contact

Privacy questions: open an issue at
https://github.com/sampathmannam/MindAnchor/issues, or email the
maintainer. The repo is a private R&D project; there is no
support team, but issues are read and answered.

---

*This document is the privacy promise. It is not legal advice.
If you are running MindAnchor inside a regulated environment
(clinic, school, employer-issued device), the responsible party
is whoever installed it there, not the launcher author.*
