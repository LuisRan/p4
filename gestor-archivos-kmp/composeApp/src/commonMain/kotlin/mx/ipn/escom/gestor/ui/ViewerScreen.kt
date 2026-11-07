@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package mx.ipn.escom.gestor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.escom.gestor.core.*
import mx.ipn.escom.gestor.data.FileEntry
import mx.ipn.escom.gestor.data.decodeImage

private const val MAX_TEXT_BYTES = 300 * 1024
private const val MAX_IMAGE_BYTES = 30 * 1024 * 1024

/** Resultado de cargar el contenido del archivo. */
private sealed interface Loaded {
    data object Loading : Loaded
    data class Fail(val error: AppException) : Loaded
    data class Text(val text: String, val truncated: Boolean) : Loaded
    data class Img(val bitmap: ImageBitmap) : Loaded
}

/** Visor a pantalla completa: texto/markdown/JSON/XML con formato, o imagen con zoom y rotación. */
@Composable
fun ViewerScreen(state: AppState, entry: FileEntry) {
    val kind = FileTypes.kindOf(entry.name)
    var loaded by remember(entry.id) { mutableStateOf<Loaded>(Loaded.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(entry.id, attempt) {
        loaded = Loaded.Loading
        loaded = withContext(Dispatchers.Default) {
            try {
                val store = state.platform.fileStore
                if (kind == ViewerKind.Image) {
                    val bytes = store.readBytes(entry.id, MAX_IMAGE_BYTES)
                    decodeImage(bytes)?.let { Loaded.Img(it) }
                        ?: Loaded.Fail(AppException(FailureKind.ReadFailed, "imagen dañada o formato no compatible"))
                } else {
                    val bytes = store.readBytes(entry.id, MAX_TEXT_BYTES + 1)
                    val truncated = bytes.size > MAX_TEXT_BYTES
                    Loaded.Text((if (truncated) bytes.copyOf(MAX_TEXT_BYTES) else bytes).decodeToString(), truncated)
                }
            } catch (e: Throwable) {
                Loaded.Fail(e.asAppException())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.name, maxLines = 1) },
                navigationIcon = { TextButton(onClick = { state.viewing = null }) { Text("←") } },
                actions = {
                    TextButton(onClick = { state.toggleFavorite(entry) }) { Text(if (state.isFavorite(entry.id)) "★" else "☆") }
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋮") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Detalles") }, onClick = { menu = false; state.dialog = Dialog.Details(entry) })
                            DropdownMenuItem(text = { Text("Compartir") }, onClick = { menu = false; state.share(entry) })
                            DropdownMenuItem(text = { Text("Abrir con…") }, onClick = { menu = false; state.openWith(entry) })
                            DropdownMenuItem(text = { Text("Exportar…") }, onClick = { menu = false; state.export(entry) })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(state.snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val l = loaded) {
                Loaded.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is Loaded.Fail -> ErrorPane(l.error.message ?: "Error", onRetry = { attempt++ }, onBack = { state.viewing = null })
                is Loaded.Img -> ImageViewer(l.bitmap, entry.name)
                is Loaded.Text -> TextViewer(l, kind)
            }
        }
    }
}

// ---------------------------------------------------------------- texto

