package com.edenpractical.comms.pairing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildEvidenceReportTest {
    private val revision = "a".repeat(40)
    private val certificate = "b".repeat(64)

    private fun evidence() = BuildEvidenceReport.Evidence(
        versionName = "0.2.12-internal-10014",
        versionCode = 10014,
        sourceRevision = revision,
        apkSignerSha256 = certificate
    )

    @Test fun completePublicEvidenceIsCopiedWithoutCallingItUpdateVerification() {
        val report = BuildEvidenceReport.format(evidence())
        assertTrue(report.contains("App package: com.edenpractical.comms"))
        assertTrue(report.contains("App versionCode: 10014"))
        assertTrue(report.contains("Source revision: $revision"))
        assertTrue(report.contains("APK signing certificate SHA-256: $certificate"))
        assertTrue(report.contains("NOT a device upgrade test"))
        assertTrue(report.contains("NOT verified"))
        assertTrue(report.contains("NOT the TLS peer identity"))
        assertFalse(report.contains("PRIVATE KEY"))
        val brief = BuildEvidenceReport.brief(evidence())
        assertTrue(brief.contains("source " + revision.take(10)))
        assertTrue(brief.contains("signer " + certificate.take(10)))
        assertFalse(brief.contains(revision))
        assertFalse(brief.contains(certificate))
    }

    @Test fun unknownLocalBuildDataMustNeverPretendToBeCIBuild() {
        val report = BuildEvidenceReport.format(evidence().copy(
            versionCode = null,
            sourceRevision = "unavailable",
            apkSignerSha256 = null
        ))
        assertTrue(report.contains("App versionCode: unavailable"))
        assertTrue(report.contains("Source revision: unavailable"))
        assertTrue(report.contains("APK signing certificate SHA-256: unavailable"))
        assertTrue(report.contains("Source revision may be unavailable for local IDE builds"))
    }

    @Test fun maliciousOrMalformedMetadataIsNeverCopied() {
        val bad = BuildEvidenceReport.format(evidence().copy(
            versionName = "0.2.12\nQR=SECRET-WIFI",
            versionCode = Long.MAX_VALUE,
            sourceRevision = "ABCD\nprivate-name",
            apkSignerSha256 = certificate + "\nDATA=secret"
        ))
        assertTrue(bad.contains("App version: unavailable"))
        assertTrue(bad.contains("App versionCode: unavailable"))
        assertTrue(bad.contains("Source revision: unavailable"))
        assertTrue(bad.contains("APK signing certificate SHA-256: unavailable"))
        assertFalse(bad.contains("QR=SECRET-WIFI"))
        assertFalse(bad.contains("private-name"))
        assertFalse(bad.contains("DATA=secret"))
    }

    @Test fun allSignerAndRevisionPatternsAreStrictlyValidated() {
        for (badRevision in listOf("", "A".repeat(40), "0".repeat(39), "0".repeat(41))) {
            assertTrue(BuildEvidenceReport.revision(badRevision) == "unavailable")
        }
        for (badSigner in listOf(null, "", "A".repeat(64), "f".repeat(63), "g".repeat(64))) {
            assertTrue(BuildEvidenceReport.signer(badSigner) == "unavailable")
        }
        assertTrue(BuildEvidenceReport.code(1L) == "1")
        assertTrue(BuildEvidenceReport.code(0L) == "unavailable")
    }
}
