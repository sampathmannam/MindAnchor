package org.mindanchor.support

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Resolves the country the device is in, in ISO 3166-1 alpha-2 lowercase.
 *
 * The honest priority: the SIM's home network, the network the phone is
 * currently attached to, and the user's chosen locale — in that order.
 * `Locale.getDefault().getCountry()` is the least reliable signal (a
 * phone in London with a US-purchased account on a roaming SIM still
 * reads "US" by locale) but it is the *only* signal available without
 * the `READ_PRIVILEGED_PHONE_STATE` permission, and the no-internet
 * promise of this app means the user is reading "Get help" in a country
 * they are physically in, not necessarily the country their SIM is from.
 *
 * The single rule that matters here: when the sources disagree, prefer
 * the network. A phone in a roaming context reading the wrong country
 * is a bigger harm than a phone reading the user's home country while
 * they travel. So the network's country is taken when it disagrees with
 * the locale.
 *
 * No `INTERNET` permission is required. The whole point of the bundled
 * list is that it works offline.
 */
object DeviceCountry {

    /**
     * ISO 3166-1 alpha-2 in lowercase, or null when nothing useful can
     * be resolved. A null is *not* an error condition; the caller is
     * expected to fall back to a "for any country" sheet.
     */
    fun resolve(context: Context): String? {
        val network = networkCountry(context)
        val locale = Locale.getDefault().country?.takeIf { it.isNotBlank() }?.lowercase()
        // Prefer network when it disagrees with locale: a roaming phone
        // should be read by the country it's in, not the country its
        // SIM claims to be from.
        return network?.lowercase() ?: locale
    }

    private fun networkCountry(context: Context): String? {
        return runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return@runCatching null
            tm.networkCountryIso?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
