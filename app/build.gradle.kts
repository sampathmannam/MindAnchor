plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.mindanchor"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.mindanchor"
        minSdk = 33
        targetSdk = 35
        versionCode = 16
        versionName = "0.16.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    // No WorkManager here, deliberately. It expressed the nightly
    // report's "charging and idle" constraints in two lines, and it also
    // merged ACCESS_NETWORK_STATE into the manifest, which PrivacyTest
    // caught. A no-network app is the basis of the promise on the About
    // screen, so the constraints are checked by hand instead — see
    // ReportSchedule for the full reasoning.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
