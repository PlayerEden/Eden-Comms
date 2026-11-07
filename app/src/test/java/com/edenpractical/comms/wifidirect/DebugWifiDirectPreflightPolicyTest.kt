package com.edenpractical.comms.wifidirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWifiDirectPreflightPolicyTest {
    @Test fun permissionGuidanceChangesAtAndroid13NotAtAndroid12() {
        for (api in 26..32) {
            val status = DebugWifiDirectPreflightPolicy.assess(
                DebugWifiDirectPreflightPolicy.Observation(api, true, true)
            )
            assertEquals(DebugWifiDirectPreflightPolicy.PermissionFamily.ACCESS_FINE_LOCATION,
                status.permissionFamily)
            assertFalse(status.discoveryAllowed)
        }
        for (api in 33..37) {
            val status = DebugWifiDirectPreflightPolicy.assess(
                DebugWifiDirectPreflightPolicy.Observation(api, true, true)
            )
            assertEquals(DebugWifiDirectPreflightPolicy.PermissionFamily.NEARBY_WIFI_DEVICES,
                status.permissionFamily)
            assertFalse(status.discoveryAllowed)
        }
    }

    @Test fun capabilityIsNotAuthenticatedPeerOrPermissionToDiscover() {
        assertEquals(DebugWifiDirectPreflightPolicy.Support.NOT_ADVERTISED,
            DebugWifiDirectPreflightPolicy.assess(DebugWifiDirectPreflightPolicy.Observation(36, false, true)).support)
        assertEquals(DebugWifiDirectPreflightPolicy.Support.MANAGER_MISSING,
            DebugWifiDirectPreflightPolicy.assess(DebugWifiDirectPreflightPolicy.Observation(36, true, false)).support)
        val capable = DebugWifiDirectPreflightPolicy.assess(DebugWifiDirectPreflightPolicy.Observation(36, true, true))
        assertEquals(DebugWifiDirectPreflightPolicy.Support.POTENTIALLY_SUPPORTED, capable.support)
        assertTrue(capable.description.contains("does not prove"))
        assertFalse(capable.discoveryAllowed)
        assertFalse(DebugWifiDirectPreflightPolicy.assess(DebugWifiDirectPreflightPolicy.Observation(26, false, false)).discoveryAllowed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun olderThanMinimumAppApiIsRejected() {
        DebugWifiDirectPreflightPolicy.assess(
            DebugWifiDirectPreflightPolicy.Observation(25, true, true)
        )
    }
}
