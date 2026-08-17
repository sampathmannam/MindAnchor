/*
 * v0.36.0 — Health Connect permission string constants.
 *
 * The SDK's own `HealthPermission.getReadPermission(KClass)` factory
 * is the source of truth at runtime, but pulling the strings out
 * into a file of constants means [HealthConnectSource] can be
 * tested without depending on the SDK's `HealthPermission` object
 * (which lives in the `androidx.health.connect.client.permission`
 * package and is `@RestrictTo(LIBRARY)`).
 */
package org.mindanchor.vitals

object HealthConnectPermissionStrings {
    const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
    const val READ_RESTING_HEART_RATE = "android.permission.health.READ_RESTING_HEART_RATE"
    const val READ_HEART_RATE_VARIABILITY = "android.permission.health.READ_HEART_RATE_VARIABILITY"
    const val READ_SLEEP = "android.permission.health.READ_SLEEP"
    const val READ_STEPS = "android.permission.health.READ_STEPS"
    const val READ_EXERCISE = "android.permission.health.READ_EXERCISE"
    const val READ_TOTAL_CALORIES_BURNED = "android.permission.health.READ_TOTAL_CALORIES_BURNED"
    const val READ_MINDFULNESS = "android.permission.health.READ_MINDFULNESS"
}
