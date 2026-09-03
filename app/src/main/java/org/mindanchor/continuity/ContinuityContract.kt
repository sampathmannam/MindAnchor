package org.mindanchor.continuity

/**
 * The wire constants backup, restore, and the research export all agree on.
 *
 * Versions here are **additive, never rewritten**: a checkpoint or export
 * file written months ago must still decode, restore, and verify on
 * today's build, so an old version is retired from
 * [SUPPORTED_SNAPSHOT_FORMAT_VERSIONS] never — only added to.
 *
 * A version constant moves in the **same commit** as the shape it
 * describes, never ahead of it. A build that stamps a format version its
 * payload does not actually have has destroyed the one discriminator a
 * later reader would need to interpret the file correctly.
 */
object ContinuityContract {

    /**
     * The snapshot payload shape this build writes.
     *
     * Raised to 4 in the same commit that appended the advisory
     * opportunity and episode-event lists to [ContinuityPayload], never
     * before it: a build that stamps a version its payload does not
     * have destroys the one discriminator a later reader needs.
     */
    const val SNAPSHOT_FORMAT_VERSION = 4

    /**
     * Program 0's snapshot payload shape — the ten-field payload whose
     * content hash [ContinuityContentHasher] freezes. Named separately
     * from [SNAPSHOT_FORMAT_VERSION] because the two diverge as soon as
     * Program 1 appends its fields, and every existing encrypted
     * checkpoint on the user's Drive is this one.
     */
    const val PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION = 1

    /** Program 1's snapshot payload shape, including research history. */
    const val PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION = 2

    /** Program 2's snapshot payload shape, including the passive operational history lists. */
    const val PROGRAM_TWO_SNAPSHOT_FORMAT_VERSION = 3

    /** Every snapshot payload shape this build can decode and content-hash. */
    val SUPPORTED_SNAPSHOT_FORMAT_VERSIONS = setOf(
        PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
        PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION,
        PROGRAM_TWO_SNAPSHOT_FORMAT_VERSION,
        SNAPSHOT_FORMAT_VERSION,
    )

    /** The encrypted envelope shape. Program 1 does not change it. */
    const val ENVELOPE_FORMAT_VERSION = 1

    const val LATEST_FILE_NAME = "MindAnchor-Continuity-Latest.mab"

    /**
     * The research export's version identifier. It versions the data
     * dictionary and the export document shape together, because they are
     * frozen together — a dictionary change is an export change.
     *
     * Raised to v3 in the same commit that changed the export document,
     * never before it, for the same reason the snapshot version is: a file
     * stamped with a version its content does not have is a file nobody
     * can interpret later.
     */
    const val RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v3"

    /** Program 0's research export version. Still readable and still verifiable. */
    const val PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v1"

    /** Program 1's research export version, including the research ledger and provenance registries. */
    const val PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v2"

    /** Every research export version this build can decode and content-hash. */
    val SUPPORTED_RESEARCH_DICTIONARY_VERSIONS = setOf(
        PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION,
        PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION,
        RESEARCH_DICTIONARY_VERSION,
    )
}
