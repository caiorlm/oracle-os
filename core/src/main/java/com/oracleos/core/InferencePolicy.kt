package com.oracleos.core

/** A failed budget check must not prevent direct library, map or emergency access. */
enum class InferenceBlockReason {
    SURVIVAL_MODE,
    INVALID_ESTIMATE,
    LOW_BATTERY,
    HIGH_THERMAL_LOAD,
    INSUFFICIENT_MEMORY,
}

data class InferenceBudgetDecision(
    val canLoad: Boolean,
    val reason: InferenceBlockReason?,
    val requiredAvailableRamMb: Long,
)

/** Conservative preflight; a native engine must still handle load failure gracefully. */
class InferenceBudgetPolicy(
    private val minimumBatteryPercent: Int = 15,
    private val severeThermalStatus: Int = 3,
    private val minimumReserveMb: Long = 384,
    private val lowRamReserveMb: Long = 512,
) {
    fun evaluate(
        mode: OracleMode,
        profile: HardwareProfile,
        estimatedModelRamMb: Long,
        estimatedContextRamMb: Long,
    ): InferenceBudgetDecision {
        if (mode == OracleMode.SURVIVAL) {
            return InferenceBudgetDecision(false, InferenceBlockReason.SURVIVAL_MODE, 0)
        }
        if (estimatedModelRamMb <= 0 || estimatedContextRamMb < 0) {
            return InferenceBudgetDecision(false, InferenceBlockReason.INVALID_ESTIMATE, 0)
        }

        val reserve = maxOf(
            if (profile.isLowRamDevice) lowRamReserveMb else minimumReserveMb,
            profile.availableRamMb.coerceAtLeast(0) / 4,
        )
        val required = safeAdd(safeAdd(estimatedModelRamMb, estimatedContextRamMb), reserve)

        if (!profile.isCharging && profile.batteryPercent in 0 until minimumBatteryPercent) {
            return InferenceBudgetDecision(false, InferenceBlockReason.LOW_BATTERY, required)
        }
        if (profile.thermalStatus >= severeThermalStatus) {
            return InferenceBudgetDecision(false, InferenceBlockReason.HIGH_THERMAL_LOAD, required)
        }
        if (profile.availableRamMb < required) {
            return InferenceBudgetDecision(false, InferenceBlockReason.INSUFFICIENT_MEMORY, required)
        }
        return InferenceBudgetDecision(true, null, required)
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}

enum class InferenceRoute {
    ON_DEVICE,
    LOCAL_SERVER,
    KNOWLEDGE_ONLY,
    UNAVAILABLE,
}

/**
 * Picks an available route after checking native memory and an explicitly paired local server.
 * A server must be both paired and healthy; this policy never initiates pairing or network IO.
 */
object InferenceSelectionPolicy {
    fun select(
        mode: OracleMode,
        installed: Set<Capability>,
        localBudget: InferenceBudgetDecision,
        serverPaired: Boolean,
        serverHealthy: Boolean,
        preferredProvider: InferenceProvider = InferenceProvider.ON_DEVICE,
    ): InferenceRoute {
        val active = CapabilityPolicy.available(mode, installed)
        val local = Capability.LOCAL_LLM in active && localBudget.canLoad
        val remote = Capability.REMOTE_LLM in active && serverPaired && serverHealthy

        if (preferredProvider == InferenceProvider.LOCAL_SERVER && remote) {
            return InferenceRoute.LOCAL_SERVER
        }
        if (local) return InferenceRoute.ON_DEVICE
        if (remote) return InferenceRoute.LOCAL_SERVER
        if (Capability.DOCUMENT_SEARCH in active) return InferenceRoute.KNOWLEDGE_ONLY
        return InferenceRoute.UNAVAILABLE
    }
}
