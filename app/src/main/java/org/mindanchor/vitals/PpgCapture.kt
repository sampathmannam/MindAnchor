package org.mindanchor.vitals

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import androidx.concurrent.futures.await
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where one camera-PPG attempt currently stands.
 *
 * [Measuring] carries both elapsed and remaining seconds because the screen
 * needs the countdown, but a future progress indicator might want the
 * elapsed side instead — cheaper to carry both than to make the caller
 * subtract from a duration it would otherwise have to remember separately.
 */
sealed interface PpgCaptureState {
    data object Idle : PpgCaptureState
    data class Measuring(val elapsedSeconds: Int, val secondsRemaining: Int) : PpgCaptureState
    data class Done(val hrv: HrvResult, val heartRateBpm: Int?) : PpgCaptureState
    /** Plain-language reason, shown verbatim nowhere — the screen picks its own copy. */
    data class Failed(val reason: String) : PpgCaptureState
}

/**
 * Turns the rear camera and its torch into one number per frame, and hands
 * the resulting series to [Hrv.fromPpg].
 *
 * This class owns exactly the Android parts [Ppg] and [Hrv] refuse to know
 * about: binding a use case, keeping the flash lit, and reading raw camera
 * bytes without ever turning them into anything a person could look at.
 * Nothing here decides whether a reading is trustworthy — that judgement
 * happens entirely inside the pure pipeline, and this class's only job is
 * to feed it an honest `sampleRate` and an unbroken series of samples, or
 * to say plainly that it could not.
 *
 * ## Frame rate is measured, not assumed
 *
 * Exposure time on a lit, close-up subject varies with ambient light and
 * with how the auto-exposure algorithm is behaving that second, so the
 * analyser's true rate is rarely a clean 30 fps and drifts over the course
 * of a measurement. [Ppg] needs one `sampleRate` for the whole series — its
 * moving-average filters are built on a constant sample spacing — so this
 * class times every frame with [android.media.Image]'s own sensor clock and
 * reports the mean interval across the whole recording as that rate. A
 * single frame's fps means nothing on its own; the average over ninety
 * seconds is stable to well under a percent even though individual frame
 * gaps vary several-fold.
 *
 * ## Irregular frames
 *
 * Ordinary jitter — a frame arriving 20 ms early or late because exposure
 * metering nudged — is exactly what the mean-interval rate already
 * absorbs, and [Ppg]'s wide moving-average windows do not notice a few
 * milliseconds of timing noise. What they cannot absorb is a genuine stall:
 * the analyser executor blocked, the app briefly backgrounded, the camera
 * pipeline hiccuping. A stall does not fail loudly — it just leaves one gap
 * far longer than its neighbours, and treating that gap as an ordinary
 * sample interval would silently mis-time every beat computed across it.
 * [finish] checks the single worst gap against the mean and refuses the
 * whole recording if it is disproportionate, on the same principle the rest
 * of this codebase follows: an honest refusal beats a plausible wrong
 * number.
 *
 * ## Never throws
 *
 * A camera can fail for reasons that have nothing to do with the person's
 * pulse — another app holding it, a transient binder error, a device
 * without a flash. None of that should crash a wellbeing screen, so every
 * path through [start] ends in [PpgCaptureState.Done] or
 * [PpgCaptureState.Failed], the same discipline [org.mindanchor.grayscale.Grayscale]
 * follows for a system service that is equally out of this app's control.
 */
