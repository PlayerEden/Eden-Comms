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

    @Test fun lateAbandonedGrantDoesNotCorruptANewerPendingRequest() {
        val host = DebugLanVoiceFloor(true)
        val joiner = DebugLanVoiceFloor(false)
        val first = joiner.requestLocal(10).packet!!
        val oldGrant = host.incoming(first, 11).outbound!!
        val cancel = joiner.cancelPending()!!
        host.incoming(cancel, 12)
        val second = joiner.requestLocal(13).packet!!
        val newGrant = host.incoming(second, 14).outbound!!
        assertEquals(DebugLanVoiceFloor.Signal.NONE,
            joiner.incoming(oldGrant, 15).signal)
        assertFalse(joiner.maySendAudio())
        assertEquals(DebugLanVoiceFloor.Signal.GRANTED,
            joiner.incoming(newGrant, 16).signal)
        assertTrue(joiner.maySendAudio())
    }

    @Test fun hostRejectsTwoVoiceTurnsOnOneJoinerGrant() {
        val host = DebugLanVoiceFloor(true)
        host.incoming(packet(DebugLanVoiceFloorCodec.Kind.REQUEST, 1), 10)
        host.remoteAudioStart()
        host.remoteAudioEnd()
        assertThrows(TestPacketException::class.java) { host.remoteAudioStart() }
        host.incoming(packet(DebugLanVoiceFloorCodec.Kind.RELEASE, 1), 20)
        host.incoming(packet(DebugLanVoiceFloorCodec.Kind.REQUEST, 2), 21)
        host.remoteAudioStart()
        host.remoteAudioEnd()
    }

    @Test fun multipleRapidCancelledRequestsIgnoreEachLateGrantExactlyOnce() {
        val host = DebugLanVoiceFloor(true)
        val joiner = DebugLanVoiceFloor(false)
        val oldGrants = mutableListOf<DebugLanVoiceFloorCodec.Frame>()
        // The host may have already sent several GRANTs while the joiner's
        // user rapidly taps and releases Talk, before replies reach the UI.
        for (time in 1L..3L) {
            val request = joiner.requestLocal(time * 10).packet!!
            oldGrants.add(host.incoming(request, time * 10 + 1).outbound!!)
            val cancel = joiner.cancelPending()!!
            host.incoming(cancel, time * 10 + 2)
        }
        val current = joiner.requestLocal(100).packet!!
        val currentGrant = host.incoming(current, 101).outbound!!

        oldGrants.forEach { grant ->
            assertEquals(DebugLanVoiceFloor.Signal.NONE,
                joiner.incoming(grant, 105).signal)
            assertFalse(joiner.maySendAudio())
        }
        assertEquals(DebugLanVoiceFloor.Signal.GRANTED,
            joiner.incoming(currentGrant, 106).signal)
        assertTrue(joiner.maySendAudio())
        // The canceled response is no longer whitelisted after one consume:
        // a duplicate wire control is still an invalid/replayed protocol event.
        assertThrows(TestPacketException::class.java) {
            joiner.incoming(oldGrants.first(), 107)
        }
    }

    @Test fun cancelledBusyRequestsIgnoreLateDenialsWithoutReservingFloor() {
        val host = DebugLanVoiceFloor(true)
        val joiner = DebugLanVoiceFloor(false)
        assertEquals(DebugLanVoiceFloor.Ask.GRANTED, host.requestLocal(1).status)
        val denials = mutableListOf<DebugLanVoiceFloorCodec.Frame>()
        repeat(4) { i ->
            val request = joiner.requestLocal(10L + i).packet!!
            denials.add(host.incoming(request, 20L + i).outbound!!)
            assertEquals(DebugLanVoiceFloorCodec.Kind.DENY, denials.last().kind)
            host.incoming(joiner.cancelPending()!!, 30L + i)
        }
        denials.forEach { denial ->
            assertEquals(DebugLanVoiceFloor.Signal.NONE,
                joiner.incoming(denial, 50).signal)
        }
        assertFalse(joiner.maySendAudio())
        host.releaseLocal()
        val request = joiner.requestLocal(60).packet!!
        val grant = host.incoming(request, 61).outbound!!
        assertEquals(DebugLanVoiceFloor.Signal.GRANTED,
            joiner.incoming(grant, 62).signal)
    }

    @Test fun cancellationHistoryIsBoundedAndTooOldReplyFailsClosed() {
        val joiner = DebugLanVoiceFloor(false)
        val oldest = joiner.requestLocal(10).packet!!
        joiner.cancelPending()
        for (i in 1..DebugLanVoiceFloor.MAX_ABANDONED_REQUESTS) {
            joiner.requestLocal(10L + i)
            joiner.cancelPending()
        }
        // Evicted IDs are not authorized to be silently consumed. This
        // defensive bound limits RAM on an unresponsive session.
        assertThrows(TestPacketException::class.java) {
            joiner.incoming(packet(DebugLanVoiceFloorCodec.Kind.GRANT, oldest.requestId), 99)
        }
        assertEquals(DebugLanVoiceFloor.Signal.NONE,
            joiner.incoming(packet(
                DebugLanVoiceFloorCodec.Kind.DENY,
                oldest.requestId + DebugLanVoiceFloor.MAX_ABANDONED_REQUESTS
            ), 100).signal)
        assertFalse(joiner.maySendAudio())
        joiner.close()
        assertThrows(TestPacketException::class.java) {
            joiner.incoming(packet(
                DebugLanVoiceFloorCodec.Kind.GRANT,
                oldest.requestId + DebugLanVoiceFloor.MAX_ABANDONED_REQUESTS
            ), 101)
        }
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
