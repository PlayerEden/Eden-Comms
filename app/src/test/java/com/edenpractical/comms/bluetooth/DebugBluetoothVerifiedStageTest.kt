package com.edenpractical.comms.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBluetoothVerifiedStageTest {
    @Test fun strictlyOrderedFixedNonSensitiveProgress() {
        val checkpoints = DebugBluetoothVerifiedStage.entries
        assertEquals(5, checkpoints.size)
        assertEquals((1..5).toList(), checkpoints.map { it.step })
        assertEquals(DebugBluetoothVerifiedStage.TLS_PIN, checkpoints.first())
        assertEquals(DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS, checkpoints.last())
        for (stage in checkpoints) {
            val display = stage.display()
            assertTrue(display.startsWith("Verified test step ${stage.step}/5:"))
            for (forbidden in listOf("MAC:", "IP:", "fingerprint:", "BEGIN PRIVATE KEY",
                    "recipient", "message body", "fully secure", "chat available")) {
                assertFalse(display.contains(forbidden, ignoreCase = true))
            }
        }
    }
}
