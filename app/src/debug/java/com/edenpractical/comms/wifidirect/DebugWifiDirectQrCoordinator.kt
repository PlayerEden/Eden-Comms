package com.edenpractical.comms.wifidirect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.edenpractical.comms.security.AndroidKeystoreIdentity
import com.edenpractical.comms.security.LocalTlsIdentity

/**
 * Three in-person QR steps. All public metadata is kept in RAM only until
 * cancel, background or expiry; the Keystore identity is not modified unless
 * explicitly created. No connection, peer address, trust grant or message API.
 */
internal class DebugWifiDirectQrCoordinator {
    enum class Stage {
        CHOOSE, HOST_SHOW, HOST_SCAN_REPLY, HOST_SHOW_ACK,
        JOIN_SCAN_HOST, JOIN_SHOW_REPLY, JOIN_SCAN_ACK, JOIN_PREPARED
    }

    private val repository = AndroidKeystoreIdentity()
    private var identity: LocalTlsIdentity? = null
    private var host: DebugWifiDirectQrCodec.Host? = null
    private var reply: DebugWifiDirectQrCodec.Reply? = null
    private var ack: DebugWifiDirectQrCodec.Ack? = null

    var stage by mutableStateOf(Stage.CHOOSE)
        private set
    var status by mutableStateOf(
        "Three fresh in-person QR steps prepare a peer identity expectation only."
    )
        private set
    var qrPayload by mutableStateOf("")
        private set
    var comparison by mutableStateOf("")
        private set
    var identityReady by mutableStateOf(false)
        private set
    var scannerGeneration by mutableIntStateOf(0)
        private set

    fun loadExistingIdentity() {
        try {
            identity = repository.existing()
            identityReady = identity != null
        } catch (_: Exception) {
            identity = null
            identityReady = false
            status = "Couldn't load your existing local identity."
        }
    }

    /** Only an explicit tap creates a key; no silent replacement. */
    fun createIdentityWithConsent() {
        try {
            identity = repository.createWithConsent()
            identityReady = true
            status = "Local public identity ready. No Wi-Fi Direct connection or trust granted."
        } catch (_: Exception) {
            identity = null
            identityReady = false
            status = "Identity could not be created; no QR generated."
        }
    }

    fun startHost() {
        val own = identity ?: return
        clearQrOnly()
        try {
            host = DebugWifiDirectQrCodec.newHost(
                own.publicFingerprint(), System.currentTimeMillis() / 1_000L
            )
            qrPayload = DebugWifiDirectQrCodec.encodeHost(requireNotNull(host))
            stage = Stage.HOST_SHOW
            status = "Show this host QR to the person beside you. " +
                "It contains public identity metadata, not a Wi-Fi target."
        } catch (_: Exception) {
            clearQrOnly()
            status = "Could not prepare a fresh host QR."
        }
    }

    fun startJoin() {
        if (identity == null) return
        clearQrOnly()
        stage = Stage.JOIN_SCAN_HOST
        scannerGeneration++
        status = "Scan the host phone's fresh Wi-Fi Direct preparation QR in person."
    }

    fun scanReply() {
        if (stage != Stage.HOST_SHOW) return
        val h = host ?: return
        try {
            DebugWifiDirectQrCodec.checkFresh(h, System.currentTimeMillis() / 1_000L)
            stage = Stage.HOST_SCAN_REPLY
            qrPayload = ""
            scannerGeneration++
            status = "Scan the other phone's reply QR. No radio connection will be made."
        } catch (_: Exception) { clear(); status = "QR expired. Start again." }
    }

    fun scanAck() {
        if (stage != Stage.JOIN_SHOW_REPLY) return
        val h = host ?: return
        try {
            DebugWifiDirectQrCodec.checkFresh(h, System.currentTimeMillis() / 1_000L)
            stage = Stage.JOIN_SCAN_ACK
            qrPayload = ""
            scannerGeneration++
            status = "Scan the host phone's acknowledgement QR (third QR)."
        } catch (_: Exception) { clear(); status = "QR expired. Start again." }
    }

    fun scanned(value: String) {
        val own = identity ?: return
        val now = System.currentTimeMillis() / 1_000L
        try {
            when (stage) {
                Stage.JOIN_SCAN_HOST -> {
                    val h = DebugWifiDirectQrCodec.decodeHost(value, now)
                    val r = DebugWifiDirectQrCodec.newReply(h, own.publicFingerprint(), now)
                    host = h
                    reply = r
                    qrPayload = DebugWifiDirectQrCodec.encodeReply(r)
                    stage = Stage.JOIN_SHOW_REPLY
                    status = "Show this reply QR to the host phone. " +
                        "The other phone must still return a third acknowledgement QR."
                }
                Stage.HOST_SCAN_REPLY -> {
                    val h = requireNotNull(host)
                    val r = DebugWifiDirectQrCodec.decodeReply(value, h, now)
                    val a = DebugWifiDirectQrCodec.makeAck(h, r, now)
                    reply = r
                    ack = a
                    comparison = DebugWifiDirectQrCodec.comparisonCode(h, r, now)
                    qrPayload = DebugWifiDirectQrCodec.encodeAck(a)
                    stage = Stage.HOST_SHOW_ACK
                    status = "Show this third QR to the other phone. Only compare " +
                        "the visible code after that phone has scanned it."
                }
                Stage.JOIN_SCAN_ACK -> {
                    val h = requireNotNull(host)
                    val r = requireNotNull(reply)
                    ack = DebugWifiDirectQrCodec.decodeAck(
                        value, h, r, own.publicFingerprint(), now
                    )
                    comparison = DebugWifiDirectQrCodec.comparisonCode(h, r, now)
                    qrPayload = ""
                    stage = Stage.JOIN_PREPARED
                    status = "Three QR metadata frames match. Compare the code aloud " +
                        "in person; neither phone or Wi-Fi Direct target is authenticated."
                }
                else -> Unit
            }
        } catch (_: Exception) {
            // Never log or echo raw QR, certificate pins, challenges or parser details.
            status = "Wrong, unrelated, tampered or expired QR. Retry or cancel."
        }
    }

    fun retryScanner() {
        if (stage in setOf(Stage.HOST_SCAN_REPLY, Stage.JOIN_SCAN_HOST,
                Stage.JOIN_SCAN_ACK)) scannerGeneration++
    }

    fun cameraFailed() {
        status = "QR camera unavailable. Retry or cancel; no radio action was attempted."
    }

    fun expireIfNeeded() {
        val h = host ?: return
        try {
            DebugWifiDirectQrCodec.checkFresh(h, System.currentTimeMillis() / 1_000L)
        } catch (_: Exception) {
            clear()
            status = "Wi-Fi Direct QR preparation expired. Start fresh."
        }
    }

    fun clear() {
        clearQrOnly()
        status = "All temporary Wi-Fi Direct QR metadata cleared. No peer trusted."
    }

    private fun clearQrOnly() {
        host?.clear()
        reply?.clear()
        ack?.clear()
        host = null
        reply = null
        ack = null
        qrPayload = ""
        comparison = ""
        stage = Stage.CHOOSE
    }
}
