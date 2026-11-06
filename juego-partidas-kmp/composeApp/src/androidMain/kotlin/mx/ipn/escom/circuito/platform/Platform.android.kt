package mx.ipn.escom.circuito.platform

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import mx.ipn.escom.circuito.CircuitoApplication
import mx.ipn.escom.circuito.saves.SaveIoException

/** Archivos privados: `filesDir/<dir>/<nombre>` (sin permisos de almacenamiento). */
private class AndroidFileStore(private val root: File) : FileStore {
    private fun dir(d: String) = File(root, d).apply { mkdirs() }
    override fun list(dir: String) = dir(dir).listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
    override fun read(dir: String, name: String): String? = try {
        File(dir(dir), name).takeIf { it.isFile }?.readText(Charsets.UTF_8)
    } catch (e: Exception) { null }

    override fun write(dir: String, name: String, content: String) {
        try {
            // Escritura atómica: archivo temporal + renombrado.
            val target = File(dir(dir), name)
            val tmp = File(dir(dir), "$name.tmp")
            tmp.writeText(content, Charsets.UTF_8)
            if (!tmp.renameTo(target)) { target.delete(); if (!tmp.renameTo(target)) throw java.io.IOException("renombrado") }
        } catch (e: Exception) {
            throw SaveIoException("No se pudo escribir \"$name\": ${e.message}")
        }
    }

    override fun delete(dir: String, name: String) = File(dir(dir), name).delete()
}

actual fun createFileStore(): FileStore = AndroidFileStore(CircuitoApplication.appContext.filesDir)

private class AndroidPlatform(
    private val context: Context,
    private val exporter: (String, String, String, (Boolean) -> Unit) -> Unit,
    private val importer: ((ImportedFile?) -> Unit) -> Unit,
) : PlatformServices {
    private val tracks = HashMap<String, AudioTrack>()

    /** AudioTrack estático con el PCM del WAV (se salta la cabecera de 44 bytes). */
    override fun playWav(id: String, wav: ByteArray) {
        try {
            val t = tracks.getOrPut(id) {
                val pcm = wav.copyOfRange(44, wav.size)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(22050).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    )
                    .setBufferSizeInBytes(pcm.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track
            }
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) { /* el sonido nunca debe romper el juego */ }
    }

    @Suppress("DEPRECATION")
    override fun haptic(kind: Haptic) {
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val ms = if (kind == Haptic.STRONG) 60L else 15L
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(ms)
        } catch (e: Exception) { }
    }

    override fun exportText(fileName: String, mime: String, content: String, onResult: (Boolean) -> Unit) =
        exporter(fileName, mime, content, onResult)

    /** Escribe en cache/share y lanza ACTION_SEND con FileProvider. */
    override fun shareText(fileName: String, mime: String, content: String) {
        try {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, fileName).apply { writeText(content, Charsets.UTF_8) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Compartir partida").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    override fun importText(onResult: (ImportedFile?) -> Unit) = importer(onResult)
}

private class PendingHolder {
    var export: Triple<String, String, (Boolean) -> Unit>? = null
    var import: ((ImportedFile?) -> Unit)? = null
}

@Composable
actual fun rememberPlatformServices(): PlatformServices {
    val context = LocalContext.current
    // Storage Access Framework: CreateDocument (exportar) y OpenDocument (importar).
    // Los callbacks pendientes viven en un holder recordado (sobreviven a recomposiciones).
    val h = remember { PendingHolder() }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val p = h.export; h.export = null
        if (p == null) return@rememberLauncherForActivityResult
        if (uri == null) { p.third(false); return@rememberLauncherForActivityResult }
        val ok = try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(p.second.toByteArray(Charsets.UTF_8)) } != null
        } catch (e: Exception) { false }
        p.third(ok)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val cb = h.import; h.import = null
        if (cb == null) return@rememberLauncherForActivityResult
        if (uri == null) { cb(null); return@rememberLauncherForActivityResult }
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            var name = "importado"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) name = c.getString(idx) ?: name
            }
            cb(ImportedFile(name, text))
        } catch (e: Exception) { cb(ImportedFile("importado", "")) }
    }
    return remember(context) {
        AndroidPlatform(
            context,
            exporter = { name, _, content, cb ->
                h.export = Triple(name, content, cb)
                createLauncher.launch(name)
            },
            importer = { cb ->
                h.import = cb
                openLauncher.launch(arrayOf("*/*"))
            },
        )
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) =
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
