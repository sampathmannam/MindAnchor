package org.mindanchor

/**
 * Marks an instrumented class that exists to *put data on the device*
 * rather than to assert anything.
 *
 * A fixture writes into the same app instance every other instrumented
 * test shares, so leaving one in the ordinary run would make the suite
 * order-dependent — the failure mode where a test passes alone, passes in
 * CI for a month, and then fails the week somebody adds a class whose name
 * sorts earlier.
 *
 * `app/build.gradle.kts` excludes this annotation from every Gradle run.
 * Fixtures are invoked by hand through `am instrument`, which does not read
 * those arguments.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Fixture
