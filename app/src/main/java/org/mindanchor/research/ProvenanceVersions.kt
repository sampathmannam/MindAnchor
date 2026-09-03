package org.mindanchor.research

import kotlinx.serialization.Serializable
import org.mindanchor.advisory.AdvisoryPolicy
import org.mindanchor.continuity.ContinuityContract
import org.mindanchor.intelligence.PassiveEstimator

/**
 * Everything that could change how a record is produced or interpreted,
 * captured as one value.
 *
 * A study phase carries the vector that was in effect while it ran. Two
 * records made under different vectors were made by different software and
 * must not be pooled without saying so — which is the whole content of the
 * design's rule that historical decisions are never silently
 * reinterpreted.
 */
@Serializable
data class ProvenanceVector(
    val appVersionCode: Int,
    val appVersionName: String,
    val protocolCatalogSha256: String,
    val ruleSetVersion: String,
    val modelSetVersion: String,
    val transformationSetVersion: String,
    val missingDataPolicyVersion: String,
    val instrumentVersion: String,
    val dictionaryVersion: String,
    val sourceDeviceId: String,
)

/**
 * One stored value carrying both rule-set versions, kept decomposable.
 *
 * A phase records which version of every rule body ran while it ran.
 * Program 3 adds a second body, and overwriting the passive value with
 * it would make the older question — which passive rules produced this
 * record? — permanently unanswerable. Encoding both, in a form a reader
 * can take apart again, keeps it answerable. Values written before this
 * shape existed carry no prefix and read back as passive-only, which is
 * exactly what they were.
 */
object RuleSetVersionVector {
    private const val PREFIX = "rule-version-vector-v1|"

    fun encode(passive: String, advisory: String): String =
        "${PREFIX}passive=$passive|advisory=$advisory"

    fun passive(value: String): String =
        if (value.startsWith(PREFIX)) {
            value.substringAfter("passive=").substringBefore("|advisory=")
        } else {
            value
        }

    fun advisory(value: String): String? =
        if (value.startsWith(PREFIX)) value.substringAfter("|advisory=") else null
}

/**
 * Builds the current [ProvenanceVector].
 *
 * Every component is read from whatever actually owns it, never restated
 * here — so a change in the protocol catalogue, the transformation
 * registry, the missing-data policy, the morning measure's instrument, or
 * the data dictionary opens a new study phase without anyone having to
 * remember to make it do so.
 */
object ProvenanceVersions {

    /** Tracks the rule version declared by the passive estimator itself. */
    const val PASSIVE_RULE_SET_VERSION = PassiveEstimator.RULE_VERSION

    /** Tracks the rule version declared by the advisory policy itself. */
    const val ADVISORY_RULE_SET_VERSION = AdvisoryPolicy.RULE_VERSION

    /**
     * Both rule versions in effect. A newly opened phase legitimately
     * reports one rule-version change as this value replaces the
     * passive-only value earlier phases carry.
     */
    val RULE_SET_VERSION: String = RuleSetVersionVector.encode(
        passive = PASSIVE_RULE_SET_VERSION,
        advisory = ADVISORY_RULE_SET_VERSION,
    )

    /** The personal robust baseline model used by passive intelligence. */
    const val MODEL_SET_VERSION = "personal-robust-baseline-v4"

    fun vector(appVersionCode: Int, appVersionName: String, sourceDeviceId: String): ProvenanceVector =
        ProvenanceVector(
            appVersionCode = appVersionCode,
            appVersionName = appVersionName,
            protocolCatalogSha256 = EvidenceProtocolCatalog.registry.catalogSha256,
            ruleSetVersion = RULE_SET_VERSION,
            modelSetVersion = MODEL_SET_VERSION,
            transformationSetVersion = TransformationRegistry.setVersion,
            missingDataPolicyVersion = MissingDataPolicy.VERSION,
            instrumentVersion = MorningMeasure.INSTRUMENT_VERSION,
            dictionaryVersion = ContinuityContract.RESEARCH_DICTIONARY_VERSION,
            sourceDeviceId = sourceDeviceId,
        )
}