class PpgCapture(
    private val context: Context,
    /**
     * v0.25.5 WP-D: the local-only telemetry store for sessions.
     * Defaulted so existing callers (and existing tests) keep their
     * public surface, and so the test surface can pass a test-only
     * store with a controlled clock.
     */
    private val sessionStore: PpgSessionStore = PpgSessionStore(context),
) {

    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private val sampleLock = Any()
    private val lumaSamples = ArrayList<Double>()
    private val frameTimestampsNanos = ArrayList<Long>()

    @Volatile
    private var stopRequested = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analysisUseCase: ImageAnalysis? = null

    private val _state = MutableStateFlow<PpgCaptureState>(PpgCaptureState.Idle)

    /** Progress the screen renders: idle, counting down, or a final outcome. */
    val state: StateFlow<PpgCaptureState> = _state.asStateFlow()

    /**
     * Runs one full measurement: binds the camera, lights the torch, waits
     * out [durationSeconds] while [state] carries the countdown, then reads
     * the result through [Hrv.fromPpg]. Suspends for the whole duration —
     * callers launch it from a cancellable coroutine (a screen leaving
     * composition is exactly the cancellation this is built to survive
     * cleanly).
     *
     * Camera teardown runs from a `finally` wrapped in [NonCancellable]:
     * without that, a screen navigating away mid-measurement would cancel
     * this coroutine before the torch-off and `unbindAll` calls inside the
     * `finally` block ever got a dispatcher to run on, leaving the flash lit
     * and the camera bound to a lifecycle that is going away.
     */
    suspend fun start(
        lifecycleOwner: LifecycleOwner,
        durationSeconds: Int = MEASUREMENT_SECONDS,
    ): PpgCaptureState {
        stopRequested = false
        synchronized(sampleLock) {
            lumaSamples.clear()
            frameTimestampsNanos.clear()
        }
        // v0.25.5 WP-D: the session start is the moment the user asks
        // for a measurement, not the moment the camera actually opens.
        // The "duration" the user sees on the history line is the
        // time they actually spent waiting, including the warm-up
        // before the first frame.
        val sessionStart = Instant.now()
        _state.value = PpgCaptureState.Measuring(0, durationSeconds)

        var result: PpgCaptureState = PpgCaptureState.Idle
        try {
            val provider = awaitCameraProvider()
            cameraProvider = provider

            val boundCamera = withContext(Dispatchers.Main) {
                val useCase = buildAnalysisUseCase()
                analysisUseCase = useCase
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, useCase)
            }
            camera = boundCamera

            if (!boundCamera.cameraInfo.hasFlashUnit()) {
                // Without a torch the finger is never transilluminated and
                // the recording would be ninety seconds of near-uniform
                // black — not worth running out.
                result = PpgCaptureState.Failed(REASON_NO_FLASH)
            } else {
                withContext(Dispatchers.Main) {
                    // enableTorch returns a ListenableFuture<Void>,
                    // deliberately not awaited. Torch-on failing silently
                    // still lets the recording run and get refused by the
                    // quality gate, which is safer than the whole
                    // measurement throwing over a flash driver hiccup.
                    runCatching { boundCamera.cameraControl.enableTorch(true) }
                }
                result = runMeasurementLoop(durationSeconds)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            result = PpgCaptureState.Failed(error.message ?: REASON_CAMERA_FAILED)
        } finally {
            withContext(NonCancellable) {
                teardown()
                // v0.25.5 WP-D: log the session regardless of outcome.
                // A failed session is still a session — the history
                // line "you tried this 4 times yesterday" is the
                // useful answer to a question the user is allowed to
                // ask. The store is fail-soft; an exception here
                // never reaches the user.
                recordSession(sessionStart, result)
            }
        }

        _state.value = result
        return result
    }

    /**
     * v0.25.5 WP-D: append the session to the local log. The mean HR
     * is the result's [PpgCaptureState.Done.heartRateBpm] when the
     * run succeeded; null on every other state, so the history line
     * shows "Tue 8:14am · 45s" without a bpm when the gate refused.
     */
    private suspend fun recordSession(start: Instant, result: PpgCaptureState) {
        val meanHr: Double? = when (result) {
            is PpgCaptureState.Done -> result.heartRateBpm?.toDouble()
            else -> null
        }
        sessionStore.record(PpgSession(start = start, end = Instant.now(), meanHr = meanHr))
    }

    /** Ends the measurement early; the loop notices within [TICK_MILLIS]. */
    fun stop() {
        stopRequested = true
    }

    /** Releases the analyser thread. Call once the screen no longer needs this instance. */
    fun release() {
        runCatching { analyzerExecutor.shutdown() }
    }

    // --- Measurement loop --------------------------------------------------

    private suspend fun runMeasurementLoop(durationSeconds: Int): PpgCaptureState {
        val startMillis = System.currentTimeMillis()
        while (true) {
            delay(TICK_MILLIS)
            if (stopRequested) break
            val elapsedSeconds = ((System.currentTimeMillis() - startMillis) / 1000L).toInt()
            val remaining = (durationSeconds - elapsedSeconds).coerceAtLeast(0)
            _state.value = PpgCaptureState.Measuring(
                elapsedSeconds = elapsedSeconds.coerceAtMost(durationSeconds),
                secondsRemaining = remaining,
            )
            if (elapsedSeconds >= durationSeconds) break
        }
        return if (stopRequested) PpgCaptureState.Failed(REASON_CANCELLED) else finish()
    }

    /**
     * Turns the collected frames into a heart-rate-variability result, or a
     * refusal — see the class doc for why the gap check exists.
     */
    private suspend fun finish(): PpgCaptureState {
        val (values, timestamps) = synchronized(sampleLock) {
            lumaSamples.toList() to frameTimestampsNanos.toList()
        }
        if (values.size < MIN_FRAMES || timestamps.size != values.size) {
            return PpgCaptureState.Failed(REASON_TOO_FEW_FRAMES)
        }

        val intervalsSeconds = ArrayList<Double>(timestamps.size - 1)
        for (i in 1 until timestamps.size) {
            val delta = (timestamps[i] - timestamps[i - 1]) / NANOS_PER_SECOND
            if (delta > 0.0) intervalsSeconds.add(delta)
        }
        if (intervalsSeconds.isEmpty()) return PpgCaptureState.Failed(REASON_BAD_TIMING)

        val meanIntervalSeconds = intervalsSeconds.sum() / intervalsSeconds.size
        val sampleRate = 1.0 / meanIntervalSeconds

        val worstGapSeconds = intervalsSeconds.max()
        if (worstGapSeconds > max(GAP_FLOOR_SECONDS, meanIntervalSeconds * GAP_FACTOR)) {
            return PpgCaptureState.Failed(REASON_INTERRUPTED)
        }

        val hrv = withContext(Dispatchers.Default) { Hrv.fromPpg(values, sampleRate) }
        return PpgCaptureState.Done(hrv, hrv.metrics?.meanHeartRate?.roundToInt())
    }

    // --- Camera plumbing -----------------------------------------------------

    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        // await() comes from androidx.concurrent:concurrent-futures-ktx,
        // which is also what puts Guava's ListenableFuture on the compile
        // classpath at all — CameraX returns it from getInstance() and
        // enableTorch() without exporting it, so those signatures do not
        // resolve on their own. A hand-rolled addListener bridge failed to
        // compile here for exactly that reason.
        ProcessCameraProvider.getInstance(context).await()

    private fun buildAnalysisUseCase(): ImageAnalysis {
        val useCase = ImageAnalysis.Builder()
            // The alternative, STRATEGY_BLOCK_PRODUCER, queues frames the
            // analyser hasn't gotten to yet; on a thread doing pixel
            // sampling that queue only grows, and a growing backlog is
            // exactly the "stalled analyser" failure mode the task
            // description warns about. Keeping only the latest frame means
            // a slow tick is merely a dropped frame, not a wedged pipeline.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        // NOTE(ci): ImageAnalysis.Analyzer is a Kotlin-SAM-convertible
        // functional interface with a single `analyze(ImageProxy)` method
        // in 1.4.1; verify the lambda still resolves against that shape.
        useCase.setAnalyzer(analyzerExecutor) { image -> onFrame(image) }
        return useCase
    }

    private fun onFrame(image: ImageProxy) {
        try {
            val luma = meanLuma(image)
            synchronized(sampleLock) {
                lumaSamples.add(luma)
                // NOTE(ci): ImageInfo.timestamp is documented as the
                // sensor/monotonic capture time in nanoseconds, not
                // wall-clock time; only differences between frames are
                // meaningful, which is all this ever computes.
                frameTimestampsNanos.add(image.imageInfo.timestamp)
            }
        } finally {
            // A proxy left unclosed stops the analyser from ever being
            // handed another frame — CameraX will not deliver frame N+1
            // until frame N is closed. This finally block is the only
            // thing standing between one bad frame and a measurement that
            // silently stops at a couple of seconds of data.
            image.close()
        }
    }

    private suspend fun teardown() {
        withContext(Dispatchers.Main) {
            runCatching { camera?.cameraControl?.enableTorch(false) }
            runCatching { analysisUseCase?.clearAnalyzer() }
            runCatching { cameraProvider?.unbindAll() }
        }
        camera = null
        analysisUseCase = null
        cameraProvider = null
    }

    companion object {
        const val MEASUREMENT_SECONDS = 90
        private const val TICK_MILLIS = 250L
        private const val MIN_FRAMES = 30
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        /**
         * A gap has to clear both a floor and a multiple of the mean
         * spacing before it counts as a stall. The floor keeps a single
         * slow-but-honest frame at a low true fps from tripping the
         * relative test; the multiple keeps a fast, jittery recording from
         * needing an enormous absolute gap before anything is noticed.
         */
        private const val GAP_FLOOR_SECONDS = 0.5
        private const val GAP_FACTOR = 5.0

        /**
         * Every 8th row and column of the Y plane. A torch-lit fingertip
         * fills the frame with one slowly pulsing patch of brightness, not
         * fine detail, so 1/64th of the pixels is already far more spatial
         * resolution than the signal has any use for — and keeping the
         * per-frame cost low is what keeps the analyser from falling behind
         * and quietly dropping frames under STRATEGY_KEEP_ONLY_LATEST.
         */
        private const val PIXEL_STEP = 8

        const val REASON_NO_FLASH = "no camera flash available"
        const val REASON_CAMERA_FAILED = "camera failed"
        const val REASON_CANCELLED = "measurement cancelled"
        const val REASON_TOO_FEW_FRAMES = "too few frames captured"
        const val REASON_BAD_TIMING = "frame timing unusable"
        const val REASON_INTERRUPTED = "capture was interrupted"

        /**
         * Mean brightness of the Y (luma) plane of a YUV_420_888 frame,
         * subsampled every [PIXEL_STEP] rows and columns.
         *
         * Reads straight out of the plane's [java.nio.ByteBuffer] with
         * absolute `get(index)` calls rather than draining it, so nothing
         * about the frame's own state is disturbed before [ImageProxy]
         * closes it. Only ever produces the single mean value returned
         * here — the buffer itself is never copied, retained, or exposed
         * past the end of this call.
         */
        internal fun meanLuma(image: ImageProxy): Double {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height
            val capacity = buffer.capacity()

            var sum = 0L
            var count = 0
            var row = 0
            while (row < height) {
                val rowStart = row * rowStride
                var col = 0
                while (col < width) {
                    val index = rowStart + col * pixelStride
                    if (index in 0 until capacity) {
                        sum += (buffer.get(index).toInt() and 0xFF)
                        count++
                    }
                    col += PIXEL_STEP
                }
                row += PIXEL_STEP
            }
            return if (count > 0) sum.toDouble() / count else 0.0
        }
    }
}
