package com.edenpractical.comms.transport

import com.edenpractical.comms.pairing.DebugSessionAudioOwner
import com.edenpractical.comms.pairing.DebugTapVoiceSendGate
import com.edenpractical.comms.protocol.DebugLanVoiceAssembler
import com.edenpractical.comms.protocol.DebugLanVoiceFloorCodec
import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.protocol.TestPacketException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * F4-M: deterministic, virtual two-phone LAN PTT regression.
 *
 * The fixture uses the *actual* bounded TestPacketCodec frame write/read,
 * floor arbiter, live PCM queue, voice wire codec and strict assembler.
 * Delays and Wi-Fi failures are injected by the test in a controlled order.
 * There is NO TLS socket, Android microphone/speaker or physical handset:
 * these tests cannot prove real-world crypto, latency or audibility.
 */
class DebugLanVirtualTwoPhoneRegressionTest {
    private val floorKind = DebugLanVoiceFloorCodec.Kind
    private val voiceKind = DebugLanVoiceWireCodec.Kind

    private class Phone(val isHost: Boolean) {
        val floor = DebugLanVoiceFloor(isHost)
        val audio = DebugLanVoiceAssembler()
        var closed = false
            private set

        fun incomingFloor(frame: DebugLanVoiceFloorCodec.Frame, atMs: Long)
            : DebugLanVoiceFloor.Incoming {
            if (closed) throw TestPacketException()
            return floor.incoming(frame, atMs)
        }

        fun incomingVoice(frame: DebugLanVoiceWireCodec.Frame): ByteArray? {
            if (closed) throw TestPacketException()
            if (frame.kind == DebugLanVoiceWireCodec.Kind.START) floor.remoteAudioStart()
            val result = audio.accept(frame)
            if (frame.kind == DebugLanVoiceWireCodec.Kind.END) floor.remoteAudioEnd()
            return result
        }

        fun disconnect() {
            closed = true
            floor.close()
            audio.clear()
        }
    }

    /** Real wire envelope and inner codec, but an in-memory, NOT TLS, link. */
    private fun roundTrip(packet: TestPacketCodec.Packet): TestPacketCodec.Packet {
        val output = ByteArrayOutputStream()
        TestPacketCodec.write(output, packet)
        val bytes = output.toByteArray()
        return try {
            TestPacketCodec.read(ByteArrayInputStream(bytes))
        } finally {
            bytes.fill(0)
        }
    }

    private fun deliverFloor(
        recipient: Phone,
        frame: DebugLanVoiceFloorCodec.Frame,
        atMs: Long
    ): DebugLanVoiceFloor.Incoming {
        val received = roundTrip(TestPacketCodec.debugVoiceFloor(frame))
        assertEquals(TestPacketCodec.Type.DEBUG_VOICE_FLOOR, received.type)
        return recipient.incomingFloor(received.floor!!, atMs)
    }

    private fun deliverVoice(
        recipient: Phone,
        frame: DebugLanVoiceWireCodec.Frame
    ): ByteArray? {
        try {
            val received = roundTrip(TestPacketCodec.debugVoice(frame))
            val incoming = received.voice!!
            return try {
                recipient.incomingVoice(incoming)
            } finally {
                incoming.clear()
            }
        } finally {
            frame.clear()
        }
    }

    private fun pcm(value: Int): ByteArray =
        ByteArray(DebugLanVoiceWireCodec.PCM_BYTES) { value.toByte() }

