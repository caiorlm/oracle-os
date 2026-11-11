package com.oracleos.app

import android.app.Application
import android.content.Intent
import com.oracleos.app.data.ModelStore
import com.oracleos.app.data.MapStore
import com.oracleos.app.data.PackStore
import com.oracleos.app.location.OracleLocationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    /** Long-lived work such as verifying data packs; never tied to one screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val packStore by lazy { PackStore(this, knowledgeEngine, appScope, modelStore) }
    val mapStore by lazy { MapStore(this) { packStore.mapFiles() } }
    val locationTracker by lazy { OracleLocationTracker(this) }
    val llmEngine by lazy {
        LlamaCppEngine(
            context = this,
            modelPath = { id -> modelStore.pathFor(id) },
            hardwareProfiler = hardwareProfiler,
            currentMode = ::currentMode,
            config = InferenceConfig(contextTokens = 1024, maxOutputTokens = 96, threads = 2),
        )
    }
    val controller by lazy { OracleControllerImpl(knowledgeEngine, this) }
    private val guideMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        com.oracleos.app.ui.Lang.init(this)
        com.oracleos.app.ui.PowerSaver.init(this)
        // Attaches already-verified packs quickly; new or changed files are hashed in the background.
        packStore.refresh()
    }

    /** Opens the Kiwix reader for the ZIM collections copied to the phone. */
    /**
     * Opens Kiwix, directly on [fileName] in /sdcard/Kiwix when given. Kiwix reads that folder itself
     * (it needs "All files access" once), so a file:// link is handed to it; the strict-mode check
     * against file links is relaxed only for this one call.
     */
    fun openKiwix(fileName: String? = null): Boolean {
        val pkg = KIWIX_PACKAGES.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null } ?: return false
        // Collections kept in Oracle's own storage are shared with a read-only content link: Kiwix
        // opens them through a file descriptor and needs no storage permission.
        val own = fileName?.let { name -> getExternalFilesDir(null)?.let { java.io.File(it, "kiwix/$name") } }
        if (own != null && own.isFile) {
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", own)
                val view = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/octet-stream")
                    .setPackage(pkg)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                if (view.resolveActivity(packageManager) != null) {
                    startActivity(view)
                    return true
                }
            } catch (_: Exception) {
                // Fall through to the shared Kiwix folder.
            }
        }
        if (fileName != null) {
            val file = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Kiwix/$fileName")
            val previous = android.os.StrictMode.getVmPolicy()
            try {
                android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.Builder().build())
                for (type in listOf("application/octet-stream", "*/*")) {
                    val view = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(android.net.Uri.fromFile(file), type)
                        .setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (view.resolveActivity(packageManager) != null) {
                        startActivity(view)
                        return true
                    }
                }
            } catch (_: Exception) {
                // Fall back to opening the Kiwix library screen.
            } finally {
                android.os.StrictMode.setVmPolicy(previous)
            }
        }
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Opens Android's "All files access" switch for Kiwix, so it can read /sdcard/Kiwix without asking again. */
    fun openKiwixFileAccess(): Boolean {
        val pkg = KIWIX_PACKAGES.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null } ?: return false
        val uri = android.net.Uri.parse("package:$pkg")
        val intents = listOf(
            Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri),
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri),
        )
        for (intent in intents) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) { }
        }
        return false
    }

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
        val KIWIX_PACKAGES = listOf("org.kiwix.kiwixmobile", "org.kiwix.kiwixmobile.standalone")
    }
}
