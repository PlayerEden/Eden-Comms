package com.edenpractical.comms.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyMethodCatalogTest {
    @Test fun allThreeRealDevelopmentMethodsHaveOneDistinctHonestEntry() {
        val methods = NearbyMethodCatalog.methods
        assertEquals(ConnectionMethod.values().toSet(), methods.map { it.id }.toSet())
        assertEquals(ConnectionMethod.values().size, methods.size)
        assertEquals(methods.size, methods.distinctBy { it.id }.size)
        assertEquals(
            listOf(ConnectionMethod.LAN, ConnectionMethod.BLUETOOTH, ConnectionMethod.WIFI_DIRECT),
            methods.map { it.id }
        )
        assertTrue(methods.all {
            it.availability == NearbyMethodCatalog.Availability.DEBUG_TEST_ONLY &&
                it.title.isNotBlank() && it.description.isNotBlank() &&
                it.requirements.isNotBlank() && it.futureDiscovery.isNotBlank()
        })
    }

    @Test fun releaseAlwaysUnavailableButDebugHasSpecificExistingEntrypoint() {
        for (method in ConnectionMethod.values()) {
            assertSame(
                NearbyMethodCatalog.Selection.NotAvailable,
                NearbyMethodCatalog.select(method, debuggable = false)
            )
            assertFalse(NearbyMethodCatalog.isDebugEntry(method, debuggable = false))
            assertTrue(NearbyMethodCatalog.isDebugEntry(method, debuggable = true))
        }
        assertSame(
            NearbyMethodCatalog.Selection.LaunchDebugLanQr,
            NearbyMethodCatalog.select(ConnectionMethod.LAN, true)
        )
        assertSame(
            NearbyMethodCatalog.Selection.LaunchDebugBluetooth,
            NearbyMethodCatalog.select(ConnectionMethod.BLUETOOTH, true)
        )
        assertSame(
            NearbyMethodCatalog.Selection.LaunchDebugWifiDirectPreflight,
            NearbyMethodCatalog.select(ConnectionMethod.WIFI_DIRECT, true)
        )
        assertFalse(NearbyMethodCatalog.canEnterDebugLan(debuggable = false))
        assertTrue(NearbyMethodCatalog.canEnterDebugLan(debuggable = true))
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun methodSelectionDoesNotPretendRadioProofOrGrantApprovals() {
        val lan = NearbyMethodCatalog.current(ConnectionMethod.LAN)
        assertTrue(lan.description.contains("Internet is not needed"))
        assertTrue(lan.requirements.contains("Two QR scans"))
        assertTrue(lan.futureDiscovery.contains("not automatic device search"))
        val bt = NearbyMethodCatalog.current(ConnectionMethod.BLUETOOTH)
        assertTrue(bt.requirements.contains("permissions"))
        assertTrue(bt.requirements.contains("both approvals"))
        assertTrue(bt.futureDiscovery.contains("No production discovery"))
        val p2p = NearbyMethodCatalog.current(ConnectionMethod.WIFI_DIRECT)
        assertTrue(p2p.requirements.contains("preflight"))
        assertTrue(p2p.requirements.contains("both approvals"))
        assertTrue(p2p.futureDiscovery.contains("Not production discovery"))
        assertTrue(NearbyMethodCatalog.methods.all {
            it.availability != NearbyMethodCatalog.Availability.PLANNED
        })
    }
}
