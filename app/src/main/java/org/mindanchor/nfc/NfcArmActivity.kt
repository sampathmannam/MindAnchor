package org.mindanchor.nfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.goinglight.GoingLightScheduler
import org.mindanchor.sunset.SunsetController

/**
 * The v0.27+ (Phase 2 G-4) NFC physical anchor.
 *
 * A cheap NFC tag (NTAG215 / NTAG216, ~$1 per tag) on
 * the nightstand, the fridge, or the desk. Tapping
 * the tag with the phone arms Going Light / Sunset
 * / Sleep Lock, depending on the tag's NDEF
 * payload. The bedside tag is also the only way to
 * *disarm* an active Sleep Lock window.
 *
 * ## Why an Activity rather than a service
 *
 * Android's [NfcAdapter] requires a foreground
 * [PendingIntent] for the tag-discovered dispatch.
 * The PendingIntent is the [MainActivity] launch
 * intent, and the Activity is the foreground at the
 * moment of the tap. The Activity reads the NDEF
 * payload and dispatches the action; no service is
 * needed because the tap is a user-driven event
 * with a UI affordance.
 *
 * ## Why the foreground-dispatch lifecycle is
 *   repeated in onResume / onPause
 *
 * The Android `enableForegroundDispatch` and
 * `disableForegroundDispatch` pair is required
 * because the system can preempt the foreground
 * Activity (a phone call, the lock screen). The
 * Activity must enable on resume and disable on
 * pause or the next tap will silently fail. The
 * pattern is documented in the official NFC
 * guide; it is not a v0.27+ invention.
 *
 * ## Why a calm consent card
 *
 * The first-time UX shows a plain-English consent
 * card explaining "if you tap the tag, Going Light
 * arms." A second-tap disarms. The card is
 * validate-then-suggest, never a force.
 */
class NfcArmActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Read the NDEF payload of the tapped tag and
     * dispatch the action. The payload format is
     * `mindanchor:arm:going-light` /
     * `mindanchor:arm:sunset` /
     * `mindanchor:arm:sleep-lock`. An unrecognised
     * tag is treated as a no-op (the user can rename
     * a regular NFC tag without consequences).
     */
    private fun handleIntent(intent: Intent?) {
        val tag: Tag? = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag == null) {
            renderIdle()
            return
        }
        val payload = readNdefPayload(tag) ?: return renderIdle()
        val action = parsePayload(payload) ?: return renderIdle()
        dispatch(action)
    }

    private fun renderIdle() {
        // The consent card is the default. The actual
        // Compose surface is the follow-up commit
        // (G-4 wiring + Activity onCreate Composable).
        // The contract is the readNdefPayload +
        // parsePayload + dispatch trio.
    }

    private fun readNdefPayload(tag: Tag): String? {
        val ndef = android.nfc.tech.Ndef.get(tag) ?: return null
        val message = runCatching { ndef.cachedNdefMessage }.getOrNull() ?: return null
        return message.records.firstOrNull()?.payload?.let { payload ->
            // Skip the language code byte (3 bits) + UTF-8 bit (1 bit)
            // + length byte that prefixes a well-formed NDEF Text record.
            if (payload.isNotEmpty()) {
                val status = payload[0].toInt()
                val length = status and 0x3F
                if (payload.size > length + 1) {
                    String(payload, length + 1, payload.size - length - 1, Charsets.UTF_8)
                } else null
            } else null
        }
    }

    private fun parsePayload(payload: String): ArmAction? = when {
        payload.startsWith("mindanchor:arm:going-light") -> ArmAction.GOING_LIGHT
        payload.startsWith("mindanchor:arm:sunset") -> ArmAction.SUNSET
        payload.startsWith("mindanchor:arm:sleep-lock") -> ArmAction.SLEEP_LOCK
        else -> null
    }

    private fun dispatch(action: ArmAction) {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        when (action) {
            ArmAction.GOING_LIGHT -> lifecycleScope.launch {
                val prefs = FrictionPrefs(this@NfcArmActivity)
                val schedule = prefs.goingLightSchedule.first()
                GoingLightScheduler.enable(this@NfcArmActivity, schedule)
            }
            ArmAction.SUNSET -> lifecycleScope.launch {
                SunsetController.onToggled(this@NfcArmActivity, true)
            }
            ArmAction.SLEEP_LOCK -> {
                // Sleep Lock is a v0.27+ Phase 2 G-5
                // feature; the NFC arm is the entry
                // point. The G-5 implementation is the
                // follow-up.
            }
        }
    }

    private enum class ArmAction { GOING_LIGHT, SUNSET, SLEEP_LOCK }
}
