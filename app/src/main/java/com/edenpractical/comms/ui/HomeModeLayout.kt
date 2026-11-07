package com.edenpractical.comms.ui

/**
 * Pure presentation policy for the two Home communication choices.
 * Stacking does not authorize peer links, text or voice.
 */
internal object HomeModeLayout {
    fun shouldStack(availableWidthDp: Float, fontScale: Float): Boolean =
        availableWidthDp < 360f || fontScale >= 1.3f
}
