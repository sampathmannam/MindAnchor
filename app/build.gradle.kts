plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
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
    buildToolsVersion = "36.0.0"

    // Pinned to the exact version .github/workflows/probe-ndk.yml proved
    // present on the CI runners — the engine build depends on it, and an
    // unpinned NDK is a build that works until the runner image changes.
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "org.mindanchor"
        minSdk = 33
        targetSdk = 35
        versionCode = 63
        versionName = "0.35.1"
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
                // The off-list is load-bearing, not tidiness. LLAMA_CURL
                // must be OFF because this app's privacy promise is that
                // no path to the network exists anywhere in it, native
                // code included. GGML_NATIVE must be OFF because
                // -march=native on a build machine produces code the
                // phone may not run. The rest keeps the vendored tree to
                // exactly the library — no tools, no tests, no server.
                arguments += listOf(
                    "-DLLAMA_CURL=OFF",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DBUILD_SHARED_LIBS=OFF",
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
