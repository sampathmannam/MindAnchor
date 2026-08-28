plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "org.mindanchor"
    // Health Connect 1.1.0 stable requires compileSdk 36+. Bumped
    // here; targetSdk stays at 35 so the app does not opt into
    // Android 16 runtime behaviour yet.
    compileSdk = 36
    // buildToolsVersion 36.0.0 is what installed compileSdk 36
    // ships with locally; the CI runner image installs the same
    // platform via the setup-android step.
    // v0.25.9: AGP requires an exact build-tools version (the "+"
    // suffix is not valid for `buildToolsVersion`, only for the
    // externalNativeBuild { cmake { version = ... } } block).
    // Pinned to 36.0.0. Comment at lines 16-17 above documents the
    // version-shipping-with-CI image.
    buildToolsVersion = "36.0.0"

    // Pinned to the exact version .github/workflows/probe-ndk.yml proved
    // present on the CI runners — the engine build depends on it, and an
    // unpinned NDK is a build that works until the runner image changes.
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "org.mindanchor"
        minSdk = 33
        targetSdk = 35
        // v0.68.0: LLM multi-provider picker (PR #38).
        // Replaces the v0.25.7 single-provider Groq path
        // with Google AI Studio (free, no credit card) +
        // OpenRouter (free, 20+ free models on one key) +
        // Groq (paid, kept for users with an existing key).
        // The Settings → Reading → Daily letter (LLM)
        // section grows a FlowRow of FilterChips with a
        // "✓ Free" suffix on free providers, and a new
        // "Get a [free] {provider} API key" OutlinedButton
        // that launches Intent.ACTION_VIEW on the active
        // provider's signupUrl — the smooth path from the
        // v0.70.0: AnchorCore wellbeing loop (Tasks 1–10) —
        //   DayFact + AnchorState + AnchorCore + SriWeekLedger +
        //   AnchorPrefs + AnchorCoreSource, Hook A (letter
        //   prompt splice), Hook B (friction tone hold), Hook C
        //   (one-card sunset proposal), PreHome open-loop
        //   handback + one-sentence sleep fact, Settings →
        //   Measuring master + per-hook toggles + override
        //   revoke, refresh-on-demand triggers + Hook B
        //   call-site wiring. Zero new permissions; no
        //   network; clinical-review wordlist gate green.
        //   versionCode 92→93.
        // v0.70.1: Health Connect app-list alias
        // (VIEW_PERMISSION_USAGE) — without it Android 14+
        // auto-denies every health permission request in one
        // frame and the launcher never appears in Health
        // Connect's own app list. versionCode 93→95 (94 was
        // consumed by the diverged t31-t32 release commit
        // d01980c; skipping it keeps the two lineages'
        // versionCodes from colliding on the same number
        // with different bits).
        // v0.70.2: second grant step for the two "additional"
        // Health Connect permissions (READ_HEALTH_DATA_IN_BACKGROUND
        // + READ_HEALTH_DATA_HISTORY). Bundled into the record-read
        // request — as they had been since they were declared —
        // Health Connect silently drops them from the dialog and
        // they stay ungranted. They now ride their own launch from
        // a Settings row shown while a record read is granted and
        // either of the two is missing. Background is what lets the
        // overnight look's ~03:00 receiver read at all; history
        // lifts the 30-day read floor so the baseline can backfill.
        // versionCode 95→96.
        // v0.70.3: COROS history seeds the wellness baseline.
        // The bridge already syncs 28 days of RHR and 7 nights
        // of HRV, but the per-signal ledger only grew from its
        // own daily reads, so a fresh connect still said "still
        // building a picture" for 14 more days about data the
        // account already had. Synced history now backfills the
        // ledger on every sync — append-only (an existing day
        // always wins) with Sourcing.pick precedence (a
        // camera-PPG measurement beats the watch's number).
        // versionCode 96→97.
        // v0.70.4: HomeActivity + PreHomeActivity locked to
        // portrait. Neither activity declared screenOrientation,
        // by deliberate prior design (free rotation for
        // mounts/stands/tablets) — but no other launcher on the
        // phone behaves that way, so the home screen rotating
        // whenever the phone is set down or tilted read as the
        // system's own auto-rotate switching itself on. Reported
        // against the real device 2026-08-28; no code anywhere
        // in this app touches the actual rotation setting
        // (grepped, and a 35s idle poll showed zero drift) — the
        // manifest's free-rotation stance was the whole cause.
        // versionCode 97→98.
        // v0.70.5: battery audit. Checked every background/
        // continuous-resource mechanism (clock tick, notification
        // listener, nightly report scheduling, Going Light's VPN
        // packet loop, the accessibility service's event scope,
        // camera+torch teardown) — all already correctly built
        // against real drain (lifecycle-gated, bounded retries,
        // blocking I/O, NonCancellable cleanup). The two real gaps:
        // CorosSyncWorker and BanditResetWorker had no
        // setRequiresBatteryNotLow constraint, so both would still
        // fire on a critically low battery. Both now defer until
        // the level recovers or the phone is charging.
        // versionCode 98→99.
        // v0.70.6: removed the on-device model feature (Settings →
        // Reading → Model: import/download a GGUF, run it for report
        // narration, note classification, and the legacy Phi-4 letter
        // path) — the user does not want it. All three consumers
        // already fell back to their no-model behavior on every real
        // phone (nothing had ever imported one), so nothing observable
        // changes; the capability to ever add one is simply gone, along
        // with the vendored llama.cpp native library, the legacy
        // AlarmManager-based letter scheduler that only that model
        // could feed, and ~20 now-orphaned strings. The modern cloud-LLM
        // daily letter (Settings → Reading → Daily letter (LLM)) is a
        // separate, untouched feature.
        // versionCode 99→100.
        // v0.70.7: Google Drive backup now covers notes, letters,
        // check-ins, and wellness readings (was notes + letters only),
        // runs as a real nightly AlarmManager job (was two Settings
        // toggles wired to nothing and a streaming trigger that was
        // never started), and adds a restore path — the read half the
        // interface always anticipated but never got. Dropped the
        // AES-256-GCM layer: its key was Android Keystore-bound and
        // could never follow the user to a new phone, which silently
        // defeated the whole point of a backup. The sync now diffs
        // against what is already in Drive instead of re-uploading
        // everything every night, so it can't grow the Drive files or
        // slow down over time. Safety plan and crisis contacts stay
        // phone-only, unchanged.
        // versionCode 100→101.
        // v0.70.8: fixed a bug in GoogleDriveBackupTarget's multipart
        // body builder that made every single Drive upload fail with
        // HTTP 400 "Missing end boundary in multipart body" — the raw
        // payload bytes were glued directly onto the closing boundary
        // with no CRLF between them (RFC 2046 §5.1.1 requires one before
        // every boundary delimiter, including the closing one). This
        // silently broke v0.70.7's entire backup feature on the very
        // first real upload; found by driving the live sign-in and
        // backup flow end to end on a real device against a real
        // Google Cloud OAuth client, not by a unit test — the mocked
        // Drive responses in the existing test suite never exercised
        // real RFC 2046 parsing. Added a regression test that checks
        // the actual byte sequence around the closing boundary rather
        // than a loose substring match.
        // versionCode 101→102.
        // v0.70.9: fixed a second live-only bug in the Drive backup —
        // BackupScheduler called BackupTarget.append once per new entry
        // in a loop, and GoogleDriveBackupTarget.append finds-or-creates
        // the Drive file on every call. Drive's file-search index does
        // not reliably see a file the instant it is created, so a
        // second entry's "does this file exist" check could still say
        // no immediately after the first entry's call had just created
        // it, spawning a second file with the same name instead of
        // appending to the first. Confirmed live: backing up 2 notes in
        // one run produced 2 separate MindAnchor-Notes.txt files in
        // Drive. Fixed by collecting every new entry for a type before
        // appending anything, so each backupAll run makes exactly one
        // find-or-create decision per type. Re-verified live after
        // cleaning up the duplicates this bug had already created: one
        // file per type, correct combined content, restore correctly
        // finds nothing new.
        // versionCode 102→103.
        // v0.70.10: fixed a third live-only bug — GoogleDriveAuth.
        // currentAccessToken read the on-disk TokenStore cache first and
        // returned it immediately whenever it was non-blank, only ever
        // calling GoogleAuthUtil.getToken (the real fresh-token fetch)
        // on a cache miss. Once any token was cached it was treated as
        // good forever, but Google access tokens expire in about an
        // hour. Confirmed live: a backup that worked right after sign-in
        // failed about ninety minutes later with HTTP 401 "Invalid
        // Credentials" — and would have failed every night after,
        // forever, since nothing ever cleared the cache to force a
        // refresh. This would have made the nightly sync (which runs
        // hours after the user was last in the app) fail permanently
        // after its first night. Fixed by always asking for a fresh
        // token when an account is signed in — GoogleAuthUtil.getToken
        // already has its own correct cache-and-refresh against Play
        // Services, so this class re-caching on top of it was both
        // redundant and wrong. TokenStore is now purely a fallback for
        // when a fresh fetch cannot be made at all.
        // versionCode 103→104.
        // v0.70.11: UI/alignment audit across the app. Fixed the home
        // screen's "search" button rendering at titleMedium (visibly
        // larger) instead of labelMedium like its "settings" and
        // "Digest" siblings in the same row; the Settings → Reading
        // "Model" label wrapping mid-word into "Mo"/"del" because the
        // model-name button had no width cap; and "1 notifications
        // released" on the home diet card (now a proper plurals
        // resource, correct at any count). Also fixed three places
        // that still flatly claimed nothing backs up to the cloud /
        // backup is off — stale since v0.70.7 added opt-in Google
        // Drive backup: the "Keep a copy" intro, the About paragraph,
        // and the privacy card's "Where the data goes" / "Where the
        // data does not go" sections, all now describe Drive backup
        // as a second opt-in exception and are explicit that the
        // safety plan and crisis contacts never leave the phone.
        // versionCode 104→105.
        // v0.70.12: CI schema fix (every run had been failing
        // instantly all session on a workflow-file schema error);
        // fixed Semgrep/detekt findings that surfaced once CI could
        // actually run; a real Room migration gap (1→5 had no path
        // through 3→4) that the same CI fix exposed. UI audit
        // continuation: collapsible "Why?" rationale text on Quiet/
        // Measuring/Pauses (was a wall of prose in front of every
        // control); Digest/Notes/History lists no longer force-fill
        // the screen when short; home nav baseline alignment;
        // Earlier/Later buttons now read as tappable; removed the
        // home screen's "This week" notification-diet card per
        // request; added a night-time star field to the background
        // (fades in/out on the same schedule the sky's own colour
        // already does); "Apps to batch" extracted from an inlined
        // dump of every installed app into its own searchable
        // screen; guarded the Sleep Lock's startLockTask call
        // against re-triggering while already locked.
        // versionCode 105→106.
        // v0.70.13: two follow-ups from the sleep-window UI audit.
        // (1) The home screen still scrolled after the "This week"
        // card was removed — reproduced live with 6 favourites (the
        // documented max) plus a couple of quick notes, measured
        // ~95dp of real overflow on-device. Fixed by tightening the
        // favourites list's padding (still floors at the 48dp touch
        // target) and collapsing the quick-notes preview from up to
        // 3 rows to just the latest note — verified live, all 6
        // favourites now fit with room to spare and a swipe no
        // longer moves anything. (2) The Sleep Lock's unlock field
        // was missing the bringIntoViewOnFocus() modifier every
        // other input field in the app already uses for the same
        // imePadding + verticalScroll Column — the likely cause of
        // the reported gap between the field and the keyboard.
        // versionCode 106→107.
        // v0.70.14: the v0.70.13 bringIntoViewOnFocus fix on its own
        // wasn't the whole story — a live screenshot from the real
        // device showed the field correctly scrolled clear of the
        // keyboard, but with a large dead gap between the Sleep Lock
        // card and the keyboard, with the bottom nav row floating in
        // the middle of it. Root cause: Modifier.verticalScroll()
        // measures its Column with unbounded height, so
        // Arrangement.CenterVertically was never actually centring —
        // content just packed to the top and the slack landed below
        // it as dead space, on the home screen and (much more
        // visibly) here once the keyboard shrank the usable area.
        // Wrapped the Column in BoxWithConstraints and gave it
        // heightIn(min = maxHeight) so it has a real height to centre
        // within — verified live against the real sleep window: the
        // dead gap is gone and the layout no longer looks top-packed
        // with the keyboard open or closed.
        //
        // Also added a sun to the daytime sky, the same treatment
        // the v0.70.12 night stars got: a fixed, non-animating glow
        // whose opacity is the exact complement of the stars' (same
        // dawn/dusk windows), so the two are never both on screen.
        // versionCode 107→108.
        // v0.70.15: fixed the LLM API key never sticking in
        // Settings → Daily letter (LLM). LlmPrefs.apiKey was
        // `flow { emit(keyStore.read()) }` — a cold flow that reads
        // the encrypted key once per collection and then completes.
        // LlmSettingsViewModel turns it into a StateFlow via
        // stateIn(), which collects it exactly once and then just
        // reads .value forever after; setApiKey() writes the new
        // key straight to the encrypted store but never touched
        // that already-completed flow, so the field looked like it
        // kept losing whatever was typed, and Test Connection kept
        // testing a stale (often blank) key. Existing unit tests
        // never caught it because they all call apiKey.first() —
        // a fresh collection every time — instead of going through
        // stateIn() the way the real screen does.
        //
        // Fixed with a reactive cache that setApiKey() updates in
        // place. It has to be shared across every LlmPrefs instance
        // (not per-instance) because LauncherViewModel and
        // LlmSettingsViewModel each construct their own against the
        // same encrypted file — an instance-level cache would leave
        // the letter writer holding a stale key after the user
        // updates it in Settings, the same bug in a different shape.
        // Added a regression test that collects apiKey into a
        // stateIn() StateFlow before writing, the exact pattern the
        // old code silently failed.
        // versionCode 108→109.
        versionCode = 109
        versionName = "0.70.15"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Fixtures write months of history into the app under test, which
        // would leak into whatever ran next. They are excluded from every
        // Gradle run, CI included, and invoked deliberately instead:
        //   adb shell am instrument -w -e class org.mindanchor.SeedThirtyDays \
        //     org.mindanchor.test/androidx.test.runner.AndroidJUnitRunner
        // am instrument does not read these arguments, so that still works.
        testInstrumentationRunnerArguments["notAnnotation"] = "org.mindanchor.Fixture"

        externalNativeBuild {
            cmake {
                // The off-list is load-bearing, not tidiness. WHISPER_CURL
                // must be OFF because this app's privacy promise is that
                // no path to the network exists anywhere in it, native
                // code included. GGML_NATIVE must be OFF because
                // -march=native on a build machine produces code the
                // phone may not run. The rest keeps the vendored tree to
                // exactly the library — no tools, no tests, no server,
                // no examples, no models. BUILD_SHARED_LIBS=OFF means
                // every whisper/ggml object is linked statically into
                // the one .so.
                //
                // v0.70.5: the LLAMA_* entries this list used to carry
                // are gone along with the llama.cpp target itself —
                // see app/src/main/cpp/CMakeLists.txt.
                arguments += listOf(
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DBUILD_SHARED_LIBS=OFF",
                    "-DWHISPER_CURL=OFF",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                    "-DWHISPER_BUILD_SERVER=OFF",
                )
                cppFlags += "-std=c++17"
            }
        }

        // arm64 is every real phone this app supports (minSdk 33);
        // x86_64 exists so the CI emulator can load the library and
        // prove the JNI surface on device rather than trusting it.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // AGP's default is to demand its own pinned CMake exactly;
            // the trailing + accepts anything newer. 3.22.1 is what a
            // stock Android Studio SDK ships, the CI runners carry 3.31
            // and 4.1 (probed, like the NDK), and the vendored llama
            // tree asks for far less than either — so this floor is the
            // one every machine that builds this project actually clears.
            version = "3.22.1+"
        }
    }

    // Real signing, when and only when a key is supplied.
    //
    // Debug-signed builds are the reason Play Protect blocks every install
    // and the user has to dig through "restricted settings" to get the app
    // onto a phone — a miserable first contact for something meant to feel
    // calm.
    //
    // The key lives in CI secrets and never in this repository. When the
    // secrets are absent, as they are for every fork and every local
    // build, signingConfig stays null and Gradle falls back to the debug
    // key exactly as before. Nothing breaks for anyone who does not have
    // the key; the release simply is not the official one.
    val keystoreFile = System.getenv("MINDANCHOR_KEYSTORE")
    val hasKeystore = !keystoreFile.isNullOrBlank() && file(keystoreFile).exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystoreFile!!)
                storePassword = System.getenv("MINDANCHOR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MINDANCHOR_KEY_ALIAS")
                keyPassword = System.getenv("MINDANCHOR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Unit-test mocking. The IntegritySealedCodec tests
    // call `android.util.Base64.encodeToString` and
    // `android.util.Base64.decode` directly (the codec
    // uses the Android Base64 helper, not java.util's,
    // because the encoded form must round-trip the
    // way the device's KeyStore and the on-device
    // observer see it). Returning default values is
    // not enough — the test would silently produce
    // empty-string MACs and never trigger the
    // failure paths. The right answer is to either
    // (a) refactor the codec to take a small `Base64`
    // interface, or (b) add the mock-android jar to
    // the test classpath. The `returnDefaultValues =
    // true` option is the v0.20.1 pre-merge
    // placeholder; the cleaner fix is a follow-up
    // commit. (PR #18 did the placeholder fix; the
    // follow-up is tracked separately.)
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Preserve Kotlin parameter names in the bytecode so
        // reflection-based finding tests (e.g. v0.25.2-A's
        // LetterSurfaceWiringFindingTest) can pin a Composable's
        // parameter shape without guessing at arg2 / arg3.
        javaParameters = true
    }

    buildFeatures {
        compose = true
    }

    // Reproducible, F-Droid-friendly builds: no proprietary dependencies anywhere.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

