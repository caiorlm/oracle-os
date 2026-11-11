package com.oracleos.app.feature.knowledge

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oracleos.app.ui.OracleBackground
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline viewer for an original PDF from a verified library pack, opened at the cited page.
 * Uses the platform PdfRenderer; no other app or network is involved.
 */
class PdfPageActivity : ComponentActivity() {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val lock = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_PATH)?.let(::File)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: file?.name ?: "Documento"
        val opened = try {
            requireNotNull(file).takeIf { it.isFile } ?: error("missing")
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor!!)
            true
        } catch (_: Exception) {
            false
        }
        if (!opened) {
            Toast.makeText(this, "Não foi possível abrir o PDF original.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val pageCount = renderer!!.pageCount
        val start = (intent.getIntExtra(EXTRA_PAGE, 1) - 1).coerceIn(0, pageCount - 1)
        val targetWidth = (resources.displayMetrics.widthPixels * 2).coerceIn(800, 2600)
        setContent {
            OracleTheme {
                PdfViewer(title, pageCount, start, onClose = ::finish) { index -> render(index, targetWidth) }
            }
        }
    }

    private suspend fun render(index: Int, width: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            val current = renderer ?: return@withContext null
            current.openPage(index).use { page ->
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    override fun onDestroy() {
        renderer?.close()
        descriptor?.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_PAGE = "page"
        private const val EXTRA_TITLE = "title"

        fun open(context: Context, file: File, page: Int?, title: String) {
            context.startActivity(Intent(context, PdfPageActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_PAGE, page ?: 1)
                .putExtra(EXTRA_TITLE, title))
        }
    }
}

@Composable
private fun PdfViewer(
    title: String,
    pageCount: Int,
    startIndex: Int,
    onClose: () -> Unit,
    render: suspend (Int) -> Bitmap?,
) {
    var index by remember { mutableIntStateOf(startIndex) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(index) {
        bitmap = null
        scale = 1f
        offset = Offset.Zero
        bitmap = render(index)
    }
    Column(Modifier.fillMaxSize().background(OracleBackground).statusBarsPadding().navigationBarsPadding()) {
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1A2129))
                .pointerInput(index) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val current = bitmap
            if (current == null) {
                CircularProgressIndicator()
            } else {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = "Página ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y,
                    ),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OracleOutlineButton("‹", { if (index > 0) index-- })
            Text("Página ${index + 1} de $pageCount", style = MaterialTheme.typography.bodyMedium, color = OracleTextMuted,
                modifier = Modifier.weight(1f))
            OracleOutlineButton("›", { if (index < pageCount - 1) index++ })
            OracleOutlineButton("Fechar", onClose)
        }
    }
}
