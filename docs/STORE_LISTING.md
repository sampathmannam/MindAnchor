# Store listing

This document is the source of truth for the Google Play
Store listing for `sampathmannam/MindAnchor`. The
short / long descriptions, screenshots, and contact
fields are referenced verbatim by
`fastlane/metadata/android/en-US/*` (the Play Store
fastlane config) and are uploaded to the Play Store
through `bundle exec fastlane supply`.

## App name

```
MindAnchor
```

The name is set in `app/src/main/res/values/strings.xml`
as `<string name="app_name">MindAnchor</string>`. The
Tamil localisation is "காவலன்" (Kavalan), set in
`app/src/main/res/values-ta/strings.xml`. The Play Store
listing uses the English name for the default locale and
"காவலன்" for the Tamil locale.

## Short description (80 characters)

```
A quiet mental-health-first Android launcher.
```

This appears under the app name in the Play Store search
results and at the top of the listing. The 80-character
ceiling is a Play Store constraint; the description must
fit on one line on a 5-inch screen. The wording avoids
"best" and "powerful" — see the clinical-review log
`docs/CLINICAL_REVIEW.md` for the precedent on
substantive wording changes.

## Long description (4000 characters)

```
MindAnchor is a mental-health-first Android launcher.
The home screen is yours, not the app's — a clock, a
greeting, and a row of cards you control. Long-press
any app to add a one-breath pause before it opens.

What's in the box
-----------------

A launcher, not a productivity app. Every surface is
designed for the time of day when the phone feels like
too much. Nothing is a streak, nothing is a score, and
nothing nags.

• A 2am shell — when the clock crosses midnight, the
  home surface turns down the colour and the contrast.
  Not "do not disturb" — the same launcher, just gentler.
• A friction gate — a one-breath pause before any app
  opens, with a typed intention for the next few
  minutes. The pause is yours to set, yours to skip.
• A notification digest — held notifications arrive
  together at 8:00, 12:30, and 18:00 by default. The
  schedule is yours to change.
• A weekly letter — every week, a short letter drawn
  from your own last week. The model runs on the phone.
  No data leaves the device.
• Per-app session lengths — pre-commit to 5, 10, or 20
  minutes before opening a chosen app. The launcher
  closes it for you when the time is up. You can extend
  in 5-minute increments.
• A pulse check-in — a WHO-5 question at the cadence
  your own response pattern has earned. Not a daily
  reminder; a cadence that adapts.
• A "ground me" surface — three options when the
  feelings are too much: a breath, a name on the speed
  dial, a line to write.

What's not in the box
---------------------

MindAnchor does not have streaks, scores, badges, or
achievements. It does not have a social feed. It does
not have a "for-you" recommendation engine. The home
screen is yours.

Privacy
-------

MindAnchor is on-device. The privacy promise is
documented in `docs/CLINICAL_REVIEW.md` and
`docs/CLINICIAN_PACK.md`. No note body, no letter body,
no held-notification text, no Health Connect reading,
and no contact list ever leaves the phone. The
"sign-in" features (Google Drive backup, COROS
side-channel) sign in to the third-party service
through that service's own API; the data that crosses
the wire is the data the third-party service already
sees, not the data the launcher holds.

This is a clinical surface
--------------------------

Wording changes are reviewed by a clinician before they
ship. The clinical-review log at
`docs/CLINICAL_REVIEW.md` is the public record. The
review is not a marketing claim — it is a process. A
future wording change that does not appear in the log
flips the build red.
```

The long description is ~2,500 characters, well under the
4,000-character ceiling. The wording was chosen to make
the privacy promise concrete (which data crosses the
wire, and which does not) and to keep the
"clinical-review" framing first-class — the reviewer
reading the listing should know the wording is not
arbitrary.

## Screenshots (8 phone screenshots)

The Play Store listing accepts up to 8 phone
screenshots. Placeholders are committed at
`fastlane/metadata/android/en-US/phoneScreenshots/`
(see the directory once created; the v0.25.19 release
ships with 8 placeholder PNGs at the resolution
`1080x1920`).

The planned shots, in order:

1. **Home surface** — clock, greeting, 5 home cards
2. **Friction gate** — the one-breath pause with a
   typed intention
3. **Notification digest** — the held-notifications
   release view
4. **Letter reader** — a letter in the inbox
5. **Pulse check-in** — a WHO-5 question
6. **2am shell** — the dim home surface
7. **Settings — Quiet** — batching + quiet hours
8. **Support** — the three-option "ground me" surface

## Feature graphic (1024x500)

A placeholder PNG is at
`fastlane/metadata/android/en-US/featureGraphic.png`.
The actual feature graphic is a future work item; the
text "MindAnchor" in the launcher's brand font on a
calm background, with a single sentence in the
launcher's voice.

## Content rating

```
Everyone
```

The rating is set through the Play Console's
"Content rating" questionnaire. The launcher has no
violence, no suggestive content, no drugs, no
gambling, no user-generated content (the note feature
is local), and no social features. The questionnaire
classifies the app as "Everyone" without any
age-gating.

## Privacy policy URL

```
https://sampathmannam.github.io/MindAnchor/privacy.html
```

The URL is a placeholder. The actual privacy policy
page is a future work item; the existing in-app
`AboutScreen` and `docs/CLINICIAN_PACK.md` are the
substantive content. The privacy policy URL is what the
Play Store displays on the listing and what the
in-app `AboutScreen` opens.

## Contact email

```
sampathmannam+mindanchor@gmail.com
```

The `+mindanchor` suffix is a Gmail filter; emails
to this address land in a label, separate from the
developer's personal inbox. The address is the public
contact for the listing and the in-app "Send feedback"
target.

## Languages

The Play Store listing ships in English (default) and
Tamil (`values-ta/`). The fastlane metadata for the
Tamil locale is `fastlane/metadata/android/ta/*`. The
wording-review gate is the same as the app strings:
every wording change must be added to the
clinical-review log before merge.
