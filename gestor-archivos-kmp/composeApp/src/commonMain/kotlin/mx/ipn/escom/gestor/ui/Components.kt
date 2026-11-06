package mx.ipn.escom.gestor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.escom.gestor.core.FileTypes
import mx.ipn.escom.gestor.core.ViewerKind
import mx.ipn.escom.gestor.data.FileEntry

/** Glifo (emoji) por tipo de archivo; evita depender del paquete de iconos extendidos. */
fun glyphFor(e: FileEntry): String = if (e.isDirectory) "📁" else when (FileTypes.kindOf(e.name)) {
    ViewerKind.Image -> "🖼"
    ViewerKind.Json, ViewerKind.Xml -> "🧾"
    ViewerKind.Text, ViewerKind.Markdown -> "📄"
    ViewerKind.Unsupported -> "📦"
}

/** Icono o miniatura (con caché) del elemento. */
@Composable
fun EntryThumb(state: AppState, e: FileEntry, size: Dp) {
    val isImage = !e.isDirectory && FileTypes.kindOf(e.name) == ViewerKind.Image
    val bmp: ImageBitmap? by produceState<ImageBitmap?>(null, e.id, e.size, e.modified) {
        if (isImage) value = withContext(Dispatchers.Default) { state.thumbs.get(e) }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, e.name, Modifier.size(size), contentScale = ContentScale.Crop)
        else Text(glyphFor(e), fontSize = (size.value * 0.6f).sp)
    }
}
