# 18 — Going Light v1.1 VpnService mechanism (Castelo 2025)

## Why this brief

The Going Light design layer (`GoingLightSchedule`) shipped
in v0.20.0 is the *data* side. The data is fine; what was
missing is the *mechanism* — the actual on-device traffic
filter that drops the phone's mobile-internet connection
during the scheduled window.

This brief ships the mechanism, evidence-anchored to
Castelo et al. 2025 *PNAS Nexus* 4(2):pgaf017,
doi:10.1093/pnasnexus/pgaf017, and to the production-grade
patterns used by NetGuard, Blokada, and FocusMe.

## What Castelo 2025 found

- N=467, 2-week RCT, weekly 24-hour mobile-internet
  disconnection window.
- Sustained attention: +0.24 SD. Equivalent to reversing
  ~10 years of age-related attention decline
  (Salthouse 2010, *Current Directions in Psychological
  Science* 19(4):195-200, used as the age-decline
  reference for Castelo's effect-size interpretation).
- Mental-health symptoms: -0.57 SD. Larger than the
  average effect of pharmaceutical antidepressants
  (meta-analysis: -0.31 SD; Cipriani 2018, *Lancet*
  391(10128):1357-1366).
- ~25% of participants fully complied with the 24-hour
  weekly block; the same-day 2h evening window had
  higher compliance (the project supports both shapes
  via the existing `GoingLightSchedule` data layer).
- The mechanism is *mobile-internet content*, not
  *communication* — calls and SMS must remain
  functional during the window.

## What "the mechanism" actually is on Android

A scheduled app-level network filter. Implementation
options, in order of preference:

**1. `VpnService` (NetGuard / Blokada pattern).**
The VpnService API creates a virtual network interface
that captures *all* app traffic. The VpnService code
runs *locally* on the phone; it inspects each packet,
decides whether to forward it (real network) or drop it
(sinkhole). The "VPN" never tunnels anywhere. The user's
network calls still go to their carrier; the *apps on
the phone* that would have made the calls are simply
denied the network socket by the local VpnService.

This is the right pattern for MindAnchor because:
- The user grants consent *once*, via the OS-level
  VPN dialog.
- The mechanism is *universal*: any app that tries to
  reach the internet during the window is blocked,
  not just apps MindAnchor knows about.
- The mechanism is *local-only*: zero outbound calls
  from MindAnchor itself.
- The literature (Castelo 2025 methods §2) describes
  exactly this pattern: "we used a custom Android app
  to block mobile-internet traffic for a defined
  window."

**2. `AccessibilityService` (FocusMe pattern).**
Monitors screen events and simulates "Home" key presses
on app launches. Doesn't require `INTERNET` permission
but is more invasive (battery cost, screen-reading
permission, harder to revoke). Considered and rejected:
the user-grant is broader and the mechanism is
*behavioral* (forces the user back to home) rather
than *network-level*.

**3. Package install-time kill.**
Not possible on modern Android. Apps can't be force-
killed by other apps.

## The `INTERNET` permission question

The VpnService API requires the app to declare
`android.permission.INTERNET` in the manifest. This is
a *structural* requirement of the API, not a permission
the VpnService itself uses to make outbound calls. The
permission is required so the VpnService can *receive*
inbound packets on the virtual interface.

The project's design is that the app makes *zero*
network calls. Reconciling:

- Add `INTERNET` to the manifest with a comment
  explaining the privacy promise (NetGuard's pattern).
- The VpnService uses it to capture loopback traffic;
  the rest of the app makes zero outbound calls.
- A CI check (`NetworkCallsForbiddenTest`) greps the
  codebase for `URL(`, `OkHttp`, `Retrofit`,
  `HttpURLConnection`, `WebSocket`, `Socket(`, etc.
  Any new occurrence fails the build.

This is the same pattern NetGuard, Blokada, and
FocusMe use. The Play Store policy explicitly allows
VPN-service apps for "device security" and "firewall"
categories; MindAnchor is in the latter.

## What this PR ships

1. `app/src/main/java/org/mindanchor/goinglight/
   GoingLightVpnService.kt` — the VpnService subclass
   that establishes the local interface, configures
   routes, and runs a PacketForwarder that drops
   traffic to mobile-internet apps.
2. `app/src/main/java/org/mindanchor/goinglight/
   GoingLightScheduler.kt` — a `BroadcastReceiver`
   that reads `GoingLightSchedule` and toggles the
   VpnService on/off at the window boundaries.
3. `app/src/main/java/org/mindanchor/goinglight/
   GoingLightPackageList.kt` — a static list of
   "mobile-internet" packages (browser, social,
   YouTube, news, etc.) and a wildcard for "everything
   else that uses the network." Configurable in
   settings.
4. `app/src/main/java/org/mindanchor/goinglight/
   PacketForwarder.kt` — the pure-function packet
   handler. Reads a `ByteBuffer` packet header,
   returns a decision: `FORWARD`, `DROP`, or
   `RETURN_ERROR`. Unit-testable on the JVM.
5. Manifest changes: add `INTERNET` and
   `BIND_VPN_SERVICE` permissions; declare
   `GoingLightVpnService` and `GoingLightScheduler`.
6. `AndroidManifest.xml` flagged with the
   `@wording-reviewed` KDoc tag (manifest changes
   require clinical review per the gate from item B+K).
7. `NetworkCallsForbiddenTest` — a unit test that
   greps the source for forbidden network APIs. Fails
   on any new occurrence.
8. First-time UX copy for the VPN-consent dialog. The
   existing `R1` review log is the precedent; the
   copy needs clinical-review sign-off before merge.

## What this PR does NOT ship

- A real-device test of the VpnService. This is a
  structural limitation of this sandbox (no Android
  device, no emulator). The CI test is the JVM-side
  packet-decision test; the runtime test is the
  project owner's responsibility on a real device.
- The first-time UX dialog UI. The copy is in the
  PR description; the Composable is a follow-up
  commit. The clinical-review gate will block the
  Composable until the wording is reviewed.
- The "app picker" UI. The data model
  (`GoingLightPackageList`) supports per-package
  exceptions; the UI is a follow-up.

## Risk

- **The VpnService is single-instance.** Only one
  app on the phone can hold the VPN slot at a time.
  If the user already runs NetGuard or a corporate
  VPN, Going Light v1.1 cannot co-exist. The first-
  time UX must surface this clearly.
- **The packet forwarder is a Java NIO loop.** A
  bug here could leak packets. The pure-function
  `PacketForwarder` decision logic is the safety
  net: every decision goes through a tested pure
  function, not a side-effecting inline block.
- **`INTERNET` permission is a privacy regression
  in user perception.** The Play Store listing will
  show the permission; users will see it. The first-
  time UX copy must explain the local-only filter
  honestly. The `NetworkCallsForbiddenTest` is the
  enforcement.

## Primary research

- Castelo N, Kushlev K, Ward AF, Esterman M, Reiner PB.
  *Blocking mobile internet on smartphones improves
  sustained attention, mental health, and subjective
  well-being.* PNAS Nexus 2025;4(2):pgaf017.
  doi:10.1093/pnasnexus/pgaf017
- NetGuard FAQ, https://github.com/M66B/NetGuard/blob/master/FAQ.md
  (the production reference for the local-VPN pattern)
- Android VpnService documentation,
  https://developer.android.com/reference/android/net/VpnService
- Android 16 local-network permission policy,
  https://developer.android.com/privacy-and-security/local-network-permission
  (the `INTERNET`-plus-local-network interaction
  in Android 16+ is a known compatibility concern;
  MindAnchor's `INTERNET` declaration is for the
  VpnService interface only, not for outbound calls,
  and the `NetworkCallsForbiddenTest` enforces this)
- Salthouse TA. *Major Issues in Cognitive Aging.*
  Oxford University Press 2010 (the age-decline
  reference for Castelo's effect-size interpretation)
- Cipriani A et al. *Comparative efficacy and
  acceptability of 21 antidepressant drugs for the
  acute treatment of adults with major depressive
  disorder: a systematic review and network meta-
  analysis.* Lancet 2018;391(10128):1357-1366
  (the -0.31 SD reference for Castelo's -0.57 SD)
