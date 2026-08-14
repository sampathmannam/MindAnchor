@file:Suppress("MaxLineLength", "SwallowedException")
package org.mindanchor.watch

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.26.1 §3.3 FindingTest: the AppWatchService + SmsInterceptor
 * are registered in the manifest with the right
 * foregroundServiceType + permission pair and the SMS_RECEIVED
 * intent filter.
 *
 * The Android 14 service-type rules require a foreground service
 * declared with `foregroundServiceType="dataSync"` to also
 * declare `android.permission.FOREGROUND_SERVICE_DATA_SYNC` —
 * a missing pair is a runtime start-failure on Android 12+, not
 * a build-time error. The SmsInterceptor must be `exported="true"`
 * because the SMS_RECEIVED broadcast is a system broadcast that
 * only exported receivers can hear.
 */
class AppWatchServiceManifestFindingTest {

    @Test
    fun `AppWatchService is registered with foregroundServiceType dataSync`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val idx = manifest.indexOf("android:name=\".watch.AppWatchService\"")
        assertTrue(
            "AndroidManifest.xml must register .watch.AppWatchService " +
                "as a service. The §3.3 SMS tone-check foreground " +
                "service is the v0.26.1 entry point for the " +
                "After-23 prompt.",
            idx >= 0,
        )
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</service>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        assertTrue(
            "AppWatchService must declare " +
                "android:foregroundServiceType=\"dataSync\" " +
                "so the startForeground call from onStartCommand " +
                "is valid on Android 12+. " +
                "window=$window",
            window.contains("android:foregroundServiceType=\"dataSync\""),
        )
    }

    @Test
    fun `FOREGROUND_SERVICE_DATA_SYNC permission pairs with dataSync service type`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val perm = manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
        val idx = manifest.indexOf("android:name=\".watch.AppWatchService\"")
        assertTrue("AppWatchService declaration must be present", idx >= 0)
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</service>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        val type = window.contains("android:foregroundServiceType=\"dataSync\"")
        assertTrue(
            "FOREGROUND_SERVICE_DATA_SYNC permission must be declared " +
                "alongside AppWatchService's dataSync " +
                "foregroundServiceType. Android 12+ requires the " +
                "pair, and the AppWatchServiceManifestFindingTest " +
                "pins both. perm=$perm, type=$type.",
            perm && type,
        )
    }

    @Test
    fun `SmsInterceptor is registered with the SMS_RECEIVED intent filter`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val idx = manifest.indexOf("android:name=\".watch.SmsInterceptor\"")
        assertTrue(
            "AndroidManifest.xml must register .watch.SmsInterceptor " +
                "as a receiver. The §3.3 side-channel is the " +
                "SMS_RECEIVED entry point.",
            idx >= 0,
        )
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</receiver>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        assertTrue(
            "SmsInterceptor must declare an intent filter for " +
                "android.provider.Telephony.SMS_RECEIVED so the " +
                "system delivers the broadcast. window=$window",
            window.contains("android.provider.Telephony.SMS_RECEIVED"),
        )
    }

    @Test
    fun `SmsInterceptor is exported true (system broadcasts require exported receivers)`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val idx = manifest.indexOf("android:name=\".watch.SmsInterceptor\"")
        assertTrue("SmsInterceptor declaration must be present", idx >= 0)
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</receiver>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        assertTrue(
            "SmsInterceptor must declare android:exported=\"true\" " +
                "so the system can deliver SMS_RECEIVED. The " +
                "receiver reads only the broadcast extras and " +
                "writes to its own DataStore; the broadcast is " +
                "system-originated so the receiver has to be " +
                "exported. window=$window",
            Regex("android:exported=\"true\"").containsMatchIn(window),
        )
    }

    @Test
    fun `RECEIVE_SMS permission is declared alongside the SMS receiver`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        assertTrue(
            "RECEIVE_SMS permission must be declared so the " +
                "SmsInterceptor can receive SMS_RECEIVED at all.",
            manifest.contains("android.permission.RECEIVE_SMS"),
        )
    }

    @Test
    fun `AppWatchService Kotlin class extends android app Service`() {
        val cls = Class.forName("org.mindanchor.watch.AppWatchService")
        val svc = cls.superclass
        assertNotNull("AppWatchService must extend android.app.Service", svc)
        assertTrue(
            "AppWatchService's parent class must be android.app.Service, " +
                "so startForegroundService + startForeground work. " +
                "super=${svc?.name}",
            svc?.name == "android.app.Service",
        )
    }

    @Test
    fun `SmsInterceptor Kotlin class extends BroadcastReceiver`() {
        val cls = Class.forName("org.mindanchor.watch.SmsInterceptor")
        val sup = cls.superclass
        assertNotNull("SmsInterceptor must extend android.content.BroadcastReceiver", sup)
        assertTrue(
            "SmsInterceptor's parent class must be BroadcastReceiver " +
                "so the system can deliver the SMS_RECEIVED " +
                "broadcast. super=${sup?.name}",
            sup?.name == "android.content.BroadcastReceiver",
        )
    }

    private fun read(path: String): String? = try {
        java.io.File(path).readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
