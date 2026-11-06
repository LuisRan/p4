package mx.ipn.escom.gestor.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

actual fun nowMillis(): Long = System.currentTimeMillis()

/** Decodifica con inSampleSize para no agotar memoria con imágenes enormes. */
private fun decodeSampled(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

actual fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    try { decodeSampled(bytes, maxDimension)?.asImageBitmap() } catch (_: Throwable) { null }

actual fun createThumbnailPng(bytes: ByteArray, maxSize: Int): ByteArray? = try {
    decodeSampled(bytes, maxSize * 2)?.let { src ->
        val scale = maxSize.toFloat() / maxOf(src.width, src.height)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }.toByteArray()
    }
} catch (_: Throwable) { null }
