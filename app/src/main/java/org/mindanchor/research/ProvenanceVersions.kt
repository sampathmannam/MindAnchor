package org.mindanchor.research

import kotlinx.serialization.Serializable
import org.mindanchor.continuity.ContinuityContract

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
 * Builds the current [ProvenanceVector].
 *
 * Every component is read from whatever actually owns it, never restated
 * here — so a change in the protocol catalogue, the transformation
 * registry, the missing-data policy, the morning measure's instrument, or
 * the data dictionary opens a new study phase without anyone having to
 * remember to make it do so.
 */
object ProvenanceVersions {

    /**
     * This build ships no decision rules.
     *
     * Not a placeholder: it is the honest statement that nothing in this
     * build decides anything about a person's state. Program 2's first
     * rule set replaces this constant, and because the constant is part of
     * the vector, that replacement opens a study phase by construction.
     */
    const val RULE_SET_VERSION = "rule-set-none-v1"

    /** This build ships no models. Same reasoning as [RULE_SET_VERSION]. */
    const val MODEL_SET_VERSION = "model-set-none-v1"

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
