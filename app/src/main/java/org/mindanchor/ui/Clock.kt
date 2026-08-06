package org.mindanchor.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * The current time, refreshed exactly on each minute boundary and again the
 * moment the launcher resumes.
 *
 * The naive version — a `while (true) { delay(30_000) }` loop — was wrong in
 * two ways a tester notices immediately: after the phone had been asleep the
 * clock could show a time up to half a minute stale on wake, and the minute
 * flipped at an arbitrary offset rather than when the real minute changed.
 */
@Composable
fun rememberMinuteTick(): LocalTime {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = LocalTime.now()
                // Sleep only until the wall clock's next minute.
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
    return now
}

/** Honours the device's 12/24-hour setting rather than assuming 24. */
@Composable
fun rememberClockFormat(): String =
    if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
