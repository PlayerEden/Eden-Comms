package com.edenpractical.comms.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBluetoothCandidateLedgerTest {
    @Test fun collectsUpToTwelveDistinctOpaqueHandlesAndDeduplicates() {
        val ledger = DebugBluetoothCandidateLedger<String>()
        val token = requireNotNull(ledger.begin())
        repeat(12) { i ->
            assertTrue(ledger.observe(token, "opaque-handle-$i"))
        }
        assertEquals(12, ledger.rows(token).size)
        assertFalse(ledger.observe(token, "opaque-handle-2"))
        assertFalse(ledger.observe(token, "opaque-handle-12"))
        assertEquals(
            (1..12).map { "Unverified signal $it" },
            ledger.rows(token).map { it.label }
        )
        assertTrue(ledger.rows(token).all { it.ordinal in 1..12 })
        assertEquals(DebugBluetoothCandidateLedger.Phase.COLLECTING, ledger.phase())
        assertNull(ledger.selectedOrdinal())
    }

    @Test fun selectingExplicitlyKeepsOnlyOneHandleAndRejectsInvalidSelections() {
        val ledger = DebugBluetoothCandidateLedger<Any>()
        val token = requireNotNull(ledger.begin())
        val a = Any()
        val b = Any()
        assertTrue(ledger.observe(token, a))
        assertTrue(ledger.observe(token, b))
        assertFalse(ledger.select(token, 0))
        assertFalse(ledger.select(token, 3))
        assertTrue(ledger.select(token, 2))
        assertEquals(DebugBluetoothCandidateLedger.Phase.SELECTED, ledger.phase())
        assertEquals(2, ledger.selectedOrdinal())
        assertTrue(ledger.rows(token).isEmpty())
        assertFalse(ledger.observe(token, Any()))
        assertFalse(ledger.finish(token))
        assertFalse(ledger.select(token, 1))
        ledger.clear()
        assertNull(ledger.selectedOrdinal())
        assertEquals(DebugBluetoothCandidateLedger.Phase.EMPTY, ledger.phase())
    }

    @Test fun completedScanCanBeReviewedButNotExtendedOrReused() {
        val ledger = DebugBluetoothCandidateLedger<String>()
        val token = requireNotNull(ledger.begin())
        assertTrue(ledger.observe(token, "A"))
        assertTrue(ledger.finish(token))
        assertEquals(DebugBluetoothCandidateLedger.Phase.REVIEW, ledger.phase())
        assertFalse(ledger.finish(token))
        assertFalse(ledger.observe(token, "B"))
        assertEquals(listOf("Unverified signal 1"), ledger.rows(token).map { it.label })
        assertTrue(ledger.select(token, 1))
        assertEquals(1, ledger.selectedOrdinal())
    }

    @Test fun staleScanCallbacksCannotPoisonNewScanOrSelectedDevice() {
        val ledger = DebugBluetoothCandidateLedger<String>()
        val old = requireNotNull(ledger.begin())
        assertTrue(ledger.observe(old, "old"))
        assertNull(ledger.begin()) // must not replace a running scan
        assertEquals(1, ledger.rows(old).size)
        ledger.clear()
        assertTrue(ledger.rows(old).isEmpty())
        val current = requireNotNull(ledger.begin())
        assertTrue(current.generation > old.generation)
        assertFalse(ledger.observe(old, "old-after-restart"))
        assertFalse(ledger.finish(old))
        assertFalse(ledger.select(old, 1))
        assertTrue(ledger.observe(current, "new"))
        assertEquals(1, ledger.rows(current).size)
        assertTrue(ledger.select(current, 1))
        assertEquals(1, ledger.selectedOrdinal())
        assertTrue(ledger.rows(old).isEmpty())
        assertNotEquals(DebugBluetoothCandidateLedger.Phase.COLLECTING, ledger.phase())
    }

    @Test fun allTemporaryHandlesAreDiscardedByExplicitForget() {
        val ledger = DebugBluetoothCandidateLedger<String>(maxCandidates = 2)
        val old = requireNotNull(ledger.begin())
        assertTrue(ledger.observe(old, "one"))
        assertTrue(ledger.observe(old, "two"))
        assertFalse(ledger.observe(old, "three"))
        ledger.clear()
        assertTrue(ledger.rows(old).isEmpty())
        assertNull(ledger.selectedOrdinal())
        val second = requireNotNull(ledger.begin())
        assertTrue(ledger.observe(second, "three"))
        assertFalse(ledger.observe(old, "one"))
        assertEquals(listOf("Unverified signal 1"), ledger.rows(second).map { it.label })
    }
}
