package org.mindanchor.vitals.coros

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Known-answer tests for the COROS password hasher.
 *
 * The hash scheme is the public one from `cygnusb/coros-mcp`:
 *   `hashlib.md5(value.encode()).hexdigest()`
 * — plain MD5 of the UTF-8 bytes, lowercase hex. These tests
 * pin a few values so a future refactor (e.g. switching to
 * a `DigestUtils.md5Hex` helper) cannot silently break the
 * wire format the COROS server expects.
 *
 * v0.20.7 (CodeRabbit audit on the bridge): the uppercase
 * vs lowercase hex distinction was tested in production
 * once (2025-12-04) — uppercase returned `result: "1001"`
 * from `/account/login`. The lowercase assertion below is
 * the regression test for that finding.
 */
class CorosPasswordHasherTest {

    @Test
    fun `empty string hashes to the well-known empty MD5`() {
        // MD5("") = d41d8cd98f00b204e9800998ecf8427e
        assertEquals(
            "d41d8cd98f00b204e9800998ecf8427e",
            CorosPasswordHasher.md5Hex(""),
        )
    }

    @Test
    fun `lowercase ASCII password matches the python md5 hex digest`() {
        // MD5("password") = 5f4dcc3b5aa765d61d8327deb882cf99
        assertEquals(
            "5f4dcc3b5aa765d61d8327deb882cf99",
            CorosPasswordHasher.md5Hex("password"),
        )
    }

    @Test
    fun `hash output is always lowercase hex`() {
        // The COROS API rejects uppercase hex (verified
        // 2025-12-04 against a real account). The contract
        // is the hex digits are 0-9 and a-f only.
        val out = CorosPasswordHasher.md5Hex("UPPER_and_lower_42")
        assertEquals(
            "every character must be a lowercase hex digit",
            out,
            out.lowercase(),
        )
        assertTrue(
            "hash must be 32 hex characters",
            out.length == 32,
        )
        assertTrue(
            "hash must match the hex pattern",
            out.matches(Regex("[0-9a-f]{32}")),
        )
    }

    @Test
    fun `UTF-8 bytes are hashed, not Java chars`() {
        // A non-ASCII string. The exact value is the MD5
        // of the UTF-8 bytes of the 4-character string
        // "café" — the same one any standard library
        // produces.
        // MD5("café" UTF-8) = 07117fe4a1ebd544965dc19573183da2
        assertEquals(
            "07117fe4a1ebd544965dc19573183da2",
            CorosPasswordHasher.md5Hex("café"),
        )
    }

    @Test
    fun `CharSequence input other than String is accepted`() {
        // The SettingsScreen's password field is a
        // String at the call site, but the contract is
        // CharSequence so a future CharArray-backed
        // password editor can use this without copying
        // through String.
        val asStringBuilder: CharSequence = StringBuilder("builder-not-string")
        assertNotNull(CorosPasswordHasher.md5Hex(asStringBuilder))
    }
}
