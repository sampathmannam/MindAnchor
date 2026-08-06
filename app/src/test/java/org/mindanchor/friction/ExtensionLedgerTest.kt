package org.mindanchor.friction

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionLedgerTest {

    @Test
    fun `empty ledger counts zero`() {
        assertEquals(0, ExtensionLedger.count("", "com.a", "2026-08-06"))
    }

    @Test
    fun `increment counts per app per day`() {
        var ledger = ExtensionLedger.increment("", "com.a", "2026-08-06")
        ledger = ExtensionLedger.increment(ledger, "com.a", "2026-08-06")
        ledger = ExtensionLedger.increment(ledger, "com.b", "2026-08-06")
        assertEquals(2, ExtensionLedger.count(ledger, "com.a", "2026-08-06"))
        assertEquals(1, ExtensionLedger.count(ledger, "com.b", "2026-08-06"))
    }

    @Test
    fun `yesterday's entries are dropped on new-day increment`() {
        var ledger = ExtensionLedger.increment("", "com.a", "2026-08-05")
        ledger = ExtensionLedger.increment(ledger, "com.a", "2026-08-06")
        assertEquals(1, ExtensionLedger.count(ledger, "com.a", "2026-08-06"))
        assertEquals(0, ExtensionLedger.count(ledger, "com.a", "2026-08-05"))
    }

    @Test
    fun `malformed lines are ignored`() {
        val ledger = "garbage\ncom.a\t2026-08-06\tnotanumber"
        assertEquals(0, ExtensionLedger.count(ledger, "com.a", "2026-08-06"))
    }
}
