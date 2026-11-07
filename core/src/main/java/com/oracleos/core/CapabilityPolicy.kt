package com.oracleos.core

/**
 * Applies Oracle's mode to capabilities already installed and available.
 * Android permission checks remain the responsibility of the feature implementation.
 */
object CapabilityPolicy {
    private val survivalCapabilities = setOf(
        Capability.EMERGENCY_DIALER,
        Capability.SMS_COMPOSER,
        Capability.OFFLINE_MAPS,
        Capability.GPS,
        Capability.COMPASS,
        Capability.DOCUMENT_SEARCH,
        Capability.DEVICE_STATUS,
        Capability.FLASHLIGHT,
        Capability.APP_LAUNCHER,
    )

    fun available(mode: OracleMode, installed: Set<Capability>): Set<Capability> =
        when (mode) {
            OracleMode.STANDARD, OracleMode.FULL_CONTROL -> installed.toSet()
            OracleMode.SURVIVAL -> installed.intersect(survivalCapabilities)
        }
}
