package mx.ipn.escom.gestor.data

/** Formatea milisegundos epoch como "dd/MM/yyyy HH:mm" en la zona horaria local. */
expect fun formatDateTime(millis: Long): String

/** Decodifica una imagen (limitando su tamaño en memoria). null si el formato no es válido. */
expect fun decodeImage(bytes: ByteArray, maxDimension: Int = 2560): androidx.compose.ui.graphics.ImageBitmap?

/** Genera una miniatura PNG de lado máximo [maxSize]; null si no se pudo decodificar. */
expect fun createThumbnailPng(bytes: ByteArray, maxSize: Int): ByteArray?

/** Milisegundos desde epoch. */
expect fun nowMillis(): Long
