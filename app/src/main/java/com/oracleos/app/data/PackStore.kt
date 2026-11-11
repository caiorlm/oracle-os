package com.oracleos.app.data

import android.content.Context
import com.oracleos.knowledge.AndroidKnowledgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Signed Oracle data packs placed in the app's external files directory
 * (Android/data/com.oracleos.app/files/packs/<pack>/), normally by the Mac install script.
 *
 * A pack is used only after its manifest signature is valid for the trusted key shipped in the
 * APK and every file it needs matches the SHA-256 and size in that manifest. Hashing large files
 * happens once; afterwards a record keyed by path, size and modification time is trusted until
 * the file changes. Nothing in a pack is executed.
 */
class PackStore(
    context: Context,
    private val knowledge: AndroidKnowledgeRepository,
    private val scope: CoroutineScope,
    private val models: ModelStore? = null,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("oracle_pack_verification", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val trustedKey: ByteArray? by lazy {
        runCatching { appContext.assets.open(PUBLIC_KEY_ASSET).use { PackSignature.publicKeyFromPem(String(it.readBytes())) } }
            .getOrNull()
    }
    private val _state = MutableStateFlow(PackState())
    val state: StateFlow<PackState> = _state.asStateFlow()
    private var job: Job? = null

    /** Folder the install script writes to; created so adb push has a target. */
    fun packsDirectory(): File? = appContext.getExternalFilesDir(null)?.let { File(it, "packs").apply { mkdirs() } }

    /** Scans and verifies in the background; safe to call repeatedly. */
    fun refresh(force: Boolean = false) {
        if (job?.isActive == true && !force) return
        job = scope.launch { verifyAll() }
    }

    fun mapFiles(): List<File> = state.value.packs
        .filter { it.signatureValid }
        .flatMap { pack -> pack.files.filter { it.type == "map" && it.verified }.map { it.file } }

    fun kiwixCollections(): List<KiwixCollection> = state.value.kiwix

    /** Verified model files in signed packs, by SHA-256. */
    fun modelFiles(): Map<String, File> = state.value.packs
        .filter { it.signatureValid }
        .flatMap { pack -> pack.files.filter { it.type == "gguf" && it.verified } }
        .associate { it.sha256 to it.file }

    /** Resolves an original file referenced by a pack document, only if it was verified. */
    fun verifiedOriginal(packDirectory: File, relativePath: String): File? {
        val pack = state.value.packs.firstOrNull { it.directory == packDirectory } ?: return null
        return pack.files.firstOrNull { it.relativePath == relativePath && it.verified }?.file
    }

    private suspend fun verifyAll() {
        mutex.withLock { verifyAllLocked() }
    }

    private suspend fun verifyAllLocked() {
        val root = packsDirectory()
        if (root == null) {
            _state.value = PackState(message = "Armazenamento externo do app indisponível.")
            return
        }
        val key = trustedKey
        val directories = root.listFiles { file -> File(file, MANIFEST).isFile }?.sortedBy { it.name }.orEmpty()
        val results = mutableListOf<InstalledPack>()
        _state.value = state.value.copy(busy = directories.isNotEmpty(), message = null)
        for (directory in directories) {
            val pack = withContext(Dispatchers.IO) { inspect(directory, key) }
            results += pack
            _state.value = state.value.copy(packs = results.toList())
            if (!pack.signatureValid) continue
            val verifiedFiles = pack.files.map { file ->
                if (!file.present || file.type == "zim") file else file.copy(verified = verifyFile(file, pack.packId))
            }
            val updated = pack.copy(files = verifiedFiles)
            results[results.lastIndex] = updated
            _state.value = state.value.copy(packs = results.toList(), progress = null)
            activate(updated)
        }
        val kiwix = readKiwixCollections(root)
        _state.value = PackState(packs = results.toList(), kiwix = kiwix, busy = false,
            message = if (directories.isEmpty()) "Nenhum pacote encontrado em ${root.absolutePath}" else null)
    }

    private fun inspect(directory: File, key: ByteArray?): InstalledPack {
        val manifestFile = File(directory, MANIFEST)
        return try {
            val manifestBytes = manifestFile.readBytes()
            val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
            require(manifest.optInt("schemaVersion") == 1) { "Versão de manifesto não suportada" }
            val signatureInfo = manifest.getJSONObject("signature")
            require(signatureInfo.getString("algorithm") == "Ed25519") { "Algoritmo de assinatura não suportado" }
            val signatureFile = safeChild(directory, signatureInfo.getString("file"))
            val signatureValid = key != null && signatureFile.isFile &&
                PackSignature.verify(key, manifestBytes, signatureFile.readBytes())
            val packages = manifest.getJSONArray("packages")
            val files = (0 until packages.length()).map { index ->
                val item = packages.getJSONObject(index)
                val relative = item.getString("path")
                val file = safeChild(directory, relative)
                PackFile(
                    id = item.getString("id"),
                    type = item.getString("type"),
                    relativePath = relative,
                    file = file,
                    sha256 = item.getString("sha256").lowercase(),
                    bytes = item.getLong("bytes"),
                    present = file.isFile && file.length() == item.getLong("bytes"),
                    verified = false,
                )
            }
            InstalledPack(
                packId = manifest.getString("packId"),
                version = manifest.getString("version"),
                directory = directory,
                signatureValid = signatureValid,
                files = files,
                error = when {
                    key == null -> "Chave pública confiável ausente no APK"
                    !signatureValid -> "Assinatura inválida: pacote ignorado"
                    else -> null
                },
            )
        } catch (error: Exception) {
            InstalledPack(directory.name, "?", directory, false, emptyList(), "Manifesto ilegível: ${error.message}")
        }
    }

    private fun verifyFile(file: PackFile, packId: String): Boolean {
        val cacheKey = "v:" + file.file.absolutePath
        val stamp = "${file.bytes}:${file.file.lastModified()}:${file.sha256}"
        if (prefs.getString(cacheKey, null) == stamp) return true
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 20)
        var done = 0L
        var lastReport = 0L
        file.file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                done += read
                if (done - lastReport > 32L * 1024 * 1024) {
                    lastReport = done
                    _state.value = state.value.copy(progress = VerifyProgress(packId, file.relativePath, done, file.bytes))
                }
            }
        }
        val ok = done == file.bytes && digest.digest().toHex() == file.sha256
        if (ok) prefs.edit().putString(cacheKey, stamp).apply() else prefs.edit().remove(cacheKey).apply()
        return ok
    }

    /** Copies a verified library database into private storage and attaches it to the search. */
    private suspend fun activate(pack: InstalledPack) {
        // A signed GGUF with the pinned Lite hash becomes the local model, without the file picker.
        pack.files.filter { it.type == "gguf" && it.verified }.forEach { models?.installVerified(it.file, it.sha256) }
        val index = pack.files.firstOrNull { it.type == "index" && it.verified } ?: return
        val target = File(appContext.filesDir, "library/${pack.packId}-${pack.version}.db")
        val marker = File(target.path + ".sha256")
        withContext(Dispatchers.IO) {
            if (!(target.isFile && marker.isFile && marker.readText() == index.sha256)) {
                target.parentFile?.mkdirs()
                val staging = File(target.path + ".part")
                index.file.inputStream().use { input -> staging.outputStream().use { input.copyTo(it, 1 shl 20) } }
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                marker.writeText(index.sha256)
                // Older versions of the same pack are no longer needed.
                target.parentFile?.listFiles { f -> f.name.startsWith(pack.packId + "-") && f != target && f != marker }
                    ?.forEach { it.delete() }
            }
        }
        knowledge.attachPack(pack.packId, pack.version, target, pack.directory)
    }

    private fun readKiwixCollections(root: File): List<KiwixCollection> {
        val file = File(root, KIWIX_LIST)
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                KiwixCollection(
                    id = item.getString("id"),
                    title = item.optString("title", item.getString("id")),
                    fileName = item.getString("file"),
                    bytes = item.optLong("bytes"),
                    onPhone = item.optBoolean("onPhone"),
                    verifiedOnPhone = item.optBoolean("verifiedOnPhone"),
                    category = item.optString("category").takeIf { it.isNotBlank() },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun safeChild(directory: File, relative: String): File {
        require(relative.isNotBlank() && !relative.startsWith("/") && relative.split('/').none { it == ".." || it == "." || it.isEmpty() }) {
            "Caminho inseguro no manifesto: $relative"
        }
        val file = File(directory, relative)
        require(file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) { "Caminho fora do pacote: $relative" }
        return file
    }

    companion object {
        const val MANIFEST = "pack-manifest.json"
        const val KIWIX_LIST = "kiwix-collections.json"
        const val PUBLIC_KEY_ASSET = "keys/oracle-dev-public.pem"
    }
}

data class PackFile(
    val id: String,
    val type: String,
    val relativePath: String,
    val file: File,
    val sha256: String,
    val bytes: Long,
    val present: Boolean,
    val verified: Boolean,
)

data class InstalledPack(
    val packId: String,
    val version: String,
    val directory: File,
    val signatureValid: Boolean,
    val files: List<PackFile>,
    val error: String?,
) {
    val ready: Boolean get() = signatureValid && files.filter { it.present && it.type != "zim" }.all { it.verified }
    val totalBytes: Long get() = files.sumOf { it.bytes }
}

data class KiwixCollection(
    val id: String,
    val title: String,
    val fileName: String,
    val bytes: Long,
    val onPhone: Boolean,
    val verifiedOnPhone: Boolean,
    val category: String?,
)

data class VerifyProgress(val packId: String, val path: String, val done: Long, val total: Long)

data class PackState(
    val packs: List<InstalledPack> = emptyList(),
    val kiwix: List<KiwixCollection> = emptyList(),
    val busy: Boolean = false,
    val progress: VerifyProgress? = null,
    val message: String? = null,
)

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
