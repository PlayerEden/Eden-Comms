package com.edenpractical.comms.wifidirect

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two real JVM TCP sockets on loopback: validates network reachability only,
 * NOT any Android Wi-Fi Direct group, peer identity, TLS, consent or chat.
 */
class DebugWifiDirectTcpProbeTest {
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private fun unusedPort(): Int = ServerSocket(0, 1, loopback).use { it.localPort }

    @Test fun realHostAcceptAndJoinConnectUseZeroApplicationBytes() {
        val port = unusedPort()
        val host = DebugWifiDirectTcpProbe(port)
        val join = DebugWifiDirectTcpProbe(port)
        val ready = CountDownLatch(1)
        val complete = CountDownLatch(2)
        val successes = AtomicInteger(0)
        try {
            assertTrue(host.start(DebugWifiDirectTcpProbe.Role.HOST,
                loopback, { if (it) successes.incrementAndGet(); complete.countDown() },
                forJvmLoopback = true, onListening = { ready.countDown() }))
            assertTrue("Host did not bind test loopback", ready.await(2, TimeUnit.SECONDS))
            assertTrue(join.start(DebugWifiDirectTcpProbe.Role.JOIN,
                loopback, { if (it) successes.incrementAndGet(); complete.countDown() },
                forJvmLoopback = true))
            assertTrue(complete.await(4, TimeUnit.SECONDS))
            assertEquals(2, successes.get())
        } finally {
            host.close()
            join.close()
        }
    }

    @Test fun blockedHostAcceptCanBeCanceledWithoutLateSuccessCallback() {
        val host = DebugWifiDirectTcpProbe(unusedPort())
        val ready = CountDownLatch(1)
        val completed = AtomicInteger(0)
        assertTrue(host.start(DebugWifiDirectTcpProbe.Role.HOST,
            loopback, { completed.incrementAndGet() }, forJvmLoopback = true,
            onListening = { ready.countDown() }))
        assertTrue(ready.await(2, TimeUnit.SECONDS))
        host.close()
        host.close()
        // An abort must never be reported as a TCP handshake success.
        Thread.sleep(80)
        assertEquals(0, completed.get())
        assertFalse(host.start(DebugWifiDirectTcpProbe.Role.JOIN,
            loopback, { completed.incrementAndGet() }, forJvmLoopback = true))
    }

    @Test fun rejectPublicWildcardMulticastLinkLocalAndDefaultLoopback() {
        val privateAddress = InetAddress.getByAddress(
            byteArrayOf(192.toByte(), 168.toByte(), 49, 1)
        )
        assertTrue(DebugWifiDirectTcpProbe.testAddressAllowed(privateAddress))
        for (raw in listOf(
            byteArrayOf(8, 8, 8, 8),
            byteArrayOf(0, 0, 0, 0),
            byteArrayOf(224.toByte(), 0, 0, 1),
            byteArrayOf(169.toByte(), 254.toByte(), 1, 1),
            byteArrayOf(127, 0, 0, 1)
        )) {
            val address = InetAddress.getByAddress(raw)
            assertFalse(DebugWifiDirectTcpProbe.testAddressAllowed(address))
        }
        val probe = DebugWifiDirectTcpProbe(unusedPort())
        try {
            assertFalse(probe.start(DebugWifiDirectTcpProbe.Role.JOIN,
                InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)), {}))
            assertFalse(probe.start(DebugWifiDirectTcpProbe.Role.JOIN, loopback, {}))
        } finally {
            probe.close()
        }
    }

    @Test fun duplicateAdmissionIsRejectedAndNoUserPayloadApiExists() {
        val probe = DebugWifiDirectTcpProbe(unusedPort())
        val ready = CountDownLatch(1)
        try {
            assertTrue(probe.start(DebugWifiDirectTcpProbe.Role.HOST, loopback,
                {}, forJvmLoopback = true, onListening = { ready.countDown() }))
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            assertFalse(probe.start(DebugWifiDirectTcpProbe.Role.HOST,
                loopback, {}, forJvmLoopback = true))
        } finally {
            probe.close()
        }
    }
}
