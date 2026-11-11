package com.oracleos.app.feature.plants

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.oracleos.app.OracleApplication
import com.oracleos.app.feature.zim.ZimEngine
import com.oracleos.app.feature.zim.ZimReaderActivity
import com.oracleos.app.ui.Lang
import com.oracleos.app.ui.LabelValue
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleDanger
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleLoading
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleTheme
import com.oracleos.app.ui.OracleWarning
import com.oracleos.app.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

data class PlantGuess(val scientific: String, val probability: Float)

/**
 * Google AIY Vision "plants V1" classifier (2 102 species, trained on iNaturalist photos; Apache-2.0),
 * the same model used by the open-source nature-id project. Quantized, about 5 MB, runs on the phone.
 * The code also accepts a float NCHW/NHWC model (e.g. PlantNet-300K) if one is placed in assets.
 */
object PlantClassifier {
    private const val MODEL = "plants/plants.tflite"
    private const val LABELS = "plants/labels.txt"
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var labels: List<String> = emptyList()

    fun available(context: Context): Boolean =
        runCatching { context.assets.openFd(MODEL).close(); true }.getOrDefault(false)

    @Synchronized
    private fun load(context: Context): Interpreter {
        interpreter?.let { return it }
        val fd = context.assets.openFd(MODEL)
        val buffer = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        labels = context.assets.open(LABELS).bufferedReader().readLines()
        return Interpreter(buffer, Interpreter.Options().setNumThreads(4)).also { interpreter = it }
    }

