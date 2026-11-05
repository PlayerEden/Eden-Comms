package com.edenpractical.comms.security

import java.io.ByteArrayInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionProofExchangeTest {
    private fun binding(
        host: Byte = 1,
        join: Byte = 2,
        hostNonce: Byte = 3,
        joinNonce: Byte = 4
    ) = SessionProofBinding(
        ByteArray(32) { host },
        ByteArray(32) { join },
        ByteArray(16) { hostNonce },
        ByteArray(16) { joinNonce }
    )

    /**
     * Connect two in-memory streams without Android radios. This checks both
     * roles' transcript validation; physical TLS/socket QA remains mandatory.
     */
    private fun exchange(
        hostBinding: SessionProofBinding,
        joinBinding: SessionProofBinding
    ): Pair<Boolean, Boolean> {
        val hostToJoin = PipedOutputStream()
        val joinInput = PipedInputStream(hostToJoin, 1024)
        val joinToHost = PipedOutputStream()
        val hostInput = PipedInputStream(joinToHost, 1024)
        val pool = Executors.newFixedThreadPool(2)
        return try {
            val host = pool.submit<Boolean> {
                try {
                    SessionProofExchange.runHost(hostInput, hostToJoin, hostBinding)
                    true
                } catch (_: Exception) {
                    false
                } finally {
                    hostToJoin.close()
                    hostInput.close()
                }
            }
            val join = pool.submit<Boolean> {
                try {
                    SessionProofExchange.runJoin(joinInput, joinToHost, joinBinding)
                    true
                } catch (_: Exception) {
                    false
                } finally {
                    joinToHost.close()
                    joinInput.close()
                }
            }
            Pair(host.get(5, TimeUnit.SECONDS), join.get(5, TimeUnit.SECONDS))
        } finally {
            hostToJoin.close()
            joinToHost.close()
            pool.shutdownNow()
        }
    }

    @Test fun matchingQrSessionAndFreshChallengesPassForBothPhones() {
        assertEquals(Pair(true, true), exchange(binding(), binding()))
    }

    @Test fun alteredJoinerFingerprintFailsClosed() {
        assertEquals(Pair(false, false), exchange(binding(), binding(join = 7)))
    }

    @Test fun staleHostQrNonceFailsClosed() {
        assertEquals(Pair(false, false), exchange(binding(), binding(hostNonce = 8)))
    }

    @Test fun changedJoinQrNonceFailsClosed() {
        assertEquals(Pair(false, false), exchange(binding(), binding(joinNonce = 9)))
    }

    @Test fun truncatedProofCannotPass() {
        assertThrows(Exception::class.java) {
            SessionProofExchange.runJoin(ByteArrayInputStream(byteArrayOf(1, 2)), PipedOutputStream(), binding())
        }
    }

    @Test fun incompleteOrSelfPinsAreRejectedBeforeNetworkUse() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionProofBinding(ByteArray(31), ByteArray(32), ByteArray(16), ByteArray(16))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SessionProofBinding(ByteArray(32) { 1 }, ByteArray(32) { 1 }, ByteArray(16), ByteArray(16))
        }
    }
}
