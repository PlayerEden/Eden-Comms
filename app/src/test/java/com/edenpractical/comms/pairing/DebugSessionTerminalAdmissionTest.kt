package com.edenpractical.comms.pairing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5-D pure UI event admission tests. No Android event loop, actual TLS, QR,
 * real disconnect timing, delivery or production authorization simulated.
 */
class DebugSessionTerminalAdmissionTest {
    private val admission = DebugSessionEventAdmission
    private val waiting = DebugSessionTranscript.fresh()
    private val active = waiting.ready()
    private val finishedBeforeReady = waiting.finish()
    private val finishedAfterReady = active.finish()

    @Test fun terminalEventsAcceptedOnlyInMatchingLivePhases() {
        for (stage in listOf("connecting", "showReply", "approval")) {
            assertTrue(stage, admission.canAcceptTerminal(12, 12, stage, waiting))
            assertFalse(stage, admission.canAcceptTerminal(12, 12, stage, active))
        }
        assertTrue(admission.canAcceptTerminal(12, 12, "demo", active))
        assertFalse(admission.canAcceptTerminal(12, 12, "demo", waiting))
        assertFalse(admission.canAcceptTerminal(12, 12, "connecting", finishedBeforeReady))
        assertFalse(admission.canAcceptTerminal(12, 12, "demo", finishedAfterReady))
    }

    @Test fun staleAndAlreadyFinishedCallbacksNeverOverwriteResultOrNewAttempt() {
        for (stage in listOf(
            "checking", "choose", "showHost", "scanHost", "identityError", "result"
        )) {
            assertFalse(stage, admission.canAcceptTerminal(12, 12, stage, waiting))
            assertFalse(stage, admission.canAcceptTerminal(12, 12, stage, active))
        }
        for (stage in listOf("connecting", "showReply", "approval", "demo")) {
            assertFalse(stage, admission.canAcceptTerminal(11, 12, stage, waiting))
            assertFalse(stage, admission.canAcceptTerminal(11, 12, stage, active))
        }
        assertFalse(admission.canAcceptTerminal(12, 12, "result", finishedAfterReady))
        assertFalse(admission.canAcceptTerminal(12, 12, "result", finishedBeforeReady))
    }

    @Test fun applyingFirstTerminalInvalidatesOldGenerationBeforeNextRound() {
        var generation = 27
        var stage = "demo"
        var transcript = active
        assertTrue(admission.canAcceptTerminal(27, generation, stage, transcript))

        // This mirrors stopTest(): increment attempt, finish transcript,
        // and present result before the next queued main-thread callback.
        generation++
        transcript = transcript.finish()
        stage = "result"
        assertFalse(admission.canAcceptTerminal(27, generation, stage, transcript))
        assertFalse(admission.canAcceptReady(27, generation, "connecting", waiting))
        assertFalse(admission.canRecord(27, generation, "demo", active))

        stage = "connecting"
        transcript = waiting
        assertFalse(admission.canAcceptTerminal(27, generation, stage, transcript))
        assertTrue(admission.canAcceptTerminal(28, generation, stage, transcript))
    }
}
