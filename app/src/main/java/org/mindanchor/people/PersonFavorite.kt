package org.mindanchor.people

import java.net.URLEncoder
import org.mindanchor.support.PhoneMatch

/**
 * How a person prefers to be reached.
 *
 * Two to begin with, because both are answered by every Android phone and
 * neither needs a permission. Third-party channels (WhatsApp, Signal) are a
 * separate decision: they need per-app deep links and a story for when the
 * app is not installed, and getting that wrong turns "Talk to Mum" into a
 * dead tile.
 */
enum class TalkChannel { CALL, TEXT }

/**
 * A person on the home surface (master plan T-2.1).
 *
 * The launcher's favorites are apps; the evidence this rests on is about
 * people (Holt-Lunstad 2010 on social connection and mortality; Verduyn
 * 2017 on active versus passive use). So a favorite here is a person and
 * the way they are reached, and opening one lands in the conversation
 * rather than in the app that happens to host it.
 *
 * Free of Android types: which intent to fire, and what counts as
 * reachable, are decisions worth testing without a device.
 */
data class PersonFavorite(
    val name: String,
    val phone: String,
    val channel: TalkChannel,
) {
    /** Reads as the action, not as an app name. */
    fun label(): String = "Talk to $name"

    companion object {
        /**
         * A favorite, or null when this could not reach anyone.
         *
         * The number keeps whatever shape the person typed -- normalisation
         * decides reachability, it is not a storage format, and the dialer
         * should show them what they entered. The bar for "reachable" is
         * [PhoneMatch.normalize], the same one the crisis card already
         * trusts to recognise a person.
         */
        fun of(name: String, phone: String, channel: TalkChannel): PersonFavorite? {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) return null
            if (PhoneMatch.normalize(phone) == null) return null
            return PersonFavorite(cleanName, phone.trim(), channel)
        }
    }
}

/** What tapping a person should launch, free of Android types. */
data class TalkIntent(val action: String, val uri: String)

object TalkTo {

    private const val ACTION_DIAL = "android.intent.action.DIAL"
    private const val ACTION_SENDTO = "android.intent.action.SENDTO"

    /**
     * The intent for a person, and deliberately never `ACTION_CALL`.
     *
     * Dialling the moment a tile is touched is the wrong default for a
     * launcher built around pauses: the person still gets the last say on
     * the dialer screen. It is also why none of this needs CALL_PHONE.
     * `SupportScreen` already makes the same choice for crisis contacts.
     */
    fun intentFor(favorite: PersonFavorite): TalkIntent = when (favorite.channel) {
        TalkChannel.CALL -> TalkIntent(ACTION_DIAL, "tel:${encode(favorite.phone)}")
        TalkChannel.TEXT -> TalkIntent(ACTION_SENDTO, "smsto:${encode(favorite.phone)}")
    }

    /**
     * Percent-encode the number for a URI.
     *
     * [URLEncoder] is form encoding, which turns a space into `+` -- and `+`
     * is the international dialling prefix, so that substitution would
     * quietly rewrite the number. Spaces become `%20` here instead.
     */
    private fun encode(phone: String): String =
        URLEncoder.encode(phone, Charsets.UTF_8.name()).replace("+", "%20")
}