    fun classify(context: Context, photo: Bitmap, top: Int = 5): List<PlantGuess> {
        val model = load(context)
        val inTensor = model.getInputTensor(0)
        val shape = inTensor.shape()                       // [1,224,224,3] or [1,3,224,224]
        val nchw = shape.size == 4 && shape[1] == 3
        val size = if (nchw) shape[2] else shape[1]
        val quantIn = inTensor.dataType() == org.tensorflow.lite.DataType.UINT8
        val side = minOf(photo.width, photo.height)
        val square = Bitmap.createBitmap(photo, (photo.width - side) / 2, (photo.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val input = ByteBuffer.allocateDirect((if (quantIn) 1 else 4) * 3 * size * size).order(ByteOrder.nativeOrder())
        fun channel(p: Int, c: Int) = (p shr (16 - c * 8)) and 0xFF
        if (quantIn) {
            for (p in pixels) for (c in 0 until 3) input.put(channel(p, c).toByte())
        } else if (nchw) {
            for (c in 0 until 3) for (p in pixels) input.putFloat((channel(p, c) / 255f - MEAN[c]) / STD[c])
        } else {
            for (p in pixels) for (c in 0 until 3) input.putFloat((channel(p, c) / 255f - MEAN[c]) / STD[c])
        }
        input.rewind()
        val outTensor = model.getOutputTensor(0)
        val classes = outTensor.shape().last()
        val scores: DoubleArray
        if (outTensor.dataType() == org.tensorflow.lite.DataType.UINT8) {
            val out = ByteBuffer.allocateDirect(classes).order(ByteOrder.nativeOrder())
            model.run(input, out)
            val q = outTensor.quantizationParams()
            val scale = if (q.scale == 0f) 1f / 255f else q.scale
            scores = DoubleArray(classes) { ((out.get(it).toInt() and 0xFF) - q.zeroPoint) * scale.toDouble() }
        } else {
            val out = Array(1) { FloatArray(classes) }
            model.run(input, out)
            val raw = out[0]
            val looksLikeProbabilities = raw.all { it >= 0f } && kotlin.math.abs(raw.sum() - 1f) < 0.05f
            scores = if (looksLikeProbabilities) DoubleArray(classes) { raw[it].toDouble() } else {
                val max = raw.max()
                DoubleArray(classes) { exp((raw[it] - max).toDouble()) }
            }
        }
        val usable = scores.indices.filter { labels.getOrNull(it)?.let { name -> name.isNotBlank() && name != "background" } == true }
        val total = usable.sumOf { scores[it] }.takeIf { it > 0 } ?: 1.0
        // Subspecies of the same species ("Taraxacum officinale officinale") are merged into one line.
        return usable.groupBy { PlantWiki.binomial(labels[it]) }
            .map { (name, ids) -> PlantGuess(name, (ids.sumOf { scores[it] } / total).toFloat()) }
            .sortedByDescending { it.probability }.take(top)
    }
}

data class PlantArticle(
    val title: String,
    val lead: List<String>,
    val uses: List<Pair<String, String>>,
    val collection: String,
    val file: File,
    val path: String,
)

/** Finds the species in the Wikipedia collections on the phone and pulls out the useful parts. */
object PlantWiki {
    private val USE_SECTIONS = Regex(
        "use|uses|usage|culinary|food|edib|cultivat|medic|toxic|poison|wood|timber|fruit|propert|herbal|folk|care|growing|hazard|" +
            "uso|usos|utiliza|culin|aliment|comest|cultivo|medicin|tóxic|toxic|venen|madeira|fruto|propriedad|cuidad|perigo", RegexOption.IGNORE_CASE)
    private val USE_WORDS = Regex(
        "\\b(edible|eaten|cooked|raw\\b|leaves are|fruit|berries|medicin|toxic|poison|tea\\b|infusion|timber|firewood|fibre|cordage|dye\\b|" +
            "comest|comid|cozid|crus?\\b|fruto|bagas|medicin|tóxic|venen|chá\\b|infus|lenha|madeira|fibra|corda|tinta)", RegexOption.IGNORE_CASE)

    /** Genus + species, without the botanical author ("Quercus robur L." → "Quercus robur"). */
    fun binomial(name: String): String {
        val words = name.replace("×", "x").split(' ').filter { it.isNotBlank() }
        if (words.size >= 3 && words[1] == "x") return "${words[0]} × ${words[2]}"
        return words.take(2).joinToString(" ")
    }

    fun collections(context: Context): List<Pair<String, File>> {
        val dir = context.getExternalFilesDir(null)?.let { File(it, "kiwix") } ?: return emptyList()
        val files = dir.listFiles()?.filter { it.name.endsWith(".zim") } ?: return emptyList()
        fun pick(prefix: String) = files.filter { it.name.startsWith(prefix) }.maxByOrNull { it.name }
        val order = if (Lang.en) listOf("wikipedia_en_top1m_maxi", "wikipedia_en_top1m", "wikipedia_en_all", "wikipedia_pt", "wikipedia_en_simple", "wikipedia_en_medicine")
            else listOf("wikipedia_pt", "wikipedia_en_top1m_maxi", "wikipedia_en_top1m", "wikipedia_en_all", "wikipedia_en_simple", "wikipedia_en_medicine")
        return order.mapNotNull { prefix -> pick(prefix)?.let { prefix to it } }.distinctBy { it.second }
    }

    fun find(context: Context, scientific: String): PlantArticle? {
        val name = binomial(scientific)
        for ((collection, file) in collections(context)) {
            val archive = runCatching { ZimEngine.open(context, file) }.getOrNull() ?: continue
            val slug = name.replace(' ', '_')
            val path = listOf(slug, "A/$slug").firstOrNull { runCatching { archive.hasEntryByPath(it) }.getOrDefault(false) }
                ?: runCatching { if (archive.hasEntryByTitle(name)) archive.getEntryByTitle(name).path else null }.getOrNull()
                ?: continue
            return runCatching {
                val item = archive.getEntryByPath(path).getItem(true)
                val html = String(item.data.data, Charsets.UTF_8)
                parse(item.title.ifBlank { name }, html, collection, file, item.path)
            }.getOrNull() ?: continue
        }
        return null
    }

    private fun clean(fragment: String): String =
        Html.fromHtml(fragment, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\[\\d+\\]"), "").replace(Regex("\\s+"), " ").trim()

    private fun parse(title: String, html: String, collection: String, file: File, path: String): PlantArticle {
        // Tag names must end right after the letters, or "<li" would also match "<link …>".
        val body = html.substringAfter("id=\"mw-content-text\"", html)
        val token = Regex("<h([2-4])(?:\\s[^>]*)?>(.*?)</h\\1>|<p(?:\\s[^>]*)?>(.*?)</p>|<li(?:\\s[^>]*)?>(.*?)</li>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        var section = ""
        val lead = ArrayList<String>()
        val uses = ArrayList<Pair<String, String>>()
        for (m in token.findAll(body)) {
            when {
                m.groupValues[2].isNotEmpty() -> section = clean(m.groupValues[2])
                else -> {
                    val isItem = m.groupValues[4].isNotEmpty()
                    val text = clean(m.groupValues[3].ifEmpty { m.groupValues[4] })
                    if (text.length < 40) continue
                    if (section.isEmpty() && !isItem && lead.size < 3) lead += text
                    else if (section.isNotEmpty() && (USE_SECTIONS.containsMatchIn(section) || (!isItem && USE_WORDS.containsMatchIn(text)))
                        && uses.size < 14 && uses.none { it.second == text }) uses += section to text.take(900)
                }
            }
        }
        return PlantArticle(title, lead, uses, collection, file, path)
    }
}

class PlantIdActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val test = if (intent.getBooleanExtra(EXTRA_TEST, false))
            getExternalFilesDir(null)?.let { File(it, "test/plant.jpg") }?.takeIf { it.isFile } else null
        setContent { OracleTheme { androidx.compose.material3.Surface(color = com.oracleos.app.ui.OracleBackground, contentColor = com.oracleos.app.ui.OracleText, modifier = androidx.compose.ui.Modifier.fillMaxSize()) { PlantScreen(onBack = { finish() }, testPhoto = test?.let { Uri.fromFile(it) }) } } }
    }

    companion object {
        private const val EXTRA_TEST = "test"
        fun open(context: Context, test: Boolean = false) = context.startActivity(Intent(context, PlantIdActivity::class.java)
            .putExtra(EXTRA_TEST, test).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun decode(context: Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > 1024) decoder.setTargetSampleSize(longest / 1024)
    }

@Composable
private fun PlantScreen(onBack: () -> Unit, testPhoto: Uri? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as OracleApplication
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var guesses by remember { mutableStateOf<List<PlantGuess>>(emptyList()) }
    var selected by remember { mutableStateOf<PlantGuess?>(null) }
    var article by remember { mutableStateOf<PlantArticle?>(null) }
    var lookedUp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var aiText by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    val modelReady = remember { PlantClassifier.available(context) }

    fun lookup(guess: PlantGuess) {
        selected = guess; article = null; lookedUp = false; aiText = ""
        scope.launch {
            busy = "A procurar na enciclopédia"
            article = withContext(Dispatchers.IO) { runCatching { PlantWiki.find(context, guess.scientific) }.getOrNull() }
            lookedUp = true; busy = null
        }
    }

    fun analyse(uri: Uri) {
        scope.launch {
            error = null; guesses = emptyList(); selected = null; article = null
            busy = "A identificar"
            try {
                val bitmap = withContext(Dispatchers.IO) { decode(context, uri) }
                photo = bitmap
                guesses = withContext(Dispatchers.Default) { PlantClassifier.classify(context, bitmap) }
                busy = null
                guesses.firstOrNull()?.let { lookup(it) }
            } catch (e: Throwable) {
                busy = null; error = e.message ?: "Não foi possível analisar a foto."
            }
        }
    }

    val photoFile = remember { File(context.cacheDir, "photos/plant.jpg").apply { parentFile?.mkdirs() } }
    val photoUri = remember { FileProvider.getUriForFile(context, "com.oracleos.app.files", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) analyse(photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) analyse(uri) }
    androidx.compose.runtime.LaunchedEffect(testPhoto) { testPhoto?.let { analyse(it) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OracleHeader("Identificar planta", "Árvores, flores, ervas e frutos pela foto, sem internet") }
        item { OracleOutlineButton("◂ Voltar", onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OracleButton("Tirar foto", {
                    runCatching { camera.launch(photoUri) }.onFailure { error = "Nenhuma câmara disponível." }
                })
                OracleOutlineButton("Escolher da galeria", {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
        }
        item {
            Text("Para melhor resultado: fotografe de perto uma só parte (flor, folha, fruto ou casca), bem iluminada e focada. Se puder, faça fotos de várias partes.",
                color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (!modelReady) item {
            OracleCard(Modifier.fillMaxWidth(), accent = OracleWarning) {
                Text("O modelo de identificação ainda não está nesta versão do Oracle.", fontWeight = FontWeight.SemiBold)
            }
        }
        photo?.let { bitmap ->
            item {
                Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp))
            }
        }
        busy?.let { item { OracleLoading(it) } }
        error?.let { item { Text(it, color = OracleDanger) } }
        if (guesses.isNotEmpty()) {
            item { Text("Possíveis espécies", fontWeight = FontWeight.SemiBold) }
            items(guesses) { guess ->
                OracleCard(Modifier.fillMaxWidth(), accent = if (guess == selected) OracleGreen else OracleTextMuted,
                    onClick = { lookup(guess) }) {
                    Text(PlantWiki.binomial(guess.scientific), fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)
                    LabelValue("Confiança", "${(guess.probability * 100).toInt()}%")
                }
            }
            if ((guesses.firstOrNull()?.probability ?: 0f) < 0.5f) item {
                Text("Confiança baixa: tente outra foto, mais perto, de outra parte da planta.", color = OracleWarning,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        selected?.let { guess ->
            val a = article
            if (lookedUp && a == null) item {
                OracleCard(Modifier.fillMaxWidth(), accent = OracleWarning) {
                    Text(PlantWiki.binomial(guess.scientific), fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)
                    Text("Esta espécie não está nas enciclopédias deste telefone. Pesquise o nome no Acervo ou pergunte à IA.",
                        color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (a != null) {
                item {
                    OracleCard(Modifier.fillMaxWidth(), accent = OracleGreen) {
                        Text(a.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(PlantWiki.binomial(guess.scientific), fontStyle = FontStyle.Italic, color = OracleTextMuted)
                        a.lead.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (a.uses.isNotEmpty()) item {
                    OracleCard(Modifier.fillMaxWidth(), accent = OracleWarning) {
                        Text("Usos, alimentação, toxicidade e cuidados", fontWeight = FontWeight.SemiBold)
                        var last = ""
                        a.uses.forEach { (section, text) ->
                            if (section != last) { Text(section, fontWeight = FontWeight.SemiBold, color = OracleGreen); last = section }
                            Text(text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OracleOutlineButton("Ler artigo completo", { ZimReaderActivity.open(context, a.file, a.title, a.path) })
                        OracleOutlineButton("Explicar com a IA", {
                            if (!app.controller.localActive) { aiText = "Ative a IA local na aba IA para uma explicação prática."; return@OracleOutlineButton }
                            aiBusy = true; aiText = ""
                            scope.launch {
                                aiText = try {
                                    app.controller.explainPlant(a.title + " (" + PlantWiki.binomial(guess.scientific) + ")",
                                        (a.lead + a.uses.map { it.second }).joinToString("\n").take(3200)) { token -> aiText += token }
                                } catch (e: Throwable) { e.message ?: "Falha na IA local." }
                                aiBusy = false
                            }
                        })
                    }
                }
                if (aiBusy && aiText.isBlank()) item { OracleLoading("A IA está a escrever") }
                if (aiText.isNotBlank()) item {
                    OracleCard(Modifier.fillMaxWidth()) { Text(aiText, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        item {
            OracleCard(Modifier.fillMaxWidth(), accent = OracleDanger) {
                Text("Antes de comer ou usar como remédio", fontWeight = FontWeight.SemiBold)
                Text("A identificação por foto pode errar. Nunca coma uma planta só por esta app: confirme com o guia de plantas silvestres e pelo menos duas fontes. Várias plantas comestíveis têm sósias mortais (cicuta e cenoura-brava, dedaleira e consolda, lírio-do-vale e alho-de-urso). Cogumelos: nunca pela foto. Grávidas, crianças e doentes: evite plantas medicinais sem médico.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Modelo Google AIY Vision Plants V1 (iNaturalist, Apache-2.0) · 2102 espécies de todo o mundo. Textos da Wikipédia offline (CC BY-SA).",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Text(" ", modifier = Modifier.padding(bottom = 24.dp)) }
    }
}
