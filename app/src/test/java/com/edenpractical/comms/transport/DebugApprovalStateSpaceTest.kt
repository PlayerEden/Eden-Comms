package com.edenpractical.comms.transport

import com.edenpractical.comms.transport.DebugApprovalGate.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3a REVIEW SUPPORT ONLY: explores ordering and loss of debug approval events.
 * These JVM state-machine tests are not a security audit, live TLS test,
 * hardware result, or authority to enable unrestricted messaging.
 */
class DebugApprovalStateSpaceTest {
    private enum class Action { LOCAL_APPROVE, PEER_APPROVE, LOCAL_CONFIRM, PEER_CONFIRM, DECLINE, CLOSE }

    /** Independent spec-style oracle; intentionally kept out of app sources. */
    private class Oracle {
        private var local = false
        private var peer = false
        private var localFinal = false
        private var peerFinal = false
        private var terminal: State? = null

        fun apply(action: Action): State {
            if (action == Action.CLOSE) terminal = State.CLOSED
            else if (terminal == null) when (action) {
                Action.DECLINE -> terminal = State.DECLINED
                Action.LOCAL_APPROVE -> if (local) terminal = State.INVALID else local = true
                Action.PEER_APPROVE -> if (peer) terminal = State.INVALID else peer = true
                Action.LOCAL_CONFIRM -> {
                    if (!local || !peer || localFinal) terminal = State.INVALID
                    else localFinal = true
                }
                Action.PEER_CONFIRM -> {
                    if (!peer || peerFinal) terminal = State.INVALID
                    else peerFinal = true
                }
                Action.CLOSE -> error("Handled above")
            }
            return state()
        }

        fun state(): State {
            terminal?.let { return it }
            if (local && peer) return when {
                localFinal && peerFinal -> State.CONFIRMED
                localFinal -> State.LOCAL_CONFIRMED
                peerFinal -> State.PEER_CONFIRMED
                else -> State.BOTH_OBSERVED
            }
            return when {
                local -> State.LOCAL_ONLY
                peer -> State.PEER_ONLY
                else -> State.WAITING
            }
        }

        fun mayConfirm(): Boolean = terminal == null && local && peer && !localFinal
    }

    @Test fun allControlSequencesThroughSixEventsFailClosedUnlessFullyConfirmed() {
        val actions = Action.values()
        var checked = 0
        fun inspect(trace: List<Action>) {
            val gate = DebugApprovalGate()
            val oracle = Oracle()
            assertEquals(State.WAITING, gate.snapshot())
            for (action in trace) {
                val actual = when (action) {
                    Action.LOCAL_APPROVE -> gate.localWritten()
                    Action.PEER_APPROVE -> gate.peerObserved()
                    Action.LOCAL_CONFIRM -> gate.localConfirmationWritten()
                    Action.PEER_CONFIRM -> gate.peerConfirmationObserved()
                    Action.DECLINE -> gate.decline()
                    Action.CLOSE -> gate.close()
                }
                val expected = oracle.apply(action)
                val context = trace.joinToString(" > ")
                assertEquals("event result: $context", expected, actual)
                assertEquals("snapshot: $context", expected, gate.snapshot())
                assertEquals("preset gate: $context", expected == State.CONFIRMED, gate.maySendPreset())
                assertEquals("confirm gate: $context", oracle.mayConfirm(), gate.mayWriteConfirmation())
            }
            checked++
        }
        fun enumerate(trace: List<Action>) {
            inspect(trace)
            if (trace.size == 6) return
            for (action in actions) enumerate(trace + action)
        }
        enumerate(emptyList())
        assertEquals(55_987, checked) // 6^0 + ... + 6^6; deterministic, no skipped paths.
    }

    private enum class WireStep {
        A_APPROVES, B_APPROVES, A_APPROVAL_DELIVERED, B_APPROVAL_DELIVERED,
        A_CONFIRMS, B_CONFIRMS, A_CONFIRM_DELIVERED, B_CONFIRM_DELIVERED
    }

