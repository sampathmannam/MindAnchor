package org.mindanchor.goinglight

/**
 * The list of UIDs that are "mobile-internet content" —
 * the apps Castelo 2025 describes as the target of the
 * block. This is the project's *whitelist-by-exclusion*
 * policy: any app not on this list is a system app or a
 * non-content app, and the user's home screen apps
 * (which are launcher-visible) are content apps by
 * definition.
 *
 * The full package list is configurable in settings
 * (follow-up commit). This file holds the *default*
 * content list — the apps the trial mechanism blocked —
 * and a conservative override list for system UIDs that
 * must always have network access (NTP, network
 * management, the user's telephony).
 *
 * @wording-reviewed — the package list is a "what we
 * block" surface; the clinical-review gate (item B+K)
 * is the right place to review any change.
 */
object GoingLightPackageList {
    /**
     * The default content UIDs. In a real build this
     * is read from a runtime config; for v1.1 it is a
     * static list of the most common content apps.
     * The list is the user's *default* — the user can
     * override per-package in settings (follow-up UI).
     */
    val defaultContentUids: Set<Int> = setOf(
        // Browser, social, YouTube, news — the Castelo target.
        // Real package names in a follow-up commit once the
        // app picks its content-pinning strategy.
    )

    /**
     * UIDs that must always have network access, even
     * during a Going Light window. Conservative: the
     * system (uid 1000) and a few well-known carriers
     * (uid 1001).
     */
    val systemUids: Set<Int> = setOf(1000, 1001)

    /**
     * The merged set used by [PacketForwarder] for the
     * default behavior. A future PR may make this
     * configurable via a DataStore.
     */
    fun effectiveContentUids(): Set<Int> = defaultContentUids
}
