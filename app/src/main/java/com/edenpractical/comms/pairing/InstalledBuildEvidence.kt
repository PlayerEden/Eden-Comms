package com.edenpractical.comms.pairing

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.edenpractical.comms.BuildConfig
import java.security.MessageDigest

/**
 * Reads only this installed APK's *public signing certificate* and version.
 * No Keystore private-key access, QR peer identity, network, data persistence,
 * permissions or background work. A failure is "unavailable", never invented.
 */
internal object InstalledBuildEvidence {
    @Suppress("DEPRECATION")
    fun read(context: Context): BuildEvidenceReport.Evidence {
        val revision = BuildConfig.SOURCE_REVISION
        return try {
            val pm = context.packageManager
            val packageName = context.packageName
            require(packageName == BuildConfig.APPLICATION_ID &&
                BuildEvidenceReport.packageId(packageName) != "unavailable")
            val info = when {
                Build.VERSION.SDK_INT >= 33 ->
                    pm.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(
                            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                        )
                    )
                Build.VERSION.SDK_INT >= 28 ->
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                else ->
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            // Active APK signer only; do not quietly treat a rotation history
            // or multiple signers as a single matching current certificate.
            val currentSigner = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.let { signing ->
                    if (signing.hasMultipleSigners()) null
                    else signing.apkContentsSigners?.singleOrNull()?.toByteArray()
                }
            } else {
                info.signatures?.singleOrNull()?.toByteArray()
            }
            val signerFingerprint = currentSigner?.let { raw ->
                val digest = MessageDigest.getInstance("SHA-256").digest(raw)
                digest.joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xFF)
                }
            }
            BuildEvidenceReport.Evidence(
                versionName = info.versionName ?: "unavailable",
                versionCode = versionCode,
                sourceRevision = revision,
                apkSignerSha256 = signerFingerprint,
                applicationId = packageName
            )
        } catch (_: Exception) {
            BuildEvidenceReport.Evidence(
                versionName = "unavailable",
                versionCode = null,
                sourceRevision = revision,
                apkSignerSha256 = null,
                applicationId = BuildConfig.APPLICATION_ID
            )
        }
    }
}
