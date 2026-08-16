package org.mindanchor.narrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding tests for v0.23.0 Phi-4 mini download.
 *
 * Each test pins one piece of the contract the v0.23.0
 * release depends on. A refactor that breaks the test
 * forces the contributor to think about whether the
 * shape is still right.
 */
class Phi4ModelDownloadFindingTest {

    /**
     * The Unsloth mirror URL is the default. Pinning
     * the exact string means a refactor that swaps to
     * Microsoft's mirror (or to a different model
     * family) is a finding-test failure, not a silent
     * behaviour change for the user.
     */
    @Test
    fun `primary URL is the Unsloth mirror and points to Q2_K`() {
        // v0.31.1: Q4_K_M (2.32 GB) was too large for
        // phones with 1.5-2 GB free RAM. The Q2_K
        // quantisation (1.57 GB) fits with margin.
        assertEquals(
            "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/" +
                "Phi-4-mini-instruct-Q2_K.gguf",
            Phi4ModelDownload.PRIMARY_URL,
        )
    }

    /**
     * The fallback is the Microsoft mirror. The
     * launcher does not auto-failover today, but the
     * constant is here so a future contributor who
     * wires the failover knows which URL is the
     * "official" second choice.
     */
    @Test
    fun `fallback URL is the Microsoft mirror and points to Q2_K`() {
        // v0.31.1: matches the new primary.
        assertEquals(
            "https://huggingface.co/microsoft/Phi-4-mini-instruct-GGUF/resolve/main/" +
                "Phi-4-mini-instruct-Q2_K.gguf",
            Phi4ModelDownload.FALLBACK_URL,
        )
    }

    /**
     * The file is named to match the upstream artefact
     * exactly. A user who has already downloaded the
     * file from a browser looks for it by name; the
     * launcher uses the same name so a future
     * "already-downloaded" fast path is obvious.
     */
    @Test
    fun `download subpath matches the upstream Q2_K filename`() {
        // v0.31.1: was Q4_K_M, now Q2_K.
        assertEquals(
            "Phi-4-mini-instruct-Q2_K.gguf",
            Phi4ModelDownload.DOWNLOAD_SUBPATH,
        )
    }

    /**
     * The approximate size is the order of magnitude the
     * UI advertises ("2.49 GB"). The actual download
     * may be a few kilobytes off; this is the number the
     * user is told to expect.
     */
    @Test
    fun `approximate size is in the 1-point-5 GB ballpark`() {
        // v0.31.1: was Q4_K_M (2.0-3.0 GB ballpark).
        // Q2_K is 1.5-1.7 GB.
        val size = Phi4ModelDownload.APPROXIMATE_BYTES
        // 1.0 GB .. 2.0 GB. The exact figure varies
        // slightly across HuggingFace rebuilds; the
        // bracket pins the order of magnitude.
        assertTrue("size must be at least 1 GB: $size", size >= 1_000_000_000L)
        assertTrue("size must be at most 2 GB: $size", size <= 2_000_000_000L)
    }

    /**
     * The launcher has the URL constants right; the
     * primary and fallback both point at the
     * Q4_K_M weights, and both resolve to the same
     * filename. A future contributor who refactors the
     * URLs (e.g., to use a different quantisation) has
     * to update both URLs together.
     */
    @Test
    fun `both URLs target the Q2_K file`() {
        // v0.31.1: was Q4_K_M, now Q2_K.
        assertTrue(Phi4ModelDownload.PRIMARY_URL.contains("Q2_K"))
        assertTrue(Phi4ModelDownload.FALLBACK_URL.contains("Q2_K"))
        // The basename is the same in both, so a
        // download started from one and finished from
        // the other would land in the same destination.
        assertEquals(
            Phi4ModelDownload.PRIMARY_URL.substringAfterLast('/'),
            Phi4ModelDownload.FALLBACK_URL.substringAfterLast('/'),
        )
    }

    /**
     * The receiver filter rejects any URI that is not
     * the Phi-4 file. A user who has two downloads in
     * flight (e.g., a different model from the browser)
     * will not have the launcher's receiver triggered
     * for that other download.
     */
    @Test
    fun `isPhi4File accepts the right basename and rejects others`() {
        val right = "file:///storage/emulated/0/Download/${Phi4ModelDownload.DOWNLOAD_SUBPATH}"
        assertTrue("the right basename must be accepted", Phi4ModelDownload.isPhi4File(right))
        val wrong = "file:///storage/emulated/0/Download/llama-3-8b.gguf"
        assertFalse("a different file must be rejected", Phi4ModelDownload.isPhi4File(wrong))
        assertFalse("null must be rejected", Phi4ModelDownload.isPhi4File(null))
        assertFalse("a blank string must be rejected", Phi4ModelDownload.isPhi4File(""))
        // The system also delivers `content://` URIs.
        val contentUri = "content://com.android.providers.downloads.documents/document/1234"
        assertFalse(
            "a content URI for a different file must be rejected",
            Phi4ModelDownload.isPhi4File(contentUri),
        )
    }

    /**
     * The Android [DownloadManager] appends `-N` to the
     * destination filename when a file with the requested
     * name already exists in the public Downloads
     * collection. The launcher must still recognise the
     * download as the Phi-4 model — otherwise a user who
     * has any prior download attempt on the device (a
     * browser download, a previous in-app retry) will see
     * "no model on file" even after the system download
     * completed. v0.30.1 (auto-integration bug).
     */
    @Test
    fun `isPhi4File accepts the DownloadManager collision-suffix basename`() {
        // v0.31.1: the basename prefix is now
        // "Phi-4-mini-instruct-Q" (covers both the
        // v0.30.x Q4_K_M files already on disk and
        // the new Q2_K download). The collision
        // suffix is unchanged — Android's
        // DownloadManager still appends `-N` on
        // filename collision.
        val collisionOne = "file:///storage/emulated/0/Download/Phi-4-mini-instruct-Q2_K-1.gguf"
        assertTrue(
            "first collision suffix must be accepted",
            Phi4ModelDownload.isPhi4File(collisionOne),
        )
        val collisionFour = "file:///storage/emulated/0/Download/Phi-4-mini-instruct-Q2_K-4.gguf"
        assertTrue(
            "later collision suffix must be accepted",
            Phi4ModelDownload.isPhi4File(collisionFour),
        )
        // Negative cases — the prefix + extension
        // check must not over-accept. The v0.31.1
        // prefix is "Phi-4-mini-instruct-Q" which
        // matches Q2_K, Q3_K_*, Q4_K_*, etc. So
        // a different QUANT of the same family is
        // still a positive match — the loader
        // picks up the actual format from the GGUF
        // header. The negative case is therefore
        // "wrong file family" or "wrong extension".
        val wrongFamily = "file:///storage/emulated/0/Download/Phi-4-mini-reasoning-Q2_K.gguf"
        assertFalse(
            "a different model family with the same family prefix must be rejected",
            Phi4ModelDownload.isPhi4File(wrongFamily),
        )
        val fakePhi = "file:///storage/emulated/0/Download/Phi-4-mini-instruct-Q2_K.txt"
        assertFalse(
            "the right prefix with the wrong extension must be rejected",
            Phi4ModelDownload.isPhi4File(fakePhi),
        )
    }
}
