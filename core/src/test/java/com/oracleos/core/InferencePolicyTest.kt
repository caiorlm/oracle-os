package com.oracleos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferencePolicyTest {
    private val profile = HardwareProfile(
        totalRamMb = 4096,
        availableRamMb = 1536,
        availableStorageMb = 2048,
        batteryPercent = 65,
        isCharging = false,
        thermalStatus = 0,
        isLowRamDevice = true,
    )

    @Test
    fun smallModelFitsConservativeFourGigabyteBudget() {
        val decision = InferenceBudgetPolicy().evaluate(OracleMode.STANDARD, profile, 600, 160)

        assertTrue(decision.canLoad)
        assertEquals(1272L, decision.requiredAvailableRamMb)
    }

    @Test
    fun lowMemoryBatteryThermalAndSurvivalBlockModel() {
        val policy = InferenceBudgetPolicy()
        assertEquals(
            InferenceBlockReason.INSUFFICIENT_MEMORY,
            policy.evaluate(OracleMode.STANDARD, profile.copy(availableRamMb = 900), 600, 160).reason,
        )
        assertEquals(
            InferenceBlockReason.LOW_BATTERY,
            policy.evaluate(OracleMode.STANDARD, profile.copy(batteryPercent = 10), 600, 160).reason,
        )
        assertEquals(
            InferenceBlockReason.HIGH_THERMAL_LOAD,
            policy.evaluate(OracleMode.STANDARD, profile.copy(thermalStatus = 3), 600, 160).reason,
        )
        assertEquals(
            InferenceBlockReason.SURVIVAL_MODE,
            policy.evaluate(OracleMode.SURVIVAL, profile, 600, 160).reason,
        )
    }

    @Test
    fun routeFallsBackToDocumentSearchWhenEnginesUnavailable() {
        val active = setOf(Capability.DOCUMENT_SEARCH, Capability.LOCAL_LLM, Capability.REMOTE_LLM)
        val blocked = InferenceBudgetDecision(false, InferenceBlockReason.INSUFFICIENT_MEMORY, 1272)

        assertEquals(
            InferenceRoute.KNOWLEDGE_ONLY,
            InferenceSelectionPolicy.select(OracleMode.STANDARD, active, blocked, false, true),
        )
        assertEquals(
            InferenceRoute.LOCAL_SERVER,
            InferenceSelectionPolicy.select(OracleMode.STANDARD, active, blocked, true, true),
        )
        assertEquals(
            InferenceRoute.KNOWLEDGE_ONLY,
            InferenceSelectionPolicy.select(OracleMode.SURVIVAL, active, blocked, true, true),
        )
    }

    @Test
    fun healthyPairedServerOnlyWinsWhenPreferred() {
        val active = setOf(Capability.DOCUMENT_SEARCH, Capability.LOCAL_LLM, Capability.REMOTE_LLM)
        val allowed = InferenceBudgetDecision(true, null, 1272)

        assertEquals(
            InferenceRoute.ON_DEVICE,
            InferenceSelectionPolicy.select(OracleMode.STANDARD, active, allowed, true, true),
        )
        assertEquals(
            InferenceRoute.LOCAL_SERVER,
            InferenceSelectionPolicy.select(
                OracleMode.STANDARD,
                active,
                allowed,
                true,
                true,
                InferenceProvider.LOCAL_SERVER,
            ),
        )
        assertFalse(InferenceBudgetPolicy().evaluate(OracleMode.STANDARD, profile, -1, 0).canLoad)
    }
}
