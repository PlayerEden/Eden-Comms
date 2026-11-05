package com.edenpractical.comms.transport

import java.net.BindException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import com.edenpractical.comms.security.SessionProofException
import java.security.InvalidKeyException
import java.security.SignatureException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Privacy-safe diagnostics for the one-shot development handshake probe.
 *
 * Classify only exception TYPES and the step that failed. Never show or log
 * certificate bytes, IPs, hostnames, QR payloads, exception messages or stack traces.
 * These classifications guide testing; they aren't proof of a particular cause.
 */
internal object LanProbeDiagnostics {
    enum class Step {
        WIFI, TLS_SETUP, BIND, WAIT_FOR_PEER, TCP_CONNECT, TLS_HANDSHAKE, VERIFY_PIN, SESSION_BINDING, READY_EXCHANGE
    }

    enum class Code {
        WIFI_UNAVAILABLE,
        TLS_UNSUPPORTED,
        LISTEN_UNAVAILABLE,
        HOST_WAIT_TIMEOUT,
        PEER_UNREACHABLE,
        HOST_NOT_ACCEPTING,
        NETWORK_UNREACHABLE,
        CONNECT_TIMEOUT,
        HANDSHAKE_TIMEOUT,
        CERTIFICATE_REJECTED,
        LOCAL_SIGNING_FAILED,
        TLS_HANDSHAKE_FAILED,
        SESSION_BINDING_REJECTED,
        SESSION_BINDING_TIMEOUT,
        READY_TIMEOUT,
        READY_REJECTED,
        CONNECTION_DROPPED,
        UNKNOWN
    }

    data class Failure(val code: Code, val message: String) {
        fun display(): String = "Test failed (${code.name.replace('_', '-')}). ${message}"
    }

    fun classify(step: Step, error: Throwable): Failure {
        val causes = generateSequence(error) { if (it.cause === it) null else it.cause }.take(12).toList()
        fun has(kind: Class<out Throwable>) = causes.any { kind.isInstance(it) }

        val code = when {
            has(SSLPeerUnverifiedException::class.java) ||
                has(CertificateException::class.java) -> Code.CERTIFICATE_REJECTED
            has(BindException::class.java) -> Code.LISTEN_UNAVAILABLE
            has(NoRouteToHostException::class.java) -> Code.NETWORK_UNREACHABLE
            has(ConnectException::class.java) -> Code.HOST_NOT_ACCEPTING
            has(SessionProofException::class.java) -> Code.SESSION_BINDING_REJECTED
            has(SocketTimeoutException::class.java) && step == Step.SESSION_BINDING ->
                Code.SESSION_BINDING_TIMEOUT
            has(SocketTimeoutException::class.java) && step == Step.READY_EXCHANGE ->
                Code.READY_TIMEOUT
            has(SocketTimeoutException::class.java) && step == Step.WAIT_FOR_PEER ->
                Code.HOST_WAIT_TIMEOUT
            has(SocketTimeoutException::class.java) && step == Step.TCP_CONNECT ->
                Code.CONNECT_TIMEOUT
            has(SocketTimeoutException::class.java) -> Code.HANDSHAKE_TIMEOUT
            step == Step.TLS_HANDSHAKE && (
                has(InvalidKeyException::class.java) ||
                has(SignatureException::class.java)
            ) -> Code.LOCAL_SIGNING_FAILED
            has(SSLHandshakeException::class.java) || has(SSLException::class.java) ->
                Code.TLS_HANDSHAKE_FAILED
            step == Step.WIFI -> Code.WIFI_UNAVAILABLE
            step == Step.TLS_SETUP -> Code.TLS_UNSUPPORTED
            step == Step.BIND -> Code.LISTEN_UNAVAILABLE
            step == Step.WAIT_FOR_PEER -> Code.HOST_WAIT_TIMEOUT
            step == Step.TCP_CONNECT -> Code.PEER_UNREACHABLE
            step == Step.TLS_HANDSHAKE -> Code.CONNECTION_DROPPED
            step == Step.SESSION_BINDING -> Code.SESSION_BINDING_REJECTED
            step == Step.READY_EXCHANGE -> Code.READY_REJECTED
            step == Step.VERIFY_PIN -> Code.CERTIFICATE_REJECTED
            else -> Code.UNKNOWN
        }

        val explanation = when (code) {
            Code.WIFI_UNAVAILABLE -> "Connect both phones to the same Wi-Fi network."
            Code.TLS_UNSUPPORTED -> "This device couldn't prepare the required TLS 1.3 connection."
            Code.LISTEN_UNAVAILABLE -> "The host couldn't open its local listening port. Try a new test."
            Code.HOST_WAIT_TIMEOUT -> "The other phone did not connect. Keep both apps open on the same Wi-Fi and try fresh codes."
            Code.PEER_UNREACHABLE -> "The other phone couldn't be reached. Check the Wi-Fi network and retry."
            Code.HOST_NOT_ACCEPTING -> "The other phone is not accepting connections. Complete both QR scans and keep the apps open. Your Wi-Fi may block device-to-device connections."
            Code.NETWORK_UNREACHABLE -> "No network route to the host. Check that both phones use the same non-guest Wi-Fi and that device isolation is off."
            Code.CONNECT_TIMEOUT -> "The connection timed out. Keep both phones open on the same Wi-Fi and try fresh codes."
            Code.HANDSHAKE_TIMEOUT -> "The encrypted handshake timed out. Try again with both screens open."
            Code.CERTIFICATE_REJECTED -> "The expected device identity wasn't verified. Start a new pairing; never bypass verification."
            Code.LOCAL_SIGNING_FAILED -> "This phone's private key couldn't complete the TLS signature. Check the device identity's signing permissions in the QR test."
            Code.TLS_HANDSHAKE_FAILED -> "TLS couldn't authenticate the connection. A device compatibility or identity issue needs investigation."
            Code.SESSION_BINDING_REJECTED -> "Both phones did not confirm the same QR session. Start again with two fresh QR scans."
            Code.SESSION_BINDING_TIMEOUT -> "The QR session check timed out. Keep both screens open and try again."
            Code.READY_TIMEOUT -> "The final readiness check timed out. Keep both apps open and start again with fresh codes."
            Code.READY_REJECTED -> "The phones did not finish the same test readiness exchange. Restart both tests using fresh codes."
            Code.CONNECTION_DROPPED -> "The connection stopped during authentication. Retry with both screens open."
            Code.UNKNOWN -> "The connection test couldn't finish. No messages or audio were sent."
        }
        return Failure(code, explanation)
    }
}
