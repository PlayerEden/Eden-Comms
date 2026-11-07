package com.edenpractical.comms.bluetooth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.edenpractical.comms.security.AndroidKeystoreIdentity
import com.edenpractical.comms.security.LocalTlsIdentity
import java.lang.Exception

/**
 * User-guided in-person Bluetooth QR PREPARATION (not peer authentication).
 * The Activity owns this coordinator and clears it on exit, avoiding persistence
 * across process death, navigation, contact databases, or automatic pairing.
 */
internal class DebugBluetoothQrCoordinator {
    enum class Stage {
        CHOOSE, HOST_SHOW, HOST_SCAN_REPLY, JOIN_SCAN_HOST,
        JOIN_SHOW_REPLY, HOST_PREPARED, JOIN_PREPARED
    }

    private val repository = AndroidKeystoreIdentity()
    private var identity: LocalTlsIdentity? = null
    private var host: BluetoothQrPairingCodec.HostInvite? = null
    private var reply: BluetoothQrPairingCodec.JoinReply? = null
    private var prepared: BluetoothQrPairingCodec.Prepared? = null

    var stage by mutableStateOf(Stage.CHOOSE)
        private set
    var status by mutableStateOf(
        "Begin a fresh two-QR exchange in person. Discovery alone proves nothing."
    )
        private set
    var qrPayload by mutableStateOf("")
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
            status = "Local device identity could not be read. No QR prepared."
        }
    }

    /** Explicit person tap; NEVER automatically generate/replace device keys. */
    fun createIdentityWithConsent() {
        try {
            identity = repository.createWithConsent()
            identityReady = true
            status = "Your local identity is ready. It is not a trusted contact or connection."
        } catch (_: Exception) {
            identityReady = false
            identity = null
            status = "Identity could not be created. No QR prepared."
        }
    }

    fun startHost() {
        val own = identity ?: return fail("Create or load your device identity first.")
        clearQrOnly()
        try {
            val invite = BluetoothQrPairingCodec.freshHost(
                own.publicFingerprint(), System.currentTimeMillis() / 1000
            )
            host = invite
            qrPayload = BluetoothQrPairingCodec.encodeHost(invite)
            stage = Stage.HOST_SHOW
            status = "Show this fresh Bluetooth host QR to the other phone. " +
                "It is NOT verification of the radio link."
        } catch (_: Exception) {
            clearQrOnly()
            fail("Couldn't prepare a fresh host QR.")
        }
    }

    fun startJoin() {
        if (identity == null) {
            fail("Create or load your device identity first.")
            return
        }
        clearQrOnly()
        stage = Stage.JOIN_SCAN_HOST
        scannerGeneration++
        status = "Scan the host phone's Bluetooth QR. No connection is made by scanning."
    }

    fun scanReply() {
        if (stage != Stage.HOST_SHOW || host == null) return
        stage = Stage.HOST_SCAN_REPLY
        scannerGeneration++
        status = "Scan the reply QR on the other phone."
    }

    fun scanned(raw: String) {
        val own = identity ?: return fail("Local identity unavailable.")
        val now = System.currentTimeMillis() / 1000
        try {
            when (stage) {
                Stage.HOST_SCAN_REPLY -> {
                    val invite = host ?: return
                    val seenReply = BluetoothQrPairingCodec.decodeReply(raw, invite, now)
                    val result = BluetoothQrPairingCodec.preparedHost(invite, seenReply, now)
                    reply = seenReply
                    prepared = result
                    stage = Stage.HOST_PREPARED
                    qrPayload = ""
                    status = "Both QR metadata fields matched on this phone. " +
                        "The Bluetooth socket and peer private key are NOT authenticated."
                }
                Stage.JOIN_SCAN_HOST -> {
                    val invite = BluetoothQrPairingCodec.decodeHost(raw, now)
                    val response = BluetoothQrPairingCodec.freshReply(
                        invite, own.publicFingerprint(), now
                    )
                    val result = BluetoothQrPairingCodec.preparedJoin(
                        invite, response, own.publicFingerprint(), now
                    )
                    host = invite
                    reply = response
                    prepared = result
                    qrPayload = BluetoothQrPairingCodec.encodeReply(response)
                    stage = Stage.JOIN_SHOW_REPLY
                    status = "Show this reply QR to the host. The host must scan it. " +
                        "You cannot know from this screen whether it was received."
                }
                else -> Unit
            }
        } catch (_: Exception) {
            // Do not echo raw QR, cert pins, challenge or detailed parser errors.
            status = "That QR was invalid, unrelated, or expired. Tap Retry, or start fresh."
        }
    }

    fun replyShown() {
        if (stage != Stage.JOIN_SHOW_REPLY || prepared == null) return
        qrPayload = ""
        stage = Stage.JOIN_PREPARED
        status = "Your reply QR was displayed, but this phone has NO proof the host " +
            "scanned it. Bluetooth socket and peer identity remain UNVERIFIED."
    }

    fun readyForDebugLink(hostRole: Boolean): Boolean {
        val required = if (hostRole) Stage.HOST_PREPARED else Stage.JOIN_PREPARED
        if (stage != required || identity == null) return false
        return try {
            val now = System.currentTimeMillis() / 1000
            // Validate the entire canonical role-bound transcript and expiry.
            // This does NOT prove that a remote radio device owns the pin.
            prepared?.binding(now)
            prepared?.peerFingerprint(now)
            prepared != null
        } catch (_: Exception) {
            clearQrOnly()
            fail("Bluetooth QR codes expired. Start a new two-code exchange.")
            false
        }
    }

    fun cameraFailed() {
        status = "Camera could not start. Tap Retry, or cancel the QR exchange."
    }

    fun retryCamera() {
        if (stage == Stage.HOST_SCAN_REPLY || stage == Stage.JOIN_SCAN_HOST)
            scannerGeneration++
    }

    fun clear() {
        clearQrOnly()
        status = "Temporary Bluetooth QR preparation cleared. No peer is trusted."
    }

    private fun clearQrOnly() {
        prepared?.clear()
        prepared = null
        host?.pin?.fill(0)
        host?.nonce?.fill(0)
        host = null
        reply?.hostNonce?.fill(0)
        reply?.pin?.fill(0)
        reply?.nonce?.fill(0)
        reply = null
        qrPayload = ""
        stage = Stage.CHOOSE
    }

    private fun fail(message: String) { status = message }
}
