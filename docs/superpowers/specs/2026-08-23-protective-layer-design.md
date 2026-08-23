# MindAnchor Protective Layer — Design Spec (v0.26+)

**Status:** Draft for user review
**Date:** 2026-08-23
**Target:** v0.26+ (6-9 month build)
**Source:** Brainstormed with user on 2026-08-23

## Context

Modern Android phones optimize for engagement, not well-being. The user unlocks, opens Instagram, and 90 minutes vanish. Every notification fights for attention. Default apps are pre-installed for vendor profit, not user privacy.

MindAnchor v0.25.20 is a launcher with mood, sleep, letters, and auto-update. Its primary metric is mental-health self-tracking. But the *bigger* mental-health leverage is owning the three surfaces that drive doom-scrolling:

1. **The moment of unlock** (lock screen → home) — Phase 1
2. **The notification shade** (which notifications get through) — Phase 2
3. **The default-app layer** (which apps are first-class) — Phase 3

This spec extends MindAnchor to own all three. It does not replace v0.25.20's self-tracking capabilities; it builds on them.

## Goal (6-month primary metric)

**Reduce doomscroll session frequency and duration.** Measured by:

- **Behavioral:** AccessibilityService-captured notification-demotion count; PreHome breath-then-open rate
- **Self-reported:** Weekly reflection question "Did you open Instagram/YouTube/Twitter when you didn't mean to?" (added to the existing Letters flow)
- **Battery:** AccessibilityService overhead < 5%/day

**Secondary:** Do not regress the v0.25.20 mental-health self-tracking rate (mood, sleep, letters). The protective layer is additive.

## Non-goals

- **No cloud.** All data on-device, encrypted with Android Keystore.
- **No AI-driven intervention.** No LLM, no behavioral scoring. Just "is this package in the doomscroll list."
- **Not a lock screen replacement.** The system keyguard still gates authentication. PreHome is a moment-of-pause *after* unlock, before the home.
- **Not forcing defaults.** We walk the user to system default-app settings; we don't override them.

## Risks

- **AccessibilityService battery cost** — long-running services that monitor notifications drain battery. Mitigation: batched event handling (`notificationTimeout=500ms`), active-hours gating (default 21:00-07:00), no wake lock, foreground service with explicit "MindAnchor is curating notifications" notification (Android 14+ requirement). Battery target: <5%/day. If exceeded, options are shorter active hours, lower held-retention, user-tunable.
- **AccessibilityService consent drop-off** — the permission is the most distrusted on Android. Onboarding will lose users. Mitigation: plain-English consent screen that explicitly states what we do and don't do (we read notifications only from doomscroll apps; we never read your messages; we don't send your data anywhere), single-screen activation, no nag.
- **OEM lock-screen widget support varies** — Pixel supports lock-screen widgets; Samsung One UI is limited; Xiaomi MIUI, Oppo ColorOS, Vivo OriginOS vary. Mitigation: widget renders on home always; lock-screen rendering is opportunistic; runtime detection via `AppWidgetManager.isRequestPinAppWidgetSupported` and the keyguard widget feature flag.
- **PreHome as launcher might confuse users coming from v0.25.20** — they expect the existing home. Mitigation: "Always show PreHome" toggle in Settings (default ON for v0.26); a single-tap "Skip to home" on PreHome; release notes explain the change.
- **DoomscrollList is opinionated** — Instagram, YouTube, Twitter, Reddit, TikTok, Snapchat, Facebook. Some users will disagree. Mitigation: user-editable in Settings; per-package toggle: "prompt me before opening" / "never prompt" / "always open".

## Architecture (3 phases, 6-9 months)

### Phase 1: PreHome moment-of-pause (Months 1-3, no new permissions)

**Purpose:** Insert a brief reflective moment between unlock and home.

**Components:**

- **`PreHomeActivity`** — fullscreen Activity, new launcher entry point
  - Replaces the existing `HomeActivity` as the `MAIN`/`HOME` intent target
  - Shows: time, date, today's morning intention (editable inline)
  - 3-second breath animation, then transitions to `HomeActivity`
  - Settings toggle: "Always show PreHome" (default: ON for v0.26)
  - If disabled, `HomeActivity` launches directly (existing behavior unchanged)
  - Skip button always available
- **`MorningIntentionRepository`** — persists today's intention; new day = blank
  - Backed by DataStore (Preferences)
- **`MorningIntentionWidget`** — `AppWidgetProvider`, `widgetCategory="keyguard|home_screen"`
  - Renders on home screen always
  - Renders on system lock screen if OEM supports (Pixel: yes; Samsung One UI: limited; Xiaomi MIUI: limited)
  - Detection: `AppWidgetManager.isRequestPinAppWidgetSupported` + keyguard-widget feature flag
- **`DoomscrollPromptDialog`** — when user taps an app from PreHome, if the app is in the doomscroll list:
  - 3-second breath animation
  - Three options: "Open anyway" / "Hold for 1 min" / "Pick a different app"
  - "Hold for 1 min" prevents the app from being launched for 60s (a soft reprieve; after 60s the tap is forgotten)
