package com.edenpractical.comms.bluetooth

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import org.junit.Assert.*
import org.junit.Test

/** No Android Bluetooth socket or authority: pure JVM future-owner rehearsal. */
class DebugBluetoothFutureChannelBundleTest {
    private class CountingSocket(private val fail: Boolean = false) : Closeable {
        val closes = AtomicInteger(0)
        override fun close() {
            closes.incrementAndGet()
            if (fail) error("synthetic RFCOMM close failure")
        }
    }
    private class CountingAdapter(
        private val events: MutableList<String>? = null,
        private val fail: Boolean = false
    ) : AutoCloseable {
        val closes = AtomicInteger(0)
        override fun close() {
            events?.add("adapter")
            closes.incrementAndGet()
            if (fail) error("synthetic TLS adapter close failure")
        }
    }
    private fun engine(): SSLEngine =
        SSLContext.getInstance("TLS").apply { init(null, null, null) }.createSSLEngine()

    private fun observed() = DebugBluetoothContinuationBudget.Observations(
        peerPinChecked = true, qrChallengeChecked = true, exporterChecked = true,
        bilateralApprovals = true, bilateralConfirmations = true,
        foreground = true, connectPermission = true
    )
    private fun context(pin: Byte = 0x11) = DebugBluetoothFutureSessionContext(
        true, ByteArray(32) { pin }, ByteArray(32) { 0x22 }
    )

    @Test fun exactReferenceMatchIsOnlyCorrelationAndClosureIsIrreversible() {
        val socket = CountingSocket()
        val tls = engine()
        val adapter = CountingAdapter()
        val bundle = DebugBluetoothFutureChannelBundle(socket, tls, adapter)
        assertTrue(bundle.matchesCandidates(socket, tls))
        assertFalse(bundle.matchesCandidates(CountingSocket(), tls))
        val otherEngine = engine()
        try {
            assertFalse(bundle.matchesCandidates(socket, otherEngine))
        } finally { otherEngine.closeOutbound() }
        assertFalse(bundle.isClosed())
        bundle.close()
        assertTrue(bundle.isClosed())
        assertFalse(bundle.matchesCandidates(socket, tls))
        assertEquals(1, socket.closes.get())
        assertEquals(1, adapter.closes.get())
        assertTrue(tls.isOutboundDone)
        bundle.close()
        assertEquals(1, socket.closes.get())
        assertEquals(1, adapter.closes.get())
    }

    @Test fun adapterFailureDoesNotPreventRadioOrEngineCleanup() {
        val socket = CountingSocket(fail = true)
        val tls = engine()
        val adapter = CountingAdapter(fail = true)
        val bundle = DebugBluetoothFutureChannelBundle(socket, tls, adapter)
        bundle.close() // failure in adapter and socket must be contained
        assertTrue(bundle.isClosed())
        assertEquals(1, adapter.closes.get())
        assertEquals(1, socket.closes.get())
        assertTrue(tls.isOutboundDone)
        bundle.close()
        assertEquals(1, adapter.closes.get())
        assertEquals(1, socket.closes.get())
    }

    @Test fun adapterIsInvalidatedBeforeRadioIsClosed() {
        val events = mutableListOf<String>()
        val socket = object : Closeable {
            override fun close() { events.add("radio") }
        }
        val adapter = CountingAdapter(events)
        val tls = engine()
        DebugBluetoothFutureChannelBundle(socket, tls, adapter).close()
        assertEquals(listOf("adapter", "radio"), events)
        assertTrue(tls.isOutboundDone)
    }

