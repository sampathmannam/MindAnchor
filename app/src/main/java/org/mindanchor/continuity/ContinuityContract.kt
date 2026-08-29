package org.mindanchor.continuity

/**
 * The wire constants backup, restore, and the research export all agree on.
 *
 * Versions here are **additive, never rewritten**. Program 1 bumped the
 * snapshot payload and the research export shape, so both carry a new
 * current version and keep Program 0's version in the supported set
 * forever: a checkpoint or export file written months ago must still
 * decode, restore, and verify on today's build.
 */
object ContinuityContract {

    /** The snapshot payload shape this build writes. */
    const val SNAPSHOT_FORMAT_VERSION = 2

    /** Program 0's snapshot payload shape. Still readable, still verifiable. */
    const val PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION = 1

    /** Every snapshot payload shape this build can decode and content-hash. */
    val SUPPORTED_SNAPSHOT_FORMAT_VERSIONS =
        setOf(PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION, SNAPSHOT_FORMAT_VERSION)

    /** The encrypted envelope shape. Program 1 did not change it. */
    const val ENVELOPE_FORMAT_VERSION = 1

    const val LATEST_FILE_NAME = "MindAnchor-Continuity-Latest.mab"

    /**
     * The research export's single version identifier. It versions the data
     * dictionary and the export document shape together, because the design
     * freezes them together — a dictionary change is an export change.
     */
    const val RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v2"

    /** Program 0's research export version. */
    const val PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v1"

    /** Every research export version this build can decode and content-hash. */
    val SUPPORTED_RESEARCH_DICTIONARY_VERSIONS =
        setOf(PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION, RESEARCH_DICTIONARY_VERSION)
}
