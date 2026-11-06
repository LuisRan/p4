package mx.ipn.escom.circuito.platform

import androidx.compose.runtime.Composable

/** Tipos de vibración/haptics. */
enum class Haptic { SELECT, STRONG }

/** Archivo elegido por el usuario al importar. */
class ImportedFile(val name: String, val content: String)

/**
 * Servicios dependientes de la plataforma (expect/actual por `rememberPlatformServices`).
 *  - Android: AudioTrack, Vibrator, Storage Access Framework, Intent chooser.
 *  - iOS: AVAudioPlayer, UIFeedbackGenerator, UIDocumentPicker, UIActivityViewController.
 */
interface PlatformServices {
    /** Reproduce un WAV PCM16 (se cachea por [id]). */
    fun playWav(id: String, wav: ByteArray)

    fun haptic(kind: Haptic)

    /** Exporta texto a una ubicación elegida por el usuario. [onResult] true si se guardó. */
    fun exportText(fileName: String, mime: String, content: String, onResult: (Boolean) -> Unit)

    /** Comparte el contenido como archivo mediante el selector del sistema. */
    fun shareText(fileName: String, mime: String, content: String)

    /** Abre el selector de archivos; entrega null si se cancela. */
    fun importText(onResult: (ImportedFile?) -> Unit)
}

@Composable
expect fun rememberPlatformServices(): PlatformServices

/** Carpeta privada de la app (sandbox). */
interface FileStore {
    fun list(dir: String): List<String>
    fun read(dir: String, name: String): String?
    /** Escritura atómica; lanza SaveIoException si falla. */
    fun write(dir: String, name: String, content: String)
    fun delete(dir: String, name: String): Boolean
}

/** Almacenamiento privado real de cada plataforma. */
expect fun createFileStore(): FileStore

/** Implementación en memoria (pruebas y vistas previas). */
class MemoryFileStore : FileStore {
    private val files = LinkedHashMap<String, String>()
    private fun key(d: String, n: String) = "$d/$n"
    override fun list(dir: String) = files.keys.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }
    override fun read(dir: String, name: String) = files[key(dir, name)]
    override fun write(dir: String, name: String, content: String) { files[key(dir, name)] = content }
    override fun delete(dir: String, name: String) = files.remove(key(dir, name)) != null
}

/** Botón/gesto "atrás" del sistema (Android); en iOS no hace nada. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
