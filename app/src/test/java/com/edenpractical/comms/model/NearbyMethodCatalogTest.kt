package com.edenpractical.comms.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyMethodCatalogTest {
    @Test fun everyProductMethodHasExactlyOneEntryAndOnlyLanHasDebugTest() {
        val methods = NearbyMethodCatalog.methods
        assertEquals(ConnectionMethod.values().toSet(), methods.map { it.id }.toSet())
        assertEquals(ConnectionMethod.values().size, methods.size)
        assertEquals(methods.size, methods.distinctBy { it.id }.size)
        assertEquals(
            listOf(ConnectionMethod.LAN, ConnectionMethod.BLUETOOTH, ConnectionMethod.WIFI_DIRECT),
            methods.map { it.id }
        )
        assertEquals(
            listOf(NearbyMethodCatalog.Availability.DEBUG_TEST_ONLY,
                NearbyMethodCatalog.Availability.PLANNED,
                NearbyMethodCatalog.Availability.PLANNED),
            methods.map { it.availability }
        )
        assertTrue(methods.all {
            it.title.isNotBlank() && it.description.isNotBlank() &&
                it.requirements.isNotBlank() && it.futureDiscovery.isNotBlank()
        })
    }

    @Test fun onlyDebugLanCanEnterExistingQrPathAndNoMethodUnlocksRelease() {
        for (method in ConnectionMethod.values()) {
            assertSame(
                NearbyMethodCatalog.Selection.NotAvailable,
                NearbyMethodCatalog.select(method, debuggable = false)
            )
            if (method == ConnectionMethod.LAN) {
                assertSame(
                    NearbyMethodCatalog.Selection.LaunchDebugLanQr,
                    NearbyMethodCatalog.select(method, debuggable = true)
                )
            } else {
                assertSame(
                    NearbyMethodCatalog.Selection.NotAvailable,
                    NearbyMethodCatalog.select(method, debuggable = true)
                )
            }
        }
        assertFalse(NearbyMethodCatalog.canEnterDebugLan(debuggable = false))
        assertTrue(NearbyMethodCatalog.canEnterDebugLan(debuggable = true))
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun discoveryIsNotPretendedForAnyMethodAndNoInternetIsRequiredForLanTest() {
        val lan = NearbyMethodCatalog.current(ConnectionMethod.LAN)
        assertTrue(lan.description.contains("Internet is not needed"))
        assertTrue(lan.requirements.contains("Two QR scans"))
        assertTrue(lan.futureDiscovery.contains("not automatic device search"))
        for (method in listOf(ConnectionMethod.BLUETOOTH, ConnectionMethod.WIFI_DIRECT)) {
            val entry = NearbyMethodCatalog.current(method)
            assertEquals(NearbyMethodCatalog.Availability.PLANNED, entry.availability)
            assertTrue(entry.futureDiscovery.contains("not implemented"))
            assertTrue(entry.requirements.contains("permission"))
            assertSame(NearbyMethodCatalog.Selection.NotAvailable,
                NearbyMethodCatalog.select(method, debuggable = true))
        }
    }
}
