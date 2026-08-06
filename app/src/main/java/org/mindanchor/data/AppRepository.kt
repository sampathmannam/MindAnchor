package org.mindanchor.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A launchable app as the launcher sees it. */
data class InstalledApp(
    val component: String,
    val defaultLabel: String,
)

/**
 * Installed-app catalog backed by [LauncherApps], kept fresh via its
 * package-change callback. Main profile only for now.
 */
class AppRepository(context: Context) {

    private val launcherApps =
        context.getSystemService(LauncherApps::class.java) as LauncherApps

    /** Emits the current app list immediately, then again on every package change. */
    val apps: Flow<List<InstalledApp>> = callbackFlow {
        fun load(): List<InstalledApp> =
            launcherApps.getActivityList(null, Process.myUserHandle())
                .map { it.toInstalledApp() }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                trySend(load())
            }

            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                trySend(load())
            }

            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                trySend(load())
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean,
            ) {
                trySend(load())
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean,
            ) {
                trySend(load())
            }
        }

        launcherApps.registerCallback(callback)
        trySend(load())
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    fun launch(component: String) {
        val componentName = android.content.ComponentName.unflattenFromString(component)
            ?: return
        launcherApps.startMainActivity(componentName, Process.myUserHandle(), null, null)
    }

    private fun LauncherActivityInfo.toInstalledApp() = InstalledApp(
        component = componentName.flattenToString(),
        defaultLabel = label.toString(),
    )
}
