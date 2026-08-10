package org.mindanchor.sim.personas

import org.mindanchor.vitals.DailyVitals
import java.time.LocalDate

/**
 * One synthetic persona — a 14-day schedule of [DailyVitals] and
 * behavioural events the launcher's pure-Kotlin logic can be exercised
 * against, with the citations that anchor the persona's behavioural
 * shape.
 *
 * ## Why a persona library, not real data
 *
 * The launcher cannot be tested against real longitudinal data on
 * this machine. It cannot be tested against a held-out real-user
 * test set either — the privacy promise is "your data never leaves
 * this phone," which forecloses the obvious test corpus. The
 * persona library is the *third* option: a set of synthetic
 * 14-day schedules, each anchored in verified research (see
 * `docs/research/22-research-index.md`), that exercise the
 * launcher's logic in known ways so the simulation runner can
 * compare what the launcher *did* against what the citation
 * predicted.
 *
 * ## Anchoring
 *
 * Every persona names the research it is built from. The shape
 * (early vs late chronotype, shift vs day worker, low vs high
 * activity, etc.) is grounded in the verified citations; the
 * specific numbers (HRV mean, RHR mean) are *plausible values*
 * consistent with the cited literature, not extracted from it.
 * The simulation run is not a clinical test of the literature —
 * it is a test of whether the launcher's logic respects the
 * literature when it has data in the shape the literature
 * describes.
 *
 * ## Determinism
 *
 * The persona generates a *deterministic* 14-day schedule given
 * a [seed] and a [start] date. The same seed + start + persona
 * always produces the same 14 days. This is what makes the
 * simulation runner's output reproducible across runs and
 * machines — a regression in the launcher's logic against a
 * persona produces a different number on the same input.
 */
interface Persona {

    /** Stable identifier, used in simulation output. */
    val id: String

    /** Human-readable name, used in tests and reports. */
    val name: String

    /**
     * Short description of the behavioural shape, with citations.
     * The citations here must be the same ones in
     * `docs/research/22-research-index.md` — if you change one,
     * change both.
     */
    val description: String

    /**
     * Generate the 14-day schedule for this persona, starting at
     * [start]. The schedule is deterministic for a given [seed].
     */
    fun schedule(start: LocalDate, seed: Long): List<DailyVitals>
}
