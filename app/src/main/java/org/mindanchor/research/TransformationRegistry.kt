package org.mindanchor.research

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
 * that list — and, importantly, it is the *actual* list, not an aspiration:
 * Program 1 performs exactly two transformations, and both are here.
 * Program 2's feature windows and baselines join it, and because
 * [setVersion] is part of the provenance version vector, that arrival opens
 * a new study phase by construction rather than by anyone remembering to.
 */
object TransformationRegistry {

    /**
     * Pinned: this configuration is part of [setVersion], which is part of
     * the version vector. Changing it would open a study phase for no
     * reason a reader could explain.
     */
    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = true
    }

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
    )

    /** The version of the transformation with [id], or null if this build performs no such transformation. */
    fun versionOf(id: String): String? = transformations.firstOrNull { it.id == id }?.version

    /** The content hash of [transformations] — the vector component. */
    val setVersion: String by lazy { setVersionOf(transformations) }

    /**
     * The content hash of an arbitrary transformation list, sorted by id so
     * declaration order is not a version. Exposed so a test can prove that
     * adding or changing a transformation moves the version.
     */
    fun setVersionOf(transformations: List<Transformation>): String {
        val canonical = transformations.sortedBy { it.id }
        return MessageDigest.getInstance("SHA-256")
            .digest(json.encodeToString(canonical).encodeToByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }
}
