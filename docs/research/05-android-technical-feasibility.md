# SOTA Survey 5: Android Technical Feasibility (API 33–36, as of 2025-08)

> Pre-build engineering research for MindAnchor. Verdict up front: **no un-grantable
> blockers found** — every core v1 feature is achievable with user-grantable special
> accesses, with documented degraded modes.

## 1. Notification batching mechanics

`NotificationListenerService` (NLS) gives `onNotificationPosted`, `cancelNotification(key)`, `snoozeNotification(key, durationMs)`, `getActiveNotifications()`. Two viable strategies:

- **Snooze**: system hides the notification and re-posts *the original* at timeout — MessagingStyle, direct-reply actions, app identity, channels all survive. Caveats: no clean public "unsnooze-now" (duration picked up-front; early release = re-snooze bookkeeping), per-notification not per-app, OEM bugs reported. https://developer.android.com/reference/android/service/notification/NotificationListenerService
- **Cancel + re-post copy** (what Mindful does — verified from source): `cancelNotification(sbn.key)`, persist title/content to DB, cache the original `contentIntent` PendingIntent (Mindful: LRU 100, 24h TTL — PendingIntents are tokens that still fire as the origin app), then at release time re-post copies from your own app: per-thread `MessagingStyle` notifications grouped per package + `InboxStyle` group summary, contentIntent = cached original → app launch intent → own app (see `MindfulNotificationListenerService.kt`, `NotificationBatchReceiver.kt`). **What breaks**: notifications appear under *your* app identity/small icon (unspoofable), direct-reply `RemoteInput` actions are lost (re-attaching copied `Notification.Action`s is fragile), custom RemoteViews lost. Mindful skips `isOngoing`, non-clearable, and `FLAG_GROUP_SUMMARY` notifications — mandatory (foreground-service notifications can't be cancelled by listeners anyway).
- **Channel manipulation**: dead end — listeners can only modify channels of companion-device-associated apps; NotificationAssistantService is OEM-signature-only.
- **Sender tiering**: read `EXTRA_MESSAGES`/`MessagingStyle` persons, `Notification.category == CATEGORY_MESSAGE`, shortcut/conversation id. "Humans pass instantly" = simply don't cancel.
- **OS changes**: Android 15 redacts OTP/2FA content from untrusted listeners (`RECEIVE_SENSITIVE_NOTIFICATIONS` is role/signature-gated) — never batch OTP-adjacent notifications; pass through anything the OS marks sensitive. Android 16 adds forced per-app bundling + notification cooldown — partial overlap; re-posts will themselves get auto-grouped. Bundel (Kotlin/Compose/Hilt/Room, Apache-2.0) is dormant and never shipped batching end-to-end — architecture reference only.

## 2. Launcher role

Standard `ACTION_MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT` intent filter; prompt with `RoleManager.createRequestRoleIntent(ROLE_HOME)` (API 29+). No special permission. Constraints: third-party launchers get degraded gesture-nav animations/predictive back on some OEMs (unfixable app-side); cannot replace lock screen; widgets need `AppWidgetHost` (skip for v1). Olauncher/mLauncher patterns: hiding = filtering the `LauncherApps` list; renaming = local label overrides; text list instead of icons; optional AccessibilityService solely for double-tap-lock (`GLOBAL_ACTION_LOCK_SCREEN`) — keep optional.

## 3. App gating / friction

- **UsageStats polling + overlay**: Mindful's `LaunchTrackingManager` polls `UsageStatsManager.queryEvents` every **750 ms** while unlocked (paused on screen-off), from a `specialUse` FGS, drawing a `SYSTEM_ALERT_WINDOW` overlay. Latency ~0.75–1.5 s (target app flashes first), modest battery, no Play-sensitive API beyond `PACKAGE_USAGE_STATS`. Open TimeLimit (GPL-3.0) uses the same model + Device Admin for tamper protection.
- **AccessibilityService**: event-driven `TYPE_WINDOW_STATE_CHANGED`, near-instant, enables in-app content blocking (Mindful's `ShortsPlatformManager` blocks Reels/Shorts; DigiPaws is accessibility-first). Mindful runs a **hybrid**: accessibility when enabled, polling fallback otherwise — recommended pattern.
- **Launcher-intercept is the most robust path**: since MindAnchor IS the launcher, intention prompts fire in-process before `startActivity` — immune to background killing, zero latency, zero extra permissions. Polling/accessibility only covers non-launcher entry points (notifications, share sheets).
- **OEM reliability**: Samsung/Xiaomi aggressively kill FGS and even disable accessibility services (dontkillmyapp.com — Samsung rated worst); need battery-optimization-exemption onboarding + self-healing rebind.
- **Policy**: Google Play requires AccessibilityService declaration + prominent disclosure; wellbeing/parental-control uses tolerated, but stricter review from **Jan 28, 2026**; Android 17 reportedly blocks non-accessibility-tool apps in Advanced Protection mode. **F-Droid does not care** (Mindful, DigiPaws, Open TimeLimit all listed). → Architect accessibility as optional enhancement, never core.

## 4. Grayscale

- **Android 15+ (API 35): clean public path.** `AutomaticZenRule` + `ZenDeviceEffects.Builder().setShouldDisplayGrayscale(true)` (also dim wallpaper, night mode) — public API for third-party apps holding DND access. Sunset mode = one Zen rule with grayscale, no shell tricks. https://developer.android.com/reference/android/service/notification/ZenDeviceEffects
- **Android 13/14 fallback**: `Settings.Secure` daltonizer (monochromacy) requires `WRITE_SECURE_SETTINGS` — grantable only via one-time `adb shell pm grant` or Shizuku (Shizuku dies on reboot without wireless debugging; DigiPaws users specifically requested plain-ADB, issue #165). FOSS precedents: zagoa/grayscale, Grayscaler. Ship as optional "power user" step with in-app ADB instructions; otherwise sunset = dark theme + feed lockout without grayscale.

## 5. Scheduling reliability

3×/day release doesn't need second-precision. `SCHEDULE_EXACT_ALARM` **denied by default on Android 14+**; request via `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` settings intent; `USE_EXACT_ALARM` is restricted to alarm-clock/calendar apps — don't use. Mindful's verified pattern: `canScheduleExactAlarms() ? setExactAndAllowWhileIdle : set` → BroadcastReceiver → WorkManager unique work → re-schedule next alarm. Inexact alarms in Doze can slip minutes–hours (acceptable overnight; request exact for user-facing times). The NLS is system-bound (no FGS needed, survives via `requestRebind`); the gating tracker runs as FGS `specialUse` — requires `FOREGROUND_SERVICE_SPECIAL_USE`, manifest subtype explanation, Play Console declaration. The `health` FGS type is fitness-gated — not a fit.

## 6. DND / sunset mode

`ACCESS_NOTIFICATION_POLICY` (user grant) required. Apps **targeting API 35+ can no longer set global DND** — `setInterruptionFilter`/`setNotificationPolicy` silently convert to an implicit per-app `AutomaticZenRule`. Design for `AutomaticZenRule` from day one: one explicit "Sunset" rule (schedule-triggered) with `ZenPolicy` (allow starred contacts/repeat callers → maps directly to the "designated humans" tier) + `ZenDeviceEffects` grayscale on 15+. Android 16 surfaces these as user-visible "Modes". On 13/14 the same code sets global filter.

## 7. Sleep estimation (zero-Google)

`ACTION_SCREEN_ON/OFF` receivable only by context-registered receivers → requires persistent process; OEM killers make this lossy. Better: **`UsageStatsManager.queryEvents` retroactively** — `SCREEN_INTERACTIVE`(15)/`SCREEN_NON_INTERACTIVE`(16), `KEYGUARD_SHOWN`(17)/`KEYGUARD_HIDDEN`(18) give the exact screen/first-unlock timeline; detailed events retained ~7–30 days — ingest daily into Room, no live receiver needed (Mindful's `ScreenUsageHelper` does this). Longest nightly non-interactive gap + first `KEYGUARD_HIDDEN` = sleep window → Sleep Regularity Index. Fully offline, no permission beyond `PACKAGE_USAGE_STATS`. Google's Sleep API is Play-Services-proprietary — avoid.

## 8. Health Connect

`androidx.health.connect:connect-client` (stable, Apache-2.0, talks to the OS module, no GMS dependency — F-Droid-safe). **Part of the OS on Android 14+**; Android 13 needs the Play Store APK → optional feature. Readable: `SleepSessionRecord` (with stages), `HeartRateVariabilityRmssdRecord`, `RestingHeartRateRecord`, ~50 types. Per-type runtime permissions via system screen; 30-day historical limit unless `PERMISSION_READ_HEALTH_DATA_HISTORY`; background reads need `PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND`. Play: triggers the Health apps declaration.

## 9. Keyboard/IME (keystroke dynamics)

Requires being the IME. HeliBoard (GPL-3.0, active) is license-compatible; FlorisBoard (Apache-2.0) also. But IMEs are a whole product — maintaining a fork is a part-time job. **Out of scope for core**; later, propose timing hooks upstream to HeliBoard (metadata only, content never logged) or ship a separate optional APK.

## 10. Store / distribution

- **F-Droid**: everything includable (FLOSS, no binaries, no tracking); accessibility/NLS/QUERY_ALL_PACKAGES precedents exist (Mindful, DigiPaws, Open TimeLimit).
- **Google Play**: `QUERY_ALL_PACKAGES` + `PACKAGE_USAGE_STATS` declaration forms (launchers and app-blockers are accepted use cases); AccessibilityService declaration + prominent disclosure, tightened review from Jan 2026; Health Content policy (effective 2025-08-28) requires Health apps declaration + privacy policy for wellbeing/sleep claims — use "estimates", never "diagnoses"; FGS `specialUse` needs justification. **Posture: F-Droid primary, Play best-effort.**

## 11. Reuse vs build

| Codebase | License | State | Verdict |
|---|---|---|---|
| Mindful (akaMrNagar/Mindful) | **GPL-2.0-only** — NOT compatible with GPLv3 | Active; Flutter UI + solid native Kotlin service layer | Best *reference* for NLS batching, hybrid tracking, overlay, alarm scheduling. Can't copy code unless the (single) author relicenses — asking is viable |
| Olauncher / mLauncher fork | GPL-3.0 | Olauncher semi-maintained; mLauncher active | **Extract**: launcher role handling, hidden/renamed apps, gestures. Views-based — port patterns |
| Siempo | GPL-3.0 | Dead (~2019 Java; founder postmortem published) | UX/concept mining only (intention screen, batching UX); code worthless |
| Bundel | Apache-2.0 (GPLv3-compatible one-way) | Dormant, early-stage | Compose/Hilt/Room NLS scaffolding copyable; batching never finished |
| Kvaesitso | GPL-3.0 | Very active, Kotlin/Compose | Compose launcher plumbing (app list, `LauncherApps`) to crib |
| DigiPaws | GPL-3.0 | Active | Grayscale-via-Shizuku/`WRITE_SECURE_SETTINGS` and content-blocking accessibility code directly reusable |

**Decision: build fresh in Kotlin/Compose; lift GPL-3.0 code from mLauncher/Kvaesitso/DigiPaws/Open TimeLimit; treat Mindful as a design doc (GPL-2.0 wall); use Bundel freely.**

## Hard blockers / degraded-mode fallbacks

- **No un-grantable blockers.** All core features achievable with user-grantable special accesses.
- **Without AccessibilityService** (recommended default): batching ✔ (NLS), launcher ✔, friction ✔ via launcher-intercept + 750 ms polling overlay for other entry points, sunset ✔, sleep ✔. Lost: in-app content blocking (Reels/Shorts), <100 ms gating, double-tap-lock.
- **Without ADB/`WRITE_SECURE_SETTINGS`**: grayscale ✔ on Android 15/16 via ZenDeviceEffects; on 13/14 fall back to dark theme + feed lockout.
- **Without exact-alarm grant**: batch releases drift minutes–hours in Doze — acceptable, or prompt.
- **Android 15 OTP redaction**: pass through anything the OS marks sensitive.
- **OEM killers**: battery-exemption + dontkillmyapp onboarding; NLS auto-rebinds; being HOME is itself an anti-kill strategy.

## Architecture sketch

Single APK, multi-module Gradle; **minSdk 33** (cut legacy paths), target 36.

- `:core` — Room DB (notifications, usage events, settings), DataStore
- `:core-permissions` — onboarding state machine for the ~6 special accesses
- `:feature-notifications` — NLS + tier classifier, batch store, `AlarmManager → Receiver → WorkManager` release pipeline
- `:feature-launcher` — Compose text-first HOME activity, hidden/rename store, **in-process friction screens** (intention/breathing/timebox before `startActivity`)
- `:feature-gating` — `specialUse` FGS: usage-stats poller + overlay; optional `:feature-accessibility` add-on
- `:feature-sunset` — AutomaticZenRule + ZenDeviceEffects (35+) / global filter (33–34) + daltonizer fallback + batch-until-morning flag
- `:feature-sleep` — daily UsageEvents ingest + SRI estimator; later `:feature-healthconnect` (optional)

Manifest permissions: `BIND_NOTIFICATION_LISTENER_SERVICE` (service), `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` (special), `SYSTEM_ALERT_WINDOW`, `ACCESS_NOTIFICATION_POLICY`, `SCHEDULE_EXACT_ALARM`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, optional `WRITE_SECURE_SETTINGS` (ADB), optional `BIND_ACCESSIBILITY_SERVICE`, later Health Connect read permissions.
