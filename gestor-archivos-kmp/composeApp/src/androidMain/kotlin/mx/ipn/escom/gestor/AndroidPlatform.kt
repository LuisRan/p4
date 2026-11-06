package mx.ipn.escom.gestor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.escom.gestor.core.AppException
import mx.ipn.escom.gestor.core.FailureKind
import mx.ipn.escom.gestor.core.FileTypes
import mx.ipn.escom.gestor.data.FileEntry
import mx.ipn.escom.gestor.data.FileStore
import mx.ipn.escom.gestor.data.PlatformServices
import mx.ipn.escom.gestor.data.RootInfo
import java.io.File

/**
 * Servicios de Android. Se crea en MainActivity.onCreate (antes de STARTED) porque registra
 * los lanzadores de ActivityResult:
 *  - ACTION_OPEN_DOCUMENT_TREE  -> carpetas (con takePersistableUriPermission)
 *  - ACTION_OPEN_DOCUMENT       -> importar archivos
 *  - ACTION_CREATE_DOCUMENT     -> exportar ("Guardar como")
 *  - ACTION_SEND / ACTION_VIEW  -> compartir / abrir con (Intent chooser)
 */
class AndroidPlatform(
    private val activity: ComponentActivity,
    private val fileProviderAuthority: String,
) : PlatformServices {
    private val context: Context = activity.applicationContext
    override val fileStore: FileStore = AndroidFileStore(context)

    private val documents = File(context.filesDir, "Documents").apply { mkdirs() }
    private val support = File(context.filesDir, "Support").apply { mkdirs() }
    override val privateDir: String = File(context.filesDir, "internal").apply { mkdirs() }.absolutePath
    override val cacheDir: String = context.cacheDir.absolutePath

    override val internalRoots = listOf(
        RootInfo(documents.absolutePath, "Documentos de la app"),
        RootInfo(support.absolutePath, "Datos internos de la app"),
    )

    // ----- lanzadores (deben registrarse antes de que la Activity pase a STARTED) -----

    private var treeResult: CompletableDeferred<Uri?>? = null
    private var openResult: CompletableDeferred<List<Uri>>? = null
    private var createResult: CompletableDeferred<Uri?>? = null

    private val treeLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        treeResult?.complete(it)
    }
    private val openLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        openResult?.complete(it)
    }
    private val createLauncher = activity.registerForActivityResult(
        object : ActivityResultContract<Pair<String, String>, Uri?>() {
            override fun createIntent(context: Context, input: Pair<String, String>) =
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)

            override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
                if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
        }
    ) { createResult?.complete(it) }

    // ----- carpetas -----

    override suspend fun pickFolder(): RootInfo? {
        val d = CompletableDeferred<Uri?>().also { treeResult = it }
        treeLauncher.launch(null)
        val uri = d.await() ?: return null
        // Permiso persistente: sobrevive a reinicios hasta que el usuario lo revoque.
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, rw)
        } catch (_: SecurityException) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (e: SecurityException) { throw AppException(FailureKind.PermissionRevoked, "el proveedor no permitió conservar el acceso") }
        }
        return rootFor(uri, granted = true)
    }

    private fun rootFor(uri: Uri, granted: Boolean): RootInfo {
        val name = try { DocumentFile.fromTreeUri(context, uri)?.name } catch (_: Exception) { null }
            ?: uri.lastPathSegment?.substringAfterLast(':') ?: "Carpeta"
        return RootInfo(uri.toString(), name, token = uri.toString(), external = true, available = granted)
    }

    override fun resolveRoot(token: String, nameHint: String): RootInfo {
        val uri = Uri.parse(token)
        // ¿Sigue vigente el permiso persistente? (el usuario puede revocarlo desde ajustes del sistema)
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        val exists = granted && try { DocumentFile.fromTreeUri(context, uri)?.exists() == true } catch (_: Exception) { false }
        return RootInfo(token, nameHint, token = token, external = true, available = exists)
    }

    // ----- importar / exportar -----

    override suspend fun pickFilesToImport(): List<FileEntry> {
        val d = CompletableDeferred<List<Uri>>().also { openResult = it }
        openLauncher.launch(arrayOf("*/*"))
        val uris = d.await()
        return withContext(Dispatchers.Default) { uris.map { fileStore.stat(it.toString()) } }
    }

    override suspend fun exportFile(entry: FileEntry): Boolean {
        val d = CompletableDeferred<Uri?>().also { createResult = it }
        createLauncher.launch(entry.name to (entry.mime ?: "application/octet-stream"))
        val dest = d.await() ?: return false
        withContext(Dispatchers.Default) {
            try {
                val input = if (entry.id.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(entry.id))
                else File(entry.id).inputStream()
                val out = context.contentResolver.openOutputStream(dest, "wt")
                    ?: throw AppException(FailureKind.WriteFailed, "no se pudo abrir el destino")
                (input ?: throw AppException(FailureKind.ReadFailed, "no se pudo abrir el origen")).use { i -> out.use { o -> i.copyTo(o) } }
            } catch (e: AppException) { throw e }
            catch (e: SecurityException) { throw AppException(FailureKind.PermissionRevoked, e.message) }
            catch (e: java.io.IOException) {
                throw AppException(if (e.message?.contains("ENOSPC") == true) FailureKind.NoSpace else FailureKind.WriteFailed, e.message)
            }
        }
        return true
    }

    // ----- compartir / abrir con -----

    /** URI compartible: content:// tal cual (SAF) o FileProvider para archivos del espacio propio. */
    private fun shareUri(entry: FileEntry): Uri = if (entry.id.startsWith("content://")) Uri.parse(entry.id)
    else FileProvider.getUriForFile(context, fileProviderAuthority, File(entry.id))

    private fun start(chooser: Intent) {
        try {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            throw AppException(FailureKind.NotAllowed, "no hay ninguna aplicación que pueda realizar esta acción")
        }
    }

    override fun share(entry: FileEntry) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(entry.mime ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, shareUri(entry))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(Intent.createChooser(send, "Compartir ${entry.name}"))
    }

    override fun openWith(entry: FileEntry) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(shareUri(entry), entry.mime ?: FileTypes.mimeOf(entry.name) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(Intent.createChooser(view, "Abrir con"))
    }
}
