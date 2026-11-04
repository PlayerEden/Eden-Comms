package com.edenpractical.comms.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReleaseCapabilitiesTest {
    @Test fun foundationDoesNotPretendToBeEncryptedMessaging() {
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun allThreeConnectionMethodsAreNamedClearly() {
        assertEquals(3, ConnectionMethod.entries.size)
        assertEquals("Local Wi-Fi", ConnectionMethod.LAN.displayName)
        assertEquals("Bluetooth", ConnectionMethod.BLUETOOTH.displayName)
        assertEquals("Wi-Fi Direct", ConnectionMethod.WIFI_DIRECT.displayName)
    }
}
