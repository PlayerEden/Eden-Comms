package com.edenpractical.comms.qa

import com.edenpractical.comms.bluetooth.DebugBluetoothApprovedTextChannel
import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import org.junit.Assert.*
import org.junit.Test

class DebugNearbyQaEvidenceTest {
    @Test fun ignoresEventsBeforeActualReadyAndAfterDisconnect() {
        val evidence = DebugNearbyQaEvidence("Bluetooth")
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.Written(1, "ignored"))
        assertFalse(evidence.hasObservation())
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.Ready)
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.Written(2, "counted"))
        evidence.end()
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.Received(3, "late"))
        val report = evidence.summary()
        assertTrue(report.contains("Text written to TLS here: 1"))
        assertTrue(report.contains("Text received here: 0"))
        assertTrue(report.contains("Channel still active locally: no"))
    }

    @Test fun redactsAllTextsAndPcmAndPreservesOnlyNumericCounts() {
        val evidence = DebugNearbyQaEvidence("Wi-Fi Direct • join")
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.Ready)
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.Written(
            4567, "secret test text alpha"
        ))
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.Received(
            4568, "secret test text beta"
        ))
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.Acknowledged(4567))
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.FloorGranted)
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.FloorDenied)
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.VoiceWritten(4))
        val pcm = "hidden PCM marker".toByteArray()
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.LiveVoiceFrame(
            DebugLanVoiceWireCodec.Kind.START, byteArrayOf()
        ))
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.LiveVoiceFrame(
            DebugLanVoiceWireCodec.Kind.DATA, pcm
        ))
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.LiveVoiceFrame(
            DebugLanVoiceWireCodec.Kind.END, byteArrayOf()
        ))
        val report = evidence.summary()
        assertTrue(report.contains("Text written to TLS here: 1"))
        assertTrue(report.contains("Text received here: 1"))
        assertTrue(report.contains("Peer-app ACK observed here: 1"))
        assertTrue(report.contains("Voice floor grants observed here: 1"))
        assertTrue(report.contains("Voice floor denials observed here: 1"))
        assertTrue(report.contains("Voice PCM frames written to TLS here: 4"))
        assertTrue(report.contains("Inbound voice DATA frames: 1"))
        for (forbidden in listOf(
            "secret", "alpha", "beta", "4567", "4568", "hidden PCM marker"
        )) assertFalse("Sensitive test value leaked: $forbidden", report.contains(forbidden))
        assertTrue(report.contains("not a delivery/read receipt"))
        assertTrue(report.contains("not a delivery/read receipt") ||
            report.contains("not a delivery/read receipt".uppercase()))
        evidence.observe(DebugBluetoothApprovedTextChannel.Event.Ready)
        assertTrue(evidence.summary().contains("Text written to TLS here: 0"))
    }

    @Test fun invalidMethodLabelsCannotSmuggleUserDataIntoReport() {
        try {
            DebugNearbyQaEvidence("remote peer is someone's address")
            fail("Should accept only approved local radio method labels")
        } catch (_: IllegalArgumentException) { }
    }
}