- **`DoomscrollList`** — hardcoded default: Instagram, YouTube, Twitter, Reddit, TikTok, Snapchat, Facebook
  - User-editable in Settings → "Doomscroll list"
  - Per-package toggle: "prompt me before opening" / "never prompt" / "always open"

**Permissions:** None new. Default home + Android system APIs.

**BPD-safe copy:**

- PreHome greeting: "Today is a new page. What's one thing you'd like it to be about?" (validate-then-suggest, no directive)
- Doomscroll prompt: "You tapped Instagram. Just checking — is that what you wanted? You can open it, or take a breath first."
- Morning intention: never "you should..." language; always "what would you like..."
- Skip button: "Skip to home" (no judgment, no "are you sure?")

**Key files:**

- `app/src/main/java/.../prehome/PreHomeActivity.kt`
- `app/src/main/java/.../prehome/MorningIntentionRepository.kt`
- `app/src/main/java/.../prehome/DoomscrollPromptDialog.kt`
- `app/src/main/java/.../prehome/DoomscrollList.kt`
- `app/src/main/java/.../widget/MorningIntentionWidget.kt`
- `app/src/main/res/xml/prehome_widget_info.xml`
- `app/src/main/AndroidManifest.xml` (re-point HOME intent to PreHomeActivity)
- `app/src/test/java/.../prehome/*` (unit tests)

### Phase 2: AccessibilityService notification curate (Months 4-6, 1 scary permission)

**Purpose:** Demote doomscroll-app notifications to a "Held for later" batch.

**Components:**

- **`MindAnchorAccessibilityService`** — `AccessibilityService`
  - Listens for `AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED`
  - On notification from a doomscroll package: extract (title, text, package, timestamp), dismiss, persist
  - Active hours: configurable (default 21:00-07:00); outside hours, service is suspended via `Service.setLifecycleRequest(SERVICE_LIFECYCLE_STOP)`
  - Battery mitigations:
    - `notificationTimeout=500ms` (batch events)
    - `FLAG_RETRIEVE_ACTIVE_NOTIFICATIONS` only when HeldNotificationsScreen is open
    - Outside active hours, the service is suspended
    - No wake lock
    - Foreground service notification (Android 14+ requirement): "MindAnchor is curating notifications" with a clear "open" action
- **`HeldNotificationsRepository`** — Room DB, encrypted via SQLCipher
  - Schema: `id, package, title, text, receivedAt, dismissedAt`
  - Retention: 7 days (auto-prune)
  - Encryption key: Android Keystore-derived, device-bound
- **`HeldNotificationsScreen`** — Compose UI, accessible from home via a "12 held" card
  - Shows: list with package icon, title, timestamp
  - Per-row: "Open" (launches the source app) / "Dismiss" (delete from DB)
  - Grouped by package, sorted by receivedAt desc
- **`AccessibilityServiceConsentActivity`** — explains what we do, requests consent, then sends user to `Settings.ACTION_ACCESSIBILITY_SETTINGS`
  - Plain English: "MindAnchor can hold back notifications from apps that hurt your attention. We never read your messages from other apps. We don't send your data anywhere. This costs a small amount of battery — usually less than 5% per day."
  - One-time toast on first demote: "MindAnchor held N notifications. See them in Held for later." (shown once, never again)
- **Settings:**
  - "Enable MindAnchor notification curate" — opens system accessibility settings
  - Active hours (default 21:00-07:00)
  - Per-app demote toggle (default list + add/remove)
  - Held retention: 1-7 days (default 7)

**Permissions:** `BIND_ACCESSIBILITY_SERVICE` with:

- `eventTypes=TYPE_NOTIFICATION_STATE_CHANGED` (only notification events)
- `canRetrieveWindowContent=false` (we do NOT read other apps' content)
- `flagDefault=FLAG_INCLUDE_NOT_IMPORTANT_VIEWS|FLAG_REPORT_VIEW_IDS`

**BPD-safe copy:**

- Consent screen: see above (validate-then-suggest, plain English, no coercion)
- "Held for later" label, never "Blocked" (gentler)
- Empty state: "No held notifications. Enjoy your focus." (no "you've been good!" judgment)
- Held list rows: tap-to-expand reveals full text; "Open" is a clear, non-judgmental action
- Foreground service notification: "MindAnchor is curating notifications. Tap to configure." (factual, not nagging)

**Key files:**

- `app/src/main/java/.../accessibility/MindAnchorAccessibilityService.kt`
- `app/src/main/java/.../accessibility/AccessibilityServiceConsentActivity.kt`
- `app/src/main/java/.../held/HeldNotificationsRepository.kt`
- `app/src/main/java/.../held/HeldNotificationsScreen.kt`
- `app/src/main/java/.../held/HeldNotificationEntity.kt`
- `app/src/main/java/.../held/HeldNotificationDao.kt`
- `app/src/main/res/xml/accessibility_service_config.xml`
- `app/src/main/AndroidManifest.xml` (add accessibility service)
- `app/src/test/java/.../accessibility/*` (unit tests)
- `app/src/androidTest/java/.../accessibility/*` (instrumented tests)

### Phase 3: Healthy defaults walkthrough (Month 7, no new permissions)

**Purpose:** Make it easy for the user to set their default apps to privacy-respecting alternatives.

**Components:**

- **`HealthyDefaultsActivity`** (in Settings → "Healthy defaults")
  - For each category: browser, SMS, email, dialer, share-sheet
  - Shows: current default + recommendation
  - "Change" button → deep-links to system default-app settings via `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` (or category-specific intents where supported)
- **`HealthyDefaultsRepository`** — DataStore (Preferences), persists user's per-category recommendation status
- **Recommendations** (hardcoded, no auto-install):
  - Browser: DuckDuckGo
  - SMS: Signal (or default Messages if user prefers)
  - Email: K-9 Mail, FairEmail
  - Dialer: default (no good alternative)
  - Share-sheet: depends on system
- Recommendations presented as "we like this" not "you should switch"

**Permissions:** None new. Just system intents.

**Key files:**

- `app/src/main/java/.../settings/HealthyDefaultsActivity.kt`
- `app/src/main/java/.../settings/HealthyDefaultsRepository.kt`
- Update `SettingsActivity` to add "Healthy defaults" entry
- `app/src/test/java/.../settings/*` (unit tests)

## Data Flow

```
User unlocks phone (system keygate)
  ↓
PreHomeActivity (Phase 1)
  - Shows: morning intention + 3-sec breath
  - User taps an app
  - If doomscroll: DoomscrollPromptDialog
  - User opens, holds, or picks different
  ↓
While app is open, notifications arrive
  - MindAnchorAccessibilityService (Phase 2) intercepts doomscroll notifications
  - Saves to HeldNotificationsRepository, dismisses
  - User can view in HeldNotificationsScreen from home
  ↓
HomeActivity (existing v0.25.20 features)
  - Mood, weather, anchor note, letters
  - "12 held" card → HeldNotificationsScreen
  - Today's intention summary
  ↓
Settings → Healthy defaults (Phase 3)
  - Per-category walkthrough to system default-app settings
```

## Error Handling

- AccessibilityService disabled by user → graceful degradation: PreHome + Healthy defaults still work, "12 held" card is hidden
- HeldNotificationsRepository full → auto-prune to 7 days, never throws
- MorningIntentionWidget not supported by OEM lock screen → widget still renders on home, lock-screen rendering is opportunistic
- PreHomeActivity crashes → Android system fallback to HomeActivity (existing)
- DoomscrollList package not installed → ignore, no error
- `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` not available on this OEM → show a fallback with manual instructions

## Testing

- Unit tests: all ViewModels, Repositories, DoomscrollList, MorningIntentionRepository, HeldNotificationsRepository
- Instrumented tests: PreHomeActivity launch + breath, AccessibilityService notification demotion, HealthyDefaults deep links
- Smoke test on phone ZD2232FCR5 (Android 17): Phase 1 cold-start, Phase 2 notification flow with Instagram, Phase 3 deep links

## Open Questions (resolve in plan, not spec)

- Battery budget: target < 5%/day. If real-world usage exceeds, options are shorter active hours, lower held retention, or user-tunable.
- Default for "Always show PreHome": ON for v0.26, with skip button. If users uninstall in protest, OFF default in v0.27.
- Lock screen widget OEM support: needs runtime detection; Pixel stock is the reference.
- DoomscrollList defaults: is the initial list (Instagram, YouTube, Twitter, Reddit, TikTok, Snapchat, Facebook) the right starting set? Or should we start with just 2-3 and let users add?

## Privacy

- No network endpoints. No analytics. No cloud sync.
- HeldNotificationsRepository: encrypted with SQLCipher; key in Android Keystore, device-bound
- AccessibilityService reads notifications only from packages in DoomscrollList
- Update `docs/PRIVACY.md` with AccessibilityService scope, retention, on-device storage

## Rollout

- Phase 1: v0.26, "Always show PreHome" toggle (default ON, with skip)
- Phase 2: v0.27, requires user to enable in system accessibility settings
- Phase 3: v0.27, no opt-in required

## Timeline

| Phase | Months | Deliverable |
|-------|--------|-------------|
| 1 | 1-3 | PreHome, DoomscrollPrompt, MorningIntentionWidget, DoomscrollList |
| 2 | 4-6 | AccessibilityService, HeldNotificationsRepository, HeldNotificationsScreen |
| 3 | 7 | HealthyDefaultsActivity, HealthyDefaultsRepository |
| Buffer | 8-9 | Stabilization, OEM testing, accessibility-service battery tuning |

Total: 6-9 months. Matches user-stated timeline.
