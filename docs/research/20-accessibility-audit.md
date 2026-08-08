# 20 — Accessibility audit on FrictionGate and home screen

## Why this brief

The senior-architect review noted that the FrictionGate
has no `contentDescription` and no `Modifier.semantics`
integration I could find. A blind user cannot tell what
the gate is asking. This is the single biggest
*correctness* issue in the product — not a quality
issue, but a population-correctness one. The mental-
health population has a higher rate of visual
impairment, cognitive load issues, and motor-control
issues than the general population, and the existing
FrictionGate has no accessibility integration.

## Primary research

- WCAG 2.2 SC 1.1.1 (Non-Text Content): "All
  non-text content that is presented to the user has
  a text alternative that serves the equivalent
  purpose." Reference: https://www.w3.org/TR/wcag2mobile-22/
- WCAG 2.2 SC 4.1.2 (Name, Role, Value): controls
  must have a name that describes their purpose.
- Android Accessibility documentation:
  https://developer.android.com/guide/topics/ui/accessibility/apps
  - 48dp x 48dp minimum touch target.
  - 4.5:1 text contrast ratio for body text, 3:1 for
    non-text elements.
  - "Use the Role semantics property (like Role.Button
    or Role.Switch) to expose a UI element's type."
- CVS Health Android Compose accessibility
  techniques: https://github.com/cvs-health/android-compose-accessibility-techniques
  - "Purely decorative non-text content that conveys
    no meaning should be marked as such with a null
    contentDescription."
  - "Use Modifier.semantics(mergeDescendants = true) { }
    on the enclosing group layout (and nulling out the
    non-text content's contentDescription) ... the
    simplest way" to group content for a single
    TalkBack announcement.
  - "Use Modifier.clearAndSetSemantics to remove the
    existing semantics of a composable *and all of its
    child composables* and replace it with the
    semantic values supplied by a lambda."

## What this PR ships

1. `Modifier.semantics(mergeDescendants = true) { }` on
   the three FrictionGate sub-composables
   (`FeatherGate`, `BreathingPause`, `IntentionPrompt`).
   Each composite is announced as a single unit by
   TalkBack, not as a stream of individual text and
   buttons.

2. `contentDescription` on every interactive element
   that does not have adjacent text:
   - The breathing circle in `BreathingPause` (a
     decorative animation that conveys the current
     phase; TalkBack announces the phase text, so
     the circle itself is decorative and gets
     `contentDescription = null`).
   - The "never mind" exit button in each sub-
     composable gets an explicit Role.Button and a
     content description that names the action.
   - The "small thing" text button in `IntentionPrompt`
     gets a `Role.Button` and a content description
     that includes the small thing text (so TalkBack
     reads both the role and the content).

3. `Modifier.semantics { contentDescription = ... }`
   on the AppIcon row in `HomeScreen`, with the app
   label and the action ("Open Chrome" rather than
   just "Chrome"). The existing Text composable's
   text is preserved as the visual label; the
   semantics layer adds the action description.

4. `LiveRegion` semantics on the FrictionGate's
   tone change (when the gate transitions from
   `BreathingPause` to `IntentionPrompt`). A blind
   user who has the gate in the background of their
   screen reader should hear "the breath is done,
   what are you opening?" without having to swipe to
   find it.

5. `AccessibilityAnnouncement` on the 5/10/20 minute
   time-box buttons. The button text is "5 minutes"
   but a screen reader user benefits from "Open for 5
   minutes" — the action precedes the duration.

6. `AccessibilityAction` for the "small thing taken"
   button. The current text is just the small-thing
   text ("two minutes outside"); the semantics layer
   adds the action label "Take the small thing
   instead of opening."

7. `Modifier.semantics { role = Role.Switch }` on
   the Going Light toggle (a follow-up; this PR
   prepares the pattern but does not wire the toggle
   itself, which is a separate surface).

8. New strings file additions for the
   `contentDescription` values. These are clinical-
   review-required (item B+K gate will block them).

## What this PR does NOT ship

- A full TalkBack manual-test pass. This is a
  structural limitation of the sandbox (no Android
  device). The tests are JVM-side; the runtime
  test is the project owner's responsibility.
- Color contrast auditing. The project already uses
  Material 3 theme tokens which are AA-compliant by
  default; a follow-up could add a per-component
  contrast checker.
- Larger touch targets. The 48dp minimum is
  Material 3 default; the project does not have
  custom-sized interactive elements I could find.
  A follow-up could add a per-element audit.

## Risk

- The `contentDescription` strings are user-facing
  and clinical-review-required. The clinical-review
  gate (item B+K) blocks the strings.xml change.
- A blind user may not get the *visual* cues the
  sighted design assumes (e.g. the breathing circle
  grows and shrinks). The `LiveRegion` on the phase
  text is the screen-reader equivalent: the phase
  text changes from "in" to "sip" to "out" in time
  with the visual animation.

## Verification

- 5 new tests in
  `app/src/test/java/org/mindanchor/accessibility/
  FrictionGateAccessibilityTest.kt`:
  1. FeatherGate has a mergeDescendants semantics
     modifier
  2. BreathingPause has a mergeDescendants semantics
     modifier and a null contentDescription on the
     breathing circle
  3. IntentionPrompt has a mergeDescendants semantics
     modifier
  4. The "never mind" button has a Role.Button
     semantic
  5. The time-box buttons have explicit
     contentDescription strings ("Open for 5 minutes",
     etc.)
- Brace/paren balance re-checked on all changed
  files.
- strings.xml apostrophe check on the new content-
  description strings.

## Primary sources

- WCAG 2.2 (W3C Recommendation, mobile guide),
  https://www.w3.org/TR/wcag2mobile-22/
- Android Accessibility documentation,
  https://developer.android.com/guide/topics/ui/accessibility/apps
- CVS Health Android Compose accessibility
  techniques,
  https://github.com/cvs-health/android-compose-accessibility-techniques
