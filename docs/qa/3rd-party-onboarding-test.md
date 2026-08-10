# 3rd-party onboarding walkthrough — script

## Why this exists

WP-10 of the v0.21.0 10/10 roadmap: **onboarding is clear in 60
seconds.** A 3rd party who has never seen the app should be able to
read the home screen, identify the headline feature, understand the
privacy story, and know where to find the "magic" — in under a
minute.

This document is the script. Two non-developers should be timed
through it; both timings should land under 60 seconds. If they
don't, the failure is *us*, not the user.

## Setup

- An Android phone with the debug APK installed.
- The launcher set as the default home screen.
- Fresh state (uninstall + reinstall between test sessions).
- The participant does *not* see this script, the v0.21.0 release
  notes, or any prior version of the app.
- The participant is told only: "This is a launcher. Open it."

## What "magic" means here

The v0.21.0 launcher's headline feature is the **friction gate**
— the one-breath + intention prompt that appears before a flagged
app opens. The setting where a person adds an app to the gate is
the magic setting. A successful walkthrough ends with the
participant able to:

1. Name the headline feature ("the pause before opening apps" or
   equivalent).
2. State the privacy promise in plain words ("nothing leaves the
   phone" or equivalent).
3. Locate the friction gate setting (long-press any app → "Add a
   pause" works without explanation; the settings → pauses path
   is the alternative).

## The script

The participant is **not** told any of these steps. They are told
"open the launcher, do whatever feels natural, and tell me when
you're done." Time starts when they tap the launcher icon. Time
ends when they have named the headline feature and the privacy
promise, in any order.

### Observations the proctor makes

- First surface they look at: home screen, settings, app drawer,
  other?
- First thing they tap: what? (The launcher intentionally has
  nothing to tap at the centre; the first reach tells us whether
  the home screen is too empty or just right.)
- Time to "what does this do" (first question, however phrased).
- Time to "oh, the friction gate" (or equivalent).
- Time to "where does my data go" (or equivalent).
- Final ask they have that the app didn't answer.

### The four onboarding screens, in case the participant asks

These exist for the proctor's reference, not the participant's.
The proctor's job is to *not* help unless asked.

1. **Welcome.** "A calmer phone, built from published research. No
   accounts, no server — everything stays on this device. You
   choose what turns on; nothing is imposed."
2. **Goals.** "What pulls at you? Pick what fits." Four checkboxes.
   The proctor should note whether the participant reads each
   label or just clicks "continue".
3. **Chronotype.** "When are you most awake?" Four radios. The
   "I work shifts and sleep during the day" option is the
   differentiator: a participant who reads all four options is
   a participant the design is working for.
4. **Plan.** "Your plan — switch each on when ready:" The screen
   the participant lands on. A successful walkthrough is one
   where the participant taps "begin" without re-reading the
   earlier screens.

### Common participant moments to watch for

- **"Where is the app drawer?"** — the launcher *is* the app
  drawer, by long-press on a blank space or by typing. If the
  participant looks for a separate drawer, the design has
  failed.
- **"How do I search for an app?"** — the search affordance is
  at the top of the home screen, labelled "search" in plain
  text. A participant who taps the bottom of the screen looking
  for an icon is a design failure.
- **"What does the wind-down setting do?"** — the sunset mode
  is the second-most-asked-about feature. The proctor should
  note *when* the participant asks: if it's at the onboarding
  stage, the chronotype default is doing its job; if it's
  twenty minutes in, the home screen hasn't surfaced it.

## Acceptance

- Two 3rd-party walkthroughs complete in under 60 seconds each.
- Both participants can name the friction gate, the privacy
  promise, and where to find both in settings.
- The two sessions surface at least one observation the proctor
  had not anticipated; that observation goes into a follow-up
  doc and the timeline of next iteration.

## What this script does NOT do

- It does not measure retention. A walkthrough that takes
  45 seconds but leaves the participant unsure of what the app
  *is for* is a failure even though the time is good.
- It does not measure aesthetic preference. A launcher that
  someone *likes* but cannot navigate in 60 seconds is still
  not a launcher.
- It does not test the chronotype step's *correctness*. The
  chronotype's job is to put a sensible default in front of the
  user; whether the default is the *right* one for that user is
  a 2-week live test, not a 60-second walkthrough.

## Where the results go

After both walkthroughs:

- Timing results, with the participant's age and prior
  Android-launcher familiarity, go into this file under
  "Results (v0.21.0)".
- Each proctor observation becomes a line in
  `docs/qa/launcher-walkthrough-observations.md` (one file
  per version), so a regression in v0.22.0 against v0.21.0 is
  diff-able.
- Any failure of acceptance goes into a new WP in the 10/10
  plan, with the *participant* (not the dev) named as the
  source of the regression.

## Results (v0.21.0)

*Pending first walkthrough. Two proctors scheduled for the v0.21.0
release window. Script as written above; no walkthroughs run yet
under this document.*
