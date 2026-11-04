package com.edenpractical.comms.transport

import java.net.BindException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LanProbeDiagnosticsTest {
    private val d = LanProbeDiagnostics

    @Test fun unreachablePeerIsDistinguishedFromTimeout() {
        assertEquals(d.Code.PEER_UNREACHABLE,
            d.classify(d.Step.TCP_CONNECT, ConnectException("192.168.2.55")).code)
        assertEquals(d.Code.CONNECT_TIMEOUT,
            d.classify(d.Step.TCP_CONNECT, SocketTimeoutException("192.168.2.55")).code)
    }

    @Test fun hostWaitTimeoutHasSpecificInstruction() {
        val failure = d.classify(d.Step.WAIT_FOR_PEER, SocketTimeoutException())
        assertEquals(d.Code.HOST_WAIT_TIMEOUT, failure.code)
        assert(failure.display().contains("Start listening first"))
    }

    @Test fun certificateFailureOverridesGenericTlsFailure() {
        val tls = SSLHandshakeException("sensitive certificate data")
        tls.initCause(CertificateException("SECRET-FINGERPRINT"))
        val failure = d.classify(d.Step.TLS_HANDSHAKE, tls)
        assertEquals(d.Code.CERTIFICATE_REJECTED, failure.code)
        assertFalse(failure.display().contains("SECRET"))
        assertFalse(failure.display().contains("FINGERPRINT"))
        assertFalse(failure.display().contains("sensitive"))
    }

    @Test fun handshakeAndBindAreClassifiedWithoutRawExceptionDetails() {
        assertEquals(d.Code.TLS_HANDSHAKE_FAILED,
            d.classify(d.Step.TLS_HANDSHAKE, SSLHandshakeException("private")).code)
        assertEquals(d.Code.LISTEN_UNAVAILABLE,
            d.classify(d.Step.BIND, BindException("private network address")).code)
    }

    @Test fun setupAndWifiFailuresHaveSafeDescriptions() {
        assertEquals(d.Code.WIFI_UNAVAILABLE,
            d.classify(d.Step.WIFI, IllegalStateException("wifi details")).code)
        assertEquals(d.Code.TLS_UNSUPPORTED,
            d.classify(d.Step.TLS_SETUP, IllegalStateException("keystore secret")).code)
        assertFalse(d.classify(d.Step.TLS_SETUP, IllegalStateException("keystore secret"))
            .display().contains("keystore secret"))
    }

    @Test fun timeoutDuringHandshakeIsNotMislabeledAsHostTimeout() {
        assertEquals(d.Code.HANDSHAKE_TIMEOUT,
            d.classify(d.Step.TLS_HANDSHAKE, SocketTimeoutException()).code)
    }
}