/*
 * ListenableFuture, on the compile classpath only.
 *
 * This took three wrong turns, so the whole account is here.
 *
 * CameraX returns Guava's ListenableFuture from getInstance() and
 * enableTorch(), and nothing on the compile classpath exports the
 * interface, so those signatures fail to resolve.
 *
 * Guava publishes two artifacts under com.google.guava:listenablefuture.
 * 1.0 holds the interface. "9999.0-empty-to-avoid-conflict-with-guava" is
 * an empty jar, published so projects that already carry full Guava do not
 * end up with the class twice. Something in the AndroidX graph asks for
 * the empty one, and 9999.0 sorts above 1.0, so newest-wins picks it —
 * on every configuration, compileOnly included. That is why simply
 * declaring the dependency changed nothing.
 *
 * Forcing 1.0 everywhere fixed compilation and immediately produced
 * "Duplicate class ListenableFuture found in guava-31.1-android and
 * listenablefuture-1.0", because full Guava really is in this graph and
 * the empty jar was the only thing preventing the collision.
 *
 * So the force is scoped to compile classpaths alone. Compiling sees the
 * real interface; packaging and runtime keep the empty placeholder and
 * load the single copy inside full Guava. Both halves, neither breaking
 * the other.
 */
configurations.configureEach {
    if (name.endsWith("CompileClasspath")) {
        resolutionStrategy.force("com.google.guava:listenablefuture:1.0")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    // COROS Training Hub bridge (opt-in side-channel). EncryptedSharedPreferences
    // stores the user's email + password so the 24h web token can be refreshed
    // without re-prompting. OkHttp is the HTTP client for the Training Hub REST
    // endpoints; the work-runtime is for the periodic-sync background worker.
    // Carve-out: only files under app/src/main/java/org/mindanchor/vitals/coros/
    // may reference these (see NetworkCallsForbiddenTest).
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    // Google Sign-In: the OAuth entry point for v0.25.4's
    // Google Drive backup. The `drive.file` scope is
    // requested at sign-in; the resulting `GoogleSignInAccount`
    // is exchanged for an access token via `GoogleAuthUtil`
    // on every Drive API call. The raw Drive REST is then
    // hit via the existing `okhttp` dep — no
    // `play-services-drive` AAR (see libs.versions.toml note).
    implementation(libs.play.services.auth)
    // Device-agnostic wearable ingestion. Integrating with Health Connect
    // rather than with any one watch's app is what makes changing watches
    // a non-event: whatever writes there is readable, and nothing in this
    // app knows or cares which brand produced it.
    implementation(libs.androidx.health.connect)
    // Camera PPG. HRV is the best physiological signal available here and
    // COROS does not release it — it never leaves their own app. Fingertip
    // PPG is the one route that needs no wearable at all, so it survives
    // changing watches, losing one, or wearing none.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    // Supplies ListenableFuture.await(), which turns CameraX's Guava
    // futures into ordinary suspend calls.
    implementation(libs.androidx.concurrent.futures.ktx)
    // compileOnly so nothing extra is packaged; the compile-classpath
    // force above is what makes it the real artifact rather than the
    // empty placeholder.
    compileOnly(libs.guava.listenablefuture)
    // WorkManager is included solely for the COROS Training Hub
    // side-channel's periodic 6h sync. The launcher itself is
    // still a no-outbound-calls app: the no-network promise on
    // the About screen is enforced by NetworkCallsForbiddenTest
    // (see corosBridgeFiles carve-out for the opt-in path).
    // The nightly report's "charging and idle" constraints are
    // still checked by hand in ReportSchedule, not by WorkManager.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    // OkHttp's MockWebServer: a localhost-bound HTTP server that
    // records requests and returns scripted responses. v0.20.7's
    // CorosApiTest exercises the four Training Hub endpoints
    // through it without touching the live server. Pinned to the
    // same version as the main okhttp client (4.12.0).
    testImplementation(libs.okhttp.mockwebserver)
    // Mockito for the CorosAuthTest's stub of the
    // CorosCredentialStore's parent constructor argument.
    testImplementation(libs.mockito.core)
    // Robolectric for the ReaderPrefsRoundTripFindingTest — a JVM
    // test that needs a real Android Context to exercise the
    // DataStore round-trip (set, then re-read). Without Robolectric
    // the test would need to be an androidTest, which means a
    // connected device or emulator per run. Robolectric runs the
    // test in a sandboxed JVM with a real Context, no emulator.
    // 4.13 is the highest 4.x that compiles cleanly against the
    // project's compileSdk 36 + AGP 8.9.1; later 4.x lines pull
    // in SDK 35 native binaries that conflict with the project's
    // SDK 33 minSdk toolchain.
    testImplementation(libs.robolectric)
    // androidx.test:core-ktx for ApplicationProvider, used by the
    // ReaderPrefs round-trip test to get a real Android Context
    // inside the Robolectric sandbox. Same version as the catalog
    // entry; the Robolectric test would fail to compile without it.
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// TestGuild #82 (Kover / coverage slot) — the missing
// test-management pillar. The kover Gradle plugin is
// applied here. The defaults run against the existing
// src/test/java tree and produce:
//   app/build/reports/kover/htmlDebug/index.html
//   app/build/reports/kover/reportDebug.xml
// which CI dashboards ingest. To also cover the main
// source set (production code paths), extend `kover { sources { ... } }`
// in Kover ≥ 0.8; the Kover 0.9 DSL shape is documented at
// https://kotlin.github.io/kotlinx-kover/gradle-plugin/.
dependencies {
    kover(project(":app"))
}
