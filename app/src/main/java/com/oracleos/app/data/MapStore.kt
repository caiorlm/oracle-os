package com.oracleos.app.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Imports the pinned London PMTiles archive selected through Android's document picker. */
class MapStore(context: Context) {
    private val appContext = context.applicationContext
    private val mapDir = File(appContext.filesDir, "maps")
    private val destination get() = File(mapDir, "greater-london.pmtiles")

    fun londonPath(): String? = destination.takeIf {
        it.isFile && it.length() == LONDON_BYTES && hasPmtilesHeader(it)
    }?.absolutePath

    suspend fun importLondon(uri: Uri): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Selecione o pacote PMTiles pelo Android." }
        require(LONDON_BYTES > 0 && LONDON_SHA256.length == 64) { "Manifesto do mapa não configurado." }
        mapDir.mkdirs()
        require(StatFs(mapDir.absolutePath).availableBytes >= LONDON_BYTES + MIN_FREE_AFTER_IMPORT) {
            "Armazenamento insuficiente para importar o mapa."
        }
        val staging = File.createTempFile("london-", ".part", mapDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Não foi possível ler o pacote selecionado.")
            input.use { source ->
                staging.outputStream().buffered().use { target ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= LONDON_BYTES) { "Pacote de mapa maior que o esperado." }
                        digest.update(buffer, 0, read)
                        target.write(buffer, 0, read)
                    }
                }
            }
            require(count == LONDON_BYTES) { "Tamanho incorreto do pacote de Londres: $count bytes." }
            require(hasPmtilesHeader(staging)) { "Arquivo não é PMTiles v3." }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual == LONDON_SHA256) { "SHA-256 do mapa não corresponde ao pacote publicado." }
            Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            destination.absolutePath
        } finally {
            staging.delete()
        }
    }

    private fun hasPmtilesHeader(file: File): Boolean = DataInputStream(file.inputStream()).use { input ->
        val header = ByteArray(8)
        input.readFully(header)
        header.contentEquals(PMTILES_V3)
    }

    companion object {
        const val LONDON_BYTES = 68_128_278L
        const val LONDON_SHA256 = "8283946741789a09547afde134c049ae3412647faa26607fca149fe37fb148e2"
        private val PMTILES_V3 = byteArrayOf('P'.code.toByte(), 'M'.code.toByte(), 'T'.code.toByte(),
            'i'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 3)
        private const val MIN_FREE_AFTER_IMPORT = 128L * 1024L * 1024L
    }
}
