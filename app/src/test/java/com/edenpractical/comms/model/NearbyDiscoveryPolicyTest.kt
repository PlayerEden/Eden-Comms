package com.edenpractical.comms.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDiscoveryPolicyTest {
    @Test fun everyConnectionMethodHasDiscoveryDisabledForNow() {
        for (method in ConnectionMethod.values()) {
            assertFalse(NearbyDiscoveryPolicy.availableNow(method))
        }
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
        assertTrue(NearbyDiscoveryPolicy.currentStatus.contains("OFF"))
        assertTrue(NearbyDiscoveryPolicy.currentStatus.contains("NOT IMPLEMENTED"))
    }

    @Test fun personConsentAndOsPermissionAreExplicitlySeparate() {
        assertTrue(NearbyDiscoveryPolicy.optInStep.contains("explicitly start"))
        assertTrue(NearbyDiscoveryPolicy.permissionStep.contains("before requesting"))
        assertTrue(NearbyDiscoveryPolicy.permissionStep.contains("Declining"))
        assertTrue(NearbyDiscoveryPolicy.foregroundStep.contains("screen is open"))
        assertTrue(NearbyDiscoveryPolicy.foregroundStep.contains("Stop control"))
        assertTrue(NearbyDiscoveryPolicy.trustStep.contains("never make it a trusted contact"))
        assertTrue(NearbyDiscoveryPolicy.trustStep.contains("separately verify and approve"))
        assertTrue(NearbyDiscoveryPolicy.privacyStep.contains("No background scanning"))
        assertTrue(NearbyDiscoveryPolicy.privacyStep.contains("saved scan results"))
    }
}
