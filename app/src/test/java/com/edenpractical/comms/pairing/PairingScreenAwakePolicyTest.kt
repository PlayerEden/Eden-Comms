package com.edenpractical.comms.pairing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingScreenAwakePolicyTest {
    @Test fun onlyActualPairingAndForegroundChatNeedWakefulness() {
        for (stage in listOf("showHost", "scanHost", "scanReply", "showReply",
            "connecting", "approval", "demo")) {
            assertTrue(stage, PairingScreenAwakePolicy.shouldKeepOn(stage))
        }
    }

    @Test fun homeIdleFailureAndCompletedScreensReleaseWakefulness() {
        for (stage in listOf("checking", "choose", "result", "identityError",
            "", "invalid", "wifi", "home", "developer", "ptt")) {
            assertFalse(stage, PairingScreenAwakePolicy.shouldKeepOn(stage))
        }
    }
}
