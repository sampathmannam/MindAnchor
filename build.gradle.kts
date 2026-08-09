// Top-level build file. The :app module is the launcher.
//
// detekt is the static-analysis gate added in docs/research/17.
// Applied at root so the detekt command exists for
// `./gradlew detekt`; the per-module configuration (source set,
// type resolution) lives in app/build.gradle.kts.
//
// v0.20.1 (CodeRabbit audit 2026-08-08): the vendored-source
// exclusion list ('**/build/**', '**/generated/**',
// '**/llama/**') is now applied to BOTH the standard `Detekt`
// task and the `DetektCreateBaselineTask`. The v0.20.0
// configuration only applied the exclusion to `Detekt`, so
// regenerating the baseline would scan the vendored engine
// and add thousands of findings to baseline.xml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}

// The vendored-source exclusion list. Centralized so the
// standard Detekt task and the DetektCreateBaselineTask
// use the same list. The list is a property because
// detekt's `exclude()` calls take varargs and the same
// arguments apply to both task types.
val detektExcludes = arrayOf(
    "**/build/**",
    "**/generated/**",
    "**/llama/**",  // vendored inference engine, not our code
)

// Resolve the detekt version ONCE at the root, before the
// subprojects block runs. The `subprojects { ... }` block
// can read the version catalog directly, but the catalog
// is only registered for the root project; resolving the
// value here and capturing it in a `val` makes the value
// available inside `subprojects` without re-accessing the
// (root-only) extension.
val detektToolVersion: String = libs.versions.detekt.get()

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class.java) {
        toolVersion = detektToolVersion
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline = file("$rootDir/config/detekt/baseline.xml")
    }

    // v0.20.1: apply the same exclusion list to the
    // standard Detekt task AND the baseline task. The
    // v0.20.0 config only applied to Detekt, so
    // regenerating the baseline would scan the
    // vendored engine and add thousands of findings.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
        }
        exclude(*detektExcludes)
    }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        exclude(*detektExcludes)
    }
}
