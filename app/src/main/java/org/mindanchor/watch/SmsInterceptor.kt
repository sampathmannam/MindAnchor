@file:Suppress("MaxLineLength")
package org.mindanchor.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * v0.26.1 §3.3: the SMS_RECEIVED entry point.
 *
 * Fires when any SMS arrives. The receiver extracts the sender
 * + body from the `pdus` extra, writes a record to
 * [SmsToneCheckPrefs] (the audit log), then starts
 * [AppWatchService] as a foreground service so the tone-check
 * notification is posted even when the app is in the background.
 *
 * The receiver is registered with `android:priority="999"` so
 * it runs before any other SMS handler. `exported="true"` is
 * required by the Android 12+ explicit-broadcast rules: the
 * SMS_RECEIVED broadcast is system-broadcast and the receiver
 * must be exported to receive it. The receiver does *no
 * aborting* — that would require default-SMS-app status, which
 * is out of scope for v0.26.1.
 *
 * The whole body is truncated to [SmsToneCheckLedger]'s
 * excerpt cap (280 chars) before being written to the store
 * or sent to the service. The body of someone else's message
 * is not the launcher's to keep, and the excerpt is the
 * minimum needed to surface the prompt.
 *
 * On Android 13+ the receiver is registered with
 * `RECEIVER_EXPORTED` (or the equivalent in the manifest),
 * which the AppWatchServiceManifestFindingTest pins.
 */
class SmsInterceptor : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val (sender, body) = extract(context, intent) ?: return
        val excerpt = SmsToneCheckLedger.excerpt(body)
        val record = SmsToneCheck(
            atMillis = System.currentTimeMillis(),
            sender = sender,
            bodyExcerpt = excerpt,
        )
        // The DataStore write is suspend; the receiver lifetime is
        // short (it returns when onReceive returns), so the
        // write happens in a fire-and-forget coroutine on a
        // process-level scope. The store's HMAC tag is not
        // here (this is the SMS audit log, not the user
        // content) so a write race against the foreground
        // service's notification post is not a correctness
        // hazard.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { SmsToneCheckPrefs(context).append(record) }
        }
        // Start the foreground service so the tone-check
        // notification posts. The service stops itself after
        // the notification is up; the receiver's job is done
        // once the service is started.
        val serviceIntent = Intent(context, AppWatchService::class.java).apply {
            putExtra(AppWatchService.EXTRA_SENDER, sender)
            putExtra(AppWatchService.EXTRA_BODY, excerpt)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    /**
     * Decode the `pdus` extra into (sender, body).
     *
     * Returns null when the broadcast is malformed — an
     * empty pdus array, an unparsable PDU, or an SMS without
     * a body. A null return is a no-op: the receiver
     * silently drops the broadcast rather than throwing
     * inside the system's broadcast handler.
     */
    private fun extract(@Suppress("UNUSED_PARAMETER") context: Context, intent: Intent): Pair<String, String>? {
        // `getSerializableExtra` is deprecated in API 33+ in
        // favour of the typed overload; the typed overload
        // takes a Class<T>, the sms pdus are an Array<ByteArray>
        // which the platform delivers as Serializable. The
        // typed overload is what silences the deprecation
        // warning without changing the runtime behaviour.
        val pdus: Array<ByteArray>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("pdus", Array<ByteArray>::class.java)
        } else {
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            intent.getSerializableExtra("pdus") as? Array<ByteArray>
        }
        if (pdus == null || pdus.isEmpty()) return null
        val format = intent.getStringExtra("format")
        val messages = ArrayList<SmsMessage>(pdus.size)
        for (bytes in pdus) {
            val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SmsMessage.createFromPdu(bytes, format)
            } else {
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(bytes)
            } ?: continue
            messages += message
        }
        if (messages.isEmpty()) return null
        val sender = messages.first().displayOriginatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (sender.isBlank() && body.isBlank()) return null
        return sender to body
    }
}
