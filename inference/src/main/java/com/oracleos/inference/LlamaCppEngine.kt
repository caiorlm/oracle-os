package com.oracleos.inference

import android.content.Context
import com.oracleos.core.HardwareProfiler
import com.oracleos.core.InferenceBudgetPolicy
import com.oracleos.core.InferenceBlockReason
import com.oracleos.core.LlmEngine
import com.oracleos.core.OracleMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ceil

/**
 * CPU-only llama.cpp adapter. The app must import a GGUF into private storage through
 * a user-selected Android document before [load] is called. No model is bundled or fetched.
 *
 * One native model can be active per process. Calls from multiple instances are serialized.
 */
class LlamaCppEngine(
    context: Context,
    private val modelPath: (modelId: String) -> String?,
    private val hardwareProfiler: HardwareProfiler,
    private val currentMode: () -> OracleMode,
    private val config: InferenceConfig = InferenceConfig(),
    private val budgetPolicy: InferenceBudgetPolicy = InferenceBudgetPolicy(),
) : LlmEngine {
    private val privateFilesDir = context.applicationContext.filesDir.canonicalFile
    private var loadedModelId: String? = null
    @Volatile private var activeGenerationId: Long = 0L

    private external fun nativeLoad(path: String, contextTokens: Int, threads: Int)
    private external fun nativeBegin(promptUtf8: ByteArray, maxTokens: Int): Long
    private external fun nativeNext(generationId: Long): ByteArray?
    private external fun nativeCancel(generationId: Long)
    private external fun nativeEnd(generationId: Long)
    private external fun nativeUnload()

    override suspend fun load(modelId: String) {
        require(modelId.isNotBlank()) { "Model id is blank" }
        val path = modelPath(modelId) ?: throw IllegalStateException("Model has not been imported: $modelId")
        val model = File(path).canonicalFile
        require(model.isFile && model.canRead() && model.extension.equals("gguf", ignoreCase = true)) {
            "Imported model is not a readable GGUF file"
        }
        require(model.toPath().startsWith(privateFilesDir.toPath())) {
            "Model must be imported into app-private storage"
        }
        val header = ByteArray(4)
        require(model.inputStream().use { it.read(header) == 4 } && header.contentEquals(GGUF_MAGIC)) {
            "Imported model has no GGUF header"
        }
        val modelMb = ceil(model.length().toDouble() / MIB).toLong()
        // Measured on a 4 GB ARM64 device: model mmap plus context raised process PSS
        // by about 611 MiB for a 508 MiB GGUF. Keep a separate 384 MiB system reserve.
        val estimatedModelRamMb = modelMb + 96L
        val estimatedContextRamMb = 96L * config.contextTokens / 1024L
        val budget = budgetPolicy.evaluate(
            currentMode(), hardwareProfiler.snapshot(), estimatedModelRamMb, estimatedContextRamMb,
        )
        check(budget.canLoad) {
            val reason = when (budget.reason) {
                InferenceBlockReason.SURVIVAL_MODE -> "modo Sobrevivência ativo"
                InferenceBlockReason.LOW_BATTERY -> "bateria baixa"
                InferenceBlockReason.HIGH_THERMAL_LOAD -> "aquecimento elevado"
                InferenceBlockReason.INSUFFICIENT_MEMORY -> "memória insuficiente"
                else -> "recurso indisponível"
            }
            "IA local indisponível: $reason. Necessários cerca de ${budget.requiredAvailableRamMb} MB de RAM livre."
        }

        withContext(Dispatchers.IO) {
            nativeMutex.withLock {
                check(owner == null || owner === this@LlamaCppEngine) { "Another LlamaCppEngine owns the native model" }
                if (loadedModelId == modelId) return@withLock
                ensureLibraryLoaded()
                if (owner === this@LlamaCppEngine) {
                    nativeUnload()
                    owner = null
                    loadedModelId = null
                }
                nativeLoad(model.absolutePath, config.contextTokens, config.threads)
                owner = this@LlamaCppEngine
                loadedModelId = modelId
            }
        }
    }

    override fun generate(prompt: String): Flow<String> = callbackFlow {
        require(prompt.isNotBlank()) { "Prompt is blank" }
        require(prompt.toByteArray(Charsets.UTF_8).size <= MAX_PROMPT_BYTES) { "Prompt exceeds 16 KiB" }
        val worker = launch(Dispatchers.IO) {
            try {
                nativeMutex.withLock {
                    check(owner === this@LlamaCppEngine && loadedModelId != null) { "No model is loaded" }
                    val id = nativeBegin(prompt.toByteArray(Charsets.UTF_8), config.maxOutputTokens)
                    check(id > 0) { "Could not start generation" }
                    activeGenerationId = id
                    val utf8 = Utf8Chunks()
                    try {
                        while (isActive) {
                            val piece = nativeNext(id) ?: break
                            val text = utf8.append(piece)
                            if (text.isNotEmpty() && trySend(text).isFailure) break
                        }
                        val tail = utf8.finish()
                        if (isActive && tail.isNotEmpty()) trySend(tail)
                    } finally {
                        nativeEnd(id)
                        activeGenerationId = 0L
                    }
                }
                close()
            } catch (cancelled: CancellationException) {
                close()
            } catch (error: Throwable) {
                close(error)
            }
        }
        awaitClose {
            cancel()
            worker.cancel()
        }
    }

    /** Interrupts an active prompt or token decode. The model remains loaded. */
    fun cancel() {
        val id = activeGenerationId
        if (id != 0L) nativeCancel(id)
    }

    override suspend fun unload() {
        cancel()
        withContext(Dispatchers.IO) {
            nativeMutex.withLock {
                if (owner === this@LlamaCppEngine) {
                    nativeUnload()
                    owner = null
                    loadedModelId = null
                }
            }
        }
    }

    private fun ensureLibraryLoaded() {
        nativeLibraryLoaded // lazy, synchronized once per process
    }

    companion object {
        const val LITE_MODEL_ID = "oracle-lite"
        private const val MIB = 1024.0 * 1024.0
        private const val MAX_PROMPT_BYTES = 16 * 1024
        private val GGUF_MAGIC = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())
        private val nativeMutex = Mutex()
        @Volatile private var owner: LlamaCppEngine? = null
        private val nativeLibraryLoaded: Unit by lazy { System.loadLibrary("oracle_inference") }
    }
}