@Composable
private fun TextViewer(l: Loaded.Text, kind: ViewerKind) {
    val canFormat = kind == ViewerKind.Json || kind == ViewerKind.Xml
    var formatted by remember { mutableStateOf(true) }
    var rendered by remember { mutableStateOf(true) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(Modifier.fillMaxSize()) {
        if (l.truncated) {
            Text(
                "Archivo grande: se muestran los primeros ${MAX_TEXT_BYTES / 1024} KB.",
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (canFormat || kind == ViewerKind.Markdown) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canFormat) FilterChip(formatted, { formatted = !formatted }, label = { Text("Formateado") })
                if (kind == ViewerKind.Markdown) FilterChip(rendered, { rendered = !rendered }, label = { Text("Vista renderizada") })
            }
        }
        if (kind == ViewerKind.Markdown && rendered) {
            MarkdownView(l.text)
        } else {
            // Prepara (en segundo plano) el texto formateado y sus tokens de color.
            val content by produceState<Pair<String, String?>?>(null, l.text, formatted, kind) {
                value = withContext(Dispatchers.Default) {
                    if (canFormat && formatted) {
                        try { (if (kind == ViewerKind.Json) prettyJson(l.text) else prettyXml(l.text)) to null }
                        catch (e: Exception) { l.text to "No se pudo dar formato (${e.message?.take(80) ?: "contenido inválido"}). Se muestra el original." }
                    } else l.text to null
                }
            }
            val c = content
            if (c == null) CircularProgressIndicator(Modifier.padding(16.dp)) else {
                c.second?.let {
                    Text(it, Modifier.fillMaxWidth().padding(8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                HighlightedText(c.first, kind, dark)
            }
        }
    }
}

/** Texto monoespaciado con resaltado, troceado en bloques de líneas dentro de una LazyColumn. */
@Composable
private fun HighlightedText(text: String, kind: ViewerKind, dark: Boolean) {
    val chunks by produceState<List<AnnotatedString>>(emptyList(), text, kind, dark) {
        value = withContext(Dispatchers.Default) {
            val tokens = when (kind) {
                ViewerKind.Json -> SyntaxHighlighter.tokenizeJson(text)
                ViewerKind.Xml -> SyntaxHighlighter.tokenizeXml(text)
                else -> listOf(Token(text, TokenType.Plain))
            }
            val palette = paletteFor(dark)
            val full = buildAnnotatedString {
                for (t in tokens) {
                    val color = palette[t.type]
                    if (color == null) append(t.text) else withStyle(SpanStyle(color = color)) { append(t.text) }
                }
            }
            chunkByLines(full, 60)
        }
    }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            itemsIndexed(chunks) { _, chunk ->
                Text(chunk, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

/** Parte un AnnotatedString en bloques de [lines] líneas conservando los estilos. */
private fun chunkByLines(s: AnnotatedString, lines: Int): List<AnnotatedString> {
    val out = ArrayList<AnnotatedString>()
    val text = s.text
    var start = 0
    var count = 0
    for (i in text.indices) {
        if (text[i] == '\n' && ++count == lines) {
            out.add(s.subSequence(start, i)); start = i + 1; count = 0
        }
    }
    if (start <= text.length) out.add(s.subSequence(start, text.length))
    return out
}

private fun paletteFor(dark: Boolean): Map<TokenType, Color> = if (dark) mapOf(
    TokenType.Key to Color(0xFF9CDCFE), TokenType.String to Color(0xFFCE9178), TokenType.Number to Color(0xFFB5CEA8),
    TokenType.Keyword to Color(0xFFC586C0), TokenType.Punct to Color(0xFF9E9E9E), TokenType.Tag to Color(0xFF4EC9B0),
    TokenType.AttrName to Color(0xFF9CDCFE), TokenType.AttrValue to Color(0xFFCE9178), TokenType.Comment to Color(0xFF6A9955),
) else mapOf(
    TokenType.Key to Color(0xFF0B3C8C), TokenType.String to Color(0xFFA31515), TokenType.Number to Color(0xFF098658),
    TokenType.Keyword to Color(0xFF6A1B3A), TokenType.Punct to Color(0xFF616161), TokenType.Tag to Color(0xFF800000),
    TokenType.AttrName to Color(0xFFE50000), TokenType.AttrValue to Color(0xFF0451A5), TokenType.Comment to Color(0xFF008000),
)

// ---------------------------------------------------------------- markdown

@Composable
private fun MarkdownView(source: String) {
    val blocks = remember(source) { Markdown.parse(source) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val typo = MaterialTheme.typography
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(blocks) { _, b ->
                when (b) {
                    is MdBlock.Heading -> {
                        val style = listOf(typo.headlineMedium, typo.headlineSmall, typo.titleLarge, typo.titleMedium, typo.titleSmall, typo.labelLarge)[b.level - 1]
                        Text(inlineText(b.text, codeBg), Modifier.padding(top = 12.dp, bottom = 4.dp), style = style, fontWeight = FontWeight.Bold)
                    }
                    is MdBlock.ListItem -> Row(Modifier.padding(start = (8 + b.indent * 4).dp, top = 2.dp, bottom = 2.dp)) {
                        Text(b.bullet, Modifier.width(24.dp))
                        Text(inlineText(b.text, codeBg))
                    }
                    is MdBlock.Quote -> Row(Modifier.padding(vertical = 4.dp).height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
                        Text(inlineText(b.text, codeBg), Modifier.padding(start = 12.dp), fontStyle = FontStyle.Italic)
                    }
                    is MdBlock.Code -> Text(
                        b.text, Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(6.dp)).background(codeBg).padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                    )
                    MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    MdBlock.Blank -> Spacer(Modifier.height(8.dp))
                    is MdBlock.Paragraph -> Text(inlineText(b.text, codeBg))
                }
            }
        }
    }
}

private fun inlineText(s: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    for (seg in Markdown.inline(s)) {
        val style = SpanStyle(
            fontWeight = if (seg.bold) FontWeight.Bold else null,
            fontStyle = if (seg.italic) FontStyle.Italic else null,
            fontFamily = if (seg.code) FontFamily.Monospace else null,
            background = if (seg.code) codeBg else Color.Unspecified,
        )
        withStyle(style) { append(seg.text) }
    }
}

// ---------------------------------------------------------------- imagen

/** Imagen con pellizco (zoom), arrastre, doble toque (zoom 2x / restablecer) y rotación en pasos de 90 grados. */
@Composable
private fun ImageViewer(bitmap: ImageBitmap, name: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var quarterTurns by remember { mutableIntStateOf(0) }
    val angle by animateFloatAsState(quarterTurns * 90f)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    })
                },
        ) {
            Image(
                bitmap, name, Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                    rotationZ = angle
                },
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(12.dp)
                .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { quarterTurns -= 1 }) { Text("⟲") }
            TextButton(onClick = { quarterTurns += 1 }) { Text("⟳") }
            TextButton(onClick = { scale = (scale / 1.5f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero }) { Text("−") }
            TextButton(onClick = { scale = (scale * 1.5f).coerceAtMost(8f) }) { Text("+") }
            TextButton(onClick = { scale = 1f; offset = Offset.Zero; quarterTurns = 0 }) { Text("Restablecer") }
        }
    }
}
