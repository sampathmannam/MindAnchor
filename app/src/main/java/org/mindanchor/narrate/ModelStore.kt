package org.mindanchor.narrate

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores an imported GGUF model file in app-private storage, and answers
 * whether this phone can actually run it.
 *
 * ## Why app-private storage, and why one file
 *
 * Same reasoning as [org.mindanchor.corpus.CorpusStore]'s imported corpus:
 * app-private storage means nothing else on the phone can read a model
 * somebody chose to add, and there is deliberately no history of models —
 * importing a new one replaces the old one outright, because running two
 * generations of the same nightly paragraph from two different models
 * is not a feature anybody asked for.
 *
 * ## Why import is all-or-nothing
 *
 * A model file is large — gigabytes, copied over a content resolver
 * stream that can fail at any byte for reasons that have nothing to do
 * with the model itself: a revoked permission, a full disk, a picker app
 * that goes away mid-copy. A half-copied file that [hasModel] still
 * reports as present is worse than no file at all, because everything
 * downstream — [fit], and eventually a real inference engine — takes
 * "present" as license to try loading it, and a truncated GGUF fails in
 * whatever way the loader was not built to expect rather than in the one
 * place this file already knows how to say no cleanly. So [importFrom]
 * treats any failure, at any point in the copy, as a reason to delete
 * whatever bytes made it to disk and report nothing was imported at all.
 */
object ModelStore {

    private const val MODEL_FILE_NAME = "model.gguf"

    /** Where an imported model lives: app-private, so nothing else can read it. */
    fun modelFile(context: Context): File = File(context.filesDir, MODEL_FILE_NAME)

    /**
     * Copies a picked file's bytes to app-private storage, replacing
     * whatever model was there before.
     *
     * Returns false on any failure at all — a stream the picker would not
     * open, an IO error partway through the copy, anything — and leaves
     * no partial file behind; see the class KDoc for why a half-copied
     * model is worse than none.
     */
    fun importFrom(context: Context, uri: Uri): Boolean {
        val destination = modelFile(context)
        // Copied beside the real file and moved into place only once the
        // last byte has landed. Writing straight to the destination would
        // truncate it on the first byte, so an import that failed halfway
        // — and gigabytes over a content resolver is exactly where that
        // happens — would leave somebody with neither the model they
        // picked nor the one they already had. A mis-tap or a flaky
        // stream must not be able to destroy a working model.
        val staging = File(destination.parentFile, "$MODEL_FILE_NAME.part")
        val copied = runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching false
            input.use { stream ->
                staging.outputStream().use { output -> stream.copyTo(output) }
            }
            true
        }.getOrDefault(false)

        if (!copied) {
            // Whatever made it to disk before the failure is not a model,
            // it is the start of one, and it must not be left behind
            // looking like the real thing.
            runCatching { staging.delete() }
            return false
        }

        val moved = runCatching {
            // renameTo will not overwrite on every filesystem, so the old
            // model goes first. By this point the replacement is complete
            // on disk, so the window in which neither exists is the length
            // of one rename rather than the length of a copy.
            if (destination.exists()) destination.delete()
            staging.renameTo(destination)
        }.getOrDefault(false)

        if (!moved) runCatching { staging.delete() }
        return moved
    }

    /** Whether a complete-looking model is on file. A zero-byte file counts as absent. */
    fun hasModel(context: Context): Boolean =
        runCatching { modelFile(context).length() > 0L }.getOrDefault(false)

    /** The model's size on disk, or 0 when none is on file. */
    fun modelBytes(context: Context): Long =
        runCatching { modelFile(context).length() }.getOrDefault(0L)

    /** Removes the stored model, if any. Idempotent: absent is success too. */
    fun clear(context: Context): Boolean = runCatching {
        val file = modelFile(context)
        if (file.exists()) file.delete()
        true
    }.getOrDefault(false)

    /**
     * Whether the model on file would actually run on this phone.
     *
     * Delegates the arithmetic entirely to [ModelSlot.fit] — this
     * function's only job is reading the two numbers that decision needs
     * from Android. On any failure to read them at all, [ModelSlot.Fit.TOO_LARGE]
     * is returned: refusing a model this could not check is the same safe
     * default [ModelSlot] itself falls back to for a model or a phone it
     * could not measure.
     */
    fun fit(context: Context): ModelSlot.Fit = runCatching {
        // NOTE(ci): ActivityManager.MemoryInfo, ActivityManager#getMemoryInfo,
        // MemoryInfo#totalMem, and ActivityManager#isLowRamDevice are the
        // standard shape for reading total device memory on Android, but
        // unverified against this build's exact SDK/AGP versions — check
        // here first if this file fails to compile.
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        ModelSlot.fit(
            modelBytes = modelBytes(context),
            totalMemBytes = memInfo.totalMem,
            isLowRamDevice = activityManager.isLowRamDevice,
        )
    }.getOrDefault(ModelSlot.Fit.TOO_LARGE)

    /**
     * The cached "model would actually run" state, exposed as a
     * [StateFlow] so a UI can observe it without re-running the
     * [ActivityManager] probe on every recomposition.
     *
     * Initialised to `false` because there is no [Context] available
     * at object-construction time to ask [fit] for a real answer. The
     * first call to [refreshFit] — typically from
     * [org.mindanchor.settings.SettingsViewModel.refreshModel] on
     * settings-screen entry — replaces the default with the real
     * value. A UI that shows up before that first refresh sees
     * `modelFits = false`, which is the same refuse-by-default
     * posture the absence of a model warrants anyway.
     *
     * The same refuse-by-default value is what [ModelStore.fit] falls
     * back to when it cannot read the device, so a transient
     * [refreshFit] failure leaves the UI in the same state as
     * "no model" — never "yes, run a 12 GB model on a 4 GB phone".
     */
    private val _fitState = MutableStateFlow(false)

    /** The observable [StateFlow] form of [ModelSlot.runnable] applied to [fit]. */
    fun fitFlow(): StateFlow<Boolean> = _fitState.asStateFlow()

    /**
     * Recomputes the [fit] answer for [context] and publishes the
     * runnable form to [fitFlow]. Callers (the settings view model)
     * invoke this after importing or clearing a model so any observer
     * of [fitFlow] sees the new value without having to re-probe the
     * device itself.
     */
    fun refreshFit(context: Context) {
        _fitState.value = ModelSlot.runnable(fit(context))
    }
}
