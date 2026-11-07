package com.edenpractical.comms.wifidirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWifiDirectCandidateLedgerTest {
    @Test fun stableAnonymousOrdinalsAndHardBound() {
        val ledger = DebugWifiDirectCandidateLedger<String>()
        val token = requireNotNull(ledger.begin())
        assertNull(ledger.begin())
        assertTrue(ledger.observe(token, listOf("alpha", "beta", "alpha")))
        assertEquals(listOf("Unverified device 1", "Unverified device 2"),
            ledger.rows(token).map { it.label })
        assertTrue(ledger.observe(token, listOf("beta", "charlie")))
        assertFalse(ledger.observe(token, listOf("charlie")))
        assertEquals(listOf("Unverified device 1", "Unverified device 2",
            "Unverified device 3"), ledger.rows(token).map { it.label })
        assertTrue(ledger.observe(token, (1..20).map { "other-$it" }))
        assertEquals(DebugWifiDirectCandidateLedger.MAX_CANDIDATES, ledger.rows(token).size)
        assertEquals((1..8).toList(), ledger.rows(token).map { it.ordinal })
        assertTrue(ledger.rows(token).all { !it.selected })
    }

    @Test fun selectionIsOnlyALocalOrdinalAndNeverReturnsHandle() {
        val ledger = DebugWifiDirectCandidateLedger<Any>()
        val token = requireNotNull(ledger.begin())
        val a = Any()
        val b = Any()
        assertTrue(ledger.observe(token, listOf(a, b)))
        assertFalse(ledger.choose(token, 0))
        assertFalse(ledger.choose(token, 3))
        assertTrue(ledger.choose(token, 2))
        assertEquals(2, ledger.selectedOrdinal(token))
        assertEquals(listOf(false, true), ledger.rows(token).map { it.selected })
        assertFalse(ledger.observe(token, listOf(Any())))
        assertEquals(2, ledger.rows(token).size)
        ledger.clear()
        assertTrue(ledger.rows(token).isEmpty())
        assertNull(ledger.selectedOrdinal(token))
    }

    @Test fun oldCallbacksAndSelectionsAreRejectedAfterClearAndNewStart() {
        val ledger = DebugWifiDirectCandidateLedger<String>()
        val old = requireNotNull(ledger.begin())
        assertTrue(ledger.observe(old, listOf("old")))
        ledger.clear()
        val fresh = requireNotNull(ledger.begin())
        assertTrue(fresh.generation > old.generation)
        assertFalse(ledger.observe(old, listOf("late")))
        assertFalse(ledger.choose(old, 1))
        assertNull(ledger.selectedOrdinal(old))
        assertTrue(ledger.observe(fresh, listOf("fresh")))
        assertEquals(1, ledger.rows(fresh).size)
        assertEquals(0, ledger.rows(old).size)
    }

    @Test fun oneShotTargetTransferErasesLedgerBeforePlatformUse() {
        val ledger = DebugWifiDirectCandidateLedger<String>()
        val token = requireNotNull(ledger.begin())
        assertTrue(ledger.observe(token, listOf("nearby-a", "nearby-b")))
        assertFalse(ledger.consumeForOneShotLink(token) { throw AssertionError("Not selected") })
        assertTrue(ledger.choose(token, 2))
        var seen = ""
        assertTrue(ledger.consumeForOneShotLink(token) { handle ->
            // The sink receives only the selected opaque handle after erasure.
            assertTrue(ledger.rows(token).isEmpty())
            assertNull(ledger.selectedOrdinal(token))
            seen = handle
        })
        assertEquals("nearby-b", seen)
        assertFalse(ledger.consumeForOneShotLink(token) { throw AssertionError("Reused") })
        assertFalse(ledger.choose(token, 2))
        val next = requireNotNull(ledger.begin())
        assertFalse(ledger.consumeForOneShotLink(token) { throw AssertionError("Stale") })
        assertTrue(ledger.observe(next, listOf("new")))
        assertTrue(ledger.choose(next, 1))
        assertTrue(ledger.consumeForOneShotLink(next) { assertEquals("new", it) })
    }

    @Test fun clearingAndRestartingNeverPreserveOldCandidateHandles() {
        val ledger = DebugWifiDirectCandidateLedger<String>()
        val first = requireNotNull(ledger.begin())
        ledger.observe(first, listOf("a", "b"))
        ledger.choose(first, 1)
        ledger.clear()
        assertNull(ledger.selectedOrdinal(first))
        val second = requireNotNull(ledger.begin())
        assertTrue(ledger.rows(second).isEmpty())
        assertTrue(ledger.observe(second, listOf("c")))
        assertEquals("Unverified device 1", ledger.rows(second).single().label)
    }
}
