package org.mindanchor.prehome

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.doomscrollDataStore by preferencesDataStore(name = "doomscroll_list")

/**
 * v0.26+ (spec Phase 1) — the doomscroll package
 * list. The list of Android package names that the
 * launcher's `PreHomeActivity` will offer a
 * moment-of-pause on before the user opens them.
 *
 * The default set is the v0.26+ spec's recommended
 * six (Instagram, YouTube, Twitter, Reddit,
 * TikTok, Snapchat, Facebook). The user can edit
 * the list in Settings (`add / remove / always
 * open`) — that surface is a follow-up. The store is
 * plain string-set; no auto-detection, no usage
 * stats, no "is this app I spend time on" heuristic.
 * The launcher is opinionated; the user is the
 * second opinion.
 *
 * ## Why a separate DataStore
 *
 * The friction feature already has a `flagged`
 * package set on [org.mindanchor.data.FrictionPrefs];
 * the PreHome list is a different opinion (the
 * spec calls them the same set, but the surfaces
 * are different: friction gates the app, PreHome
 * prompts before the user opens it). Sharing the
 * store would couple the two and lock the
 * surfaces together. A separate store is the right
 * shape.
 */
class DoomscrollList(private val context: Context) {

    private val packagesKey = stringSetPreferencesKey("doomscroll_packages")

    /**
     * The current set of doomscroll packages. Read by
     * the `PreHomeActivity` to decide which app-launch
     * intents to intercept.
     */
    val packages: Flow<Set<String>> = context.doomscrollDataStore.data.map { prefs ->
        prefs[packagesKey] ?: DEFAULT_DOOMSCROLL
    }

    /**
     * The default set. The names below are package
     * names (the Android `applicationId`), not display
     * labels. A typo in the manifest would mean the
     * package never matches; the user is the source
     * of truth in the Settings edit surface.
     */
    val defaultPackages: Set<String> = DEFAULT_DOOMSCROLL.toSet()

    suspend fun add(packageName: String) {
        context.doomscrollDataStore.edit { prefs ->
            prefs[packagesKey] = (prefs[packagesKey] ?: DEFAULT_DOOMSCROLL) + packageName
        }
    }

    suspend fun remove(packageName: String) {
        context.doomscrollDataStore.edit { prefs ->
            prefs[packagesKey] = (prefs[packagesKey] ?: DEFAULT_DOOMSCROLL) - packageName
        }
    }

    suspend fun setAll(packages: Set<String>) {
        context.doomscrollDataStore.edit { prefs ->
            prefs[packagesKey] = packages
        }
    }

    /**
     * Removes the stored entry, so the next read of
     * [packages] falls back to [defaultPackages].
     * Used by tests to reset between cases; not
     * surfaced in the user-facing Settings surface.
     */
    suspend fun clear() {
        context.doomscrollDataStore.edit { prefs ->
            prefs.remove(packagesKey)
        }
    }

    companion object {
        /**
         * The v0.26+ spec's recommended doomscroll set.
         * The list is deliberately small; the user's
         * edit surface (a follow-up) is the surface
         * for "I want to add / remove".
         */
        val DEFAULT_DOOMSCROLL: Set<String> = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.zhiliaoapp.musically", // TikTok
            "com.snapchat.android",
            "com.facebook.katana",
        )
    }
}
