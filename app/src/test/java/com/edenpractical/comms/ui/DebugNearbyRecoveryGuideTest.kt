package com.edenpractical.comms.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugNearbyRecoveryGuideTest {
    @Test fun everyObservedTerminalSituationHasPrivacySafeNextAction() {
        DebugNearbyRecoveryGuide.Situation.entries.forEach { situation ->
            val step = DebugNearbyRecoveryGuide.forSituation(situation)
            assertTrue(situation.name, step.heading.isNotBlank())
            assertTrue(situation.name, step.action.isNotBlank())
            assertTrue(situation.name, step.caution.isNotBlank())
            val all = step.heading + step.action + step.caution
            assertFalse(all.contains("192.168."))
            assertFalse(all.contains("SSID"))
            assertFalse(all.contains("certificate fingerprint"))
            assertFalse(all.contains("automatically approve", ignoreCase = true))
            assertFalse(all.contains("skip verification", ignoreCase = true))
        }
    }

    @Test fun bluetoothRestartNeedsFreshTwoWayHumanApproval() {
        val ended = DebugNearbyRecoveryGuide.forSituation(
            DebugNearbyRecoveryGuide.Situation.BLUETOOTH_ENDED
        )
        assertTrue(ended.action.contains("two-code QR"))
        assertTrue(ended.action.contains("Both people must approve again"))
        assertTrue(ended.caution.contains("No connection is active"))
        val permission = DebugNearbyRecoveryGuide.forSituation(
            DebugNearbyRecoveryGuide.Situation.BLUETOOTH_PERMISSION
        )
        assertTrue(permission.action.contains("permission"))
        assertTrue(permission.caution.contains("No automatic restart"))
    }

    @Test fun p2pHostCleanupNeverClaimsGroupRemovedUntilConfirmed() {
        val cleaning = DebugNearbyRecoveryGuide.forSituation(
            DebugNearbyRecoveryGuide.Situation.P2P_HOST_CLEANING
        )
        assertTrue(cleaning.heading.contains("CLEANUP"))
        assertTrue(cleaning.caution.contains("not confirmed"))
        val unknown = DebugNearbyRecoveryGuide.forSituation(
            DebugNearbyRecoveryGuide.Situation.P2P_HOST_CLEANUP_UNCONFIRMED
        )
        assertTrue(unknown.heading.contains("NOT CONFIRMED"))
        assertTrue(unknown.caution.contains("Do not assume"))
        val link = DebugNearbyRecoveryGuide.forSituation(
            DebugNearbyRecoveryGuide.Situation.P2P_HOST_LINK_ENDED
        )
        assertTrue(link.action.contains("STOP AND REMOVE TEST GROUP"))
        assertTrue(link.caution.contains("both approvals"))
    }

    @Test fun p2pJoinRecoverySeparatesRadioLossFromOrdinaryDiscovery() {
        val radio = DebugNearbyRecoveryGuide.forSituation(
            DebugNearbyRecoveryGuide.Situation.P2P_JOIN_RADIO_OFF
        )
        val search = DebugNearbyRecoveryGuide.forSituation(
            DebugNearbyRecoveryGuide.Situation.P2P_JOIN_SEARCH_ENDED
        )
        assertTrue(radio.action.contains("Turn Wi-Fi back on"))
        assertTrue(search.action.contains("Start another search"))
        assertFalse(search.caution.contains("verified chat active"))
        val link = DebugNearbyRecoveryGuide.forSituation(
            DebugNearbyRecoveryGuide.Situation.P2P_JOIN_LINK_ENDED
        )
        assertTrue(link.action.contains("group removal"))
        assertTrue(link.caution.contains("both approvals"))
    }
}