    @Test fun futureOwnerRejectionClosesAllResourcesWithoutGrantingAnySession() {
        val socket = CountingSocket()
        val tls = engine()
        val adapter = CountingAdapter()
        val bundle = DebugBluetoothFutureChannelBundle(socket, tls, adapter)
        val owner = DebugBluetoothFutureOwnedSession()
        val ctx = context()
        try {
            assertFalse(owner.begin(1, 100L,
                observed().copy(bilateralConfirmations = false),
                bundle, ctx))
            assertEquals(DebugBluetoothFutureOwnedSession.Phase.CLOSED, owner.snapshot().phase)
            assertTrue(bundle.isClosed())
            assertEquals(1, socket.closes.get())
            assertEquals(1, adapter.closes.get())
            assertTrue(tls.isOutboundDone)
        } finally {
            ctx.close()
            owner.close()
        }
    }

    @Test fun currentContextMismatchClosesBundleButStaleObservationDoesNot() {
        val socket = CountingSocket()
        val tls = engine()
        val adapter = CountingAdapter()
        val bundle = DebugBluetoothFutureChannelBundle(socket, tls, adapter)
        val owner = DebugBluetoothFutureOwnedSession()
        val good = context()
        val wrong = context(pin = 0x33)
        try {
            assertTrue(owner.begin(42, 500, observed(), bundle, good))
            assertFalse(owner.observe(41, 501, false, false, wrong))
            assertFalse(bundle.isClosed())
            assertEquals(0, socket.closes.get())
            assertFalse(owner.observe(42, 502, true, true, wrong))
            assertEquals(DebugBluetoothFutureOwnedSession.End.INVALIDATED, owner.snapshot().end)
            assertTrue(bundle.isClosed())
            assertEquals(1, socket.closes.get())
            assertEquals(1, adapter.closes.get())
            assertTrue(tls.isOutboundDone)
        } finally {
            good.close()
            wrong.close()
            owner.close()
        }
    }

    @Test fun repeatedOfferOfSameOwnedBundleDoesNotCloseItWhileCurrent() {
        val socket = CountingSocket()
        val tls = engine()
        val adapter = CountingAdapter()
        val bundle = DebugBluetoothFutureChannelBundle(socket, tls, adapter)
        val owner = DebugBluetoothFutureOwnedSession()
        val ctx = context()
        try {
            assertTrue(owner.begin(21, 1, observed(), bundle, ctx))
            assertFalse(owner.begin(22, 2, observed(), bundle, ctx))
            assertFalse(bundle.isClosed())
            assertTrue(owner.stop(21))
            assertTrue(bundle.isClosed())
            assertEquals(1, socket.closes.get())
            assertEquals(1, adapter.closes.get())
            assertTrue(tls.isOutboundDone)
        } finally {
            ctx.close()
            owner.close()
        }
    }

    @Test fun concurrentStopTimeoutAndCloseCleanUpExactlyOnce() {
        val socket = CountingSocket()
        val tls = engine()
        val adapter = CountingAdapter()
        val bundle = DebugBluetoothFutureChannelBundle(socket, tls, adapter)
        val owner = DebugBluetoothFutureOwnedSession(
            DebugBluetoothContinuationBudget(maxLifetimeMs = 10)
        )
        val ctx = context()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(5)
        try {
            assertTrue(owner.begin(99, 100, observed(), bundle, ctx))
            val tasks = (0..4).map { i ->
                pool.submit {
                    assertTrue(start.await(3, TimeUnit.SECONDS))
                    when (i) {
                        0 -> owner.stop(99)
                        1 -> owner.observe(99, 110, true, true, ctx)
                        2 -> owner.observe(99, 101, false, true, ctx)
                        3 -> owner.close()
                        else -> bundle.close()
                    }
                }
            }
            start.countDown()
            tasks.forEach { it.get(3, TimeUnit.SECONDS) }
            assertTrue(bundle.isClosed())
            assertEquals(1, adapter.closes.get())
            assertEquals(1, socket.closes.get())
            assertTrue(tls.isOutboundDone)
            assertEquals(DebugBluetoothFutureOwnedSession.Phase.CLOSED, owner.snapshot().phase)
        } finally {
            start.countDown()
            pool.shutdownNow()
            ctx.close()
            owner.close()
        }
    }
}
