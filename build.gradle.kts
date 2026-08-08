// Top-level build file. The :app module is the launcher.
//
// detekt is the static-analysis gate added in docs/research/17.
// Applied at root so the detekt command exists for
// `./gradlew detekt`; the per-module configuration (source set,
// type resolution) lives in app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class.java) {
        toolVersion = libs.versions.detekt.get()
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline = file("$rootDir/config/detekt/baseline.xml")
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
        }
        exclude("**/build/**", "**/generated/**", "**/llama/**")
    }
}
