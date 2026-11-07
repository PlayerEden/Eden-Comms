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
        val policy = DebugWifiDirectPreflightPolicy
        assertEquals(policy.Support.NOT_ADVERTISED,
            policy.assess(policy.Observation(36, false, true)).support)
        assertEquals(policy.Support.MANAGER_MISSING,
            policy.assess(policy.Observation(36, true, false)).support)
        val capable = policy.assess(policy.Observation(36, true, true))
        assertEquals(policy.Support.POTENTIALLY_SUPPORTED, capable.support)
        assertTrue(capable.description.contains("does not prove"))
        assertFalse(capable.discoveryAllowed)
        assertFalse(policy.assess(policy.Observation(26, false, false)).discoveryAllowed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun olderThanMinimumAppApiIsRejected() {
        DebugWifiDirectPreflightPolicy.assess(
            DebugWifiDirectPreflightPolicy.Observation(25, true, true)
        )
    }
}
