package com.oracleos.core

import kotlinx.coroutines.flow.Flow

/** User-selected operating mode. None of these modes grants Android privileges. */
enum class OracleMode {
    STANDARD,
    FULL_CONTROL,
    SURVIVAL,
}

enum class InferenceProvider {
    ON_DEVICE,
    LOCAL_SERVER,
}

/** A feature that has actually been installed and can be offered on this device. */
enum class Capability {
    EMERGENCY_DIALER,
    SMS_COMPOSER,
    OFFLINE_MAPS,
    GPS,
    COMPASS,
    DOCUMENT_SEARCH,
    DEVICE_STATUS,
    FLASHLIGHT,
    LOCAL_LLM,
    REMOTE_LLM,
    CAMERA,
    OCR,
    VOICE,
    SSD_PACKS,
    APP_LAUNCHER,
}

/** Values represent the current device state, not advertised model compatibility. */
data class HardwareProfile(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val availableStorageMb: Long,
    /** -1 when the battery level cannot be read. */
    val batteryPercent: Int,
    val isCharging: Boolean,
    /** Android PowerManager thermal status, or [THERMAL_STATUS_UNKNOWN]. */
    val thermalStatus: Int,
    val isLowRamDevice: Boolean,
) {
    companion object {
        const val THERMAL_STATUS_UNKNOWN: Int = -1
    }
}

data class SourceReference(
    val documentId: String,
    val title: String,
    val page: Int?,
    val section: String?,
    val excerpt: String,
)

data class OracleAnswer(
    val text: String,
    val sources: List<SourceReference>,
    val provider: InferenceProvider?,
    val modelId: String?,
    val warnings: List<String>,
)

interface LlmEngine {
    suspend fun load(modelId: String)
    fun generate(prompt: String): Flow<String>
    suspend fun unload()
}

interface KnowledgeEngine {
    suspend fun search(query: String, maxResults: Int = 5): List<SourceReference>
}

interface OracleController {
    suspend fun answer(question: String): OracleAnswer
    suspend fun setMode(mode: OracleMode)
}

/** Implementations can refresh this immediately before selecting a model. */
fun interface HardwareProfiler {
    fun snapshot(): HardwareProfile
}
