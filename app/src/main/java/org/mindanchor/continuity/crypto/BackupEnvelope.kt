package org.mindanchor.continuity.crypto

import kotlinx.serialization.Serializable
import org.mindanchor.continuity.ContinuityContract

/**
 * The portable, authenticated encryption envelope around a continuity
 * snapshot's JSON. See [BackupEnvelopeCodec] for encrypt/decrypt.
 *
 * Portable: nothing about this shape or [BackupEnvelopeCodec] binds to the
 * source phone's Android Keystore — only [RecoveryKey.bytes] (which the
 * user carries as the `MA1-...` human form) is needed to decrypt, on any
 * device.
 */
@Serializable
data class BackupEnvelope(
    val formatVersion: Int,
    val keyId: String,
    val createdAt: Long,
    val ivBase64: String,
    val ciphertextBase64: String,
    val plaintextSha256: String,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = ContinuityContract.ENVELOPE_FORMAT_VERSION
    }
}
