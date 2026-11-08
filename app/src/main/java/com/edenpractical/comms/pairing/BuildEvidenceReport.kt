package com.edenpractical.comms.pairing

/**
 * Public APK/build metadata only, not an Android Keystore identity, QR token,
 * TLS peer certificate, delivery proof or independent security assessment.
 * Formatting is independent of Android so it can be regression tested on JVM.
 */
internal object BuildEvidenceReport {
    data class Evidence(
        val versionName: String,
        val versionCode: Long?,
        val sourceRevision: String,
        val apkSignerSha256: String?,
        val applicationId: String = "com.edenpractical.comms"
    )

    fun packageId(value: String): String =
        value.takeIf {
            it == "com.edenpractical.comms" ||
                it == "com.edenpractical.comms.preview"
        } ?: "unavailable"

    fun version(value: String): String =
        value.takeIf {
            it.length in 1..64 && it.all { c ->
                c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' ||
                    c == '.' || c == '_' || c == '-'
            }
        } ?: "unavailable"

    fun code(value: Long?): String =
        value?.takeIf { it in 1..2_100_000_000L }?.toString() ?: "unavailable"

    fun revision(value: String): String =
        value.takeIf { it.matches(Regex("[0-9a-f]{40}")) } ?: "unavailable"

    fun signer(value: String?): String =
        value?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: "unavailable"

    /** Short display is a human hint, NOT a substitute for the full evidence. */
    fun brief(evidence: Evidence): String {
        val source = revision(evidence.sourceRevision)
        val certificate = signer(evidence.apkSignerSha256)
        return "${version(evidence.versionName)} (code ${code(evidence.versionCode)}), " +
            "source ${if (source == "unavailable") source else source.take(10)}, " +
            "signer ${if (certificate == "unavailable") certificate else certificate.take(10)}"
    }

    /** All copied fields are fixed-format public metadata; no arbitrary strings. */
    fun format(evidence: Evidence): String = listOf(
        "EDEN COMMS — INSTALLED BUILD EVIDENCE",
        "Public APK metadata from this one phone; NOT a device upgrade test.",
        "App package: ${packageId(evidence.applicationId)}",
        "App version: ${version(evidence.versionName)}",
        "App versionCode: ${code(evidence.versionCode)}",
        "Source revision: ${revision(evidence.sourceRevision)}",
        "APK signing certificate SHA-256: ${signer(evidence.apkSignerSha256)}",
        "APK signing certificate is NOT the TLS peer identity or QR certificate.",
        "Source revision may be unavailable for local IDE builds.",
        "APK file SHA-256, in-place update safety and Keystore retention are NOT verified here.",
        "Copying uses the Android system clipboard and is an explicit user action.",
        "No device serial, peer identity, QR code, address, text or secret is included."
    ).joinToString("\n")
}
