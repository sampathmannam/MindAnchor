plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// Program 3 advisory delivery is compiled out unless a build deliberately
// asks for it. Only the exact lower-case literal `true` counts, so a typo
// or an empty value leaves the ordinary, zero-protocol build. Operational
// evidence cannot authorize an ordinary build on its own, and a release
// build forces both fields false regardless of what was passed.
val program3PersonalResearch =
    providers.gradleProperty("mindanchor.program3.personalResearch").orNull == "true"
val program3OperationalEvidence =
    providers.gradleProperty("mindanchor.program3.operationalEvidenceApproved").orNull == "true"
require(!program3OperationalEvidence || program3PersonalResearch) {
    "Program 3 operational evidence cannot authorize an ordinary build"
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
        // v0.71.0: Task 13 release-hardening bump (Program 0's
        //   complete, reviewed feature slice — Tasks 1-12).
        //   versionCode 94→95.
        // v0.72.0: Program 1 scientific foundation — evidence protocol
        //   registry, append-only hash-chained research ledger, study
        //   phases carrying the provenance version vector, frozen data
        //   dictionary, and a self-describing research export. Room v6→v7
        //   (additive; two new append-only tables). Snapshot format 1→2
        //   and research export v1→v2, both with the older version kept
        //   readable and verifiable. Zero new permissions, no network.
        //   versionCode 95→96.
        versionCode = 96
        versionName = "0.72.0"
        // MindAnchorTestRunner puts WorkManager into test mode for the
        // whole instrumented suite — see that class's KDoc for why:
        // without it, a test that writes through a real repository
        // incidentally enqueues a real CheckpointBackupWorker that can
        // execute on a real background thread and corrupt
        // ContinuitySettingsTest's on-disk state mid-run.
        testInstrumentationRunner = "org.mindanchor.MindAnchorTestRunner"
        // Fixtures write months of history into the app under test, which
        // would leak into whatever ran next. They are excluded from every
        // Gradle run, CI included, and invoked deliberately instead:
        //   adb shell am instrument -w -e class org.mindanchor.SeedThirtyDays \
        //     org.mindanchor.test/androidx.test.runner.AndroidJUnitRunner
        // am instrument does not read these arguments, so that still works.
        testInstrumentationRunnerArguments["notAnnotation"] = "org.mindanchor.Fixture"

        externalNativeBuild {
            cmake {
                // The off-list is load-bearing, not tidiness. LLAMA_CURL
                // and WHISPER_CURL must be OFF because this app's
                // privacy promise is that no path to the network
                // exists anywhere in it, native code included.
                // GGML_NATIVE must be OFF because -march=native on
                // a build machine produces code the phone may not
                // run. The rest keeps the vendored trees to
                // exactly the libraries — no tools, no tests, no
                // server, no examples, no models. The same
                // BUILD_SHARED_LIBS=OFF applies to both
                // add_subdirectory()s; every llama/ggml and
                // whisper/ggml object is linked statically into
                // its respective .so.
                arguments += listOf(
                    "-DLLAMA_CURL=OFF",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
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
        debug {
            buildConfigField("boolean", "PROGRAM3_PERSONAL_RESEARCH", program3PersonalResearch.toString())
            buildConfigField(
                "boolean",
                "PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED",
                program3OperationalEvidence.toString(),
            )
        }
        release {
            buildConfigField("boolean", "PROGRAM3_PERSONAL_RESEARCH", "false")
            buildConfigField("boolean", "PROGRAM3_OPERATIONAL_EVIDENCE_APPROVED", "false")
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
        buildConfig = true
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
    testImplementation(libs.kotlinx.coroutines.test)
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
    // work-testing's WorkManagerTestInitHelper needs a real Android
    // Context (Robolectric, same as the rest of this test classpath) —
    // Task 10's ContinuityWorkSchedulerTest runs as a plain
    // testDebugUnitTest JVM test, not a connectedDebugAndroidTest, so
    // this goes on testImplementation, not androidTestImplementation.
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// TestGuild #82 (Kover / coverage slot) — the missing
// test-management pillar. The kover Gradle plugin is
// applied here. The defaults run against the existing
// src/test/java tree and produce:
//   app/build/reports/kover/htmlDebug/index.html
//   app/build/reports/kover/reportDebug.xml
// which CI dashboards ingest. The plugin is applied directly to this
// single application module, so no cross-project Kover dependency is needed.

// Commits AnchorDatabase's Room schema exports (app/schemas) so migrations
// are validated against the exact prior schema rather than trusted blind.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
