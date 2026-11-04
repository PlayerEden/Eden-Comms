package com.edenpractical.comms.transport

import java.net.BindException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LanProbeDiagnosticsTest {
    private val d = LanProbeDiagnostics

    @Test fun unreachablePeerIsDistinguishedFromTimeout() {
        assertEquals(LanProbeDiagnostics.Code.HOST_NOT_ACCEPTING,
            d.classify(LanProbeDiagnostics.Step.TCP_CONNECT, ConnectException("192.168.2.55")).code)
        assertEquals(LanProbeDiagnostics.Code.CONNECT_TIMEOUT,
            d.classify(LanProbeDiagnostics.Step.TCP_CONNECT, SocketTimeoutException("192.168.2.55")).code)
    }

    @Test fun networkRouteFailureIsDifferentFromRejectedConnection() {
        val failure = d.classify(
            LanProbeDiagnostics.Step.TCP_CONNECT,
            NoRouteToHostException("SECRET-ADDRESS")
        )
        assertEquals(LanProbeDiagnostics.Code.NETWORK_UNREACHABLE, failure.code)
        assertFalse(failure.display().contains("SECRET"))
    }

    @Test fun refusingHostDoesNotEchoSensitiveSocketDetails() {
        val failure = d.classify(
            LanProbeDiagnostics.Step.TCP_CONNECT,
            ConnectException("SECRET-HOST-IP")
        )
        assertEquals(LanProbeDiagnostics.Code.HOST_NOT_ACCEPTING, failure.code)
        assertFalse(failure.display().contains("SECRET"))
        assert(failure.display().contains("Start listening"))
    }

    @Test fun hostWaitTimeoutHasSpecificInstruction() {
        val failure = d.classify(LanProbeDiagnostics.Step.WAIT_FOR_PEER, SocketTimeoutException())
        assertEquals(LanProbeDiagnostics.Code.HOST_WAIT_TIMEOUT, failure.code)
        assert(failure.display().contains("Start listening first"))
    }

    @Test fun certificateFailureOverridesGenericTlsFailure() {
        val tls = SSLHandshakeException("sensitive certificate data")
        tls.initCause(CertificateException("SECRET-FINGERPRINT"))
        val failure = d.classify(LanProbeDiagnostics.Step.TLS_HANDSHAKE, tls)
        assertEquals(LanProbeDiagnostics.Code.CERTIFICATE_REJECTED, failure.code)
        assertFalse(failure.display().contains("SECRET"))
        assertFalse(failure.display().contains("FINGERPRINT"))
        assertFalse(failure.display().contains("sensitive"))
    }

    @Test fun handshakeAndBindAreClassifiedWithoutRawExceptionDetails() {
        assertEquals(LanProbeDiagnostics.Code.TLS_HANDSHAKE_FAILED,
            d.classify(LanProbeDiagnostics.Step.TLS_HANDSHAKE, SSLHandshakeException("private")).code)
        assertEquals(LanProbeDiagnostics.Code.LISTEN_UNAVAILABLE,
            d.classify(LanProbeDiagnostics.Step.BIND, BindException("private network address")).code)
    }

    @Test fun setupAndWifiFailuresHaveSafeDescriptions() {
        assertEquals(LanProbeDiagnostics.Code.WIFI_UNAVAILABLE,
            d.classify(LanProbeDiagnostics.Step.WIFI, IllegalStateException("wifi details")).code)
        assertEquals(LanProbeDiagnostics.Code.TLS_UNSUPPORTED,
            d.classify(LanProbeDiagnostics.Step.TLS_SETUP, IllegalStateException("keystore secret")).code)
        assertFalse(d.classify(LanProbeDiagnostics.Step.TLS_SETUP, IllegalStateException("keystore secret"))
            .display().contains("keystore secret"))
    }

    @Test fun timeoutDuringHandshakeIsNotMislabeledAsHostTimeout() {
        assertEquals(LanProbeDiagnostics.Code.HANDSHAKE_TIMEOUT,
            d.classify(LanProbeDiagnostics.Step.TLS_HANDSHAKE, SocketTimeoutException()).code)
    }
}
