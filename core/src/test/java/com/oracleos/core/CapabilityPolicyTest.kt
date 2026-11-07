package com.oracleos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityPolicyTest {
    @Test
    fun survivalKeepsInstalledEmergencyFeaturesAndReleasesHeavyFeatures() {
        val installed = setOf(
            Capability.EMERGENCY_DIALER,
            Capability.SMS_COMPOSER,
            Capability.OFFLINE_MAPS,
            Capability.DOCUMENT_SEARCH,
            Capability.LOCAL_LLM,
            Capability.REMOTE_LLM,
            Capability.CAMERA,
        )

        val active = CapabilityPolicy.available(OracleMode.SURVIVAL, installed)

        assertTrue(Capability.EMERGENCY_DIALER in active)
        assertTrue(Capability.SMS_COMPOSER in active)
        assertTrue(Capability.OFFLINE_MAPS in active)
        assertTrue(Capability.DOCUMENT_SEARCH in active)
        assertFalse(Capability.LOCAL_LLM in active)
        assertFalse(Capability.REMOTE_LLM in active)
        assertFalse(Capability.CAMERA in active)
    }

    @Test
    fun neverInventsMissingHardwareOrPermissions() {
        val installed = setOf(Capability.DOCUMENT_SEARCH)

        assertEquals(installed, CapabilityPolicy.available(OracleMode.STANDARD, installed))
        assertEquals(installed, CapabilityPolicy.available(OracleMode.FULL_CONTROL, installed))
        assertEquals(installed, CapabilityPolicy.available(OracleMode.SURVIVAL, installed))
    }
}
