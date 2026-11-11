package com.oracleos.app.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Imports only the pinned Lite model after checking size and SHA-256. */
class ModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "models")
    private val destination get() = File(modelDir, LITE_FILENAME)

    fun liteModelPath(): String? = destination.takeIf { it.isFile && it.length() == LITE_BYTES }?.absolutePath

    suspend fun verifiedLiteModelPath(): String? = withContext(Dispatchers.IO) {
        val model = destination.takeIf { it.isFile && it.length() == LITE_BYTES } ?: return@withContext null
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().buffered().use { source ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        model.takeIf { actual == LITE_SHA256 }?.absolutePath
    }

    suspend fun importLite(uri: Uri): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Selecione um arquivo GGUF pelo Android." }
        modelDir.mkdirs()
        val freeBytes = StatFs(modelDir.absolutePath).availableBytes
        require(freeBytes >= LITE_BYTES + MIN_FREE_AFTER_IMPORT) { "Armazenamento insuficiente para importar o modelo Lite." }
        val staging = File.createTempFile("oracle-lite-", ".part", modelDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Não foi possível ler o arquivo selecionado.")
            input.use { source ->
                staging.outputStream().buffered().use { target ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= LITE_BYTES) { "O arquivo selecionado é maior que o modelo Lite esperado." }
                        digest.update(buffer, 0, read)
                        target.write(buffer, 0, read)
                    }
                    target.flush()
                }
            }
            require(count == LITE_BYTES) { "Tamanho do modelo Lite incorreto: $count bytes." }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualHash == LITE_SHA256) { "SHA-256 do modelo Lite não corresponde ao arquivo publicado." }
            Files.move(
                staging.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            destination.absolutePath
        } finally {
            staging.delete()
        }
    }

    /** Installs the Lite model from a signed pack file that PackStore has already hashed. */
    suspend fun installVerified(source: File, sha256: String) = withContext(Dispatchers.IO) {
        if (!sha256.equals(LITE_SHA256, ignoreCase = true) || source.length() != LITE_BYTES) return@withContext
        if (liteModelPath() != null) return@withContext
        modelDir.mkdirs()
        val staging = File(modelDir, "$LITE_FILENAME.part")
        source.inputStream().use { input -> staging.outputStream().use { input.copyTo(it, 1 shl 20) } }
        Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    // ---- Several on-device models (chosen in System). Each is pinned by size and SHA-256. ----

    private val prefs = appContext.getSharedPreferences("oracle_models", Context.MODE_PRIVATE)

    /** The model chosen in System; the Lite model by default. */
    var selectedId: String
        get() = prefs.getString("selected", MODELS.first().id) ?: MODELS.first().id
        set(value) { prefs.edit().putString("selected", value).apply() }

    fun selected(): ModelSpec = MODELS.firstOrNull { it.id == selectedId } ?: MODELS.first()

    /** Path of an installed model, by engine id (the Lite id keeps working). */
    fun pathFor(engineId: String): String? {
        val spec = if (engineId == com.oracleos.inference.LlamaCppEngine.LITE_MODEL_ID) MODELS.first()
            else MODELS.firstOrNull { it.id == engineId } ?: return null
        return File(modelDir, spec.fileName).takeIf { it.isFile && it.length() == spec.bytes }?.absolutePath
    }

    fun isInstalled(spec: ModelSpec): Boolean = pathFor(spec.id) != null

    /**
     * Copies a model from a signed pack (already hashed by PackStore) into private storage, checking
     * the pinned SHA-256 again. Other large models are removed first, so only one big copy exists;
     * the Lite model is always kept.
     */
    suspend fun install(spec: ModelSpec, source: File, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        require(source.isFile && source.length() == spec.bytes) { "Modelo incompleto no telefone." }
        if (isInstalled(spec)) return@withContext
        modelDir.mkdirs()
        MODELS.drop(1).filter { it.id != spec.id }.forEach { File(modelDir, it.fileName).delete() }
        val free = StatFs(modelDir.absolutePath).availableBytes
        require(free >= spec.bytes + MIN_FREE_AFTER_IMPORT) { "Armazenamento insuficiente para instalar o modelo." }
        val staging = File(modelDir, spec.fileName + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        source.inputStream().use { input ->
            staging.outputStream().buffered(1 shl 20).use { out ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                    done += read
                    onProgress(done.toFloat() / spec.bytes)
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (hash != spec.sha256) { staging.delete(); error("SHA-256 do modelo não corresponde.") }
        Files.move(staging.toPath(), File(modelDir, spec.fileName).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        /** Five Qwen 3.5 models, smallest first. RAM is what the phone needs in total to run each. */
        val MODELS = listOf(
            ModelSpec("qwen35-08b-q4km", "Qwen 3.5 0.8B · Lite", "Qwen3.5-0.8B-Q4_K_M.gguf", 532_517_120L,
                "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517", 3072),
            ModelSpec("qwen35-2b-q4km", "Qwen 3.5 2B", "Qwen3.5-2B-Q4_K_M.gguf", 1_280_835_840L,
                "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223", 3072),
            ModelSpec("qwen35-4b-q4km", "Qwen 3.5 4B", "Qwen3.5-4B-Q4_K_M.gguf", 2_740_937_888L,
                "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4", 4608),
            ModelSpec("qwen35-9b-q3km", "Qwen 3.5 9B · compacto", "Qwen3.5-9B-Q3_K_M.gguf", 4_673_643_744L,
                "8fed90306e4f019e2bf35f3766470b7bc59ea1a9dae00f5ceb20b43cb5514393", 6656),
            ModelSpec("qwen35-9b-q4km", "Qwen 3.5 9B · máximo", "Qwen3.5-9B-Q4_K_M.gguf", 5_680_522_464L,
                "03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8", 7680),
        )
        const val LITE_FILENAME = "Qwen3.5-0.8B-Q4_K_M.gguf"
        const val LITE_BYTES = 532_517_120L
        const val LITE_SHA256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
        private const val MIN_FREE_AFTER_IMPORT = 256L * 1024L * 1024L
    }
}

data class ModelSpec(val id: String, val label: String, val fileName: String, val bytes: Long, val sha256: String, val minRamMb: Int)
