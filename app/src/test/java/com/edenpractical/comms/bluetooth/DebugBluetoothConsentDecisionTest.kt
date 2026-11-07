package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.model.ReleaseCapabilities
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBluetoothConsentDecisionTest {
    @Test fun approvalRequiresAnExplicitTapAndCannotBeRepeated() {
        val prompt = DebugBluetoothConsentDecision()
        assertTrue(prompt.approve())
        assertFalse(prompt.approve())
        assertFalse(prompt.decline())
        assertEquals(
            DebugBluetoothConsentDecision.Decision.APPROVE,
            prompt.await(100)
        )
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun explicitDeclineIsNotApproval() {
        val prompt = DebugBluetoothConsentDecision()
        assertTrue(prompt.decline())
        assertEquals(
            DebugBluetoothConsentDecision.Decision.DECLINE,
            prompt.await(100)
        )
        assertFalse(prompt.approve())
    }

    @Test fun noUserResponseTimesOutWithoutAutomaticApproval() {
        val prompt = DebugBluetoothConsentDecision()
        assertEquals(
            DebugBluetoothConsentDecision.Decision.TIMEOUT,
            prompt.await(1)
        )
        prompt.close()
        assertFalse(prompt.approve())
    }

    @Test fun cancellationOverridesAnEarlierTapAndWakesWaiter() {
        val prompt = DebugBluetoothConsentDecision()
        assertTrue(prompt.approve())
        prompt.close()
        assertEquals(
            DebugBluetoothConsentDecision.Decision.CANCELED,
            prompt.await(100)
        )
        assertFalse(prompt.approve())
    }

    @Test fun backgroundClosureUnblocksWaitingWorker() {
        val prompt = DebugBluetoothConsentDecision()
        val entered = CountDownLatch(1)
        val ended = CountDownLatch(1)
        var result: DebugBluetoothConsentDecision.Decision? = null
        val worker = Thread {
            entered.countDown()
            result = prompt.await(5_000)
            ended.countDown()
        }
        worker.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        prompt.close()
        assertTrue(ended.await(2, TimeUnit.SECONDS))
        worker.join(2_000)
        assertEquals(DebugBluetoothConsentDecision.Decision.CANCELED, result)
    }

    @Test fun unboundedConsentWaitIsProhibited() {
        val prompt = DebugBluetoothConsentDecision()
        assertThrows(IllegalArgumentException::class.java) {
            prompt.await(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            prompt.await(60_000)
        }
    }
}