    /**
     * Explore legal two-endpoint event interleavings. Per-direction ordered
     * delivery is modeled, but this is not Android transport or TLS testing.
     */
    @Test fun twoEndpointsNeverUnlockPresetsUntilTheirOwnFourFactsAreRecorded() {
        var completed = 0
        var prefixes = 0
        val all = WireStep.values().toList()

        fun replay(trace: List<WireStep>): Pair<DebugApprovalGate, DebugApprovalGate> {
            val a = DebugApprovalGate()
            val b = DebugApprovalGate()
            var aTap = false
            var bTap = false
            var aSawB = false
            var bSawA = false
            var aFinalWritten = false
            var bFinalWritten = false
            var aSawFinal = false
            var bSawFinal = false

            for (step in trace) {
                when (step) {
                    WireStep.A_APPROVES -> { a.localWritten(); aTap = true }
                    WireStep.B_APPROVES -> { b.localWritten(); bTap = true }
                    WireStep.A_APPROVAL_DELIVERED -> { b.peerObserved(); bSawA = true }
                    WireStep.B_APPROVAL_DELIVERED -> { a.peerObserved(); aSawB = true }
                    WireStep.A_CONFIRMS -> { assertTrue(a.mayWriteConfirmation()); a.localConfirmationWritten(); aFinalWritten = true }
                    WireStep.B_CONFIRMS -> { assertTrue(b.mayWriteConfirmation()); b.localConfirmationWritten(); bFinalWritten = true }
                    WireStep.A_CONFIRM_DELIVERED -> { b.peerConfirmationObserved(); bSawFinal = true }
                    WireStep.B_CONFIRM_DELIVERED -> { a.peerConfirmationObserved(); aSawFinal = true }
                }
                assertEquals("A early unlock: $trace", aTap && aSawB && aFinalWritten && aSawFinal,
                    a.maySendPreset())
                assertEquals("B early unlock: $trace", bTap && bSawA && bFinalWritten && bSawFinal,
                    b.maySendPreset())
            }
            return a to b
        }

        fun enumerate(trace: List<WireStep>, visited: Set<WireStep>) {
            val (a, b) = replay(trace)
            prefixes++
            if (visited.size == all.size) {
                completed++
                assertTrue(a.maySendPreset())
                assertTrue(b.maySendPreset())
                return
            }
            for (step in all) {
                if (step in visited) continue
                val allowed = when (step) {
                    WireStep.A_APPROVES, WireStep.B_APPROVES -> true
                    WireStep.A_APPROVAL_DELIVERED -> WireStep.A_APPROVES in visited
                    WireStep.B_APPROVAL_DELIVERED -> WireStep.B_APPROVES in visited
                    WireStep.A_CONFIRMS -> a.mayWriteConfirmation()
                    WireStep.B_CONFIRMS -> b.mayWriteConfirmation()
                    WireStep.A_CONFIRM_DELIVERED -> WireStep.A_CONFIRMS in visited &&
                        WireStep.A_APPROVAL_DELIVERED in visited
                    WireStep.B_CONFIRM_DELIVERED -> WireStep.B_CONFIRMS in visited &&
                        WireStep.B_APPROVAL_DELIVERED in visited
                }
                if (allowed) enumerate(trace + step, visited + step)
            }
        }
        enumerate(emptyList(), emptySet())
        assertTrue("No valid complete schedules explored", completed > 0)
        assertTrue("No intermediate prefixes explored", prefixes > completed)
    }

    @Test fun interruptedFinalDeliveryCanLeaveOneSideConfirmedAndTheOtherNot() {
        val a = DebugApprovalGate()
        val b = DebugApprovalGate()
        a.localWritten(); b.peerObserved() // A's APPROVE reaches B.
        b.localWritten(); a.peerObserved() // B's APPROVE reaches A.
        a.localConfirmationWritten(); b.peerConfirmationObserved() // A's CONFIRM reaches B.
        b.localConfirmationWritten() // B's CONFIRM is written but never reaches A.

        assertFalse("A cannot invent B's missing final CONFIRM", a.maySendPreset())
        assertTrue("B has four local debug facts, not global agreement", b.maySendPreset())
        a.close(); b.close()
        assertFalse(a.maySendPreset())
        assertFalse(b.maySendPreset())
    }
}
