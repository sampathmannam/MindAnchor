package org.mindanchor.narrate

/**
 * Whether a language model of a given size can actually be run on this
 * phone, decided before anything is loaded rather than discovered when
 * the system kills the process.
 *
 * ## Why this is a decision and not an attempt
 *
 * The generation runs in a background worker, overnight, with nobody
 * watching. A background process that asks for more memory than the
 * system will spare does not fail politely — it is killed, and from the
 * outside that is indistinguishable from a quiet night with nothing to
 * report. Deciding up front means the honest answer ("this model is
 * larger than this phone can run in the background") can be shown to
 * somebody instead of being silently swallowed every night.
 *
 * ## The arithmetic, and its assumptions
 *
 * A GGUF model is memory-mapped, so in principle it need not all be
 * resident. In practice a model paging off storage on every token is
 * slow enough not to finish, so the file is budgeted as though it must
 * be resident. On top of it, the KV cache for a short generation at a
 * small context is on the order of a few hundred megabytes; [KV_CACHE_ALLOWANCE]
 * is the allowance for that plus the runtime's own overhead.
 *
 * [USABLE_FRACTION] is the share of total RAM a *background* process can
 * expect to hold without being reclaimed. It is deliberately well under
 * half: the launcher, the system UI, and whatever the person left open
 * are all resident too, and the whole point of running overnight is not
 * to disturb any of them.
 *
 * Worked through, on the two sizes that matter: an 8 GB phone yields a
 * budget of about 3.6 GB, which a 2.4 GB four-bit model plus its cache
 * clears at [Fit.TIGHT]; a 12 GB phone yields about 5.4 GB, which the
 * same model clears comfortably. Both numbers are conservative on
 * purpose. Being told a model will not run is recoverable — a smaller
 * one can be imported. Being killed nightly is not, because nothing
 * says it happened.
 */
object ModelSlot {

    /** Share of total RAM a background process can expect to keep. */
    const val USABLE_FRACTION = 0.45

    /** KV cache at a small context, plus runtime overhead. */
    const val KV_CACHE_ALLOWANCE_BYTES = 512L * 1024 * 1024

    /**
     * Above this share of the budget, a model runs but is at risk on a
     * bad night — another app left resident, a system update unpacking.
     * It is still offered, with the risk stated, because on an 8 GB phone
     * refusing everything in this band would mean refusing every model
     * worth having.
     */
    const val TIGHT_FRACTION = 0.75

    enum class Fit {
        /** Runs with room to spare. */
        FITS,

        /** Runs, but may be reclaimed on a busy night. */
        TIGHT,

        /** Will not reliably survive a background run on this phone. */
        TOO_LARGE,

        /**
         * The phone is flagged by Android as low-memory. No model is
         * offered at any size: on these devices a background process
         * holding a gigabyte is reclaimed as a matter of course.
         */
        UNSUPPORTED,
    }

    /**
     * What a model of [modelBytes] would do on a phone with [totalMemBytes].
     *
     * [isLowRamDevice] is Android's own flag, and it is taken at its word
     * rather than second-guessed from the byte count.
     */
    fun fit(modelBytes: Long, totalMemBytes: Long, isLowRamDevice: Boolean): Fit {
        if (isLowRamDevice) return Fit.UNSUPPORTED
        if (modelBytes <= 0L || totalMemBytes <= 0L) return Fit.TOO_LARGE
        val budget = (totalMemBytes * USABLE_FRACTION).toLong()
        val needed = modelBytes + KV_CACHE_ALLOWANCE_BYTES
        return when {
            needed > budget -> Fit.TOO_LARGE
            needed > budget * TIGHT_FRACTION -> Fit.TIGHT
            else -> Fit.FITS
        }
    }

    /**
     * How much context to give the model, in tokens.
     *
     * The KV cache grows with the context, so the phone with least room
     * gets the smallest window. Halving it on a tight fit is the cheapest
     * thing to give up: this summarises at most three observations and a
     * handful of short passages, and 2048 tokens is more than that needs.
     */
    fun contextTokens(fit: Fit): Int = when (fit) {
        Fit.FITS -> 2048
        Fit.TIGHT -> 1024
        Fit.TOO_LARGE, Fit.UNSUPPORTED -> 0
    }

    /** Whether a model of this fit should be run at all. */
    fun runnable(fit: Fit): Boolean = fit == Fit.FITS || fit == Fit.TIGHT
}
