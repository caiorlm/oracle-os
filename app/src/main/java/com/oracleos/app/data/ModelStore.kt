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

    companion object {
        const val LITE_FILENAME = "Qwen3.5-0.8B-Q4_K_M.gguf"
        const val LITE_BYTES = 532_517_120L
        const val LITE_SHA256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
        private const val MIN_FREE_AFTER_IMPORT = 256L * 1024L * 1024L
    }
}
