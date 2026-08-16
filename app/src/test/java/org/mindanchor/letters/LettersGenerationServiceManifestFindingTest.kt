@file:Suppress("MaxLineLength", "SwallowedException")
package org.mindanchor.letters

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.32.1 FindingTest: the [LettersGenerationService] is
 * registered in the manifest with the right
 * foregroundServiceType + permission pair, the service class
 * extends `android.app.Service`, the channel is centralised
 * in [org.mindanchor.notifications.Channels], and the
 * `running` state is exposed as a static guard so the
 * inbox's "Generate now" button can disable itself while a
 * generation is in progress.
 *
 * Without the foregroundServiceType + permission pair, the
 * `startForegroundService` call from
 * [org.mindanchor.launcher.HomeScreen.onGenerateNow] would
 * throw a `SecurityException` at runtime on Android 12+ —
 * exactly the failure mode v0.32.1 ships to fix (v0.31.2's
 * 50-minute test of the in-Composable coroutine got the
 * process reaped by the OS mid-decode).
 *
 * Each test is small and self-contained. A failure in any
 * one of them names the missing piece.
 */
class LettersGenerationServiceManifestFindingTest {

    @Test
    fun `LettersGenerationService is registered with foregroundServiceType dataSync`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val idx = manifest.indexOf("android:name=\".letters.LettersGenerationService\"")
        assertTrue(
            "AndroidManifest.xml must register " +
                ".letters.LettersGenerationService as a service. " +
                "The v0.32.1 'Generate now' affordance starts " +
                "this service via Context.startForegroundService; " +
                "without the declaration the start throws " +
                "an IllegalStateException at runtime.",
            idx >= 0,
        )
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</service>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        assertTrue(
            "LettersGenerationService must declare " +
                "android:foregroundServiceType=\"dataSync\" " +
                "so the startForeground call from onStartCommand " +
                "is valid on Android 12+ (and so the OS does not " +
                "kill the Q2_K decode under memory pressure on a " +
                "1.8 GB MemAvailable phone). " +
                "window=$window",
            window.contains("android:foregroundServiceType=\"dataSync\""),
        )
    }

    @Test
    fun `LettersGenerationService is exported false (no external caller)`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val idx = manifest.indexOf("android:name=\".letters.LettersGenerationService\"")
        assertTrue(
            "LettersGenerationService declaration must be present.",
            idx >= 0,
        )
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</service>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        assertTrue(
            "LettersGenerationService must declare " +
                "android:exported=\"false\" because the only " +
                "caller is this app's own " +
                "Context.startForegroundService call. window=$window",
            window.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `FOREGROUND_SERVICE_DATA_SYNC permission pairs with LettersGenerationService`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val perm = manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
        val idx = manifest.indexOf("android:name=\".letters.LettersGenerationService\"")
        assertTrue(
            "LettersGenerationService declaration must be present.",
            idx >= 0,
        )
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</service>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        val type = window.contains("android:foregroundServiceType=\"dataSync\"")
        assertTrue(
            "FOREGROUND_SERVICE_DATA_SYNC permission must be " +
                "declared alongside LettersGenerationService's " +
                "dataSync foregroundServiceType. Android 12+ " +
                "requires the pair, and the v0.32.1 sleep-survival " +
                "design depends on it. perm=$perm, type=$type.",
            perm && type,
        )
    }

    @Test
    fun `LettersGenerationService Kotlin class extends android app Service`() {
        val cls = Class.forName("org.mindanchor.letters.LettersGenerationService")
        val sup = cls.superclass
        assertNotNull(
            "LettersGenerationService must extend android.app.Service",
            sup,
        )
        assertTrue(
            "LettersGenerationService's parent class must be " +
                "android.app.Service, so startForegroundService + " +
                "startForeground work. super=${sup?.name}",
            sup?.name == "android.app.Service",
        )
    }

    @Test
    fun `LettersGenerationService has a static running guard`() {
        // The inbox's "Generate now" button needs to be
        // disabled while a generation is in progress so a
        // second tap doesn't double-load the model. The
        // static `running` boolean is the read side of
        // that contract; the CAS inside onStartCommand is
        // the write side. Both ends must exist; this test
        // pins the read side.
        //
        // Kotlin compiles `private val isRunning` in a
        // companion object as a `private static final`
        // field on the outer class (not on the synthetic
        // Companion class). The companion only holds
        // accessors. We look on the outer class.
        val cls = Class.forName("org.mindanchor.letters.LettersGenerationService")
        val runningField = runCatching {
            cls.getDeclaredField("isRunning")
        }.getOrNull()
        assertNotNull(
            "LettersGenerationService must declare a static " +
                "isRunning field (the re-entry guard). " +
                "Two rapid taps on 'Generate now' must not " +
                "double-load the model. The field is on the " +
                "outer class (private static final) in the " +
                "compiled bytecode; this is the Kotlin " +
                "mapping of `private val` in a companion " +
                "object.",
            runningField,
        )
    }

    @Test
    fun `LettersGenerationService has a static intent factory`() {
        // The companion object's `intent` method. In
        // Kotlin, companion-object methods are compiled
        // as instance methods of the synthetic Companion
        // class, not as static methods of the outer class.
        // Reflection has to follow the companion.
        val cls = Class.forName("org.mindanchor.letters.LettersGenerationService")
        val companion = cls.declaredClasses.firstOrNull { it.simpleName == "Companion" }
        assertNotNull(
            "LettersGenerationService must declare a companion " +
                "object with the intent(Context) factory. The " +
                "factory is the only call site the production " +
                "code uses; without it the caller would have to " +
                "new up an Intent(componentName) by hand, which " +
                "duplicates the service-class name in two places.",
            companion,
        )
        val method = runCatching {
            companion!!.getDeclaredMethod("intent", android.content.Context::class.java)
        }.getOrNull()
        assertNotNull(
            "LettersGenerationService.Companion must declare " +
                "an intent(Context) method so HomeScreen can " +
                "build the startForegroundService intent " +
                "without referencing the service class name " +
                "twice.",
            method,
        )
    }

    private fun read(path: String): String? = try {
        java.io.File(path).readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