    @Test fun busyHostRapidCanceledRepliesThenFullVoiceTurnaround() {
        val host = Phone(true)
        val joiner = Phone(false)
        assertEquals(DebugLanVoiceFloor.Ask.GRANTED,
            host.floor.requestLocal(100).status)

        // Another phone quickly holds/releases Talk before it has observed
        // the host's START. Host denies the requests while keeping its floor.
        val delayedDenials = mutableListOf<DebugLanVoiceFloorCodec.Frame>()
        repeat(5) { index ->
            val request = joiner.floor.requestLocal(110L + index * 5).packet!!
            assertFalse(joiner.floor.maySendAudio())
            val denial = deliverFloor(host, request, 111L + index * 5).outbound!!
            assertEquals(floorKind.DENY, denial.kind)
            delayedDenials.add(denial)
            val cancel = joiner.floor.cancelPending()!!
            assertEquals(floorKind.CANCEL, cancel.kind)
            deliverFloor(host, cancel, 112L + index * 5)
        }
        // Network delay/reordering of replies must not re-enable an old mic.
        delayedDenials.reversed().forEachIndexed { i, denial ->
            assertEquals(DebugLanVoiceFloor.Signal.NONE,
                deliverFloor(joiner, denial, 145L + i).signal)
            assertFalse(joiner.floor.maySendAudio())
        }
        assertThrows(TestPacketException::class.java) {
            deliverFloor(joiner, delayedDenials.first(), 155)
        }

        // Host's 20ms PCM reaches the peer BEFORE capture is finished.
        val hostQueue = DebugLanLiveVoiceQueue()
        assertTrue(hostQueue.offer(pcm(0x2a)))
        assertNull(deliverVoice(joiner, DebugLanVoiceWireCodec.start(1)))
        val first = hostQueue.poll()!!
        assertFalse(hostQueue.isFinishedAndEmpty())
        assertNull(deliverVoice(joiner, DebugLanVoiceWireCodec.data(1, 0, first)))
        first.fill(0)
        hostQueue.finish()
        val receivedHost = deliverVoice(joiner, DebugLanVoiceWireCodec.end(1, 1))!!
        assertEquals(DebugLanVoiceWireCodec.PCM_BYTES, receivedHost.size)
        assertEquals(0x2a.toByte(), receivedHost[0])
        receivedHost.fill(0)
        hostQueue.clear()
        assertNull(host.floor.releaseLocal())

        // The same two peers turn the channel around to the joining phone.
        val requested = joiner.floor.requestLocal(200).packet!!
        val granted = deliverFloor(host, requested, 201).outbound!!
        assertEquals(floorKind.GRANT, granted.kind)
        assertFalse(joiner.floor.maySendAudio()) // A REQUEST is not a GRANT.
        assertEquals(DebugLanVoiceFloor.Signal.GRANTED,
            deliverFloor(joiner, granted, 202).signal)
        assertTrue(joiner.floor.maySendAudio())

        val joinerQueue = DebugLanLiveVoiceQueue()
        assertTrue(joinerQueue.offer(pcm(0x37)))
        assertNull(deliverVoice(host, DebugLanVoiceWireCodec.start(1)))
        val next = joinerQueue.poll()!!
        assertNull(deliverVoice(host, DebugLanVoiceWireCodec.data(1, 0, next)))
        next.fill(0)
        joinerQueue.finish()
        val receivedJoiner = deliverVoice(host, DebugLanVoiceWireCodec.end(1, 1))!!
        assertEquals(0x37.toByte(), receivedJoiner[0])
        receivedJoiner.fill(0)
        joinerQueue.clear()
        deliverFloor(host, joiner.floor.releaseLocal()!!, 204)

        // The presenter is free to take the next turn, with no stale floor.
        assertEquals(DebugLanVoiceFloor.Ask.GRANTED,
            host.floor.requestLocal(205).status)
        host.disconnect()
        joiner.disconnect()
        assertFalse(host.floor.maySendAudio())
        assertFalse(joiner.floor.maySendAudio())
    }

    @Test fun cancelOldGrantsThenFreshGrantCannotStartCanceledTapMic() {
        val host = Phone(true)
        val joiner = Phone(false)
        val firstRequest = joiner.floor.requestLocal(10).packet!!
        val oldGrant = deliverFloor(host, firstRequest, 11).outbound!!
        val firstCancelled = joiner.floor.cancelPending()!!
        deliverFloor(host, firstCancelled, 12)

        val secondRequest = joiner.floor.requestLocal(13).packet!!
        val secondGrant = deliverFloor(host, secondRequest, 14).outbound!!
        val secondCancelled = joiner.floor.cancelPending()!!
        deliverFloor(host, secondCancelled, 15)

        val thirdRequest = joiner.floor.requestLocal(16).packet!!
        val currentGrant = deliverFloor(host, thirdRequest, 17).outbound!!
        val canceledMic = DebugTapVoiceSendGate()
        assertTrue(canceledMic.cancel())
        assertNull(canceledMic.submitIfActive {
            fail("The canceled AudioRecord callback must never queue PCM")
        })
        assertEquals(DebugLanVoiceFloor.Signal.NONE,
            deliverFloor(joiner, oldGrant, 18).signal)
        assertEquals(DebugLanVoiceFloor.Signal.NONE,
            deliverFloor(joiner, secondGrant, 19).signal)
        assertFalse(joiner.floor.maySendAudio())
        assertEquals(DebugLanVoiceFloor.Signal.GRANTED,
            deliverFloor(joiner, currentGrant, 20).signal)
        assertTrue(joiner.floor.maySendAudio())

        // A canceled, unstarted mic releases its exact reserved floor and
        // produces no voice START/DATA/END on the virtual wire.
        val unsent = DebugLanLiveVoiceQueue()
        assertTrue(unsent.offer(pcm(0x19)))
        unsent.cancel()
        assertNull(unsent.poll())
        assertTrue(unsent.isFinishedAndEmpty())
        assertFalse(unsent.offer(pcm(0x22)))
        deliverFloor(host, joiner.floor.releaseLocal()!!, 21)
        assertEquals(DebugLanVoiceFloor.Ask.GRANTED,
            host.floor.requestLocal(22).status)
        host.disconnect()
        joiner.disconnect()
        assertThrows(TestPacketException::class.java) {
            deliverFloor(joiner, oldGrant, 23)
        }
    }

