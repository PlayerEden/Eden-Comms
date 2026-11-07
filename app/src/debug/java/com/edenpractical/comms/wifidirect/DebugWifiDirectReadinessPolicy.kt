package com.edenpractical.comms.wifidirect

/** Readiness observations are not an authenticated peer or permission to scan. */
internal object DebugWifiDirectReadinessPolicy {
    data class Observation(
        val apiLevel: Int,
        val featureAdvertised: Boolean,
        val managerPresent: Boolean,
        val wifiEnabled: Boolean?,
        val locationModeEnabled: Boolean?,
        val permissionGranted: Boolean
    )
    enum class State {
        NOT_SUPPORTED, SERVICE_MISSING, WIFI_UNKNOWN, WIFI_OFF,
        LOCATION_UNKNOWN, LOCATION_OFF, PERMISSION_MISSING, PREREQUISITES_OBSERVED
    }
    data class Report(
        val observation: Observation,
        val state: State,
        val permissionRequestAllowed: Boolean,
        val discoveryAllowed: Boolean
    )
    fun evaluate(o: Observation): Report {
        require(o.apiLevel >= 26) { "Unsupported Android API" }
        val state = when {
            !o.featureAdvertised -> State.NOT_SUPPORTED
            !o.managerPresent -> State.SERVICE_MISSING
            o.wifiEnabled == null -> State.WIFI_UNKNOWN
            !o.wifiEnabled -> State.WIFI_OFF
            o.locationModeEnabled == null -> State.LOCATION_UNKNOWN
            !o.locationModeEnabled -> State.LOCATION_OFF
            !o.permissionGranted -> State.PERMISSION_MISSING
            else -> State.PREREQUISITES_OBSERVED
        }
        return Report(o, state,
            permissionRequestAllowed = o.featureAdvertised && o.managerPresent &&
                !o.permissionGranted,
            discoveryAllowed = false)
    }
}
