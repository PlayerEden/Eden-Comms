package com.edenpractical.comms.wifidirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWifiDirectReadinessPolicyTest {
    private fun check(
        api: Int = 36,
        feature: Boolean = true,
        service: Boolean = true,
        wifi: Boolean? = true,
        location: Boolean? = true,
        permission: Boolean = false
    ) = DebugWifiDirectReadinessPolicy.evaluate(
        DebugWifiDirectReadinessPolicy.Observation(
            api, feature, service, wifi, location, permission)
    )

    @Test fun unknownOffAndUnsupportedCannotPassReadiness() {
        assertEquals(DebugWifiDirectReadinessPolicy.State.NOT_SUPPORTED,
            check(feature = false).state)
        assertEquals(DebugWifiDirectReadinessPolicy.State.SERVICE_MISSING,
            check(service = false).state)
        assertEquals(DebugWifiDirectReadinessPolicy.State.WIFI_UNKNOWN,
            check(wifi = null).state)
        assertEquals(DebugWifiDirectReadinessPolicy.State.WIFI_OFF,
            check(wifi = false).state)
        assertEquals(DebugWifiDirectReadinessPolicy.State.LOCATION_UNKNOWN,
            check(location = null).state)
        assertEquals(DebugWifiDirectReadinessPolicy.State.LOCATION_OFF,
            check(location = false).state)
        assertEquals(DebugWifiDirectReadinessPolicy.State.PERMISSION_MISSING,
            check().state)
        assertEquals(DebugWifiDirectReadinessPolicy.State.PREREQUISITES_OBSERVED,
            check(permission = true).state)
    }

    @Test fun evenGreenChecksAreNotDiscoveryAuthority() {
        for (api in 26..37) {
            assertFalse(check(api = api, permission = true).discoveryAllowed)
            assertFalse(check(api = api).discoveryAllowed)
            assertTrue(check(api = api).permissionRequestAllowed)
            assertFalse(check(api = api, permission = true).permissionRequestAllowed)
            assertFalse(check(api = api, feature = false).permissionRequestAllowed)
            assertFalse(check(api = api, service = false).permissionRequestAllowed)
            assertEquals(DebugWifiDirectReadinessPolicy.State.WIFI_OFF,
                check(api = api, wifi = false, permission = true).state)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedMinimumApiCannotPass() { check(api = 25) }
}