    @Test fun brokenLinkDuringAudioAndFloorWaitKillsOnlyOldSession() {
        val host = Phone(true)
        val joiner = Phone(false)
        val oldOwner = Any()
        val freshOwner = Any()
        val audioOwnership = DebugSessionAudioOwner()
        var micStops = 0
        audioOwnership.activate(oldOwner)

        // The host is transmitting while a second phone has a pending
        // request (its radio START has not reached the joiner yet).
        assertEquals(DebugLanVoiceFloor.Ask.GRANTED,
            host.floor.requestLocal(100).status)
        val pending = joiner.floor.requestLocal(101).packet!!
        val delayedDeny = deliverFloor(host, pending, 102).outbound!!
        assertNull(deliverVoice(joiner, DebugLanVoiceWireCodec.start(5)))
        val outgoing = DebugLanLiveVoiceQueue()
        assertTrue(outgoing.offer(pcm(0x4d)))
        assertNull(deliverVoice(joiner, DebugLanVoiceWireCodec.data(
            5, 0, outgoing.poll()!!.also { it.fill(0) }
        )))
        // Simulate abrupt Wi-Fi loss: the sender discards untransmitted PCM,
        // both independent floor leases close, and the receiver drops a
        // partial turn instead of fabricating END or playing a full clip.
        outgoing.cancel()
        host.disconnect()
        joiner.disconnect()
        assertFalse(joiner.floor.maySendAudio())
        assertFalse(host.floor.maySendAudio())
        assertThrows(TestPacketException::class.java) {
            deliverFloor(joiner, delayedDeny, 103)
        }
        assertThrows(TestPacketException::class.java) {
            deliverVoice(joiner, DebugLanVoiceWireCodec.end(5, 1))
        }
        // A newer QR/TLS session owns the mic. The old close callback is
        // forbidden from stopping its hardware after new-session activation.
        audioOwnership.activate(freshOwner)
        assertFalse(audioOwnership.stopIfCurrent(oldOwner) { micStops++ })
        assertEquals(0, micStops)
        assertTrue(audioOwnership.stopIfCurrent(freshOwner) { micStops++ })
        assertEquals(1, micStops)

        val newHost = Phone(true)
        val newJoiner = Phone(false)
        val request = newJoiner.floor.requestLocal(200).packet!!
        val grant = deliverFloor(newHost, request, 201).outbound!!
        assertEquals(DebugLanVoiceFloor.Signal.GRANTED,
            deliverFloor(newJoiner, grant, 202).signal)
        newHost.disconnect()
        newJoiner.disconnect()
    }

    @Test fun malformedVoiceSequenceFailsClosedAndNeverRecoversOldTurn() {
        val host = Phone(true)
        val joiner = Phone(false)
        val request = joiner.floor.requestLocal(10).packet!!
        val grant = deliverFloor(host, request, 11).outbound!!
        assertEquals(DebugLanVoiceFloor.Signal.GRANTED,
            deliverFloor(joiner, grant, 12).signal)
        assertNull(deliverVoice(host, DebugLanVoiceWireCodec.start(9)))
        // The strict assembler must reject missing sequence 0. The actual
        // LanDemoSession catches this protocol error and closes the TLS link.
        assertThrows(TestPacketException::class.java) {
            deliverVoice(host, DebugLanVoiceWireCodec.data(9, 1, pcm(3)))
        }
        host.disconnect()
        joiner.disconnect()
        assertThrows(TestPacketException::class.java) {
            deliverVoice(host, DebugLanVoiceWireCodec.data(9, 0, pcm(3)))
        }
        assertFalse(joiner.floor.maySendAudio())
    }

    @Test fun missingGrantAndMissingReleaseRequireDisconnectNotFloorReassignment() {
        val joiner = Phone(false)
        val first = joiner.floor.requestLocal(100).packet!!
        assertEquals(floorKind.REQUEST, first.kind)
        assertFalse(joiner.floor.maySendAudio())
        assertFalse(joiner.floor.timedOut(100 + DebugLanVoiceFloor.PENDING_TIMEOUT_MS))
        assertTrue(joiner.floor.timedOut(101 + DebugLanVoiceFloor.PENDING_TIMEOUT_MS))
        joiner.disconnect()
        assertFalse(joiner.floor.maySendAudio())

        val host = Phone(true)
        host.incomingFloor(DebugLanVoiceFloorCodec.Frame(floorKind.REQUEST, 7), 300)
        assertTrue(host.floor.timedOut(301 + DebugLanVoiceFloor.LEASE_TIMEOUT_MS))
        host.disconnect()
        assertThrows(TestPacketException::class.java) {
            deliverFloor(host,
                DebugLanVoiceFloorCodec.Frame(floorKind.RELEASE, 7), 10_000)
        }
    }
}
