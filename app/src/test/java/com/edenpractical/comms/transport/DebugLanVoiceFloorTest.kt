package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.DebugLanVoiceFloorCodec
import com.edenpractical.comms.protocol.TestPacketException
import org.junit.Assert.*
import org.junit.Test

class DebugLanVoiceFloorTest {
    private val wire = DebugLanVoiceFloorCodec
    private fun packet(kind: DebugLanVoiceFloorCodec.Kind, id: Long) = DebugLanVoiceFloorCodec.Frame(kind, id)

    @Test fun joinerCannotTransmitUntilExplicitCorrelatedGrant() {
        val host = DebugLanVoiceFloor(true)
        val joiner = DebugLanVoiceFloor(false)
        val asked = joiner.requestLocal(100)
        assertEquals(DebugLanVoiceFloor.Ask.PENDING, asked.status)
        assertFalse(joiner.maySendAudio())
        val response = host.incoming(asked.packet!!, 130).outbound!!
        assertEquals(DebugLanVoiceFloorCodec.Kind.GRANT, response.kind)
        assertEquals(DebugLanVoiceFloor.Signal.GRANTED,
            joiner.incoming(response, 150).signal)
        assertTrue(joiner.maySendAudio())
        host.remoteAudioStart()
        host.remoteAudioEnd()
        val released = joiner.releaseLocal()
        assertEquals(DebugLanVoiceFloorCodec.Kind.RELEASE, released!!.kind)
        host.incoming(released, 310)
        assertEquals(DebugLanVoiceFloor.Ask.GRANTED, host.requestLocal(400).status)
    }

    @Test fun simultaneousPressGoesToHostAndJoinerIsDenied() {
        val host = DebugLanVoiceFloor(true)
        val joiner = DebugLanVoiceFloor(false)
        val joinRequest = joiner.requestLocal(1).packet!!
        assertEquals(DebugLanVoiceFloor.Ask.GRANTED, host.requestLocal(2).status)
        val deny = host.incoming(joinRequest, 3).outbound!!
        assertEquals(DebugLanVoiceFloorCodec.Kind.DENY, deny.kind)
        assertEquals(DebugLanVoiceFloor.Signal.DENIED, joiner.incoming(deny, 4).signal)
        assertFalse(joiner.maySendAudio())
        assertThrows(TestPacketException::class.java) { host.remoteAudioStart() }
        host.releaseLocal()
        assertEquals(DebugLanVoiceFloor.Ask.PENDING, joiner.requestLocal(5).status)
    }

    @Test fun cancellationBeforeGrantNeverStartsAbandonedMicrophone() {
        val host = DebugLanVoiceFloor(true)
        val joiner = DebugLanVoiceFloor(false)
        val req = joiner.requestLocal(10).packet!!
        val granted = host.incoming(req, 11).outbound!!
        val cancel = joiner.cancelPending()!!
        assertEquals(DebugLanVoiceFloorCodec.Kind.CANCEL, cancel.kind)
        assertEquals(DebugLanVoiceFloor.Signal.NONE, joiner.incoming(granted, 12).signal)
        assertFalse(joiner.maySendAudio())
        host.incoming(cancel, 13)
        assertEquals(DebugLanVoiceFloor.Ask.GRANTED, host.requestLocal(14).status)
    }

    @Test fun replayWrongRoleAndUnapprovedRemotePcmAreRejected() {
        val host = DebugLanVoiceFloor(true)
        val joiner = DebugLanVoiceFloor(false)
        assertThrows(TestPacketException::class.java) {
            host.incoming(packet(DebugLanVoiceFloorCodec.Kind.GRANT, 1), 1)
        }
        assertThrows(TestPacketException::class.java) {
            joiner.incoming(packet(DebugLanVoiceFloorCodec.Kind.REQUEST, 1), 1)
        }
        assertThrows(TestPacketException::class.java) { host.remoteAudioStart() }
        val req = joiner.requestLocal(4).packet!!
        host.incoming(req, 5)
        assertThrows(TestPacketException::class.java) { host.incoming(req, 6) }
        assertThrows(TestPacketException::class.java) {
            host.incoming(packet(DebugLanVoiceFloorCodec.Kind.RELEASE, req.requestId + 1), 7)
        }
    }

    @Test fun releaseBeforePcmEndAndCancelledActiveVoiceFailClosed() {
        val host = DebugLanVoiceFloor(true)
        val request = packet(DebugLanVoiceFloorCodec.Kind.REQUEST, 9)
        host.incoming(request, 50)
        host.remoteAudioStart()
        assertThrows(TestPacketException::class.java) {
            host.incoming(packet(DebugLanVoiceFloorCodec.Kind.RELEASE, 9), 51)
        }
        assertThrows(TestPacketException::class.java) {
            host.incoming(packet(DebugLanVoiceFloorCodec.Kind.CANCEL, 9), 52)
        }
        host.remoteAudioEnd()
        host.incoming(packet(DebugLanVoiceFloorCodec.Kind.RELEASE, 9), 53)
        assertEquals(DebugLanVoiceFloor.Ask.GRANTED, host.requestLocal(54).status)
    }

    @Test fun missingGrantAndNeverReleasedLeaseRequireWholeSessionClose() {
        val joiner = DebugLanVoiceFloor(false)
        joiner.requestLocal(100)
        assertFalse(joiner.timedOut(100 + DebugLanVoiceFloor.PENDING_TIMEOUT_MS))
        assertTrue(joiner.timedOut(100 + DebugLanVoiceFloor.PENDING_TIMEOUT_MS + 1))
        joiner.close()
        assertFalse(joiner.timedOut(30_000))
        assertFalse(joiner.maySendAudio())

        val host = DebugLanVoiceFloor(true)
        host.incoming(packet(DebugLanVoiceFloorCodec.Kind.REQUEST, 1), 200)
        assertTrue(host.timedOut(200 + DebugLanVoiceFloor.LEASE_TIMEOUT_MS + 1))
        host.close()
        assertThrows(TestPacketException::class.java) {
            host.incoming(packet(DebugLanVoiceFloorCodec.Kind.RELEASE, 1), 20_000)
        }
    }
}
