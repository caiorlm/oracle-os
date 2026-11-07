package com.oracleos.app

import android.app.Application
import com.oracleos.app.data.ModelStore
import com.oracleos.app.data.MapStore
import com.oracleos.core.OracleMode
import com.oracleos.core.android.AndroidHardwareProfiler
import com.oracleos.knowledge.AndroidKnowledgeRepository
import com.oracleos.knowledge.DocumentFormat
import com.oracleos.knowledge.DocumentImportMetadata
import com.oracleos.inference.InferenceConfig
import com.oracleos.inference.LlamaCppEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/** Small application composition root. Heavy resources are opened only on demand. */
class OracleApplication : Application() {
    val hardwareProfiler by lazy { AndroidHardwareProfiler(this) }
    val knowledgeEngine by lazy { AndroidKnowledgeRepository(this) }
    val modelStore by lazy { ModelStore(this) }
    val mapStore by lazy { MapStore(this) }
    val llmEngine by lazy {
        LlamaCppEngine(
            context = this,
            modelPath = { id -> if (id == LlamaCppEngine.LITE_MODEL_ID) modelStore.liteModelPath() else null },
            hardwareProfiler = hardwareProfiler,
            currentMode = ::currentMode,
            config = InferenceConfig(contextTokens = 1024, maxOutputTokens = 96, threads = 2),
        )
    }
    val controller by lazy { OracleControllerImpl(knowledgeEngine, this) }
    private val guideMutex = Mutex()

    /** Ships a small, inspectable guide so search has a real source on a fresh offline install. */
    suspend fun ensureCoreGuide() = guideMutex.withLock {
        val guide = assets.open("oracle-guide.md").use { it.readBytes() }
        val guideHash = MessageDigest.getInstance("SHA-256").digest(guide).joinToString("") { "%02x".format(it) }
        if (knowledgeEngine.getDocument(CORE_GUIDE_ID)?.summary?.sha256 != guideHash) {
            knowledgeEngine.importStream(
                ByteArrayInputStream(guide),
                DocumentImportMetadata(
                    title = "Guia básico do Oracle OS",
                    format = DocumentFormat.MARKDOWN,
                    documentId = CORE_GUIDE_ID,
                    origin = "Incluído no APK Oracle OS 0.1.0",
                ),
            )
        }
    }

    fun currentMode(): OracleMode {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(MODE_KEY, null)
        return OracleMode.entries.firstOrNull { it.name == saved } ?: OracleMode.STANDARD
    }

    fun saveMode(mode: OracleMode) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(MODE_KEY, mode.name).apply()
    }

    private companion object {
        const val PREFS = "oracle_settings"
        const val MODE_KEY = "oracle_mode"
        const val CORE_GUIDE_ID = "oracle-core-guide"
    }
}
