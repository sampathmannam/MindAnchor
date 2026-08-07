package org.mindanchor.admin

/**
 * Decides which apps may be suspended, and — more importantly — which may
 * never be.
 *
 * Device Owner gives this app the power to make other apps genuinely
 * unopenable. In a launcher aimed at people having a bad night, that power
 * is one mistake away from real harm: a suspended dialer at three in the
 * morning is somebody unable to call anyone. So the decision about what
 * can be switched off is pure, sits apart from any Android glue, and is
 * tested against exactly the cases that would hurt.
 *
 * The rule is simple and refuses to be clever: an app is suspendable only
 * if the person chose it *and* it is not a way of reaching another human
 * or of getting back to this screen.
 */
object SuspensionGuard {

    /**
     * Filters [chosen] down to what is safe to suspend.
     *
     * [dialer], [sms] and [self] are passed in rather than looked up so
     * that this stays testable and so the caller is forced to acknowledge
     * each of them. A null dialer or sms — which happens on tablets — is
     * not treated as "nothing to protect"; it simply protects less, and
     * the other rules still hold.
     *
     * [alsoNeverSuspend] carries anything else the caller wants kept
     * reachable, such as the packages of the person's own crisis contacts'
     * messaging apps.
     */
    fun suspendable(
        chosen: Set<String>,
        dialer: String?,
        sms: String?,
        self: String,
        alsoNeverSuspend: Set<String> = emptySet(),
    ): List<String> {
        val protectedPackages = buildSet {
            dialer?.let { add(it) }
            sms?.let { add(it) }
            add(self)
            addAll(alsoNeverSuspend)
            addAll(ALWAYS_REACHABLE)
        }
        return chosen
            .filter { it.isNotBlank() }
            .filterNot { it in protectedPackages }
            .sorted()
    }

    /**
     * Packages that stay reachable whatever anyone chooses.
     *
     * Settings is here so that a phone can always be un-broken from within
     * itself: if this app ever suspends something it should not have, the
     * way out must not itself be suspended. Emergency and telephony
     * packages are here for the obvious reason.
     */
    val ALWAYS_REACHABLE = setOf(
        "com.android.settings",
        "com.android.emergency",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.dialer",
        "com.android.mms",
        "com.android.messaging",
    )
}
