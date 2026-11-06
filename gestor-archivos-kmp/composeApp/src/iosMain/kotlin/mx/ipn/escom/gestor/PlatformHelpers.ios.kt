@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package mx.ipn.escom.gestor.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual fun formatDateTime(millis: Long): String {
    val f = NSDateFormatter().apply { dateFormat = "dd/MM/yyyy HH:mm" }
    return f.stringFromDate(NSDate.dateWithTimeIntervalSince1970(millis / 1000.0))
}

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/** Reduce la imagen (Skia) si supera [maxDimension]. */
private fun scaled(img: Image, maxDimension: Int): Image {
    val m = maxOf(img.width, img.height)
    if (m <= maxDimension) return img
    val s = maxDimension.toFloat() / m
    val w = (img.width * s).toInt().coerceAtLeast(1)
    val h = (img.height * s).toInt().coerceAtLeast(1)
    val bmp = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(w, h)) }
    Canvas(bmp).drawImageRect(img, org.jetbrains.skia.Rect.makeWH(img.width.toFloat(), img.height.toFloat()),
        org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()), SamplingMode.LINEAR, null, true)
    return Image.makeFromBitmap(bmp)
}

actual fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    try { scaled(Image.makeFromEncoded(bytes), maxDimension).toComposeImageBitmap() } catch (_: Throwable) { null }

actual fun createThumbnailPng(bytes: ByteArray, maxSize: Int): ByteArray? = try {
    scaled(Image.makeFromEncoded(bytes), maxSize).encodeToData(EncodedImageFormat.PNG)?.bytes
} catch (_: Throwable) { null }
