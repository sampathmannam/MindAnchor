package org.mindanchor.research

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import org.mindanchor.journal.StructuralContextExtractor

/** One raw-to-derived transformation this build performs, and its version. */
@Serializable
data class Transformation(
    val id: String,
    val version: String,
    val input: String,
    val output: String,
    val description: String,
)

/**
 * Every transformation that turns a raw record into a derived one, listed
 * with its own version.
 *
 * The design calls for "versioned raw-to-feature transformations". This is
 * that list — and, importantly, it is the *actual* list, not an aspiration.
 * Because [setVersion] is part of the provenance version vector, adding or
 * changing a transformation opens a new study phase by construction rather
 * than by anyone remembering to.
 */
object TransformationRegistry {

    val transformations: List<Transformation> = listOf(
        Transformation(
            id = "structural-context",
            // Reads the extractor's own constant rather than restating it,
            // so changing the extractor opens a study phase automatically.
            version = StructuralContextExtractor.EXTRACTOR_VERSION,
            input = "One Journal entry, as the person wrote it.",
            output = "Structural FACT rows: entry kind, local date, word count, user-authored title.",
            description = "Derives structural metadata only. It reads no meaning from the body text: " +
                "no sentiment, no inferred emotion, no clinical interpretation.",
        ),
        Transformation(
            id = "research-export-canonicalisation",
            version = "export-canon-v1",
            input = "Journal entries, structural context, morning measures, ledger events, study phases.",
            output = "A canonically sorted, content-hashed research export document.",
            description = "Sorts every list into a stable order and hashes the content, so two exports " +
                "of the same data agree byte for byte regardless of when or where they were taken.",
        ),
        Transformation(
            id = "passive-personal-baseline",
            version = "personal-baseline-v3",
            input = "Point-in-time eligible daily passive-feature revisions within one baseline segment.",
            output = "Frozen-reference and trailing-candidate feature centres, scales, and sample counts.",
            description = "Builds a personal baseline from median/MAD statistics, with a declared IQR fallback " +
                "for zero MAD and eligibility floors for total, weekday, weekend, and stratum data. Canonical " +
                "point-in-time " +
                "revisions freeze at the first eligible ingestion cutoff. The frozen reference and trailing " +
                "candidate use the same pooling decision for a like-for-like population comparison.",
        ),
        Transformation(
            id = "passive-block-calibration",
            version = "block-calibration-v3",
            input = "Historical daily passive observation scores.",
            output = "A block-resampled threshold, expected episode rate, seed, and configuration.",
            description = "Scores each historical day against its own weekday/weekend stratum, then calibrates " +
                "thresholds with block resampling against an engineering false-observation budget rather than " +
                "clinical accuracy.",
        ),
        Transformation(
            id = "passive-observation-explanation",
            version = "observation-explanation-v1",
            input = "A passive observation state with its domain evidence and data status.",
            output = "A deterministic observation-only explanation.",
            description = "Renders fixed templates that describe recorded signals and data limitations without " +
                "diagnosis, prediction, or intervention language.",
        ),
    )

    /** The version of the transformation with [id], or null if this build performs no such transformation. */
    fun versionOf(id: String): String? = transformations.firstOrNull { it.id == id }?.version

    /** The content hash of [transformations] — the vector component. */
    val setVersion: String by lazy { setVersionOf(transformations) }

    /**
     * SHA-256 over every `id@version` line, sorted, mirroring
     * [EvidenceProtocolRegistry.catalogSha256]'s line format.
     *
     * Deliberately **not** over the whole [Transformation]: `input`,
     * `output` and `description` are documentation, and hashing them would
     * mean a typo fix in a description opened a new study phase and split
     * the series for a change with no semantic content. Each
     * transformation's own [Transformation.version] is its semantic
     * identity.
     *
     * If the frozen set-version test goes red, the answer is a version
     * bump on the transformation that actually changed — not a re-pinned
     * constant.
     */
    fun setVersionOf(entries: List<Transformation>): String {
        val lines = entries
            .map { "${it.id}@${it.version}" }
            .sorted()
            .joinToString(separator = "\n")
        return MessageDigest.getInstance("SHA-256")
            .digest(lines.encodeToByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }
}