data class InferenceConfig(
    val contextTokens: Int = 2048,
    val maxOutputTokens: Int = 256,
    val threads: Int = Runtime.getRuntime().availableProcessors().minus(2).coerceIn(1, 4),
) {
    init {
        require(contextTokens in 512..8192)
        require(maxOutputTokens in 1..1024 && maxOutputTokens < contextTokens)
        require(threads in 1..8)
    }
}

/** Keeps incomplete multi-byte UTF-8 characters between llama tokens. */
private class Utf8Chunks {
    private val pending = ByteArrayOutputStream(8)

    fun append(piece: ByteArray): String {
        pending.write(piece)
        val bytes = pending.toByteArray()
        val complete = completePrefix(bytes)
        if (complete == 0) return ""
        val result = String(bytes, 0, complete, Charsets.UTF_8)
        pending.reset()
        pending.write(bytes, complete, bytes.size - complete)
        return result
    }

    fun finish(): String = String(pending.toByteArray(), Charsets.UTF_8)

    private fun completePrefix(bytes: ByteArray): Int {
        var offset = 0
        while (offset < bytes.size) {
            val first = bytes[offset].toInt() and 0xff
            val width = when {
                first < 0x80 -> 1
                first in 0xc2..0xdf -> 2
                first in 0xe0..0xef -> 3
                first in 0xf0..0xf4 -> 4
                else -> 1 // Invalid lead byte becomes a replacement character.
            }
            if (offset + width > bytes.size) break
            if (width > 1 && (offset + 1 until offset + width).any {
                    (bytes[it].toInt() and 0xc0) != 0x80
                }) {
                offset++
            } else {
                offset += width
            }
        }
        return offset
    }
}
